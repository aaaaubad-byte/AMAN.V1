# AMAN | أمان — FINAL MASTER REFERENCE — CUSTOMER + ADMIN + DATABASE

**الحالة: FINAL / CLOSED IMPLEMENTATION REFERENCE**

هذا الملف هو المرجع التنفيذي الموحد لتطبيق AMAN، ويجمع في مرجع واحد:

1. واجهة العميل Customer.
2. واجهة الإدارة Admin.
3. القواعد المشتركة والتنقل وحالات العرض.
4. العمليات التجارية والحالات.
5. بنية قاعدة البيانات والعلاقات والعقود.
6. RPC / RLS / RBAC / المعاملات / Idempotency / Audit / Ledger / Notifications.
7. التتبع العكسي من الواجهة إلى قاعدة البيانات.

**قاعدة حاكمة:** لا يجوز للتنفيذ اختراع عنصر أو فلتر أو حالة أو قاعدة أعمال غير مثبتة في هذا المرجع. وعند وجود اختلاف بين مصادر سابقة، يكون هذا المرجع هو نقطة التنفيذ النهائية بعد قرارات الإغلاق الواردة فيه.

---

## قاعدة واجهة مهمة — البحث والفلترة والتبديل التلقائي

البحث والفلترة والتبديل التلقائي **مكونات تشغيلية مشتركة** في معظم شاشات القوائم والتشغيل لدى العميل والإدارة.

لكن **«حسب الحاجة» تعني حسب طبيعة الشاشة والبيانات المعروضة، وليس أن المكوّن نادر أو اختياري من حيث المبدأ**.

- **البحث:** يظهر في القوائم والشاشات التشغيلية التي تستفيد من الوصول إلى سجل محدد، ويُحدد نطاق البحث وفق حقول الشاشة.
- **الفلترة:** تظهر في الشاشات التي تحتوي بيانات قابلة للتصنيف، وتُبنى خياراتها حصريًا من نوع البيانات والحالات الموجودة في تلك الشاشة. لا توجد قائمة فلاتر عامة تُنسخ آليًا إلى جميع الشاشات.
- **التبديل التلقائي:** يظهر في الشاشات التشغيلية المتسلسلة التي تحتاج الانتقال التلقائي بين السجلات، وتكون خياراته وسلوكه محددين بحسب طبيعة المعالجة. وهو لا ينفذ عملية حساسة أو سدادًا خارجيًا تلقائيًا نيابة عن المستخدم.
- **بيئة العمل الثابتة:** إذا كانت الشاشة تحتوي بيئة عمل، تبقى ثابتة أعلى القائمة، بينما تتحرك القائمة السفلية.
- **الشاشات الثابتة** مثل الدخول والاستعادة والحساب وعن أمان لا تُجبر على إظهار شريط بحث/فلترة لمجرد أن المكوّن مشترك.
- **شاشات التقارير والبحث المخصص** لها أدواتها الخاصة، ولا تُعامل كقوائم تشغيلية عادية.

مثال: فلترة شاشة العملاء تكون حسب بيانات العملاء وحالاتهم، وفلترة شاشة الأرقام حسب الشركة وحالة الرقم والحماية، وفلترة شاشة المهام حسب الشركة والنوع والحالة والاستحقاق. **لا يجوز نقل فلاتر شاشة إلى شاشة أخرى بلا أساس وظيفي.**

---

# AMAN | أمان — FINAL MASTER REFERENCE — CUSTOMER + ADMIN + DATABASE

> **SSOT شجري:** واجهة → عنصر → حقل/زر → حالة → إجراء → منطق عمل → عقد Backend → جدول/عمود → Ledger/Audit/Notification → Cache/Refresh → Test.
> المصادر: مراجع V10/FINAL المرفوعة. `GAP` = غير محسوم في المصدر ولا يجوز اختراعه.

## 0. ROOT
```text
AMAN
├─ CORE
├─ CUSTOMER C01–C20
├─ ADMIN A01–A20
├─ SHARED
├─ DOMAIN
├─ DATABASE
├─ TRACEABILITY
└─ TESTS
```

# 1. CORE
```text
USER → customer_profile(account_type=USER)
SUBSCRIBER → subscriber_identity بعد تحقق شرط الاشتراك
NUMBER: غير نشط → نشط → منتهي → تجديد → نشط
PROTECTION: تفعيل / تمديد / تجديد؛ لا إلغاء من واجهة العميل
POINTS: package → purchase(PENDING) → approval → ledger → balance
TASKS: plan → periodic_task → schedule_history → execution
A04 = كل المهام والخطط والتاريخ
A09 = المهام داخل نافذة السداد فقط؛ لا تنفيذ سداد خارجي تلقائي
```

# 1.1 UI LAYOUT DECISION — شريط الأدوات التشغيلي

> **قرار مغلق ونهائي:** البحث + الفلترة + التبديل التلقائي هي **مكوّنات تشغيلية مشتركة في معظم شاشات القوائم والتشغيل في تطبيق العميل والإدارة**. لا يجوز حذفها لمجرد أن عقد الشاشة المختصر لم يكررها حرفيًا. الاستثناء هو الشاشات غير القائمة/غير التشغيلية مثل التهيئة، تسجيل الدخول، الحساب، حول أمان، وبعض النماذج/التقارير التي لها أدواتها الخاصة.

## القاعدة العامة

```text
الشاشة التشغيلية ذات القائمة
↓
العنوان
↓
صف الأدوات التشغيلي
├─ بحث
├─ فلترة
└─ تبديل تلقائي
↓
بيئة العمل العلوية الثابتة — إذا كانت الشاشة تحتوي بيئة عمل
↓
القائمة السفلية القابلة للتمرير
```

### البحث
- مكوّن موحد في الشاشات التشغيلية ذات القوائم.
- الحالة الطبيعية: أيقونة/زر بحث ضمن صف الأدوات.
- عند التفعيل يتمدد حقل البحث ويصغر ظهور الأدوات الأخرى إلى أيقونات.
- البحث يفلتر **القائمة الحالية** ولا ينشئ مصدر بيانات جديدًا.
- حالات Loading / Empty / No Results / Error مطلوبة عند انطباقها.

### الفلترة
- مكوّن موحد في الشاشات التشغيلية ذات القوائم.
- خيارات الفلترة **تختلف حسب نوع الشاشة وبياناتها**.
- الفلاتر الديناميكية، مثل الشركات والحالات، تأتي من بيانات النظام ولا تكون قائمة ثابتة داخل التطبيق.
- الفلترة تطبق على القائمة الحالية.
- الترتيب يختلف حسب الشاشة: الأحدث/الأقدم/الأقرب للاستحقاق/الأبعد وغيرها.

### التبديل التلقائي
- مكوّن موحد ضمن صف الأدوات في الشاشات التشغيلية التي تعرض سجلات متتابعة للمعالجة.
- الخيارات: **5 ثوانٍ (افتراضي) / 10 / 20 / 30 / مخصص**.
- ينتقل إلى العنصر التالي بعد المدة المحددة وفق حالة الشاشة.
- لا ينفذ العملية نيابة عن المستخدم.
- في الشاشات التي تعتمد تنفيذًا خارجيًا، لا يتم الانتقال باعتبار العملية ناجحة إلا بعد تسجيل النتيجة على الخادم.

### بيئة العمل العلوية الثابتة
- تظهر في الشاشات التي تحتوي قائمة + سجل محدد/مساحة تشغيل.
- **ثابتة ولا تتحرك مع القائمة.**
- تعرض السجل المحدد.
- الحقول في شبكة/أزواج بصرية.
- الإجراءات داخل البيئة.
- القوائم المنسدلة Inline.
- حالات Review / Processing / Success / Error داخل الشاشة.
- القائمة وحدها قابلة للتمرير.
- اختيار عنصر من القائمة يملأ بيئة العمل بالسجل نفسه.
- البحث والفلترة والترتيب تؤثر في القائمة وبالتالي في السجل المحدد.

