---
title: "Sprzątanie aplikacji"
lang: pl
translation_key: appcleaner
permalink: /pl/appcleaner
source_hash: 505b031f74bf71a2
---
# Sprzątanie aplikacji (AppCleaner)
{: #app-cleaner }

Sprzątanie aplikacji (AppCleaner) to narzędzie, które zna aplikacje i wyszukuje „zbędne” pliki. Plik uznaje się za zbędny, jeśli można go usunąć bez negatywnych skutków i jest on albo niepotrzebny, albo zostanie w razie potrzeby utworzony ponownie. Większość rodzajów plików w „pamięci podręcznej” (cache) mieści się w tej definicji.

## Ustawienia
{: #settings }

### Uwzględnij aplikacje systemowe
{: #include-system-apps }

To ustawienie uwzględnia w wynikach aplikacje systemowe. Domyślnie nie jest włączone, ponieważ niektóre aplikacje systemowe (zależnie od ROM-u) źle reagują na wyczyszczenie ich pamięci podręcznej. Niektóre aplikacje systemowe nie mają też przycisku „Wyczyść pamięć podręczną”, przez co SD Maid może utknąć podczas czyszczenia pamięci podręcznej za pomocą usługi ułatwień dostępu.

### Uwzględnij innych użytkowników
{: #include-other-users }

Wymaga roota. Po włączeniu SD Maid przeszukuje foldery innych użytkowników oprócz użytkownika, na którym aktualnie działa SD Maid.

### Uwzględnij uruchomione aplikacje
{: #include-running-apps }

Określa, czy uwzględniać uruchomione aplikacje.

### Minimalny rozmiar
{: #minimum-size }

Jak duża musi być pamięć podręczna aplikacji, aby pojawiła się na liście. Ustawienie minimalnego rozmiaru to dobry kompromis między użytecznością a szybkością. Czyszczenie pamięci podręcznej aplikacji, która ma tylko kilka KB, zwykle nie przynosi żadnych korzyści. Dotyczy to łącznej ilości pamięci podręcznej znalezionej dla aplikacji (z uwzględnieniem wszystkich aktywnych filtrów).

### Minimalny wiek
{: #minimum-age }

Pozwala usuwać tylko stare pliki pamięci podręcznej. Jest stosowany do każdego pliku lub folderu w wynikach.

### Uwzględnij niedostępne pamięci podręczne
{: #include-inaccessible-caches }

W zależności od uprawnień dostępnych dla SD Maid niektóre pliki mogą nie być bezpośrednio dostępne. Zwykle chodzi o prywatną domyślną pamięć podręczną aplikacji (np. `/data/data/eu.darken.sdmse/cache`). Jeśli to ustawienie jest włączone, SD Maid spróbuje za pomocą usługi ułatwień dostępu przejść do ustawień systemowych i nacisnąć przycisk `Wyczyść pamięć podręczną` dla danej aplikacji.

### Filtr
{: #filter }

Możesz włączać i wyłączać różne filtry, aby wpływać na to, jakie wyniki znajdzie Sprzątanie aplikacji.

#### Filtry ogólne
{: #common-filters }

Filtry ogólne obejmują różne typy plików we wszystkich aplikacjach.

#### Filtry szczegółowe
{: #specific-filters }

Filtry szczegółowe dotyczą konkretnych aplikacji, np. WhatsApp lub Telegram.

## Rozwiązywanie problemów
{: #troubleshooting }
### `com.miui.securitycenter`
{: #commiuisecuritycenter }
#### Brak uprawnienia `GET_USAGE_STATS`
{: #missing-get_usage_stats-permission }

MIUI i HyperOS używają dodatkowej aplikacji o nazwie „Security” lub „Security Center” („Zabezpieczenia”, nazwa pakietu: `com.miui.securitycenter`) do wyświetlania szczegółów systemowych aplikacji. Normalny przebieg czyszczenia pamięci podręcznej aplikacji wygląda tak: kliknij `Clear data` („Wyczyść dane”), wybierz `Clear cache` („Wyczyść pamięć podręczną”), potwierdź czynność.

Jeśli przycisk „Clear data” („Wyczyść dane”) jest wyszarzony dla wszystkich aplikacji i nie da się go kliknąć, to aplikacji „Security Center” brakuje uprawnienia `GET_USAGE_STATS`. Normalnie powinno być ono przyznane domyślnie. Nie wiadomo, jak do tego dochodzi, ale rozwiązaniem jest ponowne przyznanie tego uprawnienia.

Przejdź do: `System Settings` > `Security and privacy` > `More security and privacy` > `Special permissions` > `Usage access` > znajdź aplikację `Security` > przyznaj uprawnienie

Alternatywnie, jeśli masz dostęp ADB lub wyższy, uruchom polecenie `pm grant com.miui.securitycenter android.permission.PACKAGE_USAGE_STATS`

<a href="/assets/images/appcleaner/miui-usage-stats-1.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-1.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-2.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-2.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-3.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-3.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-4.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-4.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-5.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-5.webp" width="100px">
</a>
<a href="/assets/images/appcleaner/miui-usage-stats-6.webp">
  <img src="/assets/images/appcleaner/miui-usage-stats-6.webp" width="100px">
</a>
