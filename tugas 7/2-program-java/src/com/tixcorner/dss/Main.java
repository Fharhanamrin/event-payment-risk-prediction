package com.tixcorner.dss;

import com.tixcorner.dss.db.DssDatabase;
import com.tixcorner.dss.ml.Dataset;
import com.tixcorner.dss.ml.ModelEvaluation;
import com.tixcorner.dss.ml.ModelVersion;
import com.tixcorner.dss.prediction.AuditFollowUpLog;
import com.tixcorner.dss.prediction.PredictionResult;
import com.tixcorner.dss.prediction.PredictionRun;
import com.tixcorner.dss.source.Event;
import com.tixcorner.dss.source.Invoice;
import com.tixcorner.dss.user.Admin;
import com.tixcorner.dss.user.DssUser;
import com.tixcorner.dss.user.PengelolaEvent;
import com.tixcorner.dss.user.UserEvent;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Demo end-to-end Sistem Prediksi Risiko Kegagalan Pembayaran Tiket Event (Tixcorner DSS).
 * Urutan langkah mengikuti 6 page desain Figma.
 */
public class Main {

    private static final Locale ID = Locale.forLanguageTag("id-ID");

    public static void main(String[] args) throws Exception {
        Path data = Path.of(args.length > 0 ? args[0] : "data");

        // Waktu dibekukan sama dengan object diagram tugas 6 agar hasil selalu sama.
        DssDatabase.setWaktuSekarang(LocalDateTime.of(2026, 10, 15, 15, 0));
        DssDatabase.muatSumberTixcorner(data.resolve("event.csv"), data.resolve("invoice_aktif.csv"));

        Admin rina = new Admin(1, "Rina Kartika", "rina.admin@tixcorner.com", "rina.admin", "Admin#2026");
        PengelolaEvent bagas = new PengelolaEvent(2, "Bagas Pratama", "bagas@senjaraya.example", "bagas.eo", "Senja#2026");
        PengelolaEvent dimas = new PengelolaEvent(3, "Dimas Saputra", "dimas@kuliner.example", "dimas.eo", "Kuliner#2026");
        DssDatabase.USERS.addAll(List.of(rina, bagas, dimas));
        DssDatabase.USER_EVENTS.add(new UserEvent(2, "EVT-2026-018"));
        DssDatabase.USER_EVENTS.add(new UserEvent(3, "EVT-2026-021"));

        // ================= PAGE 1: LOGIN =================
        judul("PAGE 1 · LOGIN  (FR-01, FR-02)");
        masuk("rina.admin", "salahsandi");
        for (int i = 1; i <= 5; i++) {
            DssDatabase.cariUser("dimas.eo").get().login("dimas.eo", "tebak" + i + "xyz");
        }
        System.out.println("  dimas.eo salah 5x → terkunci sampai " + fmt(dimas.getLockedUntil()));
        masuk("dimas.eo", "Kuliner#2026");
        masuk("rina.admin@tixcorner.com", "Admin#2026");

        // ================= PAGE 3: DATASET & MODEL =================
        judul("PAGE 3 · DATASET & MODEL  (FR-11, FR-12, FR-13)");
        Dataset ds = rina.siapkanDataset(data.resolve("dataset_historis.csv").toString());
        int[] kelas = Dataset.jumlahKelas(ds.getSemuaBaris());
        System.out.printf("  Dataset #%d %s | %s...%n", ds.getDatasetId(), ds.getVersion(), ds.getChecksum().substring(0, 19));
        System.out.printf("  Baris mentah %d → valid %d (gagal %d / berhasil %d)%n",
                ds.getJumlahBarisMentah(), ds.getJumlahBarisValid(), kelas[1], kelas[0]);
        ds.getAlasanTolak().forEach((alasan, n) -> System.out.printf("    ditolak %-42s : %d%n", alasan, n));
        System.out.println("  Split  : " + ds.getSplitPeriods());
        System.out.println("  Layak latih? " + ds.isLayakLatih());

        Map<String, String> param = new LinkedHashMap<>();
        param.put("criterion", "gini");
        param.put("max_depth", "5");
        param.put("min_samples_leaf", "20");
        param.put("random_state", "42");
        ModelVersion mv = rina.latihModel(ds.getDatasetId(), param);
        ModelEvaluation ev = mv.getEvaluasiTerakhir();
        int[] cm = ev.getConfusion();
        System.out.printf("%n  Model #%d %s (%s) → status %s%n", mv.getModelId(), mv.getModelVersion(), mv.getParameters(), mv.getStatus());
        System.out.printf("  Evaluasi #%d uji %s | %s%n", ev.getEvaluationId(), ev.getTestPeriod(), ev.getClassSupport());
        System.out.printf("  Confusion matrix   : TP=%d  FP=%d  TN=%d  FN=%d%n", cm[0], cm[1], cm[2], cm[3]);
        System.out.printf(Locale.US, "  Accuracy %.3f | Precision %.3f | Recall %.3f | F1 %.3f%n",
                ev.getAccuracy(), ev.getPrecisionFailed(), ev.getRecallFailed(), ev.getF1Failed());
        System.out.println("  Baseline           : " + ev.getBaselineMetrics());
        System.out.println("  Kriteria           : " + ev.getAcceptanceCriteria());
        System.out.println("  Lulus? " + ev.isLulus() + " | lebih baik dari baseline? " + ev.bandingkanBaseline());
        boolean aktif = rina.aktifkanModel(mv.getModelId());
        System.out.printf("  Aktivasi → %s, status %s, disetujui oleh user #%d pada %s%n",
                aktif ? "BERHASIL" : "DITOLAK", mv.getStatus(), mv.getApprovedBy(), fmt(mv.getApprovedAt()));
        System.out.println("\n  Struktur pohon " + mv.getModelVersion() + ":");
        System.out.print(mv.cetakPohon().indent(4));

        // ================= PAGE 4: JALANKAN PREDIKSI =================
        judul("PAGE 4 · JALANKAN PREDIKSI  (FR-05, FR-06)");
        PredictionRun run = rina.jalankanPrediksi(List.of("EVT-2026-018", "EVT-2026-021", "EVT-2026-025"));
        System.out.printf("  Run #%d | model %s | waktu observasi %s%n", run.getRunId(), run.getModel().getModelVersion(), fmt(run.getExecutedAt()));
        System.out.printf("  Berhasil diproses %d | dilewati %d | gagal diproses %d%n",
                run.getTotalProcessed(), run.getTotalSkipped(), run.getTotalErrors());
        run.getDilewati().forEach((id, alasan) -> System.out.println("    dilewati " + id + " : " + alasan));
        System.out.println("\n  10 skor tertinggi:");
        System.out.printf("  %-6s %-15s %-13s %-6s %-7s %s%n", "ID", "Invoice", "Event", "Skor", "Risiko", "Sisa jam");
        run.getHasil().stream()
                .sorted(Comparator.comparingDouble(PredictionResult::getFailureProbability).reversed()
                        .thenComparing(PredictionResult::getInvoiceId))
                .limit(10)
                .forEach(r -> System.out.printf(Locale.US, "  %-6d %-15s %-13s %-6.2f %-7s %.1f%n", r.getResultId(),
                        r.getInvoiceId(), r.getInvoice().getEventId(), r.getFailureProbability(), r.getRiskLevel(),
                        r.getInvoice().getSisaJamJatuhTempo()));

        // ================= PAGE 2: DASHBOARD RINGKASAN =================
        judul("PAGE 2 · DASHBOARD RINGKASAN  (FR-03, FR-04, FR-14)");
        List<PredictionResult> semua = rina.lihatRingkasan(Map.of());
        System.out.println("  Total invoice diprediksi : " + semua.size());
        cetakDistribusi("  Semua event", semua);
        for (Event e : DssDatabase.events()) {
            cetakDistribusi("  " + e.getEventId() + " " + e.getNamaEvent(), rina.lihatRingkasan(Map.of("event_id", e.getEventId())));
        }
        long pending = semua.stream().filter(r -> Invoice.PENDING.equals(r.getInvoice().getStatusPembayaran())).count();
        System.out.printf("  Status aktual (konteks, bukan prediksi): pending %d | partially_paid %d | gagal aktual 0%n",
                pending, semua.size() - pending);
        Map<String, String> filter = new LinkedHashMap<>();
        filter.put("event_id", "EVT-2026-018");
        filter.put("risk_level", "Tinggi");
        System.out.println("  Filter " + filter + " → " + rina.lihatRingkasan(filter).size() + " invoice");
        System.out.println("  Ekspor CSV → " + rina.eksporLaporan("CSV", Map.of()));
        coba(() -> rina.eksporLaporan("PDF", Map.of()));
        coba(() -> rina.eksporLaporan("CSV", Map.of("event_id", "EVT-2099-000")));

        // ================= PAGE 5: DETAIL & JALUR KEPUTUSAN =================
        judul("PAGE 5 · DETAIL PREDIKSI & JALUR KEPUTUSAN  (FR-07, FR-08)");
        int idDetail = cariResult(semua, "INV-2026-00871").getResultId();
        PredictionResult d = rina.lihatDetail(idDetail);
        Invoice inv = d.getInvoice();
        System.out.printf("  Result #%d | %s | %s | run #%d | model %s%n", d.getResultId(), d.getInvoiceId(),
                inv.getEventId(), d.getRunId(), d.getModelVersion());
        System.out.printf("  Tagihan %s | terbayar %s (%.0f%%) | cicilan %dx | %s | promo %s%n",
                rupiah(inv.getNominalTagihan()), rupiah(inv.getNominalTerbayar()), inv.getPersentaseTerbayar() * 100,
                inv.getJumlahCicilan(), inv.getMetodePembayaran(), inv.isPenggunaanPromo() ? "ya" : "tidak");
        System.out.printf("  Dibuat %s | jatuh tempo %s | sisa %.1f jam%n", fmt(inv.getCreatedAt()), fmt(inv.getDueAt()), inv.getSisaJamJatuhTempo());
        System.out.println("  Snapshot fitur : " + d.getFeatureSnapshot());
        System.out.printf(Locale.US, "  Skor P(gagal) = %.2f → kategori %s (ambang %s)%n",
                d.getFailureProbability(), d.getRiskLevel(), run.getModel().getRiskThresholds());
        System.out.println("  Jalur keputusan:");
        List<String> jalur = d.getJalurKeputusan();
        for (int i = 0; i < jalur.size(); i++) {
            System.out.println("    " + (i + 1) + ". " + jalur.get(i));
        }
        System.out.println("  Status aktual saat ini (terpisah): " + inv.getStatusPembayaran()
                + (d.isMasihAktif() ? " — hasil masih berlaku" : " — HASIL LAMA"));
        PredictionResult d2 = rina.lihatDetail(cariResult(semua, "INV-2026-00872").getResultId());
        System.out.printf(Locale.US, "  Pembanding #%d %s: P(gagal)=%.2f → %s | %s%n", d2.getResultId(), d2.getInvoiceId(),
                d2.getFailureProbability(), d2.getRiskLevel(), d2.getDecisionPath());

        // ================= PAGE 6: ANTREAN PRIORITAS =================
        judul("PAGE 6 · ANTREAN PRIORITAS TINDAK LANJUT  (FR-09, FR-10)");
        DssDatabase.setWaktuSekarang(LocalDateTime.of(2026, 10, 15, 16, 30));   // antrean dibuka 1,5 jam kemudian
        AuditFollowUpLog log = rina.catatTindakLanjut(idDetail, "Sudah Dihubungi - Janji Bayar",
                "Pembeli janji melunasi sisa tagihan sebelum 16-10-2026 08:00");
        System.out.printf("  Log #%d tersimpan: result #%d → \"%s\"%n", log.getLogId(), log.getResultId(), log.getFollowUpStatus());
        System.out.println("  Catatan tindak lanjut berhasil diperbarui.");
        List<PredictionResult> antrean = rina.lihatAntreanPrioritas();
        AuditFollowUpLog xss = rina.catatTindakLanjut(antrean.get(1).getResultId(), "Kontak Tidak Valid",
                "<script>alert('x')</script> nomor tidak aktif");
        System.out.println("  Catatan disanitasi : " + xss.getAdminNotes());
        coba(() -> rina.catatTindakLanjut(idDetail, "Sudah Lunas", "status tidak ada di daftar"));
        System.out.printf("%n  Antrean dibuka %s — %d invoice Sedang/Tinggi%n", fmt(DssDatabase.sekarang()), antrean.size());
        System.out.printf("  %-4s %-15s %-7s %-6s %-9s %-9s %-8s %s%n", "No", "Invoice", "Risiko", "P", "Skor@pred",
                "Skor kini", "Sisa jam", "Tindak lanjut");
        for (int i = 0; i < Math.min(10, antrean.size()); i++) {
            PredictionResult r = antrean.get(i);
            System.out.printf(Locale.US, "  %-4d %-15s %-7s %-6.2f %-9.3f %-9.3f %-8.1f %s%n", i + 1, r.getInvoiceId(),
                    r.getRiskLevel(), r.getFailureProbability(), r.getPriorityScore(), r.getSkorPrioritasTerkini(),
                    r.getInvoice().getSisaJamJatuhTempo(), DssDatabase.statusTindakLanjutTerakhir(r.getResultId()));
        }

        // ================= VARIAN PAGE 2: PENGELOLA EVENT =================
        judul("VARIAN PAGE 2 · DASHBOARD PENGELOLA EVENT  (FR-02, NFR-16)");
        masuk("bagas.eo", "Senja#2026");
        System.out.println("  Event diizinkan : " + bagas.getEventDiizinkan());
        System.out.println("  Agregat risiko  : " + bagas.lihatAgregatRisiko(Map.of()));
        coba(() -> bagas.lihatAgregatRisiko(Map.of("event_id", "EVT-2026-021")));

        bagas.logout();
        rina.logout();
        System.out.println("\n  Logout rina & bagas.");
        coba(rina::lihatAntreanPrioritas);
    }

