---
title: "Sprzątanie systemu"
lang: pl
translation_key: systemcleaner
permalink: /pl/systemcleaner
source_hash: 1ced6df9b00f1e05
---
# Sprzątanie systemu (SystemCleaner)
{: #system-cleaner }

Sprzątanie systemu (SystemCleaner) to w zasadzie wyszukiwarka ogólnego przeznaczenia. Przeszukuje całą dostępną pamięć i porównuje znalezione pliki i foldery z zestawem filtrów.

W przeciwieństwie do innych narzędzi, takich jak Wyszukiwanie pozostałości (CorpseFinder) czy Sprzątanie aplikacji (AppCleaner), nie wie nic o zainstalowanych aplikacjach. Działa wyłącznie na ścieżkach i atrybutach plików. Dlatego do Sprzątania systemu można stosować tylko wykluczenia ścieżek i segmentów.

Każdy znaleziony plik przechodzi przez włączone filtry i zostaje przypisany do pierwszego pasującego filtra.

## Ustawienia
{: #settings }

### Filtry ogólne
{: #general-filters }

Filtry ogólne przeszukują **wszystkie dostępne obszary pamięci** w poszukiwaniu określonych typów plików, które mogą pojawić się w dowolnym miejscu urządzenia.

| Filtr | Opis |
|--------|-------------|
| **Puste foldery** | Puste foldery z całego urządzenia. Zagnieżdżone foldery mogą wymagać kilku przebiegów skanowania. |
| **Zrzuty ekranu** | Zrzuty ekranu starsze niż konfigurowalny wiek, z wielu aplikacji i lokalizacji. |
| **Pliki w koszu** | Pliki w koszach różnych aplikacji i lokalizacji, które są oznaczone do usunięcia, ale nie zostały jeszcze trwale usunięte. |
| **Zbędne pliki APK** | Pliki instalacyjne (APK), które są starsze od już zainstalowanych wersji aplikacji lub im równe. |
| **Foldery LOST.DIR** | Pliki tymczasowe tworzone podczas przesyłania danych między urządzeniami z Androidem. |
| **Pliki systemu Linux** | Pliki i foldery tworzone, gdy pamięć była podłączona do komputera z Linuksem. |
| **Pliki systemu MacOS** | Pliki i foldery tworzone podczas przeglądania pamięci w aplikacji Finder firmy Apple (np. `.DS_Store`). |
| **Pliki systemu Windows** | Dane, które Eksplorator plików Windows może tworzyć na podłączonej pamięci (np. `Thumbs.db`). |
| **Miniatury obrazów** | Podglądy obrazów i filmów zapisane w pamięci podręcznej (np. foldery `.thumbnails`). |
| **Tymczasowe pliki systemowe** | Pliki tymczasowe związane z systemem, zwykle krótkotrwała pamięć podręczna dla trwających operacji. |
| **Pliki analityki** | Dane związane z analityką i śledzeniem błędów. |
| **Pliki reklam** | Pliki utworzone na potrzeby reklam lub przez reklamy. |
| **Pliki dzienników systemowych** | Pliki dzienników systemu i usług systemowych (np. `*.log`) w różnych lokalizacjach. |

### Filtry szczegółowe
{: #specific-filters }

Filtry szczegółowe obejmują **stałe, znane ścieżki** na urządzeniu, w których przechowywane są określone rodzaje danych.

| Filtr | Opis |
|--------|-------------|
| **Pozostałość po awarii systemu** | Gdy procesy natywne ulegają awarii (np. gry), tworzone są tu pliki „tombstone”. |
| **Statystyki użytkowania** | Pliki związane ze statystykami użytkowania aplikacji. |
| **Błędy ANR** | Ślady zdarzeń „Application Not Responding” (aplikacja nie odpowiada). |
| **Lokalne pliki tymczasowe** | Pliki tymczasowe związane z systemem (np. z Android Studio). |
| **Pliki dziennika systemowego (datalogger)** | Pliki dzienników poszczególnych usług systemowych. |
| **Pamięć podręczna pobierania** | Systemowy folder pamięci podręcznej używany do różnych celów (np. aktualizacji OTA). |
| **Skrzynka na dzienniki** | Obszar, do którego system odkłada pliki dzienników. |
| **Pamięć podręczna instalatora** | Dane w pamięci podręcznej związane z zainstalowanymi aplikacjami, używane przez systemowe zarządzanie pakietami. |
| **Ostatnie zadania** | Dane związane z historią zadań w systemowym przełączniku zadań. |

### Filtry niestandardowe
{: #custom-filters }

Oprócz filtrów ogólnych dostarczanych przez aplikację możesz tworzyć filtry niestandardowe z własnymi kryteriami. Filtry niestandardowe można tworzyć w **Ustawienia > Sprzątanie systemu > Zarządzaj filtrami niestandardowymi**.

> **Ostrzeżenie**: filtry niestandardowe mogą być ryzykowne. Zawsze sprawdzaj wyniki skanowania przed usunięciem!

#### Dostępne kryteria
{: #available-criteria }

| Kryterium | Opis |
|-----------|-------------|
| **Etykieta filtra** | Nazwa twojego filtra |
| **Obszary danych** | Lokalizacje pamięci do przeskanowania (pozostaw puste, aby objąć wszystkie dostępne obszary) |
| **Ograniczenia typu pliku** | Docelowe pliki, foldery lub dowiązania symboliczne (pozostaw puste, aby objąć wszystkie) |
| **Kryteria ścieżki** | Reguły dopasowywania ścieżek plików |
| **Wykluczenia** | Ścieżki wyłączone z dopasowywania |
| **Kryteria nazwy** | Reguły dopasowywania nazw plików |
| **Minimalny rozmiar** | Dopasowuje tylko pliki większe niż ta wartość |
| **Maksymalny rozmiar** | Dopasowuje tylko pliki mniejsze niż ta wartość |
| **Minimalny wiek** | Dopasowuje tylko pliki starsze niż ta wartość |
| **Maksymalny wiek** | Dopasowuje tylko pliki nowsze niż ta wartość |
| **Path Regex** (wyrażenie regularne ścieżki) | Zaawansowane: wyrażenia regularne do dopasowywania ścieżek |

#### Tryby kryteriów ścieżki
{: #path-criteria-modes }

| Tryb | Opis | Przykład |
|------|-------------|---------|
| **Zaczyna się od** | Ścieżka zaczyna się od słowa kluczowego | `Downloads/temp` pasuje do `/sdcard/Downloads/temp/file.txt` |
| **Zawiera** | Ścieżka zawiera słowo kluczowe | `cache` pasuje do `/sdcard/app/cache/data.bin` |
| **Kończy się na** | Ścieżka kończy się słowem kluczowym | `backup/old` pasuje do `/sdcard/backup/old` |
| **Równa się** | Ścieżka dokładnie odpowiada słowu kluczowemu | `Download/temp` pasuje tylko do tej dokładnej ścieżki |

#### Tryby kryteriów nazwy
{: #name-criteria-modes }

| Tryb | Opis | Przykład |
|------|-------------|---------|
| **Zaczyna się od** | Nazwa pliku zaczyna się od słowa kluczowego | `temp_` pasuje do `temp_file.txt` |
| **Zawiera** | Nazwa pliku zawiera słowo kluczowe | `.bak` pasuje do `document.bak.old` |
| **Kończy się na** | Nazwa pliku kończy się słowem kluczowym | `.tmp` pasuje do `cache.tmp` |
| **Równa się** | Nazwa pliku dokładnie pasuje | `thumbs.db` pasuje tylko do `thumbs.db` |

Domyślnie wszystkie dopasowania są **niezależne od wielkości liter**.

#### Obszary danych
{: #data-areas }

Popularne obszary, które możesz wskazać:

| Obszar | Opis |
|------|-------------|
| **SDCARD** | Pamięć podstawowa (wewnętrzna lub karta SD) |
| **PUBLIC_MEDIA** | Foldery multimediów (DCIM, Pictures, Music itp.) |
| **PUBLIC_DATA** | Współdzielone dane aplikacji |
| **PRIVATE_DATA** | Prywatna pamięć aplikacji (wymaga roota) |
| **DOWNLOAD_CACHE** | Systemowa pamięć podręczna pobierania (wymaga roota) |
| **PORTABLE** | Pamięć wymienna (USB/microSD) |

#### Wskazówki
{: #tips }

- **Wydajność**: określaj tylko niezbędne kryteria. Zbyt wiele wzorców regex spowalnia skanowanie.
- **Wiele kryteriów**: wszystkie podane kryteria muszą być spełnione (logika AND).
- **Długie naciśnięcie**: przytrzymaj pozycję, aby zobaczyć dodatkowe opcje.
- **Testowanie**: przed włączeniem usuwania skorzystaj z podglądu skanowania.
- **Import/eksport**: filtry można eksportować i udostępniać innym użytkownikom.

#### Przestarzałe opcje
{: #deprecated-options }

Opcja `rootOnly` z SD Maid 1 **nie jest już dostępna**. Zamiast niej używaj **Obszarów danych**, aby określić, które lokalizacje pamięci są skanowane.
