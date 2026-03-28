# 📱 Barcode Ticket Scanner — Android App

Aplikasi Android native untuk scan barcode tiket, pencatatan tonnase per shift, dan export ke spreadsheet Excel (.xlsx).

---

## 🚀 Cara Build & Install

### Prasyarat
- **Android Studio** Hedgehog (2023.1.1) atau lebih baru
- **JDK 8** atau lebih baru
- **Android SDK** API 24–34

### Langkah Build
1. Buka Android Studio → **Open** → pilih folder `BarcodeScanner`
2. Tunggu Gradle sync selesai (butuh koneksi internet untuk download dependencies)
3. Colokkan HP Android (aktifkan USB Debugging di Developer Options)
4. Klik **Run ▶** atau tekan `Shift+F10`

> Untuk build APK: **Build → Build Bundle(s)/APK(s) → Build APK(s)**  
> APK akan tersimpan di `app/build/outputs/apk/debug/`

---

## 📋 Fitur Aplikasi

### Dashboard Utama
- Menampilkan **tanggal operasional** saat ini dan **shift aktif**
- Kartu statistik **Shift 1** dan **Shift 2** (jumlah tiket + total tonnase)
- **Total Penerimaan Harian** (gabungan shift 1 + shift 2)
- Daftar scan terbaru hari ini

### Scan Barcode
- Scan otomatis menggunakan kamera dengan ZXing
- **Format barcode**: `NOTIKET|TONNASE` — contoh: `TKT-001|35.5`
- Jika barcode hanya berisi nomor tiket, tonnase bisa diisi manual
- Pengecekan **duplikat tiket** dalam 1 hari operasional yang sama
- Input manual sebagai alternatif/koreksi

### Sistem Shift & Cut-off
| Shift | Jam | Keterangan |
|-------|-----|------------|
| Shift 1 | 07:00 – 19:00 | Siang |
| Shift 2 | 19:00 – 07:00 | Malam |

**Cut-off harian = 07:00 pagi**

> Contoh: Scan pada 01-Jan jam 22:30 → Operasional date = **1 Januari**, Shift = **2**  
> Scan pada 02-Jan jam 03:00 → Operasional date tetap **1 Januari**, Shift = **2**  
> Scan pada 02-Jan jam 07:05 → Operasional date = **2 Januari**, Shift = **1** ✅

### Riwayat
- Filter berdasarkan **tanggal operasional** dan **shift**
- Menampilkan semua data dengan warna kode shift
- Summary total tiket dan tonnase sesuai filter

### Ringkasan Harian
- Rekap per hari operasional: tiket & tonnase per shift
- Total penerimaan harian

### Export Excel (.xlsx)
- Export data ke file **Excel** dengan format profesional:
  - Sheet "Detail Scan"
  - Data terpisah per shift dengan warna berbeda
  - Sub-total per shift (kuning)
  - Grand total harian (hijau gelap)
- File tersimpan di: `Downloads/BarcodeScanner/`

---

## 🗄️ Struktur Database (Room/SQLite)

Tabel: `ticket_scans`
| Kolom | Tipe | Keterangan |
|-------|------|------------|
| id | LONG (PK) | Auto-increment |
| ticketNumber | TEXT | Nomor tiket |
| tonnage | REAL | Tonnase dalam Ton |
| scanTimestamp | LONG | Epoch millis waktu scan |
| shiftNumber | INT | 1 atau 2 |
| operationalDate | TEXT | Format YYYY-MM-DD (cut-off 07:00) |

---

## 🏗️ Struktur Proyek

```
app/src/main/java/com/ticketscanner/app/
├── activities/
│   ├── MainActivity.java       # Dashboard utama
│   ├── ScanActivity.java       # Scanner + input manual
│   ├── HistoryActivity.java    # Riwayat dengan filter
│   └── SummaryActivity.java    # Ringkasan harian
├── adapters/
│   ├── ScanAdapter.java        # RecyclerView scan list
│   └── SummaryAdapter.java     # RecyclerView summary
├── database/
│   ├── AppDatabase.java        # Room database
│   └── TicketScanDao.java      # DAO queries
├── models/
│   ├── TicketScan.java         # Entity model
│   └── DailyStats.java         # Stats model
└── utils/
    ├── ShiftUtils.java         # Logika shift & tanggal operasional
    ├── BarcodeParser.java      # Parser format barcode
    └── ExcelExporter.java      # Export Apache POI
```

---

## 🔧 Konfigurasi Format Barcode

Edit file `BarcodeParser.java` untuk mengubah separator:
```java
private static final String SEPARATOR = "|";
```

Ubah `|` sesuai format barcode yang digunakan di lapangan.

---

## 📦 Dependencies Utama
- `zxing-android-embedded` — barcode scanner
- `androidx.room` — local database SQLite
- `apache poi` — export Excel (.xlsx)
- `Material Components` — UI modern
