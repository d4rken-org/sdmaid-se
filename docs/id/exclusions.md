---
title: "Pengecualian"
lang: id
translation_key: exclusions
permalink: /id/exclusions
source_hash: d5c3aea2efa43796
---
# Pengecualian
{: #exclusions }

Fungsi pengecualian di SD Maid 2/SE memungkinkan pengguna menentukan folder atau berkas tertentu yang harus diabaikan aplikasi selama pemindaian sistem dan tugas pemeliharaan. Ini berguna jika ada data yang ingin Anda biarkan tidak tersentuh oleh proses aplikasi. Dengan menetapkan pengecualian, SD Maid 2/SE akan melewati direktori atau berkas tersebut selama tugas seperti PencariMayat (CorpseFinder), Pembersih Aplikasi (AppCleaner), dan fitur pengelolaan sistem lainnya.

Cara termudah untuk menambahkan pengecualian adalah langsung dari hasil. Anda dapat menggunakan tombol "Kecualikan" pada item satu per satu, atau menekan lama/memilih beberapa entri sekaligus, lalu memilih opsi "Kecualikan". Selain itu, pengecualian dapat ditambahkan secara manual melalui pengaturan aplikasi dengan menentukan jalur atau pola wildcard untuk kontrol yang lebih besar.

Pengecualian dapat diekspor dan diimpor dari menu. Impor pengecualian hanya didukung pada versi SD Maid yang serupa. Jika ada terlalu banyak pembaruan di antara versi saat impor dan saat ekspor, kesalahan dapat terjadi. Dalam hal ini, Anda dapat mencoba mengimpor pada versi yang lebih lama, lalu mengekspor pengecualian tersebut lagi.

<img src="/assets/images/exclusions/overview-1.webp" width="150">
<img src="/assets/images/exclusions/overview-2.webp" width="150">
<img src="/assets/images/exclusions/overview-3.webp" width="150">
<img src="/assets/images/exclusions/overview-4.webp" width="150">

## Jenis
{: #types }

SD Maid mendukung 3 jenis pengecualian:

### Pengecualian aplikasi
{: #app-exclusions }

Pengecualian aplikasi didasarkan pada nama paket setiap aplikasi. Pengecualian ini dapat dibuat melalui alat Pengendali Aplikasi (AppControl). Cukup pilih aplikasi dari daftar dan gunakan tindakan `Kecualikan` yang terkait.

### Pengecualian lokasi
{: #path-exclusions }

Pengecualian lokasi (jalur) dapat dibuat dengan memilih (menekan lama) jalur di alat Penganalisis Penyimpanan (StorageAnalyzer).

### Segmen pengecualian 
{: #segment-exclusions }
Segmen pengecualian adalah jenis pengecualian jalur yang tidak memerlukan kecocokan persis. Segmen adalah bagian dari jalur, misalnya sebuah folder. Jalur `/sdcard/DCIM/Camera` terdiri dari 3 segmen. Anda dapat mengecualikan semua foto kamera, baik di penyimpanan internal (`/storage/emulated/0/DCIM/Camera`) maupun penyimpanan eksternal (`/sdcard/DCIM/Camera`), dengan membuat segmen pengecualian untuk `DCIM/Camera`.

Jika Anda mencentang opsi `Izinkan penyocokan terpisah`, Anda dapat membuat pengecualian untuk `DCIM/Cam` yang cocok dengan folder `Cam` maupun folder `Camera`.

## Alat
{: #tools }

Setiap alat SD Maid memiliki cara pandang yang berbeda terhadap data. Tidak semua pengecualian memengaruhi semua alat dengan cara yang sama.

### PencariMayat
{: #corpsefinder }

Alat PencariMayat mengetahui apa itu "aplikasi" dan berkas mana yang menjadi milik suatu aplikasi. Alat ini mematuhi semua jenis pengecualian. Membuat pengecualian aplikasi berarti setiap berkas yang ditemukan PencariMayat dan dianggap milik aplikasi yang dikecualikan juga akan dikecualikan.

Jenis pengecualian yang didukung: `APP`, `PATH`, `SEGMENTS`

### Pembersih Sistem
{: #systemcleaner }

Alat Pembersih Sistem (SystemCleaner) hanya bekerja pada data jalur. Alat ini tidak mengetahui apa itu "aplikasi" atau berkas mana yang menjadi milik suatu aplikasi.

Jenis pengecualian yang didukung: `PATH`, `SEGMENTS`

### Pembersih Aplikasi
{: #appcleaner }

Alat Pembersih Aplikasi mematuhi semua jenis pengecualian. Anda dapat menggunakan pengecualian untuk mempercepat pemindaian jika ada aplikasi dengan banyak berkas yang harus dipindai.

Jenis pengecualian yang didukung: `APP`, `PATH`, `SEGMENTS`

### Penghapus Duplikat
{: #deduplicator }

Alat Penghapus Duplikat (Deduplicator) tidak mengetahui apa itu "aplikasi" dan hanya bekerja dengan data berkas.

Jenis pengecualian yang didukung: `PATH`, `SEGMENTS`

### Optimasi Media
{: #media-squeeze }

Optimasi Media (Media Squeeze) hanya bekerja dengan data berkas. Berkas yang dikecualikan tidak dikompresi.

Jenis pengecualian yang didukung: `PATH`, `SEGMENTS`

### Pemilah
{: #swiper }

Pemilah (Swiper) hanya bekerja dengan data berkas. Berkas yang dikecualikan tidak disertakan dalam sesi baru. Menekan lama `Lewati` saat memilah akan mengecualikan berkas yang sedang ditampilkan.

Jenis pengecualian yang didukung: `PATH`, `SEGMENTS`

### Penganalisis Penyimpanan
{: #storageanalyzer }

Penganalisis Penyimpanan tidak mendukung pengecualian, karena semua konten perlu dipindai untuk menghitung total ukuran yang benar.
