package com.tixcorner.dss.user;

import com.tixcorner.dss.db.DssDatabase;
import com.tixcorner.dss.ml.ModelVersion;
import com.tixcorner.dss.prediction.PredictionResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PengelolaEvent (Event Organizer) — akses terbatas (FR-02).
 * Hanya bisa melihat AGREGAT kategori risiko untuk event miliknya; tidak punya method
 * dataset, model, prediksi, detail, antrean, maupun ekspor.
 */
public class PengelolaEvent extends DssUser {

    public PengelolaEvent(int userId, String nama, String email, String username, String kataSandi) {
        super(userId, nama, email, username, "pengelola_event", kataSandi);
    }

    /**
     * FR-03 (akses EO): jumlah hasil prediksi per kategori untuk event yang diizinkan.
     * filter boleh berisi "event_id"; event milik promotor lain ditolak (HTTP 403).
     */
    public Map<String, Integer> lihatAgregatRisiko(Map<String, String> filter) {
        pastikanLogin();
        String eventDiminta = filter.get("event_id");
        if (eventDiminta != null && DssDatabase.USER_EVENTS.stream()
                .noneMatch(ue -> ue.isTerotorisasi(getUserId(), eventDiminta))) {
            throw new SecurityException("403 Forbidden: event " + eventDiminta + " bukan milik Anda");
        }
        List<String> izin = getEventDiizinkan();

        Map<String, Integer> agregat = new LinkedHashMap<>();
        agregat.put(ModelVersion.RENDAH, 0);
        agregat.put(ModelVersion.SEDANG, 0);
        agregat.put(ModelVersion.TINGGI, 0);
        // penyaringan tenant dilakukan SEBELUM agregasi
        for (PredictionResult r : DssDatabase.RESULTS) {
            String eventId = r.getInvoice().getEventId();
            boolean diizinkan = izin.contains(eventId) && (eventDiminta == null || eventDiminta.equals(eventId));
            if (diizinkan && r.isDariModelAktif()) {
                agregat.merge(r.getRiskLevel(), 1, Integer::sum);
            }
        }
        return agregat;
    }

    public List<String> getEventDiizinkan() {
        return DssDatabase.USER_EVENTS.stream()
                .filter(ue -> ue.getUserId() == getUserId())
                .map(UserEvent::getEventId)
                .toList();
    }
}
