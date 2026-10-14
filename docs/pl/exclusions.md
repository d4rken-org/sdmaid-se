---
title: "Wykluczenia"
lang: pl
translation_key: exclusions
permalink: /pl/exclusions
source_hash: d5c3aea2efa43796
---
# Wykluczenia
{: #exclusions }

Funkcja wykluczeń w SD Maid 2/SE pozwala określić konkretne foldery lub pliki, które aplikacja ma ignorować podczas skanowania systemu i zadań konserwacyjnych. Przydaje się to, gdy masz dane, na które działania aplikacji nie powinny mieć wpływu. Po ustawieniu wykluczeń SD Maid 2/SE pomija te katalogi lub pliki podczas zadań takich jak Wyszukiwanie pozostałości (CorpseFinder), Sprzątanie aplikacji (AppCleaner) i innych funkcji zarządzania systemem.

Najłatwiej dodać wykluczenie bezpośrednio z wyników. Możesz użyć przycisku „Wyklucz” przy pojedynczych elementach albo przytrzymać lub zaznaczyć kilka pozycji, a następnie wybrać opcję „Wyklucz”. Dodatkowo, dla większej kontroli, wykluczenia można dodawać ręcznie w ustawieniach aplikacji, podając ścieżki lub wzorce z symbolami wieloznacznymi.

Wykluczenia można eksportować i importować z menu. Import wykluczeń jest obsługiwany tylko w podobnych wersjach SD Maid. Jeśli między wersją importu a wersją eksportu było zbyt wiele aktualizacji, może wystąpić błąd. W takim przypadku możesz spróbować zaimportować je we wcześniejszej wersji, a następnie ponownie wyeksportować wykluczenia.

<img src="/assets/images/exclusions/overview-1.webp" width="150">
<img src="/assets/images/exclusions/overview-2.webp" width="150">
<img src="/assets/images/exclusions/overview-3.webp" width="150">
<img src="/assets/images/exclusions/overview-4.webp" width="150">

## Typy
{: #types }

SD Maid obsługuje 3 różne typy wykluczeń:

### Wykluczenia aplikacji
{: #app-exclusions }

Wykluczenia aplikacji opierają się na nazwie pakietu każdej aplikacji. Można je tworzyć za pomocą narzędzia Zarządzanie aplikacjami (AppControl). Wystarczy wybrać aplikację z listy i użyć odpowiedniej czynności `Wyklucz`.

### Wykluczenia ścieżek
{: #path-exclusions }

Wykluczenia ścieżek można tworzyć, zaznaczając (przytrzymując) ścieżki w narzędziu Analiza pamięci (StorageAnalyzer).

### Wykluczenia segmentów 
{: #segment-exclusions }
Wykluczenia segmentów to rodzaj wykluczeń ścieżek, które nie wymagają dokładnego dopasowania. Segment to część ścieżki, na przykład folder. Ścieżka `/sdcard/DCIM/Camera` składa się z 3 segmentów. Możesz wykluczyć wszystkie zdjęcia z aparatu zarówno w pamięci wewnętrznej (`/storage/emulated/0/DCIM/Camera`), jak i zewnętrznej (`/sdcard/DCIM/Camera`), tworząc wykluczenie segmentu dla `DCIM/Camera`.

Jeśli zaznaczysz opcję `Zezwalaj na częściowe dopasowania`, możesz utworzyć wykluczenie dla `DCIM/Cam`, które dopasuje zarówno folder `Cam`, jak i folder `Camera`.

## Narzędzia
{: #tools }

Każde narzędzie SD Maid inaczej widzi dane. Nie wszystkie wykluczenia działają tak samo na wszystkie narzędzia.

### Wyszukiwanie pozostałości
{: #corpsefinder }

Wyszukiwanie pozostałości wie, czym jest „aplikacja” i jakie pliki do niej należą. Respektuje wszystkie typy wykluczeń. Utworzenie wykluczenia aplikacji oznacza, że wszystkie pliki znalezione przez Wyszukiwanie pozostałości, które według niego należą do wykluczonej aplikacji, zostaną wykluczone.

Obsługiwane typy wykluczeń: `APP`, `PATH`, `SEGMENTS`

### Sprzątanie systemu
{: #systemcleaner }

Sprzątanie systemu (SystemCleaner) działa wyłącznie na danych ścieżek. Nie wie, czym jest „aplikacja” ani które pliki do niej należą.

Obsługiwane typy wykluczeń: `PATH`, `SEGMENTS`

### Sprzątanie aplikacji
{: #appcleaner }

Sprzątanie aplikacji respektuje wszystkie typy wykluczeń. Wykluczeń możesz użyć, aby przyspieszyć skanowanie, jeśli masz aplikacje z wieloma plikami do przeskanowania.

Obsługiwane typy wykluczeń: `APP`, `PATH`, `SEGMENTS`

### Usuwanie duplikatów
{: #deduplicator }

Usuwanie duplikatów (Deduplicator) nie wie, czym są „aplikacje”, i pracuje wyłącznie na danych plików.

Obsługiwane typy wykluczeń: `PATH`, `SEGMENTS`

### Kompresja multimediów
{: #media-squeeze }

Kompresja multimediów (Media Squeeze) pracuje wyłącznie na danych plików. Wykluczone pliki nie są kompresowane.

Obsługiwane typy wykluczeń: `PATH`, `SEGMENTS`

### Przesuwacz
{: #swiper }

Przesuwacz (Swiper) pracuje wyłącznie na danych plików. Wykluczone pliki nie są uwzględniane w nowych sesjach. Przytrzymanie `Pomiń` podczas przesuwania wyklucza bieżący plik.

Obsługiwane typy wykluczeń: `PATH`, `SEGMENTS`

### Analiza pamięci
{: #storageanalyzer }

Analiza pamięci nie obsługuje wykluczeń, ponieważ cała zawartość musi zostać przeskanowana, aby obliczyć prawidłowe łączne rozmiary.
