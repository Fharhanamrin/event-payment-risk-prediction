package com.tixcorner.dss.user;

import com.tixcorner.dss.db.DssDatabase;
import com.tixcorner.dss.ml.Dataset;
import com.tixcorner.dss.ml.ModelVersion;
import com.tixcorner.dss.prediction.AuditFollowUpLog;
import com.tixcorner.dss.prediction.PredictionResult;
import com.tixcorner.dss.prediction.PredictionRun;
import com.tixcorner.dss.source.Event;
import com.tixcorner.dss.source.Invoice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin Tixcorner — akses penuh (FR-02). Setiap method publik mewakili satu menu / use case.
 */
public class Admin extends DssUser {

    public Admin(int userId, String nama, String email, String username, String kataSandi) {
        super(userId, nama, email, username, "admin", kataSandi);
    }

    /** FR-11: impor berkas, validasi, bentuk label, lalu bagi periode 70/15/15. */
    public Dataset siapkanDataset(String berkas) {
        pastikanLogin();
        Dataset dataset = new Dataset(DssDatabase.nextId("dataset"), getUserId(), Path.of(berkas));
        if (!dataset.validasi()) {
            throw new IllegalArgumentException("Dataset tidak valid: " + dataset.getAlasanTolak());
        }
        dataset.bentukLabel();
        dataset.bagiPeriode(new double[]{0.70, 0.15, 0.15});
        DssDatabase.DATASETS.add(dataset);
        return dataset;
    }

