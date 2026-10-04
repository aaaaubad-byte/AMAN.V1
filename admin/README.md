# AMAN Admin — تطبيق Android أصلي

مجلد `admin/` مشروع مستقل بـ Kotlin وJetpack Compose، بواجهة عربية RTL، ومخازن محلية مشفرة. ليس تطبيق ويب.

## تحقق المصدر والاختبارات

المتطلبات: JDK 17+ وAndroid SDK Platform 35 / Build Tools 35.0.0.

```bash
cd admin
./gradlew testDebugUnitTest --no-daemon --console=plain
```

الأمر أعلاه يترجم المصدر ويشغل اختبارات الوحدة دون بناء أو تسليم APK. آخر تحقق AMAN-2: BUILD SUCCESSFUL؛ 7 اختبارات، بلا فشل.

## الإعداد وقت التشغيل

يقبل Gradle `SUPABASE_URL` و`SUPABASE_ANON_KEY` من خصائص Gradle أو متغيرات البيئة. تبقى القيم فارغة افتراضيًا، وتظهر شاشة الدخول رسالة إعداد عند غيابها. لا تضع `service_role` أو كلمة مرور أو مفتاحًا خاصًا داخل التطبيق أو Git.

## نطاق العقود

- A01–A15 موصولة بمصادر Supabase/RPC المعتمدة في `database/`، مع إظهار حالات التحميل والخطأ/الخلو واللقطات المشفرة عند انقطاع الشبكة.
- المصادقة عبر Supabase Auth، وتجديد الجلسة ثم فحص `is_admin()`، وقراءة permissions من `admin_account_info()`؛ كل mutation يعاد تفويضها في RPC/RLS.
- Offline read-only: لا تُجرى mutations أو export دون تحقق صلاحية حي. تحديث WorkManager محدود بالاتصال ويعيد محاولة أخطاء الخادم المؤقتة.
- **A04:** لا يحرر Admin رقم الهاتف. يقتصر الإجراء على العرض وتغيير حالة علاقة الرقم/الأرشفة وفق `admin_set_customer_number_status`.
- **A07:** تسجيل إكمال المهمة يتطلب أن يكون الدفع قد نُفذ خارجيًا وأن يدخل المشغل مرجعه؛ التطبيق لا ينفذ عملية الدفع.
- **A08:** Provider وTelecom Prefixes وProvider Tariffs عبر RPCs مدققة؛ عقود prefix/tariff الإضافية في `database/migrations/004_admin_provider_catalog_rpcs.sql`.
- **A13:** `notifications` هو سجل التسليم للمستخدمين باستخدام `admin_alert`؛ `admin_notifications` سجل حملات الإدارة الموجود أصلًا في migration 002.
- التقارير تقرأ الفئات المصرح بها، وCSV لا يفتح قبل تحقق `admin_reports.export` من Backend.

## حدود الاعتماد

Migrations `001`–`004` و`database/AMAN_DATABASE_FINAL.sql` ملفات مصدر فقط ولم تُطبق/تُختبر على Supabase الحية. يلزم التحقق من schema وRLS والأدوار ونتائج RPC في البيئة المخولة قبل الإنتاج. لم يجر اختبار على جهاز، ولا توجد صور مرجعية كافية لمقارنة مرئية دقيقة. راجع `IMPLEMENTATION_BLOCKERS.md` و`../MANUS/PHASE_2_STATE.md` و`../MANUS/HANDOFF.md`.
