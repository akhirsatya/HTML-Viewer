# ProtoView

Aplikasi Android sederhana untuk membuka file HTML (misalnya prototype buatan Claude) dalam mode **layar penuh**.

## Fitur
- Edge-to-edge: konten tampil sampai ke belakang status bar & garis swipe home yang transparan; warna ikon status bar menyesuaikan latar prototype otomatis
- Layar tetap menyala selama prototype dibuka
- Mendukung notch / poni layar
- JavaScript, localStorage, dan library CDN (Tailwind, React, Google Fonts, dll.) tetap jalan
- Muncul di menu **"Buka dengan"** saat kamu mengetuk file `.html` di File Manager, Google Drive, WhatsApp, dll.
- Setelah memilih file muncul panel pengaturan tampilan: mode (edge-to-edge / layar penuh total / aman), orientasi, warna ikon status bar, layar tetap menyala, zoom
- Home dengan tema terang/gelap otomatis mengikuti sistem
- Daftar file terakhir dibuka (ikon ⚙ untuk mengubah pengaturan per file) (ketuk lama untuk menghapus)
- Tombol Kembali = keluar dari prototype
- Debugging: buka `chrome://inspect` di Chrome desktop saat HP tersambung lewat USB

## Cara dapat APK tanpa install apa-apa (GitHub Actions)
1. Buat repository baru di GitHub.
2. Upload **seluruh isi folder ini** (termasuk folder `.github` yang tersembunyi).
3. Buka tab **Actions**: build berjalan otomatis (sekitar 3–5 menit).
4. Setelah selesai (centang hijau), klik build-nya, lalu unduh **ProtoView-apk** di bagian *Artifacts*.
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

## Gaya UI & font (opsional)
UI memakai gaya square (tanpa sudut membulat) dengan tombol hitam. Untuk font aslinya, unduh dari Google Fonts
**Gajraj One** (judul ProtoView), **Instrument Serif**, dan **DM Sans**, ganti nama file menjadi `gajraj_one.ttf`, `instrument_serif.ttf`, dan `dm_sans.ttf`,
lalu taruh di `app/src/main/res/font/`. Kalau file itu tidak ada, app memakai font bawaan Android.

## Font (opsional)
Taruh file font di `app/src/main/res/font/` (nama huruf kecil): `gajraj_one.ttf` (judul "ProtoView"), `dm_sans.ttf` (teks). Tanpa font ini app tetap jalan dengan font bawaan.

## Pengaturan
Ikon ⚙ di Home: tema (Light/Dark/System), bahasa (Follow system/Bahasa Indonesia/English), tentang developer, versi app.
