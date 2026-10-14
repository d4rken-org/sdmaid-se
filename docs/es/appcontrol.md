---
title: "Control de las aplicaciones"
lang: es
translation_key: appcontrol
permalink: /es/appcontrol
source_hash: 1a4a7450fc5b86ca
---
# Control de las aplicaciones (AppControl)
{: #appcontrol }
El Control de las aplicaciones (AppControl) reúne en un solo lugar todas las acciones relacionadas con el control de las aplicaciones instaladas. Tras el escaneo tendrás una lista con búsqueda de tus aplicaciones instaladas. Las aplicaciones del sistema no se muestran por defecto; hay que activarlo en los ajustes.
Algunas opciones solo están disponibles (son posibles) si se han concedido permisos root a SD Maid.

Algunas acciones se pueden ejecutar sobre varias aplicaciones y otras solo sobre elementos individuales. Para ejecutar acciones sobre varias aplicaciones, mantén pulsada una aplicación y después toca otras para seleccionarlas.

## Acciones
{: #actions }
Algunas de estas acciones solo están disponibles en los detalles de la aplicación, a los que se accede tocando una entrada de la lista.

### Iniciar aplicación
{: #run-app }
Inicia la aplicación. Abre su actividad principal, normalmente la misma acción que ejecutaría el launcher.

### Forzar la detención
{: #force-stop }
Intenta forzar la detención de la aplicación. Funciona mejor con root o ADB/Shizuku; de lo contrario, usa el servicio de accesibilidad.

### Exportar la aplicación
{: #export-apk }
Intenta copiar el archivo `.apk` de la aplicación desde el almacenamiento interno privado a la carpeta de descargas de tu almacenamiento público. Si la aplicación está cifrada, esta acción requiere root; las aplicaciones sin cifrar también se pueden exportar sin root.

### Congelar aplicación
{: #freeze-app }
Requiere root o ADB/Shizuku. Desactiva la aplicación. La aplicación no será visible y no podrá iniciarse por sí misma. Hay que volver a activarla para que funcione. No se pierden datos y este estado se mantiene aunque desinstales SD Maid. Cualquier buena aplicación para activar o desactivar aplicaciones puede activar las que se desactivaron con SD Maid, y viceversa.

### Eliminar aplicación
{: #delete-app }
Elimina la aplicación y sus datos.


## Ajustes
{: #settings }
### Determinar los tamaños
{: #determine-size }
Se puede desactivar para que la lista de aplicaciones cargue más rápido. Determinar el tamaño de una aplicación lleva mucho tiempo.

### Determinar la actividad
{: #determine-activity }
Se puede desactivar para que la lista de aplicaciones cargue más rápido. Determinar si una aplicación ha estado activa recientemente lleva mucho tiempo.
