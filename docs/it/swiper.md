---
title: "Swiper"
lang: it
translation_key: swiper
permalink: /it/swiper
source_hash: 4c7d8b7a30d3a36e
---
# Swiper
{: #swiper }

Swiper ti aiuta a fare ordine in cartelle specifiche più velocemente che con un file manager. Scorri i file uno alla volta e, con uno swipe, decidi per ciascuno se tenerlo, eliminarlo o saltarlo. Nulla viene eliminato finché non controlli le tue scelte e confermi.

## Avviare una sessione
{: #starting-a-session }

1. Apri Swiper dalla dashboard e tocca `Seleziona cartelle`. Puoi scegliere cartelle, singoli file o entrambi.
2. Facoltativamente imposta un filtro per tipo di file e un ordinamento nella scheda della sessione.
3. Tocca `Scansiona`. La ricerca nelle cartelle include tutte le sottocartelle.
4. Tocca `Inizia a scorrere`.

Puoi anche creare una sessione da [StorageAnalyzer](/it/storageanalyzer): seleziona lì gli elementi e scegli `Crea sessione Swiper`.

Buoni punti di partenza sono il rullino della fotocamera, la cartella Download e gli screenshot.

### Tipi di file
{: #file-types }

`Mostra solo questi tipi di file` limita la sessione a immagini, video, audio, documenti o archivi. Puoi anche inserire estensioni tue, ad es. `apk, apks`. Se non è spuntato nulla, vengono mostrati tutti i file. I file vengono riconosciuti solo in base all'estensione.

### Ordinamento
{: #sort-order }

Per impostazione predefinita i file vengono mostrati partendo dai più vecchi, in base alla data dell'ultima modifica. Puoi anche ordinarli partendo dai più recenti, per nome o partendo dai più grandi.

Il filtro e l'ordinamento non possono essere cambiati dopo che una scansione ha trovato dei file. Per cambiarli, avvia una nuova sessione.

### Scansionare un intero archivio
{: #scanning-a-whole-storage }

Se scegli il livello più alto di un archivio, ad es. tutto l'archivio interno, ogni file che contiene finisce nella sessione, comprese foto e media delle app. SD Maid ti chiede una conferma prima della scansione. Quando in seguito elimini file da una sessione di questo tipo, devi prima spuntare `Capisco che questi file verranno eliminati permanentemente.`

## Scorrimento
{: #swiping }

| Gesto | Azione predefinita |
|---|---|
| Scorri verso destra | Tieni |
| Scorri verso sinistra | Elimina |
| Scorri verso l'alto | Salta |
| Scorri verso il basso | Annulla l'ultima decisione |

I pulsanti in basso fanno lo stesso. Con [Inverti direzioni di scorrimento](#swap-swipe-directions) attivo, sinistra e destra sono invertite.

* **Salta** lascia un file senza decisione. I file saltati ricompaiono dopo tutti gli altri.
* **Annulla** torna indietro tra le tue ultime decisioni, fino a 50.
* **Tieni premuto Salta** per escludere un file dalle future scansioni di Swiper. Così viene creata un'[esclusione](/it/exclusions) percorso e il file viene rimosso dalla sessione.
* I pulsanti sulla scheda aprono il file in un'altra app o mostrano un'anteprima a schermo intero.
* Tocca una miniatura nella striscia per passare a quel file. Scorrerlo di nuovo sostituisce la decisione presa prima.

Puoi uscire in qualsiasi momento. Le tue decisioni vengono salvate con la sessione e puoi continuare più tardi. Quando SD Maid si riavvia, i file che non trova più vengono tolti dalla sessione. Fino ad allora, la sessione potrebbe ancora elencare file che sono stati eliminati o spostati da altro.

## Controllo ed eliminazione
{: #reviewing-and-deleting }

Tocca `Recensione` per vedere tutte le decisioni. Quando non restano file senza decisione, il controllo si apre da solo.

* Cambia le decisioni file per file, oppure tieni premuto per selezionare più file insieme.
* Tocca un file per tornare a quel file.
* `Elimina` mostra quanti file e quanto spazio sono interessati, e in quali cartelle, prima che succeda qualcosa.

L'eliminazione è definitiva. I file non vengono spostati in un cestino e SD Maid non può ripristinarli.

Dopo l'eliminazione, i file tenuti e quelli eliminati lasciano la sessione. I file senza decisione restano per dopo. Quando non resta più nulla, la sessione viene rimossa. Se alcuni file non è stato possibile eliminarli, restano nella sessione e puoi riprovare.

Rimuovere una sessione scarta solo le tue decisioni. Non tocca nessun file.

### Da sapere
{: #things-to-know }

* **Tieni vale solo per la sessione corrente.** Se avvii una nuova sessione sulla stessa cartella, i file tenuti ricompaiono. Per nascondere un file in modo permanente, escludilo (tieni premuto Salta).
* **Una sessione non rileva i nuovi file.** I file aggiunti alla cartella dopo la scansione non compaiono. Avvia una nuova sessione per includerli.
* **Gli archivi comprendono i file APK.**
* **I file nascosti sono inclusi.**

## Versione gratuita
{: #free-version }

Senza l'upgrade, Swiper è limitato a 2 sessioni da 50 file ciascuna. Con un ordinamento impostato, ottieni i primi 50 file in quell'ordine.

## Impostazioni
{: #settings }

### Inverti direzioni di scorrimento
{: #swap-swipe-directions }

Scorri a sinistra per tenere, a destra per eliminare. Disattivato per impostazione predefinita.

### Mostra dettagli file
{: #show-file-details }

Mostra nome, dimensione, data e cartella del file nella schermata di scorrimento. Attivo per impostazione predefinita.

### Feedback aptico
{: #haptic-feedback }

Vibra quando tieni o elimini un file. Attivo per impostazione predefinita.
