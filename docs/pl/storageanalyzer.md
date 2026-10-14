---
title: "Analiza pamięci"
lang: pl
translation_key: storageanalyzer
permalink: /pl/storageanalyzer
source_hash: 2dbf8557900cc687
---
# Analiza pamięci (StorageAnalyzer)
{: #storage-analyzer }

Analiza pamięci (StorageAnalyzer) odpowiada na pytanie „Co zajmuje tyle miejsca w mojej pamięci?”

Może wyglądać jak przeglądarka plików, ale nią nie jest. Wszystko jest posortowane według rozmiaru, więc możesz podążać za największymi elementami aż do tego, co faktycznie zapełnia twoją pamięć.

## Pamięci
{: #storages }

Pierwszy ekran pokazuje każdą pamięć twojego urządzenia:

* **Pamięć podstawowa**: wbudowana pamięć urządzenia, w tym aplikacje i dane systemowe.
* **Pamięć zewnętrzna**: zwykle wymienna karta SD.
* **Pamięć przenośna**: np. pendrive podłączony przez USB.

Każda karta pokazuje wolne i zajęte miejsce oraz to, jak bardzo pamięć zmieniła się w ciągu ostatnich 7 dni, zobacz [Trend pamięci](#storage-trend).

## Kategorie
{: #categories }

Otwarcie pamięci uruchamia jej skanowanie i dzieli zajęte miejsce na kategorie:

* **Aplikacje**: zainstalowane aplikacje wraz z ich kodem, danymi, pamięcią podręczną i multimediami.
* **Pliki użytkownika**: zdjęcia, filmy, pobrane pliki i wszelkie inne dane, które nie należą do konkretnej aplikacji.
* **Dane systemowe**: system operacyjny i wszystko, czego nie udało się przypisać gdzie indziej.
* **Inni użytkownicy**: aplikacje i pliki innych użytkowników lub profili służbowych na tym urządzeniu.

Dane systemowe i Inni użytkownicy istnieją tylko dla pamięci podstawowej.

### Aplikacje
{: #apps }

Aplikacje są posortowane według rozmiaru i można je wyszukiwać po nazwie. Mogą tu też być wyświetlane aplikacje systemowe, wraz z ich danymi. W przypadku aplikacji systemowych bez zainstalowanej aktualizacji wbudowany kod aplikacji jest liczony jako Dane systemowe. Otwarcie aplikacji pokazuje, na co idzie jej miejsce:

* **Kod aplikacji**: sama aplikacja. Odinstalowanie lub zarchiwizowanie aplikacji zwalnia to miejsce.
* **Dane aplikacji**: dane, ustawienia i pamięć podręczna.
* **Multimedia aplikacji**: pliki multimedialne tej aplikacji, np. odebrane zdjęcia z WhatsAppa.
* **Dodatkowe pliki aplikacji**: dane, które aplikacja umieściła poza własnymi folderami, np. zdjęcia lub pobrane pliki w folderze najwyższego poziomu.

`Pokaż ustawienia` otwiera stronę ustawień systemowych aplikacji.

### Dlaczego liczby nie zgadzają się z ustawieniami Androida
{: #why-the-numbers-dont-match-androids-settings }

Kategorie analizy nie będą dokładnie odpowiadać podziałowi w ustawieniach Androida.

**Dane systemowe** to to, co zostaje z zajętego miejsca po policzeniu Aplikacji, Plików użytkownika i Innych użytkowników. Trafia tam wszystko, czego SD Maid nie widzi lub nie może przypisać: chronione obszary systemowe, pamięć innych użytkowników, której nie udało się zmierzyć, narzut systemu plików oraz różnica między deklarowanym a rzeczywistym rozmiarem pamięci.

Bez uprawnień [Zasobnik aplikacji](/pl/setup#app-inventory) i [Statystyki używania](/pl/setup#usage-statistics) aplikacji nie da się zmierzyć. Ich miejsce jest wtedy wyświetlane jako Dane systemowe.

## Dostęp
{: #access }

To, co widzi analiza, zależy od dostępu, jaki ma SD Maid:

* **Bez roota** kod aplikacji i prywatne dane aplikacji są pokazywane tylko jako sumy i nie można ich przeglądać. Na Androidzie 13 i nowszym `Android/data` również wymaga roota lub dostępu ADB.
* **Z [rootem](/pl/setup#root-access) lub [dostępem ADB](/pl/setup#shizuku)** możesz przeglądać Dane systemowe. Root pozwala też przeglądać kod aplikacji i prywatne dane aplikacji oraz w pełni mierzyć innych użytkowników.
* **Bez uprawnienia Zasobnik aplikacji** nie da się przypisać plików do aplikacji, do których należą. Pliki użytkownika są wtedy wyświetlane tylko do odczytu: widzisz rozmiary, ale usuwanie, wykluczanie, tworzenie filtrów i sesji Przesuwacza są wyłączone.

Elementy, do których SD Maid nie ma dostępu, po dotknięciu pokazują `Brak bezpośredniego dostępu`.

Foldery zawierające ponad 100 000 elementów są tylko sumowane i nie można ich przeglądać.

## Czynności
{: #actions }

Dotknij pliku, aby otworzyć go w innej aplikacji. Przytrzymaj elementy, aby je zaznaczyć, a następnie:

* **Usuń**: usuwa zaznaczone elementy po potwierdzeniu. Bezpłatne.
* **Wyklucz**: tworzy [wykluczenie](/pl/exclusions) dla zaznaczonych ścieżek. Te wykluczenia dotyczą wszystkich narzędzi. Sama analiza nie korzysta z wykluczeń, ponieważ musi widzieć wszystko, aby obliczyć prawidłowe sumy.
* **Filtruj**: tworzy [filtr niestandardowy Sprzątania systemu](/pl/systemcleaner#custom-filters) dla zaznaczonych elementów. Wymaga uaktualnienia.
* **Utwórz sesję Przesuwacza**: rozpoczyna sesję [Przesuwacza (Swiper)](/pl/swiper) z zaznaczonymi elementami.

Dane systemowe i zawartość innych użytkowników można tylko przeglądać. Usuwanie w tych miejscach nie jest możliwe.

## Trend pamięci
{: #storage-trend }

SD Maid co kilka godzin i po każdym czyszczeniu zapisuje ilość wolnego miejsca w twoich pamięciach. `Trend pamięci` pokazuje to w formie wykresu ze znacznikami, ile zwolniło każde czyszczenie.

* Karty pamięci pokazują, jak bardzo każda pamięć zmieniła się w ciągu ostatnich 7 dni.
* Panel pokazuje łączną zmianę wszystkich pamięci albo zamiast tego szacunkową liczbę pozostałych dni, gdy pamięć się zapełnia.
* Jak długo przechowywane są migawki, ustawia się w `Ustawienia > Historia > Historia pamięci`. Domyślnie 90 dni, maksymalnie 365.

Wykres Trend pamięci wymaga uaktualnienia. Zmiana z 7 dni na kartach pamięci i w panelu oraz szacunkowa liczba pozostałych dni są bezpłatne.

## Ustawienia
{: #settings }

### Próg małej ilości miejsca na pamięci wewnętrznej
{: #low-storage-threshold }

Gdy wolne miejsce spadnie poniżej tej wartości, SD Maid uznaje, że w pamięci kończy się miejsce. Domyślnie `Automatycznie`: 5% rozmiaru pamięci, maksymalnie 2 GB.

### Ostrzeżenie o małej ilości miejsca na pamięci wewnętrznej
{: #low-space-warning }

Wymaga uaktualnienia. Ostrzega, gdy wolne miejsce w pamięci podstawowej osiągnie próg małej ilości miejsca. Przy co najmniej 5 dniach spójnej historii może też ostrzec wcześniej: gdy pamięć zmierza do osiągnięcia progu w ciągu 14 dni, a wolne miejsce wynosi już najwyżej 20% pamięci, z górnym limitem 25 GB. Domyślnie wyłączone.
