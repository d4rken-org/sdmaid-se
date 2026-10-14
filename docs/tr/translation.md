---
title: "Çeviriler"
lang: tr
translation_key: translation
permalink: /tr/translation
source_hash: 2d81db0b448d96f7
---
# Çeviriler
{: #translations }

Çeviriler Crowdin üzerinden yönetilir:
<https://crowdin.com/project/sdmaid-se>

Çeviriler, yeni uygulama güncellemeleri yayınlanmadan önce düzenli olarak çekilir. Crowdin'de tamamlanan metinler gelecek sürümlere otomatik olarak dahil edilir.

## Sorular ve sorunlar
{: #questions--issues }

Sorularınız varsa veya sorun bulursanız lütfen doğrudan Crowdin'deki ilgili metne yorum yazın.  
Metin yorumları bağlam sağlar ve çeviri sorunlarının hızla çözülmesini kolaylaştırır.

GitHub sorun kayıtlarını (issues) tek tek metinler için değil, yalnızca genel çeviri sorunları için kullanın.

## Sık yapılan hatalar
{: #common-pitfalls }

**Yer tutucular değiştirilmeden kalmalıdır**

Yer tutucuları çevirmeyin veya yeniden sıralamayın.

Örnekler:
- `%s`, `%d`, `%1$s`
- `{0}`, `{name}`
- `$value`

Doğru:
`Delete %s files` → `Supprimer %s fichiers`

Yanlış:
`Supprimer %d fichiers`

**Kaçış dizileri**

Kaçış dizilerini tam olarak oldukları gibi koruyun.

Örnekler:
- `\n` = yeni satır
- `\t` = sekme
- `\"` = metin içinde tırnak işareti
- `\\` = ters eğik çizgi

Bunları kaldırmayın veya çiftlemeyin.

**Yeni satırlar**

Kaynak metin satır sonları içeriyorsa bunları koruyun.

Örnek:
`Line1\nLine2`

Dil yönergesi açıkça belirtmedikçe tek bir cümlede birleştirmeyin.

**Biçimlendirme ve işaretleme**

Şunları bozmayın:
- HTML etiketleri (`<b>`, `<i>`, `<br>`)
- Markdown işaretleri
- Android biçimlendirmesi (`%1$s`, `&lt;`, `&amp;`)

## Diller
{: #languages }

Bazı diller belirli sözcük, biçimlendirme veya üslup seçimleri gerektirir. Terminolojiyi ve üslubu tutarlı tutmak için çeviriye başlamadan önce bu wiki'deki dile özgü yönergeleri kontrol edin. Henüz bir yönerge yoksa katkıda bulunanlar bir yönerge önerebilir.

### Fransızca
{: #french }

Koordinasyon: 

* Alain-Olivier ([Github](https://github.com/AO-LocLab), [Crowdin](https://crowdin.com/profile/yahoe-001))

Yönergeler:
* [Referans PDF](https://github.com/user-attachments/files/25190189/Directives.generales.de.traduction.en.Francais.pdf)
