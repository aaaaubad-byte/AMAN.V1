# Customer V7 — تدقيق تغطية 146 عنصرًا

**المرجع:** `V7.md`، القسم 69 — سجل عناصر الواجهة للعميل
**النطاق:** C01–C15، جميع عناصر Customer canonical IDs
**تاريخ التدقيق:** 2026-10-05

## النتيجة

**تغطية عقدية: PASS — 146/146 عنصرًا canonical موجودة في registry مستقل ومربوطة بنطاق شاشة صحيح.**

تم إنشاء `CustomerV7Elements.kt` كمصدر canonical قابل للاختبار، وتطبيع معرفات الواجهة القديمة إلى IDs V7 عبر `legacyToV7ElementId`. أضيفت assertions تمنع فقدان أو تكرار أي عنصر من قائمة V7 الرسمية.

## التوزيع

| الشاشة | العدد |
|---|---:|
| C01 | 19 |
| C02 | 14 |
| C03 | 6 |
| C04 | 13 |
| C05 | 12 |
| C06 | 10 |
| C07 | 12 |
| C08 | 13 |
| C09 | 7 |
| C10 | 6 |
| C11 | 7 |
| C12 | 5 |
| C13 | 10 |
| C14 | 4 |
| C15 | 8 |
| **الإجمالي** | **146** |

## العناصر canonical

| ID | شاشة |
|---|---|
| `C01.BRAND` | `C01` |
| `C01.CUSTOMER_NAME` | `C01` |
| `C01.NOTIFICATIONS` | `C01` |
| `C01.POINTS_BALANCE` | `C01` |
| `C01.QUICK.ADD_POINTS` | `C01` |
| `C01.QUICK.ACTIVE_NUMBERS` | `C01` |
| `C01.QUICK.INACTIVE_NUMBERS` | `C01` |
| `C01.QUICK.ADD_NUMBER` | `C01` |
| `C01.QUICK.ACTIVATE` | `C01` |
| `C01.QUICK.EXTEND` | `C01` |
| `C01.QUICK.ADMIN_ALERTS` | `C01` |
| `C01.QUICK.OPERATIONS` | `C01` |
| `C01.QUICK.SUPPORT` | `C01` |
| `C01.RECENT_OPERATIONS` | `C01` |
| `C01.NAV.ABOUT` | `C01` |
| `C01.NAV.SEARCH` | `C01` |
| `C01.NAV.HOME` | `C01` |
| `C01.NAV.REPORTS` | `C01` |
| `C01.NAV.ACCOUNT` | `C01` |
| `C02.DETAIL.PHONE` | `C02` |
| `C02.DETAIL.PROVIDER` | `C02` |
| `C02.DETAIL.STATUS` | `C02` |
| `C02.DETAIL.START` | `C02` |
| `C02.DETAIL.END` | `C02` |
| `C02.DETAIL.DURATION` | `C02` |
| `C02.DETAIL.TARIFF` | `C02` |
| `C02.DETAIL.POINTS_USED` | `C02` |
| `C02.DETAIL.REMAINING` | `C02` |
| `C02.TASK_SUMMARY` | `C02` |
| `C02.ACTION.COPY` | `C02` |
| `C02.LIST` | `C02` |
| `C02.LIST.SEARCH` | `C02` |
| `C02.LIST.FILTER` | `C02` |
| `C03.DETAIL.PHONE` | `C03` |
| `C03.DETAIL.PROVIDER` | `C03` |
| `C03.DETAIL.ADDED_AT` | `C03` |
| `C03.DETAIL.STATUS` | `C03` |
| `C03.ACTION.COPY` | `C03` |
| `C03.LIST` | `C03` |
| `C04.PACKAGES` | `C04` |
| `C04.PACKAGE.SELECTED` | `C04` |
| `C04.PACKAGE.NAME` | `C04` |
| `C04.PACKAGE.POINTS` | `C04` |
| `C04.PACKAGE.PRICE` | `C04` |
| `C04.PAYMENT_METHOD` | `C04` |
| `C04.PAYMENT_DATA` | `C04` |
| `C04.PAYMENT_INSTRUCTIONS` | `C04` |
| `C04.REFERENCE` | `C04` |
| `C04.REVIEW` | `C04` |
| `C04.CONFIRM` | `C04` |
| `C04.SUBMIT` | `C04` |
| `C04.STATUS` | `C04` |
| `C05.DETAIL.ID` | `C05` |
| `C05.DETAIL.TYPE` | `C05` |
| `C05.DETAIL.DATETIME` | `C05` |
| `C05.DETAIL.STATUS` | `C05` |
| `C05.DETAIL.POINTS` | `C05` |
| `C05.DETAIL.MONEY` | `C05` |
| `C05.DETAIL.PHONE` | `C05` |
| `C05.DETAIL.PROVIDER` | `C05` |
| `C05.DETAIL.DESCRIPTION` | `C05` |
| `C05.LIST` | `C05` |
| `C05.SEARCH` | `C05` |
| `C05.FILTER` | `C05` |
| `C06.PHONE` | `C06` |
| `C06.PROVIDER` | `C06` |
| `C06.STATUS` | `C06` |
| `C06.ADDED_AT` | `C06` |
| `C06.ADD` | `C06` |
| `C06.SAVE` | `C06` |
| `C06.EDIT` | `C06` |
| `C06.DELETE` | `C06` |
| `C06.COPY` | `C06` |
| `C06.LIST` | `C06` |
| `C07.PHONE` | `C07` |
| `C07.PROVIDER` | `C07` |
| `C07.TARIFF` | `C07` |
| `C07.DURATION_DAYS` | `C07` |
| `C07.COST` | `C07` |
| `C07.BALANCE` | `C07` |
| `C07.BALANCE_AFTER` | `C07` |
| `C07.START` | `C07` |
| `C07.END` | `C07` |
| `C07.REVIEW` | `C07` |
| `C07.CONFIRM` | `C07` |
| `C07.LIST` | `C07` |
| `C08.PHONE` | `C08` |
| `C08.STATUS` | `C08` |
| `C08.CURRENT_END` | `C08` |
| `C08.REMAINING` | `C08` |
| `C08.TARIFF` | `C08` |
| `C08.EXTENSION_DAYS` | `C08` |
| `C08.COST` | `C08` |
| `C08.BALANCE` | `C08` |
| `C08.BALANCE_AFTER` | `C08` |
| `C08.NEW_END` | `C08` |
| `C08.REVIEW` | `C08` |
| `C08.CONFIRM` | `C08` |
| `C08.LIST` | `C08` |
| `C09.DETAIL.TITLE` | `C09` |
| `C09.DETAIL.BODY` | `C09` |
| `C09.DETAIL.DATE` | `C09` |
| `C09.DETAIL.TIME` | `C09` |
| `C09.DETAIL.READ` | `C09` |
| `C09.LIST` | `C09` |
| `C09.LIST_ITEM` | `C09` |
| `C10.SUBJECT` | `C10` |
| `C10.BODY` | `C10` |
| `C10.SEND` | `C10` |
| `C10.THREADS` | `C10` |
| `C10.THREAD.DETAIL` | `C10` |
| `C10.STATUS` | `C10` |
| `C11.DETAIL.TITLE` | `C11` |
| `C11.DETAIL.BODY` | `C11` |
| `C11.DETAIL.DATE` | `C11` |
| `C11.DETAIL.TIME` | `C11` |
| `C11.DETAIL.READ` | `C11` |
| `C11.LIST` | `C11` |
| `C11.OPEN` | `C11` |
| `C12.TYPE` | `C12` |
| `C12.PERIOD` | `C12` |
| `C12.FILTERS` | `C12` |
| `C12.VIEW` | `C12` |
| `C12.RESULTS` | `C12` |
| `C13.FULL_NAME` | `C13` |
| `C13.USERNAME` | `C13` |
| `C13.PHONE` | `C13` |
| `C13.CONTACT` | `C13` |
| `C13.CREATED_AT` | `C13` |
| `C13.STATUS` | `C13` |
| `C13.POINTS_BALANCE` | `C13` |
| `C13.ACCOUNT_SETTINGS` | `C13` |
| `C13.SECURITY` | `C13` |
| `C13.LOGOUT` | `C13` |
| `C14.QUERY` | `C14` |
| `C14.FILTERS` | `C14` |
| `C14.RESULTS` | `C14` |
| `C14.RESULT.ITEM` | `C14` |
| `C15.LOGO` | `C15` |
| `C15.NAME` | `C15` |
| `C15.TAGLINE` | `C15` |
| `C15.ABOUT` | `C15` |
| `C15.SERVICE` | `C15` |
| `C15.ENTITY` | `C15` |
| `C15.VERSION` | `C15` |
| `C15.LEGAL` | `C15` |

