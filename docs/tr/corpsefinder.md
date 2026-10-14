---
title: "ArtıkBulucu"
lang: tr
translation_key: corpsefinder
permalink: /tr/corpsefinder
source_hash: 9e16286f0fda1dc8
---
# ArtıkBulucu (CorpseFinder)
{: #corpse-finder }

`ArtıkBulucu` (CorpseFinder) aracı, artık yüklü olmayan uygulamalara ait dosyaları bulur.


## Risk seviyesi
{: #risklevel }

Varsayılan olarak tüm risk seviyesi seçenekleri kapalıdır. Bunları yalnızca ne yaptığınızı biliyorsanız etkinleştirin ve bir şey silmeden önce sonuçları kontrol edin.
Bir risk seviyesi seçeneğini etkinleştirmek, silmek istemediğiniz dosyaların silinme olasılığını artırır.

Risk seviyeleri çoğunlukla, genel depolamanızın kök dizini (`/storage/emulated/0`) gibi beyaz liste konumlarına uygulanır. Bu tür konumların ortak bir adlandırma düzeni yoktur ve SD Maid sahipliği yalnızca elle güncellenen bir veritabanı aracılığıyla belirleyebilir.


### Özel kalıntıları dahil et
{: #include-desirable-remnants }

Özel (istenen) kalıntılar, sahibi bilinen ancak yüklü olmayan ve SD Maid tarafından dahili olarak `KEEPER` olarak işaretlenen dosyalardır. Bunlar genellikle kullanıcının oluşturduğu dosyalardır, ör. çektiğiniz fotoğraflar.

### Ortak adları dahil et
{: #include-common-names }

Bazı uygulamalar çok yaygın adlar kullanır. Verilerini kaydetmek için `/sdcard/Notes` klasörü oluşturan muhtemelen yüzlerce not alma uygulaması vardır. SD Maid bu uygulamalardan birini tanıyor ve o uygulama telefonunuzda yüklü değil olabilir; ancak SD Maid, telefonunuzda bu klasörü hâlâ kullanan başka bir uygulama olmadığından emin olamaz ve o uygulamanın da bu klasörü kullandığını bilmez. 

## Filtre
{: #filter }

Her filtre belirli bir veri alanından sorumludur. Bir filtreyi etkinleştirdiğinizde ArtıkBulucu bu ek alanı da tarar.

### Hafıza kartı
{: #sdcard }

* Genellikle kullanıcının dosyalarını yerleştirdiği yer
* `Android/data` içindeki belirli alt dizinler hariç genel depolama (dahili ve çıkarılabilir).
* Buradaki dosyaların bir adlandırma düzeni yoktur.

Örnekler:

```bash
/storage/emulated/0/SDMaid
/storage/DEAD-BEEF/SDMaid
/sdcard/SDMaid
```

### Genel medya
{: #public-media }

* Uygulamaların genel medyasını saklar, ör. WhatsApp üzerinden alınan medya. Klasörler, uygulamaların paket adlarıyla adlandırılır.
* Genel depolamanın, işletim sistemi için özel anlamı olan bir alt dizini. 

Örnekler:

```bash
/storage/emulated/0/Android/media/eu.darken.sdmse
/storage/DEAD-BEEF/Android/media/eu.darken.sdmse
/sdcard/Android/media/eu.darken.sdmse
```

### Genel uygulama verileri
{: #public-app-data }

* Uygulamaların ek dosyaları.
* Klasörler, uygulamaların paket adlarıyla adlandırılır.
* Genel depolamanın, işletim sistemi için özel anlamı olan bir alt dizini.

Örnekler:

```bash
/storage/emulated/0/Android/data/eu.darken.sdmse
/storage/DEAD-BEEF/Android/data/eu.darken.sdmse
/sdcard/Android/data/eu.darken.sdmse
```

### OBB kaynakları
{: #obb-resources }

* Genellikle oyunlar için ek kaynak dosyaları.
* Klasörler ve dosyalar, uygulamaların paket adlarıyla adlandırılır.
* Genel depolamanın, işletim sistemi için özel anlamı olan bir alt dizini.

Örnekler:

```bash
/storage/emulated/0/Android/obb/eu.darken.sdmse
/storage/DEAD-BEEF/Android/obb/eu.darken.sdmse
/sdcard/Android/obb/eu.darken.sdmse
```

### Özel uygulama verileri
{: #private-app-data }

* Klasörler ve dosyalar, uygulamaların paket adlarıyla adlandırılır.
* Genel depolamanın, işletim sistemi için özel anlamı olan bir alt dizini.

Örnekler:

```bash
/data/data/eu.darken.sdmse
```

### Dalvik önbellek
{: #dalvik-cache }

* Daha hızlı çalışma için uygulama kodunun optimize edilmiş sürümlerini saklar.
* Sistem bölümünde, genellikle belirli mimari dizinlerinin altında bulunur.
* Dosyalar, uygulamanın paket adı kullanılarak adlandırılır.

Örnekler:

```bash
/data/dalvik-cache/arm64/data@app@eu.darken.sdmse@base.apk@classes.dex
/data/dalvik-cache/arm/data@app@eu.darken.sdmse@base.apk@classes.dex
```

### Uygulama kütüphaneleri
{: #app-libraries }

* Uygulamaların kullandığı `.so` dosyaları gibi yerel kütüphaneleri içerir.
* Genellikle uygulamanın veri dizininde bir `lib` klasörü altında bulunur.
* Dosyalar, mimariye ve kütüphane gereksinimlerine göre adlandırılır.

Örnekler:

```bash
/data/app/eu.darken.sdmse-1/lib/arm64/libexample.so
/data/app/eu.darken.sdmse-2/lib/arm/libexample.so
```

### Uygulama kaynakları
{: #app-sources }

* Uygulamaların kaynak kodu işlevini gören APK dosyalarını ifade eder.
* Sistem, kullanıcı veya üretici uygulamaları için belirli dizinlerde saklanır.
* Dosyalar, uygulamanın paket adına ve sürümüne göre adlandırılır.

Örnekler:

```bash
/data/app/eu.darken.sdmse-1/base.apk
/system/app/ExampleApp/ExampleApp.apk
```

### Özel uygulama kaynakları
{: #private-app-sources }

* Uygulama kaynaklarına benzer, ancak gelişmiş güvenlik için korumalı dizinlerde saklanır.
* Yalnızca sistem veya ilgili uygulama tarafından erişilebilir.
* Dosyalar, uygulamanın paket adıyla adlandırılır.

Örnekler:

```bash
/data/app-private/eu.darken.sdmse/base.apk
```
