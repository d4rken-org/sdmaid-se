---
title: "Приложения"
lang: ru
translation_key: appcleaner
permalink: /ru/appcleaner
source_hash: 505b031f74bf71a2
---
# Приложения (AppCleaner)
{: #app-cleaner }

Инструмент «Приложения» (AppCleaner) учитывает установленные приложения и находит «расходуемые» файлы. Файл считается расходуемым, если его можно удалить без негативных последствий и он либо не нужен, либо будет создан заново при необходимости. Под это определение подпадает большинство типов файлов в «кэше».

## Настройки
{: #settings }

### Включать системные приложения
{: #include-system-apps }

Эта настройка добавляет системные приложения в результаты. По умолчанию она выключена, так как некоторые системные приложения (в зависимости от прошивки) плохо реагируют на очистку своего кэша. Кроме того, у некоторых системных приложений нет кнопки «Очистить кэш», из-за чего SD Maid может зависнуть при очистке кэша через службу специальных возможностей.

### Включать других пользователей
{: #include-other-users }

Требуется root. Если включено, SD Maid помимо пользователя, от имени которого сейчас работает SD Maid, будет искать и в папках других пользователей.

### Включать работающие приложения
{: #include-running-apps }

Включать ли работающие приложения.

### Минимальный размер
{: #minimum-size }

Насколько большим должен быть кэш приложения, чтобы оно попало в список. Указание минимального размера является хорошим компромиссом между пользой и скоростью. Очистка кэша приложения размером всего в несколько КБ обычно не даёт никакой пользы. Это относится к общему объёму кэша, найденного для приложения (с учётом всех активных фильтров).

### Минимальный возраст
{: #minimum-age }

Позволяет удалять только старые файлы кэша. Применяется к каждому файлу или папке в результатах.

### Включать недоступный кэш
{: #include-inaccessible-caches }

В зависимости от разрешений, доступных SD Maid, некоторые файлы могут быть недоступны напрямую. Обычно речь идёт о закрытом стандартном кэше приложения (например, `/data/data/eu.darken.sdmse/cache`). Если эта настройка включена, SD Maid попытается с помощью службы специальных возможностей перейти в системные настройки и нажать для нужного приложения кнопку `Clear cache` («Очистить кэш»).

### Фильтр
{: #filter }

Вы можете включать и выключать различные фильтры, чтобы влиять на то, какие результаты найдёт инструмент «Приложения».

#### Общие фильтры
{: #common-filters }

Общие фильтры нацелены на различные типы файлов во всех приложениях.

#### Особые фильтры
{: #specific-filters }

Особые фильтры нацелены на конкретные приложения, например WhatsApp или Telegram.

## Устранение неполадок
{: #troubleshooting }
### `com.miui.securitycenter`
{: #commiuisecuritycenter }
#### Отсутствует разрешение `GET_USAGE_STATS`
{: #missing-get_usage_stats-permission }

MIUI и HyperOS используют дополнительное приложение «Security» или «Security Center» («Безопасность», имя пакета: `com.miui.securitycenter`), чтобы показывать системные сведения о приложении. Обычный порядок действий для очистки кэша приложения: нажать `Clear data` («Очистить данные»), выбрать `Clear cache` («Очистить кэш»), подтвердить действие.

Если кнопка «Clear data» («Очистить данные») неактивна (серая) у всех приложений и на неё нельзя нажать, значит, у приложения «Security Center» нет разрешения `GET_USAGE_STATS`. Обычно оно должно быть выдано по умолчанию. Неясно, как такое происходит, но решение состоит в том, чтобы выдать это разрешение заново.

Перейдите: `System Settings` > `Security and privacy` > `More security and privacy` > `Special permissions` > `Usage access` > найдите приложение `Security` > выдайте разрешение

Либо, если у вас есть доступ через ADB или выше, выполните команду `pm grant com.miui.securitycenter android.permission.PACKAGE_USAGE_STATS`

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
