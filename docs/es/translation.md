---
title: "Traducciones"
lang: es
translation_key: translation
permalink: /es/translation
source_hash: 2d81db0b448d96f7
---
# Traducciones
{: #translations }

Las traducciones se gestionan a través de Crowdin:
<https://crowdin.com/project/sdmaid-se>

Las traducciones se descargan con regularidad antes de publicar nuevas actualizaciones de la aplicación. Las cadenas completadas en Crowdin se incluirán automáticamente en las próximas versiones.

## Preguntas y problemas
{: #questions--issues }

Si tienes preguntas o encuentras problemas, comenta directamente en la cadena afectada en Crowdin.  
Los comentarios en las cadenas aportan contexto y facilitan resolver rápidamente los problemas de traducción.

Usa los issues de GitHub solo para problemas generales de traducción, no para cadenas individuales.

## Errores frecuentes
{: #common-pitfalls }

**Los marcadores de posición no deben cambiar**

No traduzcas ni reordenes los marcadores de posición.

Ejemplos:
- `%s`, `%d`, `%1$s`
- `{0}`, `{name}`
- `$value`

Correcto:
`Delete %s files` → `Supprimer %s fichiers`

Incorrecto:
`Supprimer %d fichiers`

**Secuencias de escape**

Mantén las secuencias de escape exactamente como están.

Ejemplos:
- `\n` = salto de línea
- `\t` = tabulación
- `\"` = comillas dentro de la cadena
- `\\` = barra invertida

No las elimines ni las dupliques.

**Saltos de línea**

Si el texto original contiene saltos de línea, mantenlos.

Ejemplo:
`Line1\nLine2`

No los unas en una sola frase, salvo que la guía del idioma lo indique expresamente.

**Formato y marcado**

No rompas:
- Etiquetas HTML (`<b>`, `<i>`, `<br>`)
- Marcadores de Markdown
- Formato de Android (`%1$s`, `&lt;`, `&amp;`)

## Idiomas
{: #languages }

Algunos idiomas requieren decisiones concretas de vocabulario, formato o tono. Consulta las guías específicas de cada idioma en esta wiki antes de traducir, para mantener la coherencia de la terminología y el estilo. Si todavía no existe una guía, los colaboradores pueden proponer una.

### Francés
{: #french }

Coordinación: 

* Alain-Olivier ([Github](https://github.com/AO-LocLab), [Crowdin](https://crowdin.com/profile/yahoe-001))

Guías:
* [PDF de referencia](https://github.com/user-attachments/files/25190189/Directives.generales.de.traduction.en.Francais.pdf)
