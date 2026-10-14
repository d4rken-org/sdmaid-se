---
title: "Bug"
lang: it
translation_key: bugs
permalink: /it/bugs
source_hash: a218be31a6c4ae63
---
# Bug
{: #bugs }

🐌 🦋 🐛 🐜 🐝 🐞

I bug fanno parte della vita di un'app. Non vogliono farti del male, ma non sono nemmeno qui per restare 😠.

Cosa sono i bug?

* Se l'app va in crash, si tratta ovviamente di un bug
* Se qualcosa si comporta in modo inatteso, potrebbe essere un bug.

In ogni caso controlla di avere l'ultimo aggiornamento, il bug potrebbe essere già stato corretto.

Se il bug non è stato corretto, segnalalo :grin:.

## Segnalare un bug
{: #reporting-a-bug }

Apri una issue su GitHub:
<https://github.com/d4rken-org/sdmaid-se/issues>

* Descrivi cosa ti aspettavi e cosa è successo davvero
* Elenca i passaggi che un'altra persona dovrebbe seguire per riprodurre il problema
* Descrivi la tua configurazione
    * Il tipo di dispositivo (ad es. Pixel 5)
    * La versione di Android (ad es. Android 13.1)
    * Se hai il root, la versione di Magisk

Allega un [log di debug](#debug-log) se riesci a riprodurre il problema. 

Suggerimento: puoi [tenere premuta la voce "Changelog"](https://github.com/d4rken-org/sdmaid-se/pull/940) nelle impostazioni di SD Maid per copiare la versione negli appunti.

## Log di debug
{: #debug-log }

Un log di debug è una registrazione delle azioni di SD Maid. Avvii la registrazione, riproduci il problema, poi la interrompi e puoi condividere un file zip con un resoconto cronologico di ciò che ha fatto SD Maid.

Per avviare la registrazione di un log di debug:
* Apri le impostazioni di SD Maid premendo l'icona delle impostazioni in basso a destra nella dashboard
* Trova la voce `Supporto` nell'elenco delle impostazioni
* Tocca `Registra log di debug` nella schermata del supporto, poi di nuovo `Registra log di debug` nella finestra di dialogo
* Durante la registrazione, la dashboard mostra una scheda `Registrando log di debug`
* **Ora riproduci il problema che vuoi registrare**
* Dopo aver riprodotto il problema, tocca `Interrompi registrazione` su quella scheda della dashboard o nella schermata del supporto. Se la registrazione è molto breve, SD Maid chiede prima conferma; tocca `Interrompi comunque` per interromperla.
* Si apre la schermata `Log di debug`. Quando il file di log è compresso, tocca `Condividi` per inviarlo.

Il log di debug viene registrato finché non lo interrompi; la registrazione continua anche se forzi l'arresto di SD Maid e lo riavvii.

Se stai indagando su problemi legati alle funzioni principali, ad es. il rilevamento del root o le aree di archiviazione rilevate, è utile forzare l'arresto di SD Maid e riavviarlo durante la registrazione, perché alcuni dati vengono inizializzati solo una volta per sessione dell'app.

I file del log di debug sono salvati in `/storage/emulated/0/Android/data/eu.darken.sdmse/cache/debug/logs`.

In immagini:

<a href="/assets/images/bugs/debug-log-1.webp"><img src="/assets/images/bugs/debug-log-1.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-2.webp"><img src="/assets/images/bugs/debug-log-2.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-3.webp"><img src="/assets/images/bugs/debug-log-3.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-4.webp"><img src="/assets/images/bugs/debug-log-4.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-5.webp"><img src="/assets/images/bugs/debug-log-5.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-6.webp"><img src="/assets/images/bugs/debug-log-6.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-7.webp"><img src="/assets/images/bugs/debug-log-7.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-8.webp"><img src="/assets/images/bugs/debug-log-8.webp" width="100" alt=""></a>
