---
title: "Configurare SD Maid SE"
lang: it
translation_key: setup
permalink: /it/setup
source_hash: a14003a95dd2336d
---
# Configurare SD Maid SE
{: #setting-up-sd-maid-se }
Informazioni sui diversi passaggi di configurazione di SD Maid SE.

SD Maid si adatta all'accesso disponibile. Anche se tutti i permessi sono facoltativi, se non concedi alcun permesso la maggior parte delle funzioni non funzionerà. SD Maid funziona al meglio quando tutti gli elementi della configurazione sono completati.

SD Maid proverà a completare automaticamente alcuni passaggi della configurazione se è disponibile il [root](#root-access) o l'[accesso ADB](#shizuku).

Alcuni permessi si sovrappongono o ne comprendono altri, ad es. puoi concedere a SD Maid sia l'accesso root sia l'accesso ADB. Per ogni azione SD Maid prova sempre a usare il miglior metodo di accesso possibile e, se non riesce, riprova con il secondo migliore, poi con il terzo e così via. Concedere a SD Maid permessi sovrapposti ne migliora l'affidabilità.

> **Suggerimento:** alcuni passaggi della configurazione qui sotto richiedono di eseguire comandi ADB. Se sul tuo computer ADB non è configurato, puoi usare il [Web ADB Helper](https://d4rken.github.io/web-adb/) per eseguirli direttamente dal browser.

## Gestisci archivio
{: #manage-storage }

Il permesso "Gestisci archivio" è un permesso di accesso all'archivio di portata ampia.

* Su Android 12 e versioni precedenti concede l'accesso a tutto l'archivio pubblico.
* Su Android 13+ concede ancora l'accesso a tutto l'archivio pubblico, ma non alle sottocartelle di `Android/data`.

## Statistiche di utilizzo
{: #usage-statistics }

I permessi per le statistiche di utilizzo danno a SD Maid accesso ai dati, alle dimensioni delle cache e all'informazione se un'app è in esecuzione o quando è stata usata l'ultima volta.

## Framework di accesso all'archivio
{: #storage-access-framework }
SD Maid usa l'[Android Storage Access Framework (SAF)](http://developer.android.com/guide/topics/providers/document-provider.html) per ottenere l'accesso ad altre aree dell'archivio. SD Maid ti chiederà di concedere l'accesso a percorsi specifici che non sono accessibili con il solo permesso "Gestisci archivio" (Just Android Things™️).

* Premi il pulsante `Consenti accesso` 
* Si apre una nuova finestra
* Verifica se è già selezionato il percorso corretto, altrimenti devi navigare fino al percorso richiesto da SD Maid.
* Poi premi il pulsante `Usa questa cartella`: la voce della configurazione per questo percorso si sbloccherà e diventerà verde.

Ripeti questa procedura finché tutti i percorsi non sono verdi.

<a href="/assets/images/setup/saf-1.webp"><img src="/assets/images/setup/saf-1.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-2.webp"><img src="/assets/images/setup/saf-2.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-3.webp"><img src="/assets/images/setup/saf-3.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-4.webp"><img src="/assets/images/setup/saf-4.webp" width="100" alt=""></a>

### SAF: risoluzione dei problemi
{: #saf-troubleshooting }
Per quando le cose non vanno come previsto.

#### SAF: accesso a `Android/data|obb`
{: #saf-androiddataobb-access }

Concedere l'accesso a `Android/data|obb` non è possibile con le versioni più recenti dell'app di sistema `Files` (File; `com.google.android.documentsui`) su Android 11 e Android 12. Se l'app `Files` installata è destinata ad Android 14 (livello API 34) o successivo, SD Maid nasconde la richiesta di accesso SAF per questi percorsi (come su Android 13+).

Google Play aggiorna l'app `Files` tramite il servizio `Aggiornamento di sistema Google Play`. L'app "Files" originale (compilata per il livello API 31, ad es. `aml_doc_310851020 (310851020)`) fornita con il sistema operativo Android 12 permette a SD Maid di aprire la finestra di selezione del percorso SAF su `Android/Data`, mentre l'app "Files" più recente (compilata per il livello API 34 o successivo, ad es. `14-10492947 (340916000)`) lo impedisce.

**Soluzione alternativa:**

Puoi disinstallare l'aggiornamento dell'app `Files` e poi configurare SD Maid. Aggiornare di nuovo l'app `Files` non annullerà i permessi già concessi.

* Apri le impostazioni del dispositivo
* Vai su "App e notifiche" oppure "Application Manager" (Gestione applicazioni)
* Trova l'app di sistema `Files`
* Tocca il menu a tre puntini, di solito nell'angolo in alto a destra
* Scegli "Disinstalla aggiornamenti"

oppure

_Puoi eseguire questo comando anche con il [Web ADB Helper](https://d4rken.github.io/web-adb/)._

`adb uninstall com.google.android.documentsui`

#### SAF: OPEN_DOCUMENT_TREE ActivityNotFoundException
{: #saf-open_document_tree-activitynotfoundexception }

Concedere l'accesso ai percorsi tramite lo Storage Access Framework richiede un'app che permetta all'utente di confermare i percorsi a cui verrà concesso l'accesso. Questa app è l'app "Documents" (Documenti). È un'app di sistema integrata in ogni dispositivo Android a partire da Android 5.0 (`com.android.documentsui` o `com.google.android.documentsui`). Se ricevi questo errore, qualcuno ha eliminato o disattivato questa app. Nella maggior parte dei casi qualcuno ha usato uno strumento di "de-bloating" troppo zelante che l'ha disattivata. Puoi riattivarla tramite ADB:

_Puoi eseguire questi comandi anche con il [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```
adb shell pm enable com.android.documentsui
or
adb shell pm enable com.google.android.documentsui
```

#### SAF: finestra vuota
{: #saf-empty-window }
Se la finestra usata per concedere l'accesso ai percorsi è vuota, il pacchetto del provider dell'archivio esterno (`com.android.externalstorage`) potrebbe essere disattivato (probabilmente da uno strumento di "de-bloating" troppo zelante). Puoi riattivarlo tramite ADB:

_Puoi eseguire questo comando anche con il [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```
adb shell pm enable com.android.externalstorage
```

Eseguire di nuovo l'`Aggiornamento di sistema Google Play` ripristinerà l'aggiornamento. Puoi anche scaricare manualmente una versione aggiornata da [APKMirror.com](https://www.apkmirror.com/apk/google-inc/files-6/).

## Notifiche
{: #notifications }

Senza questo permesso SD Maid non potrà inviare notifiche. Su alcune ROM questo può ridurre l'affidabilità dell'esecuzione in background.

## Inventario App
{: #app-inventory }

SD Maid richiede il permesso `QUERY_ALL_PACKAGES` per verificare quali app sono installate sul tuo dispositivo (cioè ["visibility into the inventory of installed apps"](https://support.google.com/googleplay/android-developer/answer/10158779?hl=en), visibilità sull'inventario delle app installate). Vedi anche ["Statistiche di utilizzo"](/it/setup#usage-statistics)

Il permesso `QUERY_ALL_PACKAGES` è indispensabile per:

* [Resti delle applicazioni (CorpseFinder)](/it/corpsefinder): per capire quali file possono essere eliminati, SD Maid deve sapere se hai installato app che potrebbero averne bisogno.
* [Pulizia App (AppCleaner)](/it/appcleaner): determinare le cache di ogni app
* [AppControl](/it/appcontrol): poter elencare tutte le app installate
* [StorageAnalyzer](/it/storageanalyzer): attribuire lo spazio occupato alle app installate

Questo permesso esiste da [Android 11](https://developer.android.com/training/package-visibility) e normalmente è concesso per impostazione predefinita. Da Android 14 esistono alcuni dispositivi (ad es. [#1015](https://github.com/d4rken-org/sdmaid-se/issues/1015), [#1354](https://github.com/d4rken-org/sdmaid-se/issues/1354)) in cui il permesso non è concesso per impostazione predefinita o può essere revocato.

[<img src="/assets/images/setup/app-inventory.webp" width="100">](/assets/images/setup/app-inventory.webp)

### Elenco app non valido
{: #invalid-app-list }

Su alcuni dispositivi il permesso "App list" (Elenco app; `QUERY_ALL_PACKAGES` / `GET_INSTALLED_APPS`) è concesso, ma il sistema restituisce comunque un **elenco incompleto delle app installate**, per esempio un elenco a cui mancano pacchetti di sistema fondamentali come `android`. Quando SD Maid lo rileva, invece di proseguire mostra nella scheda della configurazione un avviso **"Elenco app invalido"** e una nota **"Possibile limitazione del dispositivo"**.

SD Maid si ferma qui di proposito: se non vede l'elenco completo delle app installate, non può stabilire in modo affidabile quali file appartengono a un'app ancora installata. Proseguire comunque potrebbe significare eliminare dati che appartengono a un'app che SD Maid semplicemente non vedeva, quindi le funzioni legate alle app restano disattivate finché non è disponibile un elenco completo.

**Cosa puoi provare:**

* Apri le impostazioni di sistema e assicurati che a SD Maid sia consentito il permesso "App list" (Elenco app). Sui dispositivi di alcuni produttori questa opzione ha un nome diverso (ad es. "Query all packages") o si trova in una schermata separata "Privacy" o "Permission manager" (Gestione autorizzazioni).
* Verifica se un risparmio energetico integrato, una protezione della privacy o una funzione per "nascondere le app" sta filtrando l'elenco delle app che SD Maid può vedere, ed escludi SD Maid da essa.
* Riavvia il dispositivo e riapri SD Maid, così lo stato del permesso viene rivalutato.

Se il permesso "App list" è già consentito e l'elenco viene ancora segnalato come non valido, di solito si tratta di una **limitazione del dispositivo** e non di qualcosa che puoi risolvere: alcuni sistemi (per esempio HarmonyOS o build di Android pesantemente modificate) non forniscono mai un elenco completo delle app alle app di terze parti. Vedi [#2441](https://github.com/d4rken-org/sdmaid-se/issues/2441) per un esempio.

In quel caso gli strumenti legati alle app ([Resti delle applicazioni](/it/corpsefinder), [Pulizia App](/it/appcleaner), [AppControl](/it/appcontrol)) restano disattivati per proteggere i tuoi dati, ma [StorageAnalyzer](/it/storageanalyzer) funziona ancora: mostra quanto spazio sta usando il tuo archivio, solo con meno dettagli; le dimensioni delle cartelle vengono mostrate in sola lettura e non possono essere attribuite alle singole app.

## Servizio di accessibilità
{: #accessibility-service }
Il servizio di accessibilità permette a SD Maid di interagire con il dispositivo come farebbe una persona, cioè guardando lo schermo e premendo i pulsanti. SD Maid può usarlo per automatizzare azioni noiose come premere "Svuota cache" su centinaia di app. Il servizio di accessibilità non può essere avviato dall'app stessa: deve essere avviato dal sistema, perché questo tipo di servizio concede a un'app un accesso aggiuntivo al dispositivo.

### ACS: risoluzione dei problemi
{: #acs-troubleshooting }

In varie circostanze il servizio può smettere di funzionare e richiedere che tu lo riattivi:
* Aggiorni l'app.
* L'app va in crash. Se succede, dovresti segnalarlo come bug. Se succede troppo spesso, potresti non riuscire a riavviarlo spegnendolo e riaccendendolo finché il dispositivo non viene riavviato.
* Il sistema chiude l'app a causa di "ottimizzazioni della batteria" troppo aggressive (vedi anche <https://dontkillmyapp.com/>)
* L'app di sicurezza del produttore lo disattiva (vedi [Bloccato da Phone Manager](#acs-blocked-by-phone-manager-oneplus-oppo-realme))

#### ACS: il servizio è disattivato
{: #acs-service-is-disabled }

Alcune ROM (ad es. MIUI) sono note per fermare in modo aggressivo tutto ciò che non è nella whitelist del produttore del dispositivo.
Se ti viene chiesto continuamente di riattivare il servizio di accessibilità, il sistema potrebbe disattivarlo ogni volta che SD Maid non è in uso.

Prova a disattivare le ottimizzazioni della batteria; sulle ROM MIUI c'è inoltre un'impostazione chiamata "Autostart" (Avvio automatico): attivala per SD Maid. Per i dispositivi Xiaomi/POCO/Redmi:

* Vai su `Security>Manage applications>Auto start in background` (Sicurezza > Gestisci app > Avvio automatico in background) e consentilo per SD Maid.
* Vai su `Security>Increase speed>Lock apps` (Sicurezza > Aumenta velocità > Blocca app) e seleziona SD Maid.


Puoi anche valutare di concedere un permesso aggiuntivo che [permette a SD Maid di riavviare automaticamente il servizio](#acs-automatic-service-restart).

[<img src="/assets/images/setup/acs-disabled.webp" width="100">](/assets/images/setup/acs-disabled.webp)

#### ACS: bloccato da Phone Manager (OnePlus, OPPO, Realme)
{: #acs-blocked-by-phone-manager-oneplus-oppo-realme }

Su OxygenOS e ColorOS l'app preinstallata "Phone Manager" (Gestione telefono) può disattivare da sola il servizio di accessibilità, di solito entro un secondo dalla fine di una pulizia. In seguito l'interruttore dell'accessibilità di Android risulta davvero spento, quindi sembra che SD Maid abbia smesso di funzionare a metà dell'esecuzione.

Disattiva la funzione in `Phone Manager>Utilities>Block suspicious app activities` (Gestione telefono > Utilità > Blocca attività sospette delle app).

La stessa schermata tiene un elenco di ciò che ha bloccato. Voci etichettate "Abnormal device control" (Controllo anomalo del dispositivo) con orari che corrispondono alle pulizie che hai eseguito confermano che la causa è questa. L'interruttore "Blocking alerts" (Avvisi di blocco) lì accanto controlla solo se ricevi una notifica, quindi con gli avvisi disattivati questo succede in silenzio.

#### ACS: attivare tramite ADB
{: #acs-enable-via-adb }

Se il tuo dispositivo non ha un modo per attivare i servizi di accessibilità dallo schermo (ad es. un dispositivo TV), è possibile attivare il servizio tramite ADB eseguendo:

_Puoi eseguire questo comando anche con il [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```bash
adb shell settings put secure enabled_accessibility_services eu.darken.sdmse/.automation.core.AutomationService
```

#### ACS: riavvio automatico del servizio
{: #acs-automatic-service-restart }

_Nota: è una tecnica avanzata, pensata per utenti esperti._

Puoi concedere un permesso aggiuntivo che permette a SD Maid di riavviare il servizio da solo.
Il permesso si chiama `WRITE_SECURE_SETTINGS` e può essere concesso solo [tramite ADB](https://www.google.com/search?q=android+adb).
Se hai configurato ADB, esegui questo comando per concedere il permesso:

_Puoi eseguire questo comando anche con il [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```bash
adb shell pm grant eu.darken.sdmse android.permission.WRITE_SECURE_SETTINGS
```

Con questo permesso concesso, SD Maid può usare il comando [descritto qui](#acs-enable-via-adb) per attivare il servizio quando necessario.
SD Maid lo userà solo se hai attivato manualmente il servizio di accessibilità almeno una volta.
Se torni nella configurazione e dici a SD Maid di non usare il servizio di accessibilità, questa tecnica di riavvio automatico non verrà usata finché non riattivi manualmente il servizio di accessibilità.

Per togliere di nuovo questo permesso a SD Maid, usa questo comando:

_Puoi eseguire questo comando anche con il [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```bash
adb shell pm revoke eu.darken.sdmse android.permission.WRITE_SECURE_SETTINGS
```

Se ricevi `Operation not allowed: java.lang.SecurityException`, [vedi qui](https://stackoverflow.com/questions/52079343). Alcuni dispositivi limitano i permessi ADB. Verifica se nelle opzioni sviluppatore c'è un'impostazione aggiuntiva (ad es. `Disable Permission Monitoring`) da attivare o disattivare; dopo aver usato il comando puoi ripristinarla.

#### ACS: Protezione avanzata
{: #acs-advanced-protection }

Su Android 17 e versioni successive, la modalità [**Protezione avanzata**](https://developer.android.com/privacy-and-security/advanced-protection-mode) di Google impedisce alle app che non sono strumenti di accessibilità dedicati (lettori di schermo, accesso con interruttori, ecc.) di usare il servizio di accessibilità. SD Maid è uno strumento di manutenzione/pulizia, quindi mentre la Protezione avanzata è attiva il suo servizio di accessibilità **non può** essere attivato: Android rifiuta il servizio a livello di sistema, anche se provi a concederlo tramite root o accesso ADB.

SD Maid non può esentarsi da questa regola: possono rientrarvi solo le app che Google classifica come strumenti di accessibilità, e le app di pulizia sono escluse esplicitamente.

Hai due opzioni:

- **Usa il root o l'accesso ADB** Quando il root o l'accesso ADB sono configurati, Pulizia App (AppCleaner) di SD Maid svuota le cache delle app tramite una chiamata di sistema diretta e **non** ha bisogno del servizio di accessibilità. Con il **root** questo vale anche per la pulizia di singole app selezionate. Con l'**accesso ADB** una pulizia completa di "tutte le app" funziona senza il servizio di accessibilità, ma svuotare la cache di app selezionate *specifiche* dipende ancora da esso. Configuralo nella Configurazione di SD Maid → [Permessi di root](#root-access) o [Accesso ADB](#shizuku).
- **Disattiva la Protezione avanzata** se vuoi proprio la pulizia basata sull'accessibilità. Si trova in `Impostazioni → Sicurezza e privacy → Protezione avanzata` (il percorso esatto varia a seconda del dispositivo). Riattivarla in seguito disattiverà di nuovo il servizio di accessibilità.

#### ACS: restrizioni AppOps
{: #acs-appops-restrictions }

Android 13+ può applicare restrizioni aggiuntive alle app installate manualmente (sideload). Quindi, se SD Maid non è installato da una fonte nota (a Google) come Google Play o altri app store, potresti non riuscire a concedere direttamente alcuni permessi a SD Maid, ad es. avviare un servizio di accessibilità.

##### OxygenOS 15 - OnePlus
{: #oxygenos-15---oneplus }

OnePlus ha dimenticato (o semplicemente deciso di non) includere l'azione `Allow restricted settings` (Consenti impostazioni con limitazioni) nelle impostazioni delle app di OxygenOS 15. Se non installi tramite Google Play, l'installer deve impostare il flag `INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS`. Se hai configurato Shizuku, puoi usare l'app ["Install with Options"](https://github.com/zacharee/InstallWithOptions).


##### Altre ROM
{: #other-roms }

Per rimuovere queste restrizioni apri la schermata delle impostazioni di sistema dell'app SD Maid. Apri il menu nell'angolo in alto a destra e seleziona `Allow restricted settings` (Consenti impostazioni con limitazioni); poi puoi tornare all'elenco dei servizi di accessibilità e attivare il servizio di SD Maid.

**Nota:**
Per migliorare l'usabilità (è sarcasmo), Google ha deciso che questa impostazione sia visibile solo se:

* hai visto almeno una volta la finestra "Restricted setting" (Impostazione con limitazioni) dopo aver toccato la voce del servizio di accessibilità di SD Maid
* hai aperto le impostazioni di sistema e raggiunto a mano la pagina delle impostazioni di SD Maid, non tramite un'altra app o scorciatoie.

😮‍💨


Altre informazioni di contesto [si trovano qui](https://blog.esper.io/android-13-sideloading-restriction-harder-malware-abuse-accessibility-apis/).

<a href="/assets/images/setup/acs-restricted-settings-1.webp"><img src="/assets/images/setup/acs-restricted-settings-1.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-2.webp"><img src="/assets/images/setup/acs-restricted-settings-2.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-3.webp"><img src="/assets/images/setup/acs-restricted-settings-3.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-4.webp"><img src="/assets/images/setup/acs-restricted-settings-4.webp" width="100" alt=""></a>

## Permessi di root
{: #root-access }

Con l'accesso root, SD Maid ha accesso completo al dispositivo e può eseguire la scansione più approfondita possibile. Di solito significa che hai sbloccato il bootloader e configurato Magisk.

Se SD Maid ritiene che il tuo dispositivo possa avere il root, chiederà per prima cosa l'accesso root. Se l'accesso root viene concesso, SD Maid lo userà per concedere quanti più altri permessi possibile. L'accesso root è il livello di accesso più alto che puoi dare a un'app; gli altri permessi servono allora solo per comodità e prestazioni, ed è per questo che non richiedono un consenso aggiuntivo durante la configurazione se SD Maid ha già il root.

## Accesso ADB
{: #shizuku }

L'accesso ADB permette a SD Maid di usare l'interfaccia di debug di Android (ADB) per operazioni con privilegi elevati. Richiede un'app separata che esegue un servizio ADB sul dispositivo e concede ad altre app l'accesso a esso: [Porter](#porter) o [Shizuku](#using-shizuku). È una modifica che puoi apportare al telefono e di cui possono approfittare altre app.

È simile al root, ma non è la stessa cosa. I permessi sono leggermente meno potenti, ma la modifica non è permanente e non richiede di sbloccare il bootloader.

### Porter
{: #porter }

[Porter](https://porter.darken.eu) è il mio fork di Shizuku, mantenuto attivamente, con supporto facoltativo per il root. Il suo sito spiega come installarlo e avviarlo. Su Android 11 e versioni successive, il debug wireless ti permette di avviarlo senza un computer.

Se configuri l'accesso ADB per la prima volta, usa Porter. Le build di SD Maid da GitHub e F-Droid ti indirizzano a Porter quando non è installata alcuna app per l'accesso ADB. La build di Google Play ti indirizza a Shizuku su Google Play, ma funziona allo stesso modo con Porter.

Se sono installati sia Porter sia Shizuku, SD Maid usa Porter. La scheda della configurazione mostra allora `Porter è installato, quindi SD Maid attende Porter invece di Shizuku`. Avvia Porter per continuare, oppure disinstallalo se vuoi che SD Maid usi Shizuku.

### Usare Shizuku
{: #using-shizuku }

Esistono due build di Shizuku:

* [Il fork di thedjchi](https://github.com/thedjchi/Shizuku) ([guida alla configurazione](https://github.com/thedjchi/Shizuku/wiki/setup)) è mantenuto attivamente e risulta funzionare sui dispositivi in cui l'originale [non si connette mai](#shizuku-never-connects).
* [L'originale di RikkaApps](https://shizuku.rikka.app/guide/setup/) funziona ancora bene sulla maggior parte dei dispositivi.

Non possono essere installati contemporaneamente. Se l'originale funziona già per te, non c'è bisogno di cambiare.

### Concedere l'accesso
{: #granting-access }

Dopo aver configurato Porter o Shizuku:

* Nella configurazione di SD Maid, scegli `Prova a usare l'accesso ADB`. È un'opzione interna di SD Maid, con cui puoi attivare/disattivare l'uso dell'accesso ADB da parte di SD Maid
* Dopo aver dato il consenso, una finestra di Porter o di Shizuku chiede se SD Maid può usarlo
* Conferma la finestra
* La scheda della configurazione `Accesso ADB` mostra se SD Maid riesce a connettersi

Configurare l'accesso ADB quando hai già concesso a SD Maid l'accesso root non attiva funzioni aggiuntive, ma migliora l'affidabilità. Se le azioni falliscono con il permesso di root, SD Maid riproverà con l'accesso ADB.

### Accesso ADB: risoluzione dei problemi
{: #shizuku-troubleshooting }

Puoi controllare lo stato dell'accesso ADB di SD Maid andando in `Impostazioni > Configurazione` e guardando la scheda della configurazione `Accesso ADB`. Dovrebbe mostrare `SD Maid ora può usare l'accesso ADB tramite Porter` (o `tramite Shizuku`).

* `Porter non ha ancora autorizzato SD Maid`: conferma la finestra di accesso oppure autorizza SD Maid nell'app Porter (o Shizuku).
* `Porter nega l'accesso a SD Maid`: a SD Maid è stato negato l'accesso in modo permanente. Apri Porter (o Shizuku) e autorizza lì SD Maid.
* `Aggiorna Porter per usarlo con SD Maid` oppure `Questa versione di SD Maid è troppo vecchia per Porter`: le due app non hanno versioni compatibili. Aggiorna quella indicata dalla scheda.

#### Shizuku non si connette mai
{: #shizuku-never-connects }

La scheda mostra `Accesso ADB non riuscito` e `Shizuku è in esecuzione, ma non ha mai connesso SD Maid al suo servizio helper.` Le versioni precedenti mostravano `Waiting for link with Shizuku` (In attesa del collegamento con Shizuku) o `Shizuku link failed` (Collegamento con Shizuku non riuscito).

Apri l'app Shizuku. Se dice che è in esecuzione e SD Maid è nel suo elenco di app autorizzate, Shizuku è configurato correttamente e configurarlo di nuovo non servirà. Quello che non funziona è il servizio helper di Shizuku: si chiude all'avvio prima di poter rispondere, e SD Maid si arrende dopo circa 15 secondi. `Riprova` esegue lo stesso controllo e fallisce allo stesso modo.

È un bug di Shizuku ([RikkaApps/Shizuku#1198](https://github.com/RikkaApps/Shizuku/issues/1198)), non di SD Maid, e blocca qualsiasi app che usa il servizio helper di Shizuku. Riguarda i dispositivi MediaTek e i dispositivi con HyperOS o MIUI.

[Il fork di thedjchi](https://github.com/thedjchi/Shizuku) risulta risolverlo. Per passare al fork:

* Disinstalla prima l'originale, non possono coesistere
* Se usavi il debug wireless, rimuovi l'abbinamento dell'originale
* Installa il fork dalle sue [release](https://github.com/thedjchi/Shizuku/releases) e segui la sua [guida alla configurazione](https://github.com/thedjchi/Shizuku/wiki/setup)
* Concedi di nuovo il permesso di Shizuku a SD Maid e a tutto ciò che lo usava
