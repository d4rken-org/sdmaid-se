---
title: "Übersetzungen"
lang: de
translation_key: translation
permalink: /de/translation
source_hash: 2d81db0b448d96f7
---
# Übersetzungen
{: #translations }

Die Übersetzungen werden über Crowdin verwaltet:
<https://crowdin.com/project/sdmaid-se>

Übersetzungen werden regelmäßig vor der Veröffentlichung neuer App-Updates abgerufen. Fertige Strings auf Crowdin werden automatisch in kommende Versionen übernommen.

## Fragen & Probleme
{: #questions--issues }

Wenn du Fragen hast oder Probleme findest, kommentiere bitte direkt den betroffenen String auf Crowdin.  
Kommentare an Strings liefern Kontext und machen es leichter, Übersetzungsprobleme schnell zu lösen.

Nutze GitHub-Issues nur für globale Übersetzungsprobleme, nicht für einzelne Strings.

## Häufige Stolperfallen
{: #common-pitfalls }

**Platzhalter müssen unverändert bleiben**

Platzhalter nicht übersetzen und nicht umstellen.

Beispiele:
- `%s`, `%d`, `%1$s`
- `{0}`, `{name}`
- `$value`

Richtig:
`Delete %s files` → `Supprimer %s fichiers`

Falsch:
`Supprimer %d fichiers`

**Escaping**

Behalte Escape-Sequenzen genau so bei, wie sie sind.

Beispiele:
- `\n` = Zeilenumbruch
- `\t` = Tabulator
- `\"` = Anführungszeichen innerhalb eines Strings
- `\\` = Backslash

Entferne oder verdopple sie nicht.

**Zeilenumbrüche**

Wenn der Ausgangstext Zeilenumbrüche enthält, behalte sie bei.

Beispiel:
`Line1\nLine2`

Füge sie nicht zu einem Satz zusammen, es sei denn, die Sprachrichtlinie verlangt das ausdrücklich.

**Formatierung & Markup**

Nicht beschädigen:
- HTML-Tags (`<b>`, `<i>`, `<br>`)
- Markdown-Markierungen
- Android-Formatierung (`%1$s`, `&lt;`, `&amp;`)

## Sprachen
{: #languages }

Manche Sprachen erfordern eine bestimmte Wortwahl, Formatierung oder Tonalität. Lies vor dem Übersetzen die sprachspezifischen Richtlinien in diesem Wiki, damit Terminologie und Stil einheitlich bleiben. Wenn es noch keine Richtlinie gibt, können Mitwirkende eine vorschlagen.

### Französisch
{: #french }

Koordination: 

* Alain-Olivier ([Github](https://github.com/AO-LocLab), [Crowdin](https://crowdin.com/profile/yahoe-001))

Richtlinien:
* [Referenz-PDF](https://github.com/user-attachments/files/25190189/Directives.generales.de.traduction.en.Francais.pdf)
