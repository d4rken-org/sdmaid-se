---
title: "Pengendali Aplikasi (AppControl)"
lang: id
translation_key: appcontrol
permalink: /id/appcontrol
source_hash: 1a4a7450fc5b86ca
---
# Pengendali Aplikasi (AppControl)
{: #appcontrol }
Pusat serba ada untuk semua tindakan yang berkaitan dengan pengendalian aplikasi terpasang. Setelah pemindaian, Anda akan mendapatkan daftar aplikasi terpasang yang dapat dicari. Aplikasi sistem tidak ditampilkan secara bawaan, ini harus diaktifkan di pengaturan.
Beberapa opsi mungkin hanya tersedia (dapat dilakukan) jika SD Maid telah diberi izin root.

Beberapa tindakan dapat dijalankan pada beberapa aplikasi sekaligus, sebagian lainnya hanya pada satu item. Untuk menjalankan tindakan pada beberapa aplikasi, tekan lama sebuah aplikasi, lalu ketuk untuk memilih aplikasi tambahan.

## Tindakan
{: #actions }
Beberapa tindakan ini hanya dapat diakses di detail aplikasi, yang dapat dibuka dengan mengetuk sebuah entri di daftar.

### Jalankan aplikasi
{: #run-app }
Meluncurkan aplikasi, yaitu aktivitas utamanya, biasanya tindakan yang sama dengan yang dijalankan launcher.

### Paksa berhenti
{: #force-stop }
Mencoba memaksa aplikasi berhenti. Paling baik bekerja dengan Root atau ADB/Shizuku, jika tidak, akan menggunakan layanan aksesibilitas.

### Ekspor apk
{: #export-apk }
Mencoba menyalin berkas `.apk` aplikasi dari penyimpanan privat internal ke direktori unduhan di penyimpanan publik Anda. Jika aplikasi terenkripsi, tindakan ini memerlukan root. Aplikasi yang tidak terenkripsi juga dapat diekspor tanpa root.

### Bekukan aplikasi
{: #freeze-app }
Memerlukan root atau ADB/Shizuku. Menonaktifkan aplikasi. Aplikasi tidak akan terlihat dan tidak dapat memulai dirinya sendiri. Aplikasi harus diaktifkan kembali agar dapat berfungsi. Tidak ada data yang hilang, dan status ini tetap bertahan meskipun Anda mencopot SD Maid. Aplikasi pengaktif/penonaktif aplikasi mana pun yang bagus dapat mengaktifkan aplikasi yang dinonaktifkan dengan SD Maid, begitu pula sebaliknya.

### Hapus aplikasi
{: #delete-app }
Menghapus aplikasi beserta datanya.


## Pengaturan
{: #settings }
### Tentukan ukuran
{: #determine-size }
Dapat dinonaktifkan untuk mempercepat pemuatan daftar aplikasi. Menentukan seberapa besar sebuah aplikasi memerlukan banyak waktu.

### Tentukan aktivitas
{: #determine-activity }
Dapat dinonaktifkan untuk mempercepat pemuatan daftar aplikasi. Menentukan apakah sebuah aplikasi baru-baru ini aktif memerlukan banyak waktu.
