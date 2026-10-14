---
title: "Storage Analyzer"
lang: it
translation_key: storageanalyzer
permalink: /it/storageanalyzer
source_hash: 2dbf8557900cc687
---
# Storage Analyzer
{: #storage-analyzer }

StorageAnalyzer risponde alla domanda "Che cosa occupa così tanto spazio nel mio archivio?"

Può sembrare un file manager, ma non lo è. Tutto è ordinato per dimensione, così puoi seguire gli elementi più grandi fino a ciò che sta davvero riempiendo il tuo archivio.

## Archivi
{: #storages }

La prima schermata mostra ogni archivio del dispositivo:

* **Archivio principale**: l'archivio integrato del dispositivo, comprese le app e i dati di sistema.
* **Archivio secondario**: di solito una scheda SD rimovibile.
* **Memoria esterna**: ad es. una chiavetta collegata tramite USB.

Ogni scheda mostra lo spazio libero e quello occupato, e quanto è cambiato l'archivio negli ultimi 7 giorni, vedi [Tendenza archiviazione](#storage-trend).

## Categorie
{: #categories }

Aprire un archivio lo scansiona e suddivide lo spazio occupato in categorie:

* **App**: le app installate con il loro codice, dati, cache e media.
* **File Utente**: foto, video, download e qualsiasi altro dato che non appartiene a un'app specifica.
* **Dati di sistema**: il sistema operativo e tutto ciò che non è stato possibile assegnare altrove.
* **Altri utenti**: app e file di altri utenti o profili di lavoro su questo dispositivo.

Dati di sistema e Altri utenti esistono solo per l'archivio principale.

### App
{: #apps }

Le app sono ordinate per dimensione e possono essere cercate per nome. Anche le app di sistema possono essere elencate qui, compresi i loro dati. Per le app di sistema senza un aggiornamento installato, il codice dell'app integrato conta come Dati di sistema. Aprire un'app mostra come viene usato il suo spazio:

* **Codice app**: l'app stessa. Disinstallare o archiviare l'app libera questo spazio.
* **Dati app**: dati, impostazioni e cache.
* **App media**: i file multimediali di questa app, ad es. le foto ricevute su WhatsApp.
* **File extra di app**: dati che l'app ha messo fuori dalle proprie cartelle, ad es. foto o download in una cartella di primo livello.

`Visualizza impostazioni` apre la pagina delle impostazioni di sistema dell'app.

### Perché i numeri non corrispondono alle impostazioni di Android
{: #why-the-numbers-dont-match-androids-settings }

Le categorie di StorageAnalyzer non corrispondono esattamente alla suddivisione nelle impostazioni di Android.

**Dati di sistema** è ciò che resta dello spazio occupato dopo aver conteggiato App, File Utente e Altri utenti. Tutto ciò che SD Maid non può vedere o assegnare finisce lì: aree di sistema protette, archivi di altri utenti che non è stato possibile misurare, l'overhead del file system e la differenza tra la dimensione dichiarata e quella reale dell'archivio.

Senza i permessi [Inventario App](/it/setup#app-inventory) e [Statistiche di utilizzo](/it/setup#usage-statistics), le app non possono essere misurate. Il loro spazio compare quindi come Dati di sistema.

## Accesso
{: #access }

Ciò che StorageAnalyzer può vedere dipende dall'accesso di cui dispone SD Maid:

* **Senza root**, il codice delle app e i dati privati delle app vengono mostrati solo come totali e non è possibile sfogliarli. Su Android 13 e versioni successive, anche `Android/data` richiede il root o l'accesso ADB.
* **Con il [root](/it/setup#root-access) o l'[accesso ADB](/it/setup#shizuku)**, puoi sfogliare i Dati di sistema. Il root permette anche di sfogliare il codice delle app e i dati privati delle app, e di misurare completamente gli altri utenti.
* **Senza il permesso Inventario App**, i file non possono essere associati alle app a cui appartengono. I File Utente vengono quindi mostrati in sola lettura: puoi vedere le dimensioni, ma eliminazione, esclusione, creazione di filtri e sessioni Swiper sono disattivate.

Gli elementi a cui SD Maid non può accedere mostrano `Accesso non disponibile` quando li tocchi.

Le cartelle con più di 100.000 elementi vengono solo sommate e non possono essere sfogliate.

## Azioni
{: #actions }

Tocca un file per aprirlo in un'altra app. Tieni premuti gli elementi per selezionarli, poi:

* **Elimina**: elimina gli elementi selezionati dopo una conferma. Gratuito.
* **Escludi**: crea un'[esclusione](/it/exclusions) per i percorsi selezionati. Queste esclusioni valgono per tutti gli strumenti. StorageAnalyzer stesso non usa le esclusioni, perché deve vedere tutto per calcolare i totali corretti.
* **Filtro**: crea un [filtro personalizzato del Pulitore di sistema (SystemCleaner)](/it/systemcleaner#custom-filters) per gli elementi selezionati. Richiede l'upgrade.
* **Crea sessione Swiper**: avvia una sessione [Swiper](/it/swiper) con gli elementi selezionati.

I Dati di sistema e i contenuti degli altri utenti possono solo essere sfogliati. Lì non è possibile eliminare.

## Tendenza archiviazione
{: #storage-trend }

SD Maid registra lo spazio libero dei tuoi archivi a intervalli di qualche ora e dopo ogni pulizia. `Tendenza archiviazione` lo mostra come grafico, con indicatori di quanto spazio ha liberato ogni pulizia.

* Le schede degli archivi mostrano quanto è cambiato ogni archivio negli ultimi 7 giorni.
* La dashboard mostra la variazione complessiva di tutti gli archivi oppure, quando l'archivio si sta riempiendo, una stima dei giorni rimanenti.
* Per quanto tempo vengono conservati gli snapshot si imposta in `Impostazioni > Cronologia > Cronologia dello spazio di archiviazione`. Predefinito 90 giorni, fino a 365.

Il grafico Tendenza archiviazione richiede l'upgrade. La variazione a 7 giorni sulle schede degli archivi e nella dashboard e la stima dei giorni rimanenti sono gratuite.

## Impostazioni
{: #settings }

### Soglia di spazio di archiviazione basso
{: #low-storage-threshold }

Quando lo spazio libero scende sotto questo valore, SD Maid considera l'archivio in esaurimento. Predefinito `Automatico`: il 5% della dimensione dell'archivio, al massimo 2 GB.

### Avviso di spazio basso
{: #low-space-warning }

Richiede l'upgrade. Avvisa quando lo spazio libero nell'archivio principale raggiunge la soglia di spazio di archiviazione basso. Con almeno 5 giorni di cronologia costante può avvisare anche prima: quando l'archivio è destinato a raggiungere la soglia entro 14 giorni e lo spazio libero è già al massimo il 20% dell'archivio, con un limite di 25 GB. Disattivato per impostazione predefinita.
