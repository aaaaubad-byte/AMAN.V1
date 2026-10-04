# سجل التغييرات AMAN

## STAGE 3 — ملفات تطبيق العميل Android

- أُنشئ مشروع Android مستقل داخل `customer/` بـKotlin/Jetpack Compose، لا يدمج `admin/`، مع Manifest وموارد RTL وإعداد Gradle وطبقات البيانات/المستودع/الحالة/الواجهة والاختبارات.
- بُنيت وجهات العميل الـ15 C01–C15، وشبكة C01 (3×3) والتنقل السفلي (C15/C14/C01/C12/C13)، إضافة إلى طبقة Auth قبل الشاشات.
- رُبطت القراءات بعقود `database/` مع نطاق المستخدم، وربطت شراء النقاط بـ`submit_points_purchase` (طلب Pending لا يضيف نقاطًا)، والتفعيل بـ`activate_protection`، والتمديد بـ`extend_protection`، وقراءة الإشعارات بتحديث `read_at`.
- فصل التطبيق شراء النقاط عن استهلاكها، وأظهر تعرفة/تكلفة تقديرية من البيانات المتاحة مع إبقاء Backend مصدر الحساب النهائي، وأخفى مهام السداد الداخلية.
- أُضيف cache مشفر مرتبط بمعرف المستخدم، حالات Stale/Offline/Error/Empty، وتحديث دوري/فوري عبر WorkManager. طابور مشفر لكل مستخدم لطلب شراء النقاط فقط؛ يرسل intent عند عودة الاتصال بمفتاح ثابت ويظل واضحًا كغير مسجل بالخادم. التفعيل والتمديد لا يعملان Offline، ولا تُعرض نقاط كأنها مضافة محليًا.
- سجّل `customer/IMPLEMENTATION_BLOCKERS.md` فجوات SQL الفعلية: لا عقد كتابة C06، لا عقد إرسال دعم C10، غياب provisioning مؤكد للملف/المشترك، اعتماد قراءة التعرفة على 002، عدم كشف حماية مستخدم آخر ضمن RLS، ومشكلة idempotency في RPC التفعيل/التمديد. لا mock APIs أو بيانات ثابتة.
- التحقق: `cd customer && ./gradlew clean testDebugUnitTest assembleDebug --no-daemon --console=plain` — نجح البناء والاختبارات الأربع. استخدم `assembleDebug` للتحقق التقني فقط؛ لم يُسلّم APK أو يُنشر.
- لم يُتصل بقاعدة Supabase، ولم تُنفذ queries/RPC حقيقية أو SQL، ولم تُغير ملفات `database/`.

## STAGE 2 — تطبيق Admin Android (غير مكتمل وظيفيًا)

- يوجد مشروع Admin مستقل في `admin/`، ما زالت فجوات عقده موثقة في `admin/IMPLEMENTATION_BLOCKERS.md` و`MANUS/PHASE_2_STATE.md`؛ لم تنفذ المرحلة الثالثة أي إصلاحات له.
- HEAD السابق لهذه المرحلة: `248c431` (`phase 2: add admin contracts and checkpoint`).

## STAGE 1 — قاعدة بيانات AMAN

- أضيفت migrations المرحلة الأولى والثانية في `database/` كمصادر عقود.
- تطبيق هذه الملفات والتحقق من قاعدة Supabase المقصودة لم يثبت؛ لا تعد المرحلة مكتملة على قاعدة حية.

## AMAN-1 — Backend/Database source repairs

- أضيفت `database/migrations/003_aman1_backend_repairs.sql` و`database/AMAN_DATABASE_FINAL.sql`.
- شملت الإصلاحات provisioning من Auth إلى Profile، إنشاء subscriber بعد أول اعتماد، idempotency قبل mutation للشراء/التفعيل/التمديد، التحقق من ملكية الرقم، authoritative provider resolution، expiration/renewal، post-expiry وvisibility task rules، ودعم customer عبر RPC آمن.
- وُحّد دور الإدارة إلى `admin`، وحقول Audit إلى `actor_user_id / actor_role / before / after`، وأنواع Financial Ledger إلى `points_purchase_income / task_payment / expense / adjustment`، مع الإبقاء على جدول `notifications` الواحد حسب قرار المالك.
- نجح فحص parser لـPostgreSQL على migrations 001–003 وملف SQL النهائي. لم تُنفذ SQL ولم يُتصل بـSupabase حي.


## AMAN-2 — استكمال تطبيق Admin وربط العقود

- استكملت طبقة Admin A01–A15 في `admin/`: استعلامات فعلية، بحث وفلاتر وتقارير CSV، نماذج mutations، فحص permissions، تجديد الجلسة، cache مشفر read-only، ودعم RTL وحالات Offline/خطأ/خلو.
- A04: القرار النهائي يمنع تحرير رقم الهاتف أو إنشاء Admin Edit RPC له؛ الحالة المسموحة تتبع العقد الحالي.
- A08: أضيفت `database/migrations/004_admin_provider_catalog_rpcs.sql` لعقود إدارة Prefixes وProvider Tariffs، وتوليدت `database/AMAN_DATABASE_FINAL.sql` من migrations 001–004. الملف لم يُطبق على قاعدة حية.
- A13 يستعمل `notifications` لتسليم الرسائل، ويحتفظ بـ`admin_notifications` الموجود في migration 002 لسجل الحملات؛ لم ينشئ AMAN-2 جدول إشعارات إضافيًا.
- التحقق بعد آخر تعديل مصدر: `./gradlew testDebugUnitTest --no-daemon --console=plain` — BUILD SUCCESSFUL، 7 اختبارات. تطابق ملف SQL النهائي مع concat migrations 001–004؛ لا SQL حي ولا APK مسلم.
- حُفظ checkpoint على `main` في commit `b69d5ec` بعد نجاح `git diff --check`.
- لا تزال تهيئة/صلاحيات Supabase الحية، التحقق على جهاز، وقرار تكامل/توثيق تنفيذ الدفع الخارجي بوابات مفتوحة. التفاصيل في `MANUS/PHASE_2_STATE.md` و`admin/IMPLEMENTATION_BLOCKERS.md`.
