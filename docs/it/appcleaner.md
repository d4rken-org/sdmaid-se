---
title: "Pulizia App (App Cleaner)"
lang: it
translation_key: appcleaner
permalink: /it/appcleaner
source_hash: 505b031f74bf71a2
---
# Pulizia App (App Cleaner)
{: #app-cleaner }

Lo strumento Pulizia App (AppCleaner) conosce le app e trova i file "superflui". Un file è considerato superfluo se può essere eliminato senza effetti negativi e non è necessario oppure verrà ricreato quando servirà. La maggior parte dei tipi di file in una "cache" rientra in questa definizione.

## Impostazioni
{: #settings }

### Includi app di sistema
{: #include-system-apps }

Questa impostazione include le app di sistema nei risultati. Non è attiva per impostazione predefinita perché alcune app di sistema (a seconda della ROM) reagiscono male allo svuotamento della loro cache. Alcune app di sistema inoltre non hanno un pulsante "Svuota cache", il che può bloccare SD Maid quando svuota la cache tramite il servizio di accessibilità.

### Includi gli altri utenti
{: #include-other-users }

Richiede il root. Se attiva, SD Maid cerca anche nelle cartelle degli altri utenti, oltre che in quelle dell'utente che sta eseguendo SD Maid.

### Includi app in esecuzione
{: #include-running-apps }

Indica se includere o meno le app in esecuzione.

### Dimensione minima
{: #minimum-size }

Quanto deve essere grande la cache di un'app per essere elencata. Specificare una dimensione minima è un buon compromesso tra utilità e velocità. Svuotare la cache di un'app che occupa solo pochi KB di solito non porta benefici. Si applica alla quantità totale di cache trovata per un'app (tenendo conto di tutti i filtri attivi).

### Età minima
{: #minimum-age }

Si può usare per eliminare solo i file di cache vecchi. Viene applicata a ogni file o cartella nei risultati.

### Includi cache inaccessibili
{: #include-inaccessible-caches }

A seconda dei permessi disponibili per SD Maid, alcuni file potrebbero non essere direttamente accessibili. Di solito si tratta della cache privata predefinita delle app (ad es. `/data/data/eu.darken.sdmse/cache`). Se questa impostazione è attiva, SD Maid proverà a usare un servizio di accessibilità per aprire le impostazioni di sistema e premere il pulsante `Svuota cache` dell'app interessata.

### Filtro
{: #filter }

Puoi attivare o disattivare diversi filtri per influire sui risultati che lo strumento Pulizia App troverà.

#### Filtri comuni
{: #common-filters }

I filtri comuni riguardano vari tipi di file in tutte le app.

#### Filtri specifici
{: #specific-filters }

I filtri specifici riguardano app specifiche, ad es. WhatsApp o Telegram.

## Risoluzione dei problemi
{: #troubleshooting }
### `com.miui.securitycenter`
{: #commiuisecuritycenter }
#### Permesso `GET_USAGE_STATS` mancante
{: #missing-get_usage_stats-permission }

MIUI e HyperOS usano un'app aggiuntiva chiamata "Security" o "Security Center" (Sicurezza; nome del pacchetto: `com.miui.securitycenter`) per mostrare i dettagli di sistema di un'app. La normale sequenza di azioni per svuotare la cache di un'app è: tocca `Clear data` (Cancella dati), seleziona `Clear cache` (Svuota cache), conferma l'azione.

Se il pulsante 'Clear data' (Cancella dati) è disattivato in grigio per tutte le app e non si può toccare, all'app "Security Center" manca il permesso `GET_USAGE_STATS`. Di norma dovrebbe essere concesso per impostazione predefinita. Non è chiaro come accada, ma la soluzione è concedere di nuovo quel permesso.

Vai su: `System Settings` (Impostazioni di sistema) > `Security and privacy` (Sicurezza e privacy) > `More security and privacy` (Altre impostazioni di sicurezza e privacy) > `Special permissions` (Permessi speciali) > `Usage access` (Accesso ai dati di utilizzo) > trova l'app `Security` (Sicurezza) > concedi il permesso

In alternativa, se hai l'accesso ADB o superiore, esegui il comando `pm grant com.miui.securitycenter android.permission.PACKAGE_USAGE_STATS`

<a href="/assets/images/appcleaner/miui-usage-stats-1.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-1.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-2.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-2.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-3.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-3.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-4.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-4.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-5.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-5.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-6.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-6.webp" width="100px">
</a>
