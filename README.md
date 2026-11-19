# Prototype Viewer

Aplikasi Android sederhana untuk membuka file HTML (misalnya prototype buatan Claude) dalam mode **layar penuh**.

## Fitur
- Fullscreen immersive: status bar & navigation bar disembunyikan (geser dari tepi layar untuk memunculkan sementara)
- Layar tetap menyala selama prototype dibuka
- Mendukung notch / poni layar
- JavaScript, localStorage, dan library CDN (Tailwind, React, Google Fonts, dll.) tetap jalan
- Muncul di menu **"Buka dengan"** saat kamu mengetuk file `.html` di File Manager, Google Drive, WhatsApp, dll.
- Daftar file terakhir dibuka (ketuk lama untuk menghapus)
- Tombol Kembali = keluar dari prototype
- Debugging: buka `chrome://inspect` di Chrome desktop saat HP tersambung lewat USB

## Cara dapat APK tanpa install apa-apa (GitHub Actions)
1. Buat repository baru di GitHub.
2. Upload **seluruh isi folder ini** (termasuk folder `.github` yang tersembunyi).
3. Buka tab **Actions**: build berjalan otomatis (sekitar 3–5 menit).
4. Setelah selesai (centang hijau), klik build-nya, lalu unduh **PrototypeViewer-apk** di bagian *Artifacts*.
5. Ekstrak zip-nya, pindahkan `app-debug.apk` ke HP, lalu install (izinkan "Install dari sumber tidak dikenal").

## Cara build dengan Android Studio
1. Buka Android Studio, lalu pilih **File > Open** dan arahkan ke folder ini.
2. Tunggu proses Gradle sync selesai.
3. Sambungkan HP (USB debugging aktif), lalu klik **Run ▶**.
   Atau buat APK lewat **Build > Build APK(s)**.

## Catatan
- Prototype satu file (semua CSS/JS di dalam satu `.html`, seperti hasil Claude) paling cocok.
- Jika HTML memakai file lokal terpisah (misal `style.css` di folder yang sama), file tersebut tidak ikut terbaca saat dibuka lewat file picker. Gabungkan dulu ke dalam satu file HTML.
- Android minimal: 8.0 (API 26).
