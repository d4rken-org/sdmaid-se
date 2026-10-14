---
title: "System-Reiniger"
lang: de
translation_key: systemcleaner
permalink: /de/systemcleaner
source_hash: 1ced6df9b00f1e05
---
# System-Reiniger (SystemCleaner)
{: #system-cleaner }

Der System-Reiniger (SystemCleaner) ist im Grunde eine Allzweck-Suchmaschine. Er durchsucht den gesamten zugänglichen Speicher und gleicht gefundene Dateien und Ordner mit einer Reihe von Filtern ab.

Anders als andere Werkzeuge wie der Leichenfinder (CorpseFinder) oder der App-Reiniger (AppCleaner) weiß er nichts über installierte Apps. Er arbeitet ausschließlich mit Dateipfaden und Attributen. Deshalb lassen sich auf den System-Reiniger nur Pfad- und Segmentausschlüsse anwenden.

Jede gefundene Datei durchläuft die aktivierten Filter und wird dem ersten Filter zugeordnet, auf den sie passt.

## Einstellungen
{: #settings }

### Allgemeine Filter
{: #general-filters }

Allgemeine Filter suchen **in allen zugänglichen Speicherbereichen** nach bestimmten Dateitypen, die überall auf dem Gerät vorkommen können.

| Filter | Beschreibung |
|--------|-------------|
| **Leere Ordner** | Leere Ordner aus dem gesamten Gerät. Verschachtelte Ordner können mehrere Scandurchläufe erfordern. |
| **Screenshots** | Screenshots, die älter als ein einstellbares Alter sind, aus mehreren Apps und Orten. |
| **Gelöschte Dateien** | Dateien in Papierkörben verschiedener Apps und Orte, die zum Löschen markiert, aber noch nicht endgültig entfernt sind. |
| **Überflüssige APKs** | Installationsdateien (APKs), deren Version älter oder gleich der bereits installierten App-Version ist. |
| **LOST.DIR Ordner** | Temporäre Dateien, die bei der Übertragung von Daten zwischen Android-Geräten entstehen. |
| **Linux-Dateien** | Dateien und Ordner, die entstehen, wenn der Speicher mit einem Linux-Computer verbunden war. |
| **Mac-Dateien** | Dateien und Ordner, die entstehen, wenn der Speicher mit Apples Finder durchsucht wird (z. B. `.DS_Store`). |
| **Windows-Dateien** | Daten, die der Windows-Explorer auf verbundenen Speichern anlegen kann (z. B. `Thumbs.db`). |
| **Miniaturbilder** | Zwischengespeicherte Vorschauen für Bilder und Videos (z. B. Ordner `.thumbnails`). |
| **Temporäre Systemdateien** | Systembezogene temporäre Dateien, meist kurzlebige Caches für laufende Vorgänge. |
| **Analytik-Dateien** | Daten im Zusammenhang mit Analyse und Fehlerverfolgung. |
| **Werbedateien** | Dateien, die für oder von Werbung angelegt wurden. |
| **System-Protokolldateien** | Protokolldateien des Systems und von Systemdiensten (z. B. `*.log`) an verschiedenen Orten. |

### Spezifische Filter
{: #specific-filters }

Spezifische Filter zielen auf **feste, bekannte Pfade** auf dem Gerät ab, an denen bestimmte Arten von Daten gespeichert werden.

| Filter | Beschreibung |
|--------|-------------|
| **Rückstände eines Systemabsturzes** | Wenn native Prozesse abstürzen (z. B. Spiele), werden hier „Tombstone“-Dateien angelegt. |
| **Nutzungsstatistiken** | Dateien im Zusammenhang mit App-Nutzungsstatistiken. |
| **ANR Fehler** | Traces von „Application Not Responding“-Ereignissen (App reagiert nicht). |
| **Lokale temporäre Dateien** | Systembezogene temporäre Dateien (z. B. von Android Studio). |
| **Systemprotokolldateien (datalogger)** | Systemdienst-spezifische Protokolldateien. |
| **Download-Cache** | System-Cache-Ordner, der für verschiedene Zwecke genutzt wird (z. B. OTA-Updates). |
| **Protokoll-Ablageordner** | Ein Bereich, in dem das System Protokolldateien ablegt. |
| **Installer-Cache** | Zwischengespeicherte Daten zu installierten Apps, die von der Paketverwaltung des Systems genutzt werden. |
| **Aktuelle Aufgaben** | Daten zum Aufgabenverlauf im Aufgabenwechsler des Systems. |

### Benutzerdefinierte Filter
{: #custom-filters }

Zusätzlich zu den allgemeinen Filtern, die die App mitbringt, kannst du benutzerdefinierte Filter mit eigenen Kriterien erstellen. Benutzerdefinierte Filter lassen sich unter **Einstellungen > System-Reiniger > Benutzerdefinierte Filter verwalten** erstellen.

> **Warnung**: Benutzerdefinierte Filter können riskant sein. Prüfe vor dem Löschen immer deine Scanergebnisse!

#### Verfügbare Kriterien
{: #available-criteria }

| Kriterium | Beschreibung |
|-----------|-------------|
| **Filterbezeichnung** | Ein Name für deinen Filter |
| **Datenbereiche** | Zu durchsuchende Speicherorte (leer lassen für alle zugänglichen Bereiche) |
| **Dateityp-Einschränkungen** | Ziel sind Dateien, Ordner oder symbolische Links (leer lassen für alle) |
| **Pfadkriterien** | Regeln für den Abgleich von Dateipfaden |
| **Ausschlüsse** | Pfade, die vom Abgleich ausgenommen werden |
| **Namenskriterien** | Regeln für den Abgleich von Dateinamen |
| **Mindestgröße** | Nur Dateien, die größer als dieser Wert sind |
| **Maximalgröße** | Nur Dateien, die kleiner als dieser Wert sind |
| **Mindestalter** | Nur Dateien, die älter als dieser Wert sind |
| **Maximales Alter** | Nur Dateien, die neuer als dieser Wert sind |
| **Pfad-Regex** | Fortgeschritten: Reguläre Ausdrücke für den Pfadabgleich |

#### Modi der Pfadkriterien
{: #path-criteria-modes }

| Modus | Beschreibung | Beispiel |
|------|-------------|---------|
| **Beginnt mit** | Pfad beginnt mit dem Schlüsselwort | `Downloads/temp` passt auf `/sdcard/Downloads/temp/file.txt` |
| **Enthält** | Pfad enthält das Schlüsselwort | `cache` passt auf `/sdcard/app/cache/data.bin` |
| **Endet mit** | Pfad endet mit dem Schlüsselwort | `backup/old` passt auf `/sdcard/backup/old` |
| **Ist gleich** | Pfad stimmt exakt mit dem Schlüsselwort überein | `Download/temp` passt nur auf genau diesen Pfad |

#### Modi der Namenskriterien
{: #name-criteria-modes }

| Modus | Beschreibung | Beispiel |
|------|-------------|---------|
| **Beginnt mit** | Dateiname beginnt mit dem Schlüsselwort | `temp_` passt auf `temp_file.txt` |
| **Enthält** | Dateiname enthält das Schlüsselwort | `.bak` passt auf `document.bak.old` |
| **Endet mit** | Dateiname endet mit dem Schlüsselwort | `.tmp` passt auf `cache.tmp` |
| **Ist gleich** | Dateiname stimmt exakt überein | `thumbs.db` passt nur auf `thumbs.db` |

Jeder Abgleich ist standardmäßig **unabhängig von Groß-/Kleinschreibung**.

#### Datenbereiche
{: #data-areas }

Häufige Bereiche, die du als Ziel wählen kannst:

| Bereich | Beschreibung |
|------|-------------|
| **SDCARD** | Primärer Speicher (intern oder SD-Karte) |
| **PUBLIC_MEDIA** | Medienordner (DCIM, Pictures, Music usw.) |
| **PUBLIC_DATA** | Gemeinsam genutzte App-Daten |
| **PRIVATE_DATA** | App-privater Speicher (erfordert Root) |
| **DOWNLOAD_CACHE** | System-Download-Cache (erfordert Root) |
| **PORTABLE** | Wechselspeicher (USB/microSD) |

#### Tipps
{: #tips }

- **Leistung**: Lege nur die nötigen Kriterien fest. Zu viele Regex-Muster verlangsamen den Scan.
- **Mehrere Kriterien**: Alle angegebenen Kriterien müssen zutreffen (UND-Verknüpfung).
- **Lange drücken**: Drücke lange auf Einträge, um weitere Optionen zu sehen.
- **Testen**: Nutze die Scan-Vorschau, bevor du das Löschen aktivierst.
- **Import/Export**: Filter lassen sich exportieren und mit anderen Nutzern teilen.

#### Veraltete Optionen
{: #deprecated-options }

Die Option `rootOnly` aus SD Maid 1 ist **nicht mehr verfügbar**. Steuere stattdessen mit **Datenbereiche**, welche Speicherorte gescannt werden.
