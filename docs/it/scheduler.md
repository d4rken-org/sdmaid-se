---
title: "Pianificazione (Scheduler)"
lang: it
translation_key: scheduler
permalink: /it/scheduler
source_hash: 74e1452091eaf142
---
# Pianificazione (Scheduler)
{: #scheduler }

La funzione **Pianificazione (Scheduler)** di SD Maid 2 ti permette di automatizzare le attività di manutenzione a intervalli specifici, così che il tuo dispositivo resti ottimizzato senza interventi manuali. Supporta più strumenti, come **Pulizia App (AppCleaner)**, **Resti delle applicazioni (CorpseFinder)** e **Pulitore di sistema (SystemCleaner)**, che possono essere configurati per essere eseguiti a intervalli regolari o a orari specifici.

<img src="/assets/images/scheduler/overview-1.webp" width="150">
<img src="/assets/images/scheduler/overview-2.webp" width="150">
<img src="/assets/images/scheduler/overview-3.webp" width="150">
<img src="/assets/images/scheduler/overview-4.webp" width="150">
<img src="/assets/images/scheduler/overview-5.webp" width="150">
<img src="/assets/images/scheduler/overview-6.webp" width="150">


## Come configurare una pianificazione
{: #how-to-set-up-a-schedule }

1. Vai su **Impostazioni** e seleziona **Pianificazione**.
2. Tocca il pulsante **+** per creare una nuova pianificazione o gestire quelle esistenti.
3. Scegli quali strumenti (ad es. **Pulizia App**, **Resti delle applicazioni**, **Pulitore di sistema**) devono essere eseguiti secondo la pianificazione.
4. Imposta **Ripeti tra quanti giorni** (ad es. ogni 3 giorni) e il **Tempo approssimativo** di esecuzione.
5. Attiva la pianificazione con l'interruttore.

## Modificare o eliminare le pianificazioni
{: #editing-or-deleting-schedules }

1. Vai alle impostazioni di **Pianificazione** per gestire le tue pianificazioni.
2. Puoi modificare le pianificazioni esistenti, regolarne gli intervalli o eliminarle se non ti servono più.

Una volta attiva una pianificazione, SD Maid eseguirà le attività scelte agli orari specificati.

## Opzioni aggiuntive
{: #additional-options }

Dal menu principale delle impostazioni, in `Impostazioni > Pianificazione`, ci sono alcune opzioni in più.

- **Salta se la batteria è scarica**: se il dispositivo è in modalità di risparmio energetico, le attività pianificate verranno saltate.
- **Salta quando non in carica**: le attività pianificate verranno eseguite solo quando il dispositivo è in carica, per evitare di scaricare la batteria.
- **Comandi post-pianificazione**: puoi impostare comandi della shell da eseguire al termine delle attività pianificate.
- **Servizio di accessibilità**: per impostazione predefinita la pianificazione salta le funzioni basate sul servizio di accessibilità, perché non funzionano quando il dispositivo è bloccato. Se attivi questa opzione senza apportare altre modifiche al dispositivo, la pianificazione fallirà. Quindi, a meno che tu non sappia cosa stai facendo, lasciala disattivata. È utile solo in casi particolari, come un dispositivo Android TV sempre acceso.

## Risoluzione dei problemi
{: #troubleshooting }

### La pianificazione non viene eseguita 
{: #scheduler-is-not-executing }

Distingui tra la pianificazione che non viene eseguita affatto e le operazioni eseguite che non producono i risultati desiderati. Puoi controllare la cronologia delle esecuzioni nella [sezione Cronologia](/it/stats) di SD Maid. Se la pianificazione è stata eseguita ma non è riuscita, dovrebbe esserci un errore.

Se la pianificazione non è stata eseguita affatto, è possibile che il sistema abbia impedito l'avvio di SD Maid per risparmiare batteria. Dopo aver attivato la tua prima pianificazione, avresti dovuto vedere un avviso intitolato "Consiglio" con un'azione "Correggi", che ti chiedeva di disattivare le ottimizzazioni della batteria. Se hai chiuso per sbaglio quell'avviso, devi andare manualmente nelle impostazioni di sistema per SD Maid e verificare se SD Maid viene "ottimizzato" dal sistema.

### Servizio di accessibilità
{: #accessibility-service }
Le eliminazioni basate sull'accessibilità, ad es. Pulizia App che elimina gli "elementi inaccessibili", non funzionano tramite la pianificazione. Quando la pianificazione viene eseguita, il dispositivo di solito è bloccato: proprio come tu non puoi toccare nulla su uno schermo spento o bloccato, non può farlo nemmeno SD Maid.

Per impostazione predefinita la pianificazione non esegue operazioni basate sull'accessibilità. Puoi però attivare l'opzione "Servizio di accessibilità" nelle impostazioni della pianificazione, se sai cosa stai facendo.

Soluzioni alternative: esegui il root del dispositivo (app dell'utente e di sistema) oppure installa Shizuku (solo app dell'utente). Entrambe le modifiche permettono a SD Maid di eseguire operazioni simili senza usare il servizio di accessibilità.
