---
title: "Pulitore di sistema (System Cleaner)"
lang: it
translation_key: systemcleaner
permalink: /it/systemcleaner
source_hash: 1ced6df9b00f1e05
---
# Pulitore di sistema (System Cleaner)
{: #system-cleaner }

Lo strumento Pulitore di sistema (SystemCleaner) è sostanzialmente un motore di ricerca generico. Esplora tutto l'archivio accessibile e confronta i file e le cartelle trovati con un insieme di filtri.

A differenza di altri strumenti come Resti delle applicazioni (CorpseFinder) o Pulizia App (AppCleaner), non conosce le app installate. Lavora esclusivamente su percorsi e attributi dei file. Per questo motivo al Pulitore di sistema si possono applicare solo esclusioni percorso e segmento.

Ogni file trovato passa attraverso i filtri attivi e viene attribuito al primo filtro a cui corrisponde.

## Impostazioni
{: #settings }

### Filtri generici
{: #general-filters }

I filtri generici cercano **in tutte le aree di archiviazione accessibili** determinati tipi di file che possono trovarsi ovunque sul dispositivo.

| Filtro | Descrizione |
|--------|-------------|
| **Cartelle vuote** | Cartelle vuote da tutto il dispositivo. Le cartelle annidate possono richiedere più passaggi di scansione. |
| **Screenshot** | Screenshot più vecchi di un'età configurabile, da più app e posizioni. |
| **File cestinati** | File nei cestini di varie app e posizioni, contrassegnati per l'eliminazione ma non ancora rimossi definitivamente. |
| **APK superflui** | File di installazione (APK) di versione precedente o uguale a quella delle app già installate. |
| **Cartelle LOST.DIR** | File temporanei creati durante il trasferimento di dati tra dispositivi Android. |
| **File di Linux** | File e cartelle creati quando l'archivio è stato collegato a un computer Linux. |
| **File di Mac** | File e cartelle creati quando si sfoglia l'archivio con l'app Finder di Apple (ad es. `.DS_Store`). |
| **File di Windows** | Dati che Esplora file di Windows può creare sugli archivi collegati (ad es. `Thumbs.db`). |
| **Anteprime** | Anteprime in cache di immagini e video (ad es. cartelle `.thumbnails`). |
| **File temporanei di sistema** | File temporanei legati al sistema, di solito cache di breve durata per operazioni in corso. |
| **File di analisi** | Dati legati ad analisi statistiche e tracciamento dei bug. |
| **File di pubblicità** | File creati per o dalle pubblicità. |
| **File di log del sistema** | File di log del sistema e dei servizi di sistema (ad es. `*.log`) in varie posizioni. |

### Filtri specifici
{: #specific-filters }

I filtri specifici riguardano **percorsi fissi e noti** del dispositivo in cui vengono salvati determinati tipi di dati.

| Filtro | Descrizione |
|--------|-------------|
| **Residui dei crash di sistema** | Se un processo nativo va in crash (ad es. un gioco), qui vengono creati i file "tombstone". |
| **Statistiche di utilizzo** | File legati alle statistiche di utilizzo delle applicazioni. |
| **Errori ANR** | Tracce di eventi "Application Not Responding" (l'applicazione non risponde). |
| **File temporanei locali** | File temporanei legati al sistema (ad es. di Android Studio). |
| **File di log di sistema (datalogger)** | File di log specifici dei servizi di sistema. |
| **Cache di download** | Cartella di cache di sistema usata per vari scopi (ad es. aggiornamenti OTA). |
| **Casella di rilascio dei log** | Un'area in cui il sistema deposita i file di log. |
| **Cache installatore** | Dati in cache relativi alle app installate, usati dalla gestione dei pacchetti del sistema. |
| **Attività recenti** | Dati legati alla cronologia delle attività nel selettore delle attività del sistema. |

### Filtri personalizzati
{: #custom-filters }

Oltre ai filtri generici forniti dall'app, puoi creare filtri personalizzati con criteri tuoi. I filtri personalizzati si creano da **Impostazioni > Pulitore di sistema > Gestisci filtri personalizzati**.

> **Attenzione**: i filtri personalizzati possono essere rischiosi. Controlla sempre i risultati della scansione prima di eliminare!

#### Criteri disponibili
{: #available-criteria }

| Criterio | Descrizione |
|-----------|-------------|
| **Etichetta filtro** | Un nome per il tuo filtro |
| **Aree di dati** | Posizioni di archiviazione da scansionare (lascia vuoto per tutte le aree accessibili) |
| **Restrizioni sul tipo di file** | File, cartelle o collegamenti simbolici da considerare (lascia vuoto per tutti) |
| **Criteri di percorso** | Regole per la corrispondenza dei percorsi dei file |
| **Esclusioni** | Percorsi da escludere dalla corrispondenza |
| **Criteri di denominazione** | Regole per la corrispondenza dei nomi dei file |
| **Dimensione minima** | Corrispondono solo i file più grandi di questo valore |
| **Dimensione massima** | Corrispondono solo i file più piccoli di questo valore |
| **Età minima** | Corrispondono solo i file più vecchi di questo valore |
| **Età massima** | Corrispondono solo i file più recenti di questo valore |
| **Regex del percorso** | Avanzato: espressioni regolari per la corrispondenza dei percorsi |

#### Modalità dei criteri di percorso
{: #path-criteria-modes }

| Modalità | Descrizione | Esempio |
|------|-------------|---------|
| **Inizia con** | Il percorso inizia con la parola chiave | `Downloads/temp` corrisponde a `/sdcard/Downloads/temp/file.txt` |
| **Contiene** | Il percorso contiene la parola chiave | `cache` corrisponde a `/sdcard/app/cache/data.bin` |
| **Termina con** | Il percorso termina con la parola chiave | `backup/old` corrisponde a `/sdcard/backup/old` |
| **È uguale a** | Il percorso corrisponde esattamente alla parola chiave | `Download/temp` corrisponde solo a quel percorso esatto |

#### Modalità dei criteri di denominazione
{: #name-criteria-modes }

| Modalità | Descrizione | Esempio |
|------|-------------|---------|
| **Inizia con** | Il nome del file inizia con la parola chiave | `temp_` corrisponde a `temp_file.txt` |
| **Contiene** | Il nome del file contiene la parola chiave | `.bak` corrisponde a `document.bak.old` |
| **Termina con** | Il nome del file termina con la parola chiave | `.tmp` corrisponde a `cache.tmp` |
| **È uguale a** | Il nome del file corrisponde esattamente | `thumbs.db` corrisponde solo a `thumbs.db` |

Per impostazione predefinita tutte le corrispondenze **non distinguono tra maiuscole e minuscole**.

#### Aree di dati
{: #data-areas }

Aree comuni che puoi selezionare:

| Area | Descrizione |
|------|-------------|
| **SDCARD** | Archivio principale (interno o scheda SD) |
| **PUBLIC_MEDIA** | Cartelle multimediali (DCIM, Pictures, Music, ecc.) |
| **PUBLIC_DATA** | Dati delle app condivisi |
| **PRIVATE_DATA** | Archivio privato delle app (richiede il root) |
| **DOWNLOAD_CACHE** | Cache di download del sistema (richiede il root) |
| **PORTABLE** | Archivio rimovibile (USB/microSD) |

#### Suggerimenti
{: #tips }

- **Prestazioni**: definisci solo i criteri necessari. Troppi pattern regex rallentano la scansione.
- **Più criteri**: tutti i criteri specificati devono corrispondere (logica AND).
- **Pressione prolungata**: tieni premute le voci per altre opzioni.
- **Prova**: usa l'anteprima della scansione prima di attivare l'eliminazione.
- **Importazione/esportazione**: i filtri possono essere esportati e condivisi con altri utenti.

#### Opzioni obsolete
{: #deprecated-options }

L'opzione `rootOnly` di SD Maid 1 **non è più disponibile**. Usa invece **Aree di dati** per controllare quali posizioni di archiviazione vengono scansionate.