## تطبيق القرار حسب نوع الشاشة

| نوع الشاشة | بحث | فلترة | تبديل تلقائي | بيئة عمل ثابتة |
|---|---|---|---|---|
| قائمة تشغيلية للعميل | **نعم غالبًا** | **نعم غالبًا** | **حسب تسلسل المعالجة** | إذا كان فيها سجل/إجراء |
| قائمة تشغيلية للإدارة | **نعم غالبًا** | **نعم غالبًا** | **نعم في الشاشات التشغيلية المتتابعة** | **نعم عند وجودها** |
| شاشة بحث مخصصة C16/A12 | بحث مدمج خاص | حسب نوع النتائج | لا | لا |
| شاشة تقارير C17/A13 | لا كبحث عام | **أدوات تقرير خاصة** | لا | حسب التقرير |
| نموذج إدخال/عملية منفردة | حسب مصدر الاختيار | حسب مصدر الاختيار | لا كقاعدة | حسب العملية |
| تهيئة/دخول/استعادة/حساب/حول | لا | لا | لا | لا |

## شاشات موثقة صراحةً في المرجع
- **C06 شراء النقاط:** بحث + فلترة.
- **C07 حركة النقاط:** بحث + فلترة + قائمة + بيئة عمل.
- **C08 الأرقام المضافة:** بحث + فلترة.
- **C11 تفعيل الحماية:** بحث + فلترة + مصدر أرقام + بيئة عملية.
- **A09 السداد الدوري:** بحث + فلترة + ترتيب الاستحقاق + تبديل تلقائي + بيئة عمل ثابتة.
- القالب المشترك في المرجع يعرّف `[بحث] [فلترة] [التبديل الآلي]` كمكوّن صف أدوات للشاشات التشغيلية.

> **ممنوع تفسير عبارة «عند الحاجة» على أنها «نادر». المقصود أن الصف مشترك ويظهر وفق نوع الشاشة؛ أما معظم شاشات القوائم والتشغيل فتعتمد هذا الصف.**

# 2. CUSTOMER TREE

## C01 التهيئة
```text
UI: شعار + اسم أمان + loading
A: initialize → session check → settings → account state
NAV: no/expired session→C02 | valid→C04
DB: AUTH/IDENTITY read
STATE: loading/error/session
```

## C02 تسجيل الدخول
```text
F: email, password
B: تسجيل الدخول | إنشاء حساب→C03 | نسيت كلمة المرور→C20
A: Auth login
DB: DB-AUTH-01 + DB-ID-01
STATE: idle/loading/error/success
RULE: generic auth error
```

## C03 إنشاء حساب
```text
F: name, email, password, confirm password
CONSENT: terms, privacy
B: إنشاء الحساب
A: Auth + profile provisioning
DB: DB-AUTH-01 + DB-ID-01 + DB-CONT-01(content)
STATE: validation/loading/success/error
RULE: account=USER؛ لا subscriber عند التسجيل
```

## C04 الرئيسية
```text
HEADER: title + notifications→C14
BALANCE: points_balance
GRID: C05 إضافة رقم | C06 شراء نقاط | C07 حركة نقاط
      C08 أرقام مضافة | C09 أرقام نشطة | C10 أرقام منتهية
      C11 تفعيل | C12 تمديد | C13 تجديد
LATEST: انعكاس أحداث وليس جدولًا مستقلًا
NAV: C15 تواصل | C16 بحث | Home | C17 تقارير | C18 حساب
DB: DB-PTS-04 + read models
```

## C05 إضافة رقم
```text
F: phone, telecom(auto prefix), status, added_at
B: إضافة | حفظ التعديل | تعديل | حذف | تفعيل→C11
A: normalize → longest prefix → telecom → customer_number
DB: DB-NUM-01/02 + DB-TEL-01/02
RULE: delete only before protection
GAP: same phone may belong to multiple customers؛ لا UNIQUE phone_number_id قبل الحسم
```

## C06 شراء النقاط
```text
LIST: package name/points/price/currency/availability
F: package, points, price, currency, payment method/data, transfer reference
B: شراء
A: validate → snapshot → create PENDING
DB: DB-PTS-01/02/03
RULE: لا points/ledger credit عند PENDING
```

## C07 حركة النقاط
```text
LIST: direction, amount, date, status, reference
DETAIL: type/action/amount/before/after/date/status/reference/description
DB: DB-PTS-05
RULE: historical ledger immutable
```

## C08 الأرقام المضافة
```text
READ-ONLY
CARD: number/company/status=UNACTIVE/added_at
B: edit→C05 | delete | activate→C11
EMPTY: إضافة رقم→C05
DB: DB-NUM-02→DB-NUM-01→telecom
```

## C09 الأرقام النشطة
```text
CARD: number/company/status=ACTIVE/activation/end/duration/days remaining
B: تمديد→C12
DB: DB-PROT-01/02 + DB-NUM-02
STATE: عند الانتهاء يظهر في C10 وفق server state
```

## C10 الأرقام المنتهية
```text
CARD: number/company/status=EXPIRED/previous activation/previous end
B: تجديد→C13
EMPTY: لا توجد أرقام منتهية
DB: DB-PROT-01/02/04
```

## C11 تفعيل الحماية
```text
SOURCE: customer_number غير نشط
F: number, company, status, duration, tariff, cost, balance, balance-after,
   activation-preview, expiry-preview
B: + new context | تفعيل الحماية
A/OP-05: ownership → unactive → company → duration → tariff → balance
DB: DB-NUM-02 + DB-TEL-02/03 + DB-PTS-04/05 + DB-PROT-01/02
    + DB-OPS-01 + DB-AUD-01 + DB-NOT-01/02
ATOMIC: debit + protection + history/operation/audit/notification
CONFLICT: if ACTIVE→C12; if EXPIRED→C13
```

## C12 تمديد الحماية
```text
SOURCE: ACTIVE eligible
F: number/company/status/start/current end/current duration/days,
   extension duration/tariff/cost/balance/balance-after/new-end-preview
B: تمديد الحماية
OP-06: validate → atomic debit + extension + new end → history/audit/notification
DB: DB-PROT-02/03 + DB-PTS-04/05 + DB-OPS-01 + DB-AUD-01 + DB-NOT
```

## C13 تجديد الحماية
```text
SOURCE: C10/EXPIRED
F: number/company/status/previous start/end/duration,
   renewal duration/tariff/cost/balance/balance-after/new start/end preview
B: تجديد الحماية
OP-07: validate → atomic debit + new protection period → preserve history
DB: DB-PROT-02/04 + DB-PTS-04/05 + DB-OPS-01 + DB-AUD-01 + DB-NOT
STATE: EXPIRED→ACTIVE
```

## C14 إشعارات النظام
```text
SOURCES: purchase, protection, points, expiry/reminder, admin message, support message
CARD: type/title/body/date/time/read/reference
A: open source context
DB: DB-NOT-01/02
RULE: notification ≠ source business record
```

## C15 تواصل أمان
```text
SUPPORT: conversations → new request(subject,message) → send → conversation → close
ADMIN MSG: title/content/date/read
DB: DB-COM-01/02/03/04
RULE: support ≠ admin message ≠ notification
```

## C16 البحث
```text
F: query | B: search | filters | results
STATE: idle/loading/results/empty/error
SCOPE: customer-authorized data only
DB: authorized read/query models
```

## C17 التقارير
```text
F: report type/period/filters
DOMAINS: points/purchases/protection/numbers/activity
STATE: loading/results/empty/error
DB: reporting/read models + authoritative data
```

