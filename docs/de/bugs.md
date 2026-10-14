---
title: "Fehler"
lang: de
translation_key: bugs
permalink: /de/bugs
source_hash: a218be31a6c4ae63
---
# Fehler
{: #bugs }

🐌 🦋 🐛 🐜 🐝 🐞

Fehler (Bugs) gehören zum Leben einer App. Sie wollen dir nichts Böses, aber bleiben sollen sie auch nicht 😠.

Was sind Fehler?

* Wenn die App abstürzt, ist das offensichtlich ein Fehler
* Wenn sich etwas unerwartet verhält, könnte es ein Fehler sein.

Prüfe in jedem Fall, ob du die neueste Version hast, vielleicht ist der Fehler schon behoben.

Wenn der Fehler nicht behoben ist, dann melde ihn :grin:.

## Einen Fehler melden
{: #reporting-a-bug }

Erstelle ein Issue auf GitHub:
<https://github.com/d4rken-org/sdmaid-se/issues>

* Beschreibe, was du erwartet hast und was tatsächlich passiert ist
* Liste die Schritte auf, die jemand anderes ausführen müsste, um das Problem nachzustellen
* Beschreibe deine Umgebung
    * Dein Gerätetyp (z. B. Pixel 5)
    * Android-Version (z. B. Android 13.1)
    * Falls du gerootet bist, die Magisk-Version

Füge ein [Debug-Protokoll](#debug-log) bei, wenn du das Problem nachstellen kannst. 

Tipp: Du kannst in den Einstellungen von SD Maid [lange auf den Eintrag „Änderungsprotokoll“ drücken](https://github.com/d4rken-org/sdmaid-se/pull/940), um die Version in deine Zwischenablage zu kopieren.

## Debug-Protokoll
{: #debug-log }

Ein Debug-Protokoll ist eine Aufzeichnung der Aktionen von SD Maid. Du startest die Aufzeichnung, stellst das Problem nach, beendest sie dann und kannst eine ZIP-Datei teilen, die chronologisch festhält, was SD Maid getan hat.

So startest du die Aufzeichnung eines Debug-Protokolls:
* Öffne die Einstellungen in SD Maid, indem du unten rechts im Dashboard auf das Einstellungssymbol tippst
* Suche in der Liste der Einstellungen den Eintrag `Unterstützung`
* Tippe auf dem Unterstützungs-Bildschirm auf `Debug-Protokoll aufzeichnen` und im Dialog erneut auf `Debug-Protokoll aufzeichnen`
* Während der Aufzeichnung zeigt das Dashboard eine Karte `Debug-Protokoll wird aufgezeichnet`
* **Stelle jetzt das Problem nach, das du aufzeichnen möchtest**
* Sobald du das Problem nachgestellt hast, tippe auf dieser Dashboard-Karte oder auf dem Unterstützungs-Bildschirm auf `Aufzeichnung beenden`. Ist die Aufzeichnung sehr kurz, fragt SD Maid vorher nach; tippe dann auf `Trotzdem stoppen`, um sie zu beenden.
* Der Bildschirm `Debug-Protokoll` öffnet sich. Sobald die Protokolldatei komprimiert ist, tippe auf `Teilen`, um sie zu senden.

Das Debug-Protokoll wird aufgezeichnet, bis du es beendest. Die Aufzeichnung läuft weiter, auch wenn du das Beenden von SD Maid erzwingst und die App neu startest.

Wenn du Probleme mit Kernfunktionen untersuchst, z. B. die Root-Erkennung oder die erkannten Speicherbereiche, ist es sinnvoll, während der Aufzeichnung das Beenden von SD Maid zu erzwingen und die App neu zu starten, da manche Daten nur einmal pro App-Sitzung initialisiert werden.

Die Debug-Protokolldateien werden in `/storage/emulated/0/Android/data/eu.darken.sdmse/cache/debug/logs` gespeichert.

In Bildern:

<a href="/assets/images/bugs/debug-log-1.webp"><img src="/assets/images/bugs/debug-log-1.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-2.webp"><img src="/assets/images/bugs/debug-log-2.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-3.webp"><img src="/assets/images/bugs/debug-log-3.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-4.webp"><img src="/assets/images/bugs/debug-log-4.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-5.webp"><img src="/assets/images/bugs/debug-log-5.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-6.webp"><img src="/assets/images/bugs/debug-log-6.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-7.webp"><img src="/assets/images/bugs/debug-log-7.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-8.webp"><img src="/assets/images/bugs/debug-log-8.webp" width="100" alt=""></a>
