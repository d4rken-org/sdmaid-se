---
title: "Optimasi Media (Media Squeeze)"
lang: id
translation_key: mediasqueeze
permalink: /id/mediasqueeze
source_hash: 1d878fd5a77634c5
---
# Optimasi Media (Media Squeeze)
{: #media-squeeze }

Optimasi Media (Media Squeeze) mengompres foto dan video agar memakan lebih sedikit ruang. Anda mengorbankan sebagian kualitas demi ruang yang didapat kembali.

**Kompresi menurunkan kualitas secara permanen dan tidak dapat dibatalkan.** Berkas asli diganti dan tidak ada salinan yang disimpan. Periksa pengaturan dan pratinjau Anda sebelum memulai.

Pemindaian gratis. Kompresi memerlukan peningkatan (upgrade).

## Cara kerjanya
{: #how-it-works }

1. Pilih folder yang akan dicari. Tidak ada lokasi bawaan, mis. pilih folder kamera Anda.
2. Atur kualitas kompresi dan umur minimal.
3. Pindai. Setiap hasil menampilkan ukurannya dan perkiraan penghematan.
4. Ketuk satu item, pilih beberapa item dengan menekan lama, atau gunakan `Kompres semua`. Konfirmasi menampilkan total ukuran dan perkiraan penghematan terlebih dahulu.

Jika pilihan hanya berisi foto atau hanya video, `Lihat perbandingan` di konfirmasi menampilkan versi asli dan versi terkompresi berdampingan. Untuk video, perbandingannya berupa satu bingkai dan hanya perkiraan.

### Apa yang terjadi pada sebuah berkas
{: #what-happens-to-a-file }

* Berkas dienkode ulang dalam formatnya sendiri: JPEG tetap JPEG, WebP tetap WebP, MP4 tetap MP4.
* Resolusi gambar tetap sama, kecuali untuk [foto yang sangat besar](#compress-very-large-photos).
* Untuk video, hanya trek video yang dienkode ulang dengan bitrate lebih rendah. Trek audio disalin tanpa perubahan.
* Tanggal, lokasi, kamera, dan metadata EXIF lainnya pada foto dipertahankan. Jika SD Maid tidak dapat membaca atau mempertahankan metadata EXIF sebuah foto, foto tersebut dibiarkan tanpa perubahan.
* Metadata XMP tidak dipertahankan, mis. informasi photo sphere dan panorama atau rating dari aplikasi penyunting foto.
* Tanggal modifikasi berkas dipertahankan, sehingga galeri tetap mengurutkannya di tempat yang sama.
* Jika versi terkompresi tidak lebih kecil, berkas asli dibiarkan tidak tersentuh.

Perkiraan penghematan adalah tebakan kasar berdasarkan jenis berkas dan kualitas, bukan hasil pengukuran. Penghematan sebenarnya bisa berbeda.

### Lokasi
{: #locations }

Optimasi Media memerlukan akses langsung ke berkas dan akses tulis ke foldernya. Alat ini tidak menggunakan root atau ADB. Beberapa lokasi yang dipilih melalui pemilih folder Android, mis. drive USB, tidak dapat digunakan dan dihapus dari pilihan.

## Berkas yang pernah dikompresi
{: #previously-compressed-files }

SD Maid mengingat berkas mana yang sudah dikompresinya, serta berkas mana yang tidak menjadi lebih kecil, dan melewatinya pada pemindaian berikutnya. SD Maid mengenali berkas berdasarkan isinya, sehingga ini tetap berfungsi setelah berkas diganti nama atau dipindahkan. Mengompres berkas lagi akan mengorbankan lebih banyak kualitas dan hanya menghemat sedikit.

Riwayat ini disimpan di perangkat Anda dan menjadi bagian dari cadangan pengaturan SD Maid. Menghapus riwayat membuat berkas yang pernah dikompresi dapat diproses lagi, kecuali berkas tersebut membawa penanda EXIF dan [Tulis penanda EXIF](#write-exif-marker) diaktifkan. Menghapus riwayat tidak mengubah berkas apa pun.

## Foto yang dilewati secara bawaan
{: #photos-that-are-skipped-by-default }

Beberapa foto berisi data yang tidak dapat dipertahankan oleh kompresi. SD Maid melewatinya jika mendeteksi data tersebut, kecuali Anda mengaktifkan pengaturan yang sesuai. Deteksinya tidak lengkap: foto dalam format yang tidak dikenali akan dikompresi dan kehilangan data tersebut.

* **Foto HDR dan kedalaman**: foto HDR menjadi foto biasa, dan foto potret kehilangan data di balik efek buram latar belakangnya.
* **Motion Photo**: gambar diamnya dipertahankan, klip videonya dihapus.
* **Foto yang sangat besar** (lebar atau tinggi 8192 piksel atau lebih): lebar dan tingginya dibagi dua, dan dibagi dua lagi jika perlu, hingga keduanya di bawah 8192 piksel. Panorama selebar 20.000 piksel akhirnya menjadi selebar 5.000 piksel.

Berkas HEIC yang berisi beberapa gambar selalu dilewati.

Ringkasan pemindaian menghitung foto yang dilewati karena data HDR atau kedalaman, Motion Photo, dan ukuran yang sangat besar, serta berkas yang tidak dapat diakses. Berkas yang dilewati karena pernah dikompresi tidak dihitung, begitu pula berkas HEIC multi-gambar kecuali berkas tersebut juga memiliki data HDR atau kedalaman.

## Pengaturan
{: #settings }

Kualitas kompresi, lokasi pencarian, dan umur minimal diatur di layar Optimasi Media sebelum pemindaian. Semua pengaturan lainnya ada di `Pengaturan > Optimasi Media`.

### Kualitas kompresi
{: #compression-quality }

Bawaannya 80. Kualitas yang lebih rendah berarti berkas lebih kecil tetapi kualitas visual menurun. Di bawah 40, gambar mungkin menjadi tidak dapat digunakan. Pada 95 ke atas, hanya sedikit yang bisa dihemat.

### Umur minimal
{: #minimum-age }

Hanya sertakan berkas yang lebih lama dari ini. Bawaannya 90 hari, berdasarkan tanggal modifikasi berkas.

### Ukuran file minimum
{: #minimum-file-size }

Lewati berkas yang lebih kecil dari ini. Bawaannya 512 KB.

### JPEG
{: #jpeg }

Sertakan gambar JPEG (`.jpg`, `.jpeg`). Aktif secara bawaan.

### WebP
{: #webp }

Sertakan gambar WebP (`.webp`). Aktif secara bawaan. Berkas WebP animasi tidak terdeteksi dan akan kehilangan animasinya.

### HEIC/HEIF
{: #heicheif }

Sertakan gambar HEIC/HEIF (`.heic`, `.heif`). Nonaktif secara bawaan. Memerlukan Android 9 atau yang lebih baru. Gain map HDR, data kedalaman, dan bagian Live/Motion Photo tidak dapat dipertahankan untuk HEIC.

### Video MP4
{: #mp4-video }

Sertakan video MP4 (`.mp4`). Nonaktif secara bawaan. Mengompres video memerlukan ruang kosong sekitar 110% dari ukurannya di penyimpanan yang sama.

### Kompres foto HDR & kedalaman
{: #compress-hdr--depth-photos }

Lihat [Foto yang dilewati secara bawaan](#photos-that-are-skipped-by-default). Nonaktif secara bawaan.

### Kompres Motion Photo
{: #compress-motion-photos }

Mempertahankan gambar diam dan menghapus klip video. Nonaktif secara bawaan.

### Kompres foto yang sangat besar
{: #compress-very-large-photos }

Menyertakan foto dengan lebar atau tinggi 8192 piksel atau lebih, dan menurunkan resolusinya, lihat [Foto yang dilewati secara bawaan](#photos-that-are-skipped-by-default). Nonaktif secara bawaan.

### Lewati yang sudah dikompresi
{: #skip-previously-compressed }

Lewati berkas yang sudah dikompresi oleh SD Maid. Aktif secara bawaan.

### Tulis penanda EXIF
{: #write-exif-marker }

Menambahkan penanda ke komentar pengguna EXIF pada foto, sehingga SD Maid mengenalinya sebagai sudah dikompresi bahkan setelah riwayat kompresi dihapus. SD Maid hanya memeriksa penanda tersebut selama pengaturan ini aktif. Nonaktif secara bawaan. Hanya berlaku untuk JPEG dan WebP.

### Riwayat kompresi
{: #compression-history }

Menampilkan berapa banyak berkas yang ada dalam riwayat. Ketuk untuk menghapus riwayat.

## Pengecualian
{: #exclusions }

Optimasi Media melewati jalur yang [dikecualikan](/id/exclusions) secara umum. Anda juga dapat mengecualikan hasil dari daftar hasil, yang hanya memengaruhi Optimasi Media.

Optimasi Media tidak dapat dijalankan oleh [Penjadwal](/id/scheduler).
