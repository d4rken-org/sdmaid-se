---
title: "Wyszukiwanie pozostałości"
lang: pl
translation_key: corpsefinder
permalink: /pl/corpsefinder
source_hash: 9e16286f0fda1dc8
---
# Wyszukiwanie pozostałości (CorpseFinder)
{: #corpse-finder }

Narzędzie `Wyszukiwanie pozostałości` (CorpseFinder) znajduje pliki należące do aplikacji, które nie są już zainstalowane.


## Poziom ryzyka
{: #risklevel }

Domyślnie wszystkie opcje poziomu ryzyka są wyłączone. Włączaj je tylko wtedy, gdy wiesz, co robisz, a przed usunięciem czegokolwiek najpierw sprawdź wyniki.
Włączenie opcji poziomu ryzyka zwiększa szansę, że zostaną usunięte pliki, których nie chciałeś usuwać.

Poziomy ryzyka dotyczą głównie lokalizacji z białej listy, takich jak katalog główny twojej pamięci publicznej (`/storage/emulated/0`). Takie lokalizacje nie mają wspólnego schematu nazewnictwa, a SD Maid może ustalić ich właściciela tylko na podstawie ręcznie utrzymywanej bazy danych.


### Uwzględnij pożądane pozostałości
{: #include-desirable-remnants }

Pożądane pozostałości to pliki o znanych właścicielach, którzy nie są zainstalowani, ale które SD Maid wewnętrznie oznacza jako `KEEPER`. Zwykle są to pliki utworzone przez użytkownika, np. zrobione przez ciebie zdjęcia.

### Uwzględnij powszechne nazwy
{: #include-common-names }

Niektóre aplikacje używają po prostu bardzo powszechnych nazw. Istnieją pewnie setki aplikacji do notatek, które tworzą folder `/sdcard/Notes`, aby zapisywać w nim dane. SD Maid może znać jedną z tych aplikacji, która nie jest zainstalowana na twoim telefonie, ale SD Maid nie ma pewności, że tego folderu nie używa jeszcze inna aplikacja na twoim telefonie, o której SD Maid nie wie, że też korzysta z tego folderu. 

## Filtr
{: #filter }

Każdy filtr odpowiada za określony obszar danych. Po włączeniu filtra Wyszukiwanie pozostałości przeszuka ten dodatkowy obszar.

### Karta SD
{: #sdcard }

* Zwykle miejsce, w którym użytkownik umieszcza pliki
* Pamięć publiczna (wbudowana oraz wymienna), z wyłączeniem określonych podkatalogów w `Android/data`.
* Pliki w tym miejscu nie mają schematu nazewnictwa.

Przykłady:

```bash
/storage/emulated/0/SDMaid
/storage/DEAD-BEEF/SDMaid
/sdcard/SDMaid
```

### Publiczne pliki multimedialne
{: #public-media }

* Przechowuje publiczne multimedia aplikacji, np. multimedia otrzymane przez WhatsApp. Foldery mają nazwy pakietów aplikacji.
* Podkatalog pamięci publicznej o specjalnym znaczeniu dla systemu operacyjnego. 

Przykłady:

```bash
/storage/emulated/0/Android/media/eu.darken.sdmse
/storage/DEAD-BEEF/Android/media/eu.darken.sdmse
/sdcard/Android/media/eu.darken.sdmse
```

### Publiczne dane aplikacji
{: #public-app-data }

* Dodatkowe pliki aplikacji.
* Foldery mają nazwy pakietów aplikacji.
* Podkatalog pamięci publicznej o specjalnym znaczeniu dla systemu operacyjnego.

Przykłady:

```bash
/storage/emulated/0/Android/data/eu.darken.sdmse
/storage/DEAD-BEEF/Android/data/eu.darken.sdmse
/sdcard/Android/data/eu.darken.sdmse
```

### Zasoby OBB
{: #obb-resources }

* Zwykle dodatkowe pliki zasobów gier.
* Foldery i pliki mają nazwy pakietów aplikacji.
* Podkatalog pamięci publicznej o specjalnym znaczeniu dla systemu operacyjnego.

Przykłady:

```bash
/storage/emulated/0/Android/obb/eu.darken.sdmse
/storage/DEAD-BEEF/Android/obb/eu.darken.sdmse
/sdcard/Android/obb/eu.darken.sdmse
```

### Prywatne dane aplikacji
{: #private-app-data }

* Foldery i pliki mają nazwy pakietów aplikacji.
* Podkatalog pamięci publicznej o specjalnym znaczeniu dla systemu operacyjnego.

Przykłady:

```bash
/data/data/eu.darken.sdmse
```

### Pamięć podręczna Dalvik
{: #dalvik-cache }

* Przechowuje zoptymalizowane wersje kodu aplikacji, aby działały szybciej.
* Znajduje się na partycji systemowej, często w katalogach przeznaczonych dla konkretnej architektury.
* Nazwy plików zawierają nazwę pakietu aplikacji.

Przykłady:

```bash
/data/dalvik-cache/arm64/data@app@eu.darken.sdmse@base.apk@classes.dex
/data/dalvik-cache/arm/data@app@eu.darken.sdmse@base.apk@classes.dex
```

### Biblioteki aplikacji
{: #app-libraries }

* Zawiera natywne biblioteki używane przez aplikacje, takie jak pliki `.so`.
* Zwykle znajdują się w katalogu danych aplikacji, w folderze `lib`.
* Nazwy plików zależą od architektury i wymagań biblioteki.

Przykłady:

```bash
/data/app/eu.darken.sdmse-1/lib/arm64/libexample.so
/data/app/eu.darken.sdmse-2/lib/arm/libexample.so
```

### Źródła aplikacji
{: #app-sources }

* Chodzi o pliki APK, które stanowią kod źródłowy aplikacji.
* Przechowywane w określonych katalogach dla aplikacji systemowych, użytkownika lub producenta.
* Pliki mają nazwy według nazwy pakietu i wersji aplikacji.

Przykłady:

```bash
/data/app/eu.darken.sdmse-1/base.apk
/system/app/ExampleApp/ExampleApp.apk
```

### Prywatne źródła aplikacji
{: #private-app-sources }

* Podobne do źródeł aplikacji, ale przechowywane w chronionych katalogach dla większego bezpieczeństwa.
* Dostępne tylko dla systemu lub danej aplikacji.
* Pliki mają nazwy według nazwy pakietu aplikacji.

Przykłady:

```bash
/data/app-private/eu.darken.sdmse/base.apk
```
