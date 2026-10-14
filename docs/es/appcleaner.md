---
title: "Limpiador de aplicaciones"
lang: es
translation_key: appcleaner
permalink: /es/appcleaner
source_hash: 505b031f74bf71a2
---
# Limpiador de aplicaciones (AppCleaner)
{: #app-cleaner }

El Limpiador de aplicaciones (AppCleaner) es una herramienta que conoce las aplicaciones y encuentra archivos "prescindibles". Un archivo se considera prescindible si se puede eliminar sin efectos adversos y, o bien no es necesario, o bien se volverá a crear cuando haga falta. La mayoría de los tipos de archivo de una "caché" encajan en esta definición.

## Ajustes
{: #settings }

### Incluir aplicaciones del sistema
{: #include-system-apps }

Este ajuste incluye las aplicaciones del sistema en los resultados. No está activado por defecto porque algunas aplicaciones del sistema (según la ROM) reaccionan mal cuando se borra su caché. Algunas aplicaciones del sistema tampoco tienen un botón "Borrar caché", lo que puede hacer que SD Maid se quede atascado al borrar la caché mediante el servicio de accesibilidad.

### Incluir a otros usuarios
{: #include-other-users }

Requiere root. Si está activado, SD Maid buscará también en las carpetas de otros usuarios, además del usuario que está ejecutando SD Maid.

### Incluir aplicaciones ejecutándose
{: #include-running-apps }

Si se incluyen o no las aplicaciones en ejecución.

### Tamaño mínimo
{: #minimum-size }

Qué tamaño debe tener la caché de una aplicación para que aparezca en la lista. Indicar un tamaño mínimo es un buen equilibrio entre utilidad y velocidad. Borrar la caché de una aplicación que solo ocupa unos pocos KB normalmente no aporta ningún beneficio. Esto se aplica a la cantidad total de caché encontrada para una aplicación (teniendo en cuenta todos los filtros activos).

### Antigüedad
{: #minimum-age }

Se puede usar para eliminar solo los archivos de caché antiguos. Se aplica a cada archivo o carpeta de los resultados.

### Incluir las cachés inaccesibles
{: #include-inaccessible-caches }

Según los permisos de los que disponga SD Maid, es posible que algunos archivos no sean accesibles directamente. Normalmente se trata de la caché privada predeterminada de las aplicaciones (p. ej., `/data/data/eu.darken.sdmse/cache`). Si este ajuste está activado, SD Maid intentará usar el servicio de accesibilidad para ir a los ajustes del sistema y pulsar el botón `Borrar caché` de la aplicación correspondiente.

### Filtro
{: #filter }

Puedes activar o desactivar distintos filtros para influir en los resultados que encuentra el Limpiador de aplicaciones.

#### Filtros comunes
{: #common-filters }

Los filtros comunes buscan distintos tipos de archivo en todas las aplicaciones.

#### Filtros específicos
{: #specific-filters }

Los filtros específicos se dirigen a aplicaciones concretas, p. ej., WhatsApp o Telegram.

## Solución de problemas
{: #troubleshooting }
### `com.miui.securitycenter`
{: #commiuisecuritycenter }
#### Falta el permiso `GET_USAGE_STATS`
{: #missing-get_usage_stats-permission }

MIUI y HyperOS usan una aplicación adicional llamada "Security" o "Security Center" ("Seguridad"; nombre de paquete: `com.miui.securitycenter`) para mostrar los detalles del sistema de una aplicación. El flujo normal para borrar la caché de una aplicación es: pulsar `Clear data` ("Borrar datos"), seleccionar `Clear cache` ("Borrar caché") y confirmar la acción.

Si el botón 'Clear data' ("Borrar datos") aparece en gris para todas las aplicaciones y no se puede pulsar, a la aplicación "Security Center" le falta el permiso `GET_USAGE_STATS`. Normalmente debería estar concedido por defecto. No está claro cómo ocurre, pero la solución es volver a conceder ese permiso.

Ve a: `System Settings` ("Ajustes del sistema") > `Security and privacy` ("Seguridad y privacidad") > `More security and privacy` ("Más seguridad y privacidad") > `Special permissions` ("Permisos especiales") > `Usage access` ("Acceso a datos de uso") > busca la aplicación `Security` ("Seguridad") > concede el permiso

Como alternativa, si tienes acceso ADB o superior, ejecuta el comando `pm grant com.miui.securitycenter android.permission.PACKAGE_USAGE_STATS`

<a href="/assets/images/appcleaner/miui-usage-stats-1.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-1.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-2.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-2.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-3.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-3.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-4.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-4.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-5.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-5.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-6.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-6.webp" width="100px">
</a>
