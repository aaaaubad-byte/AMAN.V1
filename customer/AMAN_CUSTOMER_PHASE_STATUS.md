# AMAN Customer Phase Status

**التاريخ:** 2026-10-05
**المستودع:** `aaaaubad-byte/AMAN.V1`
**نسخة العمل المعزولة:** `/home/ubuntu/AMAN.V1-isolated/repo`
**الفرع:** `main`
**نطاق هذه الجولة:** Customer وعقود SQL الكنسي اللازمة لمسارات الحماية وخطة المهام؛ لم يبدأ تنفيذ Admin.

## الحكم التنفيذي

**الحالة: PARTIAL / NOT ACCEPTED AS FULL V7 COMPLETION.**

تمت مراجعة Customer داخل نسخة Git معزولة عند commit `be930195ba21c58207c79449da741d85187b3b93`، وأُصلحت فجوة تتبع ثابتة (`C02.TASK.SUMMARY`) وأزيل استيراد Compose غير مستخدم ومراجع README التاريخية غير الصالحة. لا توجد استدعاءات RPC للعميل بلا تعريف مقابل في `database/AMAN_V7_DATABASE.sql`، ولا توجد معرفات `traceElement` مستخدمة بلا سجل.

لا يصح إعلان اكتمال Customer بالكامل لأن القبول التشغيلي الحي لـSupabase/PostgreSQL وRLS والمعاملات والتدفقات البصرية لم يُثبت، كما أن V7/ARP يطلب تغطية عنصر-بعنصر أوسع من registry الحالي. لا توجد وظيفة وهمية جديدة في هذه الجولة، ولا تم استخدام SQL تاريخي كـfallback. عُدّل SQL الكنسي فقط لإزالة placeholders اللازمة لعقد محرك المهام الذي تعتمد عليه الحماية.

## مصفوفة التتبع المختصرة

| المجال | UI / الحالة | Repository / RPC | عقد SQL / الصلاحية | التحقق |
|---|---|---|---|---|
| C01–C03 | Dashboard، الأرقام، ملخص المهمة، loading/empty/offline | قراءات `profiles`, `point_balances`, `protections`, `customer_numbers`, `get_customer_task_summaries` | قراءات مستخدم مقيدة بـ`auth.uid()` وRLS | PASS ثابتًا؛ runtime NOT VERIFIED |
| C04–C05 | شراء النقاط، pending/rejected/cancelled/resubmit، العمليات والفلاتر | `submit_points_purchase_request`, `cancel_points_purchase`, `resubmit_points_purchase` | snapshot، idempotency، ledger بعد الاعتماد | PASS ثابتًا جزئيًا؛ runtime/concurrency NOT VERIFIED |
| C06 | إضافة/تعديل/أرشفة الرقم، E.164، أطول بادئة | `add_customer_number`, `update_customer_number`, `archive_customer_number` | ملكية، prefix، منع تغيير الهوية بعد التفعيل | PASS ثابتًا؛ runtime NOT VERIFIED |
| C07–C08 | تفعيل/تمديد/تجديد، حساب تقديري، تأكيد، insufficient balance | `activate_protection`, `extend_protection`, `renew_protection` | خصم ذري، idempotency، حماية، ledger/operation/notification/plan | PASS ثابتًا جزئيًا؛ runtime/concurrency NOT VERIFIED |
| C09–C11 | تنبيهات الإدارة، الدعم، إشعارات النظام، mark-read | `mark_notification_read`, `create_support_thread`, `send_support_message` | ملكية/RLS؛ لا تظهر تفاصيل المهام المالية للعميل | PASS ثابتًا جزئيًا؛ runtime NOT VERIFIED |
| C12–C15 | تقارير/CSV، الحساب/Auth، البحث، عن AMAN، bottom navigation | PostgREST للمصادر المملوكة وSupabase Auth لتغيير كلمة المرور | لا تُخزن كلمة المرور في AMAN؛ cache للمشاهدة فقط | PASS ثابتًا جزئيًا؛ UI/runtime NOT VERIFIED |

## ما تم التحقق منه

