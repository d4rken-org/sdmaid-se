---
title: "Deduplicatore (Deduplicator)"
lang: it
translation_key: deduplicator
permalink: /it/deduplicator
source_hash: 08f0bb39baf9fb1e
---
# Deduplicatore (Deduplicator)
{: #deduplicator }

Il Deduplicatore (Deduplicator) trova i file duplicati sul tuo dispositivo: copie esatte e, facoltativamente, immagini, video o audio che si assomigliano alla vista o all'ascolto. Li raggruppa in set ed elimina tutti i file di ogni set tranne uno.

La scansione è gratuita. L'eliminazione richiede l'upgrade.

## Metodi di riconoscimento
{: #detection-methods }

Puoi attivare ogni metodo in `Impostazioni > Deduplicatore`.

### Checksum (verifica) del contenuto
{: #content-checksum }

Attivo per impostazione predefinita. Trova i file con esattamente lo stesso contenuto, byte per byte, indipendentemente dal nome. È il metodo sicuro: ogni file in un set di questo tipo è una vera copia.

### Hash percettivo
{: #perceptual-hash }

Disattivato per impostazione predefinita. Trova le immagini che si assomigliano, ad es. la stessa foto salvata in un formato o una dimensione diversi, o con piccole modifiche. Le immagini vengono confrontate in scala di grigi, quindi immagini che differiscono solo nei colori possono corrispondere.

### Impronta multimediale
{: #media-fingerprint }

Disattivata per impostazione predefinita. Trova video e file audio che suonano in modo simile, e video muti che si assomigliano visivamente. Vengono confrontati solo file di durata più o meno uguale.

I file simili non sono identici. Scatti a raffica, versioni modificate o copie ridimensionate possono finire nello stesso set. **Controlla i set trovati con l'hash percettivo o l'impronta multimediale prima di eliminarli.** Ogni file simile mostra quanto corrisponde.

## Cosa viene scansionato
{: #what-gets-scanned }

Per impostazione predefinita il Deduplicatore cerca in tutto l'archivio accessibile: archivio interno, schede SD, archivi USB, `Android/data` e `Android/media`. `Android/obb` non è incluso. Puoi limitare la ricerca in [Percorsi di ricerca](#search-locations).

L'accesso a `Android/data` su Android 13 e versioni successive richiede il [root](/it/setup#root-access) o l'[accesso ADB](/it/setup#shizuku).

Vengono considerati solo i file sopra la [dimensione minima](#minimum-size). Per impostazione predefinita sono inclusi solo i tipi di file comuni, vedi [Salta tipi di file non comuni](#skip-uncommon-file-types).

## Quale file viene mantenuto
{: #which-file-is-kept }

Quando elimini un set, SD Maid mantiene un file ed elimina gli altri. Quale mantenere lo decide la strategia di eliminazione in `Impostazioni > Deduplicatore > Strategia di eliminazione`.

SD Maid controlla ogni regola in ordine. Decide la prima regola che produce un vincitore chiaro. Trascina le regole per riordinarle, tocca una regola per cambiarne la modalità.

| Regola | Predefinito |
|---|---|
| Tipo di duplicato | Mantieni le corrispondenze esatte rispetto ai file simili |
| Posizioni preferite | Mantieni i file nelle cartelle che scegli. Per impostazione predefinita non è impostata nessuna cartella. |
| Indicizzazione multimediale | Mantieni i file che la galleria e le altre app multimediali conoscono |
| Posizione di archiviazione | Mantieni i file nella memoria interna. Schede SD e archivi USB contano come esterni. |
| Profondità cartella | Mantieni file nelle cartelle meno profonde |
| Data di modifica | Mantieni file più vecchi |
| Dimensione file | Mantieni file più grandi |

**Posizioni preferite** aiuta a scegliere quale file mantenere all'interno di un gruppo, insieme alle altre regole. Non sceglie tra i gruppi. In un set che contiene sia copie esatte sia file simili, è Tipo di duplicato a decidere quale gruppo mantenere: per impostazione predefinita ogni file del gruppo dei simili viene eliminato, anche se si trova in una posizione preferita. Per esempio, se la foto della fotocamera e due copie identiche e ridimensionate arrivate da un messenger finiscono in un unico set, l'impostazione predefinita mantiene una copia del messenger ed elimina l'originale della fotocamera. Controlla questi set prima di eliminarli ed [escludi](/it/exclusions) le cartelle i cui file non devono mai essere eliminati.

Non c'è modo di contrassegnare un singolo file come quello da mantenere. Per mantenere un file diverso, cambia la strategia di eliminazione oppure seleziona tu i file da eliminare.

Cambiare la strategia di eliminazione scarta i risultati attuali. Dopo, esegui di nuovo la scansione.

## Eliminazione
{: #deleting }

### Eliminare un intero set
{: #deleting-a-whole-set }

Eliminare un set mantiene un file ed elimina **tutti gli altri file di quel set**. In un set con file simili, sono compresi anche i file che sono solo simili a quello mantenuto, non identici.

Passa l'elenco dei risultati alla visualizzazione a elenco per vedere un'icona di eliminazione su ogni file che verrebbe rimosso eliminando l'intero set. La visualizzazione a griglia predefinita non mostra queste icone.

### Eliminare singoli file
{: #deleting-individual-files }

Seleziona i file tenendoli premuti ed eliminali. In ogni set deve rimanere almeno un file, a meno che tu non attivi [Rendi "Elimina tutto" selezionabile](#make-delete-all-possible).

Nei dettagli del set, la vista `Cartelle` raggruppa i file per cartella. Eliminare una cartella lì elimina i duplicati in quella cartella. Se la cartella contiene tutti i file di un set, viene escluso il file che verrebbe mantenuto eliminando l'intero set. Con [Rendi "Elimina tutto" selezionabile](#make-delete-all-possible) attivo, la conferma ti permette di eliminarli tutti.

### Eliminare dalla dashboard
{: #deleting-from-the-dashboard }

Eliminare dalla scheda del Deduplicatore nella dashboard elimina tutti i file tranne uno in ogni set, dopo una conferma. Se il pulsante principale della dashboard esegue anche il Deduplicatore si imposta in [Singolo tocco](#one-tap).

## Esclusioni
{: #exclusions }

I percorsi esclusi per il Deduplicatore, o in generale, non vengono scansionati. Escludere un set dai risultati esclude ogni file al suo interno, compreso quello che sarebbe stato mantenuto. Vedi [Esclusioni](/it/exclusions).

Il Deduplicatore non può essere eseguito dalla [Pianificazione (Scheduler)](/it/scheduler).

## Singolo tocco (One-Tap)
{: #one-tap }

`Impostazioni > Generale > Strumenti A Singolo Tocco` decide se il pulsante principale della dashboard, la scorciatoia `Scansione + Elimina` e il widget includono il Deduplicatore. È disattivato per impostazione predefinita. La scorciatoia e il widget scansionano ed eliminano senza conferma, usando la tua strategia di eliminazione. Nella dashboard, `Modalità singolo tocco` fa lo stesso, ma se ci sono già risultati in attesa, Elimina chiede comunque prima conferma. Includi il Deduplicatore solo se [Checksum (verifica) del contenuto](#content-checksum) è l'unico metodo di riconoscimento.

## Impostazioni
{: #settings }

### Percorsi di ricerca
{: #search-locations }

Le cartelle in cui cercare. Per impostazione predefinita tutte le aree accessibili. Tieni premuto per reimpostarle.

### Strategia di eliminazione
{: #deletion-strategy }

Quale file di un set viene mantenuto, vedi [Quale file viene mantenuto](#which-file-is-kept).

### Rendi "Elimina tutto" selezionabile
{: #make-delete-all-possible }

Mostra le opzioni per eliminare tutti i file di un set, così che non rimanga nessuna copia. Disattivato per impostazione predefinita.

### Dimensione minima
{: #minimum-size }

Salta i file sotto questa dimensione. Predefinito 512 KB.

### Salta tipi di file non comuni
{: #skip-uncommon-file-types }

Include solo immagini, video, file audio, documenti, archivi e file APK comuni. Attivo per impostazione predefinita. Il tipo viene determinato dall'estensione del file.

### Metodo di riconoscimento
{: #detection-method }

Attiva [Checksum (verifica) del contenuto](#content-checksum), [Hash percettivo](#perceptual-hash) e [Impronta multimediale](#media-fingerprint).
