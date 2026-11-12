package com.tixcorner.dss.ml;

import com.tixcorner.dss.db.DssDatabase;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * Satu versi model Decision Tree (CART) beserta pipeline-nya (FR-12, FR-13, FR-06).
 * Pohon dilatih sendiri di Java agar program berjalan tanpa pustaka luar;
 * implementasi produksi pada SRS memakai scikit-learn.
 */
public class ModelVersion {

    public static final String STATUS_EKSPERIMEN = "eksperimen";
    public static final String STATUS_DISETUJUI = "disetujui";
    public static final String STATUS_AKTIF = "aktif";
    public static final String RENDAH = "Rendah";
    public static final String SEDANG = "Sedang";
    public static final String TINGGI = "Tinggi";
    public static final double AMBANG_SEDANG = 0.35;
    public static final double AMBANG_TINGGI = 0.70;
    public static final double AMBANG_BINER = 0.50;

    // ---------- atribut (kolom tabel MODEL_VERSIONS) ----------
    private final int modelId;
    private final int datasetId;
    private final String modelVersion;
    private final String parameters;
    private final String pipelinePath;
    private final String modelPath;
    private final String riskThresholds;
    private String status;
    private Integer approvedBy;
    private LocalDateTime approvedAt;

    // ---------- isi model di memori ----------
    private final Dataset dataset;
    private final String criterion;
    private final int maxDepth;
    private final int minSamplesLeaf;
    private final int randomState;
    private final List<String> kategoriMetode = new ArrayList<>();   // encoder dipelajari dari data latih
    private final List<ModelEvaluation> daftarEvaluasi = new ArrayList<>();
    private Node akar;

    private static final class Node {
        int fitur = -1;
        double ambang;
        Node kiri;
        Node kanan;
        double pGagal;
        int nSampel;

        boolean daun() {
            return fitur < 0;
        }
    }

    public ModelVersion(int modelId, Dataset dataset, Map<String, String> parameter) {
        this.modelId = modelId;
        this.dataset = dataset;
        this.datasetId = dataset.getDatasetId();
        this.criterion = parameter.getOrDefault("criterion", "gini");
        if (!criterion.equals("gini") && !criterion.equals("entropy")) {
            throw new IllegalArgumentException("criterion harus gini atau entropy");
        }
        this.maxDepth = Integer.parseInt(parameter.getOrDefault("max_depth", "5"));
        this.minSamplesLeaf = Integer.parseInt(parameter.getOrDefault("min_samples_leaf", "20"));
        this.randomState = Integer.parseInt(parameter.getOrDefault("random_state", "42"));
        this.modelVersion = String.format("DT-v1.%d", modelId - 1);   // model_id 3 -> DT-v1.2
        this.parameters = String.format("criterion=%s, max_depth=%d, min_samples_leaf=%d, random_state=%d",
                criterion, maxDepth, minSamplesLeaf, randomState);
        String folder = "models/" + modelVersion.toLowerCase();
        this.pipelinePath = folder + "/pipeline.joblib";
        this.modelPath = folder + "/model.joblib";
        this.riskThresholds = "Rendah<0.35; Sedang<0.70; Tinggi>=0.70";
        this.status = STATUS_EKSPERIMEN;
    }

    /** FR-12: pelajari encoder dari data latih SAJA, lalu bangun pohon CART. */
    public void latih() {
        List<Dataset.Baris> latih = dataset.getDataLatih();
        if (latih.isEmpty()) {
            throw new IllegalStateException("Data latih kosong");
        }
        kategoriMetode.clear();
        latih.stream().map(b -> (String) b.getFitur().get("metode_pembayaran"))
                .distinct().sorted().forEach(kategoriMetode::add);
        double[][] x = new double[latih.size()][];
        int[] y = new int[latih.size()];
        for (int i = 0; i < latih.size(); i++) {
            x[i] = vektor(latih.get(i).getFitur());
            y[i] = latih.get(i).getLabel();
        }
        akar = bangun(x, y, IntStream.range(0, x.length).toArray(), 0, new Random(randomState));
    }

    /** FR-13: evaluasi pada data uji terpisah dengan ambang biner 0,50. */
    public ModelEvaluation evaluasi() {
        pastikanTerlatih();
        List<Dataset.Baris> uji = dataset.getDataUji();
        int[] yTrue = new int[uji.size()];
        int[] yPred = new int[uji.size()];
        for (int i = 0; i < uji.size(); i++) {
            yTrue[i] = uji.get(i).getLabel();
            yPred[i] = prediksiProba(uji.get(i).getFitur()) >= AMBANG_BINER ? 1 : 0;
        }
        int[] kelasLatih = Dataset.jumlahKelas(dataset.getDataLatih());
        ModelEvaluation ev = new ModelEvaluation(DssDatabase.nextId("evaluation"), modelId,
                dataset.getPeriodeUji(), yTrue, yPred, AMBANG_BINER, kelasLatih[1] > kelasLatih[0] ? 1 : 0);
        ev.hitungMetrik();
        daftarEvaluasi.add(ev);
        return ev;
    }

