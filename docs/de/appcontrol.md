---
title: "App-Kontrolle"
lang: de
translation_key: appcontrol
permalink: /de/appcontrol
source_hash: 1a4a7450fc5b86ca
---
# App-Kontrolle (AppControl)
{: #appcontrol }
Die App-Kontrolle (AppControl) ist deine zentrale Anlaufstelle für alle Aktionen rund um die Steuerung installierter Apps. Nach dem Scan erhältst du eine durchsuchbare Liste deiner installierten Apps. System-Apps werden standardmäßig nicht angezeigt, das muss in den Einstellungen aktiviert werden.
Manche Optionen sind nur verfügbar (möglich), wenn SD Maid Root-Rechte gewährt wurden.

Manche Aktionen lassen sich auf mehrere Apps anwenden, manche nur auf einzelne Einträge. Um Aktionen auf mehrere Apps anzuwenden, drücke lange auf eine App und tippe dann weitere Apps an, um sie auszuwählen.

## Aktionen
{: #actions }
Manche dieser Aktionen sind nur in den App-Details erreichbar, die du durch Antippen eines Eintrags in der Liste öffnest.

### App starten
{: #run-app }
Startet die App. Ihre primäre Aktivität, meist dieselbe Aktion, die auch der Launcher ausführen würde.

### Beenden erzwingen
{: #force-stop }
Versucht, das Beenden der App zu erzwingen. Funktioniert am besten mit Root oder ADB/Shizuku, ansonsten wird die Bedienungshilfe verwendet.

### APK exportieren
{: #export-apk }
Versucht, die `.apk`-Datei der App aus dem internen privaten Speicher in den Download-Ordner deines öffentlichen Speichers zu kopieren. Wenn die App verschlüsselt ist, erfordert diese Aktion Root, unverschlüsselte Apps lassen sich auch ohne Root exportieren.

### App einfrieren
{: #freeze-app }
Erfordert Root oder ADB/Shizuku. Deaktiviert die App. Die App ist nicht sichtbar und kann sich nicht selbst starten. Damit sie wieder funktioniert, muss sie erneut aktiviert werden. Es gehen keine Daten verloren, und dieser Zustand bleibt auch bestehen, wenn du SD Maid deinstallierst. Jede gute App zum Aktivieren/Deaktivieren von Apps kann Apps aktivieren, die mit SD Maid deaktiviert wurden, und umgekehrt.

### App löschen
{: #delete-app }
Löscht die App und ihre Daten.


## Einstellungen
{: #settings }
### Größen bestimmen
{: #determine-size }
Kann deaktiviert werden, um das Laden der App-Liste zu beschleunigen. Zu bestimmen, wie groß eine App ist, kostet viel Zeit.

### Aktivität bestimmen
{: #determine-activity }
Kann deaktiviert werden, um das Laden der App-Liste zu beschleunigen. Zu bestimmen, ob eine App kürzlich aktiv war, kostet viel Zeit.
