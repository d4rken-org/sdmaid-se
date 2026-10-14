---
title: "Zarządzanie aplikacjami"
lang: pl
translation_key: appcontrol
permalink: /pl/appcontrol
source_hash: 1a4a7450fc5b86ca
---
# Zarządzanie aplikacjami (AppControl)
{: #appcontrol }
Zarządzanie aplikacjami (AppControl) to jedno miejsce na wszystkie czynności związane z kontrolowaniem zainstalowanych aplikacji. Po skanowaniu otrzymasz przeszukiwalną listę zainstalowanych aplikacji. Aplikacje systemowe nie są domyślnie wyświetlane, trzeba to włączyć w ustawieniach.
Niektóre opcje mogą być dostępne (możliwe) tylko wtedy, gdy SD Maid otrzymał uprawnienia roota.

Niektóre czynności można wykonać na wielu aplikacjach naraz, inne tylko na pojedynczych elementach. Aby wykonać czynność na wielu aplikacjach, przytrzymaj jedną aplikację, a następnie dotknij kolejnych, aby je zaznaczyć.

## Czynności
{: #actions }
Niektóre z tych czynności są dostępne tylko w szczegółach aplikacji, do których przechodzi się, dotykając pozycji na liście.

### Uruchom aplikację
{: #run-app }
Uruchamia aplikację. Jej główna aktywność, zwykle ta sama czynność, którą wykonałby też launcher.

### Wymuś zatrzymanie
{: #force-stop }
Próbuje wymusić zatrzymanie aplikacji. Działa najlepiej z rootem lub ADB/Shizuku, w przeciwnym razie używa usługi ułatwień dostępu.

### Eksportuj aplikację
{: #export-apk }
Próbuje skopiować plik `.apk` aplikacji z wewnętrznej pamięci prywatnej do katalogu pobranych plików w twojej pamięci publicznej. Jeśli aplikacja jest zaszyfrowana, ta czynność wymaga roota, niezaszyfrowane aplikacje można eksportować także bez roota.

### Wyłącz aplikację
{: #freeze-app }
Wymaga roota lub ADB/Shizuku. Wyłącza aplikację. Aplikacja nie będzie widoczna i nie może sama się uruchomić. Aby działała, trzeba ją ponownie włączyć. Nie dochodzi do utraty danych, a ten stan utrzymuje się także po odinstalowaniu SD Maid. Każda dobra aplikacja do włączania i wyłączania aplikacji może włączyć aplikacje wyłączone przez SD Maid i odwrotnie.

### Usuń aplikację
{: #delete-app }
Usuwa aplikację i jej dane.


## Ustawienia
{: #settings }
### Określ rozmiary
{: #determine-size }
Można wyłączyć, aby przyspieszyć wczytywanie listy aplikacji. Ustalanie rozmiaru aplikacji zajmuje dużo czasu.

### Określ aktywność
{: #determine-activity }
Można wyłączyć, aby przyspieszyć wczytywanie listy aplikacji. Ustalanie, czy aplikacja była ostatnio aktywna, zajmuje dużo czasu.
