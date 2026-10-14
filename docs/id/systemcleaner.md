---
title: "Pembersih Sistem (System Cleaner)"
lang: id
translation_key: systemcleaner
permalink: /id/systemcleaner
source_hash: 1ced6df9b00f1e05
---
# Pembersih Sistem (System Cleaner)
{: #system-cleaner }

Alat Pembersih Sistem (SystemCleaner) pada dasarnya adalah mesin pencari serbaguna. Alat ini menelusuri semua penyimpanan yang dapat diakses dan mencocokkan berkas serta folder yang ditemukan dengan sekumpulan filter.

Berbeda dengan alat lain seperti PencariMayat atau Pembersih Aplikasi, alat ini tidak mengenal aplikasi yang terpasang. Alat ini murni bekerja berdasarkan jalur dan atribut berkas. Itulah sebabnya hanya pengecualian jalur dan segmen yang dapat diterapkan pada Pembersih Sistem.

Setiap berkas yang ditemukan dilewatkan melalui filter yang aktif, dan akan dikaitkan dengan filter pertama yang cocok.

## Pengaturan
{: #settings }

### Filter umum
{: #general-filters }

Filter umum mencari **di semua area penyimpanan yang dapat diakses** untuk jenis berkas tertentu yang dapat muncul di mana saja pada perangkat.

| Filter | Deskripsi |
|--------|-------------|
| **Folder kosong** | Folder kosong dari seluruh perangkat. Folder bersarang mungkin memerlukan beberapa kali pemindaian. |
| **Tangkapan layar** | Tangkapan layar yang lebih lama dari usia yang dapat diatur, dari berbagai aplikasi dan lokasi. |
| **File yang dibuang** | Berkas di tempat sampah dari berbagai aplikasi dan lokasi yang ditandai untuk dihapus tetapi belum dihapus secara permanen. |
| **Berkas APK tidak digunakan** | Berkas instalasi (APK) yang versinya lebih lama atau sama dengan versi aplikasi yang sudah terpasang. |
| **Folder LOST.DIR** | Berkas sementara yang dibuat saat mentransfer data antar perangkat Android. |
| **Berkas Linux** | Berkas dan folder yang dibuat ketika penyimpanan terhubung ke komputer Linux. |
| **Berkas Mac** | Berkas dan folder yang dibuat saat menjelajahi penyimpanan dengan aplikasi Finder dari Apple (mis. `.DS_Store`). |
| **Berkas Windows** | Data yang dapat dibuat oleh penjelajah berkas Windows pada penyimpanan yang terhubung (mis. `Thumbs.db`). |
| **Gambar pratinjau** | Pratinjau tersimpan untuk gambar dan video (mis. folder `.thumbnails`). |
| **File sementara sistem** | Berkas sementara terkait sistem, biasanya cache berumur pendek untuk operasi yang sedang berjalan. |
| **Berkas analitik** | Data yang berkaitan dengan analitik dan pelacakan bug. |
| **Berkas iklan** | Berkas yang dibuat untuk atau oleh iklan. |
| **Berkas log sistem** | Berkas log sistem dan layanan sistem (mis. `*.log`) di berbagai lokasi. |

### Filter khusus
{: #specific-filters }

Filter khusus menargetkan **jalur tetap yang sudah dikenal** pada perangkat tempat jenis data tertentu disimpan.

| Filter | Deskripsi |
|--------|-------------|
| **Sisa kerusakan sistem** | Jika proses native mogok (mis. game), berkas "tombstone" dibuat di sini. |
| **Statistik penggunaan** | Berkas yang berkaitan dengan statistik penggunaan aplikasi. |
| **Kesalahan ANR** | Jejak dari kejadian "Application Not Responding" (Aplikasi Tidak Merespons). |
| **Berkas sementara lokal** | Berkas sementara terkait sistem (mis. dari Android Studio). |
| **Berkas log sistem (datalogger)** | Berkas log khusus layanan sistem. |
| **Cache unduhan** | Folder cache sistem yang digunakan untuk berbagai keperluan (mis. pembaruan OTA). |
| **Tempat berkas log** | Area tempat sistem meletakkan berkas log. |
| **Cache pemasangan** | Data tersimpan terkait aplikasi terpasang yang digunakan oleh pengelola paket sistem. |
| **Tugas terbaru** | Data yang berkaitan dengan riwayat tugas di pengalih tugas sistem. |

### Filter khusus buatan pengguna
{: #custom-filters }

Selain filter umum yang disediakan aplikasi, Anda dapat membuat filter khusus dengan kriteria Anda sendiri. Filter khusus dapat dibuat dari **Pengaturan > Pembersih Sistem > Kelola filter khusus**.

> **Peringatan**: Filter khusus bisa berisiko. Selalu periksa hasil pemindaian sebelum menghapus!

#### Kriteria yang Tersedia
{: #available-criteria }

| Kriteria | Deskripsi |
|-----------|-------------|
| **Filter label** | Nama untuk filter Anda |
| **Area data** | Lokasi penyimpanan yang dipindai (kosongkan untuk semua area yang dapat diakses) |
| **Batasan tipe berkas** | Menargetkan berkas, folder, atau tautan simbolik (kosongkan untuk semuanya) |
| **Kriteria jalur** | Aturan untuk mencocokkan jalur berkas |
| **Pengecualian** (jalur) | Jalur yang dikecualikan dari pencocokan |
| **Kriteria nama** | Aturan untuk mencocokkan nama berkas |
| **Ukuran minimum** | Hanya mencocokkan berkas yang lebih besar dari ini |
| **Ukuran maksimum** | Hanya mencocokkan berkas yang lebih kecil dari ini |
| **Usia minimum** | Hanya mencocokkan berkas yang lebih lama dari ini |
| **Usia maksimum** | Hanya mencocokkan berkas yang lebih baru dari ini |
| **Path Regex** (regex jalur) | Lanjutan: ekspresi reguler untuk pencocokan jalur |

#### Mode Kriteria Jalur
{: #path-criteria-modes }

| Mode | Deskripsi | Contoh |
|------|-------------|---------|
| **Dimulai dengan** | Jalur diawali dengan kata kunci | `Downloads/temp` cocok dengan `/sdcard/Downloads/temp/file.txt` |
| **Mengandung** | Jalur memuat kata kunci | `cache` cocok dengan `/sdcard/app/cache/data.bin` |
| **Diakhiri dengan** | Jalur berakhir dengan kata kunci | `backup/old` cocok dengan `/sdcard/backup/old` |
| **Sama dengan** | Jalur sama persis dengan kata kunci | `Download/temp` hanya cocok dengan jalur yang persis itu |

#### Mode Kriteria Nama
{: #name-criteria-modes }

| Mode | Deskripsi | Contoh |
|------|-------------|---------|
| **Dimulai dengan** | Nama berkas diawali dengan kata kunci | `temp_` cocok dengan `temp_file.txt` |
| **Mengandung** | Nama berkas memuat kata kunci | `.bak` cocok dengan `document.bak.old` |
| **Diakhiri dengan** | Nama berkas berakhir dengan kata kunci | `.tmp` cocok dengan `cache.tmp` |
| **Sama dengan** | Nama berkas sama persis | `thumbs.db` hanya cocok dengan `thumbs.db` |

Secara bawaan, semua pencocokan **tidak membedakan huruf besar dan kecil**.

#### Area data
{: #data-areas }

Area umum yang dapat Anda targetkan:

| Area | Deskripsi |
|------|-------------|
| **SDCARD** | Penyimpanan utama (internal atau kartu SD) |
| **PUBLIC_MEDIA** | Folder media (DCIM, Pictures, Music, dll.) |
| **PUBLIC_DATA** | Data aplikasi bersama |
| **PRIVATE_DATA** | Penyimpanan privat aplikasi (memerlukan root) |
| **DOWNLOAD_CACHE** | Cache unduhan sistem (memerlukan root) |
| **PORTABLE** | Penyimpanan yang dapat dilepas (USB/microSD) |

#### Kiat
{: #tips }

- **Performa**: Tentukan hanya kriteria yang diperlukan. Terlalu banyak pola regex akan memperlambat pemindaian.
- **Beberapa kriteria**: Semua kriteria yang ditentukan harus cocok (logika AND).
- **Tekan lama**: Tekan lama pada entri untuk opsi tambahan.
- **Pengujian**: Gunakan pratinjau pemindaian sebelum mengaktifkan penghapusan.
- **Impor/Ekspor**: Filter dapat diekspor dan dibagikan kepada pengguna lain.

#### Opsi Usang
{: #deprecated-options }

Opsi `rootOnly` dari SD Maid 1 **tidak lagi tersedia**. Sebagai gantinya, gunakan **Area data** untuk mengatur lokasi penyimpanan mana yang dipindai.