    /** Hanya model yang LULUS evaluasi terakhir yang boleh disetujui. */
    public void setujui(int adminId) {
        ModelEvaluation terakhir = getEvaluasiTerakhir();
        if (terakhir == null || !terakhir.isLulus()) {
            throw new IllegalStateException("Model belum lulus kriteria penerimaan; status tetap eksperimen");
        }
        status = STATUS_DISETUJUI;
        approvedBy = adminId;
        approvedAt = DssDatabase.sekarang();
    }

    /** Menjadikan versi ini aktif; versi aktif sebelumnya tetap tersimpan sebagai "disetujui". */
    public boolean aktifkan() {
        if (!STATUS_DISETUJUI.equals(status) && !STATUS_AKTIF.equals(status)) {
            return false;
        }
        for (ModelVersion lain : DssDatabase.MODELS) {
            if (lain != this && STATUS_AKTIF.equals(lain.status)) {
                lain.status = STATUS_DISETUJUI;
            }
        }
        status = STATUS_AKTIF;
        return true;
    }

    /** FR-06: skor P(gagal=1) = proporsi sampel latih kelas gagal pada daun yang dicapai. */
    public float prediksiProba(Map<String, Object> fitur) {
        pastikanTerlatih();
        return (float) telusuri(vektor(fitur), null).pGagal;
    }

    /** Pemetaan pasca-inferensi: Rendah < 0,35 <= Sedang < 0,70 <= Tinggi. */
    public String petakanKategori(float skor) {
        if (Float.isNaN(skor)) {
            throw new IllegalArgumentException("Skor tidak valid");   // jangan pernah jadi "Rendah"
        }
        if (skor < AMBANG_SEDANG) return RENDAH;
        if (skor < AMBANG_TINGGI) return SEDANG;
        return TINGGI;
    }

    /** Method bantu FR-08: jalur if-then dari akar sampai daun dalam bahasa yang mudah dibaca. */
    public List<String> telusuriJalur(Map<String, Object> fitur) {
        pastikanTerlatih();
        List<String> jalur = new ArrayList<>();
        Node daun = telusuri(vektor(fitur), jalur);
        jalur.add(String.format(Locale.US, "daun: P(gagal)=%.2f (n=%d)", daun.pGagal, daun.nSampel));
        return jalur;
    }

    /** Method bantu: cetak struktur pohon (untuk dokumentasi/presentasi). */
    public String cetakPohon() {
        StringBuilder sb = new StringBuilder();
        cetak(akar, "", sb);
        return sb.toString();
    }

    // ================= CART =================
    private Node bangun(double[][] x, int[] y, int[] idx, int kedalaman, Random acak) {
        Node node = new Node();
        node.nSampel = idx.length;
        int gagal = 0;
        for (int i : idx) gagal += y[i];
        node.pGagal = (double) gagal / idx.length;
        if (kedalaman >= maxDepth || idx.length < 2 * minSamplesLeaf || gagal == 0 || gagal == idx.length) {
            return node;
        }
        double impurityInduk = impurity(gagal, idx.length);
        double gainTerbaik = 1e-12;
        int fiturTerbaik = -1;
        double ambangTerbaik = 0;
        List<Integer> urutanFitur = new ArrayList<>(IntStream.range(0, Dataset.FITUR.size()).boxed().toList());
        Collections.shuffle(urutanFitur, acak);   // random_state hanya memengaruhi urutan saat gain seri
        for (int f : urutanFitur) {
            final int fi = f;
            int[] urut = Arrays.stream(idx).boxed()
                    .sorted((a, b) -> Double.compare(x[a][fi], x[b][fi]))
                    .mapToInt(Integer::intValue).toArray();
            int gagalKiri = 0;
            for (int k = 0; k < urut.length - 1; k++) {
                gagalKiri += y[urut[k]];
                int nKiri = k + 1;
                int nKanan = urut.length - nKiri;
                double v = x[urut[k]][f];
                double vBerikut = x[urut[k + 1]][f];
                if (v == vBerikut || nKiri < minSamplesLeaf || nKanan < minSamplesLeaf) {
                    continue;
                }
                double impurityAnak = (nKiri * impurity(gagalKiri, nKiri)
                        + nKanan * impurity(gagal - gagalKiri, nKanan)) / urut.length;
                double gain = impurityInduk - impurityAnak;
                if (gain > gainTerbaik) {
                    gainTerbaik = gain;
                    fiturTerbaik = f;
                    ambangTerbaik = (v + vBerikut) / 2;
                }
            }
        }
        if (fiturTerbaik < 0) {
            return node;
        }
        final int fb = fiturTerbaik;
        final double ab = ambangTerbaik;
        node.fitur = fb;
        node.ambang = ab;
        node.kiri = bangun(x, y, Arrays.stream(idx).filter(i -> x[i][fb] <= ab).toArray(), kedalaman + 1, acak);
        node.kanan = bangun(x, y, Arrays.stream(idx).filter(i -> x[i][fb] > ab).toArray(), kedalaman + 1, acak);
        return node;
    }