- Git clone معزول ونظيف قبل التعديل، والفرع `main` متزامن مع `origin/main` عند نقطة البدء.
- عدد شاشات Customer في العقد البرمجي: `15` (`C01`–`C15`).
- كل استدعاءات RPC الصريحة في Customer لها تعريف مقابل في SQL الكنسي: `15/15`.
- كل معرفات `traceElement(...)` المستخدمة في Customer لها سجل في `CustomerUiTraceability`: لا توجد فجوة بعد الإصلاح.
- `git diff --check`: ناجح.
- اختبار القواعد الثابتة: معرفات التتبع فريدة ومقيدة بنطاق الشاشة؛ اختبار C02 الجديد يثبت استمرار وجود ملخص المهام.
- إزالة مراجع `IMPLEMENTATION_BLOCKERS.md` و`../MANUS/PHASE_3_STATE.md` القديمة من README.
- استبدال placeholders الخاصة بـ`reschedule_payment_task` و`execute_payment_task` و`cancel_payment_task` بتنفيذ ذري يحفظ التاريخ، يحدّث anchor، يسجل financial ledger عند وجود مبلغ، يمنع التكرار، ويسجل audit.
- إضافة `p_preserve_task_id` داخليًا لإعادة بناء المستقبل دون إلغاء المهمة المعاد جدولتها، وإضافة grants صريحة لدوال محرك المهام الثلاث.
- نجح `pglast` في parsing عدد `169` عبارة SQL؛ هذا parsing ساكن ولا يثبت تنفيذ PL/pgSQL أو RLS.

## الاختبار والبناء

- **BUILD = NOT VERIFIED**
  **REASON = Android SDK unavailable in execution environment**
- تم تشغيل `./gradlew :app:testDebugUnitTest --no-daemon`، لكنه توقف قبل Kotlin compilation برسالة `SDK location not found` لغياب `ANDROID_HOME` و`local.properties sdk.dir`.
- لم يتم تثبيت Android SDK أو محاولة إصدار APK/AAB، وفق نطاق المرحلة.
- لم يتوفر PostgreSQL/Supabase مصرح به لتشغيل schema وPL/pgSQL وRLS أو اختبارات T01–T22.

## القيود والفجوات المتبقية

1. **F-001 / traceability:** registry الحالي يغطي التنفيذ الموجود لكنه لا يثبت تغطية كل عناصر V7 البصرية البالغ عددها 146 عنصرًا؛ يلزم استكمال mapping عنصر-بعنصر وربطه باختبارات قبول.
2. **Runtime acceptance:** لا يوجد تحقق حي لتدفقات Auth، PostgREST، RPC، RLS، rollback، التنافس، أو استجابة UI بعد mutation؛ كما لم تُنفذ دوال محرك المهام على PostgreSQL فعلي.
3. **SQL contract gap خارج مسار Customer المباشر:** `admin_adjust_points` ما زال `UNRESOLVED` لأن V7 لا يحدد قيمة مالية موثوقة لكل نقطة؛ لم تُخترع سياسة مالية. دوال محرك المهام الثلاث أصبحت منفذة ثابتًا، لكن تشغيلها الحي غير متحقق.
4. **Support idempotency:** عقد الدعم الحالي لا يوفر مفتاح idempotency آمنًا لإعادة إرسال الرسالة تلقائيًا؛ التطبيق يمنع إعادة الإرسال التلقائي ويطلب التحقق من الخادم.

## التغييرات في هذه الجولة

- إضافة `C02.TASK.SUMMARY` إلى `CustomerUiTraceability` مع عقد القراءة والصلاحية والأثر.
- إضافة assertion للاختبار الثابت الخاص بالعنصر.
- إزالة استيراد `AlertDialog` غير المستخدم.
- إزالة مراجع README إلى ملفات تاريخية غير موجودة، وتثبيت V7/SQL الكنسي كمراجع Customer.
- تحديث هذا التقرير بالحكم والقيود ونتائج التحقق.

## Git

- **العمل غير المدفوع في هذه الجولة:** تعديلات Customer وREADME والتقرير و`database/AMAN_V7_DATABASE.sql` الكنسي فقط.
- يجب ألا يُوصف هذا commit بأنه اكتمال V7 النهائي ما دامت القيود أعلاه قائمة.
