---
title: "Traduzioni"
lang: it
translation_key: translation
permalink: /it/translation
source_hash: 2d81db0b448d96f7
---
# Traduzioni
{: #translations }

Le traduzioni sono gestite tramite Crowdin:
<https://crowdin.com/project/sdmaid-se>

Le traduzioni vengono scaricate regolarmente prima della pubblicazione di nuovi aggiornamenti dell'app. Le stringhe completate su Crowdin verranno incluse automaticamente nelle versioni successive.

## Domande e problemi
{: #questions--issues }

Se hai domande o trovi problemi, commenta direttamente la stringa interessata su Crowdin.  
I commenti alle stringhe forniscono contesto e rendono più facile risolvere rapidamente i problemi di traduzione.

Usa le issue su GitHub solo per problemi di traduzione generali, non per singole stringhe.

## Errori comuni
{: #common-pitfalls }

**I segnaposto devono restare invariati**

Non tradurre né riordinare i segnaposto.

Esempi:
- `%s`, `%d`, `%1$s`
- `{0}`, `{name}`
- `$value`

Corretto:
`Delete %s files` → `Supprimer %s fichiers`

Sbagliato:
`Supprimer %d fichiers`

**Sequenze di escape**

Mantieni le sequenze di escape esattamente come sono.

Esempi:
- `\n` = a capo
- `\t` = tabulazione
- `\"` = virgolette all'interno della stringa
- `\\` = barra rovesciata

Non rimuoverle e non raddoppiarle.

**Ritorni a capo**

Se il testo di origine contiene interruzioni di riga, mantienile.

Esempio:
`Line1\nLine2`

Non unirle in un'unica frase, a meno che le linee guida della lingua non lo dicano esplicitamente.

**Formattazione e markup**

Non danneggiare:
- tag HTML (`<b>`, `<i>`, `<br>`)
- marcatori Markdown
- formattazione Android (`%1$s`, `&lt;`, `&amp;`)

## Lingue
{: #languages }

Alcune lingue richiedono scelte specifiche di terminologia, formattazione o tono. Prima di tradurre, consulta le linee guida specifiche per lingua in questo wiki per mantenere coerenti terminologia e stile. Se non esistono ancora linee guida, i collaboratori possono proporne una.

### Francese
{: #french }

Coordinamento: 

* Alain-Olivier ([Github](https://github.com/AO-LocLab), [Crowdin](https://crowdin.com/profile/yahoe-001))

Linee guida:
* [PDF di riferimento](https://github.com/user-attachments/files/25190189/Directives.generales.de.traduction.en.Francais.pdf)
