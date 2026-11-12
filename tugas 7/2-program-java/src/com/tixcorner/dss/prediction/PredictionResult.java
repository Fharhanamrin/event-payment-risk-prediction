package com.tixcorner.dss.prediction;

import com.tixcorner.dss.db.DssDatabase;
import com.tixcorner.dss.ml.ModelVersion;
import com.tixcorner.dss.source.Invoice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Hasil prediksi satu invoice (FR-06, FR-07, FR-08, FR-09). Tidak berdiri sendiri tanpa PredictionRun.
 */
public class PredictionResult {

    public static final double W_RISK = 0.6;
    public static final double W_TIME = 0.4;
    private static final String PANAH = " → ";

    private final int resultId;
    private final int runId;
    private final String invoiceId;
    private final LocalDateTime observedAt;
    private final String featureSnapshot;
    private final float failureProbability;
    private final String riskLevel;
    private final String decisionPath;
    private final float priorityScore;   // snapshot saat prediksi (audit), bukan nilai antrean terkini

    // referensi di memori
    private final PredictionRun run;
    private final Invoice invoice;

    public PredictionResult(int resultId, PredictionRun run, Invoice invoice, LocalDateTime observedAt,
                            Map<String, Object> fitur, float failureProbability, String riskLevel,
                            List<String> jalur) {
        this.resultId = resultId;
        this.run = run;
        this.runId = run.getRunId();
        this.invoice = invoice;
        this.invoiceId = invoice.getInvoiceId();
        this.observedAt = observedAt;
        this.featureSnapshot = keJson(fitur);
        this.failureProbability = failureProbability;
        this.riskLevel = riskLevel;
        this.decisionPath = String.join(PANAH, jalur);
        this.priorityScore = hitungSkorPrioritas(invoice.getSisaJamJatuhTempo(observedAt), invoice.getDurasiJam());
    }

    public List<String> getJalurKeputusan() {
        return List.of(decisionPath.split(PANAH));
    }

    /**
     * FR-09: Score = 0,6 x P(gagal) + 0,4 x S_time, dengan S_time = max(0, 1 - sisaJam / totalJam) dibatasi 0..1.
     */
    public float hitungSkorPrioritas(float sisaJam, float totalJam) {
        if (totalJam <= 0) {
            throw new IllegalArgumentException("Durasi invoice harus positif");
        }
        double sTime = Math.max(0, Math.min(1, 1 - sisaJam / totalJam));
        return (float) (W_RISK * failureProbability + W_TIME * sTime);
    }

    /** Masih aktif bila invoice belum lunas dan belum jatuh tempo pada waktu sekarang. */
    public boolean isMasihAktif() {
        return invoice.isAktif(DssDatabase.sekarang());
    }

    // ---------- method bantu ----------
    /** Skor prioritas dihitung ulang memakai sisa waktu SAAT INI (dipakai antrean). */
    public float getSkorPrioritasTerkini() {
        return hitungSkorPrioritas(invoice.getSisaJamJatuhTempo(), invoice.getDurasiJam());
    }

    public boolean isDariModelAktif() {
        return ModelVersion.STATUS_AKTIF.equals(run.getModel().getStatus());
    }

    public String getModelVersion() {
        return run.getModel().getModelVersion();
    }

    private static String keJson(Map<String, Object> fitur) {
        return fitur.entrySet().stream().map(e -> {
            Object v = e.getValue();
            String teks = v instanceof String ? "\"" + v + "\""
                    : v instanceof Double d ? String.format(Locale.US, "%.2f", d) : String.valueOf(v);
            return "\"" + e.getKey() + "\": " + teks;
        }).collect(Collectors.joining(", ", "{", "}"));
    }

    // ---------- getter ----------
    public int getResultId() {
        return resultId;
    }

    public int getRunId() {
        return runId;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public LocalDateTime getObservedAt() {
        return observedAt;
    }

    public String getFeatureSnapshot() {
        return featureSnapshot;
    }

    public float getFailureProbability() {
        return failureProbability;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public String getDecisionPath() {
        return decisionPath;
    }

    public float getPriorityScore() {
        return priorityScore;
    }
}
