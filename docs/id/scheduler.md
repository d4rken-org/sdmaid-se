---
title: "Penjadwal"
lang: id
translation_key: scheduler
permalink: /id/scheduler
source_hash: 74e1452091eaf142
---
# Penjadwal (Scheduler)
{: #scheduler }

Fitur **Penjadwal (Scheduler)** di SD Maid 2 memungkinkan Anda mengotomatiskan tugas pemeliharaan pada interval tertentu, sehingga perangkat Anda tetap optimal tanpa upaya manual. Fitur ini mendukung beberapa alat seperti **Pembersih Aplikasi (AppCleaner)**, **PencariMayat (CorpseFinder)**, dan **Pembersih Sistem (SystemCleaner)**, yang dapat diatur untuk berjalan secara berkala atau pada waktu tertentu.

<img src="/assets/images/scheduler/overview-1.webp" width="150">
<img src="/assets/images/scheduler/overview-2.webp" width="150">
<img src="/assets/images/scheduler/overview-3.webp" width="150">
<img src="/assets/images/scheduler/overview-4.webp" width="150">
<img src="/assets/images/scheduler/overview-5.webp" width="150">
<img src="/assets/images/scheduler/overview-6.webp" width="150">


## Cara Menyiapkan Jadwal
{: #how-to-set-up-a-schedule }

1. Buka **Pengaturan** dan pilih **Penjadwal**.
2. Ketuk tombol **+** untuk membuat jadwal baru atau mengelola jadwal yang sudah ada.
3. Pilih alat mana (mis. **Pembersih Aplikasi**, **PencariMayat**, **Pembersih Sistem**) yang dijalankan sesuai jadwal.
4. Atur **interval pengulangan** (mis. setiap 3 hari) dan **waktu kisaran** pelaksanaannya.
5. Aktifkan jadwal dengan menggeser sakelarnya.

## Mengedit atau Menghapus Jadwal
{: #editing-or-deleting-schedules }

1. Buka pengaturan **Penjadwal** untuk mengelola jadwal Anda.
2. Anda dapat mengubah jadwal yang sudah ada, menyesuaikan interval, atau menghapusnya jika tidak diperlukan lagi.

Setelah jadwal aktif, SD Maid akan menjalankan tugas yang dipilih pada waktu yang ditentukan.

## Opsi Tambahan
{: #additional-options }

Melalui menu pengaturan utama, `Pengaturan > Penjadwal`, tersedia beberapa opsi tambahan.

- **Lewati saat baterai lemah**: Jika perangkat dalam mode hemat daya, tugas terjadwal akan dilewati.
- **Lewati ketika tidak mengisi daya**: Tugas terjadwal hanya akan berjalan saat perangkat sedang diisi daya, sehingga baterai tidak terkuras.
- **Perintah pasca-penjadwalan**: Anda dapat mengatur perintah shell yang dijalankan setelah tugas terjadwal selesai.
- **Layanan aksesibilitas**: Secara bawaan, penjadwal akan melewati fitur yang berbasis layanan aksesibilitas karena fitur tersebut tidak berfungsi saat perangkat terkunci. Jika Anda mengaktifkannya tanpa melakukan perubahan lain pada perangkat, penjadwal akan gagal. Jadi, kecuali Anda tahu apa yang Anda lakukan, biarkan opsi ini nonaktif. Opsi ini hanya berguna dalam kasus khusus seperti perangkat Android TV yang selalu menyala.

## Pemecahan Masalah
{: #troubleshooting }

### Penjadwal tidak berjalan 
{: #scheduler-is-not-executing }

Bedakan antara penjadwal yang sama sekali tidak berjalan dan operasi yang dijalankan tetapi tidak memberikan hasil yang diinginkan. Anda dapat memeriksa riwayat eksekusi di [bagian Riwayat](/id/stats) SD Maid. Jika penjadwal sudah berjalan tetapi gagal, seharusnya ada pesan kesalahan.

Jika penjadwal sama sekali tidak berjalan, mungkin sistem mencegah SD Maid diluncurkan demi menghemat baterai. Setelah mengaktifkan jadwal pertama, Anda seharusnya melihat permintaan berjudul "Saran" dengan tindakan "Memperbaiki" yang meminta Anda menonaktifkan pengoptimalan baterai. Jika Anda tidak sengaja menutup permintaan tersebut, Anda perlu masuk sendiri ke pengaturan sistem untuk SD Maid dan memeriksa apakah SD Maid sedang "dioptimalkan" oleh sistem.

### Layanan aksesibilitas
{: #accessibility-service }
Penghapusan berbasis aksesibilitas, mis. Pembersih Aplikasi yang menghapus "item yang tidak dapat diakses", tidak berfungsi melalui penjadwal. Saat penjadwal berjalan, perangkat biasanya terkunci. Sama seperti Anda tidak dapat mengklik apa pun pada layar yang mati atau terkunci, SD Maid pun tidak bisa.

Secara bawaan, penjadwal tidak melakukan operasi berbasis aksesibilitas. Namun, Anda dapat mengaktifkan opsi "Layanan aksesibilitas" di pengaturan penjadwal jika Anda tahu apa yang Anda lakukan.

Solusi: Root perangkat (aplikasi pengguna + sistem) atau pasang Shizuku (hanya aplikasi pengguna). Kedua modifikasi ini memungkinkan SD Maid melakukan operasi serupa tanpa menggunakan fitur layanan aksesibilitas.
