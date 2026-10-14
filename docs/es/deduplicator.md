---
title: "Eliminar archivos duplicados"
lang: es
translation_key: deduplicator
permalink: /es/deduplicator
source_hash: 08f0bb39baf9fb1e
---
# Eliminar archivos duplicados (Deduplicator)
{: #deduplicator }

La herramienta Eliminar archivos duplicados (Deduplicator) encuentra archivos duplicados en tu dispositivo: copias exactas y, de forma opcional, imágenes, videos o audios que se ven o suenan parecido. Los agrupa en conjuntos y elimina todos los archivos de cada conjunto menos uno.

Escanear es gratis. Eliminar requiere la actualización a Pro.

## Métodos de detección
{: #detection-methods }

Puedes activar cada método en `Ajustes > Eliminar archivos duplicados`.

### Suma de comprobación del contenido
{: #content-checksum }

Activado por defecto. Encuentra archivos con exactamente el mismo contenido, byte a byte, sin importar su nombre. Es el método seguro: cada archivo de un conjunto así es una copia auténtica.

### Hash perceptual
{: #perceptual-hash }

Desactivado por defecto. Encuentra imágenes que se ven parecidas, p. ej., la misma foto guardada en otro formato o tamaño, o con pequeñas ediciones. Las imágenes se comparan en escala de grises, por lo que imágenes que solo se diferencian en el color pueden coincidir.

### Huella digital de medios
{: #media-fingerprint }

Desactivado por defecto. Encuentra videos y archivos de audio que suenan parecido, y videos sin sonido que se ven parecidos. Solo se comparan archivos de una duración aproximadamente igual.

Los archivos similares no son idénticos. Las ráfagas de fotos, las versiones editadas o las copias redimensionadas pueden acabar en el mismo conjunto. **Revisa los conjuntos encontrados mediante hash perceptual o huella digital de medios antes de eliminarlos.** Cada archivo similar muestra cuánto se parece.

## Qué se escanea
{: #what-gets-scanned }

Por defecto, Eliminar archivos duplicados busca en todo el almacenamiento accesible: el almacenamiento interno, las tarjetas SD, el almacenamiento USB, `Android/data` y `Android/media`. `Android/obb` no se incluye. Puedes limitar la búsqueda en [Buscar ubicaciones](#search-locations).

En Android 13 y posteriores, el acceso a `Android/data` requiere [root](/es/setup#root-access) o [acceso ADB](/es/setup#shizuku).

Solo se tienen en cuenta los archivos que superan el [tamaño mínimo](#minimum-size). Por defecto solo se incluyen los tipos de archivo comunes; consulta [Omitir los tipos de archivos poco comunes](#skip-uncommon-file-types).

## Qué archivo se conserva
{: #which-file-is-kept }

Al eliminar un conjunto, SD Maid conserva un archivo y elimina el resto. Cuál conserva lo decide la estrategia de eliminación, en `Ajustes > Eliminar archivos duplicados > Estrategia de eliminación`.

SD Maid comprueba cada regla en orden. Decide la primera regla que da un ganador claro. Arrastra las reglas para cambiar su orden y toca una regla para cambiar su modo.

| Regla | Por defecto |
|---|---|
| Tipo de duplicado | Conservar las coincidencias exactas antes que los archivos similares |
| Ubicaciones preferidas | Conservar los archivos de las carpetas que elijas. Por defecto no hay ninguna carpeta definida. |
| Indexación de medios | Conservar los archivos que conocen tu galería y otras aplicaciones multimedia |
| Ubicación de almacenamiento | Conservar los archivos del almacenamiento interno. Las tarjetas SD y el almacenamiento USB cuentan como externos. |
| Profundidad de carpetas | Conservar los archivos de carpetas poco anidadas |
| Fecha de modificación | Conservar los archivos más antiguos |
| Tamaño del archivo | Conservar los archivos más grandes |

**Ubicaciones preferidas** ayuda a elegir qué archivo se conserva dentro de un grupo, junto con las demás reglas. No elige entre grupos. En un conjunto con copias exactas y archivos similares, Tipo de duplicado decide qué grupo se conserva: por defecto, se elimina cada archivo del grupo de similares, aunque esté en una ubicación preferida. Por ejemplo, si la foto de tu cámara y dos copias idénticas y redimensionadas de una aplicación de mensajería acaban en un mismo conjunto, la configuración por defecto conserva una de las copias de la mensajería y elimina el original de la cámara. Revisa esos conjuntos antes de eliminarlos y [excluye](/es/exclusions) las carpetas cuyos archivos no deban eliminarse nunca.

No hay forma de marcar un archivo concreto como el que se debe conservar. Para conservar otro archivo, cambia la estrategia de eliminación o selecciona tú mismo los archivos que quieres eliminar.

Cambiar la estrategia de eliminación descarta los resultados actuales. Después vuelve a escanear.

## Eliminar
{: #deleting }

### Eliminar un conjunto completo
{: #deleting-a-whole-set }

Eliminar un conjunto conserva un archivo y elimina **todos los demás archivos de ese conjunto**. En un conjunto con archivos similares, esto incluye archivos que solo se parecen al conservado, sin ser idénticos.

Cambia la lista de resultados al diseño de lista para ver un icono de eliminación en cada archivo que se borraría al eliminar el conjunto completo. El diseño de cuadrícula predeterminado no muestra estos iconos.

### Eliminar archivos individuales
{: #deleting-individual-files }

Selecciona archivos manteniéndolos pulsados y elimínalos. Debe quedar al menos un archivo por conjunto, salvo que actives [Posibilidad de "Borrar todo"](#make-delete-all-possible).

En los detalles del conjunto, la vista `Carpetas` agrupa los archivos por carpeta. Al eliminar una carpeta ahí se eliminan los duplicados de esa carpeta. Si la carpeta contiene todos los archivos de un conjunto, se deja fuera el archivo que se conservaría al eliminar el conjunto completo. Con [Posibilidad de "Borrar todo"](#make-delete-all-possible) activado, la confirmación te permite eliminarlos todos.

### Eliminar desde el panel principal
{: #deleting-from-the-dashboard }

Eliminar desde la tarjeta de Eliminar archivos duplicados del panel principal borra todos los archivos menos uno de cada conjunto, tras una confirmación. Si el botón principal del panel también ejecuta Eliminar archivos duplicados se define en [Un solo toque](#one-tap).

## Exclusiones
{: #exclusions }

Las rutas excluidas para Eliminar archivos duplicados, o de forma general, no se escanean. Excluir un conjunto de los resultados excluye todos sus archivos, incluido el que se habría conservado. Consulta [Exclusiones](/es/exclusions).

Eliminar archivos duplicados no se puede ejecutar desde el [Programador](/es/scheduler).

## Un solo toque
{: #one-tap }

`Ajustes > General > Herramientas de un solo toque` decide si el botón principal del panel, el acceso directo `Escanear + Eliminar` y el widget incluyen Eliminar archivos duplicados. Está desactivado por defecto. El acceso directo y el widget escanean y eliminan sin confirmación, usando tu estrategia de eliminación. En el panel principal, `Modo de un toque` hace lo mismo, pero si ya hay resultados pendientes, Borrar pide confirmación primero. Incluye Eliminar archivos duplicados solo si [Suma de comprobación del contenido](#content-checksum) es el único método de detección.

## Ajustes
{: #settings }

### Buscar ubicaciones
{: #search-locations }

Las carpetas en las que buscar. Por defecto, todas las áreas accesibles. Mantén pulsado para restablecerlo.

### Estrategia de eliminación
{: #deletion-strategy }

Qué archivo de un conjunto se conserva; consulta [Qué archivo se conserva](#which-file-is-kept).

### Posibilidad de "Borrar todo"
{: #make-delete-all-possible }

Muestra opciones para eliminar todos los archivos de un conjunto, de modo que no quede ninguna copia. Desactivado por defecto.

### Tamaño mínimo
{: #minimum-size }

Omite los archivos por debajo de este tamaño. Por defecto, 512 KB.

### Omitir los tipos de archivos poco comunes
{: #skip-uncommon-file-types }

Incluye solo imágenes, videos, archivos de audio, documentos, archivos comprimidos y archivos APK comunes. Activado por defecto. El tipo se determina por la extensión del archivo.

### Método de detección
{: #detection-method }

Activa [Suma de comprobación del contenido](#content-checksum), [Hash perceptual](#perceptual-hash) y [Huella digital de medios](#media-fingerprint).
