---
title: "Swiper"
lang: de
translation_key: swiper
permalink: /de/swiper
source_hash: 4c7d8b7a30d3a36e
---
# Swiper
{: #swiper }

Swiper hilft dir, bestimmte Ordner schneller auszumisten als mit einem Dateimanager. Du gehst die Dateien einzeln durch und wischst jede, um sie zu behalten, zu löschen oder zu überspringen. Nichts wird gelöscht, bevor du deine Entscheidungen überprüft und bestätigt hast.

## Eine Sitzung starten
{: #starting-a-session }

1. Öffne Swiper über das Dashboard und tippe auf `Ordner auswählen`. Du kannst Ordner, einzelne Dateien oder beides auswählen.
2. Lege auf der Sitzungskarte optional einen Dateityp-Filter und eine Sortierreihenfolge fest.
3. Tippe auf `Scannen`. Ordner werden einschließlich aller Unterordner durchsucht.
4. Tippe auf `Los wischen`.

Du kannst eine Sitzung auch aus der [Speicheranalyse (StorageAnalyzer)](/de/storageanalyzer) heraus erstellen: Wähle dort Einträge aus und wähle `Swiper-Sitzung erstellen`.

Gute Startpunkte sind deine Kamerabilder, der Download-Ordner und Screenshots.

### Dateitypen
{: #file-types }

`Nur diese Dateitypen anzeigen` beschränkt die Sitzung auf Bilder, Videos, Audio, Dokumente oder Archive. Du kannst auch eigene Dateiendungen eingeben, z. B. `apk, apks`. Wenn nichts ausgewählt ist, werden alle Dateien angezeigt. Dateien werden nur anhand ihrer Endung zugeordnet.

### Sortierreihenfolge
{: #sort-order }

Dateien werden standardmäßig nach ihrem letzten Änderungsdatum angezeigt, die ältesten zuerst. Du kannst auch nach neuesten zuerst, nach Name oder nach größten zuerst sortieren.

Filter und Sortierreihenfolge lassen sich nicht mehr ändern, nachdem ein Scan Dateien gefunden hat. Um sie zu ändern, starte eine neue Sitzung.

### Einen ganzen Speicher scannen
{: #scanning-a-whole-storage }

Wenn du die oberste Ebene eines Speichers auswählst, z. B. deinen gesamten internen Speicher, landet jede Datei darauf in der Sitzung, auch Fotos und App-Medien. SD Maid bittet dich vor dem Scannen um eine Bestätigung. Wenn du später Dateien aus einer solchen Sitzung löschst, musst du zuerst `Mir ist bewusst, dass diese Dateien unwiderruflich gelöscht werden.` ankreuzen.

## Wischen
{: #swiping }

| Geste | Standardaktion |
|---|---|
| Nach rechts wischen | Behalten |
| Nach links wischen | Löschen |
| Nach oben wischen | Überspringen |
| Nach unten wischen | Letzte Entscheidung rückgängig machen |

Die Schaltflächen unten tun dasselbe. Wenn [Wischrichtungen tauschen](#swap-swipe-directions) aktiviert ist, sind links und rechts vertauscht.

* **Überspringen** lässt eine Datei unentschieden. Übersprungene Dateien kommen nach den übrigen erneut an die Reihe.
* **Rückgängig machen** geht schrittweise durch deine letzten Entscheidungen zurück, bis zu 50.
* **Lange auf Überspringen drücken**, um eine Datei von künftigen Swiper-Scans auszuschließen. Das erstellt einen Pfad-[Ausschluss](/de/exclusions) und entfernt die Datei aus der Sitzung.
* Die Schaltflächen auf der Karte öffnen die Datei in einer anderen App oder zeigen eine Vollbildvorschau.
* Tippe auf ein Vorschaubild in der Leiste, um zu dieser Datei zu springen. Wenn du sie erneut wischst, ersetzt das deine frühere Entscheidung.

Du kannst jederzeit aufhören. Deine Entscheidungen werden mit der Sitzung gespeichert, und du kannst später weitermachen. Wenn SD Maid neu startet, werden Dateien, die es nicht mehr findet, aus der Sitzung entfernt. Bis dahin kann die Sitzung noch Dateien auflisten, die anderweitig gelöscht oder verschoben wurden.

## Überprüfen und löschen
{: #reviewing-and-deleting }

Tippe auf `Bewerten`, um alle Entscheidungen zu sehen. Wenn keine unentschiedenen Dateien mehr übrig sind, öffnet sich die Überprüfung von selbst.

* Ändere Entscheidungen pro Datei oder drücke lange, um mehrere Dateien auf einmal auszuwählen.
* Tippe auf eine Datei, um zu ihr zurückzuspringen.
* `Löschen` zeigt, bevor etwas passiert, wie viele Dateien und wie viel Speicherplatz betroffen sind und in welchen Ordnern.

Löschen ist endgültig. Dateien werden nicht in einen Papierkorb verschoben, und SD Maid kann sie nicht wiederherstellen.

Nach dem Löschen verlassen behaltene und gelöschte Dateien die Sitzung. Unentschiedene Dateien bleiben für später erhalten. Sobald nichts mehr übrig ist, wird die Sitzung entfernt. Wenn sich manche Dateien nicht löschen ließen, bleiben sie in der Sitzung, und du kannst es erneut versuchen.

Wenn du eine Sitzung entfernst, werden nur deine Entscheidungen verworfen. Dateien werden dabei nicht angerührt.

### Gut zu wissen
{: #things-to-know }

* **Behalten gilt nur für die aktuelle Sitzung.** Wenn du eine neue Sitzung für denselben Ordner startest, tauchen behaltene Dateien wieder auf. Um eine Datei dauerhaft auszublenden, schließe sie aus (lange auf Überspringen drücken).
* **Eine Sitzung erfasst keine neuen Dateien.** Dateien, die nach dem Scan zum Ordner hinzukommen, erscheinen nicht. Starte eine neue Sitzung, um sie einzubeziehen.
* **Archive schließen APK-Dateien ein.**
* **Versteckte Dateien sind enthalten.**

## Kostenlose Version
{: #free-version }

Ohne das Upgrade ist Swiper auf 2 Sitzungen mit je 50 Dateien begrenzt. Ist eine Sortierreihenfolge festgelegt, erhältst du die ersten 50 Dateien in dieser Reihenfolge.

## Einstellungen
{: #settings }

### Wischrichtungen tauschen
{: #swap-swipe-directions }

Nach links wischen zum Behalten, nach rechts zum Löschen. Standardmäßig aus.

### Dateidetails anzeigen
{: #show-file-details }

Zeigt Dateiname, Größe, Datum und Ordner auf dem Wisch-Bildschirm. Standardmäßig an.

### Haptisches Feedback
{: #haptic-feedback }

Vibriert, wenn du eine Datei behältst oder löschst. Standardmäßig an.
