---
title: "Terjemahan"
lang: id
translation_key: translation
permalink: /id/translation
source_hash: 2d81db0b448d96f7
---
# Terjemahan
{: #translations }

Terjemahan dikelola melalui Crowdin:
<https://crowdin.com/project/sdmaid-se>

Terjemahan diambil secara rutin sebelum pembaruan aplikasi baru diterbitkan. String yang sudah selesai di Crowdin akan otomatis disertakan dalam rilis berikutnya.

## Pertanyaan & Masalah
{: #questions--issues }

Jika Anda punya pertanyaan atau menemukan masalah, silakan berikan komentar langsung pada string yang bersangkutan di Crowdin.  
Komentar pada string memberikan konteks dan memudahkan penyelesaian masalah terjemahan dengan cepat.

Gunakan issue GitHub hanya untuk masalah terjemahan yang bersifat global, bukan untuk string satu per satu.

## Kesalahan Umum
{: #common-pitfalls }

**Placeholder harus tetap tidak berubah**

Jangan menerjemahkan atau mengubah urutan placeholder.

Contoh:
- `%s`, `%d`, `%1$s`
- `{0}`, `{name}`
- `$value`

Benar:
`Delete %s files` → `Supprimer %s fichiers`

Salah:
`Supprimer %d fichiers`

**Escaping**

Pertahankan escape sequence persis seperti aslinya.

Contoh:
- `\n` = baris baru
- `\t` = tab
- `\"` = tanda kutip di dalam string
- `\\` = garis miring terbalik

Jangan menghapus atau menggandakannya.

**Baris baru**

Jika teks sumber berisi jeda baris, pertahankan.

Contoh:
`Line1\nLine2`

Jangan menggabungkannya menjadi satu kalimat kecuali pedoman bahasa secara tegas memintanya.

**Pemformatan & markup**

Jangan merusak:
- Tag HTML (`<b>`, `<i>`, `<br>`)
- Penanda Markdown
- Pemformatan Android (`%1$s`, `&lt;`, `&amp;`)

## Bahasa
{: #languages }

Beberapa bahasa memerlukan pilihan kata, pemformatan, atau gaya bahasa tertentu. Periksa pedoman khusus bahasa di wiki ini sebelum menerjemahkan agar istilah dan gaya tetap konsisten. Jika belum ada pedoman, kontributor dapat mengusulkannya.

### Prancis
{: #french }

Koordinasi: 

* Alain-Olivier ([Github](https://github.com/AO-LocLab), [Crowdin](https://crowdin.com/profile/yahoe-001))

Pedoman:
* [PDF Referensi](https://github.com/user-attachments/files/25190189/Directives.generales.de.traduction.en.Francais.pdf)
