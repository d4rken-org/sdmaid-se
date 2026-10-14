---
title: "SD Maid SE kurulumu"
lang: tr
translation_key: setup
permalink: /tr/setup
source_hash: a14003a95dd2336d
---
# SD Maid SE kurulumu
{: #setting-up-sd-maid-se }
SD Maid SE'nin farklı kurulum adımları hakkında bilgiler.

SD Maid, hangi erişimin mevcut olduğuna göre kendini uyarlar. Tüm izinler isteğe bağlı olsa da hiçbir izin vermezseniz özelliklerin çoğu çalışmaz. SD Maid, tüm kurulum öğeleri tamamlandığında en iyi performansı gösterir.

[Root](#root-access) veya [ADB erişimi](#shizuku) mevcutsa SD Maid bazı kurulum adımlarını otomatik olarak tamamlamaya çalışır.

Bazı izinler örtüşür veya birbirini kapsar; ör. SD Maid'e hem root erişimi hem de ADB erişimi verebilirsiniz. SD Maid her işlem için her zaman mümkün olan en iyi erişim yöntemini kullanmaya çalışır; bu başarısız olursa ikinci en iyi erişim yöntemiyle, ardından üçüncü en iyisiyle vb. yeniden dener. SD Maid'e örtüşen izinler vermek güvenilirliği artırır.

> **İpucu:** Aşağıdaki bazı kurulum adımları ADB komutları çalıştırmayı içerir. Bilgisayarınızda ADB kurulu değilse, bunları doğrudan tarayıcınızdan çalıştırmak için [Web ADB Helper](https://d4rken.github.io/web-adb/) aracını kullanabilirsiniz.

## Depolama alanını yönet
{: #manage-storage }

"Depolama alanını yönet" izni, kapsamı geniş bir depolama erişim iznidir.

* Android 12 ve öncesinde tüm genel depolamaya erişim sağlar.
* Android 13+ sürümünde yine tüm genel depolamaya erişim sağlar, ancak `Android/data` altındaki alt dizinlere erişim sağlamaz.

## Kullanım istatistikleri
{: #usage-statistics }

Kullanım istatistikleri izinleri, SD Maid'e verilere, önbellek boyutlarına ve bir uygulamanın çalışıp çalışmadığına veya en son ne zaman kullanıldığına erişim sağlar.

## Depolama erişim çerçevesi
{: #storage-access-framework }
SD Maid, ek depolama alanlarına erişim kazanmak için [Android Depolama Erişim Çerçevesi'ni (Storage Access Framework, SAF)](http://developer.android.com/guide/topics/providers/document-provider.html) kullanır. SD Maid, yalnızca "Depolama alanını yönet" iznine sahip olarak erişilemeyen belirli yollara erişim vermenizi ister (Just Android Things™️).

* `Erişim izni ver` düğmesine basın 
* Yeni bir pencere açılır
* Doğru yol zaten seçili olabilir; değilse SD Maid'in istediği yola gitmeniz gerekir.
* Ardından `Use this folder` ("Bu klasörü kullan") düğmesine basın; bu yola ait kurulum girişinin kilidi açılır ve yeşil renkte görünür.

Tüm yollar yeşil olana kadar bu işlemi tekrarlayın.

<a href="/assets/images/setup/saf-1.webp"><img src="/assets/images/setup/saf-1.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-2.webp"><img src="/assets/images/setup/saf-2.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-3.webp"><img src="/assets/images/setup/saf-3.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-4.webp"><img src="/assets/images/setup/saf-4.webp" width="100" alt=""></a>

### SAF: Sorun giderme
{: #saf-troubleshooting }
İşler planlandığı gibi gitmediğinde.

#### SAF: `Android/data|obb` erişimi
{: #saf-androiddataobb-access }

Android 11 ve Android 12'de, sistemin `Files` ("Dosyalar") uygulamasının (`com.google.android.documentsui`) yeni sürümleriyle `Android/data|obb` erişimi vermek mümkün değildir. Yüklü `Files` uygulaması Android 14'ü (API düzeyi 34) veya daha yenisini hedefliyorsa SD Maid bu yollar için SAF erişim isteğini gizler (Android 13+ sürümünde olduğu gibi).

Google Play, `Files` uygulamasını `Google Play system update` ("Google Play sistem güncellemesi") hizmeti aracılığıyla günceller. Android 12 işletim sistemiyle birlikte gelen orijinal "Files" uygulaması (API düzeyi 31 için derlenmiş, ör. `aml_doc_310851020 (310851020)`), SD Maid'in `Android/Data` üzerinde SAF yol seçim penceresini açmasına izin verirken en son "Files" uygulaması (API düzeyi 34 veya sonrası için derlenmiş, ör. `14-10492947 (340916000)`) bunu engeller.

**Geçici çözüm:**

`Files` uygulamasının güncellemesini kaldırıp ardından SD Maid'i kurabilirsiniz. `Files` uygulamasını yeniden güncellemek, daha önce verilmiş izinleri bozmaz.

* Cihaz ayarlarını açın
* "Apps & notifications" ("Uygulamalar ve bildirimler") veya "Application Manager" ("Uygulama yöneticisi") bölümüne gidin
* `Files` sistem uygulamasını bulun
* Genellikle sağ üst köşede bulunan üç nokta menüsüne dokunun
* "Uninstall updates" ("Güncellemeleri kaldır") seçeneğini belirleyin

veya

_Bu komutu [Web ADB Helper](https://d4rken.github.io/web-adb/) ile de çalıştırabilirsiniz._

`adb uninstall com.google.android.documentsui`

#### SAF: OPEN_DOCUMENT_TREE ActivityNotFoundException
{: #saf-open_document_tree-activitynotfoundexception }

Depolama Erişim Çerçevesi aracılığıyla yollara erişim vermek, kullanıcının erişim verilecek yolları onaylamasını sağlayan bir uygulama gerektirir. Bu uygulama "Documents" ("Belgeler") uygulamasıdır. Android 5.0'dan bu yana her Android cihazda yerleşik olarak bulunan bir sistem uygulamasıdır (`com.android.documentsui` veya `com.google.android.documentsui`). Bu hatayı alıyorsanız, ya biri bu uygulamayı silmiş ya da devre dışı bırakmıştır. Çoğu durumda biri aşırı hevesli bir "şişkinlik giderme" (de-bloating) aracı çalıştırmış ve uygulamayı devre dışı bırakmıştır. ADB aracılığıyla yeniden etkinleştirebilirsiniz:

_Bu komutları [Web ADB Helper](https://d4rken.github.io/web-adb/) ile de çalıştırabilirsiniz._

```
adb shell pm enable com.android.documentsui
or
adb shell pm enable com.google.android.documentsui
```

#### SAF: Boş pencere
{: #saf-empty-window }
Yollara erişim vermek için kullanılan pencere boşsa, harici depolama sağlayıcı paketiniz (`com.android.externalstorage`) muhtemelen devre dışı bırakılmıştır (büyük olasılıkla aşırı hevesli bir "şişkinlik giderme" aracıyla). ADB aracılığıyla yeniden etkinleştirebilirsiniz:

_Bu komutu [Web ADB Helper](https://d4rken.github.io/web-adb/) ile de çalıştırabilirsiniz._

```
adb shell pm enable com.android.externalstorage
```

`Google Play system update` ("Google Play sistem güncellemesi") işlemini yeniden çalıştırmak güncellemeyi geri yükler. Güncellenmiş bir sürümü [APKMirror.com](https://www.apkmirror.com/apk/google-inc/files-6/) adresinden elle de indirebilirsiniz.

## Bildirimler
{: #notifications }

Bu izin olmadan SD Maid bildirim gösteremez. Bazı ROM'larda bu, arka planda çalışırken güvenilirliği olumsuz etkileyebilir.

## Uygulama envanteri
{: #app-inventory }

SD Maid, cihazınızda hangi uygulamaların yüklü olduğunu kontrol etmek (yani ["yüklü uygulamaların envanterine görünürlük"](https://support.google.com/googleplay/android-developer/answer/10158779?hl=en)) için `QUERY_ALL_PACKAGES` iznine ihtiyaç duyar. Ayrıca bkz. ["Kullanım istatistikleri izni"](/tr/setup#usage-statistics)

`QUERY_ALL_PACKAGES` izni şunlar için zorunludur:

* [ArtıkBulucu (CorpseFinder)](/tr/corpsefinder): Hangi dosyaların silinebileceğini anlamak için SD Maid'in, bunlara ihtiyaç duyabilecek uygulamaların yüklü olup olmadığını bilmesi gerekir.
* [Uygulama Temizleyici (AppCleaner)](/tr/appcleaner): Her uygulamanın önbelleklerini belirleme
* [Uygulama Kontrolü (AppControl)](/tr/appcontrol): Yüklü tüm uygulamaları listeleyebilme
* [Depolama Analizcisi (StorageAnalyzer)](/tr/storageanalyzer): Kullanılan depolama alanını yüklü uygulamalara atama

Bu izin [Android 11](https://developer.android.com/training/package-visibility) sürümünden beri vardır ve normalde varsayılan olarak verilir. Android 14'ten bu yana iznin varsayılan olarak verilmediği veya geri alınabildiği bazı cihazlar (ör. [#1015](https://github.com/d4rken-org/sdmaid-se/issues/1015), [#1354](https://github.com/d4rken-org/sdmaid-se/issues/1354)) bulunmaktadır.

[<img src="/assets/images/setup/app-inventory.webp" width="100">](/assets/images/setup/app-inventory.webp)

### Geçersiz uygulama listesi
{: #invalid-app-list }

Bazı cihazlarda "App list" ("Uygulama listesi") izni (`QUERY_ALL_PACKAGES` / `GET_INSTALLED_APPS`) verilmiş olsa da sistem yine de **eksik bir yüklü uygulama listesi** döndürür; örneğin `android` gibi temel sistem paketlerinin eksik olduğu bir liste. SD Maid bunu algıladığında devam etmek yerine kurulum kartında bir **"Invalid app list"** ("Geçersiz uygulama listesi") uyarısı ve bir **"Olası cihaz sınırlaması"** notu gösterir.

SD Maid burada bilerek durur: yüklü uygulamaların tam listesini göremezse hangi dosyaların hâlâ yüklü olan bir uygulamaya ait olduğunu güvenilir şekilde ayırt edemez. Yine de devam etmek, SD Maid'in yalnızca göremediği bir uygulamaya ait verilerin silinmesi anlamına gelebilir; bu nedenle uygulamayla ilgili özellikler tam bir liste elde edilene kadar devre dışı kalır.

**Deneyebilecekleriniz:**

* Sistem ayarlarınızı açın ve SD Maid'e "App list" ("Uygulama listesi") izninin verildiğinden emin olun. Bazı üreticilerde bu anahtarın adı farklıdır (ör. "Query all packages", "Tüm paketleri sorgula") veya ayrı bir "Privacy" ("Gizlilik") ya da "Permission manager" ("İzin yöneticisi") ekranında bulunur.
* Yerleşik bir pil tasarrufu, gizlilik koruması veya "uygulama gizleme" özelliğinin SD Maid'in görebildiği uygulama listesini filtreleyip filtrelemediğini kontrol edin ve SD Maid'i bundan hariç tutun.
* İzin durumunun yeniden değerlendirilmesi için cihazı yeniden başlatın ve SD Maid'i yeniden açın.

"App list" ("Uygulama listesi") izni zaten verilmişse ve liste hâlâ geçersiz olarak bildiriliyorsa bu genellikle düzeltebileceğiniz bir şey değil, bir **cihaz sınırlamasıdır**: bazı sistemler (örneğin HarmonyOS veya yoğun biçimde değiştirilmiş Android sürümleri) üçüncü taraf uygulamalara hiçbir zaman tam bir uygulama listesi vermez. Bir örnek için bkz. [#2441](https://github.com/d4rken-org/sdmaid-se/issues/2441).

Bu durumda uygulamayla ilgili araçlar ([ArtıkBulucu](/tr/corpsefinder), [Uygulama Temizleyici](/tr/appcleaner), [Uygulama Kontrolü](/tr/appcontrol)) verilerinizi korumak için devre dışı kalır, ancak [Depolama Analizcisi](/tr/storageanalyzer) yine de çalışır: depolamanızın ne kadar alan kullandığını, yalnızca daha az ayrıntıyla gösterir. Klasör boyutları salt okunur olarak gösterilir ve tek tek uygulamalara atanamaz.

## Erişilebilirlik hizmeti
{: #accessibility-service }
Erişilebilirlik hizmeti, SD Maid'in cihazınızla bir insan gibi etkileşime girmesini, yani ekranı görmesini ve düğmelere basmasını sağlar. SD Maid bunu, yüzlerce uygulamada "Önbelleği temizle" düğmesine basmak gibi sıkıcı işlemleri otomatikleştirmek için kullanabilir. Erişilebilirlik hizmeti, uygulamanın kendisinin başlatamadığı bir hizmettir; sistem tarafından başlatılması gerekir, çünkü bu tür bir hizmet bir uygulamaya cihazınıza ek erişim verir.

### ACS: Sorun giderme
{: #acs-troubleshooting }

Hizmet çeşitli durumlarda çalışmayı durdurabilir ve yeniden etkinleştirmenizi gerektirebilir:
* Uygulamayı güncellediğinizde.
* Uygulama çöktüğünde. Böyle bir durumda bunu hata olarak bildirmelisiniz. Bu çok sık olursa cihaz yeniden başlatılana kadar hizmeti kapatıp açarak yeniden başlatamayabilirsiniz.
* Sistem, aşırı agresif "pil optimizasyonları" nedeniyle uygulamayı sonlandırdığında (ayrıca bkz. <https://dontkillmyapp.com/>)
* Üreticinin kendi güvenlik uygulaması hizmeti kapattığında (bkz. [Phone Manager tarafından engellendi](#acs-blocked-by-phone-manager-oneplus-oppo-realme))

#### ACS: Hizmet devre dışı
{: #acs-service-is-disabled }

Bazı ROM'lar (ör. MIUI), cihaz üreticisinin beyaz listesinde olmayan her şeyi agresif biçimde durdurmalarıyla bilinir.
Erişilebilirlik hizmetini sürekli yeniden etkinleştirmeniz isteniyorsa, sistem, SD Maid'in etkin olarak kullanılmadığı her seferde hizmeti devre dışı bırakıyor olabilir.

Pil optimizasyonlarını devre dışı bırakmayı deneyin; MIUI ROM'larında ayrıca "Autostart" ("Otomatik başlatma") adlı ek bir ayar vardır, bunu SD Maid için etkinleştirin. Xiaomi/POCO/Redmi cihazlar için:

* `Security>Manage applications>Auto start in background` bölümüne gidin ve buna SD Maid için izin verin.
* `Security>Increase speed>Lock apps` bölümüne gidin ve SD Maid'i seçin.


Ayrıca [SD Maid'in hizmeti otomatik olarak yeniden başlatmasını sağlayan](#acs-automatic-service-restart) ek bir izin vermeyi de düşünebilirsiniz.

[<img src="/assets/images/setup/acs-disabled.webp" width="100">](/assets/images/setup/acs-disabled.webp)

#### ACS: Phone Manager tarafından engellendi (OnePlus, OPPO, Realme)
{: #acs-blocked-by-phone-manager-oneplus-oppo-realme }

OxygenOS ve ColorOS'ta önceden yüklü "Phone Manager" ("Telefon Yöneticisi") uygulaması, erişilebilirlik hizmetini kendi başına kapatabilir; bu genellikle bir temizlik bittikten sonraki bir saniye içinde gerçekleşir. Android'in erişilebilirlik anahtarı bundan sonra gerçekten kapalıdır, bu yüzden SD Maid çalışmanın ortasında durmuş gibi görünür.

Özelliği `Phone Manager>Utilities>Block suspicious app activities` altında kapatın.

Aynı ekran, neleri engellediğinin bir listesini tutar. Çalıştırdığınız temizliklerle eşleşen zaman damgalarına sahip "Abnormal device control" ("Anormal cihaz kontrolü") etiketli girişler, nedenin bu olduğunu doğrular. Yanındaki "Blocking alerts" ("Engelleme uyarıları") anahtarı yalnızca bildirim alıp almayacağınızı denetler; dolayısıyla uyarılar kapalıyken bu sessizce gerçekleşir.

#### ACS: ADB ile etkinleştirme
{: #acs-enable-via-adb }

Cihazınızda erişilebilirlik hizmetlerini ekran üzerinden etkinleştirmenin bir yolu yoksa (ör. bir TV cihazı), hizmeti ADB kullanarak şu komutu çalıştırıp etkinleştirmek mümkündür:

_Bu komutu [Web ADB Helper](https://d4rken.github.io/web-adb/) ile de çalıştırabilirsiniz._

```bash
adb shell settings put secure enabled_accessibility_services eu.darken.sdmse/.automation.core.AutomationService
```

#### ACS: Hizmetin otomatik yeniden başlatılması
{: #acs-automatic-service-restart }

_Not: Bu ileri düzey bir tekniktir ve deneyimli kullanıcılara yöneliktir._

SD Maid'in hizmeti kendi başına yeniden başlatmasını sağlayan ek bir izin verebilirsiniz.
İznin adı `WRITE_SECURE_SETTINGS`'tir ve yalnızca [ADB aracılığıyla](https://www.google.com/search?q=android+adb) verilebilir.
Bunu kurduysanız izni vermek için şu komutu çalıştırın:

_Bu komutu [Web ADB Helper](https://d4rken.github.io/web-adb/) ile de çalıştırabilirsiniz._

```bash
adb shell pm grant eu.darken.sdmse android.permission.WRITE_SECURE_SETTINGS
```

Bu izin verildiğinde SD Maid, gerektiğinde hizmeti etkinleştirmek için [burada açıklanan](#acs-enable-via-adb) komutu kullanabilir.
SD Maid bunu yalnızca erişilebilirlik hizmetini en az bir kez elle etkinleştirdiyseniz kullanır.
Kuruluma yeniden girip SD Maid'e erişilebilirlik hizmetini kullanmamasını söylerseniz, erişilebilirlik hizmetini yeniden elle etkinleştirene kadar bu otomatik yeniden başlatma tekniği kullanılmaz.

Bu izni SD Maid'den yeniden kaldırmak için şu komutu kullanın:

_Bu komutu [Web ADB Helper](https://d4rken.github.io/web-adb/) ile de çalıştırabilirsiniz._

```bash
adb shell pm revoke eu.darken.sdmse android.permission.WRITE_SECURE_SETTINGS
```

`Operation not allowed: java.lang.SecurityException` hatası alıyorsanız [buraya bakın](https://stackoverflow.com/questions/52079343). Bazı cihazlar ADB iznini kısıtlar. Geliştirici seçeneklerinde açılıp kapatılması gereken ek bir ayar (ör. `Disable Permission Monitoring`) olup olmadığına bakın; komutu kullandıktan sonra bu ayarı yeniden devre dışı bırakabilirsiniz.

#### ACS: Gelişmiş Koruma
{: #acs-advanced-protection }

Android 17 ve sonrasında Google'ın [**Gelişmiş Koruma**](https://developer.android.com/privacy-and-security/advanced-protection-mode) (Advanced Protection) modu, özel erişilebilirlik araçları (ekran okuyucular, anahtar erişimi vb.) olmayan uygulamaların erişilebilirlik hizmetini kullanmasını engeller. SD Maid bir bakım/temizlik aracıdır; bu nedenle Gelişmiş Koruma etkinken erişilebilirlik hizmeti **etkinleştirilemez**. Android, hizmeti root veya ADB erişimi aracılığıyla vermeye çalışsanız bile sistem düzeyinde reddeder.

SD Maid kendini bundan muaf tutamaz: Yalnızca Google'ın erişilebilirlik aracı olarak sınıflandırdığı uygulamalar uygundur ve temizleyiciler açıkça hariç tutulmuştur.

İki seçeneğiniz vardır:

- **Root veya ADB erişimi kullanın** Root veya ADB erişimi kurulduğunda SD Maid'in Uygulama Temizleyici aracı uygulama önbelleklerini doğrudan bir sistem çağrısıyla temizler ve erişilebilirlik hizmetine **ihtiyaç duymaz**. **Root** ile bu, seçilen tek tek uygulamaların temizlenmesini de kapsar. **ADB erişimi** ile tam bir "tüm uygulamaları temizle" çalıştırması erişilebilirlik hizmeti olmadan çalışır, ancak seçilen *belirli* uygulamaların önbelleğini temizlemek yine ona dayanır. Bunu SD Maid'de Kur → [Kök erişimi](#root-access) veya [ADB erişimi](#shizuku) altında ayarlayın.
- Özellikle erişilebilirlik tabanlı temizlik istiyorsanız **Gelişmiş Koruma'yı kapatın**. `Settings → Security & privacy → Advanced Protection` ("Ayarlar → Güvenlik ve gizlilik → Gelişmiş Koruma") altında bulunur (tam yol cihaza göre değişir). Daha sonra yeniden açmak erişilebilirlik hizmetini yeniden devre dışı bırakır.

#### ACS: AppOps kısıtlamaları
{: #acs-appops-restrictions }

Android 13+, dışarıdan yüklenmiş (sideload) uygulamalara ek kısıtlamalar uygulayabilir. Dolayısıyla SD Maid, Google Play veya diğer uygulama mağazaları gibi (Google tarafından) bilinen bir kaynaktan yüklenmediyse SD Maid'e bazı izinleri doğrudan veremeyebilirsiniz, ör. bir erişilebilirlik hizmetini başlatmak.

##### OxygenOS 15 - OnePlus
{: #oxygenos-15---oneplus }

OnePlus, OxygenOS 15'te uygulama ayarlarına `Allow restricted settings` ("Kısıtlanmış ayarlara izin ver") işlemini eklemeyi unuttu (ya da eklemedi). Google Play üzerinden yüklemiyorsanız yükleyicinin `INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS` bayrağını ayarlaması gerekir. Shizuku kuruluysa ["Install with Options" uygulamasını](https://github.com/zacharee/InstallWithOptions) kullanabilirsiniz.


##### Diğer ROM'lar
{: #other-roms }

Bu kısıtlamaları kaldırmak için sistemde SD Maid'in uygulama ayarları ekranını açın. Sağ üst köşedeki menüyü açın ve `Allow restricted settings` ("Kısıtlanmış ayarlara izin ver") seçeneğini belirleyin; ardından erişilebilirlik hizmetleri listesine geri dönüp SD Maid'in hizmetini etkinleştirebilirsiniz.

**Not:**
Kullanılabilirliği artırmak için (bu bir iğnelemedir) Google, bu ayarın yalnızca şu durumlarda görünür olmasına karar verdi:

* SD Maid'in erişilebilirlik hizmeti girişine dokunduktan sonra "Restricted setting" ("Kısıtlanmış ayar") uyarısını en az bir kez gördüyseniz
* Sistem ayarlarını açıp SD Maid'in ayarlar sayfasına başka bir uygulama veya kısayol aracılığıyla değil, elle gittiyseniz.

😮‍💨


Bazı ek arka plan bilgilerini [burada bulabilirsiniz](https://blog.esper.io/android-13-sideloading-restriction-harder-malware-abuse-accessibility-apis/).

<a href="/assets/images/setup/acs-restricted-settings-1.webp"><img src="/assets/images/setup/acs-restricted-settings-1.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-2.webp"><img src="/assets/images/setup/acs-restricted-settings-2.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-3.webp"><img src="/assets/images/setup/acs-restricted-settings-3.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-4.webp"><img src="/assets/images/setup/acs-restricted-settings-4.webp" width="100" alt=""></a>

## Kök erişimi
{: #root-access }

Root erişimiyle SD Maid cihazınıza tam erişime sahip olur ve mümkün olan en kapsamlı taramayı yapabilir. Bu genellikle önyükleyicinizin (bootloader) kilidini açtığınız ve Magisk'i kurduğunuz anlamına gelir.

SD Maid cihazınızın root'lu olabileceğini düşünürse önce root erişimi ister. Root erişimi verilirse SD Maid, diğer izinlerin mümkün olduğunca çoğunu vermek için root erişimini kullanır. Root erişimi bir uygulamaya verebileceğiniz en yüksek erişim düzeyidir; diğer izinler bu durumda yalnızca kullanım kolaylığı ve performans için gereklidir. Bu yüzden SD Maid zaten root erişimine sahipse kurulum sırasında ek onay gerektirmezler.

## ADB erişimi
{: #shizuku }

ADB erişimi, SD Maid'in yükseltilmiş işlemler için Android'in hata ayıklama arayüzünü (ADB) kullanmasını sağlar. Cihazınızda bir ADB hizmeti çalıştıran ve diğer uygulamalara bu hizmete erişim veren ayrı bir uygulama gerekir: [Porter](#porter) veya [Shizuku](#using-shizuku). Bu, telefonunuzda yapabileceğiniz ve diğer uygulamaların da yararlanabileceği bir değişikliktir.

Root'a benzer, ancak aynı değildir. İzinler biraz daha az güçlüdür, ancak değişiklik kalıcı değildir ve önyükleyicinizin kilidini açmanızı gerektirmez.

### Porter
{: #porter }

[Porter](https://porter.darken.eu), isteğe bağlı root desteğine sahip, bakımını yaptığım Shizuku çatalıdır. Web sitesi nasıl yükleneceğini ve başlatılacağını açıklar. Android 11 ve sonrasında kablosuz hata ayıklama, onu bilgisayar olmadan başlatmanızı sağlar.

ADB erişimini ilk kez kuruyorsanız Porter'ı kullanın. GitHub ve F-Droid'deki SD Maid derlemeleri, hiçbir ADB erişim uygulaması yüklü değilse sizi Porter'a yönlendirir. Google Play derlemesi sizi Google Play'deki Shizuku'ya yönlendirir, ancak Porter ile de aynı şekilde çalışır.

Hem Porter hem de Shizuku yüklüyse SD Maid, Porter'ı kullanır. Kurulum kartında bu durumda `Porter yüklü olduğundan, SD Maid Shizuku yerine Porter için bekliyor` yazar. Devam etmek için Porter'ı başlatın veya SD Maid'in Shizuku'yu kullanmasını istiyorsanız Porter'ı kaldırın.

### Shizuku kullanma
{: #using-shizuku }

Shizuku'nun iki derlemesi vardır:

* [thedjchi'nin çatalı](https://github.com/thedjchi/Shizuku) ([kurulum kılavuzu](https://github.com/thedjchi/Shizuku/wiki/setup)) aktif olarak geliştirilmektedir ve orijinalinin [hiç bağlanmadığı](#shizuku-never-connects) cihazlarda çalıştığı bildirilmektedir.
* [RikkaApps'in orijinali](https://shizuku.rikka.app/guide/setup/) çoğu cihazda hâlâ sorunsuz çalışır.

İkisi aynı anda yüklenemez. Orijinali sizin için zaten çalışıyorsa geçiş yapmanıza gerek yoktur.

### Erişim verme
{: #granting-access }

Porter'ı veya Shizuku'yu kurduktan sonra:

* SD Maid'in kurulumunda `ADB erişimini kullanmayı dene` seçeneğini belirleyin. Bu SD Maid'in kendi içindeki bir ayardır; SD Maid'in ADB erişimini kullanmasını etkinleştirmek/devre dışı bırakmak için kullanabilirsiniz
* Onay verdikten sonra Porter'dan veya Shizuku'dan gelen bir iletişim kutusu SD Maid'in onu kullanıp kullanamayacağını sorar
* İletişim kutusunu onaylayın
* `ADB erişimi` kurulum kartı SD Maid'in bağlanıp bağlanamadığını gösterir

SD Maid'e zaten root erişimi verdiyseniz ADB erişimini kurmak ek özellikler etkinleştirmez, ancak güvenilirliği artırır. İşlemler root izniyle başarısız olursa SD Maid, ADB erişimiyle yeniden dener.

### ADB erişimi: Sorun giderme
{: #shizuku-troubleshooting }

SD Maid'in ADB erişimi durumunu `Ayarlar > Kur` bölümüne gidip `ADB erişimi` kurulum kartına bakarak kontrol edebilirsiniz. Kartta `SD Maid artık Porter aracılığıyla ADB erişimini kullanabilir` (veya `Shizuku aracılığıyla`) yazmalıdır.

* `Porter henüz SD Maid'e izin vermedi`: erişim iletişim kutusunu onaylayın veya Porter (ya da Shizuku) uygulamasında SD Maid'e izin verin.
* `Porter, SD Maid erişimini reddediyor`: SD Maid kalıcı olarak reddedilmiştir. Porter'ı (veya Shizuku'yu) açın ve orada SD Maid'e izin verin.
* `SD Maid ile kullanmak için Porter güncelle` veya `Bu SD Maid sürümü Porter için çok eski`: iki uygulama uyumlu bir sürümü paylaşmıyor. Kartın belirttiği uygulamayı güncelleyin.

#### Shizuku hiç bağlanmıyor
{: #shizuku-never-connects }

Kartta `ADB erişimi başarısız oldu` ve `Shizuku çalışıyor ama SD Maid'i yardımcı hizmetine bağlamadı.` yazar. Eski sürümlerde `Waiting for link with Shizuku` ("Shizuku ile bağlantı bekleniyor") veya `Shizuku link failed` ("Shizuku bağlantısı başarısız oldu") yazıyordu.

Shizuku uygulamasını açın. Çalıştığını söylüyorsa ve SD Maid yetkili listesindeyse Shizuku düzgün kurulmuştur ve yeniden kurmak işe yaramaz. Başarısız olan, Shizuku'nun yardımcı hizmetidir: geri bildirimde bulunamadan başlangıçta çöker ve SD Maid yaklaşık 15 saniye sonra vazgeçer. `Yeniden dene` aynı kontrolü çalıştırır ve aynı şekilde başarısız olur.

Bu bir SD Maid hatası değil, bir Shizuku hatasıdır ([RikkaApps/Shizuku#1198](https://github.com/RikkaApps/Shizuku/issues/1198)) ve Shizuku'nun yardımcı hizmetini kullanan her uygulamayı bozar. MediaTek cihazları ve HyperOS ya da MIUI çalıştıran cihazları etkiler.

[thedjchi'nin çatalının](https://github.com/thedjchi/Shizuku) bunu düzelttiği bildirilmektedir. Geçiş yapmak için:

* Önce orijinali kaldırın, ikisi bir arada bulunamaz
* Kablosuz hata ayıklama kullandıysanız onun eşleştirmesini kaldırın
* Çatalı [sürümlerinden](https://github.com/thedjchi/Shizuku/releases) yükleyin ve [kurulum kılavuzunu](https://github.com/thedjchi/Shizuku/wiki/setup) izleyin
* SD Maid'e ve onu kullanan diğer her şeye Shizuku iznini yeniden verin