    /** FR-12 + FR-13: latih kandidat lalu langsung evaluasi pada data uji. Status awal = eksperimen. */
    public ModelVersion latihModel(int datasetId, Map<String, String> parameter) {
        pastikanLogin();
        Dataset dataset = DssDatabase.cariDataset(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset " + datasetId + " tidak ditemukan"));
        if (!dataset.isLayakLatih()) {
            throw new IllegalStateException("Dataset belum layak dilatih (butuh kedua kelas di setiap bagian)");
        }
        ModelVersion model = new ModelVersion(DssDatabase.nextId("model"), dataset, parameter);
        model.latih();
        model.evaluasi();
        DssDatabase.MODELS.add(model);
        return model;
    }

    /** FR-13 langkah 4: setujui (jika lulus kriteria) lalu jadikan model aktif. */
    public boolean aktifkanModel(int modelId) {
        pastikanLogin();
        ModelVersion model = DssDatabase.cariModel(modelId)
                .orElseThrow(() -> new IllegalArgumentException("Model " + modelId + " tidak ditemukan"));
        try {
            if (ModelVersion.STATUS_EKSPERIMEN.equals(model.getStatus())) {
                model.setujui(getUserId());
            }
            return model.aktifkan();
        } catch (IllegalStateException e) {
            return false;   // tidak lulus kriteria -> status tetap eksperimen
        }
    }

    /** FR-05 + FR-06: prediksi seluruh invoice aktif pada event terpilih memakai model aktif. */
    public PredictionRun jalankanPrediksi(List<String> eventIds) {
        pastikanLogin();
        ModelVersion aktif = DssDatabase.cariModelAktif()
                .orElseThrow(() -> new IllegalStateException("Belum ada model aktif. Prediksi dihentikan."));
        List<Invoice> kandidat = new ArrayList<>();
        for (String eventId : eventIds) {
            DssDatabase.cariEvent(eventId).map(Event::getInvoiceAktif).ifPresent(kandidat::addAll);
        }
        PredictionRun run = new PredictionRun(DssDatabase.nextId("run"), aktif, getUserId(), kandidat);
        run.bentukFitur();
        run.jalankanInferensi();
        run.catatStatistik();
        DssDatabase.RUNS.add(run);
        DssDatabase.RESULTS.addAll(run.getHasil());
        return run;
    }

    /** FR-03 + FR-04: hasil prediksi tersimpan sesuai filter (event_id, risk_level, model_version). */
    public List<PredictionResult> lihatRingkasan(Map<String, String> filter) {
        pastikanLogin();
        return DssDatabase.RESULTS.stream()
                .filter(r -> cocok(filter.get("event_id"), r.getInvoice().getEventId()))
                .filter(r -> cocok(filter.get("risk_level"), r.getRiskLevel()))
                .filter(r -> cocok(filter.get("model_version"), r.getModelVersion()))
                .toList();
    }

    /** FR-07 + FR-08: detail satu hasil prediksi. */
    public PredictionResult lihatDetail(int resultId) {
        pastikanLogin();
        return DssDatabase.cariResult(resultId)
                .orElseThrow(() -> new IllegalArgumentException("Hasil prediksi " + resultId + " tidak ditemukan"));
    }

    /**
     * FR-09: hasil terbaru model aktif, kategori Sedang/Tinggi, invoice masih aktif.
     * Skor prioritas DIHITUNG ULANG setiap antrean dibuka; urutan seri: jatuh tempo terdekat, lalu ID invoice.
     */
    public List<PredictionResult> lihatAntreanPrioritas() {
        pastikanLogin();
        Map<String, PredictionResult> terbaruPerInvoice = new LinkedHashMap<>();
        for (PredictionResult r : DssDatabase.RESULTS) {
            if (r.isDariModelAktif()) {
                terbaruPerInvoice.put(r.getInvoiceId(), r);
            }
        }
        return terbaruPerInvoice.values().stream()
                .filter(r -> !ModelVersion.RENDAH.equals(r.getRiskLevel()))
                .filter(PredictionResult::isMasihAktif)
                .sorted(Comparator.comparingDouble(PredictionResult::getSkorPrioritasTerkini).reversed()
                        .thenComparing(r -> r.getInvoice().getDueAt())
                        .thenComparing(PredictionResult::getInvoiceId))
                .toList();
    }

    /** FR-10: simpan status + catatan investigasi ke DSS (status pembayaran sumber TIDAK diubah). */
    public AuditFollowUpLog catatTindakLanjut(int resultId, String status, String catatan) {
        pastikanLogin();
        lihatDetail(resultId); // memastikan result_id ada
        AuditFollowUpLog log = new AuditFollowUpLog(DssDatabase.nextId("log"), resultId, getUserId(), status, catatan);
        if (!log.simpan()) {
            throw new IllegalArgumentException("Status tindak lanjut tidak valid atau catatan > 500 karakter");
        }
        return log;
    }

    /**
     * FR-14: ekspor laporan ringkas sesuai filter aktif. Prototipe ini menulis format CSV;
     * PDF/Excel membutuhkan pustaka tambahan (OpenPDF / Apache POI) pada implementasi penuh.
     * Nomor kontak pembeli tidak pernah disertakan.
     */
    public String eksporLaporan(String format, Map<String, String> filter) {
        pastikanLogin();
        if (!"CSV".equalsIgnoreCase(format)) {
            throw new UnsupportedOperationException("Format " + format + " belum didukung di prototipe (gunakan CSV)");
        }
        List<PredictionResult> hasil = lihatRingkasan(filter);
        if (hasil.isEmpty()) {
            throw new IllegalStateException("Tidak ada data untuk diekspor");
        }
        Map<String, Integer> distribusi = new LinkedHashMap<>();
        for (PredictionResult r : hasil) {
            distribusi.merge(r.getRiskLevel(), 1, Integer::sum);
        }
        String dibuat = DssDatabase.sekarang().format(DssDatabase.FORMAT_WAKTU);
        StringBuilder sb = new StringBuilder();
        sb.append("# Laporan Ringkas Hasil Prediksi Risiko - Tixcorner DSS\n");
        sb.append("# Versi model,").append(hasil.get(0).getModelVersion()).append('\n');
        sb.append("# Dibuat,").append(dibuat).append(" oleh ").append(getUsername()).append('\n');
        sb.append("# Filter,").append(filter.isEmpty() ? "semua" : filter.toString().replace(',', ';')).append('\n');
        sb.append("# Distribusi,").append(distribusi.toString().replace(',', ';')).append('\n');
        sb.append("invoice_id,event_id,skor_gagal,kategori_risiko,waktu_observasi\n");
        for (PredictionResult r : hasil) {
            sb.append(r.getInvoiceId()).append(',')
                    .append(r.getInvoice().getEventId()).append(',')
                    .append(String.format(Locale.US, "%.3f", r.getFailureProbability())).append(',')
                    .append(r.getRiskLevel()).append(',')
                    .append(r.getObservedAt().format(DssDatabase.FORMAT_WAKTU)).append('\n');
        }
        try {
            Path folder = Files.createDirectories(Path.of("output"));
            String stempel = DssDatabase.sekarang().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
            Path berkas = folder.resolve("laporan_prediksi_" + stempel + ".csv");
            Files.writeString(berkas, sb.toString());
            return berkas.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Gagal menulis berkas laporan", e);
        }
    }

    private static boolean cocok(String nilaiFilter, String nilai) {
        return nilaiFilter == null || nilaiFilter.isBlank() || nilaiFilter.equalsIgnoreCase(nilai);
    }
}
