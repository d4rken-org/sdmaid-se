---
title: "Uygulama Temizleyici"
lang: tr
translation_key: appcleaner
permalink: /tr/appcleaner
source_hash: 505b031f74bf71a2
---
# Uygulama Temizleyici (AppCleaner)
{: #app-cleaner }

Uygulama Temizleyici (AppCleaner), uygulamaları tanıyan ve "gereksiz" dosyaları bulan bir araçtır. Bir dosya, olumsuz bir etki yaratmadan silinebiliyorsa ve ya gerekli değilse ya da ihtiyaç duyulduğunda yeniden oluşturulacaksa gereksiz kabul edilir. Bir "önbellek" içindeki dosya türlerinin çoğu bu tanıma uyar.

## Ayarlar
{: #settings }

### Sistem uygulamalarını dahil et
{: #include-system-apps }

Bu ayar, sistem uygulamalarını sonuçlara dahil eder. Bazı sistem uygulamaları (ROM'a bağlı olarak) önbelleklerinin temizlenmesine olumsuz tepki verdiğinden bu ayar varsayılan olarak etkin değildir. Bazı sistem uygulamalarında ayrıca "Önbelleği temizle" düğmesi bulunmaz; bu da önbellek Erişilebilirlik hizmeti üzerinden temizlenirken SD Maid'in takılıp kalmasına neden olabilir.

### Diğer kullanıcıları dahil edin
{: #include-other-users }

Root gerektirir. Etkinleştirilirse SD Maid, SD Maid'i o anda çalıştıran kullanıcının yanı sıra diğer kullanıcıların klasörlerini de tarar.

### Çalışan uygulamaları dahil et
{: #include-running-apps }

Çalışan uygulamaların dahil edilip edilmeyeceği.

### En küçük boyut
{: #minimum-size }

Bir uygulamanın önbelleğinin listelenmesi için ne kadar büyük olması gerektiği. En küçük boyut belirlemek, fayda ile hız arasında iyi bir uzlaşmadır. Yalnızca birkaç KB boyutundaki bir uygulama önbelleğini temizlemenin genellikle yararlı bir etkisi olmaz. Bu, bir uygulama için bulunan toplam önbellek miktarına uygulanır (tüm etkin filtreler dikkate alınarak).

### Asgari dosya yaşı
{: #minimum-age }

Yalnızca eski önbellek dosyalarını silmek için kullanılabilir. Sonuçlardaki her dosyaya veya klasöre ayrı ayrı uygulanır.

### Erişilemeyen önbellekleri dahil et
{: #include-inaccessible-caches }

SD Maid'in sahip olduğu izinlere bağlı olarak bazı dosyalara doğrudan erişilemeyebilir. Bu genellikle uygulamaların özel varsayılan önbelleğini ifade eder (ör. `/data/data/eu.darken.sdmse/cache`). Bu ayar etkinse SD Maid, bir Erişilebilirlik hizmeti aracılığıyla sistem ayarlarına gitmeyi ve hedef uygulama için `Önbelleği temizle` düğmesine basmayı dener.

### Filtre
{: #filter }

Uygulama Temizleyici aracının hangi sonuçları bulacağını etkilemek için farklı filtreleri etkinleştirebilir veya devre dışı bırakabilirsiniz.

#### Genel filtreler
{: #common-filters }

Genel filtreler, tüm uygulamalardaki çeşitli dosya türlerini hedefler.

#### Uygulamaya özgü filtreler
{: #specific-filters }

Uygulamaya özgü filtreler, belirli uygulamaları hedefler, ör. WhatsApp veya Telegram.

## Sorun giderme
{: #troubleshooting }
### `com.miui.securitycenter`
{: #commiuisecuritycenter }
#### Eksik `GET_USAGE_STATS` izni
{: #missing-get_usage_stats-permission }

MIUI ve HyperOS, bir uygulamanın sistem ayrıntılarını göstermek için "Security" veya "Security Center" ("Güvenlik", paket adı: `com.miui.securitycenter`) adlı ek bir uygulama kullanır. Bir uygulamanın önbelleğini temizlemek için normal işlem sırası şöyledir: `Clear data` ("Verileri temizle") öğesine dokunun, `Clear cache` ("Önbelleği temizle") öğesini seçin, işlemi onaylayın.

'Clear data' ("Verileri temizle") düğmesi tüm uygulamalarda gri görünüyor ve dokunulamıyorsa, "Security Center" uygulamasında `GET_USAGE_STATS` izni eksiktir. Normalde bu izin varsayılan olarak verilmiş olmalıdır. Bunun nasıl olduğu belirsizdir, ancak çözüm bu izni yeniden vermektir.

Şuraya gidin: `System Settings` > `Security and privacy` > `More security and privacy` > `Special permissions` > `Usage access` > `Security` uygulamasını bulun > İzni verin

Alternatif olarak, ADB erişiminiz veya daha üst düzey bir erişiminiz varsa `pm grant com.miui.securitycenter android.permission.PACKAGE_USAGE_STATS` komutunu çalıştırın.

<a href="/assets/images/appcleaner/miui-usage-stats-1.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-1.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-2.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-2.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-3.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-3.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-4.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-4.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-5.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-5.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-6.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-6.webp" width="100px">
</a>
