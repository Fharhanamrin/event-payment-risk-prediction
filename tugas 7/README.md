# Tugas 6/11/26 — Desain Figma, Program Java, dan Repositori GitHub

Proyek: **Prediksi Risiko Kegagalan Pembayaran Tiket Event Menggunakan Decision Tree (Tixcorner)**

| No | Tugas | Hasil |
|---|---|---|
| 1 | Tampilan desain Figma 6 page | [`1-desain-figma/`](1-desain-figma/) · [File Figma](https://www.figma.com/design/WG70dvhcgJE9Hw42V5b7LL) |
| 2 | Program Java sesuai class diagram | [`2-program-java/`](2-program-java/) |
| 3 | Repositori GitHub kolaborasi | https://github.com/Fharhanamrin/event-payment-risk-prediction |

Alur dokumen yang saling terhubung:
SRS (FR-01…FR-14) → use case → class diagram & object diagram (tugas 6) → desain Figma → kode Java.
Seluruh nama, ID, dan angka contoh dipakai konsisten di setiap tahap.

## Pembagian tugas 2 di GitHub (usulan, 2 anggota)

Pembagian mengikuti package Java, jadi tiap anggota mengerjakan folder yang berbeda dan risiko konflik merge kecil.

| Anggota | Branch | Isi | Kelas |
|---|---|---|---|
| Anggota 1 | `feature/user-source` | Pengguna, akses, dan data sumber | `DssUser`, `Admin`, `PengelolaEvent`, `UserEvent`, `Event`, `Invoice`, `DssDatabase` |
| Anggota 2 | `feature/ml-prediction` | Dataset, model, dan prediksi | `Dataset`, `ModelVersion`, `ModelEvaluation`, `PredictionRun`, `PredictionResult`, `AuditFollowUpLog` |
| Bersama | `main` | Integrasi dan demo | `Main.java`, `README.md`, `run.sh`, data CSV |

## Alur kerja kolaborasi

Tambahkan anggota lewat **Settings → Collaborators** di repositori. Setiap anggota lalu:

```bash
git clone https://github.com/Fharhanamrin/event-payment-risk-prediction.git
cd event-payment-risk-prediction
git checkout -b feature/user-source        # atau feature/ml-prediction
# ... ubah kode di "tugas 7/2-program-java/src" ...
git add . && git commit -m "Pesan perubahan"
git push -u origin feature/user-source
```

Gabungkan branch ke `main` lewat Pull Request.
