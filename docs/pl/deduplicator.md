---
title: "Usuwanie duplikatów"
lang: pl
translation_key: deduplicator
permalink: /pl/deduplicator
source_hash: 08f0bb39baf9fb1e
---
# Usuwanie duplikatów (Deduplicator)
{: #deduplicator }

Usuwanie duplikatów (Deduplicator) znajduje zduplikowane pliki na twoim urządzeniu: dokładne kopie, a opcjonalnie także obrazy, filmy lub pliki audio, które podobnie wyglądają lub brzmią. Grupuje je w zestawy i usuwa wszystkie pliki z wyjątkiem jednego w każdym zestawie.

Skanowanie jest bezpłatne. Usuwanie wymaga uaktualnienia.

## Metody wykrywania
{: #detection-methods }

Każdą metodę możesz włączyć w `Ustawienia > Usuwanie duplikatów`.

### Suma kontrolna zawartości
{: #content-checksum }

Domyślnie włączona. Znajduje pliki o dokładnie tej samej zawartości, bajt w bajt, niezależnie od ich nazw. To bezpieczna metoda: każdy plik w takim zestawie jest prawdziwą kopią.

### Percepcyjny skrót
{: #perceptual-hash }

Domyślnie wyłączony. Znajduje obrazy, które wyglądają podobnie, np. to samo zdjęcie zapisane w innym formacie lub rozmiarze albo z drobnymi zmianami. Obrazy są porównywane w skali szarości, więc obrazy różniące się tylko kolorem mogą zostać dopasowane.

### Sygnatury multimediów
{: #media-fingerprint }

Domyślnie wyłączone. Znajdują filmy i pliki audio, które brzmią podobnie, oraz filmy bez dźwięku, które wyglądają podobnie. Porównywane są tylko pliki o mniej więcej tej samej długości.

Podobne pliki nie są identyczne. Zdjęcia seryjne, edytowane wersje lub kopie o zmienionym rozmiarze mogą trafić do tego samego zestawu. **Sprawdź zestawy znalezione przez percepcyjny skrót lub sygnatury multimediów, zanim je usuniesz.** Każdy podobny plik pokazuje, jak bardzo jest dopasowany.

## Co jest skanowane
{: #what-gets-scanned }

Domyślnie Usuwanie duplikatów przeszukuje całą dostępną pamięć: pamięć wewnętrzną, karty SD, pamięć USB, `Android/data` i `Android/media`. `Android/obb` nie jest uwzględniany. Wyszukiwanie możesz ograniczyć w [Lokalizacjach wyszukiwania](#search-locations).

Dostęp do `Android/data` na Androidzie 13 i nowszym wymaga [roota](/pl/setup#root-access) lub [dostępu ADB](/pl/setup#shizuku).

Brane pod uwagę są tylko pliki powyżej [minimalnego rozmiaru](#minimum-size). Domyślnie uwzględniane są tylko popularne typy plików, zobacz [Pomiń niestandardowe typy plików](#skip-uncommon-file-types).

## Który plik zostaje zachowany
{: #which-file-is-kept }

Podczas usuwania zestawu SD Maid zachowuje jeden plik, a resztę usuwa. O tym, który plik zostaje zachowany, decyduje strategia usuwania w `Ustawienia > Usuwanie duplikatów > Strategia usuwania`.

SD Maid sprawdza każdą regułę po kolei. Decyduje pierwsza reguła, która wskaże wyraźnego zwycięzcę. Przeciągaj reguły, aby zmienić ich kolejność, dotknij reguły, aby zmienić jej tryb.

| Reguła | Domyślnie |
|---|---|
| Typ duplikatu | Zachowuj dokładne dopasowania zamiast podobnych plików |
| Preferowane lokalizacje | Zachowuj pliki w wybranych przez ciebie folderach. Domyślnie żadne foldery nie są ustawione. |
| Indeksowanie multimediów | Zachowuj pliki, o których wie twoja galeria i inne aplikacje multimedialne |
| Miejsce przechowywania | Zachowuj pliki w pamięci wewnętrznej. Karty SD i pamięć USB są traktowane jako zewnętrzne. |
| Głębokość folderu | Zachowuj pliki w płytko zagnieżdżonych folderach |
| Data modyfikacji | Zachowuj starsze pliki |
| Rozmiar pliku | Zachowuj większe pliki |

**Preferowane lokalizacje** pomagają wybrać, który plik zostaje zachowany w obrębie grupy, razem z pozostałymi regułami. Nie wybierają między grupami. W zestawie zawierającym zarówno dokładne kopie, jak i podobne pliki o tym, która grupa zostaje zachowana, decyduje Typ duplikatu: domyślnie każdy plik w grupie podobnych zostaje usunięty, nawet w preferowanej lokalizacji. Na przykład jeśli zdjęcie z aparatu i dwie identyczne, pomniejszone kopie z komunikatora trafią do jednego zestawu, ustawienie domyślne zachowa jedną kopię z komunikatora i usunie oryginał z aparatu. Sprawdzaj takie zestawy przed usunięciem i [wykluczaj](/pl/exclusions) foldery, z których pliki nigdy nie mogą zostać usunięte.

Nie da się oznaczyć pojedynczego pliku jako tego, który ma zostać zachowany. Aby zachować inny plik, zmień strategię usuwania albo sam wybierz pliki do usunięcia.

Zmiana strategii usuwania odrzuca bieżące wyniki. Potem przeskanuj ponownie.

## Usuwanie
{: #deleting }

### Usuwanie całego zestawu
{: #deleting-a-whole-set }

Usunięcie zestawu zachowuje jeden plik i usuwa **każdy inny plik w tym zestawie**. W zestawie z podobnymi plikami obejmuje to także pliki, które są tylko podobne do zachowanego, a nie identyczne.

Przełącz listę wyników na układ listy, aby przy każdym pliku, który zostanie usunięty razem z całym zestawem, zobaczyć ikonę usuwania. Domyślny układ siatki nie pokazuje tych ikon.

### Usuwanie pojedynczych plików
{: #deleting-individual-files }

Zaznacz pliki długim naciśnięciem i usuń je. W każdym zestawie musi pozostać co najmniej jeden plik, chyba że włączysz [Zezwól na „Usuń wszystko”](#make-delete-all-possible).

W szczegółach zestawu widok `Foldery` grupuje pliki według folderów. Usunięcie folderu w tym widoku usuwa duplikaty w tym folderze. Jeśli folder zawiera wszystkie pliki zestawu, plik, który zostałby zachowany przy usuwaniu całego zestawu, zostaje pominięty. Gdy włączona jest opcja [Zezwól na „Usuń wszystko”](#make-delete-all-possible), okno potwierdzenia pozwala usunąć je wszystkie.

### Usuwanie z panelu
{: #deleting-from-the-dashboard }

Usunięcie z karty Usuwania duplikatów w panelu usuwa, po potwierdzeniu, wszystkie pliki z wyjątkiem jednego w każdym zestawie. To, czy główny przycisk panelu uruchamia także Usuwanie duplikatów, ustawia się w sekcji [Jedno dotknięcie](#one-tap).

## Wykluczenia
{: #exclusions }

Ścieżki wykluczone dla Usuwania duplikatów lub wykluczone ogólnie nie są skanowane. Wykluczenie zestawu z wyników wyklucza każdy plik w nim, także ten, który zostałby zachowany. Zobacz [Wykluczenia](/pl/exclusions).

Usuwania duplikatów nie można uruchamiać za pomocą [Harmonogramu (Scheduler)](/pl/scheduler).

## Jedno dotknięcie
{: #one-tap }

`Ustawienia > Ogólne > Narzędzia jednego dotknięcia` decyduje, czy główny przycisk panelu, skrót `Skanuj i usuń` oraz widżet uwzględniają Usuwanie duplikatów. Domyślnie jest to wyłączone. Skrót i widżet skanują i usuwają bez potwierdzenia, korzystając z twojej strategii usuwania. W panelu `Tryb jednego dotknięcia` robi to samo, ale jeśli wyniki już czekają, Usuń i tak najpierw pyta. Uwzględniaj Usuwanie duplikatów tylko wtedy, gdy [Suma kontrolna zawartości](#content-checksum) jest jedyną metodą wykrywania.

## Ustawienia
{: #settings }

### Lokalizacje wyszukiwania
{: #search-locations }

Foldery do przeszukania. Domyślnie wszystkie dostępne obszary. Przytrzymaj, aby je zresetować.

### Strategia usuwania
{: #deletion-strategy }

Który plik w zestawie zostaje zachowany, zobacz [Który plik zostaje zachowany](#which-file-is-kept).

### Zezwól na „Usuń wszystko”
{: #make-delete-all-possible }

Pokazuje opcje usunięcia każdego pliku w zestawie, tak aby nie została żadna kopia. Domyślnie wyłączone.

### Minimalny rozmiar
{: #minimum-size }

Pomija pliki mniejsze niż ten rozmiar. Domyślnie 512 KB.

### Pomiń niestandardowe typy plików
{: #skip-uncommon-file-types }

Uwzględnia tylko popularne obrazy, filmy, pliki audio, dokumenty, archiwa i pliki APK. Domyślnie włączone. Typ jest określany na podstawie rozszerzenia pliku.

### Metoda wykrywania
{: #detection-method }

Włącz [Sumę kontrolną zawartości](#content-checksum), [Percepcyjny skrót](#perceptual-hash) i [Sygnatury multimediów](#media-fingerprint).
