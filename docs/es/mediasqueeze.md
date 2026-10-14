---
title: "Compresión y transcodificación de imágenes/videos"
lang: es
translation_key: mediasqueeze
permalink: /es/mediasqueeze
source_hash: 1d878fd5a77634c5
---
# Compresión y transcodificación de imágenes/videos (Media Squeeze)
{: #media-squeeze }

Compresión y transcodificación de imágenes/videos (Media Squeeze) comprime fotos y videos para que ocupen menos espacio. Cambias algo de calidad por el espacio que recuperas.

**La compresión reduce la calidad de forma permanente y no se puede deshacer.** El archivo original se reemplaza y no se guarda ninguna copia. Revisa tus ajustes y la vista previa antes de empezar.

Escanear es gratis. Comprimir requiere la actualización a Pro.

## Cómo funciona
{: #how-it-works }

1. Elige las carpetas en las que buscar. No hay ninguna ubicación predeterminada; elige, p. ej., la carpeta de tu cámara.
2. Define la calidad de compresión y la antigüedad mínima.
3. Escanea. Cada resultado muestra su tamaño y un ahorro estimado.
4. Toca un elemento, selecciona varios manteniéndolos pulsados o usa `Comprimir todo`. Antes, una confirmación muestra el tamaño total y el ahorro estimado.

Si la selección contiene solo fotos o solo videos, `Ver comparación` en la confirmación muestra el original y la versión comprimida uno al lado del otro. En los videos, la comparación es un único fotograma y solo es aproximada.

### Qué le ocurre a un archivo
{: #what-happens-to-a-file }

* Se vuelve a codificar en su propio formato: un JPEG sigue siendo JPEG, un WebP sigue siendo WebP y un MP4 sigue siendo MP4.
* La resolución de las imágenes no cambia, salvo en las [fotos muy grandes](#compress-very-large-photos).
* En los videos, solo se vuelve a codificar la pista de video con una tasa de bits menor. La pista de audio se copia sin cambios.
* Se conservan la fecha, la ubicación, la cámara y demás metadatos EXIF de las fotos. Si SD Maid no puede leer o conservar los metadatos EXIF de una foto, deja la foto sin cambios.
* Los metadatos XMP no se conservan, p. ej., la información de fotos esféricas y panorámicas o las valoraciones de los editores de fotos.
* Se conserva la fecha de modificación del archivo, para que las galerías lo sigan ordenando en el mismo lugar.
* Si la versión comprimida no es más pequeña, el original no se modifica.

El ahorro estimado es una aproximación basada en el tipo de archivo y la calidad, no una medición. El ahorro real puede variar.

### Ubicaciones
{: #locations }

Compresión y transcodificación de imágenes/videos necesita acceso directo a los archivos y acceso de escritura a sus carpetas. No usa root ni ADB. Algunas ubicaciones elegidas con el selector de carpetas de Android, p. ej., las unidades USB, no se pueden usar y se quitan de la selección.

## Archivos comprimidos anteriormente
{: #previously-compressed-files }

SD Maid recuerda qué archivos ya comprimió, y cuáles no se hicieron más pequeños, y los omite en escaneos posteriores. Reconoce un archivo por su contenido, así que esto también funciona después de renombrar o mover el archivo. Volver a comprimir un archivo cuesta más calidad y ahorra poco.

El historial se guarda en tu dispositivo y forma parte de la copia de seguridad de los ajustes de SD Maid. Al borrarlo, los archivos comprimidos anteriormente vuelven a ser candidatos, salvo que lleven un marcador EXIF y [Escribir marcador EXIF](#write-exif-marker) esté activado. Borrarlo no modifica ningún archivo.

## Fotos que se omiten por defecto
{: #photos-that-are-skipped-by-default }

Algunas fotos contienen datos que la compresión no puede conservar. SD Maid las omite si detecta esos datos, salvo que actives el ajuste correspondiente. La detección no es completa: las fotos en formatos que no reconoce se comprimen y pierden esos datos.

* **Fotos HDR y de profundidad**: las fotos HDR pasan a ser fotos normales y las fotos de retrato pierden los datos en los que se basa el desenfoque del fondo.
* **Motion Photos**: se conserva la imagen fija y se elimina el clip de video.
* **Fotos muy grandes** (8192 píxeles o más de ancho o de alto): su ancho y su alto se reducen a la mitad, y otra vez a la mitad si hace falta, hasta que ambos queden por debajo de 8192 píxeles. Una panorámica de 20.000 píxeles de ancho acaba teniendo 5.000 píxeles de ancho.

Los archivos HEIC que contienen varias imágenes se omiten siempre.

El resumen del escaneo cuenta las fotos omitidas por tener datos HDR o de profundidad, las Motion Photos y las fotos muy grandes, así como los archivos que no eran accesibles. No se cuentan los archivos omitidos por haberse comprimido antes, ni los archivos HEIC con varias imágenes, salvo que también tengan datos HDR o de profundidad.

## Ajustes
{: #settings }

La calidad de compresión, las ubicaciones de búsqueda y la antigüedad mínima se definen en la pantalla de Compresión y transcodificación de imágenes/videos antes de escanear. Todo lo demás está en `Ajustes > Compresión y transcodificación de imágenes/videos`.

### Calidad de compresión
{: #compression-quality }

Por defecto, 80. Una calidad menor da archivos más pequeños, pero con menos calidad visual. Por debajo de 40, las imágenes pueden quedar inservibles. A partir de 95 hay poco que ahorrar.

### Antigüedad
{: #minimum-age }

Incluye solo archivos más antiguos que esto. Por defecto, 90 días, según la fecha de modificación del archivo.

### Tamaño mínimo del archivo
{: #minimum-file-size }

Omite los archivos más pequeños que esto. Por defecto, 512 KB.

### JPEG
{: #jpeg }

Incluye imágenes JPEG (`.jpg`, `.jpeg`). Activado por defecto.

### WebP
{: #webp }

Incluye imágenes WebP (`.webp`). Activado por defecto. Los archivos WebP animados no se detectan y perderían su animación.

### HEIC/HEIF
{: #heicheif }

Incluye imágenes HEIC/HEIF (`.heic`, `.heif`). Desactivado por defecto. Requiere Android 9 o posterior. En HEIC no se pueden conservar los mapas de ganancia HDR, los datos de profundidad ni las partes de Live/Motion Photo.

### MP4 Video
{: #mp4-video }

Incluye videos MP4 (`.mp4`). Desactivado por defecto. Para comprimir un video se necesita un espacio libre de aproximadamente el 110 % de su tamaño en el mismo almacenamiento.

### Comprimir fotos HDR & de profundidad
{: #compress-hdr--depth-photos }

Consulta [Fotos que se omiten por defecto](#photos-that-are-skipped-by-default). Desactivado por defecto.

### Comprimir Motion Photos
{: #compress-motion-photos }

Conserva la imagen fija y elimina el clip de video. Desactivado por defecto.

### Comprimir fotos muy grandes
{: #compress-very-large-photos }

Incluye las fotos de 8192 píxeles o más de ancho o de alto y reduce su resolución; consulta [Fotos que se omiten por defecto](#photos-that-are-skipped-by-default). Desactivado por defecto.

### Omitir previamente comprimidas
{: #skip-previously-compressed }

Omite los archivos que SD Maid ya comprimió. Activado por defecto.

### Escribir marcador EXIF
{: #write-exif-marker }

Añade un marcador al comentario de usuario EXIF de la foto, para que SD Maid la reconozca como comprimida incluso después de borrar el historial de compresión. SD Maid solo busca el marcador mientras este ajuste está activado. Desactivado por defecto. Solo se aplica a JPEG y WebP.

### Historial de compresión
{: #compression-history }

Muestra cuántos archivos hay en el historial. Tócalo para borrar el historial.

## Exclusiones
{: #exclusions }

Compresión y transcodificación de imágenes/videos omite las rutas que están [excluidas](/es/exclusions) de forma general. También puedes excluir resultados desde la lista de resultados, lo que solo afecta a esta herramienta.

Compresión y transcodificación de imágenes/videos no se puede ejecutar desde el [Programador](/es/scheduler).
