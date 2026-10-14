---
title: "Sistem Temizleyicisi"
lang: tr
translation_key: systemcleaner
permalink: /tr/systemcleaner
source_hash: 1ced6df9b00f1e05
---
# Sistem Temizleyicisi (SystemCleaner)
{: #system-cleaner }

Sistem Temizleyicisi (SystemCleaner) aracı temelde genel amaçlı bir arama motorudur. Erişilebilen tüm depolama alanlarını tarar ve bulunan dosya ve klasörleri bir filtre kümesiyle karşılaştırır.

ArtıkBulucu veya Uygulama Temizleyici gibi diğer araçların aksine yüklü uygulamalardan haberi yoktur. Yalnızca dosya yolları ve öznitelikleri üzerinde çalışır. Bu yüzden Sistem Temizleyicisi'ne yalnızca yol ve bölüm hariç tutmaları uygulanabilir.

Bulunan her dosya etkin filtrelerden geçirilir ve eşleşen ilk filtreye atanır.

## Ayarlar
{: #settings }

### Genel filtreler
{: #general-filters }

Genel filtreler, cihazın herhangi bir yerinde bulunabilecek belirli dosya türlerini **erişilebilen tüm depolama alanlarında** arar.

| Filtre | Açıklama |
|--------|-------------|
| **Boş klasörler** | Cihazın her yerindeki boş klasörler. İç içe klasörler birden fazla tarama gerektirebilir. |
| **Ekran görüntüleri** | Birden fazla uygulama ve konumdaki, yapılandırılabilir bir yaştan daha eski ekran görüntüleri. |
| **Çöpe atılan dosyalar** | Çeşitli uygulama ve konumlardaki çöp kutularında bulunan, silinmek üzere işaretlenmiş ancak henüz kalıcı olarak kaldırılmamış dosyalar. |
| **Gereksiz APK'lar** | Zaten yüklü olan uygulama sürümlerinden daha eski veya onlarla aynı olan kurulum dosyaları (APK'lar). |
| **LOST.DIR klasörleri** | Android cihazlar arasında veri aktarılırken oluşturulan geçici dosyalar. |
| **Linux dosyaları** | Depolama bir Linux bilgisayara bağlandığında oluşturulan dosya ve klasörler. |
| **Mac dosyaları** | Depolamaya Apple'ın Finder uygulamasıyla göz atıldığında oluşturulan dosya ve klasörler (ör. `.DS_Store`). |
| **Windows dosyaları** | Windows dosya gezgininin bağlı depolamada oluşturabildiği veriler (ör. `Thumbs.db`). |
| **Önizleme resimleri** | Görseller ve videolar için önbelleğe alınmış önizlemeler (ör. `.thumbnails` klasörleri). |
| **Geçici sistem dosyaları** | Sistemle ilgili geçici dosyalar; genellikle etkin işlemler için kısa ömürlü önbellekler. |
| **Analiz dosyaları** | Analiz ve hata takibiyle ilgili veriler. |
| **Reklam dosyaları** | Reklamlar için veya reklamlar tarafından oluşturulan dosyalar. |
| **Sistem günlük dosyaları** | Çeşitli konumlardaki sistem ve sistem hizmeti günlük dosyaları (ör. `*.log`). |

### Belirli filtreler
{: #specific-filters }

Belirli filtreler, cihazda belirli veri türlerinin saklandığı **sabit, bilinen yolları** hedefler.

| Filtre | Açıklama |
|--------|-------------|
| **Sistem çökmesi kalıntıları** | Yerel süreçler (ör. oyunlar) çökerse burada "tombstone" dosyaları oluşturulur. |
| **Kullanım istatistikleri** | Uygulama kullanım istatistikleriyle ilgili dosyalar. |
| **ANR hataları** | "Application Not Responding" ("Uygulama Yanıt Vermiyor") olaylarına ait izler. |
| **Yerel geçici dosyalar** | Sistemle ilgili geçici dosyalar (ör. Android Studio'dan). |
| **Sistem günlük dosyaları (datalogger)** | Sistem hizmetine özgü günlük dosyaları. |
| **İndirme önbelleği** | Çeşitli amaçlarla (ör. OTA güncellemeleri) kullanılan sistem önbellek klasörü. |
| **Günlük bırakma kutusu** | Sistemin günlük dosyalarını bıraktığı bir alan. |
| **Yükleyici önbelleği** | Sistemin paket yönetimi tarafından kullanılan, yüklü uygulamalarla ilgili önbelleğe alınmış veriler. |
| **Son görevler** | Sistemin görev değiştiricisindeki görev geçmişiyle ilgili veriler. |

### Özel filtreler
{: #custom-filters }

Uygulamanın sunduğu genel filtrelere ek olarak kendi ölçütlerinizle özel filtreler oluşturabilirsiniz. Özel filtreler **Ayarlar > Sistem Temizleyicisi > Özel filtreleri yönet** bölümünden oluşturulabilir.

> **Uyarı**: Özel filtreler riskli olabilir. Silmeden önce tarama sonuçlarınızı her zaman kontrol edin!

#### Kullanılabilir ölçütler
{: #available-criteria }

| Ölçüt | Açıklama |
|-----------|-------------|
| **Etiket** | Filtreniz için bir ad |
| **Veri alanları** | Taranacak depolama konumları (erişilebilen tüm alanlar için boş bırakın) |
| **Dosya türü kısıtlamaları** | Dosyaları, klasörleri veya sembolik bağlantıları hedefler (tümü için boş bırakın) |
| **Yol kriterleri** | Dosya yollarını eşleştirme kuralları |
| **İstisnalar** | Eşleştirmeden hariç tutulacak yollar |
| **İsim kriterleri** | Dosya adlarını eşleştirme kuralları |
| **Minimum boyut** | Yalnızca bundan büyük dosyalarla eşleşir |
| **Maksimum boyut** | Yalnızca bundan küçük dosyalarla eşleşir |
| **Minimum yaş** | Yalnızca bundan eski dosyalarla eşleşir |
| **Maksimum yaş** | Yalnızca bundan yeni dosyalarla eşleşir |
| **Path Regex** (yol düzenli ifadesi) | İleri düzey: Yol eşleştirme için düzenli ifadeler |

#### Yol kriteri modları
{: #path-criteria-modes }

| Mod | Açıklama | Örnek |
|------|-------------|---------|
| **Yol anahtar kelime ile başlar** | Yol, anahtar kelimeyle başlar | `Downloads/temp`, `/sdcard/Downloads/temp/file.txt` ile eşleşir |
| **Yol anahtar sözcük içerir** | Yol, anahtar kelimeyi içerir | `cache`, `/sdcard/app/cache/data.bin` ile eşleşir |
| **Yol anahtar kelime ile biter** | Yol, anahtar kelimeyle biter | `backup/old`, `/sdcard/backup/old` ile eşleşir |
| **Yol anahtar sözcüğe eşittir** | Yol, anahtar kelimeyle tam olarak eşleşir | `Download/temp` yalnızca bu tam yolla eşleşir |

#### İsim kriteri modları
{: #name-criteria-modes }

| Mod | Açıklama | Örnek |
|------|-------------|---------|
| **İsim anahtar kelime ile başlar** | Dosya adı anahtar kelimeyle başlar | `temp_`, `temp_file.txt` ile eşleşir |
| **Ad anahtar sözcük içerir** | Dosya adı anahtar kelimeyi içerir | `.bak`, `document.bak.old` ile eşleşir |
| **İsim anahtar kelime ile biter** | Dosya adı anahtar kelimeyle biter | `.tmp`, `cache.tmp` ile eşleşir |
| **Ad, anahtar kelimeye eşittir** | Dosya adı tam olarak eşleşir | `thumbs.db` yalnızca `thumbs.db` ile eşleşir |

Tüm eşleştirmeler varsayılan olarak **büyük/küçük harfe duyarsızdır**.

#### Veri alanları
{: #data-areas }

Hedefleyebileceğiniz yaygın alanlar:

| Alan | Açıklama |
|------|-------------|
| **SDCARD** | Birincil depolama (dahili veya hafıza kartı) |
| **PUBLIC_MEDIA** | Medya klasörleri (DCIM, Pictures, Music vb.) |
| **PUBLIC_DATA** | Paylaşılan uygulama verileri |
| **PRIVATE_DATA** | Uygulamaya özel depolama (root gerektirir) |
| **DOWNLOAD_CACHE** | Sistem indirme önbelleği (root gerektirir) |
| **PORTABLE** | Çıkarılabilir depolama (USB/microSD) |

#### İpuçları
{: #tips }

- **Performans**: Yalnızca gerekli ölçütleri tanımlayın. Çok fazla regex kalıbı taramayı yavaşlatır.
- **Birden fazla ölçüt**: Belirtilen tüm ölçütlerin eşleşmesi gerekir (VE mantığı).
- **Uzun basma**: Ek seçenekler için girişlere uzun basın.
- **Test etme**: Silmeyi etkinleştirmeden önce tarama önizlemesini kullanın.
- **İçe/Dışa aktarma**: Filtreler dışa aktarılabilir ve diğer kullanıcılarla paylaşılabilir.

#### Kullanımdan kaldırılan seçenekler
{: #deprecated-options }

SD Maid 1'deki `rootOnly` seçeneği **artık mevcut değildir**. Bunun yerine hangi depolama konumlarının taranacağını denetlemek için **Veri alanları** seçeneğini kullanın.
