package com.tixcorner.dss.ml;

import com.tixcorner.dss.db.DssDatabase;
import com.tixcorner.dss.source.Invoice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dataset historis berlabel (FR-11). Satu baris = satu snapshot invoice sebelum jatuh tempo.
 */
public class Dataset {

    public static final long UKURAN_MAKS_BYTE = 10L * 1024 * 1024;
    public static final List<String> FITUR = List.of("nominal_transaksi", "persentase_terbayar",
            "jumlah_cicilan_dipilih", "metode_pembayaran", "penggunaan_promo", "sisa_jam_jatuh_tempo",
            "hari_pemesanan");
    private static final List<String> KOLOM_WAJIB = List.of("invoice_id", "event_id", "created_at",
            "observed_at", "due_at", "nominal_tagihan", "nominal_terbayar_saat_observasi", "jumlah_cicilan",
            "metode_pembayaran", "penggunaan_promo", "status_akhir", "lunas_at");
    private static final Set<String> STATUS_DIKELUARKAN = Set.of("refund", "event_cancelled", "admin_cancelled", "test");
    private static final DateTimeFormatter TGL = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ---------- atribut (kolom tabel DATASETS) ----------
    private final int datasetId;
    private final int createdBy;
    private String version;
    private String checksum;
    private final String featureSchema;
    private final String labelPolicy;
    private final String snapshotPolicy;
    private String splitPeriods;

    // ---------- isi dataset di memori (bukan kolom tabel) ----------
    private final Path berkas;
    private final List<Baris> baris = new ArrayList<>();
    private final Map<String, Integer> alasanTolak = new LinkedHashMap<>();
    private int jumlahBarisMentah;
    private boolean sudahDilabel;
    private List<Baris> dataLatih = List.of();
    private List<Baris> dataValidasi = List.of();
    private List<Baris> dataUji = List.of();

    /** Satu baris dataset: snapshot invoice + hasil akhir yang dipakai untuk label. */
    public static final class Baris {
        private final Invoice snapshot;
        private final LocalDateTime observedAt;
        private final String statusAkhir;
        private final LocalDateTime lunasAt;
        private int label = -1;

        Baris(Invoice snapshot, LocalDateTime observedAt, String statusAkhir, LocalDateTime lunasAt) {
            this.snapshot = snapshot;
            this.observedAt = observedAt;
            this.statusAkhir = statusAkhir;
            this.lunasAt = lunasAt;
        }

        /** Fitur dibentuk dari snapshot pada waktu observasi, bukan dari keadaan akhir transaksi. */
        public Map<String, Object> getFitur() {
            return snapshot.toFitur(observedAt);
        }

        public int getLabel() {
            return label;
        }

        public LocalDateTime getObservedAt() {
            return observedAt;
        }
    }

    public Dataset(int datasetId, int createdBy, Path berkas) {
        this.datasetId = datasetId;
        this.createdBy = createdBy;
        this.berkas = berkas;
        this.featureSchema = FITUR.size() + " fitur waktu observasi (FR-05): " + String.join(", ", FITUR);
        this.labelPolicy = "gagal=1 jika belum lunas saat jatuh tempo (pelunasan terlambat tetap gagal)";
        this.snapshotPolicy = "1 snapshot/invoice sebelum jatuh tempo";
    }

