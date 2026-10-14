---
title: "Analizador del Almacenamiento"
lang: es
translation_key: storageanalyzer
permalink: /es/storageanalyzer
source_hash: 2dbf8557900cc687
---
# Analizador del Almacenamiento (StorageAnalyzer)
{: #storage-analyzer }

El Analizador del Almacenamiento (StorageAnalyzer) responde a la pregunta "¿Qué ocupa tanto espacio en mi almacenamiento?"

Puede parecer un explorador de archivos, pero no lo es. Todo está ordenado por tamaño, para que puedas seguir los elementos más grandes hasta llegar a lo que realmente llena tu almacenamiento.

## Almacenamientos
{: #storages }

La primera pantalla muestra cada almacenamiento de tu dispositivo:

* **Almacenamiento principal**: el almacenamiento integrado de tu dispositivo, incluidas las aplicaciones y los datos del sistema.
* **Almacenamiento secundario**: normalmente una tarjeta SD extraíble.
* **Almacenamiento portátil**: p. ej., una memoria USB conectada.

Cada tarjeta muestra el espacio libre y el usado, y cuánto ha cambiado el almacenamiento en los últimos 7 días; consulta [Tendencia de almacenamiento](#storage-trend).

## Categorías
{: #categories }

Al abrir un almacenamiento, se escanea y el espacio usado se divide en categorías:

* **Aplicaciones**: las aplicaciones instaladas con su código, datos, cachés y contenido multimedia.
* **Archivos del usuario**: fotos, videos, descargas y cualquier otro dato que no pertenezca a una aplicación concreta.
* **Datos del sistema**: el sistema operativo y todo lo que no se ha podido asignar a otra categoría.
* **Otros usuarios**: aplicaciones y archivos de otros usuarios o perfiles de trabajo de este dispositivo.

Datos del sistema y Otros usuarios solo existen en el almacenamiento principal.

### Aplicaciones
{: #apps }

Las aplicaciones están ordenadas por tamaño y se pueden buscar por nombre. Aquí también se pueden mostrar las aplicaciones del sistema, incluidos sus datos. En las aplicaciones del sistema sin una actualización instalada, el código integrado de la aplicación cuenta como Datos del sistema. Al abrir una aplicación se ve en qué se usa su espacio:

* **Código de la aplicación**: la propia aplicación. Desinstalar o archivar la aplicación libera este espacio.
* **Datos de la aplicación**: datos, ajustes y cachés.
* **Aplicación multimedia**: archivos multimedia de esta aplicación, p. ej., fotos recibidas por WhatsApp.
* **Archivos adicionales de la aplicación**: datos que la aplicación ha guardado fuera de sus propias carpetas, p. ej., fotos o descargas en una carpeta de primer nivel.

`Mostrar los ajustes` abre la página de ajustes del sistema de la aplicación.

### Por qué las cifras no coinciden con los ajustes de Android
{: #why-the-numbers-dont-match-androids-settings }

Las categorías del analizador no coincidirán exactamente con el desglose de los ajustes de Android.

**Datos del sistema** es lo que queda del espacio usado después de contar Aplicaciones, Archivos del usuario y Otros usuarios. Todo lo que SD Maid no puede ver o asignar acaba ahí: áreas protegidas del sistema, almacenamiento de otros usuarios que no se ha podido medir, la sobrecarga del sistema de archivos y la diferencia entre el tamaño anunciado y el tamaño real del almacenamiento.

Sin los permisos [Lista de aplicaciones](/es/setup#app-inventory) y [Estadísticas de uso](/es/setup#usage-statistics), las aplicaciones no se pueden medir. En ese caso, su espacio aparece como Datos del sistema.

## Acceso
{: #access }

Lo que puede ver el analizador depende del acceso que tenga SD Maid:

* **Sin root**, el código de las aplicaciones y los datos privados de las aplicaciones solo se muestran como totales y no se puede entrar en ellos. En Android 13 y posteriores, `Android/data` también necesita root o acceso ADB.
* **Con [root](/es/setup#root-access) o [acceso ADB](/es/setup#shizuku)**, puedes explorar Datos del sistema. Root también permite explorar el código y los datos privados de las aplicaciones, y medir por completo a otros usuarios.
* **Sin el permiso Lista de aplicaciones**, los archivos no se pueden asociar a las aplicaciones a las que pertenecen. Archivos del usuario se muestra entonces en modo de solo lectura: puedes ver los tamaños, pero eliminar, excluir, crear filtros y las sesiones de Swiper están desactivados.

Los elementos a los que SD Maid no puede acceder muestran `No hay acceso directo` al tocarlos.

Las carpetas con más de 100.000 elementos solo se suman y no se pueden explorar.

## Acciones
{: #actions }

Toca un archivo para abrirlo en otra aplicación. Mantén pulsados los elementos para seleccionarlos y después:

* **Borrar**: elimina los elementos seleccionados tras una confirmación. Gratis.
* **Excluir**: crea una [exclusión](/es/exclusions) para las rutas seleccionadas. Estas exclusiones se aplican a todas las herramientas. El analizador no usa exclusiones, ya que necesita verlo todo para calcular los totales correctos.
* **Filtrar**: crea un [filtro personalizado del Limpiador del sistema](/es/systemcleaner#custom-filters) para los elementos seleccionados. Requiere la actualización a Pro.
* **Crear sesión de Swiper**: inicia una sesión de [Swiper](/es/swiper) con los elementos seleccionados.

Los Datos del sistema y el contenido de otros usuarios solo se pueden explorar. No es posible eliminar nada ahí.

## Tendencia de almacenamiento
{: #storage-trend }

SD Maid registra el espacio libre de tus almacenamientos cada pocas horas y después de cada limpieza. `Tendencia de almacenamiento` lo muestra en un gráfico, con marcas que indican cuánto liberó cada limpieza.

* Las tarjetas de almacenamiento muestran cuánto ha cambiado cada almacenamiento en los últimos 7 días.
* El panel principal muestra el cambio combinado de todos los almacenamientos o, en su lugar, una estimación de los días que quedan cuando el almacenamiento se está llenando.
* El tiempo que se guardan las instantáneas se define en `Ajustes > Historial > Historial de almacenamiento`. Por defecto, 90 días, hasta 365.

El gráfico de Tendencia de almacenamiento requiere la actualización a Pro. El cambio de 7 días en las tarjetas de almacenamiento y en el panel principal, y la estimación de días restantes, son gratis.

## Ajustes
{: #settings }

### Umbral de almacenamiento bajo
{: #low-storage-threshold }

Cuando el espacio libre baja de este valor, SD Maid considera que el almacenamiento se está agotando. Por defecto, `Automático`: el 5 % del tamaño del almacenamiento, como máximo 2 GB.

### Aviso de espacio bajo
{: #low-space-warning }

Requiere la actualización a Pro. Avisa cuando el espacio libre de tu almacenamiento principal alcanza el umbral de almacenamiento bajo. Con al menos 5 días de historial constante, también puede avisar antes: cuando el almacenamiento va camino de alcanzar el umbral en 14 días y el espacio libre ya es como máximo el 20 % del almacenamiento, con un límite de 25 GB. Desactivado por defecto.
