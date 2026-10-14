---
title: "Exclusiones"
lang: es
translation_key: exclusions
permalink: /es/exclusions
source_hash: d5c3aea2efa43796
---
# Exclusiones
{: #exclusions }

La función de exclusiones de SD Maid 2/SE permite definir carpetas o archivos concretos que la aplicación debe ignorar durante los escaneos del sistema y las tareas de mantenimiento. Es útil si hay datos que quieres mantener a salvo de los procesos de la aplicación. Al definir exclusiones, SD Maid 2/SE omitirá esas carpetas o archivos en tareas como el Buscador de restos (CorpseFinder), el Limpiador de aplicaciones (AppCleaner) y otras funciones de gestión del sistema.

La forma más sencilla de añadir una exclusión es hacerlo directamente desde los resultados. Puedes usar el botón "Excluir" en elementos individuales, o mantener pulsadas o seleccionar varias entradas y elegir la opción "Excluir". Además, las exclusiones se pueden añadir manualmente desde los ajustes de la aplicación, indicando rutas o patrones con comodines para tener más control.

Las exclusiones se pueden exportar e importar desde el menú. La importación de exclusiones solo funciona entre versiones parecidas de SD Maid. Si hay demasiadas actualizaciones entre la versión de importación y la de exportación, puede producirse un error. En ese caso, puedes intentar importarlas en una versión anterior y después volver a exportar las exclusiones.

<img src="/assets/images/exclusions/overview-1.webp" width="150">
<img src="/assets/images/exclusions/overview-2.webp" width="150">
<img src="/assets/images/exclusions/overview-3.webp" width="150">
<img src="/assets/images/exclusions/overview-4.webp" width="150">

## Tipos
{: #types }

SD Maid admite 3 tipos distintos de exclusiones:

### Exclusiones de aplicaciones
{: #app-exclusions }

Las exclusiones de aplicaciones se basan en el nombre de paquete de cada aplicación. Se pueden crear desde la herramienta Control de las aplicaciones (AppControl). Solo tienes que elegir una aplicación de la lista y usar la acción `Omitir la aplicación` correspondiente.

### Exclusiones de rutas
{: #path-exclusions }

Las exclusiones de rutas se pueden crear seleccionando (manteniendo pulsadas) rutas en la herramienta Analizador del Almacenamiento (StorageAnalyzer).

### Exclusiones de segmentos
{: #segment-exclusions }
Las exclusiones de segmentos son un tipo de exclusión de ruta que no requiere una coincidencia exacta. Un segmento es una parte de una ruta, como una carpeta. La ruta `/sdcard/DCIM/Camera` consta de 3 segmentos. Podrías excluir todas las fotos de tu cámara, tanto en el almacenamiento interno (`/storage/emulated/0/DCIM/Camera`) como en el externo (`/sdcard/DCIM/Camera`), creando una exclusión de segmentos para `DCIM/Camera`.

Si marcas la opción `Permitir coincidencias parciales`, podrías crear una exclusión para `DCIM/Cam` que coincida tanto con la carpeta `Cam` como con la carpeta `Camera`.

## Herramientas
{: #tools }

Cada herramienta de SD Maid tiene una visión distinta de los datos. No todas las exclusiones afectan a todas las herramientas por igual.

### Buscador de restos
{: #corpsefinder }

El Buscador de restos (CorpseFinder) sabe qué es una "aplicación" y qué archivos pertenecen a ella. Respeta todos los tipos de exclusión. Crear una exclusión de aplicación significa que se excluirán todos los archivos que el Buscador de restos encuentre y que considere que pertenecen a la aplicación excluida.

Tipos de exclusión admitidos: `APP`, `PATH`, `SEGMENTS`

### Limpiador del sistema
{: #systemcleaner }

El Limpiador del sistema (SystemCleaner) solo trabaja con rutas. No sabe qué es una "aplicación" ni qué archivos pertenecen a ella.

Tipos de exclusión admitidos: `PATH`, `SEGMENTS`

### Limpiador de aplicaciones
{: #appcleaner }

El Limpiador de aplicaciones (AppCleaner) respeta todos los tipos de exclusión. Puedes usar exclusiones para acelerar los escaneos si hay aplicaciones con muchos archivos que escanear.

Tipos de exclusión admitidos: `APP`, `PATH`, `SEGMENTS`

### Eliminar archivos duplicados
{: #deduplicator }

Eliminar archivos duplicados (Deduplicator) no sabe qué son las "aplicaciones" y solo trabaja con datos de archivos.

Tipos de exclusión admitidos: `PATH`, `SEGMENTS`

### Compresión y transcodificación de imágenes/videos
{: #media-squeeze }

Compresión y transcodificación de imágenes/videos (Media Squeeze) solo trabaja con datos de archivos. Los archivos excluidos no se comprimen.

Tipos de exclusión admitidos: `PATH`, `SEGMENTS`

### Swiper
{: #swiper }

Swiper solo trabaja con datos de archivos. Los archivos excluidos no se incluyen en sesiones nuevas. Mantener pulsado `Omitir` mientras deslizas excluye el archivo actual.

Tipos de exclusión admitidos: `PATH`, `SEGMENTS`

### Analizador del Almacenamiento
{: #storageanalyzer }

El Analizador del Almacenamiento (StorageAnalyzer) no admite exclusiones, ya que necesita escanear todo el contenido para calcular correctamente los tamaños totales.