## C18 الحساب
```text
F: name(edit), email(read-only), User ID(read/copy)
B: save | copy ID | local/security settings
DB: DB-ID-01 + Auth
```

## C19 عن أمان
```text
SECTIONS: ما هو أمان | الشروط | الخصوصية
NAV: horizontal sections + vertical content
DB: DB-CONT-01 when dynamic
```

## C20 استعادة الحساب
```text
F: name, email, User ID
B: recovery/login path
RULE: generic failure; no field-validity disclosure
DB: Auth + identity
```

# 3. ADMIN TREE

## A01 الرئيسية
```text
GRID: A02 العملاء | A03 الأرقام | A04 المهام | A05 الشركات | A06 الباقات
      A07 الدفع | A08 شراء النقاط | A09 السداد الدوري | A10 المالية
NAV: A11 تواصل | A12 بحث | Home | A13 تقارير | A14 حساب
LATEST: انعكاس زمني للمصادر الأصلية
```

## A02 إدارة العملاء
```text
DATA: name/username/phone/email/account ID/created/subscriber date/type/status/points
SUMMARY: numbers/active/expired/protection/last important operation
B: edit | suspend/unban | open numbers/points/purchases/protection/tasks | contact | notify | admin grant | copy
DB: customer_profile/subscriber_identity/customer_number/points_balance/purchase
RULE: no hard delete; no arbitrary USER→SUBSCRIBER
```

## A03 إدارة الأرقام
```text
LIST: number/company/customer/status/added/protection/end
WORKSPACE: number/company/customer/status/add date/protection/tariff snapshot/days/task plan
B: permitted edit/delete/copy/open customer/protection/tasks/company
DB: DB-NUM + DB-TEL + DB-PROT + DB-TASK
RULE: no main activation flow here
```

## A04 إدارة المهام / خطط المهام
```text
LIST: task no/number/customer/company/type/due/value/status/X-of-Y
WORKSPACE: task/number/customer/company/protection/start/end/duration/due/value/status,
           created/actual execution/schedule/previous-next/execution history
B: view | permitted edit | reschedule | cancel(reason required) | rebuild plan | open linked records | copy
DB: DB-TASK-01/02/03/04
RULE: history not rewritten; cancel has reason; no hard delete of operational history
```

## A05 إدارة الشركات
```text
COMPANY: name/short name/code/status/prefixes/length
TARIFF: DAILY/WEEKLY/MONTHLY/YEARLY
TASK CONFIG: interval/amount/currency/visibility/reschedule/post-expiry/grace/
            first-task activation/renewal
B: add/edit/save/enable/disable
DB: DB-TEL-01/02/03/04
GAP: exact tariff formula if not closed later
```

## A06 إدارة الباقات
```text
F: name/points/price/currency/order/visible/active
B: add/edit/save/show-hide/enable-disable/open purchases/finance
DB: DB-PTS-01
RULE: purchases use package snapshot
```

## A07 إدارة الدفع
```text
F: name/type/receiving account/instructions/order/visible/active
B: add/edit/save/show-hide/enable-disable
DB: DB-PTS-02
RULE: hide ≠ delete history
```

## A08 طلبات شراء النقاط
```text
F: purchase no/customer/package/points/price/payment/reference/date/status/rejection reason
STATE: PENDING/APPROVED/REJECTED
B: approve | reject
OP-03 approve: permission → pending → transaction → APPROVED → points ledger credit → balance
             → subscriber transition if rule → operation → audit → notification → refresh
OP-04 reject: permission → pending → reason → REJECTED → no points → operation/audit/notification
DB: DB-PTS-03/04/05 + DB-OPS-01/02 + DB-AUD-01 + DB-NOT-01/03/04
```

## A09 السداد الدوري
```text
PURPOSE: tasks inside payment/visibility window only
FILTER: search/company/due-date asc/desc/nearest
F: number/company/task X-of-Y/value/actual due date
B: copy number | schedule | execute
COPY: clipboard only; no state change
SCHEDULE OP-09: permission → validate → update schedule → history → visibility → audit → refresh
EXECUTE OP-08: copy → external payment → return → execute → validate current task → task_execution
              → operation/idempotency → audit → refresh → next task auto-fill
EARLY EXECUTION: actual execution becomes next-schedule anchor
AUTO SWITCH: 5/10/20/30/custom; default 5 sec; never performs external payment
DB: DB-TASK-02/03/04 + DB-OPS-01/02 + DB-AUD-01
```

## A10 دفتر المالية
```text
READ: point sales income/task costs/expenses/balance by currency/net/source/reference
EXPENSE F: type/amount/currency/description/optional ref
ADMIN POINT GRANT: customer + approved package → points ledger
DB: DB-FIN-01/02/03/04/05 + DB-PTS-05 when points grant
RULE: posted financial entry corrected by new linked correction, not silent edit
```

## A11 تواصل أمان
```text
SUPPORT: requests/conversations/messages/close
ADMIN MSG: threads/messages/date
ADMIN NOTIFICATIONS: campaigns/recipients/status
DB: DB-COM-01/02/03/04 + DB-NOT-03/04
RULE: support/message/notification separate domains
```

## A12 البحث
```text
DOMAINS: customers/numbers/protection/companies/purchases/tasks/periodic payment/operations/finance/conversations
F: query/type/filters
A: search → result → open original entity by type+id
DB: authorized read models/tables
```

## A13 التقارير
```text
DOMAINS: customers/numbers/points/protection/tasks/periodic payment/purchases/finance/operations
FLOW: type → period → filters → execute → results → export if permitted
DB: reporting/read models
```

## A14 الحساب
```text
DATA: admin name/role/profile/account/security/system info
B: permitted edit/security/logout
DB: admin_identity + auth + RBAC
```

## A15 عن أمان
```text
SECTIONS: ما هو أمان | الشروط | الخصوصية
DB: DB-CONT-01
```

## A16 التهيئة
```text
checks: app/session/connectivity/local storage/settings
→ determine next route
DB: system/config as authorized
```

## A17 تسجيل الدخول
```text
F: email/password
B: login | recovery→A19 | registration→A18
AUTH → admin_identity active → role → permission
```

## A18 إنشاء الحساب
```text
Auth registration → auth_account → admin_identity → role assignment only when authorized
RULE: registration alone does not grant admin privilege
```

## A19 استعادة الحساب
```text
verify → reset password → success/failure generic
```

## A20 كتابة إشعار إداري
```text
F: title/content/type/target
TARGETS: users/subscribers/all/all subscribers/active/expired/need extension/need renewal/dynamic segment/specific customer(s)
B: preview | send | cancel
SEND: permission → validate → resolve recipients → campaign → recipients → event → audit → result
DB: DB-NOT-03/04 + DB-NOT-01 + DB-AUD-01
```

# 4. SHARED TREE
```text
SHARED
├─ AUTH: session → auth_account → profile/identity
├─ RBAC: role → permissions → server revalidation
├─ NAV: entity_type + entity_id + source + target + context + permission
├─ NOTIFICATION: event → recipient record → read state
├─ CACHE: server read → local cache → UI
├─ MUTATION: server success only; cache never proves success
├─ RETRY: idempotency key + explicit status
└─ CONFLICT: reject → authoritative refresh → valid action
```

# 5. DOMAIN TREE
```text
IDENTITY
└─ auth_account → customer_profile/subscriber_identity/admin_identity → RBAC
TELECOM
└─ phone → longest prefix → company → tariff/config
NUMBERS
└─ phone_number ↔ customer_number ↔ customer
POINTS
└─ package → purchase → approval → ledger → balance
PROTECTION
└─ customer_number → protection_identity → period → extension/history
TASKS
└─ plan → periodic_task → schedule_history → execution
FINANCE
└─ account → financial_ledger → expense/correction
COMMUNICATION
└─ support conversation/message | admin thread/message
NOTIFICATIONS
└─ event → customer notification | admin campaign/recipient
```

