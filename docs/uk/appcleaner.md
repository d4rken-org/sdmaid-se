---
title: "Очищувач додатків"
lang: uk
translation_key: appcleaner
permalink: /uk/appcleaner
source_hash: 505b031f74bf71a2
---
# Очищувач додатків (AppCleaner)
{: #app-cleaner }

Очищувач додатків (AppCleaner) є інструментом, що враховує додатки й знаходить «непотрібні» файли. Файл вважається непотрібним, якщо його можна видалити без шкідливих наслідків і він або не потрібен, або буде створений заново за потреби. Більшість типів файлів у «кеші» підпадає під це визначення.

## Налаштування
{: #settings }

### Включити системні додатки
{: #include-system-apps }

Цей параметр включає системні додатки до результатів. За замовчуванням він вимкнений, оскільки деякі системні додатки (залежно від ROM) погано реагують на очищення свого кешу. Також у деяких системних додатків немає кнопки «Очистити кеш», через що SD Maid може зависнути під час очищення кешу через сервіс спеціальних можливостей.

### Включити інших користувачів
{: #include-other-users }

Потребує root. Якщо параметр увімкнено, SD Maid шукатиме в папках інших користувачів на додачу до користувача, від імені якого зараз працює SD Maid.

### Увімкнути запущені додатки
{: #include-running-apps }

Чи включати запущені додатки.

### Мінімальний розмір
{: #minimum-size }

Наскільки великим має бути кеш додатка, щоб потрапити до списку. Задання мінімального розміру є добрим компромісом між користю та швидкістю. Очищення кешу додатка розміром лише кілька КБ зазвичай не дає жодної користі. Це стосується загального обсягу кешу, знайденого для додатка (з урахуванням усіх активних фільтрів)

### Мінімальний вік
{: #minimum-age }

Дає змогу видаляти лише старі файли кешу. Застосовується до кожного файлу чи папки в результатах.

### Включити недоступні кеші
{: #include-inaccessible-caches }

Залежно від дозволів, доступних SD Maid, деякі файли можуть бути недоступні напряму. Зазвичай ідеться про приватний стандартний кеш додатків (наприклад, `/data/data/eu.darken.sdmse/cache`). Якщо цей параметр увімкнено, SD Maid спробує за допомогою сервісу спеціальних можливостей перейти до системних налаштувань і натиснути кнопку `Clear cache` («Очистити кеш») для цільового додатка.

### Фільтр
{: #filter }

Ви можете вмикати та вимикати різні фільтри, щоб впливати на те, які результати знайде Очищувач додатків.

#### Загальні фільтри
{: #common-filters }

Загальні фільтри націлені на різні типи файлів у всіх додатках.

#### Спеціальні фільтри
{: #specific-filters }

Спеціальні фільтри націлені на конкретні додатки, наприклад WhatsApp або Telegram.

## Усунення проблем
{: #troubleshooting }
### `com.miui.securitycenter`
{: #commiuisecuritycenter }
#### Відсутній дозвіл `GET_USAGE_STATS`
{: #missing-get_usage_stats-permission }

MIUI та HyperOS використовують додатковий додаток під назвою «Security» або «Security Center» («Безпека», назва пакета: `com.miui.securitycenter`), щоб показувати системні відомості про додаток. Звичайна послідовність дій для очищення кешу додатка така: натиснути `Clear data` («Очистити дані»), вибрати `Clear cache` («Очистити кеш»), підтвердити дію.

Якщо кнопка «Clear data» («Очистити дані») неактивна для всіх додатків і її неможливо натиснути, то в додатка «Security Center» відсутній дозвіл `GET_USAGE_STATS`. Зазвичай його надано за замовчуванням. Незрозуміло, як таке стається, але рішення полягає в тому, щоб надати цей дозвіл повторно.

Перейдіть до: `System Settings` > `Security and privacy` > `More security and privacy` > `Special permissions` > `Usage access` > знайдіть додаток `Security` > надайте дозвіл

Або, якщо у вас є доступ ADB чи вищий, виконайте команду `pm grant com.miui.securitycenter android.permission.PACKAGE_USAGE_STATS`

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
