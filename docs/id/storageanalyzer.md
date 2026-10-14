---
title: "Penganalisis Penyimpanan (Storage Analyzer)"
lang: id
translation_key: storageanalyzer
permalink: /id/storageanalyzer
source_hash: 2dbf8557900cc687
---
# Penganalisis Penyimpanan (Storage Analyzer)
{: #storage-analyzer }

Penganalisis Penyimpanan (StorageAnalyzer) menjawab pertanyaan "Apa yang memakan begitu banyak ruang di penyimpanan saya?"

Alat ini mungkin terlihat seperti penjelajah berkas, tetapi bukan penjelajah berkas. Semuanya diurutkan berdasarkan ukuran, sehingga Anda dapat menelusuri item terbesar hingga menemukan apa yang sebenarnya memenuhi penyimpanan Anda.

## Penyimpanan
{: #storages }

Layar pertama menampilkan setiap penyimpanan di perangkat Anda:

* **Penyimpanan utama**: penyimpanan bawaan perangkat Anda, termasuk aplikasi dan data sistem.
* **Penyimpanan cadangan**: biasanya kartu SD yang dapat dilepas.
* **Penyimpanan eksternal**: mis. flash drive yang terhubung melalui USB.

Setiap kartu menampilkan ruang kosong dan terpakai, serta seberapa besar perubahan penyimpanan selama 7 hari terakhir, lihat [Tren Penyimpanan](#storage-trend).

## Kategori
{: #categories }

Membuka sebuah penyimpanan akan memindainya dan membagi ruang terpakai ke dalam beberapa kategori:

* **Aplikasi**: aplikasi terpasang beserta kode, data, cache, dan medianya.
* **Berkas pengguna**: foto, video, unduhan, dan data lain yang bukan milik aplikasi tertentu.
* **Data sistem**: sistem operasi dan segala sesuatu yang tidak dapat dikelompokkan ke tempat lain.
* **Pengguna lain**: aplikasi dan berkas milik pengguna lain atau profil kerja di perangkat ini.

Data sistem dan Pengguna lain hanya ada untuk penyimpanan utama.

### Aplikasi
{: #apps }

Aplikasi diurutkan berdasarkan ukuran dan dapat dicari berdasarkan nama. Aplikasi sistem juga dapat dicantumkan di sini, termasuk datanya. Untuk aplikasi sistem tanpa pembaruan yang terpasang, kode aplikasi bawaan dihitung sebagai Data sistem. Membuka sebuah aplikasi menunjukkan ke mana ruangnya digunakan:

* **Kode aplikasi**: aplikasi itu sendiri. Mencopot atau mengarsipkan aplikasi akan mengosongkan ruang ini.
* **Data aplikasi**: data, pengaturan, dan cache.
* **Media aplikasi**: berkas media milik aplikasi ini, mis. foto WhatsApp yang diterima.
* **File aplikasi lainnya**: data yang diletakkan aplikasi di luar foldernya sendiri, mis. foto atau unduhan di folder tingkat teratas.

`Tampilkan pengaturan` membuka halaman pengaturan sistem untuk aplikasi tersebut.

### Mengapa angkanya tidak sama dengan pengaturan Android
{: #why-the-numbers-dont-match-androids-settings }

Kategori dari penganalisis tidak akan sama persis dengan rincian di pengaturan Android.

**Data sistem** adalah sisa ruang terpakai setelah Aplikasi, Berkas pengguna, dan Pengguna lain dihitung. Apa pun yang tidak dapat dilihat atau dikelompokkan oleh SD Maid akan masuk ke sana: area sistem yang dilindungi, penyimpanan pengguna lain yang tidak dapat diukur, overhead sistem berkas, dan selisih antara ukuran penyimpanan yang diiklankan dan ukuran sebenarnya.

Tanpa izin [Inventaris aplikasi](/id/setup#app-inventory) dan [Statistik penggunaan](/id/setup#usage-statistics), aplikasi tidak dapat diukur. Ruang yang digunakannya kemudian muncul sebagai Data sistem.

## Akses
{: #access }

Apa yang dapat dilihat penganalisis bergantung pada akses yang dimiliki SD Maid:

* **Tanpa root**, kode aplikasi dan data aplikasi privat hanya ditampilkan sebagai total dan tidak dapat dijelajahi. Di Android 13 dan yang lebih baru, `Android/data` juga memerlukan root atau akses ADB.
* **Dengan [root](/id/setup#root-access) atau [akses ADB](/id/setup#shizuku)**, Anda dapat menjelajahi Data sistem. Root juga memungkinkan penjelajahan kode aplikasi dan data aplikasi privat, serta pengukuran penuh pengguna lain.
* **Tanpa izin Inventaris aplikasi**, berkas tidak dapat dicocokkan dengan aplikasi pemiliknya. Berkas pengguna kemudian ditampilkan hanya-baca: Anda dapat melihat ukurannya, tetapi penghapusan, pengecualian, pembuatan filter, dan sesi Pemilah dinonaktifkan.

Item yang tidak dapat diakses SD Maid akan menampilkan `Tidak ada akses langsung yang tersedia` saat diketuk.

Folder dengan lebih dari 100.000 item hanya dihitung totalnya dan tidak dapat dijelajahi.

## Tindakan
{: #actions }

Ketuk sebuah berkas untuk membukanya di aplikasi lain. Tekan lama item untuk memilihnya, lalu:

* **Hapus**: menghapus item yang dipilih setelah konfirmasi. Gratis digunakan.
* **Kecualikan**: membuat [pengecualian](/id/exclusions) untuk jalur yang dipilih. Pengecualian ini berlaku untuk semua alat. Penganalisis sendiri tidak menggunakan pengecualian, karena perlu melihat semuanya untuk menghitung total yang benar.
* **Filter**: membuat [filter khusus Pembersih Sistem](/id/systemcleaner#custom-filters) untuk item yang dipilih. Memerlukan peningkatan (upgrade).
* **Buat sesi Memilah**: memulai sesi [Pemilah](/id/swiper) dengan item yang dipilih.

Data sistem dan konten pengguna lain hanya dapat dijelajahi. Penghapusan di sana tidak dimungkinkan.

## Tren Penyimpanan
{: #storage-trend }

SD Maid mencatat ruang kosong penyimpanan Anda setiap beberapa jam dan setelah setiap pembersihan. `Tren Penyimpanan` menampilkannya sebagai grafik, dengan penanda seberapa banyak ruang yang dikosongkan oleh setiap pembersihan.

* Kartu penyimpanan menunjukkan seberapa besar perubahan setiap penyimpanan dalam 7 hari terakhir.
* Dasbor menampilkan gabungan perubahan semua penyimpanan, atau sebagai gantinya perkiraan sisa hari ketika penyimpanan semakin penuh.
* Lamanya snapshot disimpan diatur di `Pengaturan > Riwayat > Riwayat penyimpanan`. Bawaannya 90 hari, hingga 365.

Grafik Tren Penyimpanan memerlukan peningkatan (upgrade). Perubahan 7 hari pada kartu penyimpanan dan dasbor, serta perkiraan sisa hari, tersedia gratis.

## Pengaturan
{: #settings }

### Ambang batas penyimpanan rendah
{: #low-storage-threshold }

Ketika ruang kosong turun di bawah ambang ini, SD Maid menganggap penyimpanan hampir habis. Bawaannya `Otomatis`: 5% dari ukuran penyimpanan, paling banyak 2 GB.

### Peringatan ruang rendah
{: #low-space-warning }

Memerlukan peningkatan (upgrade). Memberi peringatan ketika ruang kosong di penyimpanan utama mencapai ambang batas penyimpanan rendah. Dengan riwayat yang konsisten selama setidaknya 5 hari, peringatan juga dapat muncul lebih awal: ketika penyimpanan diperkirakan mencapai ambang batas dalam 14 hari dan ruang kosong sudah paling banyak 20% dari ukuran penyimpanan, dibatasi hingga 25 GB. Nonaktif secara bawaan.
