---
title: "Traduções"
lang: pt-BR
translation_key: translation
permalink: /pt-br/translation
source_hash: 2d81db0b448d96f7
---
# Traduções
{: #translations }

As traduções são gerenciadas pelo Crowdin:
<https://crowdin.com/project/sdmaid-se>

As traduções são importadas regularmente antes da publicação de novas atualizações do aplicativo. Strings concluídas no Crowdin serão incluídas automaticamente nas próximas versões.

## Perguntas e problemas
{: #questions--issues }

Se você tiver perguntas ou encontrar problemas, comente diretamente na string afetada no Crowdin.  
Os comentários nas strings dão contexto e facilitam a resolução rápida de problemas de tradução.

Use as issues do GitHub apenas para problemas gerais de tradução, não para strings individuais.

## Armadilhas comuns
{: #common-pitfalls }

**Os placeholders devem permanecer inalterados**

Não traduza nem reordene placeholders.

Exemplos:
- `%s`, `%d`, `%1$s`
- `{0}`, `{name}`
- `$value`

Correto:
`Delete %s files` → `Supprimer %s fichiers`

Errado:
`Supprimer %d fichiers`

**Sequências de escape**

Mantenha as sequências de escape exatamente como estão.

Exemplos:
- `\n` = nova linha
- `\t` = tabulação
- `\"` = aspas dentro da string
- `\\` = barra invertida

Não as remova nem as duplique.

**Quebras de linha**

Se o texto de origem tiver quebras de linha, mantenha-as.

Exemplo:
`Line1\nLine2`

Não junte tudo em uma única frase, a menos que a diretriz do idioma diga isso explicitamente.

**Formatação e marcação**

Não quebre:
- Tags HTML (`<b>`, `<i>`, `<br>`)
- Marcadores Markdown
- Formatação do Android (`%1$s`, `&lt;`, `&amp;`)

## Idiomas
{: #languages }

Alguns idiomas exigem escolhas específicas de termos, formatação ou tom. Consulte as diretrizes específicas do idioma neste wiki antes de traduzir, para manter a terminologia e o estilo consistentes. Se ainda não houver uma diretriz, os colaboradores podem propor uma.

### Francês
{: #french }

Coordenação: 

* Alain-Olivier ([Github](https://github.com/AO-LocLab), [Crowdin](https://crowdin.com/profile/yahoe-001))

Diretrizes:
* [PDF de referência](https://github.com/user-attachments/files/25190189/Directives.generales.de.traduction.en.Francais.pdf)
