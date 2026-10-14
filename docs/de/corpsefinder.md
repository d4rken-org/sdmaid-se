---
title: "Leichenfinder"
lang: de
translation_key: corpsefinder
permalink: /de/corpsefinder
source_hash: 9e16286f0fda1dc8
---
# Leichenfinder (CorpseFinder)
{: #corpse-finder }

Das Werkzeug Leichenfinder (`CorpseFinder`) findet Dateien, die zu Apps gehören, die nicht mehr installiert sind.


## Risikostufe
{: #risklevel }

Standardmäßig sind alle Optionen der Risikostufe ausgeschaltet. Aktiviere sie nur, wenn du weißt, was du tust, und prüfe vor dem Löschen zuerst die Ergebnisse.
Wenn du eine Option der Risikostufe aktivierst, steigt die Wahrscheinlichkeit, dass Dateien gelöscht werden, die du nicht löschen wolltest.

Risikostufen betreffen vor allem Whitelist-Orte, etwa das Stammverzeichnis deines öffentlichen Speichers (`/storage/emulated/0`). Solche Orte haben kein gemeinsames Namensmuster, und SD Maid kann den Besitzer nur über eine manuell gepflegte Datenbank bestimmen.


### Erwünschte Reste einbeziehen
{: #include-desirable-remnants }

Erwünschte Reste sind Dateien mit bekanntem Besitzer, der nicht installiert ist, die SD Maid intern aber als `KEEPER` markiert. Meist sind das vom Nutzer erstellte Dateien, z. B. Fotos, die du aufgenommen hast.

### Gebräuchliche Namen einbeziehen
{: #include-common-names }

Manche Apps verwenden einfach sehr gebräuchliche Namen. Es gibt vermutlich Hunderte von Notiz-Apps, die einen Ordner `/sdcard/Notes` anlegen, um darin Daten zu speichern. SD Maid kennt vielleicht eine dieser Apps, und sie ist nicht auf deinem Handy installiert, aber SD Maid kann nicht sicher sein, dass nicht noch eine andere App auf deinem Handy diesen Ordner nutzt, von der SD Maid nicht weiß, dass sie diesen Ordner ebenfalls verwendet. 

## Filter
{: #filter }

Jeder Filter ist für einen bestimmten Datenbereich zuständig. Wenn du einen Filter aktivierst, durchsucht der Leichenfinder diesen zusätzlichen Bereich.

### SD-Karte
{: #sdcard }

* Meist der Ort, an dem der Nutzer Dateien ablegt
* Öffentlicher Speicher (eingebaut wie auch wechselbar), ausgenommen bestimmte Unterverzeichnisse in `Android/data`.
* Dateien hier folgen keinem Namensschema.

Beispiele:

```bash
/storage/emulated/0/SDMaid
/storage/DEAD-BEEF/SDMaid
/sdcard/SDMaid
```

### Öffentliche Medien
{: #public-media }

* Speichert öffentliche App-Medien, z. B. über WhatsApp empfangene Medien. Ordner sind nach den Paketnamen der Apps benannt.
* Unterverzeichnis des öffentlichen Speichers mit besonderer Bedeutung für das Betriebssystem. 

Beispiele:

```bash
/storage/emulated/0/Android/media/eu.darken.sdmse
/storage/DEAD-BEEF/Android/media/eu.darken.sdmse
/sdcard/Android/media/eu.darken.sdmse
```

### Öffentliche App-Daten
{: #public-app-data }

* Zusätzliche Dateien von Apps.
* Ordner sind nach den Paketnamen der Apps benannt.
* Unterverzeichnis des öffentlichen Speichers mit besonderer Bedeutung für das Betriebssystem.

Beispiele:

```bash
/storage/emulated/0/Android/data/eu.darken.sdmse
/storage/DEAD-BEEF/Android/data/eu.darken.sdmse
/sdcard/Android/data/eu.darken.sdmse
```

### OBB Ressourcen
{: #obb-resources }

* Meist zusätzliche Ressourcendateien für Spiele.
* Ordner und Dateien sind nach den Paketnamen der Apps benannt.
* Unterverzeichnis des öffentlichen Speichers mit besonderer Bedeutung für das Betriebssystem.

Beispiele:

```bash
/storage/emulated/0/Android/obb/eu.darken.sdmse
/storage/DEAD-BEEF/Android/obb/eu.darken.sdmse
/sdcard/Android/obb/eu.darken.sdmse
```

### Private App-Daten
{: #private-app-data }

* Ordner und Dateien sind nach den Paketnamen der Apps benannt.
* Unterverzeichnis des öffentlichen Speichers mit besonderer Bedeutung für das Betriebssystem.

Beispiele:

```bash
/data/data/eu.darken.sdmse
```

### Dalvik Cache
{: #dalvik-cache }

* Speichert optimierte Versionen des App-Codes für eine schnellere Ausführung.
* Liegt in der Systempartition, oft unter architekturspezifischen Verzeichnissen.
* Dateien sind nach dem Paketnamen der App benannt.

Beispiele:

```bash
/data/dalvik-cache/arm64/data@app@eu.darken.sdmse@base.apk@classes.dex
/data/dalvik-cache/arm/data@app@eu.darken.sdmse@base.apk@classes.dex
```

### App-Bibliotheken
{: #app-libraries }

* Enthält native Bibliotheken, die Apps verwenden, etwa `.so`-Dateien.
* Liegt typischerweise im Datenverzeichnis der App in einem Ordner `lib`.
* Dateien sind entsprechend der Architektur und den Anforderungen der Bibliothek benannt.

Beispiele:

```bash
/data/app/eu.darken.sdmse-1/lib/arm64/libexample.so
/data/app/eu.darken.sdmse-2/lib/arm/libexample.so
```

### App-Quellen
{: #app-sources }

* Bezeichnet die APK-Dateien, die als Quellcode der Apps dienen.
* Werden in bestimmten Verzeichnissen für System-, Nutzer- oder Hersteller-Apps gespeichert.
* Dateien sind nach dem Paketnamen und der Version der App benannt.

Beispiele:

```bash
/data/app/eu.darken.sdmse-1/base.apk
/system/app/ExampleApp/ExampleApp.apk
```

### Private App-Quellen
{: #private-app-sources }

* Ähnlich wie App-Quellen, aber für mehr Sicherheit in geschützten Verzeichnissen gespeichert.
* Nur für das System oder die jeweilige App zugänglich.
* Dateien sind nach dem Paketnamen der App benannt.

Beispiele:

```bash
/data/app-private/eu.darken.sdmse/base.apk
```
