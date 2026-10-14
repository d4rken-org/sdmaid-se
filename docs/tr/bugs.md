---
title: "Hatalar"
lang: tr
translation_key: bugs
permalink: /tr/bugs
source_hash: a218be31a6c4ae63
---
# Hatalar
{: #bugs }

🐌 🦋 🐛 🐜 🐝 🐞

Hatalar bir uygulamanın hayatının parçasıdır. Size zarar vermek istemezler, ama kalıcı da değildirler 😠.

Hata nedir?

* Uygulama çöküyorsa bu açıkça bir hatadır
* Bir şey beklenmedik şekilde davranıyorsa bu bir hata olabilir.

Her durumda en son güncellemeyi kullandığınızdan emin olun, hata zaten düzeltilmiş olabilir.

Hata düzeltilmediyse bildirin :grin:.

## Hata bildirme
{: #reporting-a-bug }

GitHub üzerinde bir sorun kaydı (issue) oluşturun:
<https://github.com/d4rken-org/sdmaid-se/issues>

* Ne beklediğinizi ve gerçekte ne olduğunu açıklayın
* Başka birinin sorunu yeniden oluşturmak için izlemesi gereken adımları listeleyin
* Kurulumunuzu açıklayın
    * Cihaz modeliniz (ör. Pixel 5)
    * Android sürümü (ör. Android 13.1)
    * Cihazınız root'luysa Magisk sürümü

Sorunu yeniden oluşturabiliyorsanız bir [hata ayıklama günlüğü](#debug-log) ekleyin. 

İpucu: Sürümü panonuza kopyalamak için SD Maid ayarlarındaki ["Değişiklik günlüğü" girişine uzun basabilirsiniz](https://github.com/d4rken-org/sdmaid-se/pull/940).

## Hata ayıklama günlüğü
{: #debug-log }

Hata ayıklama günlüğü, SD Maid'in işlemlerinin bir kaydıdır. Kaydı başlatır, sorunu yeniden oluşturur, ardından kaydı durdurursunuz ve SD Maid'in ne yaptığının kronolojik dökümünü içeren bir zip dosyasını paylaşabilirsiniz.

Hata ayıklama günlüğü kaydını başlatmak için:
* Kontrol panelinin sağ alt köşesindeki ayarlar simgesine basarak SD Maid ayarlarına gidin
* Ayarlar listesinde `Destek` girişini bulun
* Destek ekranında `Hata ayıklama günlüğünü kaydet` seçeneğine, ardından iletişim kutusunda yeniden `Hata ayıklama günlüğünü kaydet` seçeneğine dokunun
* Kayıt sürerken kontrol panelinde `Hata ayıklama günlüğü kaydediliyor` kartı görünür
* **Şimdi kaydetmek istediğiniz sorunu yeniden oluşturun**
* Sorunu yeniden oluşturduktan sonra kontrol panelindeki bu kartta veya destek ekranında `Kaydı durdur` seçeneğine dokunun. Kayıt çok kısaysa SD Maid önce sorar; durdurmak için `Yine de durdur` seçeneğine dokunun.
* `Hata Ayıklama Günlüğü` ekranı açılır. Günlük dosyası sıkıştırıldıktan sonra göndermek için `Paylaş` seçeneğine dokunun.

Hata ayıklama günlüğü siz durdurana kadar kaydedilir; SD Maid'i zorla durdurup yeniden başlatsanız bile kayıt devam eder.

Root algılama veya algılanan depolama alanları gibi temel işlevlerle ilgili sorunları gideriyorsanız, bazı veriler uygulama oturumu başına yalnızca bir kez başlatıldığından kayıt sırasında SD Maid'i zorla durdurup yeniden başlatmak faydalıdır.

Hata ayıklama günlüğü dosyaları `/storage/emulated/0/Android/data/eu.darken.sdmse/cache/debug/logs` konumunda saklanır.

Resimlerle:

<a href="/assets/images/bugs/debug-log-1.webp"><img src="/assets/images/bugs/debug-log-1.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-2.webp"><img src="/assets/images/bugs/debug-log-2.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-3.webp"><img src="/assets/images/bugs/debug-log-3.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-4.webp"><img src="/assets/images/bugs/debug-log-4.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-5.webp"><img src="/assets/images/bugs/debug-log-5.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-6.webp"><img src="/assets/images/bugs/debug-log-6.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-7.webp"><img src="/assets/images/bugs/debug-log-7.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-8.webp"><img src="/assets/images/bugs/debug-log-8.webp" width="100" alt=""></a>
