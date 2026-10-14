---
title: "PencariMayat (Corpse Finder)"
lang: id
translation_key: corpsefinder
permalink: /id/corpsefinder
source_hash: 9e16286f0fda1dc8
---
# PencariMayat (Corpse Finder)
{: #corpse-finder }

Alat `PencariMayat` (CorpseFinder) menemukan berkas milik aplikasi yang sudah tidak terpasang.


## Tingkat risiko
{: #risklevel }

Secara bawaan, semua opsi tingkat risiko nonaktif. Aktifkan hanya jika Anda tahu apa yang Anda lakukan, dan periksa hasilnya terlebih dahulu sebelum menghapus apa pun.
Mengaktifkan opsi tingkat risiko meningkatkan kemungkinan terhapusnya berkas yang sebenarnya tidak ingin Anda hapus.

Tingkat risiko sebagian besar berlaku untuk lokasi daftar putih, seperti akar penyimpanan publik Anda (`/storage/emulated/0`). Lokasi seperti ini tidak memiliki pola penamaan yang umum, dan SD Maid hanya dapat menentukan kepemilikan melalui basis data yang dikelola secara manual.


### Sertakan sisa-sisa yang diinginkan
{: #include-desirable-remnants }

Sisa-sisa yang diinginkan adalah berkas dengan pemilik yang dikenal dan tidak terpasang, tetapi ditandai secara internal oleh SD Maid sebagai `KEEPER`. Biasanya ini adalah berkas buatan pengguna, mis. foto yang Anda ambil.

### Sertakan nama umum
{: #include-common-names }

Beberapa aplikasi menggunakan nama yang sangat umum. Mungkin ada ratusan aplikasi pencatat yang membuat folder `/sdcard/Notes` untuk menyimpan data. SD Maid mungkin mengenal salah satu aplikasi tersebut dan aplikasi itu tidak terpasang di ponsel Anda, tetapi SD Maid tidak dapat memastikan bahwa tidak ada aplikasi lain di ponsel Anda yang masih menggunakan folder tersebut, sementara SD Maid tidak tahu bahwa aplikasi lain itu juga menggunakannya. 

## Filter
{: #filter }

Setiap filter bertanggung jawab atas area data tertentu. Dengan mengaktifkan sebuah filter, PencariMayat akan mencari di area tambahan tersebut.

### Kartu SD
{: #sdcard }

* Biasanya tempat pengguna meletakkan berkas
* Penyimpanan publik (bawaan maupun yang dapat dilepas), tidak termasuk sub-direktori tertentu di `Android/data`.
* Berkas di sini tidak memiliki skema penamaan.

Contoh:

```bash
/storage/emulated/0/SDMaid
/storage/DEAD-BEEF/SDMaid
/sdcard/SDMaid
```

### Media umum
{: #public-media }

* Menyimpan media publik aplikasi, mis. media yang diterima melalui WhatsApp. Folder dinamai sesuai nama paket aplikasi.
* Sub-direktori penyimpanan publik yang memiliki arti khusus bagi sistem operasi. 

Contoh:

```bash
/storage/emulated/0/Android/media/eu.darken.sdmse
/storage/DEAD-BEEF/Android/media/eu.darken.sdmse
/sdcard/Android/media/eu.darken.sdmse
```

### Data aplikasi umum
{: #public-app-data }

* Berkas tambahan aplikasi.
* Folder dinamai sesuai nama paket aplikasi.
* Sub-direktori penyimpanan publik yang memiliki arti khusus bagi sistem operasi.

Contoh:

```bash
/storage/emulated/0/Android/data/eu.darken.sdmse
/storage/DEAD-BEEF/Android/data/eu.darken.sdmse
/sdcard/Android/data/eu.darken.sdmse
```

### Sumber OBB
{: #obb-resources }

* Biasanya berkas sumber daya tambahan untuk game.
* Folder & berkas dinamai sesuai nama paket aplikasi.
* Sub-direktori penyimpanan publik yang memiliki arti khusus bagi sistem operasi.

Contoh:

```bash
/storage/emulated/0/Android/obb/eu.darken.sdmse
/storage/DEAD-BEEF/Android/obb/eu.darken.sdmse
/sdcard/Android/obb/eu.darken.sdmse
```

### Data aplikasi pribadi
{: #private-app-data }

* Folder & berkas dinamai sesuai nama paket aplikasi.
* Sub-direktori penyimpanan publik yang memiliki arti khusus bagi sistem operasi.

Contoh:

```bash
/data/data/eu.darken.sdmse
```

### Dalvik cache
{: #dalvik-cache }

* Menyimpan versi kode aplikasi yang dioptimalkan agar eksekusi lebih cepat.
* Terletak di partisi sistem, sering kali di bawah direktori khusus arsitektur.
* Berkas dinamai menggunakan nama paket aplikasi.

Contoh:

```bash
/data/dalvik-cache/arm64/data@app@eu.darken.sdmse@base.apk@classes.dex
/data/dalvik-cache/arm/data@app@eu.darken.sdmse@base.apk@classes.dex
```

### Pustaka aplikasi
{: #app-libraries }

* Berisi pustaka native yang digunakan aplikasi, seperti berkas `.so`.
* Biasanya terletak di direktori data aplikasi, di bawah folder `lib`.
* Berkas dinamai sesuai arsitektur dan kebutuhan pustaka.

Contoh:

```bash
/data/app/eu.darken.sdmse-1/lib/arm64/libexample.so
/data/app/eu.darken.sdmse-2/lib/arm/libexample.so
```

### Sumber aplikasi
{: #app-sources }

* Merujuk pada berkas APK yang menjadi kode sumber aplikasi.
* Disimpan di direktori khusus untuk aplikasi sistem, pengguna, atau vendor.
* Berkas dinamai sesuai nama paket dan versi aplikasi.

Contoh:

```bash
/data/app/eu.darken.sdmse-1/base.apk
/system/app/ExampleApp/ExampleApp.apk
```

### Sumber privat aplikasi
{: #private-app-sources }

* Mirip dengan sumber aplikasi, tetapi disimpan di direktori yang dilindungi untuk keamanan yang lebih baik.
* Hanya dapat diakses oleh sistem atau aplikasi yang bersangkutan.
* Berkas dinamai sesuai nama paket aplikasi.

Contoh:

```bash
/data/app-private/eu.darken.sdmse/base.apk
```
