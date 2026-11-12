package com.tixcorner.dss.user;

import com.tixcorner.dss.db.DssDatabase;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * «abstract» DssUser — induk Admin dan PengelolaEvent (satu tabel DSS_USERS, dibedakan kolom role).
 * Mengimplementasikan FR-01: login, logout, dan penguncian akun 15 menit setelah 5x gagal.
 */
public abstract class DssUser {

    private static final int MAKS_PERCOBAAN_GAGAL = 5;
    private static final int MENIT_KUNCI = 15;
    private static final int PANJANG_MIN_SANDI = 8;

    private final int userId;
    private String nama;
    private String email;
    private String username;
    private final String role;
    private String passwordHash;
    private boolean statusAktif;
    private int failedLoginCount;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;

    /** Penanda sesi di memori (bukan kolom tabel). */
    private boolean sesiAktif;

    protected DssUser(int userId, String nama, String email, String username, String role, String kataSandiAwal) {
        if (kataSandiAwal == null || kataSandiAwal.length() < PANJANG_MIN_SANDI) {
            throw new IllegalArgumentException("Kata sandi minimal " + PANJANG_MIN_SANDI + " karakter");
        }
        this.userId = userId;
        this.nama = nama;
        this.email = email;
        this.username = username;
        this.role = role;
        this.passwordHash = buatHash(kataSandiAwal);
        this.statusAktif = true;
    }

    /** FR-01: verifikasi kredensial. Akun terkunci ditolak SEBELUM kata sandi diperiksa. */
    public boolean login(String emailAtauUsername, String kataSandi) {
        boolean identitasCocok = emailAtauUsername != null
                && (emailAtauUsername.equalsIgnoreCase(email) || emailAtauUsername.equalsIgnoreCase(username));
        if (!identitasCocok || isTerkunci() || !statusAktif) {
            return false;
        }
        if (kataSandi == null || kataSandi.length() < PANJANG_MIN_SANDI || !cocokKataSandi(kataSandi)) {
            catatGagalLogin();
            return false;
        }
        failedLoginCount = 0;
        lockedUntil = null;
        lastLoginAt = DssDatabase.sekarang();
        sesiAktif = true;
        return true;
    }

    public void logout() {
        sesiAktif = false;
    }

    public boolean isTerkunci() {
        return lockedUntil != null && DssDatabase.sekarang().isBefore(lockedUntil);
    }

    /** Menambah penghitung gagal; pada percobaan ke-5 akun dikunci 15 menit. */
    protected void catatGagalLogin() {
        failedLoginCount++;
        if (failedLoginCount >= MAKS_PERCOBAAN_GAGAL) {
            lockedUntil = DssDatabase.sekarang().plusMinutes(MENIT_KUNCI);
            failedLoginCount = 0;
        }
    }

    /** Dipanggil method turunan sebelum menjalankan fungsi yang butuh sesi aktif. */
    protected void pastikanLogin() {
        if (!sesiAktif) {
            throw new IllegalStateException("Sesi tidak aktif. Silakan login terlebih dahulu.");
        }
    }

    // ---------- hashing kata sandi (PBKDF2; produksi memakai bcrypt/Argon2id sesuai NFR-07) ----------
    private static String buatHash(String kataSandi) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return "pbkdf2$" + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(pbkdf2(kataSandi, salt));
    }

    private boolean cocokKataSandi(String kataSandi) {
        String[] bagian = passwordHash.split("\\$");
        byte[] salt = Base64.getDecoder().decode(bagian[1]);
        byte[] tersimpan = Base64.getDecoder().decode(bagian[2]);
        return MessageDigest.isEqual(tersimpan, pbkdf2(kataSandi, salt));
    }

    private static byte[] pbkdf2(String kataSandi, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(kataSandi.toCharArray(), salt, 65_536, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Algoritma hash tidak tersedia", e);
        }
    }

    // ---------- getter ----------
    public int getUserId() {
        return userId;
    }

    public String getNama() {
        return nama;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public boolean isStatusAktif() {
        return statusAktif;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public boolean isSesiAktif() {
        return sesiAktif;
    }
}
