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

## Pembagian tugas 2 di GitHub (4 anggota)

Pembagian mengikuti package Java, jadi tiap anggota mengerjakan folder yang berbeda dan risiko konflik merge kecil.

| Anggota | Branch | Package | Kelas |
|---|---|---|---|
| Fharhan Amrin Sitanggang (202343502114) | `feature/user` | `user/` + integrasi | `DssUser`, `Admin`, `PengelolaEvent`, `UserEvent`, `Main.java`, README |
| Abdul Ghadir Umar (202343502098) | `feature/source-db` | `source/` + `db/` | `Event`, `Invoice`, `DssDatabase`, data CSV |
| Tifan Dani Indra Pratama (202343502117) | `feature/ml` | `ml/` | `Dataset`, `ModelVersion`, `ModelEvaluation` |
| Yana Rizki Nur Pradana (202343501960) | `feature/prediction` | `prediction/` | `PredictionRun`, `PredictionResult`, `AuditFollowUpLog` |

## Alur kerja kolaborasi

Tambahkan anggota lewat **Settings → Collaborators** di repositori. Setiap anggota lalu:

```bash
git clone https://github.com/Fharhanamrin/event-payment-risk-prediction.git
cd event-payment-risk-prediction
git checkout -b feature/ml        # sesuai branch masing-masing di tabel
# ... ubah kode di "tugas 7/2-program-java/src" ...
git add . && git commit -m "Pesan perubahan"
git push -u origin feature/ml
```

Gabungkan branch ke `main` lewat Pull Request.