# 6. DATABASE TREE
```text
POSTGRESQL
├─ AUTH & IDENTITY
│  ├─ DB-AUTH-01 auth_account: id, auth_user_id UNIQUE, created_at, updated_at
│  ├─ DB-ID-01 customer_profile: auth_account_id, public_user_code, name, email, account_type, account_status
│  ├─ DB-ID-02 subscriber_identity: customer_profile_id UNIQUE, public_subscriber_code, activated_at
│  ├─ DB-ID-03 admin_identity: auth_account_id UNIQUE, admin_code, status
│  ├─ DB-RBAC-01 roles
│  ├─ DB-RBAC-02 permissions(domain/action)
│  ├─ DB-RBAC-03 role_permissions
│  └─ DB-RBAC-04 admin_assignments
│
├─ TELECOM
│  ├─ DB-TEL-01 telecom_company: code/name/status
│  ├─ DB-TEL-02 telecom_prefix: company_id/prefix/status
│  ├─ DB-TEL-03 protection_tariff: company/mode/rate/currency/effective dates/status
│  └─ DB-TEL-04 task_configuration: company/interval/amount/currency/visibility/reschedule/post-expiry/grace/first-task rules
│
├─ NUMBERS
│  ├─ DB-NUM-01 phone_number: normalized_phone UNIQUE/display_phone/company_id/status
│  └─ DB-NUM-02 customer_number: customer_id/phone_number_id/public code/status/added_at
│
├─ POINTS
│  ├─ DB-PTS-01 points_package: code/name/points/price/currency/order/visible/active
│  ├─ DB-PTS-02 payment_method: code/name/receiving_account/instructions/order/visible/active
│  ├─ DB-PTS-03 points_purchase: customer/package/payment + snapshots + reference/status/review
│  ├─ DB-PTS-04 points_balance: customer UNIQUE/balance
│  └─ DB-PTS-05 points_ledger: customer/direction/amount/before/after/type/source/description/created_by
│
├─ PROTECTION
│  ├─ DB-PROT-01 number_protection_identity
│  ├─ DB-PROT-02 protection_period
│  ├─ DB-PROT-03 protection_extension
│  └─ DB-PROT-04 protection_operation_history
│
├─ TASKS
│  ├─ DB-TASK-01 task_plan
│  ├─ DB-TASK-02 periodic_task
│  ├─ DB-TASK-03 task_schedule_history
│  └─ DB-TASK-04 task_execution
│
├─ FINANCE
│  ├─ DB-FIN-01 financial_ledger
│  ├─ DB-FIN-02 expense
│  ├─ DB-FIN-03 expense_type
│  ├─ DB-FIN-04 financial_correction
│  └─ DB-FIN-05 financial_account
│
├─ COMMUNICATION
│  ├─ DB-COM-01 support_conversation
│  ├─ DB-COM-02 support_message
│  ├─ DB-COM-03 admin_message_thread
│  └─ DB-COM-04 admin_message
│
├─ NOTIFICATIONS
│  ├─ DB-NOT-01 notification_event
│  ├─ DB-NOT-02 customer_notification
│  ├─ DB-NOT-03 admin_notification_campaign
│  └─ DB-NOT-04 admin_notification_recipient
│
├─ OPERATIONS
│  ├─ DB-OPS-01 operation
│  └─ DB-OPS-02 operation_idempotency
├─ AUDIT
│  └─ DB-AUD-01 audit_log
├─ CONTENT
│  └─ DB-CONT-01 app_content
├─ SYSTEM
│  └─ DB-SYS-01 maintenance_config + operational settings
└─ REPORTING/SEARCH
   └─ read models; never replace authoritative tables
```

# 7. DATABASE RELATIONSHIP TREE
```text
auth_account
├─ customer_profile ─ subscriber_identity
└─ admin_identity ─ admin_assignments ─ roles ─ role_permissions ─ permissions

customer_profile
├─ customer_number ─ phone_number ─ telecom_company
│                         ├─ telecom_prefix
│                         ├─ protection_tariff
│                         └─ task_configuration
├─ points_purchase ─ package/payment_method
├─ points_balance
├─ points_ledger
├─ protection_identity ─ protection_period ─ protection_extension/history
├─ task_plan ─ periodic_task ─ schedule_history/execution
├─ support_conversation ─ support_message
├─ admin_message_thread ─ admin_message
└─ customer_notification

operation ─ operation_idempotency
operation ─ audit_log
financial_account ─ financial_ledger ─ expense/correction
notification_event ─ customer_notification/admin campaign/recipient
```

# 8. OPERATION TREE
```text
OP-01 Add Number
C05 → normalize/prefix → customer_number → operation/audit → refresh

OP-02 Submit Purchase
C06 → validate → snapshots → PENDING → notification/refresh

OP-03 Approve Purchase
A08 → permission/PENDING → atomic APPROVED + points_ledger + balance + subscriber rule + operation/audit/notification

OP-04 Reject Purchase
A08 → permission/PENDING/reason → REJECTED + operation/audit/notification

OP-05 Activate
C11 → ownership/state/tariff/balance → atomic protection + points debit + history + operation/audit/notification

OP-06 Extend
C12 → active eligibility/tariff/balance → atomic debit + extension + history + operation/audit/notification

OP-07 Renew
C13 → expired/tariff/balance → atomic debit + new period + preserve history + operation/audit/notification

OP-08 Execute Task
A09 → permission/current state/idempotency → task_execution → state → operation/audit → refresh

OP-09 Reschedule
A04/A09 → permission/state → schedule update + history → visibility → audit

OP-10 Cancel Task
A04 → permission/cancellable → reason required → CANCELLED + history/audit; no hard delete
```

# 9. REVERSE TRACEABILITY
```text
DB-AUTH-01 → C01/C02/C03/C20 + A16/A17/A18/A19
DB-ID-01 → C03/C04/C18 + A02/A12/A13
DB-NUM-02 → C05/C08/C09/C10/C11/C12/C13 + A02/A03/A04
DB-PTS-03 → C06/C14/C17 + A08/A02/A12/A13
DB-PTS-04 → C04/C06/C11/C12/C13 + A02
DB-PTS-05 → C07/C14/C17 + A08/A10/A13
DB-PROT-* → C09/C10/C11/C12/C13/C14 + A02/A03/A04/A13
DB-TASK-* → A04/A09/A13 + customer-facing C14/C17 where applicable
DB-FIN-* → A10/A13
DB-COM-* → C15/A11
DB-NOT-* → C14/A20/A11
DB-AUD-01 → every sensitive mutation
DB-OPS-* → every retryable/sensitive mutation
DB-CONT-01 → C19/A15
```

# 10. FINAL RULES / GAPS
```text
NO dead buttons
NO placeholder business data
NO fake success
NO client-authoritative balance/state
NO sensitive mutation without server authorization
NO hard-delete operational history
NO historical rewrite from current configuration
NO notification as substitute for source record
NO ledger as substitute for business record
NO invented business rule
```

Known `GAP` markers from FINAL contracts must be resolved before final SQL where applicable:
```text
1. exact tariff calculation for each tariff mode
2. additional subscriber_identity fields if any
3. final policy for same phone linked to multiple customers
4. final points_ledger entry_type enumeration
5. remaining financial-accounting details explicitly marked unresolved in Foundation
```

# 11. FINAL BUILD TREE
```text
CORE decisions
→ CUSTOMER C01–C20
→ ADMIN A01–A20
→ SHARED contracts
→ DOMAIN rules
→ DATABASE schema/relations
→ RPC/RLS/transactions/idempotency
→ bidirectional traceability
→ Customer implementation
→ Admin implementation
→ tests
→ SQL-from-zero verification
→ APK build
```

