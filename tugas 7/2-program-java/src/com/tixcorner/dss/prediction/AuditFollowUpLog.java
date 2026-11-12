package com.tixcorner.dss.prediction;

import com.tixcorner.dss.db.DssDatabase;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Catatan tindak lanjut Admin atas satu hasil prediksi (FR-10).
 * Disimpan di DSS; status pembayaran pada sumber Tixcorner TIDAK diubah.
 */
public class AuditFollowUpLog {

    public static final List<String> STATUS_VALID = List.of(
            "Belum Dihubungi",
            "Sudah Dihubungi - Janji Bayar",
            "Kontak Tidak Valid",
            "Kendala Teknis Pembeli",
            "Selesai Diverifikasi");
    public static final int MAKS_CATATAN = 500;

    private final int logId;
    private final int resultId;
    private final int adminId;
    private final String followUpStatus;
    private String adminNotes;
    private final LocalDateTime loggedAt;

    public AuditFollowUpLog(int logId, int resultId, int adminId, String followUpStatus, String adminNotes) {
        this.logId = logId;
        this.resultId = resultId;
        this.adminId = adminId;
        this.followUpStatus = followUpStatus;
        this.adminNotes = adminNotes;
        this.loggedAt = DssDatabase.sekarang();
    }

    /** Sanitasi XSS sederhana: escape karakter HTML berbahaya. */
    public String sanitasiCatatan() {
        if (adminNotes == null) {
            return "";
        }
        return adminNotes.trim()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    /** Validasi status (5 pilihan) dan panjang catatan (maks 500), sanitasi, lalu simpan. */
    public boolean simpan() {
        if (!STATUS_VALID.contains(followUpStatus)) {
            return false;
        }
        if (adminNotes != null && adminNotes.trim().length() > MAKS_CATATAN) {
            return false;
        }
        adminNotes = sanitasiCatatan();
        DssDatabase.LOGS.add(this);
        return true;
    }

    // ---------- getter ----------
    public int getLogId() {
        return logId;
    }

    public int getResultId() {
        return resultId;
    }

    public int getAdminId() {
        return adminId;
    }

    public String getFollowUpStatus() {
        return followUpStatus;
    }

    public String getAdminNotes() {
        return adminNotes;
    }

    public LocalDateTime getLoggedAt() {
        return loggedAt;
    }
}
