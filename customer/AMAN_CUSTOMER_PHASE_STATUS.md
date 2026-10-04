# AMAN Customer Phase Status

**التاريخ:** 2026-10-05
**النطاق:** `customer/` فقط — لم يبدأ تنفيذ Admin ولم يُعدّل `database/AMAN_V7_DATABASE.sql`.

## النتيجة التنفيذية

تم تنفيذ ومراجعة مسار Customer من واجهة Compose إلى Repository وSupabase Auth/PostgREST/RPC، مع إزالة عقود Customer القديمة وعدم استخدام أي fallback قديم. كل استجابة غير مؤكدة تبقى خطأً ظاهرًا ولا تُعرض كنجاح.

تمت إضافة:

- مطابقة الجداول والأعمدة الكنسية: `system_notifications`, `amount_points`, `account_identifier`, `activated_number_id`, `p_extension_days`.
- مطابقة RPCs الكنسية: `create_profile_if_missing`, `add_customer_number`, `submit_points_purchase_request`, `mark_notification_read`, `create_support_thread`, `send_support_message`, `activate_protection`, `extend_protection`.
- إنشاء ملف العميل ورصيد النقاط بعد التسجيل عبر `create_profile_if_missing` بدل الاعتماد على trigger غير موجود في SQL الحالي.
- طابور شراء نقاط مشفّر لكل مستخدم، idempotency key مرتبط بالطلب، وحماية من الإرسال المكرر.
- تحقق E.164 والبادئة الأطول، تحقق الرصيد/التعرفة/المدة، تأكيدات العمليات الحساسة، وحالات loading/offline/stale/error.
- سجل محلي غير حساس للإجراءات والأخطاء في `customer-action-errors.jsonl` مع redaction للقيم الحساسة، لتشخيص كل Auth/navigation/search/mutation/support action.
- تصدير تقارير CSV مع تحييد Formula Injection.
- تحديث `CustomerUiTraceability` ليعكس العقد الكنسي والفجوات الفعلية.

## مصفوفة الشاشات

| الشاشة | UI / Smart Logic / State | Repository / Backend | الحالة |
|---|---|---|---|
| C01 الرئيسية | profile، balance، unread، admin alerts، recent operations، protection summary، navigation | قراءات RLS للمستخدم الحالي | مكتملة ضمن العقد |
| C02 الأرقام المفعلة | قائمة تفاصيل الحماية، انتهاء/مدة متبقية، نسخ، تفاصيل | `protections` عبر `activated_numbers → customer_numbers` | مكتملة قرائيًا |
| C03 الأرقام غير المفعلة | تصفية الأرقام التي لا تملك حماية نشطة، نسخ وتفاصيل | `customer_numbers` + ownership relation | مكتملة قرائيًا |
| C04 إضافة نقاط | package/method selectors، account/instructions، reference، review dialog، pending/outbox | `submit_points_purchase_request` | مكتملة |
| C05 العمليات | search، type/status/date filters، validation، details، empty/error | operations + point ledger + purchases + numbers | مكتملة |
| C06 إضافة رقم | E.164، أطول prefix، duplicate، online-only، add confirmation | `add_customer_number` | الإضافة مكتملة؛ التعديل/الأرشفة فجوة SQL موثقة |
| C07 تفعيل رقم | duration، tariff/cost/balance calculation، insufficient balance، confirmation path | RPC الكنسي موجود اسميًا لكن SQL body `UNRESOLVED` ومدخلاته لا تدعم first activation | `SQL_REVISION_REQUIRED` — الزر معطل لمنع عملية خاطئة |
| C08 تمديد رقم | protection selector، snapshot tariff، cost/new expiry calculation، confirmation path | RPC الكنسي موجود اسميًا لكن SQL body `UNRESOLVED` | `SQL_REVISION_REQUIRED` — الزر معطل |
| C09 تنبيهات الإدارة | list، read state، details، synchronize read | `system_notifications`, `mark_notification_read` | مكتملة |
| C10 التواصل | new thread، reply، open/closed state، refresh، uncertain-send guard، messages | `support_threads`, `support_messages`, create/send RPCs | مكتملة |
| C11 إشعارات النظام | list، read state، details، synchronize read | `system_notifications`, `mark_notification_read` | مكتملة |
| C12 التقارير | category/quick range/date validation/view/refresh/results/export | user-scoped ledger/purchases/protections/operations/numbers | مكتملة |
| C13 الحساب | profile/subscriber/balance/logout، settings/security visibility | profile/subscriber/balance/Auth | settings/security actions `DATABASE_CONTRACT_GAP` |
| C14 البحث | debounce، sanitization، server-side filters، local result synthesis، clear | user-scoped numbers/operations/purchases/system notifications | مكتملة |
| C15 عن أمان | identity/service information | static repository copy | مكتملة |

## الفجوات الصريحة

### `SQL_REVISION_REQUIRED`

1. **First activation lifecycle:** V7 يتطلب أن يبدأ التفعيل من `customer_numbers` ثم ينشئ `activated_numbers` ويُنشئ الحماية والخطة/العمليات ذريًا. SQL الحالي يعرّف `activate_protection(p_activated_number_id, ...)` فقط، وbody الحالي يرفع `UNRESOLVED` ولا ينشئ المرشح.
2. **Protection extension:** `extend_protection` موجود بالاسم والتوقيع الكنسي، لكن body الحالي يرفع `UNRESOLVED`، لذلك لا يُسمح للعميل بعرض نجاح أو إرسال تأكيد تنفيذي.

### `DATABASE_CONTRACT_GAP`

1. V7 يطلب تعديل علاقة الرقم وأرشفتها في C06، لكن SQL الحالي لا يعرّف RPC كنسيًا لـ update/archive؛ Customer يعرض السبب ولا يستدعي RPC قديمًا.
2. V7 يعرّف أقسام إعدادات الحساب والأمان في C13، لكن SQL الحالي لا يعرّف جداول أو RPC آمنًا لتغييرها؛ لذلك تظهر كحالة غير مدعومة دون أفعال وهمية.

### `UNRESOLVED`

- لا توجد إضافة خارج V7. أي سلوك غير مغطى بعقد V7 أو SQL لم يُخترع له API داخل Customer.

## التحقق

- فحص ثابت: كل `gateway.rpc(...)` المستخدم في Customer معرف في `AMAN_V7_DATABASE.sql`؛ لا توجد استدعاءات RPC قديمة مثل `submit_points_purchase` أو `update_customer_number` أو `archive_customer_number`.
- `git diff --check`: مطلوب تشغيله قبل commit النهائي.
- اختبار Gradle: تعذر الوصول إلى مرحلة Kotlin/Android لأن Plugin `com.android.application:8.7.3` غير متوفر في بيئة Gradle offline الحالية؛ هذه قيد بيئي، وليست نتيجة اختبار فاشل من كود Customer.
- لم يتم تشغيل SQL على Supabase حقيقية، وفق قيد مرحلة قاعدة البيانات السابقة؛ لا يُدّعى اتصال إنتاجي أو APK صالح للنشر.
