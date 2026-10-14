---
title: "Pemilah (Swiper)"
lang: id
translation_key: swiper
permalink: /id/swiper
source_hash: 4c7d8b7a30d3a36e
---
# Pemilah (Swiper)
{: #swiper }

Pemilah (Swiper) membantu Anda merapikan folder tertentu lebih cepat daripada penjelajah berkas. Anda menelusuri berkas satu per satu dan mengusap masing-masing untuk menyimpan, menghapus, atau melewatinya. Tidak ada yang dihapus sampai Anda meninjau pilihan Anda dan mengonfirmasinya.

## Memulai sesi
{: #starting-a-session }

1. Buka Pemilah dari dasbor dan ketuk `Pilih folder`. Anda dapat memilih folder, berkas tunggal, atau keduanya.
2. Jika perlu, atur filter jenis berkas dan urutan penyortiran pada kartu sesi.
3. Ketuk `Pindai`. Folder dicari beserta semua subfoldernya.
4. Ketuk `Mulai memilah`.

Anda juga dapat membuat sesi dari [Penganalisis Penyimpanan](/id/storageanalyzer): pilih item di sana lalu pilih `Buat sesi Memilah`.

Tempat yang baik untuk memulai adalah galeri kamera, folder Downloads, dan tangkapan layar.

### Jenis berkas
{: #file-types }

`Hanya tampilkan tipe file ini` membatasi sesi pada gambar, video, audio, dokumen, atau arsip. Anda juga dapat memasukkan ekstensi sendiri, mis. `apk, apks`. Jika tidak ada yang dicentang, semua berkas ditampilkan. Berkas dicocokkan hanya berdasarkan ekstensinya.

### Urutan penyortiran
{: #sort-order }

Secara bawaan, berkas ditampilkan dari yang terlama, berdasarkan tanggal terakhir diubah. Anda juga dapat mengurutkan dari yang terbaru, berdasarkan nama, atau dari yang terbesar.

Filter dan urutan penyortiran tidak dapat diubah setelah pemindaian menemukan berkas. Untuk mengubahnya, mulailah sesi baru.

### Memindai seluruh penyimpanan
{: #scanning-a-whole-storage }

Jika Anda memilih tingkat teratas sebuah penyimpanan, mis. seluruh penyimpanan internal Anda, setiap berkas di dalamnya akan masuk ke sesi, termasuk foto dan media aplikasi. SD Maid meminta konfirmasi Anda sebelum memindai. Saat nanti Anda menghapus berkas dari sesi seperti itu, Anda harus mencentang `Saya memahami file-file ini akan dihapus secara permanen.` terlebih dahulu.

## Memilah
{: #swiping }

| Gestur | Tindakan bawaan |
|---|---|
| Usap ke kanan | Simpan |
| Usap ke kiri | Hapus |
| Usap ke atas | Lewati |
| Usap ke bawah | Batalkan keputusan terakhir |

Tombol di bagian bawah melakukan hal yang sama. Jika [Tukar arah geser](#swap-swipe-directions) diaktifkan, kiri dan kanan dibalik.

* **Lewati** membiarkan berkas belum diputuskan. Berkas yang dilewati akan muncul kembali setelah berkas lainnya.
* **Batalkan** mundur melalui keputusan terakhir Anda, hingga 50 keputusan.
* **Tekan lama Lewati** untuk mengecualikan berkas dari pemindaian Pemilah berikutnya. Ini membuat [pengecualian](/id/exclusions) jalur dan menghapus berkas dari sesi.
* Tombol pada kartu membuka berkas di aplikasi lain atau menampilkan pratinjau layar penuh.
* Ketuk gambar mini di strip untuk melompat ke berkas tersebut. Mengusapnya lagi akan menggantikan keputusan Anda sebelumnya.

Anda dapat keluar kapan saja. Keputusan Anda disimpan bersama sesi dan Anda dapat melanjutkannya nanti. Saat SD Maid dimulai ulang, berkas yang tidak lagi dapat ditemukan akan dikeluarkan dari sesi. Sampai saat itu, sesi mungkin masih mencantumkan berkas yang telah dihapus atau dipindahkan oleh pihak lain.

## Meninjau dan menghapus
{: #reviewing-and-deleting }

Ketuk `Pratinjau` untuk melihat semua keputusan. Jika tidak ada lagi berkas yang belum diputuskan, tinjauan akan terbuka dengan sendirinya.

* Ubah keputusan per berkas, atau tekan lama untuk memilih beberapa berkas sekaligus.
* Ketuk sebuah berkas untuk kembali ke berkas tersebut.
* `Hapus` menampilkan berapa banyak berkas dan berapa banyak ruang yang terdampak, serta di folder mana, sebelum apa pun terjadi.

Penghapusan bersifat permanen. Berkas tidak dipindahkan ke folder sampah dan SD Maid tidak dapat memulihkannya.

Setelah penghapusan, berkas yang disimpan dan yang dihapus keluar dari sesi. Berkas yang belum diputuskan tetap ada untuk nanti. Setelah tidak ada yang tersisa, sesi dihapus. Jika beberapa berkas tidak dapat dihapus, berkas tersebut tetap berada di sesi dan Anda dapat mencoba lagi.

Menghapus sesi hanya membuang keputusan Anda. Tidak ada berkas yang tersentuh.

### Hal yang perlu diketahui
{: #things-to-know }

* **Simpan hanya berlaku untuk sesi saat ini.** Jika Anda memulai sesi baru pada folder yang sama, berkas yang disimpan akan muncul lagi. Untuk menyembunyikan berkas secara permanen, kecualikan berkas tersebut (tekan lama Lewati).
* **Sesi tidak mengambil berkas baru.** Berkas yang ditambahkan ke folder setelah pemindaian tidak akan muncul. Mulailah sesi baru untuk menyertakannya.
* **Arsip mencakup berkas APK.**
* **Berkas tersembunyi disertakan.**

## Versi gratis
{: #free-version }

Tanpa peningkatan (upgrade), Pemilah dibatasi hingga 2 sesi dengan masing-masing 50 berkas. Jika urutan penyortiran diatur, Anda mendapatkan 50 berkas pertama sesuai urutan tersebut.

## Pengaturan
{: #settings }

### Tukar arah geser
{: #swap-swipe-directions }

Usap ke kiri untuk menyimpan, ke kanan untuk menghapus. Nonaktif secara bawaan.

### Lihat detail berkas
{: #show-file-details }

Menampilkan nama berkas, ukuran, tanggal, dan folder di layar memilah. Aktif secara bawaan.

### Umpan balik haptic
{: #haptic-feedback }

Bergetar saat Anda menyimpan atau menghapus berkas. Aktif secara bawaan.
