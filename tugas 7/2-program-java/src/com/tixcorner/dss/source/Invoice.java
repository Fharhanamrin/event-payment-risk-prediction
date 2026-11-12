package com.tixcorner.dss.source;

import com.tixcorner.dss.db.DssDatabase;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * «read-only» Tabel sumber Tixcorner: invoices.
 * Semua atribut final dan tidak ada setter karena DSS hanya boleh membaca (NFR-17).
 */
public final class Invoice {

    public static final String PENDING = "pending";
    public static final String PARTIALLY_PAID = "partially_paid";

    private final String invoiceId;
    private final String eventId;
    private final double nominalTagihan;
    private final double nominalTerbayar;
    private final String statusPembayaran;
    private final int jumlahCicilan;
    private final String metodePembayaran;
    private final boolean penggunaanPromo;
    private final LocalDateTime createdAt;
    private final LocalDateTime dueAt;

    public Invoice(String invoiceId, String eventId, double nominalTagihan, double nominalTerbayar,
                   String statusPembayaran, int jumlahCicilan, String metodePembayaran,
                   boolean penggunaanPromo, LocalDateTime createdAt, LocalDateTime dueAt) {
        this.invoiceId = invoiceId;
        this.eventId = eventId;
        this.nominalTagihan = nominalTagihan;
        this.nominalTerbayar = nominalTerbayar;
        this.statusPembayaran = statusPembayaran;
        this.jumlahCicilan = jumlahCicilan;
        this.metodePembayaran = metodePembayaran;
        this.penggunaanPromo = penggunaanPromo;
        this.createdAt = createdAt;
        this.dueAt = dueAt;
    }

    /** Persentase terbayar = nominal terbayar / nominal tagihan (0..1). */
    public float getPersentaseTerbayar() {
        return nominalTagihan <= 0 ? 0f : (float) (nominalTerbayar / nominalTagihan);
    }

    /** Sisa jam menuju jatuh tempo dihitung dari "waktu sekarang" sistem. */
    public float getSisaJamJatuhTempo() {
        return getSisaJamJatuhTempo(DssDatabase.sekarang());
    }

    /** Overload: sisa jam relatif terhadap waktu observasi tertentu. */
    public float getSisaJamJatuhTempo(LocalDateTime waktuObservasi) {
        return Duration.between(waktuObservasi, dueAt).toMinutes() / 60f;
    }

    // ---------------- method bantu (tidak ada di diagram) ----------------

    /** Total durasi invoice (created_at s.d. due_at) dalam jam; dipakai rumus S_time FR-09. */
    public float getDurasiJam() {
        return Duration.between(createdAt, dueAt).toMinutes() / 60f;
    }

    public boolean isBelumLunas() {
        return PENDING.equals(statusPembayaran) || PARTIALLY_PAID.equals(statusPembayaran);
    }

    /** Masih aktif = belum lunas DAN belum jatuh tempo pada waktu tertentu. */
    public boolean isAktif(LocalDateTime waktu) {
        return isBelumLunas() && dueAt.isAfter(waktu);
    }

    /**
     * Snapshot 7 fitur pada waktu observasi (FR-05). Dipakai SAMA PERSIS untuk data historis
     * (pelatihan) dan invoice aktif (prediksi) agar tidak ada perbedaan transformasi.
     */
    public Map<String, Object> toFitur(LocalDateTime waktuObservasi) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("nominal_transaksi", nominalTagihan);
        f.put("persentase_terbayar", (double) getPersentaseTerbayar());
        f.put("jumlah_cicilan_dipilih", jumlahCicilan);
        f.put("metode_pembayaran", metodePembayaran);
        f.put("penggunaan_promo", penggunaanPromo);
        f.put("sisa_jam_jatuh_tempo", (double) getSisaJamJatuhTempo(waktuObservasi));
        f.put("hari_pemesanan", createdAt.getDayOfWeek().getValue()); // 1 = Senin ... 7 = Minggu
        return f;
    }

    /** FR-11 langkah 4: variasi nama status sumber dipetakan ke status kanonis. */
    public static String normalisasiStatus(String status) {
        String s = status.trim().toLowerCase().replace(' ', '_');
        return s.equals("partial_paid") ? PARTIALLY_PAID : s;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public String getEventId() {
        return eventId;
    }

    public double getNominalTagihan() {
        return nominalTagihan;
    }

    public double getNominalTerbayar() {
        return nominalTerbayar;
    }

    public String getStatusPembayaran() {
        return statusPembayaran;
    }

    public int getJumlahCicilan() {
        return jumlahCicilan;
    }

    public String getMetodePembayaran() {
        return metodePembayaran;
    }

    public boolean isPenggunaanPromo() {
        return penggunaanPromo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getDueAt() {
        return dueAt;
    }
}
