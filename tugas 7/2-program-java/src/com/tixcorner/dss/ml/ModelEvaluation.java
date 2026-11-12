package com.tixcorner.dss.ml;

import java.util.Locale;

/**
 * Hasil evaluasi satu versi model pada data uji (FR-13). Kelas positif = gagal (1).
 * Bagian komposisi dari ModelVersion (tidak berdiri sendiri).
 */
public class ModelEvaluation {

    /** Contoh kriteria penerimaan (TBD-03) — ditetapkan sebelum uji akhir. */
    public static final double MIN_RECALL = 0.60;
    public static final double MIN_F1 = 0.60;

    private final int evaluationId;
    private final int modelId;
    private final String testPeriod;
    private String classSupport;
    private String confusionMatrix;
    private final double binaryThreshold;
    private double accuracy;
    private double precisionFailed;
    private double recallFailed;
    private double f1Failed;
    private String baselineMetrics;
    private final String acceptanceCriteria;

    // data perhitungan (bukan kolom tabel)
    private final int[] yTrue;
    private final int[] yPred;
    private final int kelasMayoritas;
    private int tp;
    private int fp;
    private int tn;
    private int fn;
    private double baselineAccuracy;
    private double baselineRecall;
    private double baselineF1;

    public ModelEvaluation(int evaluationId, int modelId, String testPeriod, int[] yTrue, int[] yPred,
                           double binaryThreshold, int kelasMayoritas) {
        this.evaluationId = evaluationId;
        this.modelId = modelId;
        this.testPeriod = testPeriod;
        this.yTrue = yTrue;
        this.yPred = yPred;
        this.binaryThreshold = binaryThreshold;
        this.kelasMayoritas = kelasMayoritas;
        this.acceptanceCriteria = String.format(Locale.US,
                "recall >= %.2f; F1 >= %.2f; recall & F1 > baseline (contoh TBD-03)", MIN_RECALL, MIN_F1);
    }

    /** Menghitung confusion matrix, accuracy, precision, recall, F1 kelas gagal, dan baseline. */
    public void hitungMetrik() {
        tp = fp = tn = fn = 0;
        for (int i = 0; i < yTrue.length; i++) {
            if (yTrue[i] == 1 && yPred[i] == 1) tp++;
            else if (yTrue[i] == 0 && yPred[i] == 1) fp++;
            else if (yTrue[i] == 0) tn++;
            else fn++;
        }
        int n = yTrue.length;
        accuracy = n == 0 ? Double.NaN : (double) (tp + tn) / n;
        precisionFailed = tp + fp == 0 ? Double.NaN : (double) tp / (tp + fp);   // NaN = tidak terdefinisi
        recallFailed = tp + fn == 0 ? Double.NaN : (double) tp / (tp + fn);
        f1Failed = f1(precisionFailed, recallFailed);
        classSupport = "gagal=" + (tp + fn) + "; berhasil=" + (tn + fp);
        confusionMatrix = "TP=" + tp + ", FP=" + fp + ", TN=" + tn + ", FN=" + fn;

        // baseline: selalu menebak kelas mayoritas data latih
        int positifAktual = tp + fn;
        if (kelasMayoritas == 1) {
            baselineAccuracy = (double) positifAktual / n;
            baselineRecall = 1.0;
            baselineF1 = f1((double) positifAktual / n, 1.0);
        } else {
            baselineAccuracy = (double) (n - positifAktual) / n;
            baselineRecall = 0.0;
            baselineF1 = 0.0;
        }
        baselineMetrics = String.format(Locale.US, "mayoritas (kelas %d): acc=%.3f, recall=%.3f, F1=%.3f",
                kelasMayoritas, baselineAccuracy, baselineRecall, baselineF1);
    }

    /** Model harus lebih baik dari baseline pada recall dan F1 kelas gagal. */
    public boolean bandingkanBaseline() {
        return !Double.isNaN(f1Failed) && f1Failed > baselineF1 && recallFailed > baselineRecall;
    }

    /** Lulus bila kedua kelas ada, metrik terdefinisi, memenuhi minimum, dan mengalahkan baseline. */
    public boolean isLulus() {
        boolean duaKelas = (tp + fn) > 0 && (tn + fp) > 0;
        return duaKelas && !Double.isNaN(recallFailed) && !Double.isNaN(f1Failed)
                && recallFailed >= MIN_RECALL && f1Failed >= MIN_F1 && bandingkanBaseline();
    }

    private static double f1(double p, double r) {
        if (Double.isNaN(p) || Double.isNaN(r) || p + r == 0) {
            return Double.isNaN(p) || Double.isNaN(r) ? Double.NaN : 0.0;
        }
        return 2 * p * r / (p + r);
    }

    // ---------- getter ----------
    public int getEvaluationId() {
        return evaluationId;
    }

    public int getModelId() {
        return modelId;
    }

    public String getTestPeriod() {
        return testPeriod;
    }

    public String getClassSupport() {
        return classSupport;
    }

    public String getConfusionMatrix() {
        return confusionMatrix;
    }

    public double getBinaryThreshold() {
        return binaryThreshold;
    }

    public double getAccuracy() {
        return accuracy;
    }

    public double getPrecisionFailed() {
        return precisionFailed;
    }

    public double getRecallFailed() {
        return recallFailed;
    }

    public double getF1Failed() {
        return f1Failed;
    }

    public String getBaselineMetrics() {
        return baselineMetrics;
    }

    public String getAcceptanceCriteria() {
        return acceptanceCriteria;
    }

    public int[] getConfusion() {
        return new int[]{tp, fp, tn, fn};
    }
}
