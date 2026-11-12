package com.tixcorner.dss.db;

import com.tixcorner.dss.ml.Dataset;
import com.tixcorner.dss.ml.ModelVersion;
import com.tixcorner.dss.prediction.AuditFollowUpLog;
import com.tixcorner.dss.prediction.PredictionResult;
import com.tixcorner.dss.prediction.PredictionRun;
import com.tixcorner.dss.source.Event;
import com.tixcorner.dss.source.Invoice;
import com.tixcorner.dss.user.DssUser;
import com.tixcorner.dss.user.UserEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * KELAS PENDUKUNG (bukan bagian class diagram).
 * Mensimulasikan dua sumber data di memori:
 *  1. Basis data lokal DSS (tabel DSS_USERS, DATASETS, MODEL_VERSIONS, dst.) yang boleh ditulis.
 *  2. Sumber data Tixcorner (events, invoices) yang hanya boleh DIBACA (NFR-17, read-only).
 */
public final class DssDatabase {

    public static final DateTimeFormatter FORMAT_WAKTU = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ---------- tabel lokal DSS ----------
    public static final List<DssUser> USERS = new ArrayList<>();
    public static final List<UserEvent> USER_EVENTS = new ArrayList<>();
    public static final List<Dataset> DATASETS = new ArrayList<>();
    public static final List<ModelVersion> MODELS = new ArrayList<>();
    public static final List<PredictionRun> RUNS = new ArrayList<>();
    public static final List<PredictionResult> RESULTS = new ArrayList<>();
    public static final List<AuditFollowUpLog> LOGS = new ArrayList<>();

    // ---------- sumber Tixcorner (read-only) ----------
    private static final List<Event> EVENTS = new ArrayList<>();
    private static final List<Invoice> INVOICES = new ArrayList<>();

    // ID awal disamakan dengan object diagram tugas 6 agar contoh data konsisten.
    private static final Map<String, Integer> ID_BERIKUTNYA = new HashMap<>(Map.of(
            "dataset", 11, "model", 3, "evaluation", 5, "run", 27, "result", 501, "log", 9001));

    private static LocalDateTime waktuTetap;

    private DssDatabase() {
    }

    /** Membekukan "waktu sekarang" agar demo selalu menghasilkan angka yang sama. */
    public static void setWaktuSekarang(LocalDateTime waktu) {
        waktuTetap = waktu;
    }

    public static LocalDateTime sekarang() {
        return waktuTetap != null ? waktuTetap : LocalDateTime.now();
    }

    public static int nextId(String tabel) {
        int id = ID_BERIKUTNYA.getOrDefault(tabel, 1);
        ID_BERIKUTNYA.put(tabel, id + 1);
        return id;
    }

    // ---------- pemuatan sumber Tixcorner ----------
    public static void muatSumberTixcorner(Path eventCsv, Path invoiceCsv) throws IOException {
        EVENTS.clear();
        INVOICES.clear();
        for (String[] k : bacaCsv(eventCsv)) {
            EVENTS.add(new Event(k[0], Integer.parseInt(k[1]), k[2], waktu(k[3])));
        }
        for (String[] k : bacaCsv(invoiceCsv)) {
            INVOICES.add(new Invoice(k[0], k[1], Double.parseDouble(k[2]), Double.parseDouble(k[3]),
                    Invoice.normalisasiStatus(k[4]), Integer.parseInt(k[5]), k[6],
                    Boolean.parseBoolean(k[7]), waktu(k[8]), waktu(k[9])));
        }
    }

    /** Membaca CSV sederhana (tanpa koma di dalam nilai); baris header dilewati. */
    public static List<String[]> bacaCsv(Path berkas) throws IOException {
        List<String> baris = Files.readAllLines(berkas);
        List<String[]> hasil = new ArrayList<>();
        for (int i = 1; i < baris.size(); i++) {
            if (!baris.get(i).isBlank()) {
                hasil.add(baris.get(i).split(",", -1));
            }
        }
        return hasil;
    }

    public static LocalDateTime waktu(String teks) {
        return teks == null || teks.isBlank() ? null : LocalDateTime.parse(teks.trim(), FORMAT_WAKTU);
    }

    public static List<Event> events() {
        return Collections.unmodifiableList(EVENTS);
    }

    public static List<Invoice> invoices() {
        return Collections.unmodifiableList(INVOICES);
    }

    // ---------- pencarian ----------
    public static Optional<DssUser> cariUser(String emailAtauUsername) {
        return USERS.stream()
                .filter(u -> u.getEmail().equalsIgnoreCase(emailAtauUsername)
                        || u.getUsername().equalsIgnoreCase(emailAtauUsername))
                .findFirst();
    }

    public static Optional<Event> cariEvent(String eventId) {
        return EVENTS.stream().filter(e -> e.getEventId().equals(eventId)).findFirst();
    }

    public static Optional<Dataset> cariDataset(int datasetId) {
        return DATASETS.stream().filter(d -> d.getDatasetId() == datasetId).findFirst();
    }

    public static Optional<ModelVersion> cariModel(int modelId) {
        return MODELS.stream().filter(m -> m.getModelId() == modelId).findFirst();
    }

    public static Optional<ModelVersion> cariModelAktif() {
        return MODELS.stream().filter(m -> ModelVersion.STATUS_AKTIF.equals(m.getStatus())).findFirst();
    }

    public static Optional<PredictionResult> cariResult(int resultId) {
        return RESULTS.stream().filter(r -> r.getResultId() == resultId).findFirst();
    }

    /** Status tindak lanjut terakhir untuk badge antrean; default "Belum Dihubungi". */
    public static String statusTindakLanjutTerakhir(int resultId) {
        String status = AuditFollowUpLog.STATUS_VALID.get(0);
        for (AuditFollowUpLog log : LOGS) {
            if (log.getResultId() == resultId) {
                status = log.getFollowUpStatus();
            }
        }
        return status;
    }
}