    private double impurity(int gagal, int n) {
        double p = (double) gagal / n;
        double q = 1 - p;
        if (criterion.equals("gini")) {
            return 1 - p * p - q * q;
        }
        return -(p > 0 ? p * log2(p) : 0) - (q > 0 ? q * log2(q) : 0);
    }

    private static double log2(double v) {
        return Math.log(v) / Math.log(2);
    }

    private Node telusuri(double[] x, List<String> jalur) {
        Node n = akar;
        while (!n.daun()) {
            boolean kiri = x[n.fitur] <= n.ambang;
            if (jalur != null) {
                jalur.add(kondisi(n, kiri));
            }
            n = kiri ? n.kiri : n.kanan;
        }
        return n;
    }

    /** Pipeline: ubah snapshot fitur menjadi vektor numerik; urutan dan skema harus sama (FR-05). */
    private double[] vektor(Map<String, Object> f) {
        if (!f.keySet().containsAll(Dataset.FITUR)) {
            throw new IllegalArgumentException("Skema fitur tidak cocok dengan versi model " + modelVersion);
        }
        double[] v = new double[Dataset.FITUR.size()];
        for (int i = 0; i < v.length; i++) {
            Object nilai = f.get(Dataset.FITUR.get(i));
            if (nilai instanceof Number angka) {
                v[i] = angka.doubleValue();
            } else if (nilai instanceof Boolean b) {
                v[i] = b ? 1 : 0;
            } else {
                v[i] = kategoriMetode.indexOf(String.valueOf(nilai));   // kategori baru -> -1
            }
        }
        return v;
    }

    private String kondisi(Node n, boolean kiri) {
        String nama = Dataset.FITUR.get(n.fitur);
        String op = kiri ? "≤" : ">";
        return switch (nama) {
            case "nominal_transaksi" -> String.format(Locale.forLanguageTag("id-ID"), "%s %s Rp%,.0f", nama, op, n.ambang);
            case "persentase_terbayar" -> String.format(Locale.US, "%s %s %.1f%%", nama, op, n.ambang * 100);
            case "sisa_jam_jatuh_tempo" -> String.format(Locale.US, "%s %s %.1f jam", nama, op, n.ambang);
            case "penggunaan_promo" -> nama + " = " + (kiri ? "tidak" : "ya");
            case "metode_pembayaran" -> {
                List<String> kelompok = new ArrayList<>();
                for (int i = 0; i < kategoriMetode.size(); i++) {
                    if ((i <= n.ambang) == kiri) kelompok.add(kategoriMetode.get(i));
                }
                yield nama + " ∈ {" + String.join(", ", kelompok) + "}";
            }
            default -> String.format("%s %s %d", nama, op, (int) Math.floor(n.ambang));   // fitur bilangan bulat
        };
    }

    private void cetak(Node n, String indent, StringBuilder sb) {
        if (n.daun()) {
            sb.append(String.format(Locale.US, "%s└─ P(gagal)=%.2f n=%d → %s%n", indent, n.pGagal, n.nSampel,
                    petakanKategori((float) n.pGagal)));
            return;
        }
        sb.append(indent).append("├─ jika ").append(kondisi(n, true)).append('\n');
        cetak(n.kiri, indent + "│  ", sb);
        sb.append(indent).append("├─ jika ").append(kondisi(n, false)).append('\n');
        cetak(n.kanan, indent + "│  ", sb);
    }

    private void pastikanTerlatih() {
        if (akar == null) {
            throw new IllegalStateException("Model " + modelVersion + " belum dilatih");
        }
    }

    // ---------- getter ----------
    public int getModelId() {
        return modelId;
    }

    public int getDatasetId() {
        return datasetId;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getParameters() {
        return parameters;
    }

    public String getPipelinePath() {
        return pipelinePath;
    }

    public String getModelPath() {
        return modelPath;
    }

    public String getRiskThresholds() {
        return riskThresholds;
    }

    public String getStatus() {
        return status;
    }

    public Integer getApprovedBy() {
        return approvedBy;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public ModelEvaluation getEvaluasiTerakhir() {
        return daftarEvaluasi.isEmpty() ? null : daftarEvaluasi.get(daftarEvaluasi.size() - 1);
    }
}
