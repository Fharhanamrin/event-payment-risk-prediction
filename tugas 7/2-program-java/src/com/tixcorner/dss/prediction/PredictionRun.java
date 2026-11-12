package com.tixcorner.dss.prediction;

import com.tixcorner.dss.db.DssDatabase;
import com.tixcorner.dss.ml.ModelVersion;
import com.tixcorner.dss.source.Invoice;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Satu kali eksekusi prediksi batch (FR-05 + FR-06). Komposisi: memiliki 0..* PredictionResult.
 */
public class PredictionRun {

    private final int runId;
    private final int modelId;
    private final int executedBy;
    private final LocalDateTime executedAt;
    private int totalProcessed;
    private int totalSkipped;
    private int totalErrors;

    // data kerja di memori
    private final ModelVersion model;
    private final List<Invoice> kandidat;
    private final List<Invoice> invoiceValid = new ArrayList<>();
    private final List<Map<String, Object>> matriksFitur = new ArrayList<>();
    private final Map<String, String> dilewati = new LinkedHashMap<>();
    private final List<PredictionResult> hasil = new ArrayList<>();
    private boolean fiturSiap;

    public PredictionRun(int runId, ModelVersion model, int executedBy, List<Invoice> kandidat) {
        this.runId = runId;
        this.model = model;
        this.modelId = model.getModelId();
        this.executedBy = executedBy;
        this.executedAt = DssDatabase.sekarang();
        this.kandidat = kandidat;
    }

    /** FR-05: validasi kandidat lalu bentuk snapshot fitur; yang tidak valid dicatat dan dilewati. */
    public void bentukFitur() {
        if (!ModelVersion.STATUS_AKTIF.equals(model.getStatus())) {
            throw new IllegalStateException("Model " + model.getModelVersion() + " bukan model aktif. Prediksi dihentikan.");
        }
        invoiceValid.clear();
        matriksFitur.clear();
        dilewati.clear();
        for (Invoice inv : kandidat) {
            String alasan = alasanTidakValid(inv);
            if (alasan != null) {
                dilewati.put(inv.getInvoiceId(), alasan);
                continue;
            }
            invoiceValid.add(inv);
            matriksFitur.add(inv.toFitur(executedAt));
        }
        fiturSiap = true;
    }

    /** FR-06: skor + kategori + jalur untuk tiap invoice valid. Error dihitung, BUKAN dianggap "Rendah". */
    public List<PredictionResult> jalankanInferensi() {
        if (!fiturSiap) {
            bentukFitur();
        }
        hasil.clear();
        totalErrors = 0;
        for (int i = 0; i < invoiceValid.size(); i++) {
            try {
                Map<String, Object> fitur = matriksFitur.get(i);
                float skor = model.prediksiProba(fitur);
                hasil.add(new PredictionResult(DssDatabase.nextId("result"), this, invoiceValid.get(i), executedAt,
                        fitur, skor, model.petakanKategori(skor), model.telusuriJalur(fitur)));
            } catch (RuntimeException e) {
                totalErrors++;
            }
        }
        return Collections.unmodifiableList(hasil);
    }

    public void catatStatistik() {
        totalProcessed = hasil.size();
        totalSkipped = dilewati.size();
    }

    private String alasanTidakValid(Invoice inv) {
        if (inv.getInvoiceId() == null || inv.getInvoiceId().isBlank()) return "ID invoice kosong";
        if (!inv.isBelumLunas()) return "Status bukan pending/partially_paid";
        if (inv.getNominalTagihan() <= 0) return "Nominal tagihan tidak positif";
        if (inv.getNominalTerbayar() < 0 || inv.getNominalTerbayar() >= inv.getNominalTagihan()) return "Nominal terbayar tidak sah";
        if (!inv.getDueAt().isAfter(executedAt)) return "Sudah melewati jatuh tempo";
        return null;
    }

    // ---------- getter ----------
    public int getRunId() {
        return runId;
    }

    public int getModelId() {
        return modelId;
    }

    public ModelVersion getModel() {
        return model;
    }

    public int getExecutedBy() {
        return executedBy;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public int getTotalProcessed() {
        return totalProcessed;
    }

    public int getTotalSkipped() {
        return totalSkipped;
    }

    public int getTotalErrors() {
        return totalErrors;
    }

    public Map<String, String> getDilewati() {
        return Collections.unmodifiableMap(dilewati);
    }

    public List<PredictionResult> getHasil() {
        return Collections.unmodifiableList(hasil);
    }
}