**END — AMAN FINAL TREE REFERENCE COMPACT**


---

# 12. FINAL DATABASE CONTRACT — TABLE BY TABLE

# AMAN \| أمان

# FINAL --- DATABASE CONTRACT

## TABLE-BY-TABLE CANONICAL CONTRACT

### المرحلة التالية بعد FINAL DATABASE ARCHITECTURE

**الحالة:** DATABASE CONTRACT DRAFT --- قبل SQL\
**القاعدة:** لا SQL نهائي قبل إغلاق هذا العقد.

------------------------------------------------------------------------

# 0. الغرض

هذا الملف يحول المعمارية الجديدة إلى عقد قاعدة بيانات تفصيلي.

لا يعتمد العقد على C01--C20 أو A01--A20 كأسماء جداول.

القاعدة:

``` text
UI Screen
→ Business Operation
→ Domain Entity
→ Database Contract
```

وليس:

``` text
Screen ID
→ Table Name
```

------------------------------------------------------------------------

# 1. أنواع المفاتيح

كل كيان تجاري يستخدم داخليًا:

``` text
UUID primary key
```

وعند الحاجة إلى معرف قابل للعرض:

``` text
internal_id
+
public_code
```

لا يستخدم public_code كمفتاح FK داخلي.

**GAP:** الصيغة النهائية لكل Public ID لم تُغلق بعد.

------------------------------------------------------------------------

# 2. قواعد عامة لكل جدول

كل جدول تشغيلي يجب أن يحدد قبل SQL:

``` text
table_name
domain
purpose
owner
primary_key
columns
foreign_keys
unique_constraints
check_constraints
indexes
row_security
authorized_readers
authorized_writers
historical_policy
audit_policy
ledger_policy
notification_policy
```

ولا ينشأ جدول بلا Owner واضح.

------------------------------------------------------------------------

# 3. AUTH & IDENTITY

## DB-AUTH-01 --- auth_account

**Purpose:** ربط حساب AMAN بنظام المصادقة.

**Authoritative source:** Auth system.

**Core fields:**

``` text
id UUID PK
auth_user_id UUID UNIQUE
created_at TIMESTAMPTZ
updated_at TIMESTAMPTZ
```

**Rules:**

-   لا تخزن كلمة المرور هنا.
-   الحساب مرتبط بهوية المصادقة.
-   لا يسمح بتكرار auth identity.

**Writes:**

-   registration flow / backend provisioning.

**Reads:**

-   customer/admin authenticated context.

------------------------------------------------------------------------

## DB-ID-01 --- customer_profile

**Purpose:** الهوية التجارية للمستخدم.

**Core fields proposed:**

``` text
id UUID PK
auth_account_id UUID UNIQUE FK
public_user_code TEXT UNIQUE
name TEXT
email TEXT
account_type ENUM
account_status ENUM
created_at TIMESTAMPTZ
updated_at TIMESTAMPTZ
```

**Account type:**

``` text
USER
SUBSCRIBER
```

**Account status:**

الحالات النهائية تحتاج إغلاقًا؛ المؤكد وجود suspended/unban/release في
متطلبات الإدارة.

**Rules:**

-   customer_profile واحد لكل auth account.
-   لا يغير العميل نفسه إلى SUBSCRIBER مباشرة.
-   انتقال USER → SUBSCRIBER نتيجة عملية شراء نقاط معتمدة وفق قاعدة
    الاشتراك.

------------------------------------------------------------------------

## DB-ID-02 --- subscriber_identity

**Purpose:** تمثيل حالة المشترك بعد تحقق شرط الاشتراك.

**Core fields:**

``` text
id UUID PK
customer_profile_id UUID UNIQUE FK
public_subscriber_code TEXT UNIQUE
activated_at TIMESTAMPTZ
created_at TIMESTAMPTZ
updated_at TIMESTAMPTZ
```

**Rule:**

لا ينشأ لمجرد التسجيل.

ينشأ عندما يتحقق شرط الاشتراك.

**GAP:** هل subscriber identity يحتاج حقولًا إضافية غير الهوية والتاريخ؟
لا يوجد دعم كافٍ لتثبيت أكثر من ذلك.

------------------------------------------------------------------------

## DB-ID-03 --- admin_identity

**Purpose:** هوية الإدارة.

``` text
id UUID PK
auth_account_id UUID UNIQUE FK
admin_code TEXT UNIQUE
status
created_at
updated_at
```

**Security:** لا يكفي وجود auth session؛ يجب أن تكون هوية الإدارة فعالة
ولها صلاحية.

------------------------------------------------------------------------

## DB-RBAC-01 --- roles

``` text
id UUID PK
code TEXT UNIQUE
name TEXT
status
created_at
updated_at
```

## DB-RBAC-02 --- permissions

``` text
id UUID PK
code TEXT UNIQUE
name TEXT
domain TEXT
action TEXT
status
created_at
updated_at
```

## DB-RBAC-03 --- role_permissions

``` text
role_id UUID FK
permission_id UUID FK
PRIMARY KEY(role_id, permission_id)
```

## DB-RBAC-04 --- admin_assignments

``` text
admin_id UUID FK
role_id UUID FK
status
created_at
updated_at
```

**Rule:** كل عملية إدارية حساسة تعيد التحقق من permission على الخادم.

------------------------------------------------------------------------

# 4. TELECOM

## DB-TEL-01 --- telecom_company

``` text
id UUID PK
code TEXT UNIQUE
name TEXT
status
created_at
updated_at
```

**Owner:** Telecom domain.

------------------------------------------------------------------------

## DB-TEL-02 --- telecom_prefix

``` text
id UUID PK
telecom_company_id UUID FK
prefix TEXT
status
created_at
updated_at
```

**Constraints:**

-   prefix غير فارغ.
-   الشركة موجودة.
-   لا يسمح بتكرار Prefix غير المسموح به.

**Detection:**

``` text
normalized_phone
→ longest matching prefix
→ telecom_company
```

------------------------------------------------------------------------

## DB-TEL-03 --- protection_tariff

يمثل تعرفة الحماية التاريخية والحالية.

**Core fields:**

``` text
id UUID PK
telecom_company_id UUID FK
tariff_mode
rate/value fields
currency
effective_from
effective_to
status
created_at
updated_at
```

**Modes المطلوبة:**

``` text
DAILY
WEEKLY
MONTHLY
YEARLY
```

**GAP:** صيغة الحساب الدقيقة لكل mode، وهل جميعها قيمة مباشرة أم تحتاج
conversion rules، يجب إغلاقها قبل SQL.

------------------------------------------------------------------------

## DB-TEL-04 --- task_configuration

إعدادات المهام التابعة للشركة.

**Core fields:**

``` text
id UUID PK
telecom_company_id UUID FK
interval_days
task_amount
currency
visibility_days_before
allow_reschedule
allow_post_expiry_creation
post_expiry_grace_days
create_first_task_on_activation
create_first_task_on_renewal
status
effective_from
effective_to
created_at
updated_at
```

**Historical rule:**

تغيير الإعدادات لا يعيد كتابة المهام التاريخية.

------------------------------------------------------------------------

# 5. NUMBERS

## DB-NUM-01 --- phone_number

الكيان الفعلي للرقم.

``` text
id UUID PK
normalized_phone TEXT UNIQUE
display_phone TEXT
telecom_company_id UUID FK
status
created_at
updated_at
```

**مهم:**

وجود الرقم هنا لا يعني ملكية العميل له.

------------------------------------------------------------------------

## DB-NUM-02 --- customer_number

علاقة العميل بالرقم.

``` text
id UUID PK
customer_id UUID FK
phone_number_id UUID FK
public_added_number_code TEXT UNIQUE
status
added_at
updated_at
```

