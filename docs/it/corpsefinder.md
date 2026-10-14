---
title: "Resti delle applicazioni (Corpse Finder)"
lang: it
translation_key: corpsefinder
permalink: /it/corpsefinder
source_hash: 9e16286f0fda1dc8
---
# Resti delle applicazioni (Corpse Finder)
{: #corpse-finder }

Lo strumento `Resti delle applicazioni` (`CorpseFinder`) trova i file che appartengono ad app non più installate.


## Livello di rischio
{: #risklevel }

Per impostazione predefinita tutte le opzioni del livello di rischio sono disattivate. Attivale solo se sai cosa stai facendo e, prima di eliminare qualcosa, controlla i risultati.
Attivare un'opzione del livello di rischio aumenta la probabilità che vengano eliminati file che non volevi eliminare.

I livelli di rischio si applicano soprattutto alle posizioni in whitelist, come la radice dell'archivio pubblico (`/storage/emulated/0`). Queste posizioni non seguono uno schema di denominazione comune e SD Maid può determinarne il proprietario solo tramite un database gestito manualmente.


### Elimina residui importanti
{: #include-desirable-remnants }

I residui importanti sono file con proprietari noti che non sono installati, ma che SD Maid contrassegna internamente come `KEEPER`. Di solito sono file creati dall'utente, ad es. le foto che hai scattato.

### Includi nomi comuni
{: #include-common-names }

Alcune app usano semplicemente nomi molto comuni. Esistono probabilmente centinaia di app per prendere appunti che creano una cartella `/sdcard/Notes` in cui salvare i dati. SD Maid potrebbe conoscere una di queste app e questa potrebbe non essere installata sul tuo telefono, ma SD Maid non può essere sicuro che sul telefono non ci sia ancora un'altra app che usa quella cartella, senza che SD Maid sappia che anche quest'app la usa. 

## Filtro
{: #filter }

Ogni filtro è responsabile di una specifica area di dati. Attivando un filtro, Resti delle applicazioni cercherà anche in quell'area aggiuntiva.

### Scheda SD
{: #sdcard }

* Di solito è dove l'utente mette i propri file
* Archivio pubblico (sia integrato sia rimovibile), escluse specifiche sottocartelle in `Android/data`.
* I file qui non seguono uno schema di denominazione.

Esempi:

```bash
/storage/emulated/0/SDMaid
/storage/DEAD-BEEF/SDMaid
/sdcard/SDMaid
```

### Media pubblici
{: #public-media }

* Contiene i media pubblici delle app, ad es. i media ricevuti tramite WhatsApp. Le cartelle prendono il nome dai nomi dei pacchetti delle app.
* Sottocartella dell'archivio pubblico con un significato speciale per il sistema operativo. 

Esempi:

```bash
/storage/emulated/0/Android/media/eu.darken.sdmse
/storage/DEAD-BEEF/Android/media/eu.darken.sdmse
/sdcard/Android/media/eu.darken.sdmse
```

### Dati pubblici dell'app
{: #public-app-data }

* File aggiuntivi delle app.
* Le cartelle prendono il nome dai nomi dei pacchetti delle app.
* Sottocartella dell'archivio pubblico con un significato speciale per il sistema operativo.

Esempi:

```bash
/storage/emulated/0/Android/data/eu.darken.sdmse
/storage/DEAD-BEEF/Android/data/eu.darken.sdmse
/sdcard/Android/data/eu.darken.sdmse
```

### Risorse OBB
{: #obb-resources }

* Di solito file di risorse aggiuntivi per i giochi.
* Cartelle e file prendono il nome dai nomi dei pacchetti delle app.
* Sottocartella dell'archivio pubblico con un significato speciale per il sistema operativo.

Esempi:

```bash
/storage/emulated/0/Android/obb/eu.darken.sdmse
/storage/DEAD-BEEF/Android/obb/eu.darken.sdmse
/sdcard/Android/obb/eu.darken.sdmse
```

### Dati privati dell'app
{: #private-app-data }

* Cartelle e file prendono il nome dai nomi dei pacchetti delle app.
* Sottocartella dell'archivio pubblico con un significato speciale per il sistema operativo.

Esempi:

```bash
/data/data/eu.darken.sdmse
```

### Dalvik cache
{: #dalvik-cache }

* Contiene versioni ottimizzate del codice delle app per un'esecuzione più veloce.
* Si trova nella partizione di sistema, spesso in cartelle specifiche per l'architettura.
* I file vengono nominati usando il nome del pacchetto dell'app.

Esempi:

```bash
/data/dalvik-cache/arm64/data@app@eu.darken.sdmse@base.apk@classes.dex
/data/dalvik-cache/arm/data@app@eu.darken.sdmse@base.apk@classes.dex
```

### Librerie app
{: #app-libraries }

* Contiene le librerie native usate dalle app, come i file `.so`.
* Di solito si trova nella cartella dei dati dell'app, sotto una cartella `lib`.
* I file vengono nominati in base all'architettura e ai requisiti della libreria.

Esempi:

```bash
/data/app/eu.darken.sdmse-1/lib/arm64/libexample.so
/data/app/eu.darken.sdmse-2/lib/arm/libexample.so
```

### Sorgenti app
{: #app-sources }

* Si riferisce ai file APK che costituiscono il codice sorgente delle app.
* Sono salvati in cartelle specifiche per le app di sistema, dell'utente o del produttore.
* I file prendono il nome dal nome del pacchetto e dalla versione dell'app.

Esempi:

```bash
/data/app/eu.darken.sdmse-1/base.apk
/system/app/ExampleApp/ExampleApp.apk
```

### Sorgenti app private
{: #private-app-sources }

* Simili alle sorgenti app, ma salvate in cartelle protette per una maggiore sicurezza.
* Accessibili solo dal sistema o dall'app interessata.
* I file prendono il nome dal nome del pacchetto dell'app.

Esempi:

```bash
/data/app-private/eu.darken.sdmse/base.apk
```
