---
title: "Uygulama Kontrolü"
lang: tr
translation_key: appcontrol
permalink: /tr/appcontrol
source_hash: 1a4a7450fc5b86ca
---
# Uygulama Kontrolü (AppControl)
{: #appcontrol }
Uygulama Kontrolü (AppControl), yüklü uygulamaları denetlemeyle ilgili tüm işlemler için tek durak noktanızdır. Taramadan sonra yüklü uygulamalarınızın aranabilir bir listesini görürsünüz. Sistem uygulamaları varsayılan olarak gösterilmez, bunun ayarlarda etkinleştirilmesi gerekir.
Bazı seçenekler yalnızca SD Maid'e root izni verilmişse kullanılabilir (mümkün) olabilir.

Bazı işlemler birden fazla uygulamada, bazıları ise yalnızca tek bir öğede yürütülebilir. Birden fazla uygulamada işlem yürütmek için bir uygulamaya uzun basın, ardından ek uygulamaları seçmek için onlara dokunun.

## İşlemler
{: #actions }
Bu işlemlerden bazılarına yalnızca, listedeki bir girişe dokunarak ulaşılan uygulama ayrıntılarından erişilebilir.

### Uygulamayı başlat
{: #run-app }
Uygulamayı başlatır. Uygulamanın birincil etkinliğini açar; bu genellikle başlatıcının da yürüteceği işlemle aynıdır.

### Durdurmaya zorla
{: #force-stop }
Uygulamayı zorla durdurmayı dener. En iyi Root veya ADB/Shizuku ile çalışır, aksi takdirde Erişilebilirlik hizmetini kullanır.

### Uygulamayı dışa aktar
{: #export-apk }
Uygulamanın `.apk` dosyasını dahili özel depolamadan genel depolamanızdaki indirme klasörüne kopyalamayı dener. Uygulama şifrelenmişse bu işlem root gerektirir; şifrelenmemiş uygulamalar root olmadan da dışa aktarılabilir.

### Uygulamayı devre dışı bırak
{: #freeze-app }
Root veya ADB/Shizuku gerektirir. Uygulamayı devre dışı bırakır. Uygulama görünmez ve kendi kendine başlayamaz. Çalışması için yeniden etkinleştirilmesi gerekir. Veri kaybı olmaz ve bu durum SD Maid'i kaldırsanız bile devam eder. İyi bir uygulama etkinleştirme/devre dışı bırakma uygulaması, SD Maid ile devre dışı bırakılan uygulamaları etkinleştirebilir; bunun tersi de geçerlidir.

### Uygulamayı kaldır
{: #delete-app }
Uygulamayı ve verilerini siler.


## Ayarlar
{: #settings }
### Boyutları belirle
{: #determine-size }
Uygulama listesinin yüklenmesini hızlandırmak için devre dışı bırakılabilir. Bir uygulamanın ne kadar büyük olduğunu belirlemek çok zaman alır.

### Faaliyeti belirleyin
{: #determine-activity }
Uygulama listesinin yüklenmesini hızlandırmak için devre dışı bırakılabilir. Bir uygulamanın yakın zamanda etkin olup olmadığını belirlemek çok zaman alır.
