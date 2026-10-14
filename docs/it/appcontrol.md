---
title: "AppControl"
lang: it
translation_key: appcontrol
permalink: /it/appcontrol
source_hash: 1a4a7450fc5b86ca
---
# AppControl
{: #appcontrol }
È il punto di riferimento unico per tutte le azioni legate al controllo delle applicazioni installate. Dopo la scansione avrai un elenco ricercabile delle app installate. Le app di sistema non vengono mostrate per impostazione predefinita, devi attivarle nelle impostazioni.
Alcune opzioni potrebbero essere disponibili (possibili) solo se a SD Maid sono stati concessi i permessi di root.

Alcune azioni possono essere eseguite su più app, altre solo su singoli elementi. Per eseguire azioni su più app, tieni premuta un'app e poi tocca per selezionare altre app.

## Azioni
{: #actions }
Alcune di queste azioni sono accessibili solo nei dettagli dell'app, che si aprono toccando una voce dell'elenco.

### Avvia app
{: #run-app }
Avvia l'app. Apre la sua attività principale, di solito la stessa azione che eseguirebbe anche il launcher.

### Arresto forzato
{: #force-stop }
Prova a forzare l'arresto dell'app. Funziona meglio con root o ADB/Shizuku, altrimenti usa il servizio di accessibilità.

### Esporta apk
{: #export-apk }
Prova a copiare il file `.apk` dell'app dall'archivio interno privato alla cartella di download dell'archivio pubblico. Se l'app è crittografata, questa azione richiede il root; le app non crittografate possono essere esportate anche senza root.

### Congela app
{: #freeze-app }
Richiede root o ADB/Shizuku. Disattiva l'app. L'app non sarà visibile e non potrà avviarsi da sola. Per funzionare deve essere riattivata. Non si verifica alcuna perdita di dati e questo stato rimane anche se disinstalli SD Maid. Qualsiasi buona app per attivare/disattivare app può riattivare le app disattivate con SD Maid e viceversa.

### Elimina app
{: #delete-app }
Elimina l'app e i suoi dati.


## Impostazioni
{: #settings }
### Determina dimensioni
{: #determine-size }
Può essere disattivata per velocizzare il caricamento dell'elenco delle app. Determinare quanto è grande un'app richiede molto tempo.

### Controllo attività
{: #determine-activity }
Può essere disattivata per velocizzare il caricamento dell'elenco delle app. Determinare se un'app è stata attiva di recente richiede molto tempo.