    /** FR-11 langkah 1: cek berkas (.csv, maks 10 MB), skema kolom, duplikasi, nominal, dan urutan waktu. */
    public boolean validasi() {
        baris.clear();
        alasanTolak.clear();
        jumlahBarisMentah = 0;
        try {
            if (!Files.exists(berkas)) {
                return tolak("Berkas tidak ditemukan");
            }
            if (!berkas.getFileName().toString().toLowerCase().endsWith(".csv")) {
                return tolak("Format harus .csv (xlsx belum didukung di prototipe)");
            }
            if (Files.size(berkas) > UKURAN_MAKS_BYTE) {
                return tolak("Ukuran berkas melebihi 10 MB");
            }
            checksum = "sha256:" + sha256(berkas);
            List<String> baris0 = Files.readAllLines(berkas);
            List<String> header = Arrays.asList(baris0.get(0).split(","));
            if (!header.containsAll(KOLOM_WAJIB)) {
                return tolak("Kolom wajib tidak lengkap");
            }
            Map<String, Integer> kol = new HashMap<>();
            for (int i = 0; i < header.size(); i++) {
                kol.put(header.get(i), i);
            }
            Set<String> idTerlihat = new HashSet<>();
            for (String teks : baris0.subList(1, baris0.size())) {
                if (teks.isBlank()) {
                    continue;
                }
                jumlahBarisMentah++;
                String[] k = teks.split(",", -1);
                try {
                    String id = k[kol.get("invoice_id")];
                    if (!idTerlihat.add(id)) {
                        tolak("Duplikat invoice_id");
                        continue;
                    }
                    double nominal = Double.parseDouble(k[kol.get("nominal_tagihan")]);
                    double terbayar = Double.parseDouble(k[kol.get("nominal_terbayar_saat_observasi")]);
                    LocalDateTime created = DssDatabase.waktu(k[kol.get("created_at")]);
                    LocalDateTime observed = DssDatabase.waktu(k[kol.get("observed_at")]);
                    LocalDateTime due = DssDatabase.waktu(k[kol.get("due_at")]);
                    if (nominal <= 0) {
                        tolak("Nominal tagihan tidak positif");
                        continue;
                    }
                    if (terbayar < 0 || terbayar > nominal) {
                        tolak("Nominal terbayar tidak sah");
                        continue;
                    }
                    if (!due.isAfter(observed)) {
                        tolak("Jatuh tempo tidak setelah waktu observasi");
                        continue;
                    }
                    Invoice snapshot = new Invoice(id, k[kol.get("event_id")], nominal, terbayar,
                            terbayar > 0 ? Invoice.PARTIALLY_PAID : Invoice.PENDING,
                            Integer.parseInt(k[kol.get("jumlah_cicilan")]), k[kol.get("metode_pembayaran")],
                            Boolean.parseBoolean(k[kol.get("penggunaan_promo")]), created, due);
                    baris.add(new Baris(snapshot, observed, Invoice.normalisasiStatus(k[kol.get("status_akhir")]),
                            DssDatabase.waktu(k[kol.get("lunas_at")])));
                } catch (RuntimeException e) {
                    tolak("Format nilai tidak valid");
                }
            }
            return !baris.isEmpty();
        } catch (IOException e) {
            return tolak("Gagal membaca berkas: " + e.getMessage());
        }
    }

    /** FR-11 langkah 2-3: label gagal=1 bila tidak lunas sampai batas jatuh tempo. */
    public void bentukLabel() {
        Iterator<Baris> it = baris.iterator();
        while (it.hasNext()) {
            Baris b = it.next();
            if (STATUS_DIKELUARKAN.contains(b.statusAkhir)) {
                tolak("Dikeluarkan (" + b.statusAkhir + ")");
                it.remove();
            } else if (Invoice.PENDING.equals(b.statusAkhir) || Invoice.PARTIALLY_PAID.equals(b.statusAkhir)) {
                tolak("Belum memiliki hasil akhir");
                it.remove();
            } else if ("paid".equals(b.statusAkhir) && b.lunasAt == null) {
                tolak("Status lunas tanpa waktu pelunasan");
                it.remove();
            } else {
                boolean lunasTepatWaktu = b.lunasAt != null && !b.lunasAt.isAfter(b.snapshot.getDueAt());
                b.label = lunasTepatWaktu ? 0 : 1;
            }
        }
        baris.sort(Comparator.comparing(b -> b.observedAt));
        if (!baris.isEmpty()) {   // versi mengikuti bulan observasi terakhir yang sudah berlabel
            version = "DS-" + baris.get(baris.size() - 1).observedAt.format(DateTimeFormatter.ofPattern("yyyy-MM")) + "-v1";
        }
        sudahDilabel = true;
    }