**Rules:**

-   الرقم المضاف يمكن أن يبقى غير نشط.
-   التعديل/الحذف يخضع لحالة الرقم والحماية.
-   لا يسمح بعملية حماية لرقم غير مرتبط بالعميل المنفذ.

**GAP:** هل نفس الرقم يمكن أن يرتبط في الوقت نفسه بأكثر من عميل؟
المتطلبات الحالية تشير إلى إمكانية إضافة رقم بواسطة أكثر من مستخدم، لذلك
يجب عدم وضع UNIQUE على phone_number_id داخل customer_number إلا إذا تم
حسم خلاف ذلك.

------------------------------------------------------------------------

# 6. POINTS

## DB-PTS-01 --- points_package

``` text
id UUID PK
code TEXT UNIQUE
name TEXT
points BIGINT
price NUMERIC
currency
display_order INTEGER
is_visible BOOLEAN
is_active BOOLEAN
created_at
updated_at
```

**Historical:** الطلب يحفظ Snapshot.

------------------------------------------------------------------------

## DB-PTS-02 --- payment_method

``` text
id UUID PK
code TEXT UNIQUE
name TEXT
receiving_account TEXT
transfer_instructions TEXT
display_order INTEGER
is_visible BOOLEAN
is_active BOOLEAN
created_at
updated_at
```

إخفاء وسيلة الدفع لا يحذف بيانات الطلبات التاريخية.

------------------------------------------------------------------------

## DB-PTS-03 --- points_purchase

``` text
id UUID PK
public_purchase_code TEXT UNIQUE
customer_id UUID FK
package_id UUID FK
payment_method_id UUID FK

package_name_snapshot
points_snapshot
price_snapshot
currency_snapshot

payment_method_name_snapshot
receiving_account_snapshot
payment_instructions_snapshot

transfer_reference TEXT

status
rejection_reason TEXT NULL

submitted_at
reviewed_at
reviewed_by UUID NULL

created_at
updated_at
```

**States:**

``` text
PENDING
APPROVED
REJECTED
```

**Rules:**

-   لا تضاف النقاط عند PENDING.
-   APPROVED مرة واحدة.
-   REJECTED لا يضيف نقاطًا.
-   البيانات التاريخية لا تعتمد على القيم الحالية للباقة/الدفع.

------------------------------------------------------------------------

## DB-PTS-04 --- points_balance

``` text
id UUID PK
customer_id UUID UNIQUE FK
balance BIGINT
updated_at
```

**Rules:**

-   رصيد واحد authoritative لكل عميل.
-   لا تعديل مباشر من Android.
-   كل تغيير مرتبط بحركة ledger.

------------------------------------------------------------------------

## DB-PTS-05 --- points_ledger

``` text
id UUID PK
customer_id UUID FK
direction
amount BIGINT
balance_before BIGINT
balance_after BIGINT
entry_type
source_type
source_id UUID NULL
description TEXT NULL
created_by UUID NULL
created_at
```

**Candidate entry types:**

``` text
PURCHASE_CREDIT
ACTIVATION_DEBIT
EXTENSION_DEBIT
RENEWAL_DEBIT
ADMIN_GRANT
CORRECTION
```

**GAP:** القائمة النهائية للأنواع تحتاج اعتمادًا.

**Rule:** لا حذف لحركة تاريخية.

------------------------------------------------------------------------

# 7. PROTECTION

## DB-PROT-01 --- number_protection_identity

هوية العلاقة التشغيلية طويلة الأجل بين الرقم والحماية.

``` text
id UUID PK
customer_number_id UUID UNIQUE FK
public_activation_code TEXT UNIQUE
created_at
updated_at
```

**Rule:** ينشأ عند أول تفعيل ناجح، ويظل مرتبطًا بالرقم عبر دورات التجديد.

------------------------------------------------------------------------

## DB-PROT-02 --- protection_period

يمثل فترة حماية فعلية.

``` text
id UUID PK
protection_identity_id UUID FK
customer_id UUID FK
start_at TIMESTAMPTZ
end_at TIMESTAMPTZ
duration_days INTEGER

tariff_id UUID FK
tariff_mode_snapshot
tariff_value_snapshot
currency_snapshot
points_cost_snapshot

status
created_at
updated_at
```

**States:**

``` text
ACTIVE
EXPIRED
```

**Rules:**

-   لا إلغاء حماية.
-   لا أكثر من فترة ACTIVE لنفس protection identity.
-   الانتقال إلى EXPIRED لا ينشئ مهامًا جديدة بعد حد الإنشاء المسموح.

------------------------------------------------------------------------

## DB-PROT-03 --- protection_extension

سجل عملية تمديد.

``` text
id UUID PK
protection_period_id UUID FK
days_added INTEGER
tariff_snapshot
points_cost
balance_before
balance_after
operation_id UUID FK
created_at
```

لا يستبدل protection_period.

------------------------------------------------------------------------

## DB-PROT-04 --- protection_operation_history

اختياري معماريًا.

إذا كان operation قادرًا على حفظ كل التاريخ المطلوب، لا ننشئ هذا الجدول.

**قرار FINAL المبدئي:** عدم إنشاء جدول مكرر قبل إثبات الحاجة.

------------------------------------------------------------------------

# 8. TASKS

## DB-TASK-01 --- task_plan

``` text
id UUID PK
protection_period_id UUID FK
task_configuration_id UUID FK
status
anchor_at
version INTEGER
created_at
updated_at
```

الخطة هي تعريف توليد المهام المستقبلية.

------------------------------------------------------------------------

## DB-TASK-02 --- periodic_task

``` text
id UUID PK
public_task_code TEXT UNIQUE

task_plan_id UUID FK
protection_period_id UUID FK
customer_id UUID FK
customer_number_id UUID FK
telecom_company_id UUID FK

sequence_no INTEGER
planned_total INTEGER NULL

due_at TIMESTAMPTZ
amount NUMERIC
currency

status

executed_at TIMESTAMPTZ NULL
cancelled_at TIMESTAMPTZ NULL
cancellation_reason TEXT NULL

company_snapshot
amount_snapshot
currency_snapshot
interval_snapshot

created_at
updated_at
```

**States:**

``` text
OPEN
COMPLETED
CANCELLED
```

**Rule:**

التنفيذ المبكر يجعل تاريخ التنفيذ الفعلي هو anchor حسب القاعدة المعتمدة.

------------------------------------------------------------------------

## DB-TASK-03 --- task_schedule_history

``` text
id UUID PK
task_id UUID FK
old_due_at
new_due_at
reason
changed_by
created_at
```

لا ينشئ Task جديدًا عند إعادة الجدولة.

------------------------------------------------------------------------

## DB-TASK-04 --- task_execution

``` text
id UUID PK
task_id UUID UNIQUE FK
execution_result
external_reference TEXT NULL
executed_at
executed_by UUID
amount NUMERIC NULL
currency NULL
created_at
```

**Rule:**

مهمة واحدة لا تنفذ مرتين بنجاح.

------------------------------------------------------------------------

# 9. FINANCE

## DB-FIN-01 --- financial_ledger

``` text
id UUID PK
entry_type
direction
amount
currency

source_type
source_id UUID NULL

description
created_by UUID NULL
created_at
```

**Candidate types:**

``` text
POINTS_SALE
TASK_EXPENSE
OPERATING_EXPENSE
CORRECTION
```

**Rule:** السجل المرحل تاريخي وغير قابل للتعديل.

------------------------------------------------------------------------

## DB-FIN-02 --- expense

``` text
id UUID PK
expense_type_id UUID
amount
currency
description
reference TEXT NULL
posted_by UUID
posted_at
created_at
```

------------------------------------------------------------------------

## DB-FIN-03 --- expense_type

