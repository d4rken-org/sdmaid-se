---
title: "الأخطاء"
lang: ar
translation_key: bugs
permalink: /ar/bugs
source_hash: a218be31a6c4ae63
---
# الأخطاء
{: #bugs }

🐌 🦋 🐛 🐜 🐝 🐞

الأخطاء (bugs) جزء من حياة أي تطبيق. لا تقصد بك أي سوء، لكنها ليست هنا لتبقى أيضًا 😠.

ما هي الأخطاء؟

* إذا تعطّل التطبيق فجأة (crash) فهذا خطأ بلا شك
* إذا كان شيء ما يتصرف بشكل غير متوقع، فقد يكون خطأً.

في كل الأحوال، تأكد من أنك تستخدم آخر تحديث، فقد يكون الخطأ قد أُصلح بالفعل.

إذا لم يكن الخطأ قد أُصلح، فأبلغ عنه :grin:.

## الإبلاغ عن خطأ
{: #reporting-a-bug }

أنشئ بلاغًا (issue) على GitHub:
<https://github.com/d4rken-org/sdmaid-se/issues>

* صِف ما كنت تتوقعه وما حدث فعلًا
* اذكر الخطوات التي يجب أن يتبعها شخص آخر لإعادة إنتاج المشكلة
* صِف إعدادك
    * نوع جهازك (مثل Pixel 5)
    * إصدار Android (مثل Android 13.1)
    * إذا كان جهازك مزوّدًا بصلاحية الجذر (root)، فاذكر إصدار Magisk

أرفق [سجل تصحيح](#debug-log) إذا كنت قادرًا على إعادة إنتاج المشكلة. 

تلميح: يمكنك [الضغط مطولًا على عنصر "سجل التغييرات"](https://github.com/d4rken-org/sdmaid-se/pull/940) في إعدادات SD Maid لنسخ رقم الإصدار إلى الحافظة.

## سجل التصحيح
{: #debug-log }

سجل التصحيح (debug log) هو تسجيل لما يفعله SD Maid. تبدأ التسجيل، ثم تعيد إنتاج المشكلة، ثم توقفه، وبعدها يمكنك مشاركة ملف zip يتضمن سردًا زمنيًا لما قام به SD Maid.

لبدء تسجيل سجل تصحيح:
* انتقل إلى الإعدادات في SD Maid بالضغط على أيقونة الإعدادات في أسفل يمين لوحة التحكم
* ابحث عن عنصر `الدعم` في قائمة الإعدادات
* اضغط على `تسجيل سجل التصحيح` في شاشة الدعم، ثم اضغط على `تسجيل سجل التصحيح` مرة أخرى في مربع الحوار
* أثناء التسجيل، تعرض لوحة التحكم بطاقة `تسجيل سجل التصحيح`
* **الآن أعد إنتاج المشكلة التي تريد تسجيلها**
* بعد أن تعيد إنتاج المشكلة، اضغط على `إيقاف التسجيل` في بطاقة لوحة التحكم تلك أو في شاشة الدعم. إذا كان التسجيل قصيرًا جدًا، يطلب SD Maid التأكيد أولًا؛ اضغط على `إيقاف على أي حال` لإيقافه.
* تُفتح شاشة `سجل التصحيح`. بعد اكتمال ضغط ملف السجل، اضغط على `مشاركة` لإرساله.

سيستمر تسجيل سجل التصحيح حتى توقفه، وسيستمر التسجيل حتى لو أوقفت SD Maid إجباريًا وأعدت تشغيله.

إذا كنت تستكشف مشكلات تتعلق بالوظائف الأساسية، مثل اكتشاف صلاحية الجذر أو مناطق التخزين المكتشفة، فمن المفيد إيقاف SD Maid إجباريًا وإعادة تشغيله أثناء التسجيل، لأن بعض البيانات لا تُهيّأ إلا مرة واحدة في كل جلسة للتطبيق.

تُحفظ ملفات سجل التصحيح في `/storage/emulated/0/Android/data/eu.darken.sdmse/cache/debug/logs`.

بالصور:

<a href="/assets/images/bugs/debug-log-1.webp"><img src="/assets/images/bugs/debug-log-1.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-2.webp"><img src="/assets/images/bugs/debug-log-2.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-3.webp"><img src="/assets/images/bugs/debug-log-3.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-4.webp"><img src="/assets/images/bugs/debug-log-4.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-5.webp"><img src="/assets/images/bugs/debug-log-5.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-6.webp"><img src="/assets/images/bugs/debug-log-6.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-7.webp"><img src="/assets/images/bugs/debug-log-7.webp" width="100" alt=""></a>
<a href="/assets/images/bugs/debug-log-8.webp"><img src="/assets/images/bugs/debug-log-8.webp" width="100" alt=""></a>
