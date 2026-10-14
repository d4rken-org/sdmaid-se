---
title: "Traductions"
lang: fr
translation_key: translation
permalink: /fr/translation
source_hash: 2d81db0b448d96f7
---
# Traductions
{: #translations }

Les traductions sont gérées via Crowdin :
<https://crowdin.com/project/sdmaid-se>

Les traductions sont récupérées régulièrement avant la publication de nouvelles mises à jour de l’appli. Les chaînes terminées sur Crowdin sont automatiquement incluses dans les versions à venir.

## Questions et problèmes
{: #questions--issues }

Si vous avez des questions ou constatez des problèmes, commentez directement la chaîne concernée sur Crowdin.  
Les commentaires sur les chaînes apportent du contexte et permettent de résoudre plus rapidement les problèmes de traduction.

Utilisez les tickets GitHub uniquement pour les problèmes de traduction globaux, pas pour des chaînes individuelles.

## Pièges courants
{: #common-pitfalls }

**Les placeholders doivent rester inchangés**

Ne traduisez pas les placeholders et ne changez pas leur ordre.

Exemples :
- `%s`, `%d`, `%1$s`
- `{0}`, `{name}`
- `$value`

Correct :
`Delete %s files` → `Supprimer %s fichiers`

Incorrect :
`Supprimer %d fichiers`

**Échappement**

Conservez les séquences d’échappement exactement telles quelles.

Exemples :
- `\n` = saut de ligne
- `\t` = tabulation
- `\"` = guillemet à l’intérieur d’une chaîne
- `\\` = barre oblique inverse

Ne les supprimez pas et ne les doublez pas.

**Sauts de ligne**

Si le texte source contient des sauts de ligne, conservez-les.

Exemple :
`Line1\nLine2`

Ne les fusionnez pas en une seule phrase, sauf si les directives de la langue le demandent explicitement.

**Mise en forme et balisage**

N’endommagez pas :
- les balises HTML (`<b>`, `<i>`, `<br>`)
- les marqueurs Markdown
- la mise en forme Android (`%1$s`, `&lt;`, `&amp;`)

## Langues
{: #languages }

Certaines langues exigent une formulation, une mise en forme ou un ton particuliers. Consultez les directives propres à chaque langue dans ce wiki avant de traduire, afin que la terminologie et le style restent cohérents. S’il n’existe pas encore de directive, les contributeurs peuvent en proposer une.

### Français
{: #french }

Coordination : 

* Alain-Olivier ([Github](https://github.com/AO-LocLab), [Crowdin](https://crowdin.com/profile/yahoe-001))

Directives :
* [PDF de référence](https://github.com/user-attachments/files/25190189/Directives.generales.de.traduction.en.Francais.pdf)
