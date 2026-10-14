---
title: "Swiper"
lang: es
translation_key: swiper
permalink: /es/swiper
source_hash: 4c7d8b7a30d3a36e
---
# Swiper
{: #swiper }

Swiper te ayuda a ordenar carpetas concretas más rápido que un explorador de archivos. Recorres los archivos de uno en uno y deslizas cada uno para conservarlo, eliminarlo u omitirlo. No se elimina nada hasta que revisas tus decisiones y confirmas.

## Iniciar una sesión
{: #starting-a-session }

1. Abre Swiper desde el panel principal y toca `Seleccionar carpetas`. Puedes elegir carpetas, archivos sueltos o ambos.
2. Opcionalmente, define un filtro por tipo de archivo y un orden en la tarjeta de la sesión.
3. Toca `Escanear`. Las carpetas se recorren incluidas todas sus subcarpetas.
4. Toca `Comenzar a deslizar`.

También puedes crear una sesión desde el [Analizador del Almacenamiento](/es/storageanalyzer) (StorageAnalyzer): selecciona ahí los elementos y elige `Crear sesión de Swiper`.

Buenos lugares para empezar son el carrete de la cámara, la carpeta de descargas y las capturas de pantalla.

### Tipos de archivo
{: #file-types }

`Mostrar solo estos tipos de archivo` limita la sesión a imágenes, videos, audio, documentos o archivos comprimidos. También puedes introducir tus propias extensiones, p. ej., `apk, apks`. Si no hay nada marcado, se muestran todos los archivos. Los archivos se identifican solo por su extensión.

### Orden
{: #sort-order }

Por defecto, los archivos se muestran del más antiguo al más reciente, según su fecha de última modificación. También puedes ordenarlos del más reciente al más antiguo, por nombre o del más grande al más pequeño.

El filtro y el orden no se pueden cambiar después de que un escaneo haya encontrado archivos. Para cambiarlos, inicia una sesión nueva.

### Escanear un almacenamiento completo
{: #scanning-a-whole-storage }

Si eliges el nivel superior de un almacenamiento, p. ej., todo tu almacenamiento interno, todos sus archivos acaban en la sesión, incluidas las fotos y el contenido multimedia de las aplicaciones. SD Maid te pide confirmación antes de escanear. Cuando más adelante elimines archivos de una sesión así, primero tendrás que marcar `Entiendo que estos archivos se eliminarán permanentemente.`

## Deslizar
{: #swiping }

| Gesto | Acción predeterminada |
|---|---|
| Deslizar a la derecha | Conservar |
| Deslizar a la izquierda | Borrar |
| Deslizar hacia arriba | Omitir |
| Deslizar hacia abajo | Deshacer la última decisión |

Los botones de la parte inferior hacen lo mismo. Con [Intercambiar direcciones de deslizamiento](#swap-swipe-directions) activado, la izquierda y la derecha se invierten.

* **Omitir** deja un archivo sin decidir. Los archivos omitidos vuelven a aparecer después de los demás.
* **Deshacer** retrocede por tus últimas decisiones, hasta 50.
* **Mantén pulsado Omitir** para excluir un archivo de futuros escaneos de Swiper. Esto crea una [exclusión](/es/exclusions) de ruta y quita el archivo de la sesión.
* Los botones de la tarjeta abren el archivo en otra aplicación o muestran una vista previa a pantalla completa.
* Toca una miniatura de la tira para saltar a ese archivo. Si vuelves a deslizarlo, se reemplaza tu decisión anterior.

Puedes salir en cualquier momento. Tus decisiones se guardan con la sesión y puedes continuar más tarde. Cuando SD Maid se reinicia, los archivos que ya no encuentra se quitan de la sesión. Hasta entonces, la sesión puede seguir mostrando archivos que otra cosa ha eliminado o movido.

## Revisar y eliminar
{: #reviewing-and-deleting }

Toca `Revisar` para ver todas las decisiones. Cuando no quedan archivos sin decidir, la revisión se abre sola.

* Cambia las decisiones archivo por archivo, o mantén pulsado para seleccionar varios archivos a la vez.
* Toca un archivo para volver a él.
* `Borrar` muestra cuántos archivos y cuánto espacio se ven afectados, y en qué carpetas, antes de que ocurra nada.

La eliminación es permanente. Los archivos no se mueven a una papelera y SD Maid no puede restaurarlos.

Tras eliminar, los archivos conservados y los eliminados salen de la sesión. Los archivos sin decidir se quedan para más tarde. Cuando no queda nada, la sesión se quita. Si algunos archivos no se pudieron eliminar, se quedan en la sesión y puedes volver a intentarlo.

Quitar una sesión solo descarta tus decisiones. No toca ningún archivo.

### Lo que conviene saber
{: #things-to-know }

* **Conservar solo se aplica a la sesión actual.** Si inicias una sesión nueva en la misma carpeta, los archivos conservados vuelven a aparecer. Para ocultar un archivo de forma permanente, exclúyelo (mantén pulsado Omitir).
* **Una sesión no incluye archivos nuevos.** Los archivos añadidos a la carpeta después del escaneo no aparecen. Inicia una sesión nueva para incluirlos.
* **Los archivos comprimidos incluyen los archivos APK.**
* **Se incluyen los archivos ocultos.**

## Versión gratuita
{: #free-version }

Sin la actualización a Pro, Swiper está limitado a 2 sesiones de 50 archivos cada una. Si hay un orden definido, obtienes los 50 primeros archivos en ese orden.

## Ajustes
{: #settings }

### Intercambiar direcciones de deslizamiento
{: #swap-swipe-directions }

Desliza a la izquierda para conservar y a la derecha para eliminar. Desactivado por defecto.

### Mostrar detalles del archivo
{: #show-file-details }

Muestra el nombre, el tamaño, la fecha y la carpeta del archivo en la pantalla de deslizamiento. Activado por defecto.

### Retroalimentación háptica
{: #haptic-feedback }

Vibra cuando conservas o eliminas un archivo. Activado por defecto.
