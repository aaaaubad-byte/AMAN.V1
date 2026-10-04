# حالة مشروع AMAN

## المستودع والتنفيذ

- المستودع: `aaaaubad-byte/AMAN.V1`، الفرع: `main`.
- commit AMAN-2 المنشور: `b69d5ec` — `phase 2: complete admin android application`؛ الأب المباشر `e054ec6`.
- المرحلة الحالية: **AMAN-2 — إكمال مصدر تطبيق Admin وربط العقود**. اجتاز `git diff --check` ودُفع إلى `origin/main`.
- لا تغييرات على `customer/` أو `MANUS/PHASE_1_STATE.md`.

## حالة المراحل

- STAGE 1: migrations مصدرية؛ تطبيقها والتحقق من Supabase المقصودة غير مثبتين.
- STAGE 2 / AMAN-2: شاشات Admin A01–A15 وعقودها مربوطة في المصدر. نتيجة اختبارات الوحدة بعد آخر تعديل ناجحة؛ القبول الحي واختبار جهاز/محاكي ما زالا مفتوحين. راجع `MANUS/PHASE_2_STATE.md`.
- STAGE 3: مصادر تطبيق العميل محفوظة تحت `customer/` من checkpoint سابق؛ AMAN-2 لم يعدلها.
- AMAN-1: إصلاحات Backend السابقة في migration 003 وSQL النهائي؛ مصدرية فقط، لا تطبيق حي.
- AMAN-2 أضاف migration `004_admin_provider_catalog_rpcs.sql` لعقود A08. لم تُنفذ أو تُطبق.

## ما تم التحقق منه الآن

- `cd admin && ./gradlew testDebugUnitTest --no-daemon --console=plain` — **BUILD SUCCESSFUL**، 7 اختبارات، 0 فشل/أخطاء. يترجم المصدر ويشغل الوحدة؛ لم يُجرِ تصدير/تسليم APK في هذا التحقق.
- `database/AMAN_DATABASE_FINAL.sql` يطابق concatenation للمigrations 001–004 حرفيًا.
- لم يتوفر `pglast` في checkpoint الأخير؛ لا نزعم نجاح تحليل parser للمigration 004 في هذه الجولة.
- لا URL/anon key للمشروع، لا اتصال Supabase، لا queries/RPCs حية ولا تنفيذ SQL. لم يُختبر على جهاز/محاكي.

## الحدود المعروفة

- migration 004 لم تُطبق؛ أي اختلاف live schema/RLS يحتاج تحققًا في البيئة المخولة.
- دفع A07 خارجي يدوي؛ التطبيق يسجل العملية/مرجعها بعد إتمامها خارج التطبيق.
- لا صور مرجعية كافية لمقارنة الواجهة بكسليًا.
- لا تستخدم `service_role` ولا تدّعِ اكتمالًا إنتاجيًا بناءً على ترجمة واختبارات الوحدة.

## الخطوة التالية

افتح `MANUS/HANDOFF.md` و`MANUS/PHASE_2_STATE.md`، تحقق من HEAD وحالة Git، ثم انتظر تهيئة المشروع المقصود وتفويضًا واضحًا لاختبار/تطبيق SQL حي. بعد ذلك تحقق من migrations/RLS/RPC والأدوار، واختبر تدفقات Admin على جهاز/محاكي. لا تصدر APK ما لم يُطلب.
