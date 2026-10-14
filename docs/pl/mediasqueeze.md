---
title: "Kompresja multimediów"
lang: pl
translation_key: mediasqueeze
permalink: /pl/mediasqueeze
source_hash: 1d878fd5a77634c5
---
# Kompresja multimediów (Media Squeeze)
{: #media-squeeze }

Kompresja multimediów (Media Squeeze) kompresuje zdjęcia i filmy, aby zajmowały mniej miejsca. W zamian za odzyskane miejsce tracisz nieco na jakości.

**Kompresja trwale obniża jakość i nie da się jej cofnąć.** Oryginalny plik zostaje zastąpiony i nie jest zachowywana żadna kopia. Zanim zaczniesz, sprawdź ustawienia i podgląd.

Skanowanie jest bezpłatne. Kompresja wymaga uaktualnienia.

## Jak to działa
{: #how-it-works }

1. Wybierz foldery do przeszukania. Nie ma domyślnej lokalizacji, wybierz np. folder aparatu.
2. Ustaw jakość kompresji i minimalny wiek.
3. Uruchom skanowanie. Każdy wynik pokazuje swój rozmiar i szacowaną oszczędność.
4. Dotknij pojedynczego elementu, zaznacz kilka długim naciśnięciem albo użyj `Kompresuj wszystko`. Okno potwierdzenia najpierw pokazuje łączny rozmiar i szacowane oszczędności.

Jeśli zaznaczenie zawiera tylko zdjęcia albo tylko filmy, przycisk `Zobacz porównanie` w oknie potwierdzenia pokazuje oryginał i wersję skompresowaną obok siebie. W przypadku filmów porównanie obejmuje jedną klatkę i jest tylko przybliżone.

### Co dzieje się z plikiem
{: #what-happens-to-a-file }

* Plik jest ponownie kodowany we własnym formacie: JPEG pozostaje plikiem JPEG, WebP pozostaje plikiem WebP, MP4 pozostaje plikiem MP4.
* Rozdzielczość obrazu pozostaje bez zmian, z wyjątkiem [bardzo dużych zdjęć](#compress-very-large-photos).
* W przypadku filmów ponownie kodowana jest tylko ścieżka wideo, z niższą przepływnością. Ścieżka audio jest kopiowana bez zmian.
* Data, lokalizacja, aparat i inne metadane EXIF zdjęć są zachowywane. Jeśli SD Maid nie może odczytać lub zachować metadanych EXIF zdjęcia, pozostawia zdjęcie bez zmian.
* Metadane XMP nie są zachowywane, np. informacje o sferze zdjęciowej (photo sphere) i panoramie albo oceny z edytorów zdjęć.
* Data modyfikacji pliku jest zachowywana, dzięki czemu galerie nadal sortują go w tym samym miejscu.
* Jeśli wersja skompresowana nie jest mniejsza, oryginał pozostaje nietknięty.

Szacowane oszczędności to przybliżone przypuszczenie oparte na typie pliku i jakości, a nie pomiar. Rzeczywiste oszczędności mogą się różnić.

### Lokalizacje
{: #locations }

Kompresja multimediów potrzebuje bezpośredniego dostępu do plików i uprawnień do zapisu w ich folderach. Nie używa roota ani ADB. Niektórych lokalizacji wybranych w systemowym selektorze folderów Androida, np. dysków USB, nie można użyć i są one usuwane z wyboru.

## Wcześniej skompresowane pliki
{: #previously-compressed-files }

SD Maid pamięta, które pliki już skompresował, a które nie zmniejszyły się, i pomija je w kolejnych skanowaniach. Rozpoznaje plik po jego zawartości, więc działa to również po zmianie nazwy lub przeniesieniu pliku. Ponowna kompresja pliku kosztuje więcej jakości i daje niewielką oszczędność.

Historia jest przechowywana na twoim urządzeniu i stanowi część kopii zapasowej ustawień SD Maid. Wyczyszczenie jej sprawia, że wcześniej skompresowane pliki znów mogą zostać uwzględnione, chyba że mają znacznik EXIF, a opcja [Zapisz znacznik EXIF](#write-exif-marker) jest włączona. Wyczyszczenie historii nie zmienia żadnych plików.

## Zdjęcia domyślnie pomijane
{: #photos-that-are-skipped-by-default }

Niektóre zdjęcia zawierają dane, których kompresja nie potrafi zachować. SD Maid pomija je, jeśli wykryje takie dane, chyba że włączysz odpowiednie ustawienie. Wykrywanie nie jest kompletne: zdjęcia w formatach, których nie rozpoznaje, są kompresowane i tracą te dane.

* **Zdjęcia HDR i głębi**: zdjęcia HDR stają się zwykłymi zdjęciami, a zdjęcia portretowe tracą dane, na których opiera się rozmycie tła.
* **Ruchome zdjęcia (Motion Photos)**: zdjęcie zostaje zachowane, klip wideo zostaje usunięty.
* **Bardzo duże zdjęcia** (o szerokości lub wysokości 8192 pikseli lub więcej): ich szerokość i wysokość są zmniejszane o połowę, a w razie potrzeby ponownie o połowę, aż oba wymiary będą mniejsze niż 8192 piksele. Panorama o szerokości 20 000 pikseli zostaje zmniejszona do szerokości 5000 pikseli.

Pliki HEIC zawierające wiele obrazów są zawsze pomijane.

Podsumowanie skanowania zlicza zdjęcia pominięte z powodu danych HDR lub głębi, ruchome zdjęcia i bardzo duże zdjęcia, a także pliki, które nie były dostępne. Pliki pominięte, ponieważ były już wcześniej skompresowane, nie są liczone, podobnie jak pliki HEIC z wieloma obrazami, chyba że mają też dane HDR lub głębi.

## Ustawienia
{: #settings }

Jakość kompresji, lokalizacje wyszukiwania i minimalny wiek ustawia się na ekranie Kompresji multimediów przed skanowaniem. Wszystko inne znajduje się w `Ustawienia > Kompresja multimediów`.

### Kompresja jakości
{: #compression-quality }

Domyślnie 80. Niższa jakość oznacza mniejsze pliki, ale gorszą jakość obrazu. Poniżej 40 obrazy mogą stać się nieużyteczne. Przy 95 i więcej niewiele da się zaoszczędzić.

### Minimalny wiek
{: #minimum-age }

Uwzględnia tylko pliki starsze niż ta wartość. Domyślnie 90 dni, na podstawie daty modyfikacji pliku.

### Minimalny rozmiar pliku
{: #minimum-file-size }

Pomija pliki mniejsze niż ta wartość. Domyślnie 512 KB.

### JPEG
{: #jpeg }

Uwzględnia obrazy JPEG (`.jpg`, `.jpeg`). Domyślnie włączone.

### WebP
{: #webp }

Uwzględnia obrazy WebP (`.webp`). Domyślnie włączone. Animowane pliki WebP nie są wykrywane i straciłyby animację.

### HEIC/HEIF
{: #heicheif }

Uwzględnia obrazy HEIC/HEIF (`.heic`, `.heif`). Domyślnie wyłączone. Wymaga Androida 9 lub nowszego. Map wzmocnienia HDR (gain maps), danych głębi oraz części Live/Motion Photo nie da się zachować w plikach HEIC.

### Wideo MP4
{: #mp4-video }

Uwzględnia filmy MP4 (`.mp4`). Domyślnie wyłączone. Kompresja filmu wymaga wolnego miejsca o wielkości około 110% jego rozmiaru w tej samej pamięci.

### Kompresuj zdjęcia HDR i głębi
{: #compress-hdr--depth-photos }

Zobacz [Zdjęcia domyślnie pomijane](#photos-that-are-skipped-by-default). Domyślnie wyłączone.

### Kompresuj ruchome zdjęcia
{: #compress-motion-photos }

Zachowuje zdjęcie i usuwa klip wideo. Domyślnie wyłączone.

### Kompresuj bardzo duże zdjęcia
{: #compress-very-large-photos }

Uwzględnia zdjęcia o szerokości lub wysokości 8192 pikseli lub więcej i zmniejsza ich rozdzielczość, zobacz [Zdjęcia domyślnie pomijane](#photos-that-are-skipped-by-default). Domyślnie wyłączone.

### Pomiń poprzednio skompresowane
{: #skip-previously-compressed }

Pomija pliki, które SD Maid już skompresował. Domyślnie włączone.

### Zapisz znacznik EXIF
{: #write-exif-marker }

Dodaje znacznik do komentarza użytkownika w EXIF zdjęcia, aby SD Maid rozpoznawał je jako skompresowane nawet po wyczyszczeniu historii kompresji. SD Maid sprawdza znacznik tylko wtedy, gdy to ustawienie jest włączone. Domyślnie wyłączone. Dotyczy tylko plików JPEG i WebP.

### Historia kompresji
{: #compression-history }

Pokazuje, ile plików jest w historii. Dotknij, aby wyczyścić historię.

## Wykluczenia
{: #exclusions }

Kompresja multimediów pomija ścieżki, które są ogólnie [wykluczone](/pl/exclusions). Możesz też wykluczać wyniki z listy wyników, co dotyczy tylko Kompresji multimediów.

Kompresji multimediów nie można uruchamiać za pomocą [Harmonogramu (Scheduler)](/pl/scheduler).
