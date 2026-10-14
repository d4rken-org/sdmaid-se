---
title: "Menyiapkan SD Maid SE"
lang: id
translation_key: setup
permalink: /id/setup
source_hash: a14003a95dd2336d
---
# Menyiapkan SD Maid SE
{: #setting-up-sd-maid-se }
Informasi tentang berbagai langkah penyiapan SD Maid SE.

SD Maid menyesuaikan diri dengan akses yang tersedia. Meskipun semua izin bersifat opsional, sebagian besar fitur tidak akan berfungsi jika Anda tidak memberikan izin apa pun. SD Maid akan bekerja paling baik jika semua item penyiapan telah diselesaikan.

SD Maid akan otomatis mencoba menyelesaikan sebagian langkah penyiapan jika [root](#root-access) atau [akses ADB](#shizuku) tersedia.

Beberapa izin saling tumpang tindih atau mencakup izin lain, mis. Anda dapat memberikan akses root sekaligus akses ADB kepada SD Maid. SD Maid akan selalu mencoba menggunakan metode akses terbaik untuk setiap tindakan, dan jika gagal, mencoba lagi dengan metode akses terbaik kedua, lalu ketiga, dan seterusnya. Memberikan izin yang tumpang tindih kepada SD Maid meningkatkan keandalan.

> **Kiat:** Beberapa langkah penyiapan di bawah melibatkan menjalankan perintah ADB. Jika Anda belum menyiapkan ADB di komputer, Anda dapat menggunakan [Web ADB Helper](https://d4rken.github.io/web-adb/) untuk menjalankannya langsung dari browser.

## Kelola penyimpanan
{: #manage-storage }

Izin "Kelola penyimpanan" adalah izin akses penyimpanan dengan cakupan luas.

* Di Android 12 dan sebelumnya, izin ini memberikan akses ke seluruh penyimpanan publik.
* Di Android 13+, izin ini tetap memberikan akses ke seluruh penyimpanan publik, tetapi tidak ke sub-direktori di bawah `Android/data`.

## Statistik penggunaan
{: #usage-statistics }

Izin statistik penggunaan memberi SD Maid akses ke ukuran data dan cache, serta informasi apakah sebuah aplikasi sedang berjalan atau kapan terakhir digunakan.

## Kerangka akses penyimpanan
{: #storage-access-framework }
SD Maid menggunakan [Android Storage Access Framework (SAF)](http://developer.android.com/guide/topics/providers/document-provider.html) untuk mendapatkan akses ke area penyimpanan tambahan. SD Maid akan meminta Anda memberikan akses ke jalur tertentu yang tidak dapat diakses hanya dengan izin "Kelola penyimpanan" (Just Android Things™️).

* Tekan tombol `Berikan akses` 
* Jendela baru akan terbuka
* Jika jalur yang benar sudah dipilih, lanjutkan. Jika tidak, Anda harus menavigasi ke jalur yang diminta SD Maid.
* Lalu tekan tombol `Use this folder` ("Gunakan folder ini"), entri penyiapan untuk jalur ini akan terbuka dan berwarna hijau.

Ulangi proses ini sampai semua jalur berwarna hijau.

<a href="/assets/images/setup/saf-1.webp"><img src="/assets/images/setup/saf-1.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-2.webp"><img src="/assets/images/setup/saf-2.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-3.webp"><img src="/assets/images/setup/saf-3.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-4.webp"><img src="/assets/images/setup/saf-4.webp" width="100" alt=""></a>

### SAF: Pemecahan Masalah
{: #saf-troubleshooting }
Untuk saat segala sesuatu tidak berjalan sesuai rencana.

#### SAF: Akses `Android/data|obb`
{: #saf-androiddataobb-access }

Memberikan akses ke `Android/data|obb` tidak dimungkinkan dengan versi yang lebih baru dari aplikasi `Files` sistem (`com.google.android.documentsui`) di Android 11 dan Android 12. Jika aplikasi `Files` yang terpasang menargetkan Android 14 (API-level 34) atau yang lebih baru, SD Maid akan menyembunyikan permintaan akses SAF untuk jalur ini (seperti di Android 13+).

Google Play memperbarui aplikasi `Files` melalui layanan `Google Play system update`. Aplikasi "Files" asli (build untuk API-level 31, mis. `aml_doc_310851020 (310851020)`) yang disertakan dengan OS Android 12 memungkinkan SD Maid membuka jendela pemilihan jalur SAF pada `Android/Data`, sedangkan aplikasi "Files" terbaru (build untuk API-level 34 atau lebih baru, mis. `14-10492947 (340916000)`) mencegahnya.

**Solusi:**

Anda dapat mencopot pembaruan aplikasi `Files`, lalu menyiapkan SD Maid. Memperbarui aplikasi `Files` lagi tidak akan merusak izin yang sudah diberikan.

* Buka pengaturan perangkat
* Buka "Apps & notifications" (Aplikasi & notifikasi) atau "Application Manager" (Pengelola aplikasi)
* Temukan aplikasi sistem `Files`
* Ketuk menu tiga titik, biasanya di pojok kanan atas
* Pilih "Uninstall updates" (Copot pembaruan)

atau

_Anda juga dapat menjalankan perintah ini menggunakan [Web ADB Helper](https://d4rken.github.io/web-adb/)._

`adb uninstall com.google.android.documentsui`

#### SAF: OPEN_DOCUMENT_TREE ActivityNotFoundException
{: #saf-open_document_tree-activitynotfoundexception }

Memberikan akses ke jalur melalui Storage Access Framework memerlukan aplikasi yang memungkinkan pengguna mengonfirmasi jalur yang akan diberi akses. Aplikasi ini adalah aplikasi "Documents" (Dokumen). Ini adalah aplikasi sistem yang ada di setiap perangkat Android sejak Android 5.0  (`com.android.documentsui` atau `com.google.android.documentsui`). Jika Anda mendapatkan kesalahan ini, berarti seseorang telah menghapus atau menonaktifkan aplikasi ini. Dalam kebanyakan kasus, seseorang menjalankan alat "de-bloating" yang terlalu bersemangat sehingga menonaktifkannya. Anda dapat mengaktifkannya kembali melalui ADB:

_Anda juga dapat menjalankan perintah-perintah ini menggunakan [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```
adb shell pm enable com.android.documentsui
or
adb shell pm enable com.google.android.documentsui
```

#### SAF: Jendela kosong
{: #saf-empty-window }
Jika jendela untuk memberikan akses ke jalur kosong, kemungkinan paket penyedia penyimpanan eksternal Anda (`com.android.externalstorage`) dinonaktifkan (kemungkinan besar oleh alat "de-bloating" yang terlalu bersemangat). Anda dapat mengaktifkannya kembali melalui ADB:

_Anda juga dapat menjalankan perintah ini menggunakan [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```
adb shell pm enable com.android.externalstorage
```

Menjalankan `Google Play system update` lagi akan memulihkan pembaruan tersebut. Anda juga dapat mengunduh versi terbaru secara manual dari [APKMirror.com](https://www.apkmirror.com/apk/google-inc/files-6/).

## Notifikasi
{: #notifications }

Tanpa izin ini, SD Maid tidak akan dapat menampilkan notifikasi. Di beberapa ROM, hal ini dapat berdampak buruk pada keandalan saat berjalan di latar belakang.

## Inventaris aplikasi
{: #app-inventory }

SD Maid memerlukan izin `QUERY_ALL_PACKAGES` untuk memeriksa aplikasi apa saja yang terpasang di perangkat Anda (yaitu ["visibility into the inventory of installed apps"](https://support.google.com/googleplay/android-developer/answer/10158779?hl=en) (visibilitas atas inventaris aplikasi terpasang)). Lihat juga ["Izin Statistik penggunaan"](/id/setup#usage-statistics)

Izin `QUERY_ALL_PACKAGES` wajib untuk:

* [PencariMayat](/id/corpsefinder): Untuk memahami berkas mana yang dapat dihapus, SD Maid perlu mengetahui apakah Anda memiliki aplikasi terpasang yang mungkin membutuhkannya.
* [Pembersih Aplikasi](/id/appcleaner): Menentukan cache untuk setiap aplikasi
* [Pengendali Aplikasi](/id/appcontrol): Agar dapat menampilkan semua aplikasi terpasang
* [Penganalisis Penyimpanan](/id/storageanalyzer): Mengaitkan penyimpanan terpakai dengan aplikasi terpasang

Izin ini ada sejak [Android 11](https://developer.android.com/training/package-visibility) dan biasanya diberikan secara bawaan. Sejak Android 14, ada beberapa perangkat (mis. [#1015](https://github.com/d4rken-org/sdmaid-se/issues/1015), [#1354](https://github.com/d4rken-org/sdmaid-se/issues/1354)) yang tidak memberikan izin ini secara bawaan atau memungkinkan izin ini dicabut.

[<img src="/assets/images/setup/app-inventory.webp" width="100">](/assets/images/setup/app-inventory.webp)

### Daftar aplikasi tidak valid
{: #invalid-app-list }

Di beberapa perangkat, izin "Daftar Aplikasi" (`QUERY_ALL_PACKAGES` / `GET_INSTALLED_APPS`) sudah diberikan, tetapi sistem tetap mengembalikan **daftar aplikasi terpasang yang tidak lengkap**, misalnya daftar yang tidak memuat paket sistem inti seperti `android`. Saat SD Maid mendeteksi hal ini, SD Maid menampilkan peringatan **"Daftar aplikasi tidak valid"** dan catatan **"Kemungkinan keterbatasan perangkat"** di kartu penyiapan alih-alih melanjutkan.

SD Maid sengaja berhenti di sini: jika tidak dapat melihat daftar lengkap aplikasi terpasang, SD Maid tidak dapat membedakan dengan andal berkas mana yang milik aplikasi yang masih terpasang. Tetap melanjutkan bisa berarti menghapus data milik aplikasi yang tidak dapat dilihat SD Maid, sehingga fitur yang berkaitan dengan aplikasi tetap dinonaktifkan sampai daftar lengkap tersedia.

**Yang dapat Anda coba:**

* Buka pengaturan sistem dan pastikan SD Maid diizinkan menggunakan izin "Daftar Aplikasi". Pada beberapa produsen, sakelar ini memiliki nama lain (mis. "Kueri semua paket") atau berada di dalam layar "Privasi" atau "Pengelola izin" yang terpisah.
* Periksa apakah fitur penghemat baterai bawaan, pelindung privasi, atau "penyembunyian aplikasi" menyaring daftar aplikasi yang dapat dilihat SD Maid, dan kecualikan SD Maid dari fitur tersebut.
* Mulai ulang perangkat dan buka kembali SD Maid agar status izin dievaluasi ulang.

Jika izin "Daftar Aplikasi" sudah diizinkan dan daftar masih dilaporkan tidak valid, biasanya ini adalah **keterbatasan perangkat**, bukan sesuatu yang dapat Anda perbaiki: beberapa sistem (misalnya HarmonyOS, atau build Android yang banyak dimodifikasi) tidak pernah memberikan daftar aplikasi lengkap kepada aplikasi pihak ketiga. Lihat [#2441](https://github.com/d4rken-org/sdmaid-se/issues/2441) sebagai contoh.

Dalam hal ini, alat yang berkaitan dengan aplikasi ([PencariMayat](/id/corpsefinder), [Pembersih Aplikasi](/id/appcleaner), [Pengendali Aplikasi](/id/appcontrol)) tetap dinonaktifkan untuk melindungi data Anda, tetapi [Penganalisis Penyimpanan](/id/storageanalyzer) tetap berfungsi: alat ini menunjukkan berapa banyak ruang yang digunakan penyimpanan Anda, hanya dengan detail yang lebih sedikit (ukuran folder ditampilkan hanya-baca dan tidak dapat dikaitkan dengan aplikasi tertentu).

## Layanan aksesibilitas
{: #accessibility-service }
Layanan aksesibilitas memungkinkan SD Maid berinteraksi dengan perangkat Anda seperti manusia, yaitu melihat layar dan menekan tombol. SD Maid dapat menggunakannya untuk mengotomatiskan tindakan yang membosankan seperti menekan "Hapus cache" pada ratusan aplikasi. Layanan aksesibilitas adalah layanan yang tidak dapat diluncurkan oleh aplikasi itu sendiri, melainkan harus diluncurkan oleh sistem, karena jenis layanan ini memberikan akses tambahan ke perangkat Anda kepada aplikasi.

### ACS: Pemecahan Masalah
{: #acs-troubleshooting }

Layanan ini dapat berhenti berjalan dan mengharuskan Anda mengaktifkannya kembali dalam berbagai keadaan:
* Anda memperbarui aplikasi.
* Aplikasi mogok. Jika ini terjadi, sebaiknya Anda melaporkannya sebagai bug. Jika ini terlalu sering terjadi, Anda mungkin tidak dapat memulainya kembali dengan mematikan/menyalakannya lagi sampai perangkat di-reboot.
* Sistem menghentikan aplikasi karena "pengoptimalan baterai" yang terlalu agresif (lihat juga <https://dontkillmyapp.com/>)
* Aplikasi keamanan bawaan produsen mematikannya (lihat [Diblokir oleh Phone Manager](#acs-blocked-by-phone-manager-oneplus-oppo-realme))

#### ACS: Layanan dinonaktifkan
{: #acs-service-is-disabled }

Beberapa ROM (mis. MIUI) terkenal karena secara agresif menghentikan semua yang tidak masuk daftar putih produsen perangkat.
Jika Anda terus-menerus diminta mengaktifkan kembali layanan aksesibilitas, mungkin sistem menonaktifkannya setiap kali SD Maid tidak sedang digunakan secara aktif.

Coba nonaktifkan pengoptimalan baterai, dan di ROM MIUI ada pengaturan tambahan bernama "Autostart" (Mulai otomatis), aktifkan untuk SD Maid. Untuk perangkat Xiaomi/POCO/Redmi:

* Buka `Security>Manage applications>Auto start in background` (Keamanan > Kelola aplikasi > Mulai otomatis di latar belakang) dan izinkan untuk SD Maid.
* Buka `Security>Increase speed>Lock apps` (Keamanan > Tingkatkan kecepatan > Kunci aplikasi) dan pilih SD Maid.


Anda juga dapat mempertimbangkan untuk memberikan izin tambahan yang [memungkinkan SD Maid memulai ulang layanan secara otomatis](#acs-automatic-service-restart).

[<img src="/assets/images/setup/acs-disabled.webp" width="100">](/assets/images/setup/acs-disabled.webp)

#### ACS: Diblokir oleh Phone Manager (OnePlus, OPPO, Realme)
{: #acs-blocked-by-phone-manager-oneplus-oppo-realme }

Di OxygenOS dan ColorOS, aplikasi bawaan "Phone Manager" (Pengelola Ponsel) dapat mematikan layanan aksesibilitas dengan sendirinya, biasanya dalam satu detik setelah pembersihan selesai. Sakelar aksesibilitas Android setelahnya memang benar-benar mati, sehingga terlihat seolah-olah SD Maid berhenti bekerja di tengah proses.

Matikan fitur ini di `Phone Manager>Utilities>Block suspicious app activities` (Pengelola Ponsel > Utilitas > Blokir aktivitas aplikasi yang mencurigakan).

Layar yang sama menyimpan daftar hal yang telah diblokirnya. Entri berlabel "Abnormal device control" (Kontrol perangkat abnormal) dengan stempel waktu yang cocok dengan pembersihan yang Anda jalankan memastikan bahwa inilah penyebabnya. Sakelar "Blocking alerts" (Peringatan pemblokiran) di sebelahnya hanya mengatur apakah Anda menerima pemberitahuan, jadi jika peringatan dimatikan, hal ini terjadi tanpa disadari.

#### ACS: Mengaktifkan melalui ADB
{: #acs-enable-via-adb }

Jika perangkat Anda tidak memiliki cara untuk mengaktifkan layanan aksesibilitas melalui layar (mis. perangkat TV), layanan dapat diaktifkan menggunakan ADB dengan menjalankan:

_Anda juga dapat menjalankan perintah ini menggunakan [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```bash
adb shell settings put secure enabled_accessibility_services eu.darken.sdmse/.automation.core.AutomationService
```

#### ACS: Memulai ulang layanan secara otomatis
{: #acs-automatic-service-restart }

_Catatan: Ini adalah teknik tingkat lanjut yang ditujukan untuk pengguna mahir._

Anda dapat memberikan izin tambahan yang memungkinkan SD Maid memulai ulang layanan dengan sendirinya.
Izin ini bernama `WRITE_SECURE_SETTINGS` dan hanya dapat diberikan [melalui ADB](https://www.google.com/search?q=android+adb).
Jika Anda sudah menyiapkannya, jalankan perintah ini untuk memberikan izin:

_Anda juga dapat menjalankan perintah ini menggunakan [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```bash
adb shell pm grant eu.darken.sdmse android.permission.WRITE_SECURE_SETTINGS
```

Dengan izin ini, SD Maid dapat menggunakan perintah yang [dijelaskan di sini](#acs-enable-via-adb) untuk mengaktifkan layanan bila diperlukan.
SD Maid hanya akan menggunakannya jika Anda telah mengaktifkan layanan aksesibilitas setidaknya sekali secara manual.
Jika Anda masuk ke penyiapan lagi dan meminta SD Maid untuk tidak menggunakan layanan aksesibilitas, teknik mulai ulang otomatis ini tidak akan digunakan sampai Anda mengaktifkan layanan aksesibilitas secara manual lagi.

Untuk mencabut izin ini dari SD Maid, gunakan perintah ini:

_Anda juga dapat menjalankan perintah ini menggunakan [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```bash
adb shell pm revoke eu.darken.sdmse android.permission.WRITE_SECURE_SETTINGS
```

Jika Anda mendapatkan `Operation not allowed: java.lang.SecurityException`, [lihat di sini](https://stackoverflow.com/questions/52079343). Beberapa perangkat membatasi izin ADB. Periksa apakah ada pengaturan tambahan (mis. `Disable Permission Monitoring` (Nonaktifkan pemantauan izin)) di opsi pengembang yang perlu diubah. Setelah menjalankan perintah, Anda dapat menonaktifkannya lagi.

#### ACS: Advanced Protection
{: #acs-advanced-protection }

Di Android 17 dan yang lebih baru, mode [**Advanced Protection**](https://developer.android.com/privacy-and-security/advanced-protection-mode) (Perlindungan Lanjutan) dari Google memblokir aplikasi yang bukan alat aksesibilitas khusus (pembaca layar, akses tombol, dll.) agar tidak menggunakan layanan aksesibilitas. SD Maid adalah alat pemeliharaan/pembersihan, sehingga selama Advanced Protection aktif, layanan aksesibilitasnya **tidak dapat** diaktifkan: Android menolak layanan tersebut di tingkat sistem, bahkan jika Anda mencoba memberikannya melalui root atau akses ADB.

SD Maid tidak dapat mengecualikan dirinya sendiri dari hal ini: Hanya aplikasi yang diklasifikasikan Google sebagai alat aksesibilitas yang memenuhi syarat, dan aplikasi pembersih secara tegas dikecualikan.

Anda memiliki dua pilihan:

- **Gunakan root atau akses ADB** Jika root atau akses ADB sudah disiapkan, Pembersih Aplikasi SD Maid menghapus cache aplikasi melalui panggilan sistem langsung dan **tidak** memerlukan layanan aksesibilitas. Dengan **root**, ini juga mencakup pembersihan aplikasi tertentu yang dipilih satu per satu. Dengan **akses ADB**, pembersihan penuh "bersihkan semua aplikasi" berjalan tanpa layanan aksesibilitas, tetapi menghapus cache dari aplikasi *tertentu* yang dipilih tetap bergantung padanya. Siapkan ini di Pengaturan (Setup) SD Maid → [Akses root](#root-access) atau [Akses ADB](#shizuku).
- **Matikan Advanced Protection** jika Anda secara khusus menginginkan pembersihan berbasis aksesibilitas. Opsi ini ada di `Settings → Security & privacy → Advanced Protection` (Setelan → Keamanan & privasi → Perlindungan Lanjutan; jalur persisnya berbeda-beda tergantung perangkat). Menyalakannya kembali nanti akan menonaktifkan layanan aksesibilitas lagi.

#### ACS: Pembatasan AppOps
{: #acs-appops-restrictions }

Android 13+ dapat menerapkan pembatasan tambahan pada aplikasi yang dipasang secara sideload. Jadi, jika SD Maid tidak dipasang dari sumber yang dikenal (oleh Google) seperti Google Play atau toko aplikasi lain, Anda mungkin tidak dapat langsung memberikan beberapa izin kepada SD Maid, mis. memulai layanan aksesibilitas.

##### OxygenOS 15 - OnePlus
{: #oxygenos-15---oneplus }

OnePlus lupa (atau memang tidak) menyertakan tindakan `Allow restricted settings` (Izinkan setelan terbatas) di pengaturan aplikasi pada OxygenOS 15. Jika Anda tidak memasang melalui Google Play, penginstal perlu menetapkan flag `INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS`. Jika Anda sudah menyiapkan Shizuku, Anda dapat menggunakan [aplikasi "Install with Options"](https://github.com/zacharee/InstallWithOptions).


##### ROM lain
{: #other-roms }

Untuk mencabut pembatasan ini, buka layar pengaturan aplikasi SD Maid di sistem. Buka menu di pojok kanan atas dan pilih `Allow restricted settings` (Izinkan setelan terbatas), setelah itu Anda dapat kembali ke daftar layanan aksesibilitas dan mengaktifkan layanan SD Maid.

**Catatan:**
Untuk meningkatkan kemudahan penggunaan (ini sarkasme), Google memutuskan bahwa pengaturan ini hanya akan terlihat jika:

* Anda setidaknya pernah sekali melihat "Restricted setting" (Setelan terbatas) setelah mengklik entri layanan aksesibilitas SD Maid
* Anda telah membuka pengaturan sistem dan menavigasi ke halaman pengaturan SD Maid secara manual, bukan melalui aplikasi lain atau pintasan.

😮‍💨


Beberapa informasi latar belakang tambahan [dapat ditemukan di sini](https://blog.esper.io/android-13-sideloading-restriction-harder-malware-abuse-accessibility-apis/).

<a href="/assets/images/setup/acs-restricted-settings-1.webp"><img src="/assets/images/setup/acs-restricted-settings-1.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-2.webp"><img src="/assets/images/setup/acs-restricted-settings-2.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-3.webp"><img src="/assets/images/setup/acs-restricted-settings-3.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-4.webp"><img src="/assets/images/setup/acs-restricted-settings-4.webp" width="100" alt=""></a>

## Akses root
{: #root-access }

Dengan akses root, SD Maid memiliki akses penuh ke perangkat Anda dan dapat melakukan pemindaian yang paling menyeluruh. Biasanya ini berarti Anda telah membuka kunci bootloader dan menyiapkan Magisk.

Jika SD Maid menduga perangkat Anda mungkin sudah di-root, SD Maid akan meminta akses root terlebih dahulu. Jika akses root diberikan, SD Maid akan menggunakan akses root tersebut untuk memberikan sebanyak mungkin izin lainnya. Akses root adalah tingkat akses tertinggi yang dapat Anda berikan kepada sebuah aplikasi. Izin lainnya kemudian hanya diperlukan demi kemudahan penggunaan dan kinerja, itulah sebabnya izin-izin tersebut tidak memerlukan persetujuan tambahan selama penyiapan jika SD Maid sudah memiliki root.

## Akses ADB
{: #shizuku }

Akses ADB memungkinkan SD Maid menggunakan antarmuka debugging Android (ADB) untuk operasi dengan hak lebih tinggi. Akses ini memerlukan aplikasi terpisah yang menjalankan layanan ADB di perangkat Anda dan memberikan akses ke layanan tersebut kepada aplikasi lain: [Porter](#porter) atau [Shizuku](#using-shizuku). Ini adalah modifikasi yang dapat Anda lakukan pada ponsel Anda, yang dapat dimanfaatkan oleh aplikasi lain.

Akses ini mirip dengan root, tetapi tidak sama. Izinnya sedikit kurang kuat, tetapi modifikasinya tidak permanen dan tidak mengharuskan Anda membuka kunci bootloader.

### Porter
{: #porter }

[Porter](https://porter.darken.eu) adalah fork Shizuku yang saya kelola, dengan dukungan root opsional. Situs webnya menjelaskan cara memasang dan memulainya. Di Android 11 dan yang lebih baru, debugging nirkabel memungkinkan Anda memulainya tanpa komputer.

Jika Anda menyiapkan akses ADB untuk pertama kalinya, gunakan Porter. Build SD Maid dari GitHub dan F-Droid mengarahkan Anda ke Porter jika belum ada aplikasi akses ADB yang terpasang. Build Google Play mengarahkan Anda ke Shizuku di Google Play, tetapi tetap bekerja dengan Porter dengan cara yang sama.

Jika Porter dan Shizuku sama-sama terpasang, SD Maid menggunakan Porter. Kartu penyiapan kemudian menampilkan `Porter sudah terpasang, jadi SD Maid menunggu Porter alih-alih Shizuku`. Mulai Porter untuk melanjutkan, atau copot Porter jika Anda ingin SD Maid menggunakan Shizuku.

### Menggunakan Shizuku
{: #using-shizuku }

Ada dua build Shizuku:

* [Fork dari thedjchi](https://github.com/thedjchi/Shizuku) ([panduan penyiapan](https://github.com/thedjchi/Shizuku/wiki/setup)) dikelola secara aktif dan dilaporkan berfungsi di perangkat tempat versi aslinya [tidak pernah terhubung](#shizuku-never-connects).
* [Versi asli dari RikkaApps](https://shizuku.rikka.app/guide/setup/) masih berfungsi dengan baik di sebagian besar perangkat.

Keduanya tidak dapat dipasang secara bersamaan. Jika versi asli sudah berfungsi untuk Anda, tidak perlu beralih.

### Memberikan akses
{: #granting-access }

Setelah Anda menyiapkan Porter atau Shizuku:

* Di penyiapan SD Maid, pilih `Coba gunakan akses ADB`. Ini adalah pengaturan internal SD Maid, yang dapat Anda gunakan untuk mengaktifkan/menonaktifkan penggunaan akses ADB oleh SD Maid
* Setelah Anda memberikan persetujuan, dialog dari Porter atau Shizuku akan menanyakan apakah SD Maid boleh menggunakannya
* Konfirmasi dialog tersebut
* Kartu penyiapan `Akses ADB` menunjukkan apakah SD Maid dapat terhubung

Menyiapkan akses ADB jika Anda sudah memberikan akses Root kepada SD Maid tidak mengaktifkan fitur tambahan, tetapi meningkatkan keandalan. Jika tindakan gagal dengan izin Root, SD Maid akan mencoba lagi dengan akses ADB.

### Akses ADB: Pemecahan Masalah
{: #shizuku-troubleshooting }

Anda dapat memeriksa status akses ADB SD Maid dengan membuka `Pengaturan > Pengaturan` (Settings > Setup) dan melihat kartu penyiapan `Akses ADB`. Kartu tersebut seharusnya menampilkan `SD Maid sekarang dapat menggunakan akses ADB melalui Porter` (atau `melalui Shizuku`).

* `Porter belum mengizinkan SD Maid`: konfirmasi dialog akses, atau izinkan SD Maid di aplikasi Porter (atau Shizuku).
* `Porter menolak akses SD Maid`: SD Maid ditolak secara permanen. Buka Porter (atau Shizuku) dan izinkan SD Maid di sana.
* `Perbarui Porter untuk menggunakannya dengan SD Maid` atau `Versi SD Maid ini terlalu lama untuk Porter`: kedua aplikasi tidak memiliki versi yang kompatibel. Perbarui aplikasi yang disebutkan oleh kartu.

#### Shizuku tidak pernah terhubung
{: #shizuku-never-connects }

Kartu menampilkan `Akses ADB gagal` dan `Shizuku berjalan, tetapi tidak pernah menghubungkan SD Maid ke layanan bantunya.` Versi lama menampilkan `Waiting for link with Shizuku` (Menunggu tautan dengan Shizuku) atau `Shizuku link failed` (Tautan Shizuku gagal).

Buka aplikasi Shizuku. Jika aplikasi itu menyatakan sedang berjalan dan SD Maid ada di daftar yang diotorisasinya, Shizuku sudah disiapkan dengan benar dan menyiapkannya lagi tidak akan membantu. Yang gagal adalah layanan pembantu Shizuku: layanan ini mati saat dimulai sebelum sempat melapor balik, dan SD Maid menyerah setelah sekitar 15 detik. `Coba lagi` menjalankan pemeriksaan yang sama dan gagal dengan cara yang sama.

Ini adalah bug Shizuku ([RikkaApps/Shizuku#1198](https://github.com/RikkaApps/Shizuku/issues/1198)), bukan bug SD Maid, dan bug ini merusak setiap aplikasi yang menggunakan layanan pembantu Shizuku. Bug ini memengaruhi perangkat MediaTek, serta perangkat yang menjalankan HyperOS atau MIUI.

[Fork dari thedjchi](https://github.com/thedjchi/Shizuku) dilaporkan memperbaikinya. Untuk beralih:

* Copot versi asli terlebih dahulu, keduanya tidak dapat berdampingan
* Lepaskan pemasangannya dari debugging nirkabel jika Anda menggunakannya
* Pasang fork tersebut dari halaman [rilis](https://github.com/thedjchi/Shizuku/releases)-nya dan ikuti [panduan penyiapan](https://github.com/thedjchi/Shizuku/wiki/setup)-nya
* Berikan kembali izin Shizuku kepada SD Maid dan aplikasi lain yang sebelumnya menggunakannya
