---
title: "Konfiguracja SD Maid SE"
lang: pl
translation_key: setup
permalink: /pl/setup
source_hash: a14003a95dd2336d
---
# Konfiguracja SD Maid SE
{: #setting-up-sd-maid-se }
Informacje o poszczególnych krokach konfiguracji SD Maid SE.

SD Maid dostosowuje się do dostępnego dostępu. Wszystkie uprawnienia są opcjonalne, ale jeśli nie przyznasz żadnych, większość funkcji nie będzie działać. SD Maid działa najlepiej, gdy wszystkie elementy konfiguracji są ukończone.

SD Maid automatycznie spróbuje wykonać niektóre kroki konfiguracji, jeśli dostępny jest [root](#root-access) lub [dostęp za pomocą ADB](#shizuku).

Niektóre uprawnienia pokrywają się lub zawierają się w sobie nawzajem, np. możesz dać SD Maid zarówno dostęp do roota, jak i dostęp za pomocą ADB. SD Maid zawsze próbuje użyć najlepszej możliwej metody dostępu dla każdej czynności, a jeśli to się nie uda, ponawia próbę z drugą najlepszą metodą, potem z trzecią itd. Przyznanie SD Maid pokrywających się uprawnień zwiększa niezawodność.

> **Wskazówka:** Niektóre z poniższych kroków konfiguracji wymagają uruchomienia poleceń ADB. Jeśli nie masz skonfigurowanego ADB na komputerze, możesz użyć [Web ADB Helper](https://d4rken.github.io/web-adb/), aby uruchomić je bezpośrednio w przeglądarce.

## Zarządzanie pamięcią
{: #manage-storage }

Uprawnienie „Zarządzanie pamięcią” to uprawnienie o szerokim zakresie dostępu do pamięci.

* Na Androidzie 12 i starszym daje dostęp do całej pamięci publicznej.
* Na Androidzie 13+ nadal daje dostęp do całej pamięci publicznej, ale nie do podkatalogów w `Android/data`.

## Statystyki używania
{: #usage-statistics }

Uprawnienie do statystyk używania daje SD Maid dostęp do rozmiarów danych i pamięci podręcznej oraz informacji, czy aplikacja działa lub kiedy była ostatnio używana.

## Mechanizm dostępu do pamięci
{: #storage-access-framework }
SD Maid używa [Android Storage Access Framework (SAF)](http://developer.android.com/guide/topics/providers/document-provider.html), aby uzyskać dostęp do dodatkowych obszarów pamięci. SD Maid poprosi cię o przyznanie dostępu do określonych ścieżek, które nie są dostępne przy samym uprawnieniu „Zarządzanie pamięcią” (Just Android Things™️).

* Naciśnij przycisk `Udziel dostępu` 
* Otworzy się nowe okno
* Może być już wybrana właściwa ścieżka, w przeciwnym razie musisz przejść do ścieżki, której chce SD Maid.
* Następnie naciśnij przycisk `Użyj tego folderu`, a pozycja konfiguracji dla tej ścieżki zostanie odblokowana i oznaczona na zielono.

Powtarzaj ten proces, aż wszystkie ścieżki będą zielone.

<a href="/assets/images/setup/saf-1.webp"><img src="/assets/images/setup/saf-1.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-2.webp"><img src="/assets/images/setup/saf-2.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-3.webp"><img src="/assets/images/setup/saf-3.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-4.webp"><img src="/assets/images/setup/saf-4.webp" width="100" alt=""></a>

### SAF: Rozwiązywanie problemów
{: #saf-troubleshooting }
Na wypadek, gdy coś nie pójdzie zgodnie z planem.

#### SAF: dostęp do `Android/data|obb`
{: #saf-androiddataobb-access }

Na Androidzie 11 i Androidzie 12 przyznanie dostępu do `Android/data|obb` nie jest możliwe w nowszych wersjach systemowej aplikacji `Files` („Pliki”, `com.google.android.documentsui`). Jeśli zainstalowana aplikacja `Files` jest przeznaczona dla Androida 14 (poziom API 34) lub nowszego, SD Maid ukrywa prośbę o dostęp SAF do tych ścieżek (tak jak na Androidzie 13+).

Google Play aktualizuje aplikację `Files` przez usługę `Google Play system update` („Aktualizacja systemowa Google Play”). Oryginalna aplikacja „Files” (zbudowana dla poziomu API 31, np. `aml_doc_310851020 (310851020)`), dostarczana z systemem Android 12, pozwala SD Maid otworzyć okno wyboru ścieżki SAF dla `Android/Data`, natomiast najnowsza aplikacja „Files” (zbudowana dla poziomu API 34 lub nowszego, np. `14-10492947 (340916000)`) na to nie pozwala.

**Obejście:**

Możesz odinstalować aktualizację aplikacji `Files`, a następnie skonfigurować SD Maid. Ponowna aktualizacja aplikacji `Files` nie zepsuje już przyznanych uprawnień.

* Otwórz ustawienia urządzenia
* Przejdź do „Aplikacje i powiadomienia” lub „Menedżer aplikacji”
* Znajdź aplikację systemową `Files`
* Dotknij menu z trzema kropkami, zwykle w prawym górnym rogu
* Wybierz „Odinstaluj aktualizacje”

lub

_To polecenie możesz też uruchomić za pomocą [Web ADB Helper](https://d4rken.github.io/web-adb/)._

`adb uninstall com.google.android.documentsui`

#### SAF: OPEN_DOCUMENT_TREE ActivityNotFoundException
{: #saf-open_document_tree-activitynotfoundexception }

Przyznawanie dostępu do ścieżek przez Storage Access Framework wymaga aplikacji, w której użytkownik potwierdza ścieżki, do których zostanie przyznany dostęp. Tą aplikacją jest aplikacja „Dokumenty” (Documents). To aplikacja systemowa wbudowana w każde urządzenie z Androidem od wersji 5.0  (`com.android.documentsui` lub `com.google.android.documentsui`). Jeśli pojawia się ten błąd, to ktoś usunął lub wyłączył tę aplikację. W większości przypadków ktoś uruchomił nadgorliwe narzędzie do „usuwania bloatware”, które ją wyłączyło. Możesz ją ponownie włączyć przez ADB:

_Te polecenia możesz też uruchomić za pomocą [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```
adb shell pm enable com.android.documentsui
or
adb shell pm enable com.google.android.documentsui
```

#### SAF: Puste okno
{: #saf-empty-window }
Jeśli okno służące do przyznawania dostępu do ścieżek jest puste, to pakiet dostawcy pamięci zewnętrznej (`com.android.externalstorage`) jest prawdopodobnie wyłączony (najpewniej przez nadgorliwe narzędzie do „usuwania bloatware”). Możesz go ponownie włączyć przez ADB:

_To polecenie możesz też uruchomić za pomocą [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```
adb shell pm enable com.android.externalstorage
```

Ponowne uruchomienie `Google Play system update` („Aktualizacja systemowa Google Play”) przywróci aktualizację. Możesz też ręcznie pobrać zaktualizowaną wersję z [APKMirror.com](https://www.apkmirror.com/apk/google-inc/files-6/).

## Powiadomienia
{: #notifications }

Bez tego uprawnienia SD Maid nie będzie mógł wyświetlać powiadomień. W niektórych ROM-ach może to negatywnie wpływać na niezawodność działania w tle.

## Zasobnik aplikacji
{: #app-inventory }

SD Maid wymaga uprawnienia `QUERY_ALL_PACKAGES`, aby sprawdzić, które aplikacje są zainstalowane na twoim urządzeniu (tzn. [„wgląd w spis zainstalowanych aplikacji”](https://support.google.com/googleplay/android-developer/answer/10158779?hl=en)). Zobacz też [„Uprawnienie do statystyk używania”](/pl/setup#usage-statistics)

Uprawnienie `QUERY_ALL_PACKAGES` jest niezbędne dla:

* [Wyszukiwania pozostałości (CorpseFinder)](/pl/corpsefinder): aby ustalić, które pliki można usunąć, SD Maid musi wiedzieć, czy masz zainstalowane aplikacje, które mogą ich potrzebować.
* [Sprzątania aplikacji (AppCleaner)](/pl/appcleaner): ustalanie pamięci podręcznej każdej aplikacji
* [Zarządzania aplikacjami (AppControl)](/pl/appcontrol): możliwość wyświetlenia wszystkich zainstalowanych aplikacji
* [Analizy pamięci (StorageAnalyzer)](/pl/storageanalyzer): przypisywanie zajętej pamięci do zainstalowanych aplikacji

To uprawnienie istnieje od [Androida 11](https://developer.android.com/training/package-visibility) i zwykle jest przyznawane domyślnie. Od Androida 14 istnieją urządzenia (np. [#1015](https://github.com/d4rken-org/sdmaid-se/issues/1015), [#1354](https://github.com/d4rken-org/sdmaid-se/issues/1354)), na których uprawnienie nie jest przyznawane domyślnie lub może zostać cofnięte.

[<img src="/assets/images/setup/app-inventory.webp" width="100">](/assets/images/setup/app-inventory.webp)

### Nieprawidłowa lista aplikacji
{: #invalid-app-list }

Na niektórych urządzeniach uprawnienie „App list” („Lista aplikacji”, `QUERY_ALL_PACKAGES` / `GET_INSTALLED_APPS`) jest przyznane, ale system i tak zwraca **niekompletną listę zainstalowanych aplikacji**, na przykład listę, w której brakuje podstawowych pakietów systemowych, takich jak `android`. Gdy SD Maid to wykryje, zamiast kontynuować, pokazuje na karcie konfiguracji ostrzeżenie **„Nieprawidłowy zasobnik aplikacji”** i informację **„Możliwe ograniczenie urządzenia”**.

SD Maid celowo zatrzymuje się w tym miejscu: jeśli nie widzi pełnej listy zainstalowanych aplikacji, nie może wiarygodnie ustalić, które pliki należą do aplikacji, która nadal jest zainstalowana. Kontynuowanie mimo to mogłoby oznaczać usunięcie danych należących do aplikacji, której SD Maid po prostu nie widział, dlatego funkcje związane z aplikacjami pozostają wyłączone, dopóki nie będzie dostępna kompletna lista.

**Co możesz spróbować:**

* Otwórz ustawienia systemowe i upewnij się, że SD Maid ma przyznane uprawnienie „App list” („Lista aplikacji”). U niektórych producentów ten przełącznik ma inną nazwę (np. „Query all packages”) albo znajduje się na osobnym ekranie „Prywatność” lub „Menedżer uprawnień”.
* Sprawdź, czy wbudowany oszczędzacz baterii, ochrona prywatności lub funkcja „ukrywania aplikacji” nie filtruje listy aplikacji widocznych dla SD Maid, i wyklucz z niej SD Maid.
* Uruchom ponownie urządzenie i otwórz ponownie SD Maid, aby stan uprawnienia został ponownie oceniony.

Jeśli uprawnienie „App list” jest już przyznane, a lista nadal jest zgłaszana jako nieprawidłowa, zwykle jest to **ograniczenie urządzenia**, a nie coś, co możesz naprawić: niektóre systemy (na przykład HarmonyOS lub mocno zmodyfikowane kompilacje Androida) nigdy nie przekazują kompletnej listy aplikacji aplikacjom zewnętrznym. Przykład znajdziesz w [#2441](https://github.com/d4rken-org/sdmaid-se/issues/2441).

W takim przypadku narzędzia związane z aplikacjami ([Wyszukiwanie pozostałości](/pl/corpsefinder), [Sprzątanie aplikacji](/pl/appcleaner), [Zarządzanie aplikacjami](/pl/appcontrol)) pozostają wyłączone, aby chronić twoje dane, ale [Analiza pamięci](/pl/storageanalyzer) nadal działa: pokazuje, ile miejsca zajmuje twoja pamięć, tylko z mniejszą liczbą szczegółów. Rozmiary folderów są wyświetlane tylko do odczytu i nie można ich przypisać do poszczególnych aplikacji.

## Usługa ułatwień dostępu
{: #accessibility-service }
Usługa ułatwień dostępu pozwala SD Maid obsługiwać urządzenie jak człowiek, tzn. widzieć ekran i naciskać przyciski. SD Maid może jej używać do automatyzacji żmudnych czynności, takich jak naciskanie „Wyczyść pamięć podręczną” w setkach aplikacji. Usługa ułatwień dostępu to usługa, której aplikacja nie może sama uruchomić: musi ją uruchomić system, ponieważ ten typ usługi daje aplikacji dodatkowy dostęp do urządzenia.

### ACS: Rozwiązywanie problemów
{: #acs-troubleshooting }

W różnych sytuacjach usługa może przestać działać i trzeba ją będzie ponownie włączyć:
* Aktualizujesz aplikację.
* Aplikacja ulega awarii. Jeśli tak się stanie, zgłoś to jako błąd. Jeśli zdarza się to zbyt często, możesz nie być w stanie ponownie uruchomić usługi przez jej wyłączenie i włączenie, dopóki nie uruchomisz ponownie urządzenia.
* System zabija aplikację z powodu zbyt agresywnych „optymalizacji baterii” (zobacz też <https://dontkillmyapp.com/>)
* Aplikacja zabezpieczająca producenta wyłącza usługę (zobacz [Zablokowana przez Phone Manager](#acs-blocked-by-phone-manager-oneplus-oppo-realme))

#### ACS: Usługa jest wyłączona
{: #acs-service-is-disabled }

Niektóre ROM-y (np. MIUI) słyną z agresywnego zatrzymywania wszystkiego, czego producent urządzenia nie umieścił na białej liście.
Jeśli ciągle jesteś proszony o ponowne włączenie usługi ułatwień dostępu, system może ją wyłączać za każdym razem, gdy SD Maid nie jest aktywnie używany.

Spróbuj wyłączyć optymalizacje baterii, a w ROM-ach MIUI jest dodatkowe ustawienie o nazwie „Autostart”, włącz je dla SD Maid. W przypadku urządzeń Xiaomi/POCO/Redmi:

* Przejdź do `Security>Manage applications>Auto start in background` i zezwól na to dla SD Maid.
* Przejdź do `Security>Increase speed>Lock apps` i wybierz SD Maid.


Możesz też rozważyć przyznanie dodatkowego uprawnienia, które [pozwala SD Maid automatycznie ponownie uruchamiać usługę](#acs-automatic-service-restart).

[<img src="/assets/images/setup/acs-disabled.webp" width="100">](/assets/images/setup/acs-disabled.webp)

#### ACS: Zablokowana przez Phone Manager (OnePlus, OPPO, Realme)
{: #acs-blocked-by-phone-manager-oneplus-oppo-realme }

W OxygenOS i ColorOS preinstalowana aplikacja „Phone Manager” („Menedżer telefonu”) może sama wyłączyć usługę ułatwień dostępu, zwykle w ciągu sekundy od zakończenia czyszczenia. Przełącznik ułatwień dostępu w Androidzie jest potem rzeczywiście wyłączony, więc wygląda to tak, jakby SD Maid przestał działać w trakcie pracy.

Wyłącz tę funkcję w `Phone Manager>Utilities>Block suspicious app activities`.

Ten sam ekran przechowuje listę tego, co zablokował. Wpisy oznaczone „Abnormal device control” („Nieprawidłowe sterowanie urządzeniem”) ze znacznikami czasu odpowiadającymi uruchomionym przez ciebie czyszczeniom potwierdzają, że to jest przyczyna. Przełącznik „Blocking alerts” („Alerty o blokowaniu”) obok niego decyduje tylko o tym, czy dostajesz powiadomienie, więc przy wyłączonych alertach dzieje się to po cichu.

#### ACS: Włączanie przez ADB
{: #acs-enable-via-adb }

Jeśli twoje urządzenie nie pozwala włączyć usług ułatwień dostępu z poziomu ekranu (np. urządzenie TV), możesz włączyć usługę przez ADB, uruchamiając:

_To polecenie możesz też uruchomić za pomocą [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```bash
adb shell settings put secure enabled_accessibility_services eu.darken.sdmse/.automation.core.AutomationService
```

#### ACS: Automatyczne ponowne uruchamianie usługi
{: #acs-automatic-service-restart }

_Uwaga: to zaawansowana technika przeznaczona dla doświadczonych użytkowników._

Możesz przyznać dodatkowe uprawnienie, które pozwala SD Maid samodzielnie ponownie uruchamiać usługę.
Uprawnienie nazywa się `WRITE_SECURE_SETTINGS` i można je przyznać tylko [przez ADB](https://www.google.com/search?q=android+adb).
Jeśli masz to skonfigurowane, uruchom to polecenie, aby przyznać uprawnienie:

_To polecenie możesz też uruchomić za pomocą [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```bash
adb shell pm grant eu.darken.sdmse android.permission.WRITE_SECURE_SETTINGS
```

Gdy to uprawnienie jest przyznane, SD Maid może w razie potrzeby użyć [opisanego tutaj](#acs-enable-via-adb) polecenia, aby włączyć usługę.
SD Maid użyje tego tylko wtedy, gdy co najmniej raz ręcznie włączyłeś usługę ułatwień dostępu.
Jeśli ponownie wejdziesz do konfiguracji i każesz SD Maid nie używać usługi ułatwień dostępu, ta technika automatycznego ponownego uruchamiania nie będzie używana, dopóki ponownie ręcznie nie włączysz usługi ułatwień dostępu.

Aby ponownie odebrać SD Maid to uprawnienie, użyj tego polecenia:

_To polecenie możesz też uruchomić za pomocą [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```bash
adb shell pm revoke eu.darken.sdmse android.permission.WRITE_SECURE_SETTINGS
```

Jeśli pojawia się `Operation not allowed: java.lang.SecurityException`, [zobacz tutaj](https://stackoverflow.com/questions/52079343). Niektóre urządzenia ograniczają uprawnienia ADB. Sprawdź, czy w opcjach programisty nie ma dodatkowego ustawienia (np. `Disable Permission Monitoring`), które trzeba przełączyć. Po użyciu polecenia możesz je znowu wyłączyć.

#### ACS: Advanced Protection
{: #acs-advanced-protection }

Na Androidzie 17 i nowszym tryb [**Advanced Protection**](https://developer.android.com/privacy-and-security/advanced-protection-mode) (ochrona zaawansowana) od Google uniemożliwia korzystanie z usługi ułatwień dostępu aplikacjom, które nie są dedykowanymi narzędziami ułatwień dostępu (czytniki ekranu, dostęp przez przełączniki itp.). SD Maid jest narzędziem do konserwacji i czyszczenia, więc gdy Advanced Protection jest włączona, jego usługi ułatwień dostępu **nie da się** włączyć: Android odrzuca usługę na poziomie systemu, nawet jeśli spróbujesz ją przyznać przez root lub dostęp za pomocą ADB.

SD Maid nie może sam się z tego wyłączyć: kwalifikują się tylko aplikacje, które Google klasyfikuje jako narzędzia ułatwień dostępu, a aplikacje czyszczące są wyraźnie wykluczone.

Masz dwie możliwości:

- **Użyj roota lub dostępu za pomocą ADB** Gdy root lub dostęp za pomocą ADB jest skonfigurowany, Sprzątanie aplikacji (AppCleaner) w SD Maid czyści pamięć podręczną aplikacji przez bezpośrednie wywołanie systemowe i **nie** potrzebuje usługi ułatwień dostępu. Z **rootem** obejmuje to także czyszczenie pojedynczych wybranych aplikacji. Z **dostępem za pomocą ADB** pełne przejście „wyczyść wszystkie aplikacje” działa bez usługi ułatwień dostępu, ale czyszczenie pamięci podręcznej *konkretnych* wybranych aplikacji nadal od niej zależy. Skonfiguruj to w SD Maid w Konfiguracja → [Dostęp do roota](#root-access) lub [Dostęp za pomocą ADB](#shizuku).
- **Wyłącz Advanced Protection**, jeśli zależy ci właśnie na czyszczeniu opartym na ułatwieniach dostępu. Znajduje się to w `Settings → Security & privacy → Advanced Protection` (dokładna ścieżka zależy od urządzenia). Ponowne włączenie jej później znowu wyłączy usługę ułatwień dostępu.

#### ACS: Ograniczenia AppOps
{: #acs-appops-restrictions }

Android 13+ może nakładać dodatkowe ograniczenia na aplikacje zainstalowane spoza sklepu (sideloading). Jeśli więc SD Maid nie został zainstalowany ze znanego (Google) źródła, takiego jak Google Play lub inne sklepy z aplikacjami, możesz nie być w stanie bezpośrednio przyznać SD Maid niektórych uprawnień, np. uruchomić usługi ułatwień dostępu.

##### OxygenOS 15 - OnePlus
{: #oxygenos-15---oneplus }

OnePlus zapomniał (albo po prostu nie zdecydował się) umieścić w ustawieniach aplikacji w OxygenOS 15 czynności `Allow restricted settings` („Zezwól na ustawienia z ograniczonym dostępem”). Jeśli nie instalujesz przez Google Play, instalator musi ustawić flagę `INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS`. Jeśli masz skonfigurowane Shizuku, możesz użyć [aplikacji „Install with Options”](https://github.com/zacharee/InstallWithOptions).


##### Inne ROM-y
{: #other-roms }

Aby znieść te ograniczenia, otwórz w systemie ekran ustawień aplikacji SD Maid. Otwórz menu w prawym górnym rogu i wybierz `Allow restricted settings` („Zezwól na ustawienia z ograniczonym dostępem”), a potem możesz wrócić do listy usług ułatwień dostępu i włączyć usługę SD Maid.

**Uwaga:**
Aby poprawić użyteczność (to sarkazm), Google zdecydował, że to ustawienie będzie widoczne tylko wtedy, gdy:

* Co najmniej raz zobaczyłeś komunikat „Restricted setting” („Ustawienie z ograniczonym dostępem”) po kliknięciu pozycji usługi ułatwień dostępu SD Maid
* Otworzyłeś ustawienia systemowe i ręcznie przeszedłeś do strony ustawień SD Maid, a nie przez inną aplikację lub skróty.

😮‍💨


Dodatkowe informacje w tle [znajdziesz tutaj](https://blog.esper.io/android-13-sideloading-restriction-harder-malware-abuse-accessibility-apis/).

<a href="/assets/images/setup/acs-restricted-settings-1.webp"><img src="/assets/images/setup/acs-restricted-settings-1.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-2.webp"><img src="/assets/images/setup/acs-restricted-settings-2.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-3.webp"><img src="/assets/images/setup/acs-restricted-settings-3.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-4.webp"><img src="/assets/images/setup/acs-restricted-settings-4.webp" width="100" alt=""></a>

## Dostęp do roota
{: #root-access }

Z dostępem do roota SD Maid ma pełny dostęp do urządzenia i może przeprowadzić najdokładniejsze możliwe skanowanie. Zwykle oznacza to, że odblokowałeś bootloader i skonfigurowałeś Magisk.

Jeśli SD Maid uzna, że twoje urządzenie może być zrootowane, najpierw poprosi o dostęp do roota. Jeśli dostęp do roota zostanie przyznany, SD Maid użyje go, aby przyznać jak najwięcej pozostałych uprawnień. Dostęp do roota to najwyższy poziom dostępu, jaki możesz dać aplikacji, pozostałe uprawnienia są wtedy potrzebne tylko dla wygody i wydajności, dlatego nie wymagają dodatkowej zgody podczas konfiguracji, jeśli SD Maid ma już roota.

## Dostęp za pomocą ADB
{: #shizuku }

Dostęp za pomocą ADB pozwala SD Maid używać interfejsu debugowania Androida (ADB) do operacji z podwyższonymi uprawnieniami. Potrzebna jest do tego oddzielna aplikacja, która uruchamia usługę ADB na twoim urządzeniu i daje do niej dostęp innym aplikacjom: [Porter](#porter) lub [Shizuku](#using-shizuku). To modyfikacja, którą możesz wprowadzić w telefonie i z której mogą korzystać inne aplikacje.

Jest podobna do roota, ale nie taka sama. Uprawnienia są nieco słabsze, ale modyfikacja nie jest trwała i nie wymaga odblokowania bootloadera.

### Porter
{: #porter }

[Porter](https://porter.darken.eu) to mój utrzymywany fork Shizuku z opcjonalną obsługą roota. Na jego stronie wyjaśniono, jak go zainstalować i uruchomić. Na Androidzie 11 i nowszym debugowanie bezprzewodowe pozwala uruchomić go bez komputera.

Jeśli konfigurujesz dostęp za pomocą ADB po raz pierwszy, użyj Portera. Wersje SD Maid z GitHuba i F-Droid kierują cię do Portera, gdy nie jest zainstalowana żadna aplikacja do dostępu ADB. Wersja z Google Play kieruje cię do Shizuku w Google Play, ale z Porterem działa tak samo.

Jeśli zainstalowane są zarówno Porter, jak i Shizuku, SD Maid używa Portera. Karta konfiguracji pokazuje wtedy `Porter jest zainstalowany, więc SD Maid czeka na Porter zamiast Shizuku`. Uruchom Portera, aby kontynuować, albo odinstaluj go, jeśli chcesz, żeby SD Maid używał Shizuku.

### Używanie Shizuku
{: #using-shizuku }

Istnieją dwie wersje Shizuku:

* [Fork thedjchi](https://github.com/thedjchi/Shizuku) ([instrukcja konfiguracji](https://github.com/thedjchi/Shizuku/wiki/setup)) jest aktywnie rozwijany i według zgłoszeń działa na urządzeniach, na których oryginał [nigdy się nie łączy](#shizuku-never-connects).
* [Oryginał od RikkaApps](https://shizuku.rikka.app/guide/setup/) nadal dobrze działa na większości urządzeń.

Nie można ich zainstalować jednocześnie. Jeśli oryginał już u ciebie działa, nie ma potrzeby zmiany.

### Przyznawanie dostępu
{: #granting-access }

Po skonfigurowaniu Portera lub Shizuku:

* W konfiguracji SD Maid wybierz `Spróbuj użyć dostępu za pomocą ADB`. To ustawienie wewnętrzne SD Maid, za jego pomocą możesz włączać i wyłączać korzystanie przez SD Maid z dostępu za pomocą ADB
* Po wyrażeniu zgody okno Portera lub Shizuku zapyta, czy SD Maid może z niego korzystać
* Potwierdź w tym oknie
* Karta konfiguracji `Dostęp za pomocą ADB` pokazuje, czy SD Maid może się połączyć

Skonfigurowanie dostępu za pomocą ADB, gdy SD Maid ma już dostęp do roota, nie włącza dodatkowych funkcji, ale zwiększa niezawodność. Jeśli czynności z uprawnieniami roota się nie powiodą, SD Maid ponowi próbę z dostępem za pomocą ADB.

### Dostęp za pomocą ADB: Rozwiązywanie problemów
{: #shizuku-troubleshooting }

Stan dostępu za pomocą ADB w SD Maid możesz sprawdzić, przechodząc do `Ustawienia > Konfiguracja` i oglądając kartę konfiguracji `Dostęp za pomocą ADB`. Powinna na niej widnieć informacja `SD Maid może teraz używać dostępu za pomocą ADB przez Porter` (lub `przez Shizuku`).

* `Porter nie udzielił jeszcze zgody dla SD Maid`: potwierdź okno dostępu albo zezwól SD Maid w aplikacji Porter (lub Shizuku).
* `Porter odmawia dostępu SD Maid`: SD Maid otrzymał trwałą odmowę. Otwórz Porter (lub Shizuku) i zezwól tam SD Maid.
* `Zaktualizuj Porter, aby używać go z SD Maid` lub `Ta wersja SD Maid jest zbyt stara dla Porter`: obie aplikacje nie mają zgodnych wersji. Zaktualizuj tę, którą wskazuje karta.

#### Shizuku nigdy się nie łączy
{: #shizuku-never-connects }

Karta pokazuje `Dostęp za pomocą ADB nie powiódł się` i `Shizuku działa, ale nigdy nie połączyło SD Maid ze swoją usługą pomocniczą.` Starsze wersje pokazywały `Waiting for link with Shizuku` („Oczekiwanie na połączenie z Shizuku”) lub `Shizuku link failed` („Połączenie z Shizuku nie powiodło się”).

Otwórz aplikację Shizuku. Jeśli pokazuje, że działa, a SD Maid jest na liście autoryzowanych aplikacji, Shizuku jest skonfigurowane prawidłowo i ponowna konfiguracja nie pomoże. Zawodzi usługa pomocnicza Shizuku: kończy działanie podczas uruchamiania, zanim zdąży odpowiedzieć, a SD Maid poddaje się po około 15 sekundach. `Ponów` uruchamia to samo sprawdzenie i kończy się tak samo.

To błąd Shizuku ([RikkaApps/Shizuku#1198](https://github.com/RikkaApps/Shizuku/issues/1198)), a nie SD Maid, i psuje on działanie każdej aplikacji korzystającej z usługi pomocniczej Shizuku. Dotyczy urządzeń z MediaTekiem oraz urządzeń z HyperOS lub MIUI.

[Fork thedjchi](https://github.com/thedjchi/Shizuku) według zgłoszeń to naprawia. Aby się przełączyć:

* Najpierw odinstaluj oryginał, nie mogą istnieć obok siebie
* Usuń jego parowanie z debugowania bezprzewodowego, jeśli go używałeś
* Zainstaluj fork z jego [wydań](https://github.com/thedjchi/Shizuku/releases) i postępuj zgodnie z jego [instrukcją konfiguracji](https://github.com/thedjchi/Shizuku/wiki/setup)
* Ponownie przyznaj uprawnienie Shizuku dla SD Maid i wszystkiego innego, co z niego korzystało
