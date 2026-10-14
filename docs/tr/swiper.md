---
title: "Kaydırıcı"
lang: tr
translation_key: swiper
permalink: /tr/swiper
source_hash: 4c7d8b7a30d3a36e
---
# Kaydırıcı (Swiper)
{: #swiper }

Kaydırıcı (Swiper), belirli klasörleri bir dosya gezgininden daha hızlı ayıklamanıza yardımcı olur. Dosyaları tek tek gözden geçirir ve her birini saklamak, silmek veya atlamak için kaydırırsınız. Seçimlerinizi gözden geçirip onaylayana kadar hiçbir şey silinmez.

## Oturum başlatma
{: #starting-a-session }

1. Kontrol panelinden Kaydırıcı'yı açın ve `Klasörleri seçin` seçeneğine dokunun. Klasörleri, tek tek dosyaları veya her ikisini birden seçebilirsiniz.
2. İsteğe bağlı olarak oturum kartında bir dosya türü filtresi ve sıralama düzeni ayarlayın.
3. `Tara` seçeneğine dokunun. Klasörler, tüm alt klasörleri dahil aranır.
4. `Kaydırmaya başla` seçeneğine dokunun.

[Depolama Analizcisi](/tr/storageanalyzer) üzerinden de oturum oluşturabilirsiniz: orada öğeleri seçin ve `Swiper oturumu oluştur` seçeneğini belirleyin.

Başlamak için iyi yerler kamera albümünüz, İndirilenler klasörü ve ekran görüntüleridir.

### Dosya türleri
{: #file-types }

`Yalnızca bu dosya türlerini göster`, oturumu görsellerle, videolarla, ses dosyalarıyla, belgelerle veya arşivlerle sınırlar. Kendi uzantılarınızı da girebilirsiniz, ör. `apk, apks`. Hiçbir şey işaretlenmezse tüm dosyalar gösterilir. Dosyalar yalnızca uzantılarına göre eşleştirilir.

### Sıralama düzeni
{: #sort-order }

Dosyalar varsayılan olarak son değiştirilme tarihlerine göre en eskiden başlayarak gösterilir. Ayrıca en yeniden başlayarak, ada göre veya en büyükten başlayarak da sıralayabilirsiniz.

Bir tarama dosya bulduktan sonra filtre ve sıralama düzeni değiştirilemez. Bunları değiştirmek için yeni bir oturum başlatın.

### Bütün bir depolamayı tarama
{: #scanning-a-whole-storage }

Bir depolamanın en üst düzeyini, ör. dahili depolamanızın tamamını seçerseniz, fotoğraflar ve uygulama medyası dahil üzerindeki her dosya oturuma girer. SD Maid taramadan önce onaylamanızı ister. Daha sonra böyle bir oturumdan dosya sildiğinizde önce `Bu dosyaların kalıcı olarak silineceğini anlıyorum.` kutusunu işaretlemeniz gerekir.

## Kaydırma
{: #swiping }

| Hareket | Varsayılan işlem |
|---|---|
| Sağa kaydırma | Sakla |
| Sola kaydırma | Sil |
| Yukarı kaydırma | Atla |
| Aşağı kaydırma | Son kararı geri al |

Alttaki düğmeler de aynı şeyi yapar. [Kaydırma yönlerini değiştir](#swap-swipe-directions) etkinse sol ve sağ yer değiştirir.

* **Atla**, bir dosyayı kararsız bırakır. Atlanan dosyalar, geri kalanından sonra yeniden karşınıza çıkar.
* **Geri al**, son kararlarınızda 50'ye kadar geriye gider.
* Bir dosyayı gelecekteki Kaydırıcı taramalarından hariç tutmak için **Atla'ya uzun basın**. Bu, bir yol [hariç tutması](/tr/exclusions) oluşturur ve dosyayı oturumdan kaldırır.
* Karttaki düğmeler dosyayı başka bir uygulamada açar veya tam ekran bir önizleme gösterir.
* O dosyaya atlamak için şeritteki bir küçük resme dokunun. Onu yeniden kaydırmak önceki kararınızın yerini alır.

İstediğiniz zaman çıkabilirsiniz. Kararlarınız oturumla birlikte kaydedilir ve daha sonra devam edebilirsiniz. SD Maid yeniden başladığında artık bulamadığı dosyalar oturumdan çıkarılır. O zamana kadar oturum, başka bir şey tarafından silinmiş veya taşınmış dosyaları hâlâ listeleyebilir.

## Gözden geçirme ve silme
{: #reviewing-and-deleting }

Tüm kararları görmek için `Değerlendir` seçeneğine dokunun. Kararsız dosya kalmadığında gözden geçirme ekranı kendiliğinden açılır.

* Kararları dosya bazında değiştirin veya birkaç dosyayı aynı anda seçmek için uzun basın.
* Bir dosyaya geri dönmek için ona dokunun.
* `Sil`, herhangi bir işlem yapılmadan önce kaç dosyanın ve ne kadar alanın etkilendiğini ve hangi klasörlerde olduklarını gösterir.

Silme kalıcıdır. Dosyalar bir çöp kutusu klasörüne taşınmaz ve SD Maid bunları geri yükleyemez.

Silme işleminden sonra saklanan ve silinen dosyalar oturumdan çıkar. Kararsız dosyalar daha sonrası için kalır. Hiçbir şey kalmadığında oturum kaldırılır. Bazı dosyalar silinemediyse oturumda kalırlar ve yeniden deneyebilirsiniz.

Bir oturumu kaldırmak yalnızca kararlarınızı siler. Hiçbir dosyaya dokunmaz.

### Bilinmesi gerekenler
{: #things-to-know }

* **Sakla yalnızca geçerli oturum için geçerlidir.** Aynı klasörde yeni bir oturum başlatırsanız saklanan dosyalar yeniden görünür. Bir dosyayı kalıcı olarak gizlemek için onu hariç tutun (Atla'ya uzun basın).
* **Bir oturum yeni dosyaları algılamaz.** Taramadan sonra klasöre eklenen dosyalar görünmez. Bunları dahil etmek için yeni bir oturum başlatın.
* **Arşivler APK dosyalarını içerir.**
* **Gizli dosyalar dahil edilir.**

## Ücretsiz sürüm
{: #free-version }

Yükseltme olmadan Kaydırıcı, her biri 50 dosyalık 2 oturumla sınırlıdır. Bir sıralama düzeni ayarlanmışsa o sıradaki ilk 50 dosyayı alırsınız.

## Ayarlar
{: #settings }

### Kaydırma yönlerini değiştir
{: #swap-swipe-directions }

Saklamak için sola, silmek için sağa kaydırın. Varsayılan olarak kapalıdır.

### Dosya ayrıntılarını göster
{: #show-file-details }

Kaydırma ekranında dosya adını, boyutunu, tarihini ve klasörünü gösterir. Varsayılan olarak açıktır.

### Dokunsal geri bildirim
{: #haptic-feedback }

Bir dosyayı sakladığınızda veya sildiğinizde titreşir. Varsayılan olarak açıktır.