``` text
id UUID PK
code UNIQUE
name
status
created_at
updated_at
```

تعطيل النوع لا يغير المصروفات التاريخية.

------------------------------------------------------------------------

## DB-FIN-04 --- financial_correction

``` text
id UUID PK
original_ledger_entry_id UUID FK
correction_ledger_entry_id UUID FK
reason
created_by UUID
created_at
```

**Rule:** التصحيح يضيف حركة جديدة.

------------------------------------------------------------------------

## DB-FIN-05 --- financial_account

**Status:** تصميم مشروط.

لا يُنشأ إلا إذا احتاج النموذج المحاسبي النهائي إلى حسابات متعددة/عملات
متعددة.

**GAP:** لا نثبت وجوده في SQL قبل حسم النموذج المحاسبي.

------------------------------------------------------------------------

# 10. COMMUNICATION

## DB-COM-01 --- support_conversation

``` text
id UUID PK
customer_id UUID FK
subject TEXT NULL
status
created_at
updated_at
closed_at NULL
closed_by NULL
```

**States:**

``` text
OPEN
CLOSED
```

------------------------------------------------------------------------

## DB-COM-02 --- support_message

``` text
id UUID PK
conversation_id UUID FK
sender_type
sender_id UUID
body TEXT
sent_at
read_at NULL
```

------------------------------------------------------------------------

## DB-COM-03 --- admin_message_thread

``` text
id UUID PK
customer_id UUID FK
status
created_at
updated_at
```

------------------------------------------------------------------------

## DB-COM-04 --- admin_message

``` text
id UUID PK
thread_id UUID FK
sender_admin_id UUID
body TEXT
sent_at
read_at NULL
```

------------------------------------------------------------------------

# 11. NOTIFICATIONS

## DB-NOT-01 --- notification_event

يمثل الحدث وليس الرسالة الموجهة.

``` text
id UUID PK
event_type
source_type
source_id
created_at
```

------------------------------------------------------------------------

## DB-NOT-02 --- customer_notification

``` text
id UUID PK
customer_id UUID FK
event_id UUID FK

notification_type
title
body

reference_type NULL
reference_id NULL

is_read BOOLEAN
created_at
read_at NULL
```

**Rule:** notification ليس مصدر الحقيقة للحدث.

------------------------------------------------------------------------

## DB-NOT-03 --- admin_notification_campaign

``` text
id UUID PK
created_by UUID
target_type
target_definition
title
body
status
created_at
sent_at NULL
```

**GAP:** نموذج target_definition يجب أن يغلق قبل SQL.

------------------------------------------------------------------------

## DB-NOT-04 --- admin_notification_recipient

``` text
id UUID PK
campaign_id UUID FK
customer_id UUID FK
delivery_status
read_at NULL
created_at
```

------------------------------------------------------------------------

# 12. OPERATIONS

## DB-OPS-01 --- operation

السجل الموحد للعملية التجارية.

``` text
id UUID PK
operation_key TEXT UNIQUE
operation_type
actor_type
actor_id UUID NULL

entity_type
entity_id UUID

status

idempotency_key TEXT NULL
result_reference TEXT NULL

created_at
completed_at NULL
```

**Candidate operations:**

``` text
ADD_NUMBER
PURCHASE_POINTS
APPROVE_PURCHASE
REJECT_PURCHASE
ACTIVATE_PROTECTION
EXTEND_PROTECTION
RENEW_PROTECTION
EXECUTE_TASK
RESCHEDULE_TASK
CANCEL_TASK
POST_EXPENSE
FINANCIAL_CORRECTION
ADMIN_GRANT
SEND_NOTIFICATION
```

------------------------------------------------------------------------

## DB-OPS-02 --- operation_idempotency

``` text
id UUID PK
actor_id UUID
operation_type
idempotency_key
operation_id UUID FK
created_at
```

**Unique:**

``` text
(actor_id, operation_type, idempotency_key)
```

------------------------------------------------------------------------

# 13. AUDIT

## DB-AUD-01 --- audit_log

``` text
id UUID PK
actor_type
actor_id UUID NULL

operation_id UUID NULL

entity_type
entity_id UUID

action
permission_code NULL

before_state JSONB NULL
after_state JSONB NULL
reason TEXT NULL

created_at
```

**Rules:**

-   append-only.
-   لا يكتب من Android مباشرة.
-   العمليات الحساسة تنشئ Audit داخل نفس transaction.

------------------------------------------------------------------------

# 14. CONTENT

## DB-CONT-01 --- app_content

``` text
id UUID PK
content_key UNIQUE
version INTEGER
title
body
status
created_by
updated_by
created_at
updated_at
published_at NULL
```

**Content keys:**

``` text
ABOUT_AMAN
TERMS
PRIVACY
```

**GAP:** سياسة versioning/publishing النهائية تحتاج حسمًا.

------------------------------------------------------------------------

# 15. SYSTEM

## DB-SYS-01 --- maintenance_config

``` text
id UUID PK
enabled BOOLEAN
customer_message TEXT NULL
updated_by UUID
updated_at
```

**Rule:** سجل singleton منطقي.

------------------------------------------------------------------------

# 16. REPORTING / READ MODELS

لا ننشئ:

``` text
customer_reports
admin_reports
latest_operations
search_results
active_numbers
expired_numbers
```

كجداول مصدر للحقيقة.

بدل ذلك:

``` text
Base tables
→ Authorized queries/views
→ Read models
→ UI
```

------------------------------------------------------------------------

# 17. العلاقات الأساسية

``` text
auth_account
    1 ── 1 customer_profile
    1 ── 1 admin_identity

customer_profile
    1 ── 0..1 subscriber_identity
    1 ── N customer_number
    1 ── 1 points_balance
    1 ── N points_purchase
    1 ── N points_ledger
    1 ── N protection_period
    1 ── N support_conversation
    1 ── N admin_message_thread
    1 ── N customer_notification

phone_number
    1 ── N customer_number

telecom_company
    1 ── N telecom_prefix
    1 ── N protection_tariff
    1 ── N task_configuration

customer_number
    1 ── 0..1 number_protection_identity

number_protection_identity
    1 ── N protection_period

protection_period
    1 ── 0..N protection_extension
    1 ── 0..1 task_plan

task_plan
    1 ── N periodic_task

periodic_task
    1 ── 0..1 task_execution
    1 ── N task_schedule_history

points_package
    1 ── N points_purchase

payment_method
    1 ── N points_purchase

support_conversation
    1 ── N support_message

admin_message_thread
    1 ── N admin_message

admin_notification_campaign
    1 ── N admin_notification_recipient
```

------------------------------------------------------------------------

# 18. العلاقات التي لا نسمح بها

لا يجوز:

``` text
Customer
→ directly UPDATE points_balance

Customer
→ directly INSERT points_ledger

Customer
→ directly UPDATE protection_period

Customer
→ directly INSERT financial_ledger

Admin
→ directly UPDATE historical financial ledger

UI
→ directly mutate critical business state
```

كل ذلك يمر عبر RPC/transaction authorization.

------------------------------------------------------------------------

# 19. RPC Contract --- قائمة أولية

الـRPCs التالية مطلوبة مبدئيًا:

``` text
register_customer
create_customer_profile
submit_points_purchase
approve_points_purchase
reject_points_purchase

add_customer_number
update_customer_number
delete_customer_number

activate_protection
extend_protection
renew_protection

execute_periodic_task
reschedule_periodic_task
cancel_periodic_task

post_expense
post_financial_correction
admin_grant_points

create_support_conversation
send_support_message
close_support_conversation

send_admin_message
mark_notification_read
send_admin_notification

set_maintenance_mode
```

هذه **قائمة Contract أولية وليست SQL signature**.

كل RPC لاحقًا يجب أن يملك:

