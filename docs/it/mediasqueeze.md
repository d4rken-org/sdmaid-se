---
title: "Media Squeeze"
lang: it
translation_key: mediasqueeze
permalink: /it/mediasqueeze
source_hash: 1d878fd5a77634c5
---
# Media Squeeze
{: #media-squeeze }

Media Squeeze comprime foto e video in modo che occupino meno spazio. Sacrifichi un po' di qualità in cambio dello spazio che recuperi.

**La compressione riduce la qualità in modo permanente e non può essere annullata.** Il file originale viene sostituito e non ne viene conservata alcuna copia. Controlla le impostazioni e l'anteprima prima di iniziare.

La scansione è gratuita. La compressione richiede l'upgrade.

## Come funziona
{: #how-it-works }

1. Scegli le cartelle in cui cercare. Non c'è una posizione predefinita: scegli per esempio la cartella della fotocamera.
2. Imposta la qualità di compressione e l'età minima.
3. Avvia la scansione. Ogni risultato mostra la sua dimensione e un risparmio stimato.
4. Tocca un singolo elemento, selezionane diversi tenendoli premuti oppure usa `Comprimi tutto`. Prima compare una conferma con la dimensione totale e il risparmio stimato.

Se la selezione contiene solo foto o solo video, `Visualizza confronto` nella conferma mostra l'originale e la versione compressa uno accanto all'altra. Per i video il confronto è un singolo fotogramma ed è solo approssimativo.

### Cosa succede a un file
{: #what-happens-to-a-file }

* Viene ricodificato nel suo stesso formato: un JPEG resta un JPEG, un WebP resta un WebP, un MP4 resta un MP4.
* La risoluzione delle immagini resta la stessa, tranne che per le [foto molto grandi](#compress-very-large-photos).
* Nei video viene ricodificata solo la traccia video, con un bitrate più basso. La traccia audio viene copiata senza modifiche.
* Data, posizione, fotocamera e gli altri metadati EXIF delle foto vengono mantenuti. Se SD Maid non riesce a leggere o a conservare i metadati EXIF di una foto, lascia la foto invariata.
* I metadati XMP non vengono mantenuti, ad es. le informazioni di foto sferiche e panorami o le valutazioni degli editor di foto.
* La data di modifica del file viene mantenuta, così le gallerie continuano a ordinarlo nella stessa posizione.
* Se la versione compressa non è più piccola, l'originale non viene toccato.

Il risparmio stimato è un'ipotesi approssimativa basata sul tipo di file e sulla qualità, non una misurazione. Il risparmio reale può essere diverso.

### Posizioni
{: #locations }

Media Squeeze ha bisogno di accesso diretto ai file e di accesso in scrittura alle loro cartelle. Non usa il root né ADB. Alcune posizioni scelte tramite il selettore di cartelle di Android, ad es. le chiavette USB, non possono essere usate e vengono rimosse dalla selezione.

## File già compressi
{: #previously-compressed-files }

SD Maid ricorda quali file ha già compresso, e quali non sono diventati più piccoli, e li salta nelle scansioni successive. Riconosce un file dal suo contenuto, quindi funziona anche dopo che il file è stato rinominato o spostato. Comprimere di nuovo un file costa altra qualità e fa risparmiare poco.

La cronologia è conservata sul tuo dispositivo e fa parte del backup delle impostazioni di SD Maid. Cancellarla rende di nuovo idonei i file già compressi, a meno che non abbiano un marcatore EXIF e [Scrivi marcatore EXIF](#write-exif-marker) sia attivo. Cancellarla non modifica alcun file.

## Foto saltate per impostazione predefinita
{: #photos-that-are-skipped-by-default }

Alcune foto contengono dati che la compressione non può mantenere. SD Maid le salta se rileva quei dati, a meno che tu non attivi l'impostazione corrispondente. Il rilevamento non è completo: le foto in formati che non riconosce vengono compresse e perdono quei dati.

* **Foto HDR e di profondità**: le foto HDR diventano foto normali e le foto ritratto perdono i dati su cui si basa la sfocatura dello sfondo.
* **Motion Photo**: l'immagine fissa viene mantenuta, il video clip viene rimosso.
* **Foto molto grandi** (larghe o alte 8192 pixel o più): larghezza e altezza vengono dimezzate, e se necessario di nuovo dimezzate, finché entrambe non sono sotto gli 8192 pixel. Un panorama largo 20.000 pixel diventa largo 5.000 pixel.

I file HEIC che contengono più immagini vengono sempre saltati.

Il riepilogo della scansione conta le foto saltate per dati HDR o di profondità, le Motion Photo, quelle saltate per le dimensioni molto grandi e i file non accessibili. I file saltati perché già compressi non vengono contati, e nemmeno i file HEIC con più immagini, a meno che non abbiano anche dati HDR o di profondità.

## Impostazioni
{: #settings }

La qualità di compressione, i percorsi di ricerca e l'età minima si impostano nella schermata di Media Squeeze prima della scansione. Tutto il resto si trova in `Impostazioni > Media Squeeze`.

### Qualità di compressione
{: #compression-quality }

Predefinito 80. Una qualità più bassa significa file più piccoli ma qualità visiva ridotta. Sotto 40 le immagini possono diventare inutilizzabili. Da 95 in su c'è poco da risparmiare.

### Età minima
{: #minimum-age }

Include solo i file più vecchi di questo valore. Predefinito 90 giorni, in base alla data di modifica del file.

### Dimensione minima del file
{: #minimum-file-size }

Salta i file più piccoli di questo valore. Predefinito 512 KB.

### JPEG
{: #jpeg }

Include le immagini JPEG (`.jpg`, `.jpeg`). Attivo per impostazione predefinita.

### WebP
{: #webp }

Include le immagini WebP (`.webp`). Attivo per impostazione predefinita. I file WebP animati non vengono rilevati e perderebbero l'animazione.

### HEIC/HEIF
{: #heicheif }

Include le immagini HEIC/HEIF (`.heic`, `.heif`). Disattivato per impostazione predefinita. Richiede Android 9 o versioni successive. Per i file HEIC non è possibile conservare le gain map HDR, i dati di profondità e le parti Live/Motion Photo.

### Video MP4
{: #mp4-video }

Include i video MP4 (`.mp4`). Disattivato per impostazione predefinita. Comprimere un video richiede spazio libero pari a circa il 110% della sua dimensione sullo stesso archivio.

### Comprimi foto HDR e di profondità
{: #compress-hdr--depth-photos }

Vedi [Foto saltate per impostazione predefinita](#photos-that-are-skipped-by-default). Disattivato per impostazione predefinita.

### Comprimi le Motion Photo
{: #compress-motion-photos }

Mantiene l'immagine fissa e rimuove il video clip. Disattivato per impostazione predefinita.

### Comprimi le foto molto grandi
{: #compress-very-large-photos }

Include le foto larghe o alte 8192 pixel o più e ne riduce la risoluzione, vedi [Foto saltate per impostazione predefinita](#photos-that-are-skipped-by-default). Disattivato per impostazione predefinita.

### Salta le già compresse
{: #skip-previously-compressed }

Salta i file che SD Maid ha già compresso. Attivo per impostazione predefinita.

### Scrivi marcatore EXIF
{: #write-exif-marker }

Aggiunge un marcatore al commento utente EXIF della foto, così SD Maid la riconosce come compressa anche dopo che la cronologia della compressione è stata cancellata. SD Maid controlla il marcatore solo mentre questa impostazione è attiva. Disattivato per impostazione predefinita. Vale solo per JPEG e WebP.

### Cronologia compressione
{: #compression-history }

Mostra quanti file ci sono nella cronologia. Toccala per cancellare la cronologia.

## Esclusioni
{: #exclusions }

Media Squeeze salta i percorsi [esclusi](/it/exclusions) in generale. Puoi anche escludere risultati dall'elenco dei risultati, il che riguarda solo Media Squeeze.

Media Squeeze non può essere eseguito dalla [Pianificazione (Scheduler)](/it/scheduler).
