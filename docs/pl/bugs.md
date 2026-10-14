---
title: "Błędy"
lang: pl
translation_key: bugs
permalink: /pl/bugs
source_hash: a218be31a6c4ae63
---
# Błędy
{: #bugs }

🐌 🦋 🐛 🐜 🐝 🐞

Błędy są częścią życia każdej aplikacji. Nie chcą ci zrobić krzywdy, ale nie zostaną tu też na zawsze 😠.

Czym są błędy?

* Jeśli aplikacja się zawiesza i zamyka, to oczywiście jest błąd
* Jeśli coś zachowuje się w nieoczekiwany sposób, może to być błąd.

W każdym przypadku sprawdź, czy masz najnowszą aktualizację, błąd może być już naprawiony.

Jeśli błąd nie został naprawiony, zgłoś go :grin:.

## Zgłaszanie błędu
{: #reporting-a-bug }

Utwórz zgłoszenie (issue) na GitHubie:
<https://github.com/d4rken-org/sdmaid-se/issues>

* Opisz, czego się spodziewałeś i co faktycznie się stało
* Wypisz kroki, które ktoś inny musiałby wykonać, aby odtworzyć problem
* Opisz swoją konfigurację
    * Typ urządzenia (np. Pixel 5)
    * Wersja Androida (np. Android 13.1)
    * Jeśli masz roota, wersja Magiska

Dołącz [dziennik debugowania](#debug-log), jeśli potrafisz odtworzyć problem. 

Wskazówka: możesz [przytrzymać pozycję „Lista zmian”](https://github.com/d4rken-org/sdmaid-se/pull/940) w ustawieniach SD Maid, aby skopiować wersję do schowka.

## Dziennik debugowania
{: #debug-log }

Dziennik debugowania to zapis działań SD Maid. Rozpoczynasz nagrywanie, odtwarzasz problem, a potem je zatrzymujesz i możesz udostępnić plik zip z chronologicznym opisem tego, co robił SD Maid.

Aby rozpocząć nagrywanie dziennika debugowania:
* Przejdź do ustawień SD Maid, naciskając ikonę ustawień w prawym dolnym rogu panelu
* Znajdź pozycję `Wsparcie` na liście ustawień
* Stuknij `Rejestruj dziennik debugowania` na ekranie wsparcia, a potem ponownie `Rejestruj dziennik debugowania` w oknie dialogowym
* Podczas nagrywania panel wyświetla kartę `Rejestrowanie dziennika debugowania`
* **Teraz odtwórz problem, który chcesz nagrać**
* Po odtworzeniu problemu stuknij `Zatrzymaj rejestrowanie` na tej karcie panelu lub na ekranie wsparcia. Jeśli nagranie jest bardzo krótkie, SD Maid najpierw zapyta; stuknij `Zatrzymaj mimo to`, aby je zatrzymać.
* Otworzy się ekran `Dziennik debugowania`. Gdy plik dziennika zostanie skompresowany, stuknij `Udostępnij`, aby go wysłać.

Dziennik debugowania jest nagrywany, dopóki go nie zatrzymasz, nagrywanie trwa nawet wtedy, gdy wymusisz zatrzymanie SD Maid i uruchomisz go ponownie.

Jeśli szukasz przyczyny problemów związanych z podstawowymi funkcjami, np. wykrywaniem roota lub wykrytymi obszarami pamięci, warto w trakcie nagrywania wymusić zatrzymanie SD Maid i uruchomić go ponownie, ponieważ niektóre dane są inicjalizowane tylko raz na sesję aplikacji.

Pliki dziennika debugowania są przechowywane w `/storage/emulated/0/Android/data/eu.darken.sdmse/cache/debug/logs`.

Na obrazkach:

<a href="/assets/images/bugs/debug-log-1.webp"><img src="/assets/images/bugs/debug-log-1.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-2.webp"><img src="/assets/images/bugs/debug-log-2.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-3.webp"><img src="/assets/images/bugs/debug-log-3.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-4.webp"><img src="/assets/images/bugs/debug-log-4.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-5.webp"><img src="/assets/images/bugs/debug-log-5.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-6.webp"><img src="/assets/images/bugs/debug-log-6.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-7.webp"><img src="/assets/images/bugs/debug-log-7.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-8.webp"><img src="/assets/images/bugs/debug-log-8.webp" width="100" alt=""></a>
