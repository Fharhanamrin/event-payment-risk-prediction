# Event Payment Risk Prediction — Tixcorner DSS (Java)

Implementasi Java dari **class diagram** Sistem Prediksi Risiko Kegagalan Pembayaran Tiket Event (Tixcorner).
Tiap kelas pada diagram = satu file `.java`, lengkap dengan atribut, method, dan relasinya.
Program berjalan **tanpa library luar** (cukup JDK 17+). Decision Tree (CART) juga diimplementasikan langsung di Java.

## Cara menjalankan

```bash
./run.sh
```

Atau manual:

```bash
javac -encoding UTF-8 -d out $(find src -name "*.java")
java -Dfile.encoding=UTF-8 -cp out com.tixcorner.dss.Main data
```

Contoh output lengkap ada di [`contoh-output.txt`](contoh-output.txt). Laporan ekspor ditulis ke folder `output/`.

## Struktur

```
src/com/tixcorner/dss/
├── Main.java                  skenario demo end-to-end (urut sesuai 6 page desain Figma)
├── user/                      Pengguna & Akses (kuning di diagram)
│   ├── DssUser.java           «abstract»
│   ├── Admin.java
│   ├── PengelolaEvent.java
│   └── UserEvent.java
├── ml/                        Dataset & Model ML (hijau)
│   ├── Dataset.java
│   ├── ModelVersion.java      + Decision Tree CART (gini/entropy)
│   └── ModelEvaluation.java
├── prediction/                Prediksi & Tindak Lanjut (biru)
│   ├── PredictionRun.java
│   ├── PredictionResult.java
│   └── AuditFollowUpLog.java
├── source/                    Sumber Tixcorner «read-only» (abu-abu)
│   ├── Event.java
│   └── Invoice.java
└── db/
    └── DssDatabase.java       kelas pendukung: simulasi basis data di memori (bukan bagian diagram)
data/
├── dataset_historis.csv       3.225 baris histori invoice (data sintetis)
├── event.csv                  3 event sumber Tixcorner
└── invoice_aktif.csv          134 invoice sumber (124 kandidat prediksi)
```

## Kesesuaian dengan class diagram

| Kelas diagram | File | Method dari diagram | FR |
|---|---|---|---|
| `DssUser` «abstract» | `user/DssUser.java` | `login()`, `logout()`, `isTerkunci()`, `#catatGagalLogin()` | FR-01 |
| `Admin` | `user/Admin.java` | `siapkanDataset()`, `latihModel()`, `aktifkanModel()`, `jalankanPrediksi()`, `lihatRingkasan()`, `lihatDetail()`, `lihatAntreanPrioritas()`, `catatTindakLanjut()`, `eksporLaporan()` | FR-02…FR-14 |
| `PengelolaEvent` | `user/PengelolaEvent.java` | `lihatAgregatRisiko()`, `getEventDiizinkan()` | FR-02, FR-03 |
| `UserEvent` | `user/UserEvent.java` | `isTerotorisasi()` | NFR-16 |
| `Dataset` | `ml/Dataset.java` | `validasi()`, `bentukLabel()`, `bagiPeriode()`, `isLayakLatih()` | FR-11, FR-12 |
| `ModelVersion` | `ml/ModelVersion.java` | `latih()`, `evaluasi()`, `setujui()`, `aktifkan()`, `prediksiProba()`, `petakanKategori()` | FR-06, FR-12, FR-13 |
| `ModelEvaluation` | `ml/ModelEvaluation.java` | `hitungMetrik()`, `bandingkanBaseline()`, `isLulus()` | FR-13 |
| `PredictionRun` | `prediction/PredictionRun.java` | `bentukFitur()`, `jalankanInferensi()`, `catatStatistik()` | FR-05, FR-06 |
| `PredictionResult` | `prediction/PredictionResult.java` | `getJalurKeputusan()`, `hitungSkorPrioritas()`, `isMasihAktif()` | FR-07…FR-09 |
| `AuditFollowUpLog` | `prediction/AuditFollowUpLog.java` | `sanitasiCatatan()`, `simpan()` | FR-10 |
| `Event` «read-only» | `source/Event.java` | `getInvoiceAktif()` | — |
| `Invoice` «read-only» | `source/Invoice.java` | `getPersentaseTerbayar()`, `getSisaJamJatuhTempo()` | FR-05 |

**Relasi pada kode:**
- **Generalisasi:** `Admin` dan `PengelolaEvent` `extends DssUser`.
- **Komposisi:** `ModelVersion` membuat dan menyimpan `ModelEvaluation` di `evaluasi()`. `PredictionRun` membuat `PredictionResult` di `jalankanInferensi()`.
- **Asosiasi:** Admin menyiapkan `Dataset`, `Dataset` melatih `ModelVersion`, `ModelVersion` dipakai `PredictionRun`, `PredictionResult` memprediksi `Invoice`, `AuditFollowUpLog` menelaah `PredictionResult`.
- **Read-only:** semua atribut `Event`/`Invoice` bersifat `final` tanpa setter. Daftar sumber dikembalikan sebagai `unmodifiableList`.

**Tambahan di luar diagram** (konstruktor dan getter tidak dihitung):
- Kelas pendukung `DssDatabase`.
- Method bantu: `Invoice.toFitur()`, `Invoice.isAktif()`, `ModelVersion.telusuriJalur()`, `ModelVersion.cetakPohon()`, `PredictionResult.getSkorPrioritasTerkini()`.

## Hasil demo (data sintetis, seed 42)

- **Dataset `DS-2026-09-v1`:** 3.225 baris mentah, 3.104 valid. Pembagian kronologis latih/validasi/uji = 2.151 / 451 / 465.
- **Model `DT-v1.2`** (gini, max_depth 5, min_samples_leaf 20):
  - Hasil uji: recall **0,749**, F1 **0,710**, accuracy 0,738.
  - Baseline mayoritas: F1 0,000.
  - Lulus kriteria lalu diaktifkan.
- **Run #27:** 120 invoice diprediksi, 4 dilewati. Distribusi: Tinggi 31, Sedang 34, Rendah 55.
- **`INV-2026-00871`:** P(gagal) = 0,84, kategori **Tinggi**. `INV-2026-00872`: 0,51, kategori **Sedang**.
- **Antrean prioritas (16:30):** 62 invoice Sedang/Tinggi, skornya dihitung ulang saat antrean dibuka.
- **Pengelola Event** hanya melihat agregat event miliknya. Kalau meminta event lain, sistem menolak dengan **403**.

ID objek (dataset 11, model 3, evaluasi 5, run 27, result 501, log 9001) sengaja disamakan dengan object diagram.

## Batasan prototipe

- **Ekspor laporan:** baru format CSV. PDF/Excel butuh pustaka tambahan (OpenPDF / Apache POI).
- **Unggah dataset:** baru format `.csv`.
- **Hash kata sandi:** memakai PBKDF2 bawaan Java. SRS (NFR-07) meminta bcrypt/Argon2id di produksi.
- **Penyimpanan:** data tersimpan di memori dan hilang saat program selesai. SRS menargetkan PostgreSQL/MySQL.
- **Data:** sintetis untuk keperluan tugas, bukan data riil Tixcorner.
