# تسليم AMAN — بعد كتابة مصادر STAGE 3

## المستودع ونقطة التسليم

- Repository: `aaaaubad-byte/AMAN.V1`.
- Branch: `main`.
- مصدر العميل موجود تحت `customer/`؛ مستقل عن `admin/`.
- نقطة الأساس قبل عمل هذه المرحلة كانت `248c431` — `phase 2: add admin contracts and checkpoint`.
- لم يُسلّم APK ولم يُنشر المنتج. استُخدم assembleDebug للاختبار التقني فقط.

## حدود التفويض لهذه المرحلة

المطلوب قراءة المرجع و`database/` و`MANUS/`، كتابة ملفات عميل Android، وعدم الاتصال بـSupabase حقيقي أو تنفيذ queries/RPC/SQL فعلية. تحقق build محلي فقط. الرفع المطلوب هنا هو **مصدر Android والوثائق إلى المستودع**، لا نشر التطبيق.

## نقطة الإنجاز الدقيقة

اكتمل هيكل مستقل `customer/` ويتضمن المشروع Gradle وAndroid Manifest والموارد، واجهات C01–C15، RTL وهوية داكنة، Auth، مستودع وبوابة REST/RPC، cache مشفرًا ومجزأ حسب المستخدم، تحديثًا خلفيًا مشروطًا بالاتصال، حالات Offline/Error/Empty/Stale، وفحوص وحدة.

عمليات نقاط الشراء/التفعيل/التمديد مقسمة: `submit_points_purchase` يودع طلبًا Pending دون زيادة رصيد؛ `activate_protection` و`extend_protection` وحدهما يطلبان خصم النقاط والحماية؛ التطبيق لا يعرض المهام التشغيلية. طلب الشراء وحده يمكن أن يحفظ intent مشفرًا عند Offline ويعيد إرساله بالمفتاح نفسه؛ يظل غير مسجل بالخادم ولا يضيف نقاطًا حتى ردّه. التفعيل والتمديد لا ينفذان Offline.

## تحقق محلي

```bash
cd customer && ./gradlew clean testDebugUnitTest assembleDebug --no-daemon --console=plain
```

النتيجة: BUILD SUCCESSFUL، 4 اختبارات/صفر إخفاق، assembleDebug للتحقق فقط. لا جهاز أو قاعدة Supabase حية جرى اختبارها، ولا URL/anon key مهيأ.

## قيود تستلزم المرحلة الرابعة

التفاصيل في `customer/IMPLEMENTATION_BLOCKERS.md` و`MANUS/PHASE_3_STATE.md`. أهمها: لا كتابة أرقام C06، لا إرسال دعم C10، لا ضمان Profile/Subscriber عند signup، اعتماد قراءة التعرفة على migration 002 غير متحقق، RLS لا يكشف حماية العملاء الآخرين، وRPC التفعيل/التمديد غير idempotent على مستوى المعاملة. لا تحاول تخمين عقود أو ترقيع SQL ضمن هذه الحالة.

## الاستئناف دون إعادة العمل

1. اقرأ `MANUS/PROJECT_STATE.md` و`MANUS/PHASE_3_STATE.md` و`customer/IMPLEMENTATION_BLOCKERS.md`.
2. ابدأ مرحلة الإصلاح اللاحقة بحسم العقود المصدرية والاختبارات المخولة، لا بإعادة إنشاء `customer/`.
3. عالج عوائق `admin/` فقط في مسار المرحلة الرابعة المطلوب؛ عمل STAGE 3 لم يغير `admin/`.
4. لم يحدث اتصال حي، تنفيذ SQL، استعلامات/RPC حقيقية أو تغيير ملفات `database/`.
