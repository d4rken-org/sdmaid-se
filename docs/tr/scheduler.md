---
title: "Zamanlayıcı"
lang: tr
translation_key: scheduler
permalink: /tr/scheduler
source_hash: 74e1452091eaf142
---
# Zamanlayıcı (Scheduler)
{: #scheduler }

SD Maid 2'deki **Zamanlayıcı** (Scheduler) özelliği, bakım görevlerini belirli aralıklarla otomatikleştirmenizi sağlar; böylece cihazınız elle uğraşmanıza gerek kalmadan optimize kalır. Düzenli aralıklarla veya belirli zamanlarda çalışacak şekilde yapılandırılabilen **Uygulama Temizleyici** (AppCleaner), **ArtıkBulucu** (CorpseFinder) ve **Sistem Temizleyicisi** (SystemCleaner) gibi birden fazla aracı destekler.

<img src="/assets/images/scheduler/overview-1.webp" width="150">
<img src="/assets/images/scheduler/overview-2.webp" width="150">
<img src="/assets/images/scheduler/overview-3.webp" width="150">
<img src="/assets/images/scheduler/overview-4.webp" width="150">
<img src="/assets/images/scheduler/overview-5.webp" width="150">
<img src="/assets/images/scheduler/overview-6.webp" width="150">


## Zamanlama nasıl kurulur
{: #how-to-set-up-a-schedule }

1. **Ayarlar** bölümüne gidin ve **Zamanlayıcı** seçeneğini belirleyin.
2. Yeni bir zamanlama oluşturmak veya mevcut zamanlamaları yönetmek için **+** düğmesine dokunun.
3. Hangi araçların (ör. **Uygulama Temizleyici**, **ArtıkBulucu**, **Sistem Temizleyicisi**) bir zamanlamaya göre çalışacağını seçin.
4. **Gün cinsinden tekrar aralığını** (ör. 3 günde bir) ve çalıştırmanın **yaklaşık zamanını** ayarlayın.
5. Anahtarı açarak zamanlamayı etkinleştirin.

## Zamanlamaları düzenleme veya silme
{: #editing-or-deleting-schedules }

1. Zamanlamalarınızı yönetmek için **Zamanlayıcı** ayarlarına gidin.
2. Mevcut zamanlamaları değiştirebilir, aralıkları ayarlayabilir veya artık gerekmiyorsa silebilirsiniz.

Bir zamanlama etkinleştiğinde SD Maid seçilen görevleri belirtilen zamanlarda çalıştırır.

## Ek seçenekler
{: #additional-options }

Ana ayarlar menüsünden `Ayarlar > Zamanlayıcı` altında yapılandırılan birkaç ek seçenek vardır.

- **Düşük pilde atla**: Cihaz güç tasarrufu modundaysa zamanlanmış görevler atlanır.
- **Şarj olmazken atla**: Zamanlanmış görevler yalnızca cihaz şarj olurken çalışır ve pilin tükenmesi önlenir.
- **Program sonrası komutlar**: Zamanlanmış görevler tamamlandıktan sonra çalıştırılacak kabuk komutları belirleyebilirsiniz.
- **Erişilebilirlik hizmeti**: Varsayılan olarak zamanlayıcı, cihaz kilitliyken çalışmadıkları için Erişilebilirlik hizmetine dayalı özellikleri atlar. Cihazınızda başka hiçbir değişiklik yapmadan bunu etkinleştirirseniz zamanlayıcı başarısız olur. Bu nedenle ne yaptığınızı bilmiyorsanız bunu devre dışı bırakın. Bu yalnızca sürekli açık bir Android TV cihazı gibi uç durumlarda kullanışlıdır.

## Sorun giderme
{: #troubleshooting }

### Zamanlayıcı çalışmıyor 
{: #scheduler-is-not-executing }

Zamanlayıcının hiç çalışmaması ile yürütülen işlemlerin istenen sonuçları vermemesi arasında ayrım yapın. Çalıştırma geçmişini SD Maid'in [Geçmiş bölümünde](/tr/stats) kontrol edebilirsiniz. Zamanlayıcı çalıştıysa ancak başarısız olduysa bir hata görünmelidir.

Zamanlayıcı hiç çalışmadıysa, sistem pil tasarrufu için SD Maid'in başlatılmasını engellemiş olabilir. İlk zamanlamanızı etkinleştirdikten sonra, pil optimizasyonlarını devre dışı bırakmanızı isteyen, "Tavsiye" başlıklı ve "Onar" işlemi içeren bir uyarı görmüş olmalısınız. Bu uyarıyı yanlışlıkla kapattıysanız, SD Maid için sistem ayarlarınıza elle gidip SD Maid'in sistem tarafından "optimize edilip" edilmediğini kontrol etmeniz gerekir.

### Erişilebilirlik hizmeti
{: #accessibility-service }
Erişilebilirlik tabanlı silme işlemleri, ör. Uygulama Temizleyici'nin "erişilemeyen öğeleri" silmesi, zamanlayıcı üzerinden çalışmaz. Zamanlayıcı çalıştığında cihaz genellikle kilitlidir; kapalı bir ekranda veya kilitli bir ekranda sizin bir şeylere dokunamadığınız gibi SD Maid de dokunamaz.

Varsayılan olarak zamanlayıcı erişilebilirlik tabanlı işlemler gerçekleştirmez. Ancak ne yaptığınızı biliyorsanız zamanlayıcı ayarlarında "Erişilebilirlik hizmeti" seçeneğini etkinleştirebilirsiniz.

Geçici çözümler: Cihazı root'layın (kullanıcı + sistem uygulamaları) veya Shizuku'yu yükleyin (yalnızca kullanıcı uygulamaları). Her iki değişiklik de SD Maid'in Erişilebilirlik hizmeti özelliğini kullanmadan benzer işlemler gerçekleştirmesini sağlar.
