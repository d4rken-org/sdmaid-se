---
title: "Medienkomprimierung"
lang: de
translation_key: mediasqueeze
permalink: /de/mediasqueeze
source_hash: 1d878fd5a77634c5
---
# Medienkomprimierung (Media Squeeze)
{: #media-squeeze }

Die Medienkomprimierung (Media Squeeze) komprimiert Fotos und Videos, damit sie weniger Platz belegen. Du tauschst etwas Qualität gegen den Platz, den du zurückbekommst.

**Komprimierung verringert die Qualität dauerhaft und lässt sich nicht rückgängig machen.** Die Originaldatei wird ersetzt, und es wird keine Kopie behalten. Prüfe deine Einstellungen und die Vorschau, bevor du beginnst.

Das Scannen ist kostenlos. Das Komprimieren erfordert das Upgrade.

## So funktioniert es
{: #how-it-works }

1. Wähle die zu durchsuchenden Ordner aus. Es gibt keinen Standardort, wähle z. B. deinen Kameraordner.
2. Lege die Komprimierungsqualität und das Mindestalter fest.
3. Scanne. Jedes Ergebnis zeigt seine Größe und eine geschätzte Ersparnis.
4. Tippe auf einen einzelnen Eintrag, wähle mehrere durch langes Drücken aus oder nutze `Alle komprimieren`. Eine Bestätigung zeigt zuerst die Gesamtgröße und die geschätzte Ersparnis.

Wenn die Auswahl nur Fotos oder nur Videos enthält, zeigt `Vergleich anzeigen` in der Bestätigung das Original und die komprimierte Version nebeneinander. Bei Videos ist der Vergleich ein einzelnes Bild und nur ungefähr.

### Was mit einer Datei passiert
{: #what-happens-to-a-file }

* Sie wird in ihrem eigenen Format neu kodiert: Ein JPEG bleibt ein JPEG, ein WebP bleibt ein WebP, ein MP4 bleibt ein MP4.
* Die Bildauflösung bleibt gleich, außer bei [sehr großen Fotos](#compress-very-large-photos).
* Bei Videos wird nur die Videospur mit einer niedrigeren Bitrate neu kodiert. Die Audiospur wird unverändert übernommen.
* Datum, Ort, Kamera und andere EXIF-Metadaten von Fotos bleiben erhalten. Wenn SD Maid die EXIF-Metadaten eines Fotos nicht lesen oder erhalten kann, lässt es das Foto unverändert.
* XMP-Metadaten bleiben nicht erhalten, z. B. Informationen zu Photo Spheres und Panoramen oder Bewertungen aus Bildbearbeitungsprogrammen.
* Das Änderungsdatum der Datei bleibt erhalten, sodass Galerien sie weiterhin an derselben Stelle einsortieren.
* Wenn die komprimierte Version nicht kleiner ist, bleibt das Original unangetastet.

Die geschätzte Ersparnis ist eine grobe Schätzung anhand von Dateityp und Qualität, keine Messung. Die tatsächliche Ersparnis kann abweichen.

### Speicherorte
{: #locations }

Die Medienkomprimierung braucht direkten Zugriff auf die Dateien und Schreibzugriff auf ihre Ordner. Sie verwendet weder Root noch ADB. Manche Orte, die über die Ordnerauswahl von Android gewählt wurden, z. B. USB-Laufwerke, können nicht verwendet werden und werden aus der Auswahl entfernt.

## Bereits komprimierte Dateien
{: #previously-compressed-files }

SD Maid merkt sich, welche Dateien es bereits komprimiert hat und welche dabei nicht kleiner wurden, und überspringt sie bei späteren Scans. Es erkennt eine Datei an ihrem Inhalt, daher funktioniert das auch, nachdem die Datei umbenannt oder verschoben wurde. Eine Datei erneut zu komprimieren kostet mehr Qualität und spart wenig.

Der Verlauf wird auf deinem Gerät gespeichert und ist Teil der Einstellungssicherung von SD Maid. Wenn du ihn löschst, kommen bereits komprimierte Dateien wieder in Frage, es sei denn, sie tragen einen EXIF-Marker und [EXIF-Marker schreiben](#write-exif-marker) ist aktiviert. Das Löschen des Verlaufs ändert keine Dateien.

## Fotos, die standardmäßig übersprungen werden
{: #photos-that-are-skipped-by-default }

Manche Fotos enthalten Daten, die bei der Komprimierung nicht erhalten bleiben können. SD Maid überspringt sie, wenn es diese Daten erkennt, es sei denn, du aktivierst die passende Einstellung. Die Erkennung ist nicht vollständig: Fotos in Formaten, die es nicht erkennt, werden komprimiert und verlieren diese Daten.

* **HDR- und Tiefenfotos**: HDR-Fotos werden zu normalen Fotos, und Porträtfotos verlieren die Daten hinter ihrer Hintergrundunschärfe.
* **Motion Photos**: Das Standbild bleibt erhalten, der Videoclip wird entfernt.
* **Sehr große Fotos** (8192 Pixel oder mehr in Breite oder Höhe): Breite und Höhe werden halbiert, bei Bedarf erneut halbiert, bis beide unter 8192 Pixeln liegen. Ein 20.000 Pixel breites Panorama ist danach 5.000 Pixel breit.

HEIC-Dateien, die mehrere Bilder enthalten, werden immer übersprungen.

Die Scan-Zusammenfassung zählt Fotos, die wegen HDR- oder Tiefendaten, als Motion Photos oder wegen ihrer sehr großen Abmessungen übersprungen wurden, sowie Dateien, auf die kein Zugriff möglich war. Dateien, die übersprungen wurden, weil sie bereits komprimiert waren, werden nicht gezählt, ebenso wenig HEIC-Dateien mit mehreren Bildern, es sei denn, sie haben auch HDR- oder Tiefendaten.

## Einstellungen
{: #settings }

Komprimierungsqualität, Suchorte und Mindestalter werden vor dem Scannen auf dem Bildschirm der Medienkomprimierung festgelegt. Alles andere findest du unter `Einstellungen > Medienkomprimierung`.

### Komprimierungsqualität
{: #compression-quality }

Standard ist 80. Eine niedrigere Qualität bedeutet kleinere Dateien, aber eine geringere Bildqualität. Unter 40 können Bilder unbrauchbar werden. Ab 95 gibt es wenig zu sparen.

### Mindestalter
{: #minimum-age }

Nur Dateien einbeziehen, die älter als dieser Wert sind. Standard sind 90 Tage, bezogen auf das Änderungsdatum der Datei.

### Mindestdateigröße
{: #minimum-file-size }

Dateien überspringen, die kleiner als dieser Wert sind. Standard ist 512 KB.

### JPEG
{: #jpeg }

JPEG-Bilder (`.jpg`, `.jpeg`) einbeziehen. Standardmäßig an.

### WebP
{: #webp }

WebP-Bilder (`.webp`) einbeziehen. Standardmäßig an. Animierte WebP-Dateien werden nicht erkannt und würden ihre Animation verlieren.

### HEIC/HEIF
{: #heicheif }

HEIC/HEIF-Bilder (`.heic`, `.heif`) einbeziehen. Standardmäßig aus. Erfordert Android 9 oder neuer. HDR-Gain-Maps, Tiefendaten und Live-/Motion-Photo-Anteile können bei HEIC nicht erhalten werden.

### MP4 Video
{: #mp4-video }

MP4-Videos (`.mp4`) einbeziehen. Standardmäßig aus. Um ein Video zu komprimieren, wird auf demselben Speicher freier Platz von etwa 110 % seiner Größe benötigt.

### HDR- & Tiefenfotos komprimieren
{: #compress-hdr--depth-photos }

Siehe [Fotos, die standardmäßig übersprungen werden](#photos-that-are-skipped-by-default). Standardmäßig aus.

### Motion Photos komprimieren
{: #compress-motion-photos }

Behält das Standbild und entfernt den Videoclip. Standardmäßig aus.

### Sehr große Fotos komprimieren
{: #compress-very-large-photos }

Bezieht Fotos ein, die 8192 Pixel oder mehr breit oder hoch sind, und verringert ihre Auflösung, siehe [Fotos, die standardmäßig übersprungen werden](#photos-that-are-skipped-by-default). Standardmäßig aus.

### Bereits komprimierte überspringen
{: #skip-previously-compressed }

Dateien überspringen, die SD Maid bereits komprimiert hat. Standardmäßig an.

### EXIF-Marker schreiben
{: #write-exif-marker }

Fügt dem EXIF-Benutzerkommentar des Fotos einen Marker hinzu, damit SD Maid es auch dann als komprimiert erkennt, wenn der Komprimierungsverlauf gelöscht wurde. SD Maid prüft nur dann auf den Marker, wenn diese Einstellung aktiviert ist. Standardmäßig aus. Gilt nur für JPEG und WebP.

### Komprimierungsverlauf
{: #compression-history }

Zeigt, wie viele Dateien im Verlauf stehen. Tippe darauf, um den Verlauf zu löschen.

## Ausschlüsse
{: #exclusions }

Die Medienkomprimierung überspringt Pfade, die allgemein [ausgeschlossen](/de/exclusions) sind. Du kannst Ergebnisse auch aus der Ergebnisliste heraus ausschließen, was nur die Medienkomprimierung betrifft.

Die Medienkomprimierung kann nicht vom [Planer (Scheduler)](/de/scheduler) ausgeführt werden.
