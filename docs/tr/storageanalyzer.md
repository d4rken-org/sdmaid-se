---
title: "Depolama Analizcisi"
lang: tr
translation_key: storageanalyzer
permalink: /tr/storageanalyzer
source_hash: 2dbf8557900cc687
---
# Depolama Analizcisi (StorageAnalyzer)
{: #storage-analyzer }

Depolama Analizcisi (StorageAnalyzer) "Depolama alanımda bu kadar yeri ne kaplıyor?" sorusunu yanıtlar.

Bir dosya tarayıcısı gibi görünebilir, ancak bir dosya tarayıcısı değildir. Her şey boyuta göre sıralanır; böylece en büyük öğeleri takip ederek depolamanızı gerçekte neyin doldurduğuna kadar inebilirsiniz.

## Depolama alanları
{: #storages }

İlk ekran, cihazınızdaki her depolama alanını gösterir:

* **Birincil depolama**: uygulamalar ve sistem verileri dahil, cihazınızın dahili depolaması.
* **İkincil depolama**: genellikle çıkarılabilir bir hafıza kartı.
* **Taşınabilir depolama**: ör. USB ile bağlanmış bir flash bellek.

Her kart boş ve kullanılan alanı ve depolamanın son 7 günde ne kadar değiştiğini gösterir, bkz. [Depolama Trendi](#storage-trend).

## Kategoriler
{: #categories }

Bir depolama alanını açmak onu tarar ve kullanılan alanı kategorilere ayırır:

* **Uygulamalar**: kodları, verileri, önbellekleri ve medyalarıyla birlikte yüklü uygulamalar.
* **Kullanıcı dosyaları**: fotoğraflar, videolar, indirmeler ve belirli bir uygulamaya ait olmayan diğer tüm veriler.
* **Sistem verileri**: işletim sistemi ve başka bir yere atanamayan her şey.
* **Diğer kullanıcılar**: bu cihazdaki diğer kullanıcıların veya iş profillerinin uygulamaları ve dosyaları.

Sistem verileri ve Diğer kullanıcılar yalnızca birincil depolama için vardır.

### Uygulamalar
{: #apps }

Uygulamalar boyuta göre sıralanır ve ada göre aranabilir. Sistem uygulamaları da verileriyle birlikte burada listelenebilir. Yüklü bir güncellemesi olmayan sistem uygulamalarında yerleşik uygulama kodu Sistem verileri olarak sayılır. Bir uygulamayı açmak, alanının nereye gittiğini gösterir:

* **Uygulama kodu**: uygulamanın kendisi. Uygulamayı kaldırmak veya arşivlemek bu alanı boşaltır.
* **Uygulama verileri**: veriler, ayarlar ve önbellekler.
* **Uygulama medyası**: bu uygulamanın medya dosyaları, ör. alınan WhatsApp fotoğrafları.
* **Ek uygulama dosyaları**: uygulamanın kendi klasörlerinin dışına yerleştirdiği veriler, ör. en üst düzeydeki bir klasörde bulunan fotoğraflar veya indirmeler.

`Ayarları göster`, uygulamanın sistem ayarları sayfasını açar.

### Sayılar neden Android ayarlarıyla eşleşmiyor
{: #why-the-numbers-dont-match-androids-settings }

Analizcinin kategorileri, Android ayarlarındaki dağılımla tam olarak eşleşmez.

**Sistem verileri**, Uygulamalar, Kullanıcı dosyaları ve Diğer kullanıcılar sayıldıktan sonra kullanılan alandan geriye kalandır. SD Maid'in göremediği veya atayamadığı her şey oraya düşer: korumalı sistem alanları, ölçülemeyen diğer kullanıcıların depolaması, dosya sistemi ek yükü ve depolamanın ilan edilen boyutu ile gerçek boyutu arasındaki fark.

[Uygulama envanteri](/tr/setup#app-inventory) ve [Kullanım istatistikleri](/tr/setup#usage-statistics) izinleri olmadan uygulamalar ölçülemez. Bu durumda kapladıkları alan Sistem verileri olarak görünür.

## Erişim
{: #access }

Analizcinin neleri görebileceği, SD Maid'in sahip olduğu erişime bağlıdır:

* **Root olmadan** uygulama kodu ve özel uygulama verileri yalnızca toplam olarak gösterilir ve içlerine göz atılamaz. Android 13 ve sonrasında `Android/data` da root veya ADB erişimi gerektirir.
* **[Root](/tr/setup#root-access) veya [ADB erişimi](/tr/setup#shizuku) ile** Sistem verilerine göz atabilirsiniz. Root ayrıca uygulama koduna ve özel uygulama verilerine göz atmaya ve diğer kullanıcıları tam olarak ölçmeye de olanak tanır.
* **Uygulama envanteri izni olmadan** dosyalar, sahibi olan uygulamalarla eşleştirilemez. Bu durumda Kullanıcı dosyaları salt okunur olarak gösterilir: boyutları görebilirsiniz, ancak silme, hariç tutma, filtre oluşturma ve Kaydırıcı oturumları devre dışıdır.

SD Maid'in erişemediği öğeler dokunulduğunda `Doğrudan erişim mevcut değil` mesajını gösterir.

100.000'den fazla öğe içeren klasörlerin yalnızca toplamı hesaplanır ve içlerine göz atılamaz.

## İşlemler
{: #actions }

Bir dosyayı başka bir uygulamada açmak için ona dokunun. Öğeleri seçmek için uzun basın, ardından:

* **Sil**: seçili öğeleri bir onaydan sonra siler. Ücretsiz kullanılabilir.
* **Hariç Tut**: seçili yollar için bir [hariç tutma](/tr/exclusions) oluşturur. Bu hariç tutmalar tüm araçlar için geçerlidir. Analizcinin kendisi hariç tutmaları kullanmaz, çünkü doğru toplamları hesaplamak için her şeyi görmesi gerekir.
* **Filtre**: seçili öğeler için bir [Sistem Temizleyicisi özel filtresi](/tr/systemcleaner#custom-filters) oluşturur. Yükseltme gerektirir.
* **Swiper oturumu oluştur**: seçili öğelerle bir [Kaydırıcı](/tr/swiper) oturumu başlatır.

Sistem verilerine ve diğer kullanıcıların içeriğine yalnızca göz atılabilir. Orada silme mümkün değildir.

## Depolama Trendi
{: #storage-trend }

SD Maid, depolama alanlarınızın boş alanını birkaç saatte bir ve her temizlikten sonra kaydeder. `Depolama Trendi` bunu, her temizliğin ne kadar alan boşalttığını gösteren işaretlerle bir grafik olarak gösterir.

* Depolama kartları, her depolamanın son 7 günde ne kadar değiştiğini gösterir.
* Kontrol paneli tüm depolama alanlarının toplam değişimini ya da depolama doluyorsa bunun yerine kalan gün sayısına ilişkin bir tahmini gösterir.
* Anlık görüntülerin ne kadar süre tutulacağı `Ayarlar > Geçmiş > Depolama geçmişi` altında ayarlanır. Varsayılan 90 gün, en fazla 365 gün.

Depolama Trendi grafiği yükseltme gerektirir. Depolama kartlarındaki ve kontrol panelindeki 7 günlük değişim ve kalan gün tahmini ücretsizdir.

## Ayarlar
{: #settings }

### Düşük depolama eşiği
{: #low-storage-threshold }

Boş alan bunun altına düştüğünde SD Maid depolamanın tükenmekte olduğunu kabul eder. Varsayılan `Otomatik`: depolama boyutunun %5'i, en fazla 2 GB.

### Düşük alan uyarısı
{: #low-space-warning }

Yükseltme gerektirir. Birincil depolamanızdaki boş alan düşük depolama eşiğine ulaştığında uyarır. En az 5 günlük tutarlı bir geçmişle daha erken de uyarabilir: depolama 14 gün içinde eşiğe ulaşma yolundaysa ve boş alan zaten depolamanın en fazla %20'si (üst sınır 25 GB) ise. Varsayılan olarak kapalıdır.