``` text
rpc_id
caller
permission
input
output
preconditions
validation
transaction
locking
idempotency
reads
writes
ledger
audit
notification
errors
tests
```

------------------------------------------------------------------------

# 20. RLS Contract

## Customer

يقرأ فقط:

``` text
own customer_profile
own customer_number
own points_purchase
own points_balance
own points_ledger
own protections
own support
own notifications
```

ولا يكتب مباشرة في:

``` text
points_balance
points_ledger
protection_period
financial_ledger
audit_log
```

## Admin

الوصول يعتمد على:

``` text
auth
→ admin_identity
→ role
→ permission
→ row scope
```

ولا تعتمد الحماية على RLS وحدها؛ كل RPC حساس يعيد فحص authorization.

------------------------------------------------------------------------

# 21. Transactions

## Purchase Approval

Transaction واحدة:

``` text
lock purchase
→ validate pending
→ validate payment
→ approve purchase
→ financial ledger
→ points ledger
→ points balance
→ subscriber transition
→ operation
→ audit
→ notification
→ commit
```

## Activation

``` text
lock balance
→ validate number
→ calculate cost
→ debit points
→ create protection
→ task plan
→ point ledger
→ operation
→ audit
→ notification
→ commit
```

## Extension

``` text
lock protection
→ lock balance
→ validate active
→ calculate
→ debit
→ extend
→ rebuild future tasks
→ ledger
→ operation
→ audit
→ notification
→ commit
```

## Renewal

``` text
lock number/protection context
→ lock balance
→ validate expired
→ calculate
→ debit
→ create new period
→ determine task anchor
→ rebuild plan
→ ledger
→ operation
→ audit
→ notification
→ commit
```

## Task Execution

``` text
lock task
→ verify OPEN
→ verify idempotency
→ register execution
→ financial ledger if configured
→ complete task
→ update anchor
→ rebuild future
→ operation
→ audit
→ commit
```

------------------------------------------------------------------------

# 22. Historical Immutability

Immutable domains:

``` text
points_ledger
financial_ledger
audit_log
task_execution
purchase approval history
historical snapshots
```

Correction pattern:

``` text
Old record
    ↓
Correction operation
    ↓
New record
```

------------------------------------------------------------------------

# 23. Derived State

لا نكرر الحالة في جداول متعارضة.

## Active Number

يستخرج من:

``` text
protection_period.status = ACTIVE
```

## Expired Number

``` text
protection_period.status = EXPIRED
```

## Added Number

رقم العميل الذي لا توجد له حماية نشطة/حماية فعلية وفق تعريف C08 النهائي.

**GAP:** يجب تثبيت تعريف "الأرقام المضافة" عندما يكون للرقم حماية
تاريخية منتهية؛ هل يبقى في C08 أم ينتقل حصريًا إلى C10؟

## Subscriber

يعتمد على:

``` text
subscriber_identity exists
```

أو customer_profile.account_type بعد تثبيت مصدر الحقيقة النهائي.

------------------------------------------------------------------------

# 24. GAPS التي يجب حسمها قبل SQL

هذه القائمة إلزامية وليست اقتراحات:

### GAP-DB-001

الأسماء النهائية للجداول.

### GAP-DB-002

أنواع كل عمود.

### GAP-DB-003

قواعد NULL/default.

### GAP-DB-004

ENUM values النهائية.

### GAP-DB-005

سياسات FK:

``` text
ON DELETE
ON UPDATE
```

### GAP-DB-006

التعرفة الدقيقة وكيفية تحويلها إلى أيام/تكلفة نقاط.

### GAP-DB-007

هل phone_number يمكن أن يرتبط بأكثر من customer في نفس الوقت، وكيف تكون
الحماية عندها؟

### GAP-DB-008

تعريف "الأرقام المضافة" بعد انتهاء حماية رقم سابق.

### GAP-DB-009

الحالة النهائية للحساب بعد suspension/unban/release/close.

### GAP-DB-010

المعالجة المحاسبية لـ ADMIN_GRANT.

### GAP-DB-011

النطاق النهائي للبحث.

### GAP-DB-012

التقارير النهائية.

### GAP-DB-013

نموذج استعادة الحساب.

### GAP-DB-014

نموذج حملات الإشعارات الإدارية والاستهداف الديناميكي.

### GAP-DB-015

سياسة versioning للمحتوى القانوني.

### GAP-DB-016

تفاصيل task plan rebuild.

### GAP-DB-017

Public ID generation.

### GAP-DB-018

صلاحيات كل RPC بالتفصيل.

------------------------------------------------------------------------

# 25. معيار الانتقال إلى SQL

لا ننتقل إلى SQL حتى يصبح لدينا:

``` text
100% table names
100% columns
100% data types
100% PK
100% FK
100% NULL/default
100% unique/check constraints
100% indexes
100% states
100% RPC signatures
100% RLS
100% permissions
100% ledger effects
100% audit effects
100% notification effects
```

ثم:

``` text
DATABASE CONTRACT
        ↓
SQL MIGRATION 001
        ↓
SQL MIGRATION 002...
        ↓
SEEDS
        ↓
RLS
        ↓
RPC
        ↓
DATABASE TESTS
```

------------------------------------------------------------------------

# 26. تعريف الإغلاق

لا تعتبر قاعدة البيانات FINAL مكتملة لأن:

``` text
الجداول موجودة
```

ولا لأن:

``` text
SQL ينفذ
```

بل فقط عندما يتحقق:

``` text
UI
↕
Business Rule
↕
Operation
↕
RPC
↕
Table
↕
Column
↕
Constraint
↕
Transaction
↕
Ledger
↕
Audit
↕
Notification
↕
State
↕
Refresh
↕
Test
```

وفي الاتجاه العكسي:

``` text
Every Table
→ Owner
→ Business Purpose
→ Operation
→ Screen Consumer
```

أي عنصر بلا مسار = GAP.

وأي جدول بلا مستهلك/مالك واضح = GAP.

وأي زر بلا عملية = GAP.

وأي عملية بلا Transaction = GAP.

وأي Mutation حساسة بلا Audit/Ledger عند الحاجة = GAP.

------------------------------------------------------------------------

# 27. النتيجة الحالية

تم تحويل FINAL DATABASE ARCHITECTURE إلى مستوى **Table-by-Table Contract**
مبدئي.

لكن لم يتم اختراع التفاصيل التي لم تُحسم.

المرحلة التالية ليست كتابة SQL مباشرة.

بل:

``` text
FINAL DATABASE CONTRACT
        ↓
GAP CLOSURE
        ↓
RELATIONSHIP MATRIX
        ↓
COLUMN-BY-COLUMN FINALIZATION
        ↓
RPC CONTRACT
        ↓
RLS/RBAC CONTRACT
        ↓
FINAL SQL
```

**الحالة الحالية: DATABASE CONTRACT DRAFT --- NOT SQL READY.**


---

# FINAL CLOSURE RULE

يُعتبر التنفيذ مطابقًا لهذا المرجع فقط عندما تتطابق السلسلة كاملة:

UI
→ Action
→ State
→ ViewModel / Repository
→ Authorized Query or RPC
→ PostgreSQL
→ Constraint / RLS
→ Transaction / Idempotency
→ Ledger / Audit / Notification عند اللزوم
→ Result
→ Refresh / Cache
→ UI

ولا يُعتبر أي من التالي دليل اكتمال منفردًا:

- وجود الشاشة فقط.
- نجاح البناء فقط.
- وجود RPC فقط.
- وجود جدول فقط.
- نجاح محلي من Cache.

**لا Dead Buttons، لا Placeholder، لا Fake Success، لا Client-Authoritative State، ولا إعادة كتابة للتاريخ التشغيلي.**

# END — AMAN FINAL MASTER REFERENCE
