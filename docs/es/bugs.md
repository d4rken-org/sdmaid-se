---
title: "Errores"
lang: es
translation_key: bugs
permalink: /es/bugs
source_hash: a218be31a6c4ae63
---
# Errores
{: #bugs }

🐌 🦋 🐛 🐜 🐝 🐞

Los errores (bugs) forman parte de la vida de una aplicación. No pretenden hacerte daño, pero tampoco han venido para quedarse 😠.

¿Qué son los errores?

* Si la aplicación se cierra inesperadamente, obviamente es un error
* Si algo se comporta de forma inesperada, podría ser un error.

En cualquier caso, comprueba que tienes la última versión; puede que el error ya esté corregido.

Si el error no está corregido, infórmalo :grin:.

## Informar de un error
{: #reporting-a-bug }

Crea un issue en GitHub:
<https://github.com/d4rken-org/sdmaid-se/issues>

* Describe qué esperabas y qué ocurrió realmente
* Enumera los pasos que otra persona tendría que seguir para reproducir el problema
* Describe tu configuración
    * El modelo de tu dispositivo (p. ej., Pixel 5)
    * La versión de Android (p. ej., Android 13.1)
    * Si tienes root, la versión de Magisk

Incluye un [registro de depuración](#debug-log) si puedes reproducir el problema. 

Consejo: puedes [mantener pulsada la entrada "Registro de cambios"](https://github.com/d4rken-org/sdmaid-se/pull/940) en los ajustes de SD Maid para copiar la versión al portapapeles.

## Registro de depuración
{: #debug-log }

Un registro de depuración es una grabación de las acciones de SD Maid. Inicias la grabación, reproduces el problema, la detienes y puedes compartir un archivo zip con un relato cronológico de lo que hizo SD Maid.

Para empezar a grabar un registro de depuración:
* Ve a los ajustes de SD Maid pulsando el icono de ajustes en la parte inferior derecha del panel principal
* Busca la entrada `Ayuda` en la lista de ajustes
* Pulsa `Grabar el registro de depuración` en la pantalla de ayuda y, después, vuelve a pulsar `Grabar el registro de depuración` en el diálogo
* Mientras se graba, el panel principal muestra una tarjeta `Grabando registro de depuración`
* **Ahora reproduce el problema que quieres grabar**
* Cuando hayas reproducido el problema, pulsa `Finalizar grabación` en esa tarjeta del panel principal o en la pantalla de ayuda. Si la grabación es muy corta, SD Maid te pregunta antes; pulsa `Detener de todas formas` para detenerla.
* Se abre la pantalla `Registro de depuración`. Cuando el archivo de registro esté comprimido, pulsa `Compartir` para enviarlo.

El registro de depuración se graba hasta que lo detengas; la grabación continúa aunque fuerces la detención de SD Maid y lo reinicies.

Si estás investigando problemas relacionados con funciones básicas, p. ej., la detección de root o las áreas de almacenamiento detectadas, conviene forzar la detención de SD Maid y reiniciarlo mientras grabas, ya que algunos datos solo se inicializan una vez por sesión de la aplicación.

Los archivos del registro de depuración se guardan en `/storage/emulated/0/Android/data/eu.darken.sdmse/cache/debug/logs`.

En imágenes:

<a href="/assets/images/bugs/debug-log-1.webp"><img src="/assets/images/bugs/debug-log-1.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-2.webp"><img src="/assets/images/bugs/debug-log-2.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-3.webp"><img src="/assets/images/bugs/debug-log-3.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-4.webp"><img src="/assets/images/bugs/debug-log-4.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-5.webp"><img src="/assets/images/bugs/debug-log-5.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-6.webp"><img src="/assets/images/bugs/debug-log-6.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-7.webp"><img src="/assets/images/bugs/debug-log-7.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-8.webp"><img src="/assets/images/bugs/debug-log-8.webp" width="100" alt=""></a>
