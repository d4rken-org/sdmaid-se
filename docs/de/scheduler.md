---
title: "Planer"
lang: de
translation_key: scheduler
permalink: /de/scheduler
source_hash: 74e1452091eaf142
---
# Planer (Scheduler)
{: #scheduler }

Mit dem **Planer (Scheduler)** in SD Maid 2 kannst du Wartungsaufgaben in bestimmten Abständen automatisieren, damit dein Gerät ohne manuellen Aufwand optimiert bleibt. Er unterstützt mehrere Werkzeuge wie **App-Reiniger (AppCleaner)**, **Leichenfinder (CorpseFinder)** und **System-Reiniger (SystemCleaner)**, die so konfiguriert werden können, dass sie in regelmäßigen Abständen oder zu bestimmten Zeiten laufen.

<img src="/assets/images/scheduler/overview-1.webp" width="150">
<img src="/assets/images/scheduler/overview-2.webp" width="150">
<img src="/assets/images/scheduler/overview-3.webp" width="150">
<img src="/assets/images/scheduler/overview-4.webp" width="150">
<img src="/assets/images/scheduler/overview-5.webp" width="150">
<img src="/assets/images/scheduler/overview-6.webp" width="150">


## So richtest du einen Zeitplan ein
{: #how-to-set-up-a-schedule }

1. Öffne die **Einstellungen** und wähle **Planer**.
2. Tippe auf die Schaltfläche **+**, um einen neuen Zeitplan zu erstellen oder bestehende zu verwalten.
3. Wähle aus, welche Werkzeuge (z. B. **App-Reiniger**, **Leichenfinder**, **System-Reiniger**) nach Zeitplan laufen sollen.
4. Lege das **Wiederholungsintervall in Tagen** (z. B. alle 3 Tage) und die **Ungefähre Zeit** der Ausführung fest.
5. Aktiviere den Zeitplan mit dem Schalter.

## Zeitpläne bearbeiten oder löschen
{: #editing-or-deleting-schedules }

1. Öffne die Einstellungen des **Planers**, um deine Zeitpläne zu verwalten.
2. Du kannst bestehende Zeitpläne ändern, Intervalle anpassen oder sie löschen, wenn du sie nicht mehr brauchst.

Sobald ein Zeitplan aktiv ist, führt SD Maid die gewählten Aufgaben zu den festgelegten Zeiten aus.

## Weitere Optionen
{: #additional-options }

Im Hauptmenü der Einstellungen unter `Einstellungen > Planer` gibt es einige zusätzliche Optionen.

- **Bei schwacher Batterie überspringen**: Wenn sich das Gerät im Energiesparmodus befindet, werden geplante Aufgaben übersprungen.
- **Auslassen, wenn nicht geladen wird**: Geplante Aufgaben laufen nur, wenn das Gerät geladen wird, damit der Akku nicht leer wird.
- **Befehle nach Zeitplan**: Du kannst Shell-Befehle festlegen, die nach Abschluss der geplanten Aufgaben ausgeführt werden.
- **Bedienungshilfe**: Standardmäßig überspringt der Planer Funktionen, die auf der Bedienungshilfe beruhen, da diese nicht funktionieren, wenn das Gerät gesperrt ist. Wenn du diese Option aktivierst, ohne sonst etwas an deinem Gerät zu ändern, schlägt der Planer fehl. Lass sie also deaktiviert, es sei denn, du weißt, was du tust. Das ist nur in Sonderfällen nützlich, etwa bei einem ständig eingeschalteten Android-TV-Gerät.

## Fehlerbehebung
{: #troubleshooting }

### Der Planer wird nicht ausgeführt 
{: #scheduler-is-not-executing }

Unterscheide, ob der Planer gar nicht läuft oder ob die ausgeführten Vorgänge nicht das gewünschte Ergebnis bringen. Den Ausführungsverlauf kannst du in SD Maid im Bereich [Verlauf](/de/stats) prüfen. Wenn der Planer ausgeführt wurde, aber fehlgeschlagen ist, sollte dort ein Fehler stehen.

Wenn der Planer gar nicht gelaufen ist, hat das System möglicherweise verhindert, dass SD Maid startet, um Akku zu sparen. Nachdem du deinen ersten Zeitplan aktiviert hast, solltest du einen Hinweis mit dem Titel „Tipps“ und der Aktion „Beheben“ gesehen haben, der dich bittet, die Akku-Optimierung zu deaktivieren. Falls du diesen Hinweis versehentlich ausgeblendet hast, musst du manuell in den Systemeinstellungen für SD Maid nachsehen, ob SD Maid vom System „optimiert“ wird.

### Bedienungshilfe
{: #accessibility-service }
Löschvorgänge über die Bedienungshilfe, z. B. wenn der App-Reiniger „unzugängliche Einträge“ löscht, funktionieren nicht über den Planer. Wenn der Planer läuft, ist das Gerät meist gesperrt. So wie du auf einem ausgeschalteten oder gesperrten Bildschirm nichts antippen kannst, kann SD Maid das auch nicht.

Standardmäßig führt der Planer keine Vorgänge aus, die auf der Bedienungshilfe beruhen. Du kannst aber in den Planer-Einstellungen die Option „Bedienungshilfe“ aktivieren, wenn du weißt, was du tust.

Workarounds: Roote das Gerät (Nutzer- und System-Apps) oder installiere Shizuku (nur Nutzer-Apps). Beide Modifikationen ermöglichen SD Maid ähnliche Vorgänge, ohne die Bedienungshilfe zu verwenden.
