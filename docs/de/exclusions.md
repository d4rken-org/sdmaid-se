---
title: "Ausschlüsse"
lang: de
translation_key: exclusions
permalink: /de/exclusions
source_hash: d5c3aea2efa43796
---
# Ausschlüsse
{: #exclusions }

Mit der Ausschluss-Funktion von SD Maid 2/SE kannst du bestimmte Ordner oder Dateien festlegen, die die App bei System-Scans und Wartungsaufgaben ignorieren soll. Das ist nützlich, wenn es Daten gibt, die von den Vorgängen der App unberührt bleiben sollen. Wenn du Ausschlüsse festlegst, überspringt SD Maid 2/SE diese Verzeichnisse oder Dateien bei Aufgaben wie Leichenfinder (CorpseFinder), App-Reiniger (AppCleaner) und anderen Systemverwaltungsfunktionen.

Am einfachsten fügst du einen Ausschluss direkt aus den Ergebnissen hinzu. Du kannst entweder die Schaltfläche „Ausschließen“ bei einzelnen Einträgen verwenden oder Einträge lange drücken bzw. mehrere auswählen und dann die Option „Ausschließen“ wählen. Außerdem lassen sich Ausschlüsse für mehr Kontrolle manuell über die App-Einstellungen hinzufügen, indem du Pfade oder Platzhaltermuster angibst.

Ausschlüsse lassen sich über das Menü exportieren und importieren. Der Import von Ausschlüssen wird nur zwischen ähnlichen Versionen von SD Maid unterstützt. Liegen zu viele Updates zwischen der Import- und der Export-Version, kann ein Fehler auftreten. In diesem Fall kannst du versuchen, auf einer älteren Version zu importieren und die Ausschlüsse dann erneut zu exportieren.

<img src="/assets/images/exclusions/overview-1.webp" width="150">
<img src="/assets/images/exclusions/overview-2.webp" width="150">
<img src="/assets/images/exclusions/overview-3.webp" width="150">
<img src="/assets/images/exclusions/overview-4.webp" width="150">

## Typen
{: #types }

SD Maid unterstützt 3 verschiedene Arten von Ausschlüssen:

### App-Ausschlüsse
{: #app-exclusions }

App-Ausschlüsse beruhen auf dem Paketnamen der jeweiligen App. Sie lassen sich über das Werkzeug App-Kontrolle (AppControl) erstellen. Wähle einfach eine App aus der Liste und nutze die zugehörige Aktion `Ausschließen`.

### Pfadausschlüsse
{: #path-exclusions }

Pfadausschlüsse lassen sich erstellen, indem du in der Speicheranalyse (StorageAnalyzer) Pfade auswählst (lange drückst).

### Segmentausschlüsse 
{: #segment-exclusions }
Segmentausschlüsse sind eine Art Pfadausschluss, die keine exakte Übereinstimmung erfordert. Ein Segment ist ein Teil eines Pfads, etwa ein Ordner. Der Pfad `/sdcard/DCIM/Camera` besteht aus 3 Segmenten. Mit einem Segmentausschluss für `DCIM/Camera` könntest du alle deine Kamerabilder sowohl im internen Speicher (`/storage/emulated/0/DCIM/Camera`) als auch im externen Speicher (`/sdcard/DCIM/Camera`) ausschließen.

Wenn du die Option `Teilübereinstimmungen zulassen` ankreuzt, könntest du einen Ausschluss für `DCIM/Cam` erstellen, der sowohl auf den Ordner `Cam` als auch auf den Ordner `Camera` passt.

## Werkzeuge
{: #tools }

Jedes Werkzeug von SD Maid hat eine andere Sicht auf die Daten. Nicht alle Ausschlüsse wirken sich auf alle Werkzeuge gleich aus.

### Leichenfinder
{: #corpsefinder }

Der Leichenfinder weiß, was eine „App“ ist und welche Dateien zu einer App gehören. Er beachtet alle Ausschlusstypen. Ein App-Ausschluss bedeutet, dass alle Dateien, die der Leichenfinder findet und einer ausgeschlossenen App zuordnet, ausgeschlossen werden.

Unterstützte Ausschlusstypen: `APP`, `PATH`, `SEGMENTS`

### System-Reiniger
{: #systemcleaner }

Der System-Reiniger (SystemCleaner) arbeitet nur mit Pfaddaten. Er weiß nicht, was eine „App“ ist oder welche Dateien zu einer App gehören.

Unterstützte Ausschlusstypen: `PATH`, `SEGMENTS`

### App-Reiniger
{: #appcleaner }

Der App-Reiniger beachtet alle Ausschlusstypen. Mit Ausschlüssen kannst du Scans beschleunigen, wenn es Apps mit vielen zu scannenden Dateien gibt.

Unterstützte Ausschlusstypen: `APP`, `PATH`, `SEGMENTS`

### Duplikat-Entferner
{: #deduplicator }

Der Duplikat-Entferner (Deduplicator) weiß nicht, was „Apps“ sind, und arbeitet nur mit Dateidaten.

Unterstützte Ausschlusstypen: `PATH`, `SEGMENTS`

### Medienkomprimierung
{: #media-squeeze }

Die Medienkomprimierung (Media Squeeze) arbeitet nur mit Dateidaten. Ausgeschlossene Dateien werden nicht komprimiert.

Unterstützte Ausschlusstypen: `PATH`, `SEGMENTS`

### Swiper
{: #swiper }

Swiper arbeitet nur mit Dateidaten. Ausgeschlossene Dateien werden nicht in neue Sitzungen aufgenommen. Wenn du beim Wischen lange auf `Überspringen` drückst, wird die aktuelle Datei ausgeschlossen.

Unterstützte Ausschlusstypen: `PATH`, `SEGMENTS`

### Speicheranalyse
{: #storageanalyzer }

Die Speicheranalyse unterstützt keine Ausschlüsse, da alle Inhalte gescannt werden müssen, um die korrekten Gesamtgrößen zu berechnen.
