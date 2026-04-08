# 📱 Cara Mendapatkan APK via GitHub Actions
## (Tanpa Android Studio, Gratis 100%)

---

## ✅ Yang Anda butuhkan
- Akun GitHub (gratis) → daftar di https://github.com
- File ZIP proyek ini (sudah ada di tangan Anda)

---

## 📋 LANGKAH-LANGKAH

### LANGKAH 1 — Daftar / Login GitHub
1. Buka **https://github.com** di browser
2. Klik **Sign up** (daftar baru) atau **Sign in** (sudah punya akun)
3. Ikuti proses pendaftaran (gratis)

---

### LANGKAH 2 — Buat Repository Baru
1. Setelah login, klik tombol **+** (pojok kanan atas) → **New repository**
2. Isi:
   - **Repository name**: `BarcodeScanner` (atau nama apapun)
   - **Visibility**: pilih **Public** ✅ (agar Actions gratis)
   - **JANGAN** centang "Add a README file"
3. Klik **Create repository**

---

### LANGKAH 3 — Upload File Proyek
Setelah repository dibuat, Anda akan melihat halaman kosong.

1. Klik **uploading an existing file** (link biru di tengah halaman)
2. **Extract** file ZIP yang Anda download tadi
3. Di halaman GitHub, **drag & drop SEMUA FILE dan FOLDER** dari dalam folder `BarcodeScanner/` ke area upload

   > ⚠️ **PENTING**: Upload isi DALAM folder BarcodeScanner, bukan folder BarcodeScanner-nya sendiri.
   > Struktur yang benar di GitHub:
   > ```
   > .github/
   > app/
   > gradle/
   > build.gradle
   > settings.gradle
   > gradle.properties
   > gradlew
   > gradlew.bat
   > README.md
   > ```

4. Scroll ke bawah, klik **Commit changes**

---

### LANGKAH 4 — Tunggu Build Otomatis
1. Setelah upload, GitHub **otomatis menjalankan build**
2. Klik tab **Actions** di menu atas repository Anda
3. Anda akan melihat workflow **"Build Android APK"** sedang berjalan (ikon kuning ⏳)
4. Tunggu sekitar **5–10 menit** hingga selesai (ikon hijau ✅)

   > Jika muncul ikon merah ❌, klik workflow tersebut untuk lihat error-nya dan hubungi saya.

---

### LANGKAH 5 — Download APK
1. Klik nama workflow yang sudah selesai (ikon hijau ✅)
2. Scroll ke bawah, cari bagian **Artifacts**
3. Klik **BarcodeScanner-Debug-APK** → file ZIP akan terdownload
4. Extract ZIP tersebut → di dalamnya ada file **`app-debug.apk`**

---

### LANGKAH 6 — Install APK di HP Android
1. Pindahkan file `app-debug.apk` ke HP Android Anda
   (via kabel USB, WhatsApp ke diri sendiri, Google Drive, dll.)
2. Di HP, buka file manager → cari file APK tadi
3. Ketuk file APK → **Install**

   > ⚠️ Jika muncul peringatan "Install dari sumber tidak dikenal":
   > - Buka **Pengaturan → Keamanan → Izinkan sumber tidak dikenal**
   > - Atau saat popup muncul, ketuk **Pengaturan** lalu aktifkan izin untuk file manager/browser Anda

4. Ketuk **Install** → selesai! 🎉

---

## 🔄 Jika Ingin Build Ulang (setelah ubah kode)

### Cara Manual (tanpa push):
1. Buka repository di GitHub
2. Klik tab **Actions**
3. Klik **"Build Android APK"** di sidebar kiri
4. Klik tombol **"Run workflow"** (kanan atas)
5. Klik **"Run workflow"** (hijau) → build dimulai

---

## ❓ FAQ

**Q: Berapa lama build berlangsung?**
A: Sekitar 5–15 menit tergantung antrian server GitHub.

**Q: Apakah gratis?**
A: Ya, repository Public mendapat 2.000 menit Actions/bulan gratis. Lebih dari cukup.

**Q: APK tersimpan berapa lama?**
A: 30 hari setelah build. Bisa download ulang kapanpun.

**Q: Muncul error saat install "App not installed"?**
A: Pastikan HP Anda Android 7.0 (API 24) atau lebih baru.

**Q: Bagaimana cara update aplikasi?**
A: Upload ulang file yang diubah ke GitHub → Actions akan otomatis build APK baru.

---

## 📞 Butuh Bantuan?
Jika ada error saat build di GitHub Actions, copy teks error-nya dan tanyakan ke saya — saya bantu perbaiki!
