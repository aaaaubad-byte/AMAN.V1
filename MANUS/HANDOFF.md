# تسليم AMAN — checkpoint AMAN-2

## المستودع والحالة

- Repository: `aaaaubad-byte/AMAN.V1`، branch `main`.
- checkpoint AMAN-2 محفوظ ومدفوع إلى `origin/main`: commit `b69d5ec` — `phase 2: complete admin android application`.
- الأب المباشر للـcheckpoint: `e054ec6`.
- **حالة العمل:** كود Admin وعقوده مترجمة/مختبرة محليًا؛ تكامل Supabase الحي وجهاز Android غير مختبرين. لا يوجد ادعاء جاهزية إنتاج.

## ما اكتمل في AMAN-2

- توصيل شاشة الإدارة A01–A15 بمصادر SQL/RPC، بما في ذلك بحث/فلاتر، تفاصيل مرتبطة، mutations بحسب permission، تقارير وتصدير CSV بعد صلاحية Backend، حالات تحميل/خطأ/خلو وOffline.
- Supabase Auth: دخول، refresh، إعادة فحص `is_admin`, حساب/permissions من `admin_account_info`, معالجة 401/403/5xx؛ Cache الجلسة واللقطات مشفر، mutations معطلة على Offline.
- لوحة A01 تستمد الأعداد من `count exact` عبر PostgREST مع RLS، ولا تستخدم أرقامًا mock.
- **قرار A04:** لا تحرير لرقم الهاتف إطلاقًا ولا RPC تحرير. العرض وتغيير حالة العلاقة فقط حسب العقد الحالي.
- **قرار A08:** Providers وTelecom Prefixes وProvider Tariffs تُدار عبر RPCs؛ أضيفت migration 004 مصدرية. لا migration منفذة على Supabase.
- **إشعارات:** تسليم المستخدم في `notifications` (`admin_alert`)، وسجل الحملة في `admin_notifications` الموجود أصلًا في migration 002؛ لم يُنشأ جدول إشعارات إضافي.
- A07 يسجل مرجع الدفع بعد تنفيذه خارجيًا؛ التطبيق لا ينفذ الدفع.

## آخر نقطة عمل دقيقة

- آخر ملف مصدر عُدّل قبل التوثيق: `admin/app/src/main/java/com/aman/admin/data/AdminRepository.kt` (آخر تعديل 2026-10-04 03:50:12+03:00)، بما فيه التحقق من النماذج وفلاتر cache Offline.
- آخر شاشة عولجت: A01 مؤشرات حقيقية؛ كما صُحح بحث/تحديث القوائم التابعة A02/A05/A08 وA11.
- آخر عقد Backend: migration `004_admin_provider_catalog_rpcs.sql` لعقود حفظ بادئات الاتصالات والتعرفات الإدارية؛ وGateway يقرأ العدّادات عبر RLS. ملف SQL النهائي مصدره concat migrations 001–004.

## فحوص checkpoint

- `cd admin && ./gradlew testDebugUnitTest --no-daemon --console=plain`: BUILD SUCCESSFUL بعد آخر تعديلات المصدر، 7 اختبارات، 0 فشل.
- `cmp` على concat migrations 001–004 مقابل `database/AMAN_DATABASE_FINAL.sql`: ناجح.
- فشل فحص `pglast` لعدم تثبيت الحزمة في البيئة؛ لا نزعم parser validation للمigration 004.
- لم يجرِ الاتصال بـSupabase، ولا تشغيل SQL أو queries/RPC حية، ولا اختبار على محاكي/جهاز. لا APK مطلوب أو مسلم.

## ما لم يكتمل

1. لا قيم `SUPABASE_URL` و`SUPABASE_ANON_KEY` للمشروع المقصود.
2. migration 004 (وكذلك تحقق حال كل migrations السابقة) لم تُطبق على قاعدة حية.
3. RLS، permission matrices، النتائج الفعلية لكل RPC، التدقيق والمعاملات لم تُختبر في بيئة Supabase.
4. لا اختبار UI على جهاز/محاكي ولا مقارنة بصرية دقيقة لغياب لقطات مرجعية.
5. التنفيذ الخارجي للدفع غير مدمج؛ المسار الحالي يتطلب تنفيذًا بشريًا رسميًا ثم إدخال المرجع.

## الخطوة التالية حرفيًا

1. `git log -1 --oneline` ثم `git status --short` للتأكد من checkpoint المنشور.
2. لا تعدّل المصدر ولا تنفذ SQL حي قبل تهيئة مشروع Supabase المقصود وتوفر تفويض صريح لاختبار/تطبيق migrations.
3. عند التصريح: اختبر migrations/RLS/roles/RPCs على البيئة المخولة، وابدأ بـmigration 004 ومطابقة schema الفعلية؛ لا تستخدم service-role في التطبيق.
4. بعد اجتياز Backend، اختبر Auth والوظائف A01–A15 على جهاز/محاكي، وسجل نتائج الفشل/النجاح هنا وفي `MANUS/PHASE_2_STATE.md`.

## سجل سابق

- STAGE 3 (العميل): مصدره في `customer/` مستقل؛ لا تعد إنشاءه، ولم يلمسه AMAN-2.
- AMAN-1: `database/migrations/003_aman1_backend_repairs.sql` و`database/AMAN_DATABASE_FINAL.sql` أصلحا عقودًا مصدرية؛ لا يعني ذلك تطبيقًا حيًا.
