---
title: "Medya Sıkıştırıcı"
lang: tr
translation_key: mediasqueeze
permalink: /tr/mediasqueeze
source_hash: 1d878fd5a77634c5
---
# Medya Sıkıştırıcı (Media Squeeze)
{: #media-squeeze }

Medya Sıkıştırıcı (Media Squeeze), fotoğrafları ve videoları daha az yer kaplamaları için sıkıştırır. Geri kazandığınız alan karşılığında bir miktar kaliteden vazgeçersiniz.

**Sıkıştırma kaliteyi kalıcı olarak düşürür ve geri alınamaz.** Orijinal dosyanın yerini yenisi alır ve hiçbir kopya tutulmaz. Başlamadan önce ayarlarınızı ve önizlemeyi kontrol edin.

Tarama ücretsizdir. Sıkıştırma işlemi yükseltme gerektirir.

## Nasıl çalışır
{: #how-it-works }

1. Aranacak klasörleri seçin. Varsayılan bir konum yoktur; örneğin kamera klasörünüzü seçin.
2. Sıkıştırma kalitesini ve asgari dosya yaşını ayarlayın.
3. Tarayın. Her sonuç kendi boyutunu ve tahmini kazancı gösterir.
4. Tek bir öğeye dokunun, uzun basarak birkaç öğe seçin veya `Tümünü sıkıştır` seçeneğini kullanın. Önce bir onay penceresi toplam boyutu ve tahmini kazancı gösterir.

Seçim yalnızca fotoğraflardan veya yalnızca videolardan oluşuyorsa onay penceresindeki `Karşılaştırmayı görüntüle` seçeneği orijinali ve sıkıştırılmış sürümü yan yana gösterir. Videolarda karşılaştırma tek bir kareden oluşur ve yalnızca yaklaşıktır.

### Bir dosyaya ne olur
{: #what-happens-to-a-file }

* Kendi biçiminde yeniden kodlanır: JPEG, JPEG olarak kalır; WebP, WebP olarak kalır; MP4, MP4 olarak kalır.
* [Çok büyük fotoğraflar](#compress-very-large-photos) dışında görsel çözünürlüğü aynı kalır.
* Videolarda yalnızca video izi daha düşük bir bit hızında yeniden kodlanır. Ses izi değiştirilmeden kopyalanır.
* Fotoğrafların tarih, konum, kamera ve diğer EXIF meta verileri korunur. SD Maid bir fotoğrafın EXIF meta verilerini okuyamaz veya koruyamazsa fotoğrafı değiştirmeden bırakır.
* XMP meta verileri korunmaz, ör. fotoğraf küresi ve panorama bilgileri veya fotoğraf düzenleyicilerden gelen derecelendirmeler.
* Dosyanın değiştirilme tarihi korunur, böylece galeriler onu aynı yerde sıralamaya devam eder.
* Sıkıştırılmış sürüm daha küçük değilse orijinale dokunulmaz.

Tahmini kazanç bir ölçüm değil, dosya türüne ve kaliteye dayalı kaba bir tahmindir. Gerçek kazanç farklı olabilir.

### Konumlar
{: #locations }

Medya Sıkıştırıcı'nın dosyalara doğrudan erişime ve bulundukları klasörlere yazma erişimine ihtiyacı vardır. Root veya ADB kullanmaz. Android'in klasör seçicisi aracılığıyla seçilen bazı konumlar, ör. USB sürücüleri, kullanılamaz ve seçimden kaldırılır.

## Önceden sıkıştırılmış dosyalar
{: #previously-compressed-files }

SD Maid hangi dosyaları zaten sıkıştırdığını ve hangilerinin küçülmediğini hatırlar ve sonraki taramalarda bunları atlar. Bir dosyayı içeriğinden tanır, bu nedenle dosya yeniden adlandırıldıktan veya taşındıktan sonra da çalışır. Bir dosyayı yeniden sıkıştırmak daha fazla kaliteye mal olur ve çok az yer kazandırır.

Geçmiş cihazınızda tutulur ve SD Maid'in ayar yedeğinin bir parçasıdır. Geçmişi temizlemek, önceden sıkıştırılmış dosyaları yeniden uygun hale getirir; ancak bir EXIF işareti taşıyorlarsa ve [EXIF işareti yaz](#write-exif-marker) etkinse bu geçerli değildir. Geçmişi temizlemek hiçbir dosyayı değiştirmez.

## Varsayılan olarak atlanan fotoğraflar
{: #photos-that-are-skipped-by-default }

Bazı fotoğraflar, sıkıştırmanın koruyamadığı veriler içerir. SD Maid bu verileri algılarsa, ilgili ayarı etkinleştirmediğiniz sürece bu fotoğrafları atlar. Algılama eksiksiz değildir: tanımadığı biçimlerdeki fotoğraflar sıkıştırılır ve bu verileri kaybeder.

* **HDR ve derinlik fotoğrafları**: HDR fotoğraflar normal fotoğraflara dönüşür ve portre fotoğrafları arka plan bulanıklığının arkasındaki verileri kaybeder.
* **Motion Photos (hareketli fotoğraflar)**: hareketsiz görüntü korunur, video klip kaldırılır.
* **Çok büyük fotoğraflar** (genişliği veya yüksekliği 8192 piksel ya da daha fazla olan): her ikisi de 8192 pikselin altına inene kadar genişlikleri ve yükseklikleri yarıya, gerekirse yeniden yarıya indirilir. 20.000 piksel genişliğindeki bir panorama sonunda 5.000 piksel genişliğinde olur.

Birden fazla görüntü içeren HEIC dosyaları her zaman atlanır.

Tarama özeti, HDR veya derinlik verileri, Motion Photos ve çok büyük boyut nedeniyle atlanan fotoğrafları ve erişilemeyen dosyaları sayar. Daha önce sıkıştırıldıkları için atlanan dosyalar sayılmaz; HDR veya derinlik verileri de içermedikçe çok görüntülü HEIC dosyaları da sayılmaz.

## Ayarlar
{: #settings }

Sıkıştırma kalitesi, arama konumları ve asgari dosya yaşı, taramadan önce Medya Sıkıştırıcı ekranında ayarlanır. Diğer her şey `Ayarlar > Medya Sıkıştırıcı` altındadır.

### Sıkıştırma kalitesi
{: #compression-quality }

Varsayılan 80. Daha düşük kalite daha küçük dosyalar, ancak daha düşük görsel kalite anlamına gelir. 40'ın altında görseller kullanılamaz hale gelebilir. 95 ve üzerinde kazanılacak alan çok azdır.

### Asgari dosya yaşı
{: #minimum-age }

Yalnızca bundan daha eski dosyaları dahil eder. Varsayılan 90 gündür ve dosyanın değiştirilme tarihine dayanır.

### Minimum dosya boyutu
{: #minimum-file-size }

Bundan küçük dosyaları atlar. Varsayılan 512 KB.

### JPEG
{: #jpeg }

JPEG görsellerini (`.jpg`, `.jpeg`) dahil eder. Varsayılan olarak açıktır.

### WebP
{: #webp }

WebP görsellerini (`.webp`) dahil eder. Varsayılan olarak açıktır. Animasyonlu WebP dosyaları algılanmaz ve animasyonlarını kaybeder.

### HEIC/HEIF
{: #heicheif }

HEIC/HEIF görsellerini (`.heic`, `.heif`) dahil eder. Varsayılan olarak kapalıdır. Android 9 veya daha yenisini gerektirir. HEIC için HDR kazanç haritaları, derinlik verileri ve Live/Motion Photo bölümleri korunamaz.

### MP4 Video
{: #mp4-video }

MP4 videolarını (`.mp4`) dahil eder. Varsayılan olarak kapalıdır. Bir videoyu sıkıştırmak, aynı depolama alanında boyutunun yaklaşık %110'u kadar boş alan gerektirir.

### HDR & derinlik fotoğraflarını sıkıştır
{: #compress-hdr--depth-photos }

Bkz. [Varsayılan olarak atlanan fotoğraflar](#photos-that-are-skipped-by-default). Varsayılan olarak kapalıdır.

### Motion Photos'u Sıkıştır
{: #compress-motion-photos }

Hareketsiz görüntüyü korur ve video klibi kaldırır. Varsayılan olarak kapalıdır.

### Çok büyük fotoğrafları sıkıştır
{: #compress-very-large-photos }

Genişliği veya yüksekliği 8192 piksel ya da daha fazla olan fotoğrafları dahil eder ve çözünürlüklerini düşürür, bkz. [Varsayılan olarak atlanan fotoğraflar](#photos-that-are-skipped-by-default). Varsayılan olarak kapalıdır.

### Önceden sıkıştırılmışları atla
{: #skip-previously-compressed }

SD Maid'in zaten sıkıştırdığı dosyaları atlar. Varsayılan olarak açıktır.

### EXIF işareti yaz
{: #write-exif-marker }

Fotoğrafın EXIF kullanıcı yorumuna bir işaret ekler, böylece SD Maid sıkıştırma geçmişi temizlendikten sonra bile onu sıkıştırılmış olarak tanır. SD Maid işareti yalnızca bu ayar etkinken kontrol eder. Varsayılan olarak kapalıdır. Yalnızca JPEG ve WebP için geçerlidir.

### Sıkıştırma geçmişi
{: #compression-history }

Geçmişte kaç dosya olduğunu gösterir. Geçmişi temizlemek için dokunun.

## Hariç tutmalar
{: #exclusions }

Medya Sıkıştırıcı, genel olarak [hariç tutulan](/tr/exclusions) yolları atlar. Sonuçları sonuç listesinden de hariç tutabilirsiniz; bu yalnızca Medya Sıkıştırıcı'yı etkiler.

Medya Sıkıştırıcı, [Zamanlayıcı](/tr/scheduler) tarafından çalıştırılamaz.
