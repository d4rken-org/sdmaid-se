---
title: "Esclusioni"
lang: it
translation_key: exclusions
permalink: /it/exclusions
source_hash: d5c3aea2efa43796
---
# Esclusioni
{: #exclusions }

La funzione di esclusione di SD Maid 2/SE ti permette di definire cartelle o file specifici che l'app deve ignorare durante le scansioni del sistema e le attività di manutenzione. È utile se ci sono dati che vuoi tenere al riparo dai processi dell'app. Impostando delle esclusioni, SD Maid 2/SE salterà queste cartelle o questi file durante attività come Resti delle applicazioni (CorpseFinder), Pulizia App (AppCleaner) e le altre funzioni di gestione del sistema.

Il modo più semplice per aggiungere un'esclusione è direttamente dai risultati. Puoi usare il pulsante "Escludi" sui singoli elementi oppure tenere premute/selezionare più voci e poi scegliere l'opzione "Escludi". Inoltre, le esclusioni possono essere aggiunte manualmente dalle impostazioni dell'app, specificando percorsi o pattern con caratteri jolly per un controllo maggiore.

Le esclusioni possono essere esportate e importate dal menu. L'importazione delle esclusioni è supportata solo tra versioni simili di SD Maid. Se tra la versione di esportazione e quella di importazione ci sono troppi aggiornamenti, potrebbe verificarsi un errore. In questo caso puoi provare a importarle in una versione precedente e poi esportarle di nuovo.

<img src="/assets/images/exclusions/overview-1.webp" width="150">
<img src="/assets/images/exclusions/overview-2.webp" width="150">
<img src="/assets/images/exclusions/overview-3.webp" width="150">
<img src="/assets/images/exclusions/overview-4.webp" width="150">

## Tipi
{: #types }

SD Maid supporta 3 diversi tipi di esclusioni:

### Esclusioni app
{: #app-exclusions }

Le esclusioni app si basano sul nome del pacchetto di ogni app. Possono essere create tramite lo strumento AppControl. Basta scegliere un'app dall'elenco e usare l'azione `Escludi` corrispondente.

### Esclusioni percorso
{: #path-exclusions }

Le esclusioni percorso possono essere create selezionando (tenendo premuti) i percorsi nello strumento StorageAnalyzer.

### Esclusioni segmento 
{: #segment-exclusions }
Le esclusioni segmento sono un tipo di esclusione percorso che non richiede una corrispondenza esatta. Un segmento è una parte di un percorso, ad esempio una cartella. Il percorso `/sdcard/DCIM/Camera` è composto da 3 segmenti. Potresti escludere tutte le foto della fotocamera sia nell'archivio interno (`/storage/emulated/0/DCIM/Camera`) sia nell'archivio esterno (`/sdcard/DCIM/Camera`) creando un'esclusione segmento per `DCIM/Camera`.

Se spunti l'opzione `Consenti risultati parziali`, puoi creare un'esclusione per `DCIM/Cam` che corrisponde sia alla cartella `Cam` sia alla cartella `Camera`.

## Strumenti
{: #tools }

Ogni strumento di SD Maid ha una visione diversa dei dati. Non tutte le esclusioni influiscono allo stesso modo su tutti gli strumenti.

### Resti delle applicazioni
{: #corpsefinder }

Lo strumento Resti delle applicazioni sa cos'è un'"app" e quali file appartengono a un'app. Rispetta tutti i tipi di esclusione. Creare un'esclusione app significa che tutti i file trovati da Resti delle applicazioni che secondo lo strumento appartengono a un'app esclusa verranno esclusi.

Tipi di esclusione supportati: `APP`, `PATH`, `SEGMENTS`

### Pulitore di sistema
{: #systemcleaner }

Lo strumento Pulitore di sistema (SystemCleaner) lavora solo sui dati dei percorsi. Non sa cos'è un'"app" né quali file appartengono a un'app.

Tipi di esclusione supportati: `PATH`, `SEGMENTS`

### Pulizia App
{: #appcleaner }

Lo strumento Pulizia App rispetta tutti i tipi di esclusione. Puoi usare le esclusioni per velocizzare le scansioni se ci sono app con molti file da scansionare.

Tipi di esclusione supportati: `APP`, `PATH`, `SEGMENTS`

### Deduplicatore
{: #deduplicator }

Lo strumento Deduplicatore (Deduplicator) non sa cosa sono le "app" e lavora solo con i dati dei file.

Tipi di esclusione supportati: `PATH`, `SEGMENTS`

### Media Squeeze
{: #media-squeeze }

Media Squeeze lavora solo con i dati dei file. I file esclusi non vengono compressi.

Tipi di esclusione supportati: `PATH`, `SEGMENTS`

### Swiper
{: #swiper }

Swiper lavora solo con i dati dei file. I file esclusi non vengono inclusi nelle nuove sessioni. Tenere premuto `Salta` durante lo scorrimento esclude il file corrente.

Tipi di esclusione supportati: `PATH`, `SEGMENTS`

### StorageAnalyzer
{: #storageanalyzer }

StorageAnalyzer non supporta le esclusioni, perché tutto il contenuto deve essere scansionato per calcolare le dimensioni totali corrette.
