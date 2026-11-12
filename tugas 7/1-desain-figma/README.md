# Desain Figma — 6 Page Tixcorner Risk DSS

**File Figma:** https://www.figma.com/design/WG70dvhcgJE9Hw42V5b7LL

Ukuran frame desktop 1440 × 1024 (SRS 2.4: layar minimal 1280 × 720).
Sumber desain ada di [`tixcorner-dss-6-page.html`](tixcorner-dss-6-page.html) dan bisa dibuka di browser. Screenshot tiap frame ada di folder [`screenshot/`](screenshot/).

| # | Page | Route (SRS 3.1) | FR | Kelas di class diagram |
|---|---|---|---|---|
| 1 | Login | `/login` | FR-01, FR-02 | `DssUser.login()` |
| 2 | Dashboard Ringkasan (Admin) | `/dashboard` | FR-03, FR-04, FR-14 | `Admin.lihatRingkasan()`, `Admin.eksporLaporan()` |
| 3 | Dataset & Model | `/models` | FR-11, FR-12, FR-13 | `Dataset`, `ModelVersion`, `ModelEvaluation` |
| 4 | Jalankan Prediksi | `/prediction` | FR-05, FR-06 | `PredictionRun` |
| 5 | Detail & Jalur Keputusan | `/prediction/detail/501` | FR-07, FR-08 | `PredictionResult`, `Invoice` |
| 6 | Antrean Prioritas & Tindak Lanjut | `/priority-queue` | FR-09, FR-10 | `PredictionResult.hitungSkorPrioritas()`, `AuditFollowUpLog` |
| 2b | *Bonus:* Dashboard Pengelola Event | `/dashboard` (varian peran) | FR-02, NFR-16 | `PengelolaEvent.lihatAgregatRisiko()` |

## Keputusan desain

- **Warna risiko konsisten:** Rendah hijau, Sedang kuning, Tinggi merah, dan **selalu disertai label teks** (SRS 3.1).
- **Status aktual dipisah dari prediksi:** status aktual memakai kotak bergaris putus-putus berlabel "Status aktual · konteks" (FR-03, FR-07).
- **Ekspor laporan** (PDF/Excel/CSV) ditaruh di halaman Ringkasan, bukan halaman sendiri, sesuai SRS 3.1 butir 8.
- **Dataset digabung dengan Pelatihan Model:** tampil sebagai stepper Dataset → Latih → Evaluasi → Aktivasi.
- **Form tindak lanjut (FR-10):** berupa panel samping di halaman Antrean. Notifikasi yang muncul: "Catatan tindak lanjut berhasil diperbarui".

## Sumber angka

Angka di desain diambil dari hasil run program Java (`../2-program-java/contoh-output.txt`): metrik model, run #27, skor invoice, dan antrean prioritas.

Bagian berikut adalah data ilustrasi:
- Titik-titik tren sebelum run #27 di grafik "Tren risiko Tinggi".
- Nomor kontak pembeli yang disamarkan.
- Paket tiket dan riwayat cicilan di halaman Detail.
