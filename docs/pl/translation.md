---
title: "Tłumaczenia"
lang: pl
translation_key: translation
permalink: /pl/translation
source_hash: 2d81db0b448d96f7
---
# Tłumaczenia
{: #translations }

Tłumaczenia są zarządzane przez Crowdin:
<https://crowdin.com/project/sdmaid-se>

Tłumaczenia są regularnie pobierane przed publikacją nowych aktualizacji aplikacji. Ukończone ciągi w Crowdin zostaną automatycznie uwzględnione w nadchodzących wydaniach.

## Pytania i problemy
{: #questions--issues }

Jeśli masz pytania lub znajdziesz problemy, skomentuj bezpośrednio dany ciąg w Crowdin.  
Komentarze do ciągów dają kontekst i ułatwiają szybkie rozwiązywanie problemów z tłumaczeniem.

Zgłoszeń (issues) na GitHubie używaj tylko w przypadku ogólnych problemów z tłumaczeniem, a nie dla pojedynczych ciągów.

## Częste pułapki
{: #common-pitfalls }

**Symbole zastępcze muszą pozostać bez zmian**

Nie tłumacz symboli zastępczych ani nie zmieniaj ich kolejności.

Przykłady:
- `%s`, `%d`, `%1$s`
- `{0}`, `{name}`
- `$value`

Poprawnie:
`Delete %s files` → `Supprimer %s fichiers`

Błędnie:
`Supprimer %d fichiers`

**Sekwencje ucieczki**

Zachowuj sekwencje ucieczki dokładnie w takiej postaci, w jakiej są.

Przykłady:
- `\n` = nowy wiersz
- `\t` = tabulator
- `\"` = cudzysłów wewnątrz ciągu
- `\\` = ukośnik wsteczny

Nie usuwaj ich ani nie podwajaj.

**Nowe wiersze**

Jeśli tekst źródłowy zawiera podziały wierszy, zachowaj je.

Przykład:
`Line1\nLine2`

Nie łącz ich w jedno zdanie, chyba że wytyczne dla danego języka wyraźnie na to pozwalają.

**Formatowanie i znaczniki**

Nie psuj:
- znaczników HTML (`<b>`, `<i>`, `<br>`)
- znaczników Markdown
- formatowania Androida (`%1$s`, `&lt;`, `&amp;`)

## Języki
{: #languages }

Niektóre języki wymagają określonego słownictwa, formatowania lub tonu. Przed tłumaczeniem sprawdź wytyczne dla danego języka w tej wiki, aby zachować spójność terminologii i stylu. Jeśli wytyczne jeszcze nie istnieją, współtwórcy mogą je zaproponować.

### Francuski
{: #french }

Koordynacja: 

* Alain-Olivier ([Github](https://github.com/AO-LocLab), [Crowdin](https://crowdin.com/profile/yahoe-001))

Wytyczne:
* [Referencyjny PDF](https://github.com/user-attachments/files/25190189/Directives.generales.de.traduction.en.Francais.pdf)
