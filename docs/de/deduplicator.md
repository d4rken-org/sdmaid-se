---
title: "Duplikat-Entferner"
lang: de
translation_key: deduplicator
permalink: /de/deduplicator
source_hash: 08f0bb39baf9fb1e
---
# Duplikat-Entferner (Deduplicator)
{: #deduplicator }

Der Duplikat-Entferner (Deduplicator) findet doppelte Dateien auf deinem Gerät: exakte Kopien und optional Bilder, Videos oder Audiodateien, die ähnlich aussehen oder klingen. Er fasst sie zu Sets zusammen und löscht pro Set alle Dateien bis auf eine.

Das Scannen ist kostenlos. Das Löschen erfordert das Upgrade.

## Erkennungsmethoden
{: #detection-methods }

Du kannst jede Methode unter `Einstellungen > Duplikat-Entferner` aktivieren.

### Inhaltsprüfsumme
{: #content-checksum }

Standardmäßig an. Findet Dateien mit exakt demselben Inhalt, Byte für Byte, unabhängig von ihrem Namen. Das ist die sichere Methode: Jede Datei in einem solchen Set ist eine echte Kopie.

### Wahrnehmungshash
{: #perceptual-hash }

Standardmäßig aus. Findet Bilder, die ähnlich aussehen, z. B. dasselbe Foto in einem anderen Format oder einer anderen Größe gespeichert oder mit kleinen Bearbeitungen. Bilder werden in Graustufen verglichen, daher können Bilder übereinstimmen, die sich nur in der Farbe unterscheiden.

### Medien-Fingerabdruck
{: #media-fingerprint }

Standardmäßig aus. Findet Videos und Audiodateien, die ähnlich klingen, und stumme Videos, die ähnlich aussehen. Es werden nur Dateien mit ungefähr gleicher Länge verglichen.

Ähnliche Dateien sind nicht identisch. Serienaufnahmen, bearbeitete Versionen oder verkleinerte Kopien können im selben Set landen. **Prüfe Sets, die per Wahrnehmungshash oder Medien-Fingerabdruck gefunden wurden, bevor du sie löschst.** Bei jeder ähnlichen Datei wird angezeigt, wie stark sie übereinstimmt.

## Was gescannt wird
{: #what-gets-scanned }

Standardmäßig durchsucht der Duplikat-Entferner den gesamten zugänglichen Speicher: internen Speicher, SD-Karten, USB-Speicher, `Android/data` und `Android/media`. `Android/obb` ist nicht enthalten. Du kannst die Suche unter [Suchorte](#search-locations) einschränken.

Der Zugriff auf `Android/data` erfordert unter Android 13 und neuer [Root](/de/setup#root-access) oder [ADB-Zugriff](/de/setup#shizuku).

Es werden nur Dateien oberhalb der [Mindestgröße](#minimum-size) berücksichtigt. Standardmäßig sind nur gängige Dateitypen enthalten, siehe [Überspringe ungewöhnliche Dateitypen](#skip-uncommon-file-types).

## Welche Datei behalten wird
{: #which-file-is-kept }

Beim Löschen eines Sets behält SD Maid eine Datei und löscht den Rest. Welche behalten wird, bestimmt die Löschstrategie unter `Einstellungen > Duplikat-Entferner > Löschstrategie`.

SD Maid prüft jede Regel der Reihe nach. Die erste Regel, die einen eindeutigen Gewinner ergibt, entscheidet. Ziehe Regeln, um ihre Reihenfolge zu ändern, und tippe auf eine Regel, um ihren Modus zu ändern.

| Regel | Standard |
|---|---|
| Duplikattyp | Exakte Übereinstimmungen vor ähnlichen Dateien behalten |
| Bevorzugte Speicherorte | Dateien in Ordnern behalten, die du auswählst. Standardmäßig sind keine Ordner festgelegt. |
| Media-Indizierung | Dateien behalten, die deine Galerie und andere Medien-Apps kennen |
| Speicherort | Dateien im internen Speicher behalten. SD-Karten und USB-Speicher gelten als extern. |
| Ordnertiefe | Dateien in wenig verschachtelten Ordnern behalten |
| Änderungsdatum | Ältere Dateien behalten |
| Dateigröße | Größere Dateien behalten |

**Bevorzugte Speicherorte** hilft zusammen mit den anderen Regeln dabei, auszuwählen, welche Datei innerhalb einer Gruppe behalten wird. Zwischen Gruppen wählt diese Regel nicht. In einem Set mit sowohl exakten Kopien als auch ähnlichen Dateien entscheidet der Duplikattyp, welche Gruppe behalten wird: Standardmäßig wird jede Datei der ähnlichen Gruppe gelöscht, selbst an einem bevorzugten Speicherort. Wenn zum Beispiel dein Kamerafoto und zwei identische, verkleinerte Kopien aus einem Messenger in einem Set landen, behält die Standardeinstellung eine Messenger-Kopie und löscht das Kamera-Original. Prüfe solche Sets vor dem Löschen und [schließe](/de/exclusions) Ordner aus, deren Dateien niemals gelöscht werden dürfen.

Es gibt keine Möglichkeit, eine einzelne Datei als die zu behaltende zu markieren. Um eine andere Datei zu behalten, ändere die Löschstrategie oder wähle die zu löschenden Dateien selbst aus.

Wenn du die Löschstrategie änderst, werden die aktuellen Ergebnisse verworfen. Scanne danach erneut.

## Löschen
{: #deleting }

### Ein ganzes Set löschen
{: #deleting-a-whole-set }

Beim Löschen eines Sets bleibt eine Datei erhalten, und **jede andere Datei in diesem Set** wird gelöscht. In einem Set mit ähnlichen Dateien gehören dazu auch Dateien, die der behaltenen nur ähneln, aber nicht identisch sind.

Stelle die Ergebnisliste auf die Listenansicht um, damit bei jeder Datei, die beim Löschen des ganzen Sets entfernt wird, ein Löschsymbol erscheint. Die standardmäßige Rasteransicht zeigt diese Symbole nicht.

### Einzelne Dateien löschen
{: #deleting-individual-files }

Wähle Dateien durch langes Drücken aus und lösche sie. Pro Set muss mindestens eine Datei übrig bleiben, es sei denn, du aktivierst [Ermögliche „Alle löschen“](#make-delete-all-possible).

In den Set-Details gruppiert die Ansicht `Ordner` die Dateien nach Ordner. Wenn du dort einen Ordner löschst, werden die Duplikate in diesem Ordner gelöscht. Enthält der Ordner jede Datei eines Sets, wird die Datei ausgelassen, die beim Löschen des ganzen Sets behalten werden würde. Ist [Ermögliche „Alle löschen“](#make-delete-all-possible) aktiviert, kannst du in der Bestätigung alle löschen.

### Über das Dashboard löschen
{: #deleting-from-the-dashboard }

Löschen über die Dashboard-Karte des Duplikat-Entferners löscht nach einer Bestätigung in jedem Set alle Dateien bis auf eine. Ob die Hauptschaltfläche des Dashboards den Duplikat-Entferner ebenfalls ausführt, legst du unter [Ein-Tipp](#one-tap) fest.

## Ausschlüsse
{: #exclusions }

Pfade, die für den Duplikat-Entferner oder allgemein ausgeschlossen sind, werden nicht gescannt. Wenn du ein Set aus den Ergebnissen ausschließt, wird jede Datei darin ausgeschlossen, auch die, die behalten worden wäre. Siehe [Ausschlüsse](/de/exclusions).

Der Duplikat-Entferner kann nicht vom [Planer (Scheduler)](/de/scheduler) ausgeführt werden.

## Ein-Tipp
{: #one-tap }

`Einstellungen > Allgemein > Ein-Tipp-Tools` legt fest, ob die Hauptschaltfläche des Dashboards, die Verknüpfung `Scannen + Löschen` und das Widget den Duplikat-Entferner einbeziehen. Standardmäßig ist das aus. Die Verknüpfung und das Widget scannen und löschen ohne Bestätigung und verwenden dabei deine Löschstrategie. Auf dem Dashboard macht der `Ein-Tipp-Modus` dasselbe, aber wenn bereits Ergebnisse vorliegen, fragt Löschen trotzdem zuerst nach. Beziehe den Duplikat-Entferner nur ein, wenn [Inhaltsprüfsumme](#content-checksum) die einzige Erkennungsmethode ist.

## Einstellungen
{: #settings }

### Suchorte
{: #search-locations }

Die zu durchsuchenden Ordner. Standardmäßig alle zugänglichen Bereiche. Lange drücken setzt die Auswahl zurück.

### Löschstrategie
{: #deletion-strategy }

Welche Datei in einem Set behalten wird, siehe [Welche Datei behalten wird](#which-file-is-kept).

### Ermögliche „Alle löschen“
{: #make-delete-all-possible }

Zeigt Optionen an, um jede Datei in einem Set zu löschen, sodass keine Kopie übrig bleibt. Standardmäßig aus.

### Mindestgröße
{: #minimum-size }

Dateien unterhalb dieser Größe überspringen. Standard ist 512 KB.

### Überspringe ungewöhnliche Dateitypen
{: #skip-uncommon-file-types }

Nur gängige Bilder, Videos, Audiodateien, Dokumente, Archive und APK-Dateien einbeziehen. Standardmäßig an. Der Typ wird anhand der Dateiendung bestimmt.

### Erkennungsmethode
{: #detection-method }

Aktiviere [Inhaltsprüfsumme](#content-checksum), [Wahrnehmungshash](#perceptual-hash) und [Medien-Fingerabdruck](#media-fingerprint).
