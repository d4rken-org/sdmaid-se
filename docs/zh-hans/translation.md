---
title: "翻译"
lang: zh-Hans
translation_key: translation
permalink: /zh-hans/translation
source_hash: 2d81db0b448d96f7
---
# 翻译
{: #translations }

翻译通过 Crowdin 进行管理：
<https://crowdin.com/project/sdmaid-se>

在发布新的应用更新之前，会定期拉取翻译。在 Crowdin 上完成的字符串会自动包含在后续版本中。

## 疑问与问题
{: #questions--issues }

如果您有疑问或发现问题，请直接在 Crowdin 中对相关字符串发表评论。  
字符串评论可以提供上下文，便于快速解决翻译问题。

GitHub issue 仅用于反馈全局性的翻译问题，不要用于单个字符串。

## 常见误区
{: #common-pitfalls }

**占位符必须保持不变**

不要翻译占位符，也不要调整它们的顺序。

示例：
- `%s`、`%d`、`%1$s`
- `{0}`、`{name}`
- `$value`

正确：
`Delete %s files` → `Supprimer %s fichiers`

错误：
`Supprimer %d fichiers`

**转义**

转义序列必须原样保留。

示例：
- `\n` = 换行
- `\t` = 制表符
- `\"` = 字符串内的引号
- `\\` = 反斜杠

不要删除或重复它们。

**换行**

如果原文包含换行，请保留。

示例：
`Line1\nLine2`

除非该语言的翻译准则明确要求，否则不要合并成一句。

**格式与标记**

不要破坏：
- HTML 标签（`<b>`、`<i>`、`<br>`）
- Markdown 标记
- Android 格式（`%1$s`、`&lt;`、`&amp;`）

## 语言
{: #languages }

某些语言需要特定的措辞、格式或语气。翻译前请查看本 wiki 中针对该语言的翻译准则，以保持术语和风格一致。如果还没有相应准则，贡献者可以提议制定一份。

### 法语
{: #french }

协调人：

* Alain-Olivier（[Github](https://github.com/AO-LocLab)、[Crowdin](https://crowdin.com/profile/yahoe-001)）

翻译准则：
* [参考 PDF](https://github.com/user-attachments/files/25190189/Directives.generales.de.traduction.en.Francais.pdf)
