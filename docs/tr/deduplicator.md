---
title: "Tekilleyici"
lang: tr
translation_key: deduplicator
permalink: /tr/deduplicator
source_hash: 08f0bb39baf9fb1e
---
# Tekilleyici (Deduplicator)
{: #deduplicator }

Tekilleyici (Deduplicator), cihazınızdaki yinelenen dosyaları bulur: birebir kopyaları ve isteğe bağlı olarak birbirine benzeyen ya da benzer ses veren görselleri, videoları veya ses dosyalarını. Bunları kümeler halinde gruplar ve her kümede bir dosya hariç tümünü siler.

Tarama ücretsizdir. Silme işlemi yükseltme gerektirir.

## Algılama yöntemleri
{: #detection-methods }

Her yöntemi `Ayarlar > Tekilleyici` altında etkinleştirebilirsiniz.

### İçerik sağlama toplamı
{: #content-checksum }

Varsayılan olarak açıktır. Adlarından bağımsız olarak, bayt bayt tamamen aynı içeriğe sahip dosyaları bulur. Bu güvenli yöntemdir: böyle bir kümedeki her dosya gerçek bir kopyadır.

### Algısal hash
{: #perceptual-hash }

Varsayılan olarak kapalıdır. Birbirine benzeyen görselleri bulur, ör. farklı bir biçimde veya boyutta ya da küçük düzenlemelerle kaydedilmiş aynı fotoğraf. Görseller gri tonlamalı olarak karşılaştırılır, bu nedenle yalnızca renk bakımından farklı olan görseller eşleşebilir.

### Medya parmak izi
{: #media-fingerprint }

Varsayılan olarak kapalıdır. Benzer ses veren videoları ve ses dosyalarını, ayrıca birbirine benzeyen sessiz videoları bulur. Yalnızca yaklaşık aynı uzunluktaki dosyalar karşılaştırılır.

Benzer dosyalar aynı değildir. Seri çekimler, düzenlenmiş sürümler veya yeniden boyutlandırılmış kopyalar aynı kümeye düşebilir. **Algısal hash veya medya parmak izi ile bulunan kümeleri silmeden önce kontrol edin.** Her benzer dosya, ne kadar yakın eşleştiğini gösterir.

## Neler taranır
{: #what-gets-scanned }

Tekilleyici varsayılan olarak erişilebilen tüm depolama alanlarını tarar: dahili depolama, hafıza kartları, USB depolama, `Android/data` ve `Android/media`. `Android/obb` dahil değildir. Aramayı [Arama konumları](#search-locations) altında sınırlayabilirsiniz.

Android 13 ve sonrasında `Android/data` erişimi [root](/tr/setup#root-access) veya [ADB erişimi](/tr/setup#shizuku) gerektirir.

Yalnızca [minimum boyutun](#minimum-size) üzerindeki dosyalar dikkate alınır. Varsayılan olarak yalnızca yaygın dosya türleri dahil edilir, bkz. [Tanıdık olmayan dosya türlerini geç](#skip-uncommon-file-types).

## Hangi dosya tutulur
{: #which-file-is-kept }

Bir küme silinirken SD Maid bir dosyayı tutar ve gerisini siler. Hangisinin tutulacağına `Ayarlar > Tekilleyici > Silme stratejisi` altındaki silme stratejisi karar verir.

SD Maid her kuralı sırayla kontrol eder. Açık bir kazanan çıkaran ilk kural karar verir. Kuralları yeniden sıralamak için sürükleyin, modunu değiştirmek için bir kurala dokunun.

| Kural | Varsayılan |
|---|---|
| Yineleme türü | Benzer dosyalar yerine tam eşleşmeleri tut |
| Tercih edilen konumlar | Seçtiğiniz klasörlerdeki dosyaları tut. Varsayılan olarak hiçbir klasör ayarlanmamıştır. |
| Medya dizinleme | Galerinizin ve diğer medya uygulamalarının bildiği dosyaları tut |
| Depolama konumu | Dahili depolamadaki dosyaları tut. Hafıza kartları ve USB depolama harici sayılır. |
| Klasör derinliği | Sığ klasörlerdeki dosyaları tut |
| Değiştirilme tarihi | Daha eski dosyaları tut |
| Dosya boyutu | Daha büyük dosyaları tut |

**Tercih edilen konumlar**, diğer kurallarla birlikte bir grup içinde hangi dosyanın tutulacağını seçmeye yardımcı olur. Gruplar arasında seçim yapmaz. Hem birebir kopyalar hem de benzer dosyalar içeren bir kümede hangi grubun tutulacağına Yineleme türü karar verir: varsayılan olarak benzer gruptaki her dosya, tercih edilen bir konumda olsa bile silinir. Örneğin kamera fotoğrafınız ve bir mesajlaşma uygulamasından gelen iki özdeş, yeniden boyutlandırılmış kopya aynı kümeye düşerse varsayılan ayar mesajlaşma kopyalarından birini tutar ve kameradaki orijinali siler. Bu tür kümeleri silmeden önce kontrol edin ve dosyaları asla silinmemesi gereken klasörleri [hariç tutun](/tr/exclusions).

Tek bir dosyayı tutulacak dosya olarak işaretlemenin bir yolu yoktur. Farklı bir dosyayı tutmak için silme stratejisini değiştirin veya silinecek dosyaları kendiniz seçin.

Silme stratejisini değiştirmek mevcut sonuçları geçersiz kılar. Ardından yeniden tarayın.

## Silme
{: #deleting }

### Bütün bir kümeyi silme
{: #deleting-a-whole-set }

Bir kümeyi silmek bir dosyayı tutar ve **o kümedeki diğer tüm dosyaları** siler. Benzer dosyalar içeren bir kümede buna, tutulan dosyayla aynı olmayıp yalnızca ona benzeyen dosyalar da dahildir.

Bütün kümeyi silmenin kaldıracağı her dosyada bir silme simgesi görmek için sonuç listesini liste düzenine geçirin. Varsayılan ızgara düzeni bu simgeleri göstermez.

### Tek tek dosyaları silme
{: #deleting-individual-files }

Dosyaları uzun basarak seçin ve silin. ["Hepsini sil" seçeneğini aktifleştir](#make-delete-all-possible) ayarını etkinleştirmediğiniz sürece her kümede en az bir dosyanın kalması gerekir.

Küme ayrıntılarında `Klasörler` görünümü dosyaları klasöre göre gruplar. Orada bir klasörü silmek, o klasördeki yinelenen dosyaları siler. Klasör bir kümenin tüm dosyalarını içeriyorsa, bütün kümeyi silmenin tutacağı dosya dışarıda bırakılır. ["Hepsini sil" seçeneğini aktifleştir](#make-delete-all-possible) etkinse onay penceresi bunların hepsini silmenize izin verir.

### Kontrol panelinden silme
{: #deleting-from-the-dashboard }

Tekilleyici'nin kontrol paneli kartından silmek, bir onaydan sonra her kümede bir dosya hariç tümünü siler. Kontrol panelinin ana düğmesinin Tekilleyici'yi de çalıştırıp çalıştırmayacağı [Tek-Tık](#one-tap) altında ayarlanır.

## Hariç tutmalar
{: #exclusions }

Tekilleyici için veya genel olarak hariç tutulan yollar taranmaz. Sonuçlardan bir kümeyi hariç tutmak, tutulacak olan dahil içindeki her dosyayı hariç tutar. Bkz. [Hariç tutmalar](/tr/exclusions).

Tekilleyici, [Zamanlayıcı](/tr/scheduler) tarafından çalıştırılamaz.

## Tek-Tık
{: #one-tap }

`Ayarlar > Genel > Tek-Tık Araçlar`, kontrol panelinin ana düğmesinin, `Tara + Sil` kısayolunun ve widget'ın Tekilleyici'yi içerip içermeyeceğini belirler. Varsayılan olarak kapalıdır. Kısayol ve widget, silme stratejinizi kullanarak onay istemeden tarar ve siler. Kontrol panelinde `Tek-Tık Modu` aynısını yapar, ancak bekleyen sonuçlar varsa Sil yine önce sorar. Tekilleyici'yi yalnızca tek algılama yöntemi [İçerik sağlama toplamı](#content-checksum) olduğunda dahil edin.

## Ayarlar
{: #settings }

### Arama konumları
{: #search-locations }

Aranacak klasörler. Varsayılan olarak erişilebilen tüm alanlardır. Sıfırlamak için uzun basın.

### Silme stratejisi
{: #deletion-strategy }

Bir kümede hangi dosyanın tutulacağı, bkz. [Hangi dosya tutulur](#which-file-is-kept).

### "Hepsini sil" seçeneğini aktifleştir
{: #make-delete-all-possible }

Bir kümedeki her dosyayı silme seçeneklerini gösterir, böylece hiçbir kopya kalmaz. Varsayılan olarak kapalıdır.

### Minimum boyut
{: #minimum-size }

Bu boyutun altındaki dosyaları atlar. Varsayılan 512 KB.

### Tanıdık olmayan dosya türlerini geç
{: #skip-uncommon-file-types }

Yalnızca yaygın görselleri, videoları, ses dosyalarını, belgeleri, arşivleri ve APK dosyalarını dahil eder. Varsayılan olarak açıktır. Tür, dosya uzantısına göre belirlenir.

### Algılama yöntemi
{: #detection-method }

[İçerik sağlama toplamı](#content-checksum), [Algısal hash](#perceptual-hash) ve [Medya parmak izi](#media-fingerprint) yöntemlerini etkinleştirin.
