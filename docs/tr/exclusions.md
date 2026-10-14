---
title: "Hariç tutmalar"
lang: tr
translation_key: exclusions
permalink: /tr/exclusions
source_hash: d5c3aea2efa43796
---
# Hariç tutmalar
{: #exclusions }

SD Maid 2/SE'nin hariç tutma işlevi, kullanıcıların uygulamanın sistem taramaları ve bakım görevleri sırasında yok sayması gereken belirli klasörleri veya dosyaları tanımlamasını sağlar. Bu, uygulamanın işlemlerinden etkilenmemesini istediğiniz veriler varsa kullanışlıdır. Hariç tutmalar ayarlandığında SD Maid 2/SE, ArtıkBulucu (CorpseFinder), Uygulama Temizleyici (AppCleaner) ve diğer sistem yönetimi özellikleri gibi görevler sırasında bu dizinleri veya dosyaları atlar.

Hariç tutma eklemenin en kolay yolu doğrudan sonuçlardan eklemektir. Tek tek öğelerde "hariç tut" düğmesini kullanabilir ya da girişlere uzun basıp/birden fazlasını seçip "hariç tut" seçeneğini belirleyebilirsiniz. Ayrıca daha fazla kontrol için yolları veya joker karakter kalıplarını belirterek uygulama ayarlarından elle hariç tutma ekleyebilirsiniz.

Hariç tutmalar menüden dışa ve içe aktarılabilir. Hariç tutmaların içe aktarılması yalnızca SD Maid'in benzer sürümlerinde desteklenir. İçe aktarma ve dışa aktarma sürümleri arasında çok fazla güncelleme varsa bir hata oluşabilir. Bu durumda içe aktarmayı daha eski bir sürümde deneyebilir, ardından hariç tutmaları yeniden dışa aktarabilirsiniz.

<img src="/assets/images/exclusions/overview-1.webp" width="150">
<img src="/assets/images/exclusions/overview-2.webp" width="150">
<img src="/assets/images/exclusions/overview-3.webp" width="150">
<img src="/assets/images/exclusions/overview-4.webp" width="150">

## Türler
{: #types }

SD Maid 3 farklı hariç tutma türünü destekler:

### Uygulama hariç tutma
{: #app-exclusions }

Uygulama hariç tutmaları, her uygulamanın paket adına dayanır. Bunlar Uygulama Kontrolü (AppControl) aracıyla oluşturulabilir. Listeden bir uygulama seçin ve ilgili `Hariç Tut` işlemini kullanın.

### Yol hariç tutma
{: #path-exclusions }

Yol hariç tutmaları, Depolama Analizcisi (StorageAnalyzer) aracında yollar seçilerek (uzun basılarak) oluşturulabilir.

### Bölüm hariç tutma 
{: #segment-exclusions }
Bölüm hariç tutmaları, tam eşleşme gerektirmeyen bir yol hariç tutma türüdür. Bölüm (segment), bir yolun klasör gibi bir parçasıdır. `/sdcard/DCIM/Camera` yolu 3 bölümden oluşur. `DCIM/Camera` için bir bölüm hariç tutması oluşturarak hem dahili depolamanızdaki (`/storage/emulated/0/DCIM/Camera`) hem de harici depolamanızdaki (`/sdcard/DCIM/Camera`) tüm kamera fotoğraflarınızı hariç tutabilirsiniz.

`Kısmi eşleşmelere izin ver` seçeneğini işaretlerseniz, hem `Cam` klasörüyle hem de `Camera` klasörüyle eşleşmesi için `DCIM/Cam` için bir hariç tutma oluşturabilirsiniz.

## Araçlar
{: #tools }

SD Maid'in her aracı verilere farklı bir açıdan bakar. Tüm hariç tutmalar tüm araçları aynı şekilde etkilemez.

### ArtıkBulucu
{: #corpsefinder }

ArtıkBulucu aracı bir "uygulamanın" ne olduğunu ve hangi dosyaların bir uygulamaya ait olduğunu bilir. Tüm hariç tutma türlerine uyar. Bir uygulama hariç tutması oluşturmak, ArtıkBulucu'nun bulduğu ve hariç tutulan bir uygulamaya ait olduğunu düşündüğü tüm dosyaların hariç tutulacağı anlamına gelir.

Desteklenen hariç tutma türleri: `APP`, `PATH`, `SEGMENTS`

### Sistem Temizleyicisi
{: #systemcleaner }

Sistem Temizleyicisi (SystemCleaner) aracı yalnızca yol verileriyle çalışır. Bir "uygulamanın" ne olduğunu veya hangi dosyaların bir uygulamaya ait olduğunu bilmez.

Desteklenen hariç tutma türleri: `PATH`, `SEGMENTS`

### Uygulama Temizleyici
{: #appcleaner }

Uygulama Temizleyici aracı tüm hariç tutma türlerine uyar. Taranacak çok sayıda dosyası olan uygulamalar varsa taramaları hızlandırmak için hariç tutmaları kullanabilirsiniz.

Desteklenen hariç tutma türleri: `APP`, `PATH`, `SEGMENTS`

### Tekilleyici
{: #deduplicator }

Tekilleyici (Deduplicator) aracı "uygulamaların" ne olduğunu bilmez ve yalnızca dosya verileriyle çalışır.

Desteklenen hariç tutma türleri: `PATH`, `SEGMENTS`

### Medya Sıkıştırıcı
{: #media-squeeze }

Medya Sıkıştırıcı (Media Squeeze) yalnızca dosya verileriyle çalışır. Hariç tutulan dosyalar sıkıştırılmaz.

Desteklenen hariç tutma türleri: `PATH`, `SEGMENTS`

### Kaydırıcı
{: #swiper }

Kaydırıcı (Swiper) yalnızca dosya verileriyle çalışır. Hariç tutulan dosyalar yeni oturumlara dahil edilmez. Kaydırırken `Atla` düğmesine uzun basmak geçerli dosyayı hariç tutar.

Desteklenen hariç tutma türleri: `PATH`, `SEGMENTS`

### Depolama Analizcisi
{: #storageanalyzer }

Doğru toplam boyutları hesaplamak için tüm içeriğin taranması gerektiğinden Depolama Analizcisi hariç tutmaları desteklemez.
