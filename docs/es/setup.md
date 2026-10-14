---
title: "Configurar SD Maid SE"
lang: es
translation_key: setup
permalink: /es/setup
source_hash: a14003a95dd2336d
---
# Configurar SD Maid SE
{: #setting-up-sd-maid-se }
Información sobre los distintos pasos de configuración de SD Maid SE.

SD Maid se adapta al acceso que tenga disponible. Aunque todos los permisos son opcionales, si no concedes ninguno, la mayoría de las funciones no funcionarán. SD Maid funciona mejor cuando se completan todos los elementos de la configuración.

SD Maid intentará completar automáticamente algunos pasos de la configuración si dispone de [root](#root-access) o de [acceso ADB](#shizuku).

Algunos permisos se solapan o incluyen a otros; p. ej., puedes dar a SD Maid tanto acceso root como acceso ADB. SD Maid siempre intentará usar el mejor método de acceso posible para cada acción y, si falla, lo reintentará con el segundo mejor método, luego con el tercero, etc. Conceder a SD Maid permisos que se solapan mejora la fiabilidad.

> **Consejo:** algunos de los pasos de configuración siguientes requieren ejecutar comandos ADB. Si no tienes ADB configurado en tu computadora, puedes usar el [Web ADB Helper](https://d4rken.github.io/web-adb/) para ejecutarlos directamente desde el navegador.

## Gestionar el almacenamiento
{: #manage-storage }

El permiso "Gestionar el almacenamiento" es un permiso de acceso al almacenamiento de alcance amplio.

* En Android 12 y anteriores concede acceso a todo el almacenamiento público.
* En Android 13+ sigue concediendo acceso a todo el almacenamiento público, pero no a las subcarpetas de `Android/data`.

## Estadísticas de uso
{: #usage-statistics }

El permiso de estadísticas de uso da a SD Maid acceso a los tamaños de datos y de caché, y a si una aplicación se está ejecutando o cuándo se usó por última vez.

## Estructura de acceso al almacenamiento
{: #storage-access-framework }
SD Maid usa el [Storage Access Framework (SAF) de Android](http://developer.android.com/guide/topics/providers/document-provider.html) para obtener acceso a áreas de almacenamiento adicionales. SD Maid te pedirá que concedas acceso a rutas concretas que no son accesibles solo con el permiso "Gestionar el almacenamiento" (cosas de Android™️).

* Pulsa el botón `Otorgar acceso`
* Se abre una ventana nueva
* Comprueba si ya está seleccionada la ruta correcta; si no, tendrás que navegar hasta la ruta que quiere SD Maid.
* Después pulsa el botón `Usar esta carpeta`; la entrada de configuración de esta ruta se desbloqueará y se pondrá de color verde.

Repite este proceso hasta que todas las rutas estén en verde.

<a href="/assets/images/setup/saf-1.webp"><img src="/assets/images/setup/saf-1.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-2.webp"><img src="/assets/images/setup/saf-2.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-3.webp"><img src="/assets/images/setup/saf-3.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-4.webp"><img src="/assets/images/setup/saf-4.webp" width="100" alt=""></a>

### SAF: solución de problemas
{: #saf-troubleshooting }
Para cuando las cosas no salen como estaba previsto.

#### SAF: acceso a `Android/data|obb`
{: #saf-androiddataobb-access }

En Android 11 y Android 12 no es posible conceder acceso a `Android/data|obb` con las versiones más recientes de la aplicación del sistema `Files` ("Archivos"; `com.google.android.documentsui`). Si la aplicación `Files` instalada está orientada a Android 14 (nivel de API 34) o posterior, SD Maid ocultará la solicitud de acceso SAF para estas rutas (como en Android 13+).

Google Play actualiza la aplicación `Files` a través del servicio `Google Play system update` ("Actualización del sistema de Google Play"). La aplicación "Files" original (compilada para el nivel de API 31, p. ej., `aml_doc_310851020 (310851020)`) que venía con Android 12 permite a SD Maid abrir la ventana de selección de rutas SAF en `Android/Data`, mientras que la aplicación "Files" más reciente (compilada para el nivel de API 34 o posterior, p. ej., `14-10492947 (340916000)`) lo impide.

**Solución alternativa:**

Puedes desinstalar la actualización de `Files` y después configurar SD Maid. Volver a actualizar la aplicación `Files` no anulará los permisos ya concedidos.

* Abre los ajustes del dispositivo
* Ve a "Aplicaciones y notificaciones" o "Administrador de aplicaciones"
* Busca la aplicación del sistema `Files`
* Toca el menú de tres puntos, normalmente en la esquina superior derecha
* Elige "Desinstalar actualizaciones"

o bien

_También puedes ejecutar este comando con el [Web ADB Helper](https://d4rken.github.io/web-adb/)._

`adb uninstall com.google.android.documentsui`

#### SAF: OPEN_DOCUMENT_TREE ActivityNotFoundException
{: #saf-open_document_tree-activitynotfoundexception }

Para conceder acceso a rutas mediante el Storage Access Framework hace falta una aplicación que permita al usuario confirmar las rutas a las que se concederá acceso. Esa aplicación es "Documentos" ("Documents"). Es una aplicación del sistema integrada en todos los dispositivos Android desde Android 5.0 (`com.android.documentsui` o `com.google.android.documentsui`). Si te aparece este error, alguien ha eliminado o desactivado esta aplicación. En la mayoría de los casos, alguien ejecutó una herramienta de "debloat" (eliminación de bloatware) demasiado agresiva que la desactivó. Puedes volver a activarla mediante ADB:

_También puedes ejecutar estos comandos con el [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```
adb shell pm enable com.android.documentsui
or
adb shell pm enable com.google.android.documentsui
```

#### SAF: ventana vacía
{: #saf-empty-window }
Si la ventana para conceder acceso a rutas está vacía, es posible que el paquete del proveedor de almacenamiento externo (`com.android.externalstorage`) esté desactivado (probablemente por una herramienta de "debloat" demasiado agresiva). Puedes volver a activarlo mediante ADB:

_También puedes ejecutar este comando con el [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```
adb shell pm enable com.android.externalstorage
```

Ejecutar de nuevo `Google Play system update` ("Actualización del sistema de Google Play") restaurará la actualización. También puedes descargar manualmente una versión actualizada desde [APKMirror.com](https://www.apkmirror.com/apk/google-inc/files-6/).

## Notificaciones
{: #notifications }

Sin este permiso, SD Maid no podrá mostrar notificaciones. En algunas ROM esto puede afectar negativamente a la fiabilidad al ejecutarse en segundo plano.

## Lista de aplicaciones
{: #app-inventory }

SD Maid necesita el permiso `QUERY_ALL_PACKAGES` para comprobar qué aplicaciones están instaladas en tu dispositivo (es decir, ["visibilidad del inventario de aplicaciones instaladas"](https://support.google.com/googleplay/android-developer/answer/10158779?hl=en)). Consulta también ["Permiso de estadísticas de uso"](/es/setup#usage-statistics)

El permiso `QUERY_ALL_PACKAGES` es obligatorio para:

* [Buscador de restos (CorpseFinder)](/es/corpsefinder): para saber qué archivos se pueden eliminar, SD Maid necesita saber si tienes instaladas aplicaciones que podrían necesitarlos.
* [Limpiador de aplicaciones (AppCleaner)](/es/appcleaner): determinar las cachés de cada aplicación
* [Control de las aplicaciones (AppControl)](/es/appcontrol): poder listar todas las aplicaciones instaladas
* [Analizador del Almacenamiento (StorageAnalyzer)](/es/storageanalyzer): atribuir el almacenamiento usado a las aplicaciones instaladas

Este permiso existe desde [Android 11](https://developer.android.com/training/package-visibility) y normalmente se concede por defecto. Desde Android 14 existen algunos dispositivos (p. ej., [#1015](https://github.com/d4rken-org/sdmaid-se/issues/1015), [#1354](https://github.com/d4rken-org/sdmaid-se/issues/1354)) en los que el permiso no se concede por defecto o se puede revocar.

[<img src="/assets/images/setup/app-inventory.webp" width="100">](/assets/images/setup/app-inventory.webp)

### Lista de aplicaciones no válida
{: #invalid-app-list }

En algunos dispositivos el permiso "Lista de aplicaciones" (`QUERY_ALL_PACKAGES` / `GET_INSTALLED_APPS`) está concedido, pero el sistema sigue devolviendo una **lista incompleta de aplicaciones instaladas**; por ejemplo, una lista a la que le faltan paquetes básicos del sistema como `android`. Cuando SD Maid lo detecta, en lugar de continuar muestra un aviso **"Invalid app list"** ("Lista de aplicaciones no válida") y una nota **"Posible limitación del dispositivo"** en la tarjeta de configuración.

SD Maid se detiene aquí a propósito: si no puede ver la lista completa de aplicaciones instaladas, no puede saber con seguridad qué archivos pertenecen a una aplicación que sigue instalada. Continuar de todos modos podría suponer eliminar datos de una aplicación que SD Maid simplemente no pudo ver, así que las funciones relacionadas con aplicaciones permanecen desactivadas hasta que haya una lista completa disponible.

**Qué puedes probar:**

* Abre los ajustes del sistema y asegúrate de que SD Maid tiene permitido el permiso "Lista de aplicaciones". En algunos fabricantes este interruptor tiene otro nombre (p. ej., "Consultar todos los paquetes") o se encuentra en una pantalla aparte de "Privacidad" o "Gestor de permisos".
* Comprueba si algún ahorro de batería integrado, protección de privacidad o función para "ocultar aplicaciones" está filtrando la lista de aplicaciones que SD Maid puede ver, y excluye a SD Maid de ella.
* Reinicia el dispositivo y vuelve a abrir SD Maid para que se vuelva a evaluar el estado del permiso.

Si el permiso "Lista de aplicaciones" ya está permitido y la lista sigue apareciendo como no válida, normalmente se trata de una **limitación del dispositivo** y no de algo que puedas arreglar: algunos sistemas (por ejemplo, HarmonyOS o versiones de Android muy modificadas) nunca entregan una lista completa de aplicaciones a las aplicaciones de terceros. Consulta [#2441](https://github.com/d4rken-org/sdmaid-se/issues/2441) como ejemplo.

En ese caso, las herramientas relacionadas con aplicaciones ([Buscador de restos](/es/corpsefinder), [Limpiador de aplicaciones](/es/appcleaner), [Control de las aplicaciones](/es/appcontrol)) permanecen desactivadas para proteger tus datos, pero el [Analizador del Almacenamiento](/es/storageanalyzer) sigue funcionando: muestra cuánto espacio se está usando en tu almacenamiento, solo que con menos detalle; los tamaños de las carpetas se muestran en modo de solo lectura y no se pueden atribuir a aplicaciones concretas.

## Servicio de accesibilidad
{: #accessibility-service }
El servicio de accesibilidad permite a SD Maid interactuar con tu dispositivo como lo haría una persona, es decir, viendo la pantalla y pulsando botones. SD Maid puede usarlo para automatizar acciones tediosas, como pulsar "Borrar caché" en cientos de aplicaciones. El servicio de accesibilidad es un servicio que la propia aplicación no puede iniciar; lo tiene que iniciar el sistema, porque este tipo de servicio concede a una aplicación acceso adicional a tu dispositivo.

### ACS: solución de problemas
{: #acs-troubleshooting }

El servicio puede dejar de funcionar y requerir que lo vuelvas a activar en diversas circunstancias:
* Actualizas la aplicación.
* La aplicación se cierra inesperadamente. Si ocurre, deberías informarlo como error. Si ocurre con demasiada frecuencia, es posible que no puedas reiniciarlo desactivándolo y volviéndolo a activar hasta que reinicies el dispositivo.
* El sistema cierra la aplicación debido a "optimizaciones de batería" demasiado agresivas (consulta también <https://dontkillmyapp.com/>)
* La propia aplicación de seguridad del fabricante lo desactiva (consulta [Bloqueado por Phone Manager](#acs-blocked-by-phone-manager-oneplus-oppo-realme))

#### ACS: el servicio está desactivado
{: #acs-service-is-disabled }

Algunas ROM (p. ej., MIUI) son conocidas por detener de forma agresiva todo lo que el fabricante del dispositivo no tiene en su lista blanca.
Si se te pide constantemente que vuelvas a activar el servicio de accesibilidad, es posible que el sistema lo desactive cada vez que SD Maid no está en uso activo.

Prueba a desactivar las optimizaciones de batería; en las ROM MIUI hay además un ajuste llamado "Autostart" (inicio automático): actívalo para SD Maid. En dispositivos Xiaomi/POCO/Redmi:

* Ve a `Security>Manage applications>Auto start in background` ("Seguridad > Administrar aplicaciones > Inicio automático en segundo plano") y permítelo para SD Maid.
* Ve a `Security>Increase speed>Lock apps` ("Seguridad > Aumentar velocidad > Bloquear aplicaciones") y selecciona SD Maid.


También puedes plantearte conceder un permiso adicional que [permita a SD Maid reiniciar el servicio automáticamente](#acs-automatic-service-restart).

[<img src="/assets/images/setup/acs-disabled.webp" width="100">](/assets/images/setup/acs-disabled.webp)

#### ACS: bloqueado por Phone Manager (OnePlus, OPPO, Realme)
{: #acs-blocked-by-phone-manager-oneplus-oppo-realme }

En OxygenOS y ColorOS, la aplicación preinstalada "Phone Manager" (administrador del teléfono) puede desactivar el servicio de accesibilidad por su cuenta, normalmente en el segundo siguiente a terminar una limpieza. El interruptor de accesibilidad de Android queda realmente desactivado después, así que parece que SD Maid dejó de funcionar a mitad de la ejecución.

Desactiva la función en `Phone Manager>Utilities>Block suspicious app activities` ("Administrador del teléfono > Utilidades > Bloquear actividades sospechosas de aplicaciones").

Esa misma pantalla guarda una lista de lo que ha bloqueado. Las entradas etiquetadas como "Abnormal device control" (control anómalo del dispositivo) con marcas de tiempo que coinciden con las limpiezas que ejecutaste confirman que esta es la causa. El interruptor "Blocking alerts" (alertas de bloqueo) que hay al lado solo controla si recibes una notificación, así que con las alertas desactivadas esto ocurre sin avisar.

#### ACS: activar mediante ADB
{: #acs-enable-via-adb }

Si tu dispositivo no tiene una forma de activar los servicios de accesibilidad desde la pantalla (p. ej., un dispositivo de TV), puedes activar el servicio mediante ADB ejecutando:

_También puedes ejecutar este comando con el [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```bash
adb shell settings put secure enabled_accessibility_services eu.darken.sdmse/.automation.core.AutomationService
```

#### ACS: reinicio automático del servicio
{: #acs-automatic-service-restart }

_Nota: es una técnica avanzada y está pensada para usuarios avanzados._

Puedes conceder un permiso adicional que permite a SD Maid reiniciar el servicio por sí mismo.
El permiso se llama `WRITE_SECURE_SETTINGS` y solo se puede conceder [mediante ADB](https://www.google.com/search?q=android+adb).
Si lo tienes configurado, ejecuta este comando para conceder el permiso:

_También puedes ejecutar este comando con el [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```bash
adb shell pm grant eu.darken.sdmse android.permission.WRITE_SECURE_SETTINGS
```

Con este permiso concedido, SD Maid puede usar el comando [descrito aquí](#acs-enable-via-adb) para activar el servicio cuando sea necesario.
SD Maid solo lo usará si has activado el servicio de accesibilidad manualmente al menos una vez.
Si vuelves a la configuración y le indicas a SD Maid que no use el servicio de accesibilidad, esta técnica de reinicio automático no se usará hasta que vuelvas a activar manualmente el servicio de accesibilidad.

Para volver a quitarle este permiso a SD Maid, usa este comando:

_También puedes ejecutar este comando con el [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```bash
adb shell pm revoke eu.darken.sdmse android.permission.WRITE_SECURE_SETTINGS
```

Si obtienes `Operation not allowed: java.lang.SecurityException`, [consulta aquí](https://stackoverflow.com/questions/52079343). Algunos dispositivos restringen los permisos de ADB. Comprueba si en las opciones para desarrolladores hay un ajuste adicional (p. ej., `Disable Permission Monitoring`, "Desactivar la supervisión de permisos") que haya que activar; después de usar el comando, puedes volver a desactivarlo.

#### ACS: Protección avanzada
{: #acs-advanced-protection }

En Android 17 y posteriores, el modo [**Protección avanzada**](https://developer.android.com/privacy-and-security/advanced-protection-mode) de Google impide que las aplicaciones que no son herramientas de accesibilidad específicas (lectores de pantalla, accesibilidad con interruptores, etc.) usen el servicio de accesibilidad. SD Maid es una herramienta de mantenimiento y limpieza, así que mientras la Protección avanzada esté activada, su servicio de accesibilidad **no se puede** activar: Android rechaza el servicio a nivel de sistema, aunque intentes concederlo mediante root o acceso ADB.

SD Maid no puede eximirse de esto: solo pueden optar las aplicaciones que Google clasifica como herramientas de accesibilidad, y los limpiadores están excluidos expresamente.

Tienes dos opciones:

- **Usar root o acceso ADB** Cuando hay root o acceso ADB configurado, el Limpiador de aplicaciones (AppCleaner) de SD Maid borra las cachés de las aplicaciones mediante una llamada directa al sistema y **no** necesita el servicio de accesibilidad. Con **root** esto también incluye limpiar aplicaciones concretas seleccionadas. Con **acceso ADB** funciona sin el servicio de accesibilidad una ejecución completa de "limpiar todas las aplicaciones", pero borrar la caché de aplicaciones *concretas* seleccionadas sigue dependiendo de él. Configúralo en SD Maid, en Configuración → [Acceso root](#root-access) o [Acceso ADB](#shizuku).
- **Desactivar la Protección avanzada** si quieres específicamente la limpieza basada en la accesibilidad. Está en `Ajustes → Seguridad y privacidad → Protección avanzada` (la ruta exacta varía según el dispositivo). Si vuelves a activarla más tarde, se desactivará de nuevo el servicio de accesibilidad.

#### ACS: restricciones de AppOps
{: #acs-appops-restrictions }

Android 13+ puede aplicar restricciones adicionales a las aplicaciones instaladas manualmente (sideloading). Así que, si SD Maid no se ha instalado desde una fuente conocida (por Google) como Google Play u otras tiendas de aplicaciones, es posible que no puedas conceder directamente algunos permisos a SD Maid, p. ej., iniciar un servicio de accesibilidad.

##### OxygenOS 15 - OnePlus
{: #oxygenos-15---oneplus }

OnePlus olvidó incluir (o simplemente no incluyó) la acción `Allow restricted settings` ("Permitir ajustes restringidos") en los ajustes de las aplicaciones de OxygenOS 15. Si no instalas desde Google Play, el instalador tiene que establecer el indicador `INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS`. Si tienes Shizuku configurado, puedes usar la aplicación ["Install with Options"](https://github.com/zacharee/InstallWithOptions).


##### Otras ROM
{: #other-roms }

Para levantar estas restricciones, abre en el sistema la pantalla de ajustes de la aplicación SD Maid. Abre el menú de la esquina superior derecha y selecciona `Permitir ajustes restringidos`; después puedes volver a la lista de servicios de accesibilidad y activar el servicio de SD Maid.

**Nota:**
Para mejorar la usabilidad (es sarcasmo), Google decidió que este ajuste solo sea visible si:

* Has visto al menos una vez el aviso "Ajuste restringido" después de pulsar la entrada del servicio de accesibilidad de SD Maid
* Has abierto los ajustes del sistema y has navegado a mano hasta la página de ajustes de SD Maid, no a través de otra aplicación o de accesos directos.

😮‍💨


Puedes encontrar más información de contexto [aquí](https://blog.esper.io/android-13-sideloading-restriction-harder-malware-abuse-accessibility-apis/).

<a href="/assets/images/setup/acs-restricted-settings-1.webp"><img src="/assets/images/setup/acs-restricted-settings-1.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-2.webp"><img src="/assets/images/setup/acs-restricted-settings-2.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-3.webp"><img src="/assets/images/setup/acs-restricted-settings-3.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-4.webp"><img src="/assets/images/setup/acs-restricted-settings-4.webp" width="100" alt=""></a>

## Acceso root
{: #root-access }

Con acceso root, SD Maid tiene acceso completo a tu dispositivo y puede hacer el escaneo más exhaustivo posible. Normalmente esto significa que has desbloqueado el bootloader y has instalado Magisk.

Si SD Maid cree que tu dispositivo puede estar rooteado, pedirá primero acceso root. Si se concede, SD Maid usará el acceso root para conceder tantos de los demás permisos como sea posible. El acceso root es el nivel de acceso más alto que puedes dar a una aplicación; los demás permisos solo son necesarios entonces por comodidad y rendimiento, y por eso no requieren un consentimiento adicional durante la configuración si SD Maid ya tiene root.

## Acceso ADB
{: #shizuku }

El acceso ADB permite a SD Maid usar la interfaz de depuración de Android (ADB) para operaciones con privilegios elevados. Necesita una aplicación aparte que ejecute un servicio ADB en tu dispositivo y dé acceso a él a otras aplicaciones: [Porter](#porter) o [Shizuku](#using-shizuku). Es una modificación que puedes hacer en tu teléfono y que otras aplicaciones pueden aprovechar.

Es parecido a root, pero no es lo mismo. Los permisos son algo menos potentes, pero la modificación no es permanente y no requiere desbloquear el bootloader.

### Porter
{: #porter }

[Porter](https://porter.darken.eu) es mi fork mantenido de Shizuku, con soporte opcional para root. Su sitio web explica cómo instalarlo e iniciarlo. En Android 11 y posteriores, la depuración inalámbrica permite iniciarlo sin computadora.

Si vas a configurar el acceso ADB por primera vez, usa Porter. Las versiones de SD Maid de GitHub y F-Droid te remiten a Porter cuando no hay instalada ninguna aplicación de acceso ADB. La versión de Google Play te remite a Shizuku en Google Play, pero funciona igual con Porter.

Si están instalados tanto Porter como Shizuku, SD Maid usa Porter. La tarjeta de configuración indica entonces `Porter está instalado, por lo que SD Maid espera a Porter en lugar de Shizuku.` Inicia Porter para continuar, o desinstálalo si quieres que SD Maid use Shizuku.

### Usar Shizuku
{: #using-shizuku }

Hay dos versiones de Shizuku:

* [El fork de thedjchi](https://github.com/thedjchi/Shizuku) ([guía de configuración](https://github.com/thedjchi/Shizuku/wiki/setup)) se mantiene activamente y, según los informes, funciona en dispositivos en los que el original [nunca se conecta](#shizuku-never-connects).
* [El original de RikkaApps](https://shizuku.rikka.app/guide/setup/) sigue funcionando bien en la mayoría de los dispositivos.

No se pueden instalar a la vez. Si el original ya te funciona, no hace falta cambiar.

### Conceder acceso
{: #granting-access }

Después de configurar Porter o Shizuku:

* En la configuración de SD Maid, elige `Intenta usar acceso ADB`. Es un ajuste interno de SD Maid; puedes usarlo para activar o desactivar el uso del acceso ADB por parte de SD Maid
* Después de dar tu consentimiento, un diálogo de Porter o Shizuku pregunta si SD Maid puede usarlo
* Confirma el diálogo
* La tarjeta de configuración `Acceso ADB` muestra si SD Maid puede conectarse

Configurar el acceso ADB cuando ya has concedido acceso root a SD Maid no activa funciones adicionales, pero mejora la fiabilidad. Si las acciones fallan con el permiso root, SD Maid lo reintentará con el acceso ADB.

### Acceso ADB: solución de problemas
{: #shizuku-troubleshooting }

Puedes comprobar el estado del acceso ADB de SD Maid en `Ajustes > Configuración`, en la tarjeta de configuración `Acceso ADB`. Debería indicar `SD Maid ahora puede usar acceso ADB a través de Porter.` (o `a través de Shizuku`).

* `Porter aún no ha permitido SD Maid.`: confirma el diálogo de acceso o permite SD Maid en la aplicación Porter (o Shizuku).
* `Porter deniega el acceso de SD Maid.`: a SD Maid se le denegó el acceso de forma permanente. Abre Porter (o Shizuku) y permite SD Maid ahí.
* `Actualiza Porter para usarlo con SD Maid.` o `Esta versión de SD Maid es demasiado antigua para Porter.`: las dos aplicaciones no tienen versiones compatibles. Actualiza la que indique la tarjeta.

#### Shizuku nunca se conecta
{: #shizuku-never-connects }

La tarjeta indica `Error de acceso ADB` y `Shizuku está en ejecución, pero nunca conectó SD Maid con su servicio auxiliar.` Las versiones anteriores mostraban `Waiting for link with Shizuku` ("Esperando el enlace con Shizuku") o `Shizuku link failed` ("Falló el enlace con Shizuku").

Abre la aplicación Shizuku. Si indica que se está ejecutando y SD Maid está en su lista de aplicaciones autorizadas, Shizuku está bien configurado y volver a configurarlo no servirá de nada. Lo que falla es el servicio auxiliar de Shizuku: se cierra al iniciarse, antes de poder responder, y SD Maid se rinde tras unos 15 segundos. `Reintentar` ejecuta la misma comprobación y falla de la misma manera.

Es un error de Shizuku ([RikkaApps/Shizuku#1198](https://github.com/RikkaApps/Shizuku/issues/1198)), no de SD Maid, y afecta a cualquier aplicación que use el servicio auxiliar de Shizuku. Afecta a los dispositivos MediaTek y a los dispositivos con HyperOS o MIUI.

Según los informes, [el fork de thedjchi](https://github.com/thedjchi/Shizuku) lo soluciona. Para cambiar:

* Desinstala primero el original; no pueden coexistir
* Desvincúlalo de la depuración inalámbrica si la usabas
* Instala el fork desde sus [versiones publicadas](https://github.com/thedjchi/Shizuku/releases) y sigue su [guía de configuración](https://github.com/thedjchi/Shizuku/wiki/setup)
* Vuelve a conceder el permiso de Shizuku a SD Maid y a todo lo demás que lo usaba
