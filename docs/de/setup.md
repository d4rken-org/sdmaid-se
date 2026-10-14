---
title: "SD Maid SE einrichten"
lang: de
translation_key: setup
permalink: /de/setup
source_hash: a14003a95dd2336d
---
# SD Maid SE einrichten
{: #setting-up-sd-maid-se }
Informationen zu den verschiedenen Einrichtungsschritten von SD Maid SE.

SD Maid passt sich an den verfügbaren Zugriff an. Alle Berechtigungen sind optional, aber wenn du keine davon gewährst, funktionieren die meisten Funktionen nicht. SD Maid arbeitet am besten, wenn alle Einrichtungsschritte abgeschlossen sind.

SD Maid versucht automatisch, einige der Einrichtungsschritte abzuschließen, wenn [Root](#root-access) oder [ADB-Zugriff](#shizuku) verfügbar ist.

Manche Berechtigungen überschneiden sich oder schließen einander ein, z. B. kannst du SD Maid sowohl Root-Zugriff als auch ADB-Zugriff geben. SD Maid versucht für jede Aktion immer die bestmögliche Zugriffsmethode zu verwenden und, falls das fehlschlägt, es mit der zweitbesten, dann mit der drittbesten usw. erneut. Wenn du SD Maid sich überschneidende Berechtigungen gewährst, verbessert das die Zuverlässigkeit.

> **Tipp:** Bei einigen der folgenden Einrichtungsschritte musst du ADB-Befehle ausführen. Wenn du ADB auf deinem Computer nicht eingerichtet hast, kannst du sie mit dem [Web ADB Helper](https://d4rken.github.io/web-adb/) direkt in deinem Browser ausführen.

## Speicher verwalten
{: #manage-storage }

Die Berechtigung „Speicher verwalten“ ist eine weitreichende Berechtigung für den Speicherzugriff.

* Unter Android 12 und älter gewährt sie Zugriff auf den gesamten öffentlichen Speicher.
* Unter Android 13+ gewährt sie weiterhin Zugriff auf den gesamten öffentlichen Speicher, aber nicht auf Unterverzeichnisse unter `Android/data`.

## Nutzungsstatistiken
{: #usage-statistics }

Die Berechtigung für Nutzungsstatistiken gibt SD Maid Zugriff auf Daten- und Cache-Größen sowie darauf, ob eine App läuft oder wann sie zuletzt verwendet wurde.

## Storage Access Framework
{: #storage-access-framework }
SD Maid nutzt das [Android Storage Access Framework (SAF)](http://developer.android.com/guide/topics/providers/document-provider.html), um Zugriff auf zusätzliche Speicherbereiche zu erhalten. SD Maid bittet dich, Zugriff auf bestimmte Pfade zu gewähren, die allein mit der Berechtigung „Speicher verwalten“ nicht zugänglich sind (Just Android Things™️).

* Drücke die Schaltfläche `Zugriff gewähren` 
* Ein neues Fenster öffnet sich
* Möglicherweise ist der richtige Pfad bereits ausgewählt, andernfalls musst du zu dem Pfad navigieren, den SD Maid haben möchte.
* Drücke dann die Schaltfläche `Diesen Ordner verwenden`. Der Einrichtungseintrag für diesen Pfad wird freigeschaltet und grün eingefärbt.

Wiederhole diesen Vorgang, bis alle Pfade grün sind.

<a href="/assets/images/setup/saf-1.webp"><img src="/assets/images/setup/saf-1.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-2.webp"><img src="/assets/images/setup/saf-2.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-3.webp"><img src="/assets/images/setup/saf-3.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-4.webp"><img src="/assets/images/setup/saf-4.webp" width="100" alt=""></a>

### SAF: Fehlerbehebung
{: #saf-troubleshooting }
Für den Fall, dass etwas nicht wie geplant läuft.

#### SAF: Zugriff auf `Android/data|obb`
{: #saf-androiddataobb-access }

Unter Android 11 und Android 12 lässt sich mit neueren Versionen der System-App `Files` (`com.google.android.documentsui`) kein Zugriff auf `Android/data|obb` gewähren. Wenn die installierte App `Files` auf Android 14 (API-Level 34) oder neuer ausgerichtet ist, blendet SD Maid die SAF-Zugriffsanfrage für diese Pfade aus (wie unter Android 13+).

Google Play aktualisiert die App `Files` über den Dienst `Google Play system update`. Die ursprüngliche App „Files“ (gebaut für API-Level 31, z. B. `aml_doc_310851020 (310851020)`), die mit Android 12 ausgeliefert wurde, erlaubt SD Maid, das SAF-Fenster zur Pfadauswahl für `Android/Data` zu öffnen, während die neueste App „Files“ (gebaut für API-Level 34 oder später, z. B. `14-10492947 (340916000)`) das verhindert.

**Workaround:**

Du kannst das Update der App `Files` deinstallieren und dann SD Maid einrichten. Ein erneutes Update der App `Files` macht bereits gewährte Berechtigungen nicht kaputt.

* Öffne die Geräteeinstellungen
* Gehe zu „Apps & Benachrichtigungen“ oder „Anwendungsmanager“
* Suche die System-App `Files`
* Tippe auf das Drei-Punkte-Menü, meist oben rechts
* Wähle „Updates deinstallieren“

oder

_Du kannst diesen Befehl auch mit dem [Web ADB Helper](https://d4rken.github.io/web-adb/) ausführen._

`adb uninstall com.google.android.documentsui`

#### SAF: OPEN_DOCUMENT_TREE ActivityNotFoundException
{: #saf-open_document_tree-activitynotfoundexception }

Um über das Storage Access Framework Zugriff auf Pfade zu gewähren, ist eine App nötig, in der der Nutzer die Pfade bestätigt, auf die Zugriff gewährt wird. Diese App ist die App „Dokumente“. Sie ist eine System-App, die seit Android 5.0 in jedes Android-Gerät eingebaut ist (`com.android.documentsui` oder `com.google.android.documentsui`). Wenn du diesen Fehler bekommst, hat jemand diese App gelöscht oder deaktiviert. Meist hat jemand ein übereifriges „Debloating“-Werkzeug ausgeführt, das sie deaktiviert hat. Du kannst sie über ADB wieder aktivieren:

_Du kannst diese Befehle auch mit dem [Web ADB Helper](https://d4rken.github.io/web-adb/) ausführen._

```
adb shell pm enable com.android.documentsui
or
adb shell pm enable com.google.android.documentsui
```

#### SAF: Leeres Fenster
{: #saf-empty-window }
Wenn das Fenster zum Gewähren des Zugriffs auf Pfade leer ist, ist vielleicht das Paket deines externen Speicheranbieters (`com.android.externalstorage`) deaktiviert (vermutlich durch ein übereifriges „Debloating“-Werkzeug). Du kannst es über ADB wieder aktivieren:

_Du kannst diesen Befehl auch mit dem [Web ADB Helper](https://d4rken.github.io/web-adb/) ausführen._

```
adb shell pm enable com.android.externalstorage
```

Wenn du das `Google Play system update` erneut ausführst, wird das Update wiederhergestellt. Du kannst auch manuell eine aktualisierte Version von [APKMirror.com](https://www.apkmirror.com/apk/google-inc/files-6/) herunterladen.

## Benachrichtigungen
{: #notifications }

Ohne diese Berechtigung kann SD Maid keine Benachrichtigungen anzeigen. Auf manchen ROMs kann das die Zuverlässigkeit bei der Ausführung im Hintergrund beeinträchtigen.

## App-Inventar
{: #app-inventory }

SD Maid benötigt die Berechtigung `QUERY_ALL_PACKAGES`, um zu prüfen, welche Apps auf deinem Gerät installiert sind (d. h. [„Sichtbarkeit des Bestands installierter Apps“](https://support.google.com/googleplay/android-developer/answer/10158779?hl=en)). Siehe auch [„Berechtigung für Nutzungsstatistiken“](/de/setup#usage-statistics)

Die Berechtigung `QUERY_ALL_PACKAGES` ist zwingend erforderlich für:

* [Leichenfinder (CorpseFinder)](/de/corpsefinder): Um zu verstehen, welche Dateien gelöscht werden können, muss SD Maid wissen, ob du Apps installiert hast, die sie eventuell brauchen.
* [App-Reiniger (AppCleaner)](/de/appcleaner): Caches für jede App ermitteln
* [App-Kontrolle (AppControl)](/de/appcontrol): Alle installierten Apps auflisten können
* [Speicheranalyse (StorageAnalyzer)](/de/storageanalyzer): Belegten Speicher den installierten Apps zuordnen

Diese Berechtigung gibt es seit [Android 11](https://developer.android.com/training/package-visibility), und sie wird normalerweise standardmäßig gewährt. Seit Android 14 gibt es manche Geräte (z. B. [#1015](https://github.com/d4rken-org/sdmaid-se/issues/1015), [#1354](https://github.com/d4rken-org/sdmaid-se/issues/1354)), auf denen die Berechtigung nicht standardmäßig gewährt ist oder entzogen werden kann.

[<img src="/assets/images/setup/app-inventory.webp" width="100">](/assets/images/setup/app-inventory.webp)

### Ungültige App-Liste
{: #invalid-app-list }

Auf manchen Geräten ist die Berechtigung „App-Liste“ (`QUERY_ALL_PACKAGES` / `GET_INSTALLED_APPS`) gewährt, das System liefert aber trotzdem eine **unvollständige Liste der installierten Apps**, zum Beispiel eine Liste, in der zentrale Systempakete wie `android` fehlen. Wenn SD Maid das erkennt, zeigt es in der Einrichtungskarte eine Warnung **„Ungültige App-Liste“** und einen Hinweis **„Mögliche Geräteeinschränkung“**, statt fortzufahren.

SD Maid hält hier mit Absicht an: Wenn es die vollständige Liste der installierten Apps nicht sehen kann, kann es nicht zuverlässig erkennen, welche Dateien zu einer noch installierten App gehören. Trotzdem fortzufahren könnte bedeuten, Daten einer App zu löschen, die SD Maid schlicht nicht sehen konnte. Daher bleiben app-bezogene Funktionen deaktiviert, bis eine vollständige Liste verfügbar ist.

**Was du versuchen kannst:**

* Öffne deine Systemeinstellungen und stelle sicher, dass SD Maid die Berechtigung „App-Liste“ erlaubt ist. Bei manchen Herstellern heißt dieser Schalter anders (z. B. „Alle Pakete abfragen“) oder befindet sich in einem eigenen Bildschirm „Datenschutz“ oder „Berechtigungsmanager“.
* Prüfe, ob ein eingebauter Energiesparmodus, Datenschutzwächter oder eine Funktion zum „Verstecken von Apps“ die Liste der Apps filtert, die SD Maid sehen kann, und nimm SD Maid davon aus.
* Starte das Gerät neu und öffne SD Maid erneut, damit der Berechtigungsstatus neu ausgewertet wird.

Wenn die Berechtigung „App-Liste“ bereits erlaubt ist und die Liste trotzdem als ungültig gemeldet wird, ist das meist eine **Geräteeinschränkung** und nichts, was du beheben kannst: Manche Systeme (zum Beispiel HarmonyOS oder stark veränderte Android-Builds) geben Drittanbieter-Apps nie eine vollständige App-Liste. Ein Beispiel findest du in [#2441](https://github.com/d4rken-org/sdmaid-se/issues/2441).

In diesem Fall bleiben die app-bezogenen Werkzeuge ([Leichenfinder](/de/corpsefinder), [App-Reiniger](/de/appcleaner), [App-Kontrolle](/de/appcontrol)) zum Schutz deiner Daten deaktiviert, aber die [Speicheranalyse](/de/storageanalyzer) funktioniert weiterhin: Sie zeigt, wie viel Platz dein Speicher belegt, nur mit weniger Details. Ordnergrößen werden schreibgeschützt angezeigt und lassen sich nicht einzelnen Apps zuordnen.

## Bedienungshilfe
{: #accessibility-service }
Die Bedienungshilfe ermöglicht SD Maid, wie ein Mensch mit deinem Gerät zu interagieren, d. h. den Bildschirm zu sehen und Schaltflächen zu drücken. SD Maid kann damit mühsame Aktionen automatisieren, etwa bei Hunderten von Apps auf „Cache leeren“ zu drücken. Die Bedienungshilfe ist ein Dienst, den die App nicht selbst starten kann. Er muss vom System gestartet werden, weil diese Art von Dienst einer App zusätzlichen Zugriff auf dein Gerät gewährt.

### ACS: Fehlerbehebung
{: #acs-troubleshooting }

Unter verschiedenen Umständen kann der Dienst aufhören zu laufen, sodass du ihn erneut aktivieren musst:
* Du aktualisierst die App.
* Die App stürzt ab. Wenn das passiert, solltest du es als Fehler melden. Passiert es zu oft, kannst du den Dienst möglicherweise erst nach einem Neustart des Geräts durch Aus- und Einschalten wieder starten.
* Das System beendet die App wegen übermäßig aggressiver „Akku-Optimierungen“ (siehe auch <https://dontkillmyapp.com/>)
* Die Sicherheits-App des Herstellers schaltet ihn ab (siehe [Durch Phone Manager blockiert](#acs-blocked-by-phone-manager-oneplus-oppo-realme))

#### ACS: Dienst ist deaktiviert
{: #acs-service-is-disabled }

Manche ROMs (z. B. MIUI) sind dafür berüchtigt, rigoros alles zu stoppen, was nicht vom Gerätehersteller auf eine Whitelist gesetzt wurde.
Wenn du ständig aufgefordert wirst, die Bedienungshilfe erneut zu aktivieren, deaktiviert das System sie möglicherweise jedes Mal, wenn SD Maid nicht aktiv genutzt wird.

Versuche, die Akku-Optimierungen zu deaktivieren. Auf MIUI-ROMs gibt es zusätzlich eine Einstellung namens „Autostart“, aktiviere sie für SD Maid. Für Xiaomi/POCO/Redmi-Geräte:

* Gehe zu `Security>Manage applications>Auto start in background` und erlaube das für SD Maid.
* Gehe zu `Security>Increase speed>Lock apps` und wähle SD Maid aus.


Du kannst auch in Betracht ziehen, eine zusätzliche Berechtigung zu gewähren, die es [SD Maid erlaubt, den Dienst automatisch neu zu starten](#acs-automatic-service-restart).

[<img src="/assets/images/setup/acs-disabled.webp" width="100">](/assets/images/setup/acs-disabled.webp)

#### ACS: Durch Phone Manager blockiert (OnePlus, OPPO, Realme)
{: #acs-blocked-by-phone-manager-oneplus-oppo-realme }

Unter OxygenOS und ColorOS kann die vorinstallierte App „Phone Manager“ die Bedienungshilfe von sich aus abschalten, typischerweise innerhalb einer Sekunde nach dem Ende einer Bereinigung. Der Bedienungshilfe-Schalter von Android ist danach tatsächlich aus, daher sieht es so aus, als hätte SD Maid mitten im Durchlauf aufgehört zu arbeiten.

Schalte die Funktion unter `Phone Manager>Utilities>Block suspicious app activities` aus.

Derselbe Bildschirm führt eine Liste dessen, was er blockiert hat. Einträge mit der Bezeichnung „Abnormal device control“ (Ungewöhnliche Gerätesteuerung), deren Zeitstempel zu deinen Bereinigungen passen, bestätigen, dass das die Ursache ist. Der Schalter „Blocking alerts“ (Blockierungswarnungen) daneben steuert nur, ob du benachrichtigt wirst. Mit ausgeschalteten Warnungen passiert das also unbemerkt.

#### ACS: Über ADB aktivieren
{: #acs-enable-via-adb }

Wenn dein Gerät keine Möglichkeit bietet, Bedienungshilfen über den Bildschirm zu aktivieren (z. B. ein TV-Gerät), kannst du den Dienst über ADB mit folgendem Befehl aktivieren:

_Du kannst diesen Befehl auch mit dem [Web ADB Helper](https://d4rken.github.io/web-adb/) ausführen._

```bash
adb shell settings put secure enabled_accessibility_services eu.darken.sdmse/.automation.core.AutomationService
```

#### ACS: Automatischer Neustart des Dienstes
{: #acs-automatic-service-restart }

_Hinweis: Das ist eine fortgeschrittene Technik und richtet sich an Power-User._

Du kannst eine zusätzliche Berechtigung gewähren, mit der SD Maid den Dienst selbst neu starten kann.
Die Berechtigung heißt `WRITE_SECURE_SETTINGS` und kann nur [über ADB](https://www.google.com/search?q=android+adb) gewährt werden.
Wenn du das eingerichtet hast, führe diesen Befehl aus, um die Berechtigung zu gewähren:

_Du kannst diesen Befehl auch mit dem [Web ADB Helper](https://d4rken.github.io/web-adb/) ausführen._

```bash
adb shell pm grant eu.darken.sdmse android.permission.WRITE_SECURE_SETTINGS
```

Mit dieser Berechtigung kann SD Maid den [hier beschriebenen](#acs-enable-via-adb) Befehl nutzen, um den Dienst bei Bedarf zu aktivieren.
SD Maid nutzt das nur, wenn du die Bedienungshilfe mindestens einmal manuell aktiviert hast.
Wenn du erneut in die Einrichtung gehst und SD Maid anweist, die Bedienungshilfe nicht zu verwenden, wird diese Technik zum automatischen Neustart nicht mehr genutzt, bis du die Bedienungshilfe wieder manuell aktivierst.

Um SD Maid diese Berechtigung wieder zu entziehen, verwende diesen Befehl:

_Du kannst diesen Befehl auch mit dem [Web ADB Helper](https://d4rken.github.io/web-adb/) ausführen._

```bash
adb shell pm revoke eu.darken.sdmse android.permission.WRITE_SECURE_SETTINGS
```

Wenn du `Operation not allowed: java.lang.SecurityException` erhältst, [sieh hier](https://stackoverflow.com/questions/52079343). Manche Geräte schränken ADB-Berechtigungen ein. Prüfe, ob es in den Entwickleroptionen eine zusätzliche Einstellung gibt (z. B. `Disable Permission Monitoring`), die umgeschaltet werden muss. Nachdem du den Befehl ausgeführt hast, kannst du sie wieder deaktivieren.

#### ACS: Erweiterter Schutz
{: #acs-advanced-protection }

Unter Android 17 und neuer verhindert der Modus [**Erweiterter Schutz**](https://developer.android.com/privacy-and-security/advanced-protection-mode) (Advanced Protection) von Google, dass Apps, die keine reinen Bedienungshilfe-Werkzeuge sind (Screenreader, Schalterzugriff usw.), die Bedienungshilfe nutzen. SD Maid ist ein Wartungs- und Reinigungswerkzeug, daher **kann** seine Bedienungshilfe nicht aktiviert werden, solange der Erweiterte Schutz eingeschaltet ist. Android lehnt den Dienst auf Systemebene ab, selbst wenn du versuchst, ihn über Root oder ADB-Zugriff zu gewähren.

SD Maid kann sich davon nicht selbst ausnehmen: Nur Apps, die Google als Bedienungshilfe-Werkzeuge einstuft, kommen in Frage, und Reinigungs-Apps sind ausdrücklich ausgeschlossen.

Du hast zwei Möglichkeiten:

- **Root oder ADB-Zugriff verwenden** Wenn Root oder ADB-Zugriff eingerichtet ist, leert der App-Reiniger (AppCleaner) von SD Maid App-Caches über einen direkten Systemaufruf und braucht die Bedienungshilfe **nicht**. Mit **Root** gilt das auch für das Bereinigen einzelner ausgewählter Apps. Mit **ADB-Zugriff** funktioniert ein vollständiger Durchlauf „alle Apps bereinigen“ ohne die Bedienungshilfe, aber das Leeren des Caches *bestimmter* ausgewählter Apps ist weiterhin auf sie angewiesen. Richte das in SD Maid unter Einrichtung → [Root-Zugriff](#root-access) oder [ADB-Zugriff](#shizuku) ein.
- **Erweiterten Schutz ausschalten**, wenn du gezielt die Bereinigung über die Bedienungshilfe möchtest. Du findest ihn unter `Einstellungen → Sicherheit & Datenschutz → Erweiterter Schutz` (der genaue Pfad unterscheidet sich je nach Gerät). Wenn du ihn später wieder einschaltest, wird die Bedienungshilfe erneut deaktiviert.

#### ACS: AppOps-Einschränkungen
{: #acs-appops-restrictions }

Android 13+ kann zusätzliche Einschränkungen auf Apps anwenden, die per Sideloading installiert wurden. Wenn SD Maid also nicht aus einer (Google) bekannten Quelle wie Google Play oder anderen App-Stores installiert wurde, kannst du SD Maid manche Berechtigungen möglicherweise nicht direkt gewähren, z. B. eine Bedienungshilfe starten.

##### OxygenOS 15 - OnePlus
{: #oxygenos-15---oneplus }

OnePlus hat in OxygenOS 15 vergessen (oder schlicht darauf verzichtet), die Aktion `Allow restricted settings` („Eingeschränkte Einstellungen zulassen“) in die App-Einstellungen aufzunehmen. Wenn du nicht über Google Play installierst, muss das Installationsprogramm das Flag `INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS` setzen. Wenn du Shizuku eingerichtet hast, kannst du die [App „Install with Options“](https://github.com/zacharee/InstallWithOptions) verwenden.


##### Andere ROMs
{: #other-roms }

Um diese Einschränkungen aufzuheben, öffne im System den Bildschirm mit den App-Einstellungen von SD Maid. Öffne das Menü oben rechts und wähle `Eingeschränkte Einstellungen zulassen`. Danach kannst du zur Liste der Bedienungshilfen zurückgehen und den Dienst von SD Maid aktivieren.

**Hinweis:**
Um die Benutzerfreundlichkeit zu verbessern (das ist Sarkasmus), hat Google entschieden, dass diese Einstellung nur sichtbar ist, wenn:

* Du die Meldung „Eingeschränkte Einstellung“ mindestens einmal gesehen hast, nachdem du auf den Bedienungshilfe-Eintrag von SD Maid getippt hast
* Du die Systemeinstellungen geöffnet hast und von Hand zur Einstellungsseite von SD Maid navigiert bist, nicht über eine andere App oder Verknüpfungen.

😮‍💨


Einige zusätzliche Hintergrundinformationen [findest du hier](https://blog.esper.io/android-13-sideloading-restriction-harder-malware-abuse-accessibility-apis/).

<a href="/assets/images/setup/acs-restricted-settings-1.webp"><img src="/assets/images/setup/acs-restricted-settings-1.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-2.webp"><img src="/assets/images/setup/acs-restricted-settings-2.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-3.webp"><img src="/assets/images/setup/acs-restricted-settings-3.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-4.webp"><img src="/assets/images/setup/acs-restricted-settings-4.webp" width="100" alt=""></a>

## Root-Zugriff
{: #root-access }

Mit Root-Zugriff hat SD Maid vollen Zugriff auf dein Gerät und kann den gründlichsten Scan durchführen. Meist bedeutet das, dass du deinen Bootloader entsperrt und Magisk eingerichtet hast.

Wenn SD Maid annimmt, dass dein Gerät gerootet sein könnte, fragt SD Maid zuerst nach Root-Zugriff. Wird Root-Zugriff gewährt, nutzt SD Maid ihn, um so viele der anderen Berechtigungen wie möglich zu gewähren. Root-Zugriff ist die höchste Zugriffsstufe, die du einer App geben kannst. Die anderen Berechtigungen sind dann nur noch für Komfort und Leistung nötig, weshalb sie während der Einrichtung keine zusätzliche Zustimmung erfordern, wenn SD Maid bereits Root hat.

## ADB-Zugriff
{: #shizuku }

Mit ADB-Zugriff kann SD Maid die Debugging-Schnittstelle von Android (ADB) für erweiterte Vorgänge nutzen. Dafür ist eine separate App nötig, die einen ADB-Dienst auf deinem Gerät betreibt und anderen Apps Zugriff darauf gewährt: [Porter](#porter) oder [Shizuku](#using-shizuku). Es ist eine Modifikation an deinem Handy, die auch andere Apps nutzen können.

Sie ähnelt Root, ist aber nicht dasselbe. Die Berechtigungen sind etwas weniger mächtig, dafür ist die Modifikation nicht dauerhaft und erfordert kein Entsperren des Bootloaders.

### Porter
{: #porter }

[Porter](https://porter.darken.eu) ist mein gepflegter Fork von Shizuku, mit optionaler Root-Unterstützung. Auf seiner Website steht, wie du es installierst und startest. Unter Android 11 und neuer kannst du es per kabellosem Debugging ohne Computer starten.

Wenn du ADB-Zugriff zum ersten Mal einrichtest, verwende Porter. Die SD-Maid-Builds von GitHub und F-Droid verweisen dich auf Porter, wenn keine App für ADB-Zugriff installiert ist. Der Google-Play-Build verweist dich auf Shizuku bei Google Play, funktioniert aber genauso mit Porter.

Wenn sowohl Porter als auch Shizuku installiert sind, verwendet SD Maid Porter. Die Einrichtungskarte zeigt dann `Porter ist installiert, daher wartet SD Maid auf Porter statt auf Shizuku`. Starte Porter, um fortzufahren, oder deinstalliere es, wenn SD Maid Shizuku verwenden soll.

### Shizuku verwenden
{: #using-shizuku }

Es gibt zwei Builds von Shizuku:

* [Der Fork von thedjchi](https://github.com/thedjchi/Shizuku) ([Einrichtungsanleitung](https://github.com/thedjchi/Shizuku/wiki/setup)) wird aktiv gepflegt und funktioniert Berichten zufolge auf Geräten, auf denen sich das Original [nie verbindet](#shizuku-never-connects).
* [Das Original von RikkaApps](https://shizuku.rikka.app/guide/setup/) funktioniert auf den meisten Geräten weiterhin gut.

Beide lassen sich nicht gleichzeitig installieren. Wenn das Original bei dir bereits funktioniert, musst du nicht wechseln.

### Zugriff gewähren
{: #granting-access }

Nachdem du Porter oder Shizuku eingerichtet hast:

* Wähle in der Einrichtung von SD Maid `ADB-Zugriff verwenden`. Das ist eine interne Einstellung von SD Maid, mit der du die Nutzung von ADB-Zugriff durch SD Maid aktivieren oder deaktivieren kannst
* Nachdem du zugestimmt hast, fragt ein Dialog von Porter oder Shizuku, ob SD Maid es nutzen darf
* Bestätige den Dialog
* Die Einrichtungskarte `ADB-Zugriff` zeigt, ob SD Maid eine Verbindung herstellen kann

Wenn du SD Maid bereits Root-Zugriff gewährt hast, schaltet das Einrichten von ADB-Zugriff keine zusätzlichen Funktionen frei, verbessert aber die Zuverlässigkeit. Wenn Aktionen mit Root-Berechtigung fehlschlagen, versucht SD Maid es erneut mit ADB-Zugriff.

### ADB-Zugriff: Fehlerbehebung
{: #shizuku-troubleshooting }

Den Status des ADB-Zugriffs von SD Maid kannst du unter `Einstellungen > Einrichtung` auf der Einrichtungskarte `ADB-Zugriff` prüfen. Dort sollte `SD Maid kann jetzt ADB-Zugriff über Porter nutzen.` (bzw. `über Shizuku`) stehen.

* `Porter hat SD Maid noch nicht erlaubt.`: Bestätige den Zugriffsdialog oder erlaube SD Maid in der App Porter (bzw. Shizuku).
* `Porter verweigert SD Maid den Zugriff.`: SD Maid wurde dauerhaft abgelehnt. Öffne Porter (bzw. Shizuku) und erlaube SD Maid dort.
* `Porter aktualisieren, um es mit SD Maid zu verwenden.` oder `Diese Version von SD Maid ist zu alt für Porter.`: Die beiden Apps haben keine kompatiblen Versionen. Aktualisiere die App, die auf der Karte genannt wird.

#### Shizuku verbindet sich nie
{: #shizuku-never-connects }

Die Karte zeigt `ADB-Zugriff fehlgeschlagen` und `Shizuku läuft, konnte SD Maid jedoch nicht mit seinem Hilfsdienst verbinden.` Ältere Versionen zeigten `Waiting for link with Shizuku` oder `Shizuku link failed`.

Öffne die App Shizuku. Wenn dort steht, dass es läuft, und SD Maid in der Liste der autorisierten Apps steht, ist Shizuku korrekt eingerichtet, und eine erneute Einrichtung hilft nicht. Was fehlschlägt, ist der Hilfsdienst von Shizuku: Er stürzt beim Start ab, bevor er sich zurückmelden kann, und SD Maid gibt nach etwa 15 Sekunden auf. `Erneut versuchen` führt dieselbe Prüfung aus und scheitert auf dieselbe Weise.

Das ist ein Fehler in Shizuku ([RikkaApps/Shizuku#1198](https://github.com/RikkaApps/Shizuku/issues/1198)), nicht in SD Maid, und er betrifft jede App, die den Hilfsdienst von Shizuku nutzt. Betroffen sind MediaTek-Geräte sowie Geräte mit HyperOS oder MIUI.

[Der Fork von thedjchi](https://github.com/thedjchi/Shizuku) behebt das Berichten zufolge. So wechselst du:

* Deinstalliere zuerst das Original, beide können nicht nebeneinander bestehen
* Entkopple es vom kabellosen Debugging, falls du das genutzt hast
* Installiere den Fork aus seinen [Releases](https://github.com/thedjchi/Shizuku/releases) und folge seiner [Einrichtungsanleitung](https://github.com/thedjchi/Shizuku/wiki/setup)
* Gewähre SD Maid und allen anderen Apps, die Shizuku genutzt haben, erneut die Shizuku-Berechtigung
