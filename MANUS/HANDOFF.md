# تسليم AMAN — نقطة توقف STAGE 2 Admin Android

## المستودع وGit

- Repository: `aaaaubad-byte/AMAN.V1`.
- Branch: `main`; `origin` يشير إلى المستودع الصحيح.
- HEAD المحلي و`origin/main`: `0384a3cf11ea21ddb9e77c3e188b6a0db5970156` — `phase 1: add AMAN database migration`.
- لا commit جديد ولا Push. الـworktree يحتوي `MANUS/CHANGELOG.md` و`MANUS/PROJECT_STATE.md` معدلين، `MANUS/PHASE_2_STATE.md` و`MANUS/HANDOFF.md` جديدين، ومجلد `admin/` جديدًا. لا ملفات محذوفة.
- لا تغييرات في `database/` أو `MANUS/PHASE_1_STATE.md`.

## حدود التفويض

أذن المستخدم صراحة بالبدء في STAGE 2 بالاعتماد على SQL المخزن فقط وغير المتحقق. منع الاتصال بقاعدة Supabase الحقيقية أو إنشاء قاعدة أو تنفيذ/تغيير SQL أثناء هذه المرحلة. وأكد في طلب التنفيذ عدم Push أثناء العمل؛ لا يُسمح به إلا بعد اكتمال المرحلة (أو مسار تسليم نفاد الرصيد إن انطبق). لم يُنفذ أي اتصال حي أو SQL/RPC.

## نقطة الإنجاز الدقيقة

تم إنشاء مشروع Kotlin/Jetpack Compose أصلي داخل `admin/`، مع 15 وجهة معرفة، RTL، تسجيل دخول Email/Password يعتمد على Supabase Auth ثم `is_admin()`, جلسات/لقطات محلية مشفرة، قراءة PostgREST، بحث مجمع، تحديث دوري للقراءات، تفاصيل مرتبطة لبعض الجداول، نسخ أرقام، خروج، واعتماد طلب النقاط بعد تأكيد عبر RPC. تُسجل مهمة السداد فقط بعد تأكيد المشغل أنها نفذت خارجيًا؛ SQL نفسه لا ينفذ الدفع الخارجي.

تم اجتياز:

```bash
cd admin && ./gradlew clean testDebugUnitTest assembleDebug --console=plain
```

النتيجة `BUILD SUCCESSFUL`، 5 اختبارات/صفر إخفاق، APK debug طوله 18 MiB ومعرّفه `com.aman.admin`، SHA-256 `dad65b4cdacff051b9ca0dff0b63dcd5834927d5ae944326580839d1fd599370`. فحص APK لم يجد إعداد service-role أو بيانات اعتماد JWT خاصة. لا يوجد جهاز/محاكي Android متصل؛ لا يوجد اختبار UI أو اختبار Supabase حي.

## لماذا المرحلة ليست مكتملة

راجع جدول الشاشات التفصيلي في `MANUS/PHASE_2_STATE.md` وسجل `admin/IMPLEMENTATION_BLOCKERS.md`. من الوقائع المستخلصة من SQL الملتزم:

- `payment_tasks` لا يظهر ضمن منح `GRANT SELECT`؛ لذلك A07 لا تستطيع حتى تحميل قائمة المهام عبر PostgREST.
- `task_settings` و`provider_tariffs` لا يظهران ضمن منح القراءة؛ تفاصيل A05/A08 وإعدادات A11 غير مكتملة.
- لا توجد عقود إدارية للرفض، reschedule/cancel، CRUD الشركات والباقات والدفع والمستخدمين، إرسال الإشعارات الإدارية، أو تصدير/تقارير مالية كاملة.
- `execute_payment_task` يسجل المهمة/الحركة المالية ويعيد بناء الخطة؛ لا ينفذ عملية الدفع الخارجية.
- عنوان Supabase ومفتاح anon العام غير متاحين. الترحيل غير متحقق مقابل النشر الفعلي.

هذه العقود لا يجوز محاكاتها أو تجاوزها بمفتاح service-role. لذلك حالة المرحلة **in progress**، لا “complete”.

## الخطوة التالية بالضبط

استئنافًا من ملفات `admin/` الحالية، وليس إعادة إنشاء المشروع: حسم/اعتماد العقود الإدارية الناقصة في مصدر الحقيقة ضمن الصلاحيات المخصصة لذلك، ثم إكمال الشاشات/الأفعال وفقها وإضافة اختبارات مناسبة. بعد ذلك فقط أجرِ مراجعة مطابقة المرجع و`database/`، ثم Commit وPush. لا ترفع الـworktree الحالي غير المكتمل.
