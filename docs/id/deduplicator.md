---
title: "Penghapus Duplikat (Deduplicator)"
lang: id
translation_key: deduplicator
permalink: /id/deduplicator
source_hash: 08f0bb39baf9fb1e
---
# Penghapus Duplikat (Deduplicator)
{: #deduplicator }

Penghapus Duplikat (Deduplicator) menemukan berkas duplikat di perangkat Anda: salinan yang persis sama, dan secara opsional gambar, video, atau audio yang terlihat atau terdengar mirip. Alat ini mengelompokkannya ke dalam set dan menghapus semua berkas kecuali satu per set.

Pemindaian gratis. Penghapusan memerlukan peningkatan (upgrade).

## Metode deteksi
{: #detection-methods }

Anda dapat mengaktifkan setiap metode di `Pengaturan > Penghapus Duplikat`.

### Checksum konten
{: #content-checksum }

Aktif secara bawaan. Menemukan berkas dengan isi yang persis sama, byte demi byte, apa pun namanya. Ini adalah metode yang aman: setiap berkas dalam set seperti ini adalah salinan sejati.

### Hash persepsual
{: #perceptual-hash }

Nonaktif secara bawaan. Menemukan gambar yang terlihat mirip, mis. foto yang sama yang disimpan dalam format atau ukuran berbeda, atau dengan sedikit suntingan. Gambar dibandingkan dalam skala abu-abu, sehingga gambar yang hanya berbeda warna dapat dianggap cocok.

### Sidik jari media
{: #media-fingerprint }

Nonaktif secara bawaan. Menemukan berkas video dan audio yang terdengar mirip, serta video tanpa suara yang terlihat mirip. Hanya berkas dengan durasi yang kurang lebih sama yang dibandingkan.

Berkas yang mirip tidak identik. Foto beruntun (burst), versi yang disunting, atau salinan yang diubah ukurannya bisa masuk ke set yang sama. **Periksa set yang ditemukan oleh Hash persepsual atau Sidik jari media sebelum menghapusnya.** Setiap berkas yang mirip menunjukkan seberapa dekat kecocokannya.

## Apa yang dipindai
{: #what-gets-scanned }

Secara bawaan, Penghapus Duplikat mencari di semua penyimpanan yang dapat diakses: penyimpanan internal, kartu SD, penyimpanan USB, `Android/data`, dan `Android/media`. `Android/obb` tidak disertakan. Anda dapat membatasi pencarian di [Lokasi pencarian](#search-locations).

Akses ke `Android/data` di Android 13 dan yang lebih baru memerlukan [root](/id/setup#root-access) atau [akses ADB](/id/setup#shizuku).

Hanya berkas di atas [ukuran minimum](#minimum-size) yang diperhitungkan. Secara bawaan hanya jenis berkas umum yang disertakan, lihat [Lewati jenis file yang tidak umum](#skip-uncommon-file-types).

## Berkas mana yang disimpan
{: #which-file-is-kept }

Saat menghapus sebuah set, SD Maid menyimpan satu berkas dan menghapus sisanya. Berkas mana yang disimpan ditentukan oleh strategi penghapusan di `Pengaturan > Penghapus Duplikat > Strategi penghapusan`.

SD Maid memeriksa setiap aturan secara berurutan. Aturan pertama yang menghasilkan pemenang yang jelas menjadi penentu. Seret aturan untuk mengubah urutannya, ketuk aturan untuk mengubah modenya.

| Aturan | Bawaan |
|---|---|
| Jenis duplikat | Utamakan salinan yang persis sama daripada berkas yang mirip |
| Lokasi pilihan | Simpan berkas di folder yang Anda pilih. Secara bawaan tidak ada folder yang diatur. |
| Pengindeksan media | Simpan berkas yang dikenal oleh galeri dan aplikasi media lainnya |
| Lokasi penyimpanan | Simpan berkas di penyimpanan internal. Kartu SD dan penyimpanan USB dihitung sebagai eksternal. |
| Kedalaman folder | Simpan berkas di folder yang tidak terlalu dalam |
| Tanggal modifikasi | Simpan berkas yang lebih lama |
| Ukuran file | Simpan berkas yang lebih besar |

**Lokasi pilihan** membantu memilih berkas mana yang disimpan di dalam sebuah grup, bersama aturan lainnya. Aturan ini tidak memilih di antara grup. Dalam set yang berisi salinan persis dan berkas mirip sekaligus, Jenis duplikat menentukan grup mana yang disimpan: secara bawaan, setiap berkas dalam grup yang mirip dihapus, bahkan jika berada di lokasi pilihan. Misalnya, jika foto kamera Anda dan dua salinan identik yang diubah ukurannya dari aplikasi pesan berada dalam satu set, pengaturan bawaan akan menyimpan satu salinan dari aplikasi pesan dan menghapus foto asli dari kamera. Periksa set seperti itu sebelum menghapusnya, dan [kecualikan](/id/exclusions) folder yang berkasnya tidak boleh dihapus.

Tidak ada cara untuk menandai satu berkas tertentu sebagai berkas yang akan disimpan. Untuk menyimpan berkas lain, ubah strategi penghapusan, atau pilih sendiri berkas yang akan dihapus.

Mengubah strategi penghapusan akan membuang hasil saat ini. Pindai lagi setelahnya.

## Menghapus
{: #deleting }

### Menghapus seluruh set
{: #deleting-a-whole-set }

Menghapus sebuah set akan menyimpan satu berkas dan menghapus **setiap berkas lain dalam set tersebut**. Dalam set yang berisi berkas mirip, ini termasuk berkas yang hanya mirip dengan berkas yang disimpan, bukan identik.

Ubah daftar hasil ke tata letak daftar untuk melihat ikon hapus pada setiap berkas yang akan terhapus jika seluruh set dihapus. Tata letak kisi bawaan tidak menampilkan ikon ini.

### Menghapus berkas satu per satu
{: #deleting-individual-files }

Pilih berkas dengan menekan lama, lalu hapus. Setidaknya satu berkas per set harus tetap ada, kecuali Anda mengaktifkan [Izinkan "Hapus semua"](#make-delete-all-possible).

Di detail set, tampilan `Folder` mengelompokkan berkas berdasarkan folder. Menghapus sebuah folder di sana akan menghapus duplikat di folder tersebut. Jika folder itu berisi setiap berkas dari sebuah set, berkas yang akan disimpan saat seluruh set dihapus tidak ikut dihapus. Jika [Izinkan "Hapus semua"](#make-delete-all-possible) diaktifkan, konfirmasinya memungkinkan Anda menghapus semuanya.

### Menghapus dari dasbor
{: #deleting-from-the-dashboard }

Menghapus dari kartu dasbor Penghapus Duplikat akan menghapus semua berkas kecuali satu di setiap set, setelah konfirmasi. Apakah tombol utama dasbor juga menjalankan Penghapus Duplikat diatur di [Sekali Ketuk](#one-tap).

## Pengecualian
{: #exclusions }

Jalur yang dikecualikan untuk Penghapus Duplikat, atau secara umum, tidak dipindai. Mengecualikan sebuah set dari hasil akan mengecualikan setiap berkas di dalamnya, termasuk berkas yang seharusnya disimpan. Lihat [Pengecualian](/id/exclusions).

Penghapus Duplikat tidak dapat dijalankan oleh [Penjadwal](/id/scheduler).

## Sekali Ketuk
{: #one-tap }

`Pengaturan > Umum > Alat Sekali Ketuk` menentukan apakah tombol utama dasbor, pintasan `Pindai + Hapus`, dan widget menyertakan Penghapus Duplikat. Secara bawaan nonaktif. Pintasan dan widget memindai dan menghapus tanpa konfirmasi, menggunakan strategi penghapusan Anda. Di dasbor, `Mode Satu Ketukan` melakukan hal yang sama, tetapi jika hasil sudah menunggu, Hapus tetap meminta konfirmasi terlebih dahulu. Sertakan Penghapus Duplikat hanya jika [Checksum konten](#content-checksum) adalah satu-satunya metode deteksi.

## Pengaturan
{: #settings }

### Lokasi pencarian
{: #search-locations }

Folder yang dicari. Bawaannya semua area yang dapat diakses. Tekan lama untuk mengatur ulang.

### Strategi penghapusan
{: #deletion-strategy }

Berkas mana dalam sebuah set yang disimpan, lihat [Berkas mana yang disimpan](#which-file-is-kept).

### Izinkan "Hapus semua"
{: #make-delete-all-possible }

Menampilkan opsi untuk menghapus setiap berkas dalam sebuah set, sehingga tidak ada salinan yang tersisa. Nonaktif secara bawaan.

### Ukuran minimum
{: #minimum-size }

Lewati berkas di bawah ukuran ini. Bawaannya 512 KB.

### Lewati jenis file yang tidak umum
{: #skip-uncommon-file-types }

Hanya sertakan gambar, video, berkas audio, dokumen, arsip, dan berkas APK yang umum. Aktif secara bawaan. Jenisnya ditentukan oleh ekstensi berkas.

### Metode deteksi
{: #detection-method }

Aktifkan [Checksum konten](#content-checksum), [Hash persepsual](#perceptual-hash), dan [Sidik jari media](#media-fingerprint).
