package com.tixcorner.dss.source;

import com.tixcorner.dss.db.DssDatabase;

import java.time.LocalDateTime;
import java.util.List;

/**
 * «read-only» Tabel sumber Tixcorner: events.
 * Semua atribut final dan tidak ada setter karena DSS hanya boleh membaca (NFR-17).
 */
public final class Event {

    private final String eventId;
    private final int organizerId;
    private final String namaEvent;
    private final LocalDateTime tanggalEvent;

    public Event(String eventId, int organizerId, String namaEvent, LocalDateTime tanggalEvent) {
        this.eventId = eventId;
        this.organizerId = organizerId;
        this.namaEvent = namaEvent;
        this.tanggalEvent = tanggalEvent;
    }

    /**
     * Relasi Event 1 — 0..* Invoice ("memiliki").
     * Mengembalikan invoice yang belum lunas (pending / partially_paid) sebagai kandidat prediksi.
     * Validasi jatuh tempo dan nominal dilakukan saat PredictionRun.bentukFitur() (FR-05).
     */
    public List<Invoice> getInvoiceAktif() {
        return DssDatabase.invoices().stream()
                .filter(i -> i.getEventId().equals(eventId))
                .filter(Invoice::isBelumLunas)
                .toList();
    }

    public String getEventId() {
        return eventId;
    }

    public int getOrganizerId() {
        return organizerId;
    }

    public String getNamaEvent() {
        return namaEvent;
    }

    public LocalDateTime getTanggalEvent() {
        return tanggalEvent;
    }
}
