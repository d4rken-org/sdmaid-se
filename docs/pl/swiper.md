---
title: "Przesuwacz"
lang: pl
translation_key: swiper
permalink: /pl/swiper
source_hash: 4c7d8b7a30d3a36e
---
# Przesuwacz (Swiper)
{: #swiper }

Przesuwacz (Swiper) pomaga uporządkować wybrane foldery szybciej niż menedżer plików. Przeglądasz pliki pojedynczo i każdy z nich przesuwasz, aby go zachować, usunąć lub pominąć. Nic nie zostaje usunięte, dopóki nie przejrzysz swoich wyborów i ich nie potwierdzisz.

## Rozpoczynanie sesji
{: #starting-a-session }

1. Otwórz Przesuwacz z panelu i dotknij `Wybierz foldery`. Możesz wybrać foldery, pojedyncze pliki lub jedno i drugie.
2. Opcjonalnie ustaw na karcie sesji filtr typów plików i kolejność sortowania.
3. Dotknij `Skanuj`. Foldery są przeszukiwane wraz ze wszystkimi podfolderami.
4. Dotknij `Zacznij przesuwać`.

Sesję możesz też utworzyć z [Analizy pamięci (StorageAnalyzer)](/pl/storageanalyzer): zaznacz tam elementy i wybierz `Utwórz sesję Przesuwacza`.

Dobre miejsca na początek to rolka aparatu, folder Pobrane i zrzuty ekranu.

### Typy plików
{: #file-types }

`Pokaż tylko te typy plików` ogranicza sesję do obrazów, filmów, dźwięków, dokumentów lub archiwów. Możesz też wpisać własne rozszerzenia, np. `apk, apks`. Jeśli nic nie jest zaznaczone, wyświetlane są wszystkie pliki. Pliki są dopasowywane wyłącznie po rozszerzeniu.

### Kolejność sortowania
{: #sort-order }

Domyślnie pliki są wyświetlane od najstarszych, na podstawie daty ostatniej modyfikacji. Możesz też sortować od najnowszych, według nazwy lub od największych.

Filtra i kolejności sortowania nie można zmienić, gdy skanowanie znalazło już pliki. Aby je zmienić, rozpocznij nową sesję.

### Skanowanie całej pamięci
{: #scanning-a-whole-storage }

Jeśli wybierzesz najwyższy poziom pamięci, np. całą pamięć wewnętrzną, do sesji trafi każdy znajdujący się w niej plik, w tym zdjęcia i multimedia aplikacji. SD Maid prosi o potwierdzenie przed skanowaniem. Gdy później będziesz usuwać pliki z takiej sesji, musisz najpierw zaznaczyć `Rozumiem, że te pliki zostaną trwale usunięte.`.

## Przesuwanie
{: #swiping }

| Gest | Domyślna czynność |
|---|---|
| Przesunięcie w prawo | Zachowaj |
| Przesunięcie w lewo | Usuń |
| Przesunięcie w górę | Pomiń |
| Przesunięcie w dół | Cofnij ostatnią decyzję |

Przyciski na dole działają tak samo. Gdy włączona jest opcja [Zamień kierunki przesuwania](#swap-swipe-directions), lewo i prawo są zamienione.

* **Pomiń** pozostawia plik bez decyzji. Pominięte pliki wracają po pozostałych.
* **Cofnij** cofa twoje ostatnie decyzje, maksymalnie 50.
* **Przytrzymaj Pomiń**, aby wykluczyć plik z przyszłych skanowań Przesuwacza. Tworzy to [wykluczenie](/pl/exclusions) ścieżki i usuwa plik z sesji.
* Przyciski na karcie otwierają plik w innej aplikacji lub pokazują podgląd pełnoekranowy.
* Dotknij miniatury na pasku, aby przejść do tego pliku. Ponowne przesunięcie go zastępuje twoją wcześniejszą decyzję.

Możesz wyjść w dowolnym momencie. Twoje decyzje są zapisywane razem z sesją i możesz kontynuować później. Gdy SD Maid zostanie ponownie uruchomiony, pliki, których nie może już znaleźć, są usuwane z sesji. Do tego czasu sesja może nadal zawierać pliki, które zostały usunięte lub przeniesione przez coś innego.

## Przeglądanie i usuwanie
{: #reviewing-and-deleting }

Dotknij `Oceń`, aby zobaczyć wszystkie decyzje. Gdy nie zostaną żadne pliki bez decyzji, przegląd otworzy się sam.

* Zmieniaj decyzje dla poszczególnych plików lub przytrzymaj, aby zaznaczyć kilka plików naraz.
* Dotknij pliku, aby do niego wrócić.
* `Usuń` pokazuje, ilu plików i ile miejsca to dotyczy oraz w których folderach, zanim cokolwiek się stanie.

Usuwanie jest trwałe. Pliki nie są przenoszone do kosza, a SD Maid nie może ich przywrócić.

Po usunięciu pliki zachowane i usunięte opuszczają sesję. Pliki bez decyzji pozostają na później. Gdy nic nie zostanie, sesja jest usuwana. Jeśli niektórych plików nie udało się usunąć, pozostają w sesji i możesz spróbować ponownie.

Usunięcie sesji odrzuca tylko twoje decyzje. Nie zmienia żadnych plików.

### Warto wiedzieć
{: #things-to-know }

* **Zachowaj dotyczy tylko bieżącej sesji.** Jeśli rozpoczniesz nową sesję w tym samym folderze, zachowane pliki pojawią się ponownie. Aby trwale ukryć plik, wyklucz go (przytrzymaj Pomiń).
* **Sesja nie wychwytuje nowych plików.** Pliki dodane do folderu po skanowaniu się nie pojawią. Aby je uwzględnić, rozpocznij nową sesję.
* **Archiwa obejmują pliki APK.**
* **Ukryte pliki są uwzględniane.**

## Wersja bezpłatna
{: #free-version }

Bez uaktualnienia Przesuwacz jest ograniczony do 2 sesji po 50 plików każda. Jeśli ustawiona jest kolejność sortowania, otrzymujesz pierwsze 50 plików w tej kolejności.

## Ustawienia
{: #settings }

### Zamień kierunki przesuwania
{: #swap-swipe-directions }

Przesunięcie w lewo zachowuje, w prawo usuwa. Domyślnie wyłączone.

### Pokaż szczegóły pliku
{: #show-file-details }

Pokazuje nazwę pliku, rozmiar, datę i folder na ekranie przesuwania. Domyślnie włączone.

### Wibracja przy dotyku
{: #haptic-feedback }

Wibruje, gdy zachowujesz lub usuwasz plik. Domyślnie włączone.
