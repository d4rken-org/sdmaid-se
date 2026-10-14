---
title: "Speicheranalyse"
lang: de
translation_key: storageanalyzer
permalink: /de/storageanalyzer
source_hash: 2dbf8557900cc687
---
# Speicheranalyse (StorageAnalyzer)
{: #storage-analyzer }

Die Speicheranalyse (StorageAnalyzer) beantwortet die Frage: „Was belegt so viel Platz auf meinem Speicher?“

Sie sieht vielleicht aus wie ein Dateibrowser, ist aber keiner. Alles ist nach Größe sortiert, sodass du den größten Einträgen bis zu dem folgen kannst, was deinen Speicher tatsächlich füllt.

## Speicher
{: #storages }

Der erste Bildschirm zeigt jeden Speicher deines Geräts:

* **Hauptspeicher**: der eingebaute Speicher deines Geräts, einschließlich Apps und Systemdaten.
* **Sekundärer Speicher**: meist eine herausnehmbare SD-Karte.
* **Tragbarer Speicher**: z. B. ein per USB angeschlossener USB-Stick.

Jede Karte zeigt den freien und belegten Platz und wie stark sich der Speicher in den letzten 7 Tagen verändert hat, siehe [Speichertrend](#storage-trend).

## Kategorien
{: #categories }

Beim Öffnen wird ein Speicher gescannt und der belegte Platz in Kategorien aufgeteilt:

* **Apps**: installierte Apps mit ihrem Code, ihren Daten, Caches und Medien.
* **Benutzerdateien**: Fotos, Videos, Downloads und alle anderen Daten, die nicht zu einer bestimmten App gehören.
* **Systemdaten**: das Betriebssystem und alles, was sich nirgendwo sonst zuordnen ließ.
* **Andere Nutzer**: Apps und Dateien anderer Nutzer oder Arbeitsprofile auf diesem Gerät.

Systemdaten und Andere Nutzer gibt es nur beim Hauptspeicher.

### Apps
{: #apps }

Apps sind nach Größe sortiert und lassen sich nach Namen durchsuchen. Auch System-Apps können hier samt ihren Daten aufgeführt werden. Bei System-Apps ohne installiertes Update zählt der eingebaute App-Code zu den Systemdaten. Öffnest du eine App, siehst du, wofür ihr Platz verwendet wird:

* **App-Code**: die App selbst. Durch Deinstallieren oder Archivieren der App wird dieser Platz frei.
* **App-Daten**: Daten, Einstellungen und Caches.
* **App-Medien**: Mediendateien dieser App, z. B. empfangene WhatsApp-Fotos.
* **Zusätzliche App-Dateien**: Daten, die die App außerhalb ihrer eigenen Ordner abgelegt hat, z. B. Fotos oder Downloads in einem Ordner auf oberster Ebene.

`Einstellungen anzeigen` öffnet die Systemeinstellungsseite der App.

### Warum die Zahlen nicht mit den Android-Einstellungen übereinstimmen
{: #why-the-numbers-dont-match-androids-settings }

Die Kategorien der Speicheranalyse stimmen nicht genau mit der Aufschlüsselung in den Android-Einstellungen überein.

**Systemdaten** ist das, was vom belegten Platz übrig bleibt, nachdem Apps, Benutzerdateien und Andere Nutzer gezählt wurden. Alles, was SD Maid nicht sehen oder zuordnen kann, landet dort: geschützte Systembereiche, Speicher anderer Nutzer, der nicht gemessen werden konnte, Dateisystem-Overhead und der Unterschied zwischen der angegebenen und der tatsächlichen Größe des Speichers.

Ohne die Berechtigungen [App-Inventar](/de/setup#app-inventory) und [Nutzungsstatistiken](/de/setup#usage-statistics) lassen sich Apps nicht messen. Ihr Platz erscheint dann als Systemdaten.

## Zugriff
{: #access }

Was die Speicheranalyse sehen kann, hängt vom Zugriff ab, den SD Maid hat:

* **Ohne Root** werden App-Code und private App-Daten nur als Summen angezeigt und lassen sich nicht durchsuchen. Unter Android 13 und neuer braucht auch `Android/data` Root- oder ADB-Zugriff.
* **Mit [Root](/de/setup#root-access) oder [ADB-Zugriff](/de/setup#shizuku)** kannst du die Systemdaten durchsuchen. Root ermöglicht außerdem das Durchsuchen von App-Code und privaten App-Daten sowie die vollständige Messung anderer Nutzer.
* **Ohne die Berechtigung App-Inventar** lassen sich Dateien nicht den Apps zuordnen, denen sie gehören. Benutzerdateien werden dann schreibgeschützt angezeigt: Du siehst die Größen, aber Löschen, Ausschließen, das Erstellen von Filtern und Swiper-Sitzungen sind deaktiviert.

Einträge, auf die SD Maid nicht zugreifen kann, zeigen beim Antippen `Kein direkter Zugriff möglich`.

Ordner mit mehr als 100.000 Einträgen werden nur summiert und lassen sich nicht durchsuchen.

## Aktionen
{: #actions }

Tippe auf eine Datei, um sie in einer anderen App zu öffnen. Drücke lange auf Einträge, um sie auszuwählen, dann:

* **Löschen**: löscht die ausgewählten Einträge nach einer Bestätigung. Kostenlos nutzbar.
* **Ausschließen**: erstellt einen [Ausschluss](/de/exclusions) für die ausgewählten Pfade. Diese Ausschlüsse gelten für alle Werkzeuge. Die Speicheranalyse selbst verwendet keine Ausschlüsse, da sie alles sehen muss, um korrekte Summen zu berechnen.
* **Filter**: erstellt einen [benutzerdefinierten Filter für den System-Reiniger (SystemCleaner)](/de/systemcleaner#custom-filters) für die ausgewählten Einträge. Erfordert das Upgrade.
* **Swiper-Sitzung erstellen**: startet eine [Swiper](/de/swiper)-Sitzung mit den ausgewählten Einträgen.

Systemdaten und die Inhalte anderer Nutzer lassen sich nur durchsuchen. Löschen ist dort nicht möglich.

## Speichertrend
{: #storage-trend }

SD Maid erfasst alle paar Stunden und nach jeder Bereinigung den freien Platz deiner Speicher. `Speichertrend` zeigt das als Diagramm, mit Markierungen dafür, wie viel jede Bereinigung freigegeben hat.

* Die Speicherkarten zeigen, wie stark sich jeder Speicher in den letzten 7 Tagen verändert hat.
* Das Dashboard zeigt die gemeinsame Veränderung aller Speicher oder, wenn sich der Speicher füllt, stattdessen eine Schätzung der verbleibenden Tage.
* Wie lange Snapshots aufbewahrt werden, legst du unter `Einstellungen > Verlauf > Speicherverlauf` fest. Standard sind 90 Tage, bis zu 365.

Das Speichertrend-Diagramm erfordert das Upgrade. Die 7-Tage-Veränderung auf den Speicherkarten und im Dashboard sowie die Schätzung der verbleibenden Tage sind kostenlos.

## Einstellungen
{: #settings }

### Schwellenwert für wenig Speicher
{: #low-storage-threshold }

Wenn der freie Platz unter diesen Wert fällt, betrachtet SD Maid den Speicher als fast voll. Standard ist `Automatisch`: 5 % der Speichergröße, höchstens 2 GB.

### Warnung bei wenig Speicher
{: #low-space-warning }

Erfordert das Upgrade. Warnt, wenn der freie Platz auf deinem Hauptspeicher den Schwellenwert für wenig Speicher erreicht. Mit mindestens 5 Tagen durchgehendem Verlauf kann sie auch früher warnen: wenn der Speicher den Schwellenwert voraussichtlich innerhalb von 14 Tagen erreicht und der freie Platz bereits höchstens 20 % des Speichers beträgt, begrenzt auf 25 GB. Standardmäßig aus.
