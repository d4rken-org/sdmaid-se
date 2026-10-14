---
title: "إعداد SD Maid SE"
lang: ar
translation_key: setup
permalink: /ar/setup
source_hash: a14003a95dd2336d
---
# إعداد SD Maid SE
{: #setting-up-sd-maid-se }
معلومات حول خطوات الإعداد المختلفة في SD Maid SE.

يتكيّف SD Maid مع مستوى الوصول المتاح. مع أن جميع الأذونات اختيارية، فإن معظم الميزات لن تعمل إذا لم تمنح أي أذونات. يعمل SD Maid بأفضل أداء عند إكمال جميع عناصر الإعداد.

سيحاول SD Maid إكمال بعض خطوات الإعداد تلقائيًا إذا كانت [صلاحية الجذر](#root-access) أو [وصول ADB](#shizuku) متاحة.

بعض الأذونات متداخلة أو يشمل بعضها بعضًا، فمثلًا يمكنك منح SD Maid صلاحية الجذر ووصول ADB معًا. سيحاول SD Maid دائمًا استخدام أفضل طريقة وصول ممكنة لأي إجراء، وإذا فشلت، يعيد المحاولة بثاني أفضل طريقة ثم بالثالثة وهكذا. منح SD Maid أذونات متداخلة يحسّن الموثوقية.

> **تلميح:** تتضمن بعض خطوات الإعداد أدناه تشغيل أوامر ADB. إذا لم يكن ADB مُعدًّا على حاسوبك، يمكنك استخدام [Web ADB Helper](https://d4rken.github.io/web-adb/) لتشغيلها مباشرةً من متصفحك.

## إدارة التخزين
{: #manage-storage }

إذن "إدارة التخزين" إذن واسع النطاق للوصول إلى التخزين.

* على Android 12 والإصدارات الأقدم، يمنح الوصول إلى كل وحدات التخزين العامة.
* على Android 13 والإصدارات الأحدث، يظل يمنح الوصول إلى كل وحدات التخزين العامة، لكن ليس إلى المجلدات الفرعية تحت `Android/data`.

## بيانات الاستخدام
{: #usage-statistics }

تمنح أذونات بيانات الاستخدام SD Maid إمكانية الوصول إلى أحجام البيانات وذاكرة التخزين المؤقت، ومعرفة ما إذا كان التطبيق قيد التشغيل أو متى استُخدم آخر مرة.

## إطار الوصول إلى التخزين
{: #storage-access-framework }
يستخدم SD Maid [إطار الوصول إلى التخزين في Android ‏(SAF)](http://developer.android.com/guide/topics/providers/document-provider.html) للحصول على وصول إلى مناطق تخزين إضافية. سيطلب منك SD Maid منح الوصول إلى مسارات محددة لا يمكن الوصول إليها بمجرد امتلاك إذن "إدارة التخزين" (Just Android Things™️).

* اضغط على زر `السماح بالوصول` 
* تُفتح نافذة جديدة
* إذا كان المسار الصحيح محددًا بالفعل فذلك جيد، وإلا فعليك الانتقال إلى المسار الذي يريده SD Maid.
* ثم اضغط على زر `Use this folder` (استخدام هذا المجلد)، وسيُفتح عنصر الإعداد الخاص بهذا المسار ويتلوّن بالأخضر.

كرر هذه العملية حتى تصبح جميع المسارات خضراء.

<a href="/assets/images/setup/saf-1.webp"><img src="/assets/images/setup/saf-1.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-2.webp"><img src="/assets/images/setup/saf-2.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-3.webp"><img src="/assets/images/setup/saf-3.webp" width="100" alt=""></a>
<a href="/assets/images/setup/saf-4.webp"><img src="/assets/images/setup/saf-4.webp" width="100" alt=""></a>

### SAF: استكشاف الأخطاء وإصلاحها
{: #saf-troubleshooting }
لتلك الحالات التي لا تسير فيها الأمور كما هو مخطط.

#### SAF: الوصول إلى `Android/data|obb`
{: #saf-androiddataobb-access }

منح الوصول إلى `Android/data|obb` غير ممكن مع الإصدارات الأحدث من تطبيق `Files` (الملفات) التابع للنظام (`com.google.android.documentsui`) على Android 11 وAndroid 12. إذا كان تطبيق `Files` المثبت يستهدف Android 14 (مستوى API ‏34) أو أحدث، فسيُخفي SD Maid طلب الوصول عبر SAF لهذه المسارات (كما هو الحال على Android 13 والإصدارات الأحدث).

يحدّث Google Play تطبيق `Files` عبر خدمة `Google Play system update` (تحديث نظام Google Play). يسمح تطبيق "Files" الأصلي (المبني لمستوى API ‏31، مثل `aml_doc_310851020 (310851020)`) الذي جاء مع نظام Android 12 لـ SD Maid بفتح نافذة اختيار مسار SAF على `Android/Data`، بينما يمنع أحدث إصدار من تطبيق "Files" (المبني لمستوى API ‏34 أو أحدث، مثل `14-10492947 (340916000)`) ذلك.

**الحل البديل:**

يمكنك إلغاء تثبيت تحديث تطبيق `Files` ثم إعداد SD Maid. تحديث تطبيق `Files` مجددًا لن يُفسد الأذونات الممنوحة بالفعل.

* افتح إعدادات الجهاز
* انتقل إلى "التطبيقات والإشعارات" أو "Application Manager" (مدير التطبيقات)
* ابحث عن تطبيق النظام `Files`
* اضغط على قائمة النقاط الثلاث، وتكون عادةً في الزاوية العلوية اليمنى
* اختر "إلغاء تثبيت التحديثات"

أو

_يمكنك أيضًا تشغيل هذا الأمر باستخدام [Web ADB Helper](https://d4rken.github.io/web-adb/)._

`adb uninstall com.google.android.documentsui`

#### SAF: الخطأ OPEN_DOCUMENT_TREE ActivityNotFoundException
{: #saf-open_document_tree-activitynotfoundexception }

يتطلب منح الوصول إلى المسارات عبر إطار الوصول إلى التخزين تطبيقًا يتيح للمستخدم تأكيد المسارات التي سيُمنح الوصول إليها. هذا التطبيق هو تطبيق "Documents" (المستندات). وهو تطبيق نظام مدمج في كل جهاز Android منذ Android 5.0  (`com.android.documentsui` أو `com.google.android.documentsui`). إذا ظهر لك هذا الخطأ، فإما أن أحدهم حذف هذا التطبيق أو عطّله. في معظم الحالات، شغّل أحدهم أداة "إزالة تطبيقات مدمجة" (de-bloating) مفرطة الحماس عطّلته. يمكنك إعادة تفعيله عبر ADB:

_يمكنك أيضًا تشغيل هذه الأوامر باستخدام [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```
adb shell pm enable com.android.documentsui
or
adb shell pm enable com.google.android.documentsui
```

#### SAF: نافذة فارغة
{: #saf-empty-window }
إذا كانت النافذة المستخدمة لمنح الوصول إلى المسارات فارغة، فربما تكون حزمة موفّر التخزين الخارجي (`com.android.externalstorage`) معطّلة (على الأرجح بسبب أداة "إزالة تطبيقات مدمجة" مفرطة الحماس). يمكنك إعادة تفعيلها عبر ADB:

_يمكنك أيضًا تشغيل هذا الأمر باستخدام [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```
adb shell pm enable com.android.externalstorage
```

تشغيل `Google Play system update` (تحديث نظام Google Play) مجددًا سيستعيد التحديث. يمكنك أيضًا تنزيل إصدار محدّث يدويًا من [APKMirror.com](https://www.apkmirror.com/apk/google-inc/files-6/).

## الإشعارات
{: #notifications }

دون هذا الإذن لن يتمكن SD Maid من نشر الإشعارات. على بعض أنظمة ROM قد يؤثر ذلك سلبًا في الموثوقية عند التنفيذ في الخلفية.

## مخزون التطبيق
{: #app-inventory }

يتطلب SD Maid الإذن `QUERY_ALL_PACKAGES` للتحقق من التطبيقات المثبتة على جهازك (أي ["رؤية قائمة التطبيقات المثبتة"](https://support.google.com/googleplay/android-developer/answer/10158779?hl=en)). راجع أيضًا ["إذن بيانات الاستخدام"](/ar/setup#usage-statistics)

الإذن `QUERY_ALL_PACKAGES` إلزامي لـ:

* [العثور على الملفات المتبقية](/ar/corpsefinder) (CorpseFinder): لمعرفة الملفات التي يمكن حذفها، يحتاج SD Maid إلى معرفة ما إذا كانت لديك تطبيقات مثبتة قد تحتاج إليها.
* [منظف التطبيقات](/ar/appcleaner) (AppCleaner): تحديد ذاكرة التخزين المؤقت لكل تطبيق
* [إدارة التطبيق](/ar/appcontrol) (AppControl): القدرة على عرض جميع التطبيقات المثبتة
* [محلل التخزين](/ar/storageanalyzer) (StorageAnalyzer): نسب التخزين المستخدم إلى التطبيقات المثبتة

يوجد هذا الإذن منذ [Android 11](https://developer.android.com/training/package-visibility) ويُمنح عادةً افتراضيًا. منذ Android 14 توجد بعض الأجهزة (مثل [#1015](https://github.com/d4rken-org/sdmaid-se/issues/1015) و[#1354](https://github.com/d4rken-org/sdmaid-se/issues/1354)) التي لا يُمنح فيها هذا الإذن افتراضيًا أو يمكن سحبه.

[<img src="/assets/images/setup/app-inventory.webp" width="100">](/assets/images/setup/app-inventory.webp)

### قائمة تطبيقات غير صالحة
{: #invalid-app-list }

على بعض الأجهزة يكون إذن "قائمة التطبيقات" (`QUERY_ALL_PACKAGES` / `GET_INSTALLED_APPS`) ممنوحًا، لكن النظام يظل يُرجع **قائمة غير مكتملة بالتطبيقات المثبتة**، مثل قائمة تفتقد حزم النظام الأساسية مثل `android`. عندما يكتشف SD Maid ذلك، فإنه يعرض تحذير **"مخزون التطبيق غير صالح"** وملاحظة **"قيود الجهاز المحتملة"** في بطاقة الإعداد بدلًا من المتابعة.

يتوقف SD Maid هنا عن قصد: إذا لم يتمكن من رؤية القائمة الكاملة للتطبيقات المثبتة، فلن يستطيع أن يحدد بشكل موثوق الملفات التي تخص تطبيقًا لا يزال مثبتًا. المتابعة رغم ذلك قد تعني حذف بيانات تخص تطبيقًا لم يتمكن SD Maid من رؤيته ببساطة، لذا تبقى الميزات المتعلقة بالتطبيقات معطّلة حتى تتوفر قائمة كاملة.

**ما يمكنك تجربته:**

* افتح إعدادات النظام وتأكد من أن SD Maid مسموح له بإذن "قائمة التطبيقات". لدى بعض الشركات المصنّعة يحمل هذا المفتاح اسمًا مختلفًا (مثل "الاستعلام عن جميع الحزم") أو يوجد داخل شاشة منفصلة مثل "الخصوصية" أو "مدير الأذونات".
* تحقق مما إذا كانت ميزة مدمجة لتوفير البطارية أو حماية الخصوصية أو "إخفاء التطبيقات" تُرشّح قائمة التطبيقات التي يمكن لـ SD Maid رؤيتها، واستثنِ SD Maid منها.
* أعد تشغيل الجهاز وافتح SD Maid مجددًا لإعادة تقييم حالة الإذن.

إذا كان إذن "قائمة التطبيقات" مسموحًا بالفعل وظلت القائمة تُعتبر غير صالحة، فهذا عادةً **قيد في الجهاز** وليس شيئًا يمكنك إصلاحه: بعض الأنظمة (مثل HarmonyOS، أو إصدارات Android المعدّلة بشدة) لا تسلّم أبدًا قائمة تطبيقات كاملة لتطبيقات الجهات الخارجية. راجع [#2441](https://github.com/d4rken-org/sdmaid-se/issues/2441) كمثال.

في هذه الحالة تبقى الأدوات المتعلقة بالتطبيقات ([العثور على الملفات المتبقية](/ar/corpsefinder) و[منظف التطبيقات](/ar/appcleaner) و[إدارة التطبيق](/ar/appcontrol)) معطّلة لحماية بياناتك، لكن [محلل التخزين](/ar/storageanalyzer) يظل يعمل: فهو يُظهر مقدار المساحة التي يستخدمها التخزين لديك، لكن بتفاصيل أقل، إذ تُعرض أحجام المجلدات للقراءة فقط ولا يمكن نسبها إلى تطبيقات بعينها.

## خدمة إمكانية الوصول
{: #accessibility-service }
تتيح خدمة إمكانية الوصول لـ SD Maid التفاعل مع جهازك كما يفعل الإنسان، أي رؤية الشاشة والضغط على الأزرار. يمكن لـ SD Maid استخدامها لأتمتة الإجراءات المملة مثل الضغط على "محو ذاكرة التخزين المؤقت" في مئات التطبيقات. خدمة إمكانية الوصول خدمة لا يستطيع التطبيق نفسه تشغيلها، بل يجب أن يشغّلها النظام، لأن هذا النوع من الخدمات يمنح التطبيق وصولًا إضافيًا إلى جهازك.

### ACS: استكشاف الأخطاء وإصلاحها
{: #acs-troubleshooting }

قد تتوقف الخدمة عن العمل وتتطلب منك تفعيلها مجددًا في ظروف مختلفة:
* عندما تحدّث التطبيق.
* عندما يتعطّل التطبيق فجأة. إذا حدث ذلك فعليك الإبلاغ عنه كخطأ. إذا حدث ذلك كثيرًا، فقد لا تتمكن من إعادة تشغيلها بإيقافها وتشغيلها مجددًا حتى يُعاد تشغيل الجهاز.
* عندما يُنهي النظام التطبيق بسبب "تحسينات البطارية" المفرطة في الشدة (راجع أيضًا <https://dontkillmyapp.com/>)
* عندما يوقفها تطبيق الأمان الخاص بالشركة المصنّعة (راجع [الحظر من Phone Manager](#acs-blocked-by-phone-manager-oneplus-oppo-realme))

#### ACS: الخدمة معطّلة
{: #acs-service-is-disabled }

تشتهر بعض أنظمة ROM (مثل MIUI) بإيقاف كل ما لم تُدرجه الشركة المصنّعة للجهاز في القائمة البيضاء بشكل عدواني.
إذا كان يُطلب منك باستمرار إعادة تفعيل خدمة إمكانية الوصول، فقد يكون النظام يعطّلها في كل مرة لا يكون فيها SD Maid قيد الاستخدام الفعلي.

حاول تعطيل تحسينات البطارية، وفي أنظمة MIUI يوجد إعداد إضافي يُسمّى "Autostart" (التشغيل التلقائي)، فعّله لـ SD Maid. لأجهزة Xiaomi/POCO/Redmi:

* انتقل إلى `Security>Manage applications>Auto start in background` (الأمان > إدارة التطبيقات > التشغيل التلقائي في الخلفية) واسمح بذلك لـ SD Maid.
* انتقل إلى `Security>Increase speed>Lock apps` (الأمان > زيادة السرعة > قفل التطبيقات) واختر SD Maid.


يمكنك أيضًا التفكير في منح إذن إضافي [يسمح لـ SD Maid بإعادة تشغيل الخدمة تلقائيًا](#acs-automatic-service-restart).

[<img src="/assets/images/setup/acs-disabled.webp" width="100">](/assets/images/setup/acs-disabled.webp)

#### ACS: الحظر من Phone Manager ‏(OnePlus وOPPO وRealme)
{: #acs-blocked-by-phone-manager-oneplus-oppo-realme }

على OxygenOS وColorOS، يمكن لتطبيق "Phone Manager" (مدير الهاتف) المثبت مسبقًا أن يوقف خدمة إمكانية الوصول من تلقاء نفسه، عادةً خلال ثانية من انتهاء عملية التنظيف. يكون مفتاح إمكانية الوصول في Android متوقفًا فعلًا بعد ذلك، لذا يبدو الأمر كأن SD Maid توقف عن العمل في منتصف التشغيل.

أوقف الميزة من `Phone Manager>Utilities>Block suspicious app activities` (مدير الهاتف > الأدوات > حظر أنشطة التطبيقات المشبوهة).

تحتفظ الشاشة نفسها بقائمة لما حظرته. العناصر المعنونة بـ "Abnormal device control" (تحكم غير طبيعي في الجهاز) والتي تتطابق أوقاتها مع عمليات التنظيف التي شغّلتها تؤكد أن هذا هو السبب. المفتاح "Blocking alerts" (تنبيهات الحظر) المجاور لها لا يتحكم إلا في ما إذا كنت ستتلقى إشعارًا، لذا عند إيقاف التنبيهات يحدث هذا بصمت.

#### ACS: التفعيل عبر ADB
{: #acs-enable-via-adb }

إذا لم يكن في جهازك طريقة لتفعيل خدمات إمكانية الوصول عبر الشاشة (مثل أجهزة التلفاز)، فمن الممكن تفعيل الخدمة باستخدام ADB بتشغيل:

_يمكنك أيضًا تشغيل هذا الأمر باستخدام [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```bash
adb shell settings put secure enabled_accessibility_services eu.darken.sdmse/.automation.core.AutomationService
```

#### ACS: إعادة تشغيل الخدمة تلقائيًا
{: #acs-automatic-service-restart }

_ملاحظة: هذه تقنية متقدمة وموجهة للمستخدمين المتقدمين._

يمكنك منح إذن إضافي يتيح لـ SD Maid إعادة تشغيل الخدمة بنفسه.
يُسمّى الإذن `WRITE_SECURE_SETTINGS` ولا يمكن منحه إلا [عبر ADB](https://www.google.com/search?q=android+adb).
إذا كان ذلك مُعدًّا لديك، فشغّل هذا الأمر لمنح الإذن:

_يمكنك أيضًا تشغيل هذا الأمر باستخدام [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```bash
adb shell pm grant eu.darken.sdmse android.permission.WRITE_SECURE_SETTINGS
```

بعد منح هذا الإذن، يمكن لـ SD Maid استخدام الأمر [الموضح هنا](#acs-enable-via-adb) لتفعيل الخدمة عند الحاجة.
لن يستخدم SD Maid ذلك إلا إذا كنت قد فعّلت خدمة إمكانية الوصول يدويًا مرة واحدة على الأقل.
إذا دخلت إلى الإعداد مجددًا وطلبت من SD Maid عدم استخدام خدمة إمكانية الوصول، فلن تُستخدم تقنية إعادة التشغيل التلقائي هذه حتى تفعّل خدمة إمكانية الوصول يدويًا مرة أخرى.

لإزالة هذا الإذن من SD Maid مجددًا، استخدم هذا الأمر:

_يمكنك أيضًا تشغيل هذا الأمر باستخدام [Web ADB Helper](https://d4rken.github.io/web-adb/)._

```bash
adb shell pm revoke eu.darken.sdmse android.permission.WRITE_SECURE_SETTINGS
```

إذا ظهر لك `Operation not allowed: java.lang.SecurityException`، [فانظر هنا](https://stackoverflow.com/questions/52079343). بعض الأجهزة تقيّد أذونات ADB. تحقق مما إذا كان هناك إعداد إضافي (مثل `Disable Permission Monitoring`) في إعدادات المطوّرين يجب تبديله، وبعد استخدام الأمر يمكنك تعطيله مجددًا.

#### ACS: الحماية المتقدمة
{: #acs-advanced-protection }

على Android 17 والإصدارات الأحدث، يمنع وضع [**الحماية المتقدمة**](https://developer.android.com/privacy-and-security/advanced-protection-mode) (Advanced Protection) من Google التطبيقاتِ التي ليست أدوات مخصصة لإمكانية الوصول (قارئات الشاشة، والوصول عبر مفتاح التبديل، وغيرها) من استخدام خدمة إمكانية الوصول. SD Maid أداة صيانة وتنظيف، لذا **لا يمكن** تفعيل خدمة إمكانية الوصول الخاصة به ما دامت الحماية المتقدمة مفعّلة، إذ يرفض Android الخدمة على مستوى النظام، حتى لو حاولت منحها عبر صلاحية الجذر أو وصول ADB.

لا يستطيع SD Maid إعفاء نفسه من ذلك: فالتطبيقات التي تصنّفها Google كأدوات لإمكانية الوصول هي وحدها المؤهلة، وأدوات التنظيف مستبعدة صراحةً.

لديك خياران:

- **استخدم صلاحية الجذر أو وصول ADB** عند إعداد صلاحية الجذر أو وصول ADB، يمسح منظف التطبيقات في SD Maid ذاكرة التخزين المؤقت للتطبيقات عبر استدعاء مباشر للنظام و**لا** يحتاج إلى خدمة إمكانية الوصول. مع **صلاحية الجذر** يشمل ذلك أيضًا تنظيف تطبيقات فردية محددة. مع **وصول ADB** يعمل تشغيل "تنظيف جميع التطبيقات" الكامل دون خدمة إمكانية الوصول، لكن مسح ذاكرة التخزين المؤقت لتطبيقات *محددة* مختارة لا يزال يعتمد عليها. اضبط ذلك من الإعداد في SD Maid > [صلاحيّة الجذر](#root-access) أو [وصول ADB](#shizuku).
- **أوقف الحماية المتقدمة** إذا كنت تريد تحديدًا التنظيف المعتمد على إمكانية الوصول. تجدها في `Settings → Security & privacy → Advanced Protection` (الإعدادات > الأمان والخصوصية > الحماية المتقدمة) (يختلف المسار الدقيق حسب الجهاز). إعادة تشغيلها لاحقًا ستعطّل خدمة إمكانية الوصول مجددًا.

#### ACS: قيود AppOps
{: #acs-appops-restrictions }

قد يطبّق Android 13 والإصدارات الأحدث قيودًا إضافية على التطبيقات المثبتة من خارج المتاجر (side-loaded). لذا إذا لم يكن SD Maid مثبتًا من مصدر معروف (لدى Google) مثل Google Play أو متاجر التطبيقات الأخرى، فقد لا تتمكن من منح بعض الأذونات لـ SD Maid مباشرةً، مثل تشغيل خدمة إمكانية الوصول.

##### OxygenOS 15 - OnePlus
{: #oxygenos-15---oneplus }

نسيت OnePlus (أو لم ترغب ببساطة في) تضمين إجراء `Allow restricted settings` (السماح بالإعدادات المقيدة) في إعدادات التطبيق في OxygenOS 15. إذا لم تكن تثبّت التطبيق عبر Google Play، فيجب على أداة التثبيت تعيين العلامة `INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS`. إذا كان Shizuku مُعدًّا لديك، فيمكنك استخدام [تطبيق "Install with Options"](https://github.com/zacharee/InstallWithOptions).


##### أنظمة ROM الأخرى
{: #other-roms }

لرفع هذه القيود، افتح شاشة إعدادات تطبيق SD Maid في النظام. افتح القائمة في الزاوية العلوية اليمنى واختر `Allow restricted settings` (السماح بالإعدادات المقيدة)، وبعد ذلك يمكنك العودة إلى قائمة خدمات إمكانية الوصول وتفعيل خدمة SD Maid.

**ملاحظة:**
لتحسين سهولة الاستخدام (هذا سخرية)، قررت Google أن هذا الإعداد لن يكون مرئيًا إلا إذا:

* رأيت رسالة "Restricted setting" (إعداد مقيد) مرة واحدة على الأقل بعد الضغط على عنصر خدمة إمكانية الوصول الخاص بـ SD Maid
* فتحت إعدادات النظام وانتقلت إلى صفحة إعدادات SD Maid يدويًا، وليس عبر تطبيق آخر أو عبر اختصارات.

😮‍💨


يمكن [العثور هنا](https://blog.esper.io/android-13-sideloading-restriction-harder-malware-abuse-accessibility-apis/) على بعض المعلومات الإضافية حول الخلفية.

<a href="/assets/images/setup/acs-restricted-settings-1.webp"><img src="/assets/images/setup/acs-restricted-settings-1.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-2.webp"><img src="/assets/images/setup/acs-restricted-settings-2.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-3.webp"><img src="/assets/images/setup/acs-restricted-settings-3.webp" width="100" alt=""></a>
<a href="/assets/images/setup/acs-restricted-settings-4.webp"><img src="/assets/images/setup/acs-restricted-settings-4.webp" width="100" alt=""></a>

## صلاحيّة الجذر
{: #root-access }

مع صلاحية الجذر (root)، يحصل SD Maid على وصول كامل إلى جهازك ويمكنه إجراء أشمل فحص ممكن. يعني هذا عادةً أنك فتحت قفل محمّل الإقلاع (bootloader) وأعددت Magisk.

إذا اعتقد SD Maid أن جهازك قد يكون مزوّدًا بصلاحية الجذر، فسيطلب صلاحية الجذر أولًا. إذا مُنحت صلاحية الجذر، سيستخدمها SD Maid لمنح أكبر عدد ممكن من الأذونات الأخرى. صلاحية الجذر هي أعلى مستوى وصول يمكنك منحه لتطبيق، وتصبح الأذونات الأخرى حينها مطلوبة فقط لسهولة الاستخدام والأداء، ولهذا لا تتطلب موافقة إضافية أثناء الإعداد إذا كان SD Maid يملك صلاحية الجذر بالفعل.

## وصول ADB
{: #shizuku }

يتيح وصول ADB لـ SD Maid استخدام واجهة التصحيح في Android ‏(ADB) لتنفيذ عمليات بصلاحيات مرتفعة. يحتاج إلى تطبيق منفصل يشغّل خدمة ADB على جهازك ويمنح التطبيقات الأخرى الوصول إليها: [Porter](#porter) أو [Shizuku](#using-shizuku). إنه تعديل يمكنك إجراؤه على هاتفك وتستفيد منه التطبيقات الأخرى.

إنه مشابه للجذر، لكنه ليس مثله. الأذونات أقل قوة بقليل، لكن التعديل ليس دائمًا ولا يتطلب فتح قفل محمّل الإقلاع.

### Porter
{: #porter }

[Porter](https://porter.darken.eu) هو النسخة المتفرعة (fork) من Shizuku التي أتولى صيانتها، مع دعم اختياري لصلاحية الجذر. يشرح موقعه الإلكتروني كيفية تثبيته وتشغيله. على Android 11 والإصدارات الأحدث، يتيح لك تصحيح الأخطاء اللاسلكي تشغيله دون حاسوب.

إذا كنت تُعدّ وصول ADB لأول مرة، فاستخدم Porter. توجّهك إصدارات SD Maid من GitHub وF-Droid إلى Porter عندما لا يكون أي تطبيق لوصول ADB مثبتًا. أما إصدار Google Play فيوجّهك إلى Shizuku على Google Play، لكنه يعمل مع Porter بالطريقة نفسها.

إذا كان Porter وShizuku مثبتين معًا، يستخدم SD Maid تطبيق Porter. تقول بطاقة الإعداد حينها `Porter is installed, so SD Maid waits for Porter instead of Shizuku` (Porter مثبت، لذا ينتظر SD Maid تطبيق Porter بدلًا من Shizuku). شغّل Porter للمتابعة، أو ألغِ تثبيته إذا كنت تريد أن يستخدم SD Maid تطبيق Shizuku.

### استخدام Shizuku
{: #using-shizuku }

يوجد إصداران من Shizuku:

* [النسخة المتفرعة من thedjchi](https://github.com/thedjchi/Shizuku) ‏([دليل الإعداد](https://github.com/thedjchi/Shizuku/wiki/setup)) تخضع لصيانة نشطة، وتفيد التقارير بأنها تعمل على أجهزة [لا يتصل فيها](#shizuku-never-connects) الإصدار الأصلي أبدًا.
* [الإصدار الأصلي من RikkaApps](https://shizuku.rikka.app/guide/setup/) لا يزال يعمل جيدًا على معظم الأجهزة.

لا يمكن تثبيتهما في الوقت نفسه. إذا كان الإصدار الأصلي يعمل لديك بالفعل، فلا داعي للانتقال.

### منح الوصول
{: #granting-access }

بعد إعداد Porter أو Shizuku:

* في إعداد SD Maid، اختر `حاول استخدام وصول ADB`. هذا خيار داخلي في SD Maid، يمكنك استخدامه لتفعيل استخدام SD Maid لوصول ADB أو تعطيله
* بعد إعطاء الموافقة، يسألك مربع حوار من Porter أو Shizuku ما إذا كان يُسمح لـ SD Maid باستخدامه
* أكّد مربع الحوار
* تُظهر بطاقة الإعداد `وصول ADB` ما إذا كان SD Maid يستطيع الاتصال

إعداد وصول ADB، إذا كنت قد منحت SD Maid صلاحية الجذر بالفعل، لا يفعّل ميزات إضافية، لكنه يحسّن الموثوقية. إذا فشلت الإجراءات بصلاحية الجذر، فسيعيد SD Maid المحاولة باستخدام وصول ADB.

### وصول ADB: استكشاف الأخطاء وإصلاحها
{: #shizuku-troubleshooting }

يمكنك التحقق من حالة وصول ADB في SD Maid بالانتقال إلى `الإعدادات > الإعداد` وعرض بطاقة الإعداد `وصول ADB`. يجب أن تقول `يمكن لـ SD Maid الآن استخدام وصول ADB عبر Porter` (أو `عبر Shizuku`).

* `Porter لم يسمح لـ SD Maid بعد`: أكّد مربع حوار الوصول، أو اسمح لـ SD Maid في تطبيق Porter (أو Shizuku).
* `Porter يرفض وصول SD Maid`: رُفض SD Maid بشكل دائم. افتح Porter (أو Shizuku) واسمح لـ SD Maid من هناك.
* `Update Porter to use it with SD Maid` (حدّث Porter لاستخدامه مع SD Maid) أو `This version of SD Maid is too old for Porter` (هذا الإصدار من SD Maid قديم جدًا بالنسبة لـ Porter): لا يتشارك التطبيقان إصدارًا متوافقًا. حدّث التطبيق الذي تذكره البطاقة.

#### Shizuku لا يتصل أبدًا
{: #shizuku-never-connects }

تقول البطاقة `فشل وصول ADB` و`Shizuku is running, but it never connected SD Maid to its helper service.` (يعمل Shizuku، لكنه لم يربط SD Maid بخدمته المساعدة أبدًا). كانت الإصدارات الأقدم تقول `Waiting for link with Shizuku` (بانتظار الارتباط بـ Shizuku) أو `Shizuku link failed` (فشل الارتباط بـ Shizuku).

افتح تطبيق Shizuku. إذا قال إنه يعمل وكان SD Maid في قائمة التطبيقات المصرّح لها، فإن Shizuku مُعدّ بشكل سليم ولن تفيد إعادة إعداده. ما يفشل هو الخدمة المساعدة في Shizuku: فهي تتوقف عند بدء التشغيل قبل أن تتمكن من الرد، ويستسلم SD Maid بعد نحو 15 ثانية. يُجري `إعادة محاولة` الفحص نفسه ويفشل بالطريقة نفسها.

هذا خطأ في Shizuku ‏([RikkaApps/Shizuku#1198](https://github.com/RikkaApps/Shizuku/issues/1198))، وليس في SD Maid، ويُعطّل أي تطبيق يستخدم الخدمة المساعدة في Shizuku. يؤثر في أجهزة MediaTek، والأجهزة التي تعمل بنظام HyperOS أو MIUI.

تفيد التقارير بأن [النسخة المتفرعة من thedjchi](https://github.com/thedjchi/Shizuku) تُصلحه. للانتقال:

* ألغِ تثبيت الإصدار الأصلي أولًا، فلا يمكن أن يتعايشا معًا
* ألغِ إقرانه من تصحيح الأخطاء اللاسلكي إذا كنت قد استخدمته
* ثبّت النسخة المتفرعة من [إصداراتها](https://github.com/thedjchi/Shizuku/releases) واتبع [دليل الإعداد](https://github.com/thedjchi/Shizuku/wiki/setup) الخاص بها
* امنح SD Maid وأي تطبيق آخر كان يستخدمه إذن Shizuku مجددًا