    /**
     * FR-12 langkah 1: bagi kronologis (mis. 70/15/15). Baris yang labelnya baru diketahui
     * SETELAH periode berikutnya dimulai dikeluarkan agar tidak membocorkan masa depan.
     */
    public void bagiPeriode(double[] proporsi) {
        if (!sudahDilabel) {
            bentukLabel();
        }
        int n = baris.size();
        int nLatih = (int) Math.round(n * proporsi[0]);
        int nValidasi = (int) Math.round(n * proporsi[1]);
        List<Baris> latih = new ArrayList<>(baris.subList(0, nLatih));
        List<Baris> validasi = new ArrayList<>(baris.subList(nLatih, nLatih + nValidasi));
        List<Baris> uji = new ArrayList<>(baris.subList(nLatih + nValidasi, n));
        buangYangMelintasi(latih, validasi.get(0).observedAt);
        buangYangMelintasi(validasi, uji.get(0).observedAt);
        dataLatih = Collections.unmodifiableList(latih);
        dataValidasi = Collections.unmodifiableList(validasi);
        dataUji = Collections.unmodifiableList(uji);
        splitPeriods = String.format("latih %s (%d), validasi %s (%d), uji %s (%d)",
                periode(dataLatih), dataLatih.size(), periode(dataValidasi), dataValidasi.size(),
                periode(dataUji), dataUji.size());
    }

    /** Layak latih bila sudah dibagi dan kedua kelas ada pada data latih maupun uji. */
    public boolean isLayakLatih() {
        return sudahDilabel && !dataValidasi.isEmpty()
                && jumlahKelas(dataLatih)[0] > 0 && jumlahKelas(dataLatih)[1] > 0
                && jumlahKelas(dataUji)[0] > 0 && jumlahKelas(dataUji)[1] > 0;
    }

    // ---------- bantu ----------
    private void buangYangMelintasi(List<Baris> bagian, LocalDateTime awalPeriodeBerikut) {
        int sebelum = bagian.size();
        bagian.removeIf(b -> b.snapshot.getDueAt().isAfter(awalPeriodeBerikut));
        for (int i = 0; i < sebelum - bagian.size(); i++) {
            tolak("Melintasi batas periode split");
        }
    }

    private boolean tolak(String alasan) {
        alasanTolak.merge(alasan, 1, Integer::sum);
        return false;
    }

    private static String periode(List<Baris> bagian) {
        return bagian.get(0).observedAt.format(TGL) + " s.d. " + bagian.get(bagian.size() - 1).observedAt.format(TGL);
    }

    private static String sha256(Path berkas) throws IOException {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(berkas));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @return {jumlah berhasil (0), jumlah gagal (1)} */
    public static int[] jumlahKelas(List<Baris> bagian) {
        int[] k = new int[2];
        for (Baris b : bagian) {
            if (b.label >= 0) {
                k[b.label]++;
            }
        }
        return k;
    }

    // ---------- getter ----------
    public int getDatasetId() {
        return datasetId;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public String getVersion() {
        return version;
    }

    public String getChecksum() {
        return checksum;
    }

    public String getFeatureSchema() {
        return featureSchema;
    }

    public String getLabelPolicy() {
        return labelPolicy;
    }

    public String getSnapshotPolicy() {
        return snapshotPolicy;
    }

    public String getSplitPeriods() {
        return splitPeriods;
    }

    public int getJumlahBarisMentah() {
        return jumlahBarisMentah;
    }

    public int getJumlahBarisValid() {
        return baris.size();
    }

    public Map<String, Integer> getAlasanTolak() {
        return Collections.unmodifiableMap(alasanTolak);
    }

    public List<Baris> getSemuaBaris() {
        return Collections.unmodifiableList(baris);
    }

    public List<Baris> getDataLatih() {
        return dataLatih;
    }

    public List<Baris> getDataValidasi() {
        return dataValidasi;
    }

    public List<Baris> getDataUji() {
        return dataUji;
    }

    public String getPeriodeUji() {
        return dataUji.isEmpty() ? "-" : periode(dataUji);
    }
}
