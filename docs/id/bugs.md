---
title: "Bug"
lang: id
translation_key: bugs
permalink: /id/bugs
source_hash: a218be31a6c4ae63
---
# Bug
{: #bugs }

🐌 🦋 🐛 🐜 🐝 🐞

Bug adalah bagian dari kehidupan sebuah aplikasi. Mereka tidak bermaksud jahat, tetapi juga tidak boleh menetap 😠.

Apa itu bug?

* Jika aplikasi mogok (crash), itu jelas sebuah bug
* Jika sesuatu berperilaku tidak terduga, itu mungkin sebuah bug.

Bagaimanapun, pastikan Anda menggunakan pembaruan terbaru, karena bug tersebut mungkin sudah diperbaiki.

Jika bug belum diperbaiki, laporkanlah :grin:.

## Melaporkan bug
{: #reporting-a-bug }

Buat tiket issue di GitHub:
<https://github.com/d4rken-org/sdmaid-se/issues>

* Jelaskan apa yang Anda harapkan dan apa yang sebenarnya terjadi
* Cantumkan langkah-langkah yang harus dilakukan orang lain untuk mereproduksi masalah tersebut
* Jelaskan konfigurasi Anda
    * Jenis perangkat Anda (mis. Pixel 5)
    * Versi Android (mis. Android 13.1)
    * Jika perangkat Anda di-root, versi Magisk

Sertakan [catatan debug](#debug-log) jika Anda dapat mereproduksi masalahnya. 

Petunjuk: Anda dapat [menekan lama entri "Catatan perubahan"](https://github.com/d4rken-org/sdmaid-se/pull/940) di pengaturan SD Maid untuk menyalin versi ke papan klip.

## Catatan debug
{: #debug-log }

Catatan debug adalah rekaman tindakan SD Maid. Anda memulai perekaman, mereproduksi masalah, lalu menghentikannya, dan kemudian dapat membagikan berkas zip berisi uraian kronologis tentang apa yang dilakukan SD Maid.

Untuk mulai merekam catatan debug:
* Buka pengaturan SD Maid dengan menekan ikon pengaturan di kanan bawah dasbor
* Temukan entri `Bantuan` di daftar pengaturan
* Ketuk `Rekam log debug` di layar bantuan, lalu ketuk `Rekam log debug` lagi di dialog
* Selama perekaman, dasbor menampilkan kartu `Merekam jejak debug`
* **Sekarang reproduksi masalah yang ingin Anda rekam**
* Setelah Anda mereproduksi masalahnya, ketuk `Berhenti merekam` di kartu dasbor tersebut atau di layar bantuan. Jika rekamannya sangat singkat, SD Maid akan bertanya terlebih dahulu; ketuk `Tetap hentikan` untuk menghentikannya.
* Layar `Catatan debug` akan terbuka. Setelah berkas log selesai dikompresi, ketuk `Bagikan` untuk mengirimkannya.

Catatan debug akan terus direkam sampai Anda menghentikannya. Perekaman tetap berlanjut meskipun Anda memaksa berhenti lalu memulai ulang SD Maid.

Jika Anda memecahkan masalah yang berkaitan dengan fungsi inti, mis. deteksi root atau area penyimpanan yang terdeteksi, ada gunanya memaksa berhenti lalu memulai ulang SD Maid saat merekam, karena sebagian data hanya diinisialisasi sekali per sesi aplikasi.

Berkas catatan debug disimpan di `/storage/emulated/0/Android/data/eu.darken.sdmse/cache/debug/logs`.

Dalam gambar:

<a href="/assets/images/bugs/debug-log-1.webp"><img src="/assets/images/bugs/debug-log-1.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-2.webp"><img src="/assets/images/bugs/debug-log-2.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-3.webp"><img src="/assets/images/bugs/debug-log-3.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-4.webp"><img src="/assets/images/bugs/debug-log-4.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-5.webp"><img src="/assets/images/bugs/debug-log-5.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-6.webp"><img src="/assets/images/bugs/debug-log-6.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-7.webp"><img src="/assets/images/bugs/debug-log-7.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-8.webp"><img src="/assets/images/bugs/debug-log-8.webp" width="100" alt=""></a>
