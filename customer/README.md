# AMAN Customer Android

مشروع Android مستقل لتطبيق العميل، منفصل عن `admin/`، مبني بـKotlin وJetpack Compose وواجهات عربية RTL وهوية فحمية/حمراء.

## البناء المحلي

```bash
cd customer
./gradlew clean testDebugUnitTest assembleDebug
```

يتطلب Android SDK 35 وJDK 21 (أو JDK مدعومًا من Android Gradle Plugin). لا يحتوي المستودع على إعدادات مشروع Supabase الحقيقية. يمكن تمرير القيم العامة عند البناء من Gradle properties أو environment:

```text
SUPABASE_URL=https://<project>.supabase.co
SUPABASE_ANON_KEY=<public-anon-key>
```

يجب عدم وضع `service_role` أو أسرار خاصة داخل التطبيق. إذا غابت القيم، يعرض التطبيق رسالة إعداد ويعطل تسجيل الدخول. **البناء التجريبي ليس إصدار APK للنشر**؛ لم يُنشر التطبيق ولم يتصل بقاعدة حقيقية.

## بنية المصدر

- `app/src/main/java/com/aman/customer/data/CustomerContracts.kt`: معرّفات C01–C15 والنماذج والعقود المساعدة.
- `SupabaseGateway.kt`: Supabase Auth وقراءات PostgREST وRPC العميل المعروفة وتحديث `read_at`.
- `CustomerRepository.kt`: ربط كل وجهة بمصادر القراءة والعمليات الذرية المعتمدة.
- `CustomerCache.kt`: آخر نسخ القراءة المحلية المشفرة ومجزأة بمعرّف المستخدم.
- `CustomerOutbox.kt`: طابور مشفر per-user لإرسال intent شراء النقاط فقط عند عودة الشبكة؛ لا يحمل mutation نجاحًا محليًا.
- `CustomerRefreshWorker.kt`: تحديث دوري مشروط بالاتصال ويدفع طابور شراء النقاط idempotent قبل تحديث القراءات.
- `ui/CustomerViewModel.kt`: حالة الشاشة/المصادقة/الأخطاء وتنسيق التحديث.
- `ui/CustomerScreens.kt`: الشاشات ومكوناتها وحالات النماذج.
- `ui/AmanCustomerApp.kt` و`AmanCustomerTheme.kt`: جذر التطبيق والهوية.

## العقود المستخدمة من SQL المودع

- Auth: Supabase email/password sign-in/sign-up، جلسات refresh/logout مشفرة محليًا.
- Reads: `profiles`, `subscribers`, `customer_numbers`, `phone_numbers`, `telecom_providers`, `telecom_prefixes`, `provider_tariffs`, `points_packages`, `payment_methods`, `point_balances`, `points_purchase_requests`, `point_ledger`, `protections`, `operations`, `system_notifications`, `admin_notifications`, `support_threads`, `support_messages`؛ كل القراءة المرتبطة بمستخدم تقيد بـ`auth.uid()`/RLS.
- Mutations المعروفة: `submit_points_purchase_request`, `activate_protection`, `extend_protection`; وRPC `mark_notification_read` لتعيين `system_notifications.is_read/read_at`.
- جداول `payment_tasks` وخطة التشغيل الداخلية لا تُطلب أو تُعرض في تطبيق العميل.

المرجع الحالي للعقود هو `../V7.md` و`../database/AMAN_V7_DATABASE.sql` فقط؛ لا يستخدم Customer ملفات SQL التاريخية كـfallback. طلب شراء النقاط يمكن حفظه كـintent مشفر لكل مستخدم ثم إرساله عند عودة الاتصال؛ تميزه الواجهة عن طلب وصل Backend. التفعيل والتمديد وبقية العمليات الحساسة لا تنفذ أو تعد بالنجاح Offline.

## حالة Customer Phase

راجع `AMAN_CUSTOMER_PHASE_STATUS.md` لمصفوفة C01–C15، مسار كل Action من UI إلى RPC، وقيود التحقق الواقعية. يسجل التطبيق إجراءات وأخطاء غير حساسة محليًا لتجنب الصمت التشغيلي، ولا يسجل كلمات مرور أو رموز جلسات أو مراجع دفع.
