---
title: "Harmonogram"
lang: pl
translation_key: scheduler
permalink: /pl/scheduler
source_hash: 74e1452091eaf142
---
# Harmonogram (Scheduler)
{: #scheduler }

Funkcja **Harmonogram** (Scheduler) w SD Maid 2 pozwala automatyzować zadania konserwacyjne w określonych odstępach czasu, dzięki czemu twoje urządzenie pozostaje zoptymalizowane bez ręcznego wysiłku. Obsługuje kilka narzędzi, takich jak **Sprzątanie aplikacji** (AppCleaner), **Wyszukiwanie pozostałości** (CorpseFinder) i **Sprzątanie systemu** (SystemCleaner), które można skonfigurować tak, aby uruchamiały się regularnie lub o określonych porach.

<img src="/assets/images/scheduler/overview-1.webp" width="150">
<img src="/assets/images/scheduler/overview-2.webp" width="150">
<img src="/assets/images/scheduler/overview-3.webp" width="150">
<img src="/assets/images/scheduler/overview-4.webp" width="150">
<img src="/assets/images/scheduler/overview-5.webp" width="150">
<img src="/assets/images/scheduler/overview-6.webp" width="150">


## Jak skonfigurować harmonogram
{: #how-to-set-up-a-schedule }

1. Przejdź do **Ustawień** i wybierz **Harmonogram**.
2. Dotknij przycisku **+**, aby utworzyć nowy harmonogram lub zarządzać istniejącymi.
3. Wybierz, które narzędzia (np. **Sprzątanie aplikacji**, **Wyszukiwanie pozostałości**, **Sprzątanie systemu**) mają być uruchamiane według harmonogramu.
4. Ustaw **częstotliwość powtarzania** (np. co 3 dni) i **przybliżony czas** wykonania.
5. Włącz harmonogram, przełączając przełącznik.

## Edytowanie lub usuwanie harmonogramów
{: #editing-or-deleting-schedules }

1. Przejdź do ustawień **Harmonogramu**, aby zarządzać harmonogramami.
2. Możesz modyfikować istniejące harmonogramy, zmieniać odstępy czasu lub usuwać je, jeśli nie są już potrzebne.

Gdy harmonogram jest aktywny, SD Maid wykonuje wybrane zadania w określonych porach.

## Dodatkowe opcje
{: #additional-options }

W głównym menu ustawień, w `Ustawienia > Harmonogram`, znajduje się kilka dodatkowych opcji.

- **Pomiń przy niskim stanie baterii**: jeśli urządzenie jest w trybie oszczędzania energii, zaplanowane zadania zostaną pominięte.
- **Pomiń, jeśli nie trwa ładowanie**: zaplanowane zadania będą uruchamiane tylko podczas ładowania urządzenia, co zapobiega rozładowywaniu baterii.
- **Polecenia po harmonogramie**: możesz ustawić polecenia powłoki, które zostaną wykonane po zakończeniu zaplanowanych zadań.
- **Usługa ułatwień dostępu**: domyślnie harmonogram pomija funkcje oparte na usłudze ułatwień dostępu, ponieważ nie działają one, gdy urządzenie jest zablokowane. Jeśli to włączysz, nie wprowadzając żadnych innych zmian na urządzeniu, harmonogram nie zadziała. Dlatego, jeśli nie wiesz, co robisz, pozostaw to wyłączone. Przydaje się to tylko w nietypowych przypadkach, takich jak stale włączone urządzenie z Android TV.

## Rozwiązywanie problemów
{: #troubleshooting }

### Harmonogram się nie wykonuje 
{: #scheduler-is-not-executing }

Rozróżnij sytuację, w której harmonogram w ogóle się nie uruchamia, od takiej, w której wykonane operacje nie dają oczekiwanych wyników. Historię wykonań możesz sprawdzić w [sekcji Historia](/pl/stats) SD Maid. Jeśli harmonogram się wykonał, ale zakończył niepowodzeniem, powinien być widoczny błąd.

Jeśli harmonogram w ogóle się nie uruchomił, możliwe, że system uniemożliwił uruchomienie SD Maid, aby oszczędzać baterię. Po aktywowaniu pierwszego harmonogramu powinien był pojawić się komunikat zatytułowany „Porada” z czynnością „Napraw”, proszący o wyłączenie optymalizacji baterii. Jeśli przypadkowo go odrzuciłeś, musisz ręcznie przejść do ustawień systemowych SD Maid i sprawdzić, czy system go „optymalizuje”.

### Usługa ułatwień dostępu
{: #accessibility-service }
Usuwanie oparte na ułatwieniach dostępu, np. usuwanie „niedostępnych elementów” przez Sprzątanie aplikacji, nie działa przez harmonogram. Gdy harmonogram się wykonuje, urządzenie jest zwykle zablokowane: tak jak ty nie możesz nic kliknąć na wyłączonym lub zablokowanym ekranie, tak samo nie może SD Maid.

Domyślnie harmonogram nie wykonuje operacji opartych na ułatwieniach dostępu. Możesz jednak włączyć opcję „Usługa ułatwień dostępu” w ustawieniach harmonogramu, jeśli wiesz, co robisz.

Obejścia: zrootuj urządzenie (aplikacje użytkownika i systemowe) lub zainstaluj Shizuku (tylko aplikacje użytkownika). Każda z tych modyfikacji pozwala SD Maid wykonywać podobne operacje bez korzystania z funkcji usługi ułatwień dostępu.