    // ---------------- helper tampilan ----------------
    private static void masuk(String emailAtauUsername, String sandi) {
        DssUser user = DssDatabase.cariUser(emailAtauUsername).orElse(null);
        if (user != null && user.login(emailAtauUsername, sandi)) {
            String tujuan = user instanceof Admin ? "/dashboard (ringkasan Admin)" : "/dashboard (agregat event sendiri)";
            System.out.printf("  %-26s → login berhasil, diarahkan ke %s%n", emailAtauUsername, tujuan);
        } else if (user != null && user.isTerkunci()) {
            System.out.printf("  %-26s → DITOLAK: akun terkunci sementara sampai %s%n", emailAtauUsername, fmt(user.getLockedUntil()));
        } else {
            System.out.printf("  %-26s → Email atau kata sandi yang Anda masukkan salah%n", emailAtauUsername);
        }
    }

    private static void cetakDistribusi(String label, List<PredictionResult> hasil) {
        Map<String, Integer> n = new LinkedHashMap<>(Map.of(ModelVersion.RENDAH, 0, ModelVersion.SEDANG, 0, ModelVersion.TINGGI, 0));
        hasil.forEach(r -> n.merge(r.getRiskLevel(), 1, Integer::sum));
        double t = Math.max(1, hasil.size());
        System.out.printf(Locale.US, "%-48s Rendah %3d (%4.1f%%) | Sedang %3d (%4.1f%%) | Tinggi %3d (%4.1f%%)%n", label,
                n.get("Rendah"), 100 * n.get("Rendah") / t, n.get("Sedang"), 100 * n.get("Sedang") / t,
                n.get("Tinggi"), 100 * n.get("Tinggi") / t);
    }

    private static PredictionResult cariResult(List<PredictionResult> hasil, String invoiceId) {
        return hasil.stream().filter(r -> r.getInvoiceId().equals(invoiceId)).findFirst().orElseThrow();
    }

    private static void coba(Runnable aksi) {
        try {
            aksi.run();
        } catch (RuntimeException e) {
            System.out.println("  [ditolak] " + e.getMessage());
        }
    }

    private static void judul(String teks) {
        System.out.println("\n" + "=".repeat(78) + "\n " + teks + "\n" + "=".repeat(78));
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? "-" : t.format(DssDatabase.FORMAT_WAKTU);
    }

    private static String rupiah(double v) {
        return String.format(ID, "Rp%,.0f", v);
    }
}
