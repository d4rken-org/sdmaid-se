---
title: "Translations"
lang: en
translation_key: translation
permalink: /translation
---
# Translations
{: #translations }

Translations are managed via Crowdin:
<https://crowdin.com/project/sdmaid-se>

Translations are pulled regularly before publishing new app updates. Completed strings on Crowdin will automatically be included in upcoming releases.

## Questions & Issues
{: #questions--issues }

If you have questions or find problems, please comment directly on the affected string in Crowdin.  
String comments give context and make it easier to resolve translation issues quickly.

Use GitHub issues only for global translation problems, not for individual strings.

## Common Pitfalls
{: #common-pitfalls }

**Placeholders must stay unchanged**

Do not translate or reorder placeholders.

Examples:
- `%s`, `%d`, `%1$s`
- `{0}`, `{name}`
- `$value`

Correct:
`Delete %s files` → `Supprimer %s fichiers`

Wrong:
`Supprimer %d fichiers`

**Escaping**

Keep escape sequences exactly as they are.

Examples:
- `\n` = newline
- `\t` = tab
- `\"` = quote inside string
- `\\` = backslash

Do not remove or double them.

**New lines**

If the source contains line breaks, keep them.

Example:
`Line1\nLine2`

Do not merge into one sentence unless the language guideline explicitly says so.

**Formatting & markup**

Do not break:
- HTML tags (`<b>`, `<i>`, `<br>`)
- Markdown markers
- Android formatting (`%1$s`, `&lt;`, `&amp;`)

## Languages
{: #languages }

Some languages require specific wording, formatting, or tone choices. Check the language-specific guidelines in this wiki before translating to keep terminology and style consistent. If no guideline exists yet, contributors can propose one.

### French
{: #french }

Coordination: 

* Alain-Olivier ([Github](https://github.com/AO-LocLab), [Crowdin](https://crowdin.com/profile/yahoe-001))

Guidelines:
* [Reference PDF](https://github.com/user-attachments/files/25190189/Directives.generales.de.traduction.en.Francais.pdf)
