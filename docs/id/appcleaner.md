---
title: "Pembersih Aplikasi (App Cleaner)"
lang: id
translation_key: appcleaner
permalink: /id/appcleaner
source_hash: 505b031f74bf71a2
---
# Pembersih Aplikasi (App Cleaner)
{: #app-cleaner }

Alat Pembersih Aplikasi (AppCleaner) adalah alat yang memahami aplikasi dan menemukan berkas yang "tidak diperlukan". Sebuah berkas dianggap tidak diperlukan jika dapat dihapus tanpa efek buruk dan memang tidak dibutuhkan, atau akan dibuat ulang bila diperlukan. Sebagian besar jenis berkas di dalam "cache" termasuk dalam definisi ini.

## Pengaturan
{: #settings }

### Sertakan aplikasi sistem
{: #include-system-apps }

Pengaturan ini akan menyertakan aplikasi sistem dalam hasil. Pengaturan ini tidak aktif secara bawaan karena sebagian aplikasi sistem (tergantung ROM) bereaksi buruk ketika cache-nya dihapus. Beberapa aplikasi sistem juga tidak memiliki tombol "Hapus cache", yang dapat membuat SD Maid macet saat menghapus cache melalui layanan aksesibilitas.

### Sertakan pengguna lain
{: #include-other-users }

Memerlukan root. Jika diaktifkan, SD Maid akan mencari di folder pengguna lain selain pengguna yang sedang menjalankan SD Maid.

### Sertakan aplikasi yang sedang berjalan
{: #include-running-apps }

Menentukan apakah aplikasi yang sedang berjalan disertakan atau tidak.

### Ukuran minimum
{: #minimum-size }

Seberapa besar cache sebuah aplikasi agar ditampilkan dalam daftar. Menentukan ukuran minimum adalah kompromi yang baik antara manfaat dan kecepatan. Menghapus cache aplikasi yang hanya berukuran beberapa KB biasanya tidak memberikan manfaat. Ini berlaku untuk total cache yang ditemukan untuk sebuah aplikasi (dengan memperhitungkan semua filter yang aktif)

### Umur minimal
{: #minimum-age }

Dapat digunakan untuk hanya menghapus berkas cache yang sudah lama. Diterapkan pada setiap berkas atau folder dalam hasil.

### Sertakan cache yang tidak bisa diakses
{: #include-inaccessible-caches }

Tergantung izin yang tersedia bagi SD Maid, beberapa berkas mungkin tidak dapat diakses secara langsung. Umumnya ini merujuk pada cache bawaan privat aplikasi (mis. `/data/data/eu.darken.sdmse/cache`). Jika pengaturan ini diaktifkan, SD Maid akan mencoba menggunakan layanan aksesibilitas untuk membuka pengaturan sistem dan menekan tombol `Clear cache` ("Hapus cache") untuk aplikasi yang dituju.

### Filter
{: #filter }

Anda dapat mengaktifkan/menonaktifkan berbagai filter untuk memengaruhi hasil yang ditemukan oleh alat Pembersih Aplikasi.

#### Filter umum
{: #common-filters }

Filter umum menargetkan berbagai jenis berkas di semua aplikasi.

#### Filter khusus
{: #specific-filters }

Filter khusus menargetkan aplikasi tertentu, mis. WhatsApp atau Telegram.

## Pemecahan Masalah
{: #troubleshooting }
### `com.miui.securitycenter`
{: #commiuisecuritycenter }
#### Izin `GET_USAGE_STATS` tidak ada
{: #missing-get_usage_stats-permission }

MIUI dan HyperOS menggunakan aplikasi tambahan bernama "Security" ("Keamanan") atau "Security Center" (nama paket: `com.miui.securitycenter`) untuk menampilkan detail sistem sebuah aplikasi. Alur tindakan normal untuk menghapus cache aplikasi adalah: Klik `Clear data` ("Hapus data"), pilih `Clear cache` ("Hapus cache"), konfirmasi tindakan.

Jika tombol "Clear data" ("Hapus data") berwarna abu-abu untuk semua aplikasi dan tidak dapat diklik, berarti aplikasi "Security Center" tidak memiliki izin `GET_USAGE_STATS`. Biasanya izin ini diberikan secara bawaan. Belum jelas bagaimana hal ini terjadi, tetapi solusinya adalah memberikan izin tersebut kembali.

Buka: `System Settings` > `Security and privacy` > `More security and privacy` > `Special permissions` > `Usage access` (Pengaturan sistem > Keamanan dan privasi > Keamanan dan privasi lainnya > Izin khusus > Akses penggunaan) > Temukan aplikasi `Security` ("Keamanan") > Berikan izinnya

Sebagai alternatif, jika Anda memiliki akses ADB atau lebih tinggi, jalankan perintah `pm grant com.miui.securitycenter android.permission.PACKAGE_USAGE_STATS`

<a href="/assets/images/appcleaner/miui-usage-stats-1.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-1.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-2.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-2.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-3.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-3.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-4.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-4.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-5.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-5.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-6.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-6.webp" width="100px">
</a>
