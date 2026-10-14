---
title: "Buscador de restos"
lang: es
translation_key: corpsefinder
permalink: /es/corpsefinder
source_hash: 9e16286f0fda1dc8
---
# Buscador de restos (CorpseFinder)
{: #corpse-finder }

La herramienta `Buscador de restos` (CorpseFinder) encuentra archivos que pertenecen a aplicaciones que ya no están instaladas.


## Nivel de riesgo
{: #risklevel }

Por defecto, todas las opciones de nivel de riesgo están desactivadas. Actívalas solo si sabes lo que haces y, antes de eliminar nada, revisa primero los resultados.
Activar una opción de nivel de riesgo aumenta la probabilidad de que se eliminen archivos que no querías eliminar.

Los niveles de riesgo se aplican sobre todo a ubicaciones de lista blanca, como la raíz de tu almacenamiento público (`/storage/emulated/0`). Estas ubicaciones no siguen ningún patrón de nombres común y SD Maid solo puede determinar a quién pertenecen mediante una base de datos mantenida a mano.


### Incluir restos deseados
{: #include-desirable-remnants }

Los restos deseados son archivos con propietarios conocidos que no están instalados, pero que SD Maid marca internamente como `KEEPER`. Normalmente son archivos creados por el usuario, p. ej., fotos que hiciste.

### Incluir los nombres comunes
{: #include-common-names }

Algunas aplicaciones usan nombres muy comunes. Probablemente haya cientos de aplicaciones de notas que crean una carpeta `/sdcard/Notes` para guardar datos. Puede que SD Maid conozca una de esas aplicaciones y que no esté instalada en tu teléfono, pero SD Maid no puede estar seguro de que no haya otra aplicación en tu teléfono que siga usando esa carpeta y de la que SD Maid no sepa que también la usa. 

## Filtro
{: #filter }

Cada filtro se encarga de un área de datos concreta. Al activar un filtro, el Buscador de restos buscará también en esa área.

### Tarjeta SD
{: #sdcard }

* Normalmente es donde el usuario guarda sus archivos
* Almacenamiento público (tanto integrado como extraíble), excepto subcarpetas concretas de `Android/data`.
* Los archivos de aquí no siguen ningún esquema de nombres.

Ejemplos:

```bash
/storage/emulated/0/SDMaid
/storage/DEAD-BEEF/SDMaid
/sdcard/SDMaid
```

### Medios públicos
{: #public-media }

* Guarda contenido multimedia público de las aplicaciones, p. ej., archivos recibidos por WhatsApp. Las carpetas llevan el nombre de paquete de las aplicaciones.
* Subcarpeta del almacenamiento público con un significado especial para el sistema operativo. 

Ejemplos:

```bash
/storage/emulated/0/Android/media/eu.darken.sdmse
/storage/DEAD-BEEF/Android/media/eu.darken.sdmse
/sdcard/Android/media/eu.darken.sdmse
```

### Datos públicos de la aplicación
{: #public-app-data }

* Archivos adicionales de las aplicaciones.
* Las carpetas llevan el nombre de paquete de las aplicaciones.
* Subcarpeta del almacenamiento público con un significado especial para el sistema operativo.

Ejemplos:

```bash
/storage/emulated/0/Android/data/eu.darken.sdmse
/storage/DEAD-BEEF/Android/data/eu.darken.sdmse
/sdcard/Android/data/eu.darken.sdmse
```

### Recursos OBB
{: #obb-resources }

* Normalmente, archivos de recursos adicionales de juegos.
* Las carpetas y los archivos llevan el nombre de paquete de las aplicaciones.
* Subcarpeta del almacenamiento público con un significado especial para el sistema operativo.

Ejemplos:

```bash
/storage/emulated/0/Android/obb/eu.darken.sdmse
/storage/DEAD-BEEF/Android/obb/eu.darken.sdmse
/sdcard/Android/obb/eu.darken.sdmse
```

### Datos privados de la aplicación
{: #private-app-data }

* Las carpetas y los archivos llevan el nombre de paquete de las aplicaciones.
* Subcarpeta del almacenamiento público con un significado especial para el sistema operativo.

Ejemplos:

```bash
/data/data/eu.darken.sdmse
```

### Caché Dalvik
{: #dalvik-cache }

* Guarda versiones optimizadas del código de las aplicaciones para que se ejecuten más rápido.
* Se encuentra en la partición del sistema, a menudo en carpetas específicas de cada arquitectura.
* Los archivos se nombran con el nombre de paquete de la aplicación.

Ejemplos:

```bash
/data/dalvik-cache/arm64/data@app@eu.darken.sdmse@base.apk@classes.dex
/data/dalvik-cache/arm/data@app@eu.darken.sdmse@base.apk@classes.dex
```

### Bibliotecas de aplicaciones
{: #app-libraries }

* Contiene bibliotecas nativas que usan las aplicaciones, como archivos `.so`.
* Normalmente se encuentran en la carpeta de datos de la aplicación, dentro de una carpeta `lib`.
* Los archivos se nombran según la arquitectura y los requisitos de cada biblioteca.

Ejemplos:

```bash
/data/app/eu.darken.sdmse-1/lib/arm64/libexample.so
/data/app/eu.darken.sdmse-2/lib/arm/libexample.so
```

### Fuentes de la aplicación
{: #app-sources }

* Se refiere a los archivos APK que sirven como código fuente de las aplicaciones.
* Se guardan en carpetas específicas para aplicaciones del sistema, del usuario o del fabricante.
* Los archivos llevan el nombre de paquete y la versión de la aplicación.

Ejemplos:

```bash
/data/app/eu.darken.sdmse-1/base.apk
/system/app/ExampleApp/ExampleApp.apk
```

### Fuentes de aplicaciones privadas
{: #private-app-sources }

* Similares a las fuentes de la aplicación, pero guardadas en carpetas protegidas para mayor seguridad.
* Solo son accesibles para el sistema o para la aplicación correspondiente.
* Los archivos llevan el nombre de paquete de la aplicación.

Ejemplos:

```bash
/data/app-private/eu.darken.sdmse/base.apk
```
