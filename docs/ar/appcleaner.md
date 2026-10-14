---
title: "منظف التطبيقات"
lang: ar
translation_key: appcleaner
permalink: /ar/appcleaner
source_hash: 505b031f74bf71a2
---
# منظف التطبيقات (App Cleaner)
{: #app-cleaner }

أداة منظف التطبيقات (AppCleaner) أداة تدرك طبيعة التطبيقات وتعثر على الملفات "القابلة للاستغناء عنها". يُعدّ الملف قابلًا للاستغناء عنه إذا أمكن حذفه دون أثر سلبي، وكان إما غير ضروري، أو سيُعاد إنشاؤه عند الحاجة. معظم أنواع الملفات الموجودة في "ذاكرة التخزين المؤقت" (cache) تندرج تحت هذا التعريف.

## الإعدادات
{: #settings }

### تضمين تطبيقات النظام
{: #include-system-apps }

يُدرج هذا الإعداد تطبيقات النظام في النتائج. لا يكون مفعّلًا افتراضيًا لأن بعض تطبيقات النظام (بحسب الـ ROM) تتأثر سلبًا عند مسح ذاكرة التخزين المؤقت الخاصة بها. كما أن بعض تطبيقات النظام لا تحتوي على زر "محو ذاكرة التخزين المؤقت"، مما قد يجعل SD Maid يتعطل أثناء مسح ذاكرة التخزين المؤقت عبر خدمة إمكانية الوصول.

### ضم مستعملين آخرين
{: #include-other-users }

يتطلب صلاحية الجذر (root). عند تفعيله، سيبحث SD Maid في مجلدات المستخدمين الآخرين بالإضافة إلى المستخدم الذي يشغّل SD Maid حاليًا.

### تضمين تطبيقات التي تعمل
{: #include-running-apps }

يحدد ما إذا كان سيتم تضمين التطبيقات قيد التشغيل أم لا.

### الحجم الأدنى
{: #minimum-size }

يحدد الحجم الذي يجب أن تبلغه ذاكرة التخزين المؤقت لتطبيق ما حتى يظهر في القائمة. تحديد حجم أدنى حل وسط جيد بين الفائدة والسرعة. مسح ذاكرة التخزين المؤقت لتطبيق لا يتجاوز حجمها بضعة كيلوبايتات لا يجلب عادةً أي فائدة. ينطبق هذا على إجمالي ذاكرة التخزين المؤقت التي عُثر عليها لتطبيق ما (مع مراعاة جميع المرشحات المفعّلة)

### الحد الأدنى للسن
{: #minimum-age }

يمكن استخدامه لحذف ملفات ذاكرة التخزين المؤقت القديمة فقط. يُطبَّق على كل ملف أو مجلد في النتائج.

### قم بتضمين البيانات التي يتعذر الوصول إليها
{: #include-inaccessible-caches }

بحسب الأذونات المتاحة لـ SD Maid، قد لا يكون الوصول المباشر إلى بعض الملفات ممكنًا. يشير هذا عادةً إلى ذاكرة التخزين المؤقت الافتراضية الخاصة بالتطبيق (مثل `/data/data/eu.darken.sdmse/cache`). إذا كان هذا الإعداد مفعّلًا، سيحاول SD Maid استخدام خدمة إمكانية الوصول للانتقال إلى إعدادات النظام والضغط على زر `محو ذاكرة التخزين المؤقت` الخاص بالتطبيق المستهدف.

### المرشحات
{: #filter }

يمكنك تفعيل أو تعطيل مرشحات مختلفة للتأثير في النتائج التي ستظهرها أداة منظف التطبيقات.

#### المرشحات العامة
{: #common-filters }

تستهدف المرشحات العامة أنواعًا مختلفة من الملفات في جميع التطبيقات.

#### المرشحات المحددة
{: #specific-filters }

تستهدف المرشحات المحددة تطبيقات بعينها، مثل WhatsApp أو Telegram.

## استكشاف الأخطاء وإصلاحها
{: #troubleshooting }
### `com.miui.securitycenter`
{: #commiuisecuritycenter }
#### إذن `GET_USAGE_STATS` مفقود
{: #missing-get_usage_stats-permission }

يستخدم MIUI وHyperOS تطبيقًا إضافيًا يُسمّى "Security" أو "Security Center" (الأمان أو مركز الأمان) (اسم الحزمة: `com.miui.securitycenter`) لعرض تفاصيل النظام الخاصة بتطبيق ما. التسلسل المعتاد للإجراءات لمسح ذاكرة التخزين المؤقت لتطبيق هو: اضغط على `Clear data` (مسح البيانات)، ثم اختر `Clear cache` (مسح ذاكرة التخزين المؤقت)، ثم أكّد الإجراء.

إذا كان زر 'Clear data' (مسح البيانات) رماديًا لجميع التطبيقات ولا يمكن الضغط عليه، فهذا يعني أن تطبيق "Security Center" (مركز الأمان) يفتقد الإذن `GET_USAGE_STATS`. عادةً ما يُمنح هذا الإذن افتراضيًا. ليس واضحًا كيف يحدث ذلك، لكن الحل هو منح هذا الإذن مجددًا.

انتقل إلى: `System Settings` (إعدادات النظام) > `Security and privacy` (الأمان والخصوصية) > `More security and privacy` (المزيد من إعدادات الأمان والخصوصية) > `Special permissions` (أذونات خاصة) > `Usage access` (الوصول إلى بيانات الاستخدام) > ابحث عن تطبيق `Security` (الأمان) > امنح الإذن

بدلًا من ذلك، إذا كان لديك وصول ADB أو أعلى، فشغّل الأمر `pm grant com.miui.securitycenter android.permission.PACKAGE_USAGE_STATS`

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
