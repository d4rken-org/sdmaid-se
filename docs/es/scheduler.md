---
title: "Programador"
lang: es
translation_key: scheduler
permalink: /es/scheduler
source_hash: 74e1452091eaf142
---
# Programador (Scheduler)
{: #scheduler }

La función **Programador** (Scheduler) de SD Maid 2 te permite automatizar tareas de mantenimiento a intervalos concretos, para que tu dispositivo se mantenga optimizado sin esfuerzo manual. Admite varias herramientas, como el **Limpiador de aplicaciones** (AppCleaner), el **Buscador de restos** (CorpseFinder) y el **Limpiador del sistema** (SystemCleaner), que se pueden configurar para ejecutarse a intervalos regulares o a horas concretas.

<img src="/assets/images/scheduler/overview-1.webp" width="150">
<img src="/assets/images/scheduler/overview-2.webp" width="150">
<img src="/assets/images/scheduler/overview-3.webp" width="150">
<img src="/assets/images/scheduler/overview-4.webp" width="150">
<img src="/assets/images/scheduler/overview-5.webp" width="150">
<img src="/assets/images/scheduler/overview-6.webp" width="150">


## Cómo configurar una programación
{: #how-to-set-up-a-schedule }

1. Ve a **Ajustes** y selecciona **Programador**.
2. Toca el botón **+** para crear una programación nueva o gestionar las existentes.
3. Elige qué herramientas (p. ej., **Limpiador de aplicaciones**, **Buscador de restos**, **Limpiador del sistema**) deben ejecutarse de forma programada.
4. Define el **intervalo de repetición** (p. ej., cada 3 días) y la **hora aproximada** de ejecución.
5. Activa la programación con el interruptor.

## Editar o eliminar programaciones
{: #editing-or-deleting-schedules }

1. Ve a los ajustes del **Programador** para gestionar tus programaciones.
2. Puedes modificar las programaciones existentes, ajustar los intervalos o eliminarlas si ya no las necesitas.

Una vez que una programación está activa, SD Maid ejecutará las tareas elegidas a las horas indicadas.

## Opciones adicionales
{: #additional-options }

Desde el menú principal de ajustes, en `Ajustes > Programador`, hay algunas opciones adicionales.

- **Omitir con batería baja**: si el dispositivo está en modo de ahorro de energía, las tareas programadas se omiten.
- **Omitir cuando no se esté cargando**: las tareas programadas solo se ejecutan cuando el dispositivo se está cargando, para evitar que se agote la batería.
- **Comandos posteriores a la programación**: puedes definir comandos de shell que se ejecutarán cuando terminen las tareas programadas.
- **Servicio de accesibilidad**: por defecto, el programador omite las funciones basadas en el servicio de accesibilidad, ya que no funcionan cuando el dispositivo está bloqueado. Si activas esta opción sin hacer ningún otro cambio en tu dispositivo, el programador fallará. Así que, a menos que sepas lo que haces, déjala desactivada. Solo es útil en casos excepcionales, como un dispositivo Android TV siempre encendido.

## Solución de problemas
{: #troubleshooting }

### El programador no se ejecuta
{: #scheduler-is-not-executing }

Distingue entre que el programador no se ejecute en absoluto y que las operaciones ejecutadas no den el resultado deseado. Puedes consultar el historial de ejecuciones en la [sección Historial](/es/stats) de SD Maid. Si el programador se ejecutó pero falló, debería aparecer un error.

Si el programador no se ejecutó en absoluto, es posible que el sistema haya impedido que SD Maid se iniciara para ahorrar batería. Después de activar tu primera programación, deberías haber visto un aviso titulado "Consejo" con una acción "Reparar", que te pedía desactivar las optimizaciones de batería. Si descartaste ese aviso por error, tendrás que ir manualmente a los ajustes del sistema de SD Maid y comprobar si el sistema está "optimizando" SD Maid.

### Servicio de accesibilidad
{: #accessibility-service }
Las eliminaciones basadas en la accesibilidad, p. ej., cuando el Limpiador de aplicaciones elimina "elementos inaccesibles", no funcionan desde el programador. Cuando el programador se ejecuta, el dispositivo suele estar bloqueado; igual que tú no puedes pulsar nada en una pantalla apagada o bloqueada, SD Maid tampoco.

Por defecto, el programador no realiza operaciones basadas en la accesibilidad. Sin embargo, puedes activar la opción "Servicio de accesibilidad" en los ajustes del programador si sabes lo que haces.

Alternativas: rootear el dispositivo (aplicaciones del usuario y del sistema) o instalar Shizuku (solo aplicaciones del usuario). Cualquiera de las dos modificaciones permite a SD Maid realizar operaciones similares sin usar el servicio de accesibilidad.
