---
title: "App-Reiniger"
lang: de
translation_key: appcleaner
permalink: /de/appcleaner
source_hash: 505b031f74bf71a2
---
# App-Reiniger (AppCleaner)
{: #app-cleaner }

Der App-Reiniger (AppCleaner) ist ein app-bewusstes Werkzeug, das „entbehrliche“ Dateien findet. Eine Datei gilt als entbehrlich, wenn sie ohne negative Folgen gelöscht werden kann und entweder nicht notwendig ist oder bei Bedarf neu erstellt wird. Die meisten Arten von Dateien in einem „Cache“ fallen unter diese Definition.

## Einstellungen
{: #settings }

### System-Apps einbeziehen
{: #include-system-apps }

Diese Einstellung bezieht System-Apps in die Ergebnisse ein. Sie ist standardmäßig nicht aktiviert, da manche System-Apps (je nach ROM) negativ darauf reagieren, wenn ihr Cache geleert wird. Manche System-Apps haben außerdem keine Schaltfläche „Cache leeren“, wodurch SD Maid beim Leeren des Caches über die Bedienungshilfe hängen bleiben kann.

### Andere Benutzer einbeziehen
{: #include-other-users }

Erfordert Root. Wenn aktiviert, durchsucht SD Maid zusätzlich zum Benutzer, unter dem SD Maid gerade läuft, auch die Ordner anderer Benutzer.

### Laufende Apps einbeziehen
{: #include-running-apps }

Ob laufende Apps einbezogen werden sollen oder nicht.

### Mindestgröße
{: #minimum-size }

Wie groß der Cache einer App sein muss, damit er aufgeführt wird. Eine Mindestgröße festzulegen ist ein guter Kompromiss zwischen Nutzen und Geschwindigkeit. Den Cache einer App zu leeren, wenn er nur wenige KB groß ist, bringt in der Regel keinen Vorteil. Das gilt für die Gesamtmenge an Cache, die für eine App gefunden wurde (unter Berücksichtigung aller aktiven Filter).

### Mindestalter
{: #minimum-age }

Damit lassen sich nur alte Cache-Dateien löschen. Wird auf jede Datei und jeden Ordner in den Ergebnissen angewendet.

### Unzugängliche Caches einbeziehen
{: #include-inaccessible-caches }

Je nach den Berechtigungen, die SD Maid zur Verfügung stehen, sind manche Dateien nicht direkt zugänglich. Meist betrifft das den privaten Standard-Cache von Apps (z. B. `/data/data/eu.darken.sdmse/cache`). Wenn diese Einstellung aktiviert ist, versucht SD Maid, über eine Bedienungshilfe zu den Systemeinstellungen zu navigieren und dort für die betreffende App die Schaltfläche `Clear cache` („Cache leeren“) zu drücken.

### Filter
{: #filter }

Du kannst verschiedene Filter aktivieren oder deaktivieren, um zu beeinflussen, welche Ergebnisse der App-Reiniger findet.

#### Allgemeine Filter
{: #common-filters }

Allgemeine Filter zielen auf verschiedene Dateitypen in allen Apps ab.

#### Spezifische Filter
{: #specific-filters }

Spezifische Filter zielen auf bestimmte Apps ab, z. B. WhatsApp oder Telegram.

## Fehlerbehebung
{: #troubleshooting }
### `com.miui.securitycenter`
{: #commiuisecuritycenter }
#### Fehlende Berechtigung `GET_USAGE_STATS`
{: #missing-get_usage_stats-permission }

MIUI und HyperOS verwenden eine zusätzliche App namens „Security“ bzw. „Security Center“ („Sicherheit“, Paketname: `com.miui.securitycenter`), um die Systemdetails einer App anzuzeigen. Der normale Ablauf, um den Cache einer App zu leeren, ist: `Clear data` („Daten löschen“) antippen, `Clear cache` („Cache leeren“) auswählen, Aktion bestätigen.

Wenn die Schaltfläche „Clear data“ („Daten löschen“) bei allen Apps ausgegraut ist und sich nicht antippen lässt, fehlt der App „Security Center“ die Berechtigung `GET_USAGE_STATS`. Normalerweise sollte sie standardmäßig gewährt sein. Wie es dazu kommt, ist unklar, die Lösung ist aber, diese Berechtigung erneut zu gewähren.

Gehe zu: `System Settings` > `Security and privacy` > `More security and privacy` > `Special permissions` > `Usage access` > Suche die App `Security` > Gewähre die Berechtigung

Alternativ kannst du, wenn du ADB-Zugriff oder mehr hast, den Befehl `pm grant com.miui.securitycenter android.permission.PACKAGE_USAGE_STATS` ausführen.

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