## ما تم تنفيذه

- registry canonical يحوي IDs V7 الرسمية الـ146 دون تكرار.
- كل عنصر يملك `screenId`, `parentId`, type، label، data binding، action، validation، loading/success/error، permission، backend، database effect، audit، accessibility.
- توحيد IDs التنفيذ القديمة في `traceElement` إلى IDs V7 canonical عندما يوجد alias.
- اختبار ثابت يثبت العدد 146، uniqueness، وارتباط كل عنصر بأحد الشاشات الـ15.
- فحص `git diff --check` وparsing SQL السابقين ما زالا ناجحين.

## حدود القبول المتبقية

- registry والتتبع الثابت لا يثبتان وحدهما أن كل state تم اختباره على جهاز فعلي؛ Android SDK غير متاح، لذلك Build = NOT VERIFIED.
- قبول V7 التشغيلي يتطلب اختبار Auth/PostgREST/RLS/RPC والتزامن وحالات Offline/Retry على Supabase/PostgreSQL فعلي.
- بعض عناصر V7 مشروطة بوجود بيانات، مثل فلاتر C02، بيانات C04، والروابط القانونية C15؛ لا تظهر كأفعال وهمية عندما لا توجد وجهة أو بيانات صحيحة.
- لا يوجد ادعاء بأن 146 عنصرًا اجتازت runtime acceptance؛ التغطية الحالية هي عقدية/ساكنة ومربوطة بمسارات الواجهة الموجودة.
