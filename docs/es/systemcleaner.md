---
title: "Limpiador del sistema"
lang: es
translation_key: systemcleaner
permalink: /es/systemcleaner
source_hash: 1ced6df9b00f1e05
---
# Limpiador del sistema (SystemCleaner)
{: #system-cleaner }

El Limpiador del sistema (SystemCleaner) es, básicamente, un motor de búsqueda de uso general. Recorre todo el almacenamiento accesible y compara los archivos y carpetas encontrados con un conjunto de filtros.

A diferencia de otras herramientas como el Buscador de restos (CorpseFinder) o el Limpiador de aplicaciones (AppCleaner), no sabe nada de las aplicaciones instaladas. Trabaja únicamente con rutas y atributos de archivos. Por eso al Limpiador del sistema solo se le pueden aplicar exclusiones de rutas y de segmentos.

Cada archivo encontrado pasa por los filtros activados y se asigna al primer filtro con el que coincide.

## Ajustes
{: #settings }

### Filtros generales
{: #general-filters }

Los filtros generales buscan **en todas las áreas de almacenamiento accesibles** ciertos tipos de archivo que pueden aparecer en cualquier parte del dispositivo.

| Filtro | Descripción |
|--------|-------------|
| **Carpetas vacías** | Carpetas vacías de todo el dispositivo. Las carpetas anidadas pueden requerir varios escaneos. |
| **Capturas de pantalla** | Capturas de pantalla más antiguas que una antigüedad configurable, de varias aplicaciones y ubicaciones. |
| **Archivos eliminados** | Archivos en papeleras de varias aplicaciones y ubicaciones que están marcados para eliminarse, pero que todavía no se han borrado definitivamente. |
| **APKs superfluos** | Archivos de instalación (APK) de versiones anteriores o iguales a las de aplicaciones que ya están instaladas. |
| **Carpetas LOST.DIR** | Archivos temporales creados al transferir datos entre dispositivos Android. |
| **Archivos Linux** | Archivos y carpetas creados al conectar el almacenamiento a una computadora con Linux. |
| **Archivos Mac** | Archivos y carpetas creados al explorar el almacenamiento con la aplicación Finder de Apple (p. ej., `.DS_Store`). |
| **Archivos de Windows** | Datos que el explorador de archivos de Windows puede crear en un almacenamiento conectado (p. ej., `Thumbs.db`). |
| **Miniaturas** | Vistas previas en caché de imágenes y videos (p. ej., carpetas `.thumbnails`). |
| **Archivos temporales del sistema** | Archivos temporales relacionados con el sistema, normalmente cachés de corta duración para operaciones en curso. |
| **Archivos del análisis** | Datos relacionados con analíticas y seguimiento de errores. |
| **Archivos de anuncios** | Archivos creados para anuncios o por ellos. |
| **Archivos de registro del sistema** | Archivos de registro del sistema y de servicios del sistema (p. ej., `*.log`) en varias ubicaciones. |

### Filtros específicos
{: #specific-filters }

Los filtros específicos se dirigen a **rutas fijas y conocidas** del dispositivo donde se guardan ciertos tipos de datos.

| Filtro | Descripción |
|--------|-------------|
| **Restos de los fallos del sistema** | Si los procesos nativos fallan (p. ej., juegos), aquí se crean archivos "tombstone". |
| **Estadísticas de uso** | Archivos relacionados con las estadísticas de uso de las aplicaciones. |
| **Errores ANR** | Trazas de eventos "Application Not Responding" (la aplicación no responde). |
| **Archivos temporales locales** | Archivos temporales relacionados con el sistema (p. ej., de Android Studio). |
| **Archivos de registro del sistema (datalogger)** | Archivos de registro específicos de servicios del sistema. |
| **Cache de la descarga** | Carpeta de caché del sistema que se usa para varias cosas (p. ej., actualizaciones OTA). |
| **Buzón del registro** | Un área donde el sistema deposita archivos de registro. |
| **Caché del instalador** | Datos en caché relacionados con las aplicaciones instaladas que usa la gestión de paquetes del sistema. |
| **Tareas recientes** | Datos relacionados con el historial de tareas del selector de tareas del sistema. |

### Filtros personalizados
{: #custom-filters }

Además de los filtros generales que incluye la aplicación, puedes crear filtros personalizados con tus propios criterios. Los filtros personalizados se crean desde **Ajustes > Limpiador del sistema > Administrar filtros personalizados**.

> **Advertencia**: los filtros personalizados pueden ser arriesgados. ¡Revisa siempre los resultados del escaneo antes de eliminar!

#### Criterios disponibles
{: #available-criteria }

| Criterio | Descripción |
|-----------|-------------|
| **Etiqueta** | Un nombre para tu filtro |
| **Áreas de datos** | Ubicaciones de almacenamiento que escanear (déjalo vacío para todas las áreas accesibles) |
| **Tipos de archivo** | Archivos, carpetas o enlaces simbólicos objetivo (déjalo vacío para todos) |
| **Criterios de ruta** | Reglas para comparar rutas de archivos |
| **Exclusiones de ruta** | Rutas que se excluyen de la comparación |
| **Criterios para el nombre** | Reglas para comparar nombres de archivo |
| **Tamaño mínimo** | Solo coinciden los archivos más grandes que esto |
| **Tamaño máximo** | Solo coinciden los archivos más pequeños que esto |
| **Antigüedad mínima** | Solo coinciden los archivos más antiguos que esto |
| **Antigüedad máxima** | Solo coinciden los archivos más recientes que esto |
| **Regex de ruta** | Avanzado: expresiones regulares para comparar rutas |

#### Modos de los criterios de ruta
{: #path-criteria-modes }

| Modo | Descripción | Ejemplo |
|------|-------------|---------|
| **Comienza con** | La ruta empieza por la palabra clave | `Downloads/temp` coincide con `/sdcard/Downloads/temp/file.txt` |
| **Contiene** | La ruta incluye la palabra clave | `cache` coincide con `/sdcard/app/cache/data.bin` |
| **Termina con** | La ruta termina con la palabra clave | `backup/old` coincide con `/sdcard/backup/old` |
| **Es igual a** | La ruta coincide exactamente con la palabra clave | `Download/temp` solo coincide con esa ruta exacta |

#### Modos de los criterios para el nombre
{: #name-criteria-modes }

| Modo | Descripción | Ejemplo |
|------|-------------|---------|
| **Comienza con** | El nombre del archivo empieza por la palabra clave | `temp_` coincide con `temp_file.txt` |
| **Contiene** | El nombre del archivo incluye la palabra clave | `.bak` coincide con `document.bak.old` |
| **Termina con** | El nombre del archivo termina con la palabra clave | `.tmp` coincide con `cache.tmp` |
| **Es igual a** | El nombre del archivo coincide exactamente | `thumbs.db` solo coincide con `thumbs.db` |

Por defecto, todas las comparaciones **no distinguen entre mayúsculas y minúsculas**.

#### Áreas de datos
{: #data-areas }

Áreas habituales que puedes usar:

| Área | Descripción |
|------|-------------|
| **SDCARD** | Almacenamiento principal (interno o tarjeta SD) |
| **PUBLIC_MEDIA** | Carpetas multimedia (DCIM, Pictures, Music, etc.) |
| **PUBLIC_DATA** | Datos compartidos de las aplicaciones |
| **PRIVATE_DATA** | Almacenamiento privado de las aplicaciones (requiere root) |
| **DOWNLOAD_CACHE** | Caché de descargas del sistema (requiere root) |
| **PORTABLE** | Almacenamiento extraíble (USB/microSD) |

#### Consejos
{: #tips }

- **Rendimiento**: define solo los criterios necesarios. Demasiados patrones regex ralentizan el escaneo.
- **Varios criterios**: deben coincidir todos los criterios indicados (lógica AND).
- **Mantener pulsado**: mantén pulsadas las entradas para ver más opciones.
- **Pruebas**: usa la vista previa del escaneo antes de activar la eliminación.
- **Importar/exportar**: los filtros se pueden exportar y compartir con otros usuarios.

#### Opciones obsoletas
{: #deprecated-options }

La opción `rootOnly` de SD Maid 1 **ya no está disponible**. En su lugar, usa **Áreas de datos** para controlar qué ubicaciones de almacenamiento se escanean.
