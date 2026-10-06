package com.aman.customer.data

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Stable trace IDs for the customer UI as defined by the current master reference. */
data class CustomerUiElementContract(
    val elementId: String,
    val screenId: String,
    val parentId: String,
    val type: String,
    val label: String,
    val visualRole: String,
    val dataSource: String,
    val action: String,
    val visibility: String = "الشاشة المقصودة ظاهرة",
    val enabledWhen: String = "الحالة تسمح؛ صلاحية الخادم هي المرجع النهائي",
    val validation: String = "لا يوجد",
    val confirmation: String = "لا يوجد",
    val loading: String = "حالة تحميل مرئية",
    val success: String = "نتيجة خادم فعلية أو بيانات مقروءة",
    val error: String = "خطأ ظاهر؛ لا يُصطنع النجاح",
    val permission: String = "المستخدم المصادق عليه؛ RLS/RPC authoritative",
    val backend: String = "get_customer_screen_data / customer RPC",
    val databaseEffect: String = "قراءة فقط ما لم يذكر RPC صريح",
    val audit: String = "تدقيق الخادم عند التغيير الحساس",
    val accessibility: String = label,
)

object CustomerUiTraceability {
    val screenIds: Set<String> = CustomerScreen.entries.map { it.id }.toSet()
    val elements: List<CustomerUiElementContract> = listOf(
        e("C01.BRAND", "علامة", "AMAN | أمان", "ثابت", "عرض التهيئة"),
        e("C01.LOADING", "تحميل", "جارٍ التهيئة", "حالة التطبيق", "فحص الجلسة والإعدادات"),
        e("C02.EMAIL", "حقل", "البريد الإلكتروني", "Supabase Auth", "إدخال البريد"),
        e("C02.PASSWORD", "حقل سري", "كلمة المرور", "Supabase Auth", "إدخال كلمة المرور"),
        e("C02.SUBMIT", "زر", "تسجيل الدخول", "Supabase Auth", "تسجيل الدخول"),
        e("C02.SIGNUP", "رابط", "إنشاء حساب", "C03", "الانتقال إلى C03"),
        e("C02.RECOVERY", "رابط", "نسيت كلمة المرور", "C20", "الانتقال إلى C20"),
        e("C03.NAME", "حقل", "الاسم", "customer_profile.name", "إنشاء ملف USER"),
        e("C03.CONSENT.TERMS", "موافقة", "الشروط", "customer input", "تسجيل الموافقة الحالية"),
        e("C03.CONSENT.PRIVACY", "موافقة", "الخصوصية", "customer input", "تسجيل الموافقة الحالية"),
        e("C03.SUBMIT", "زر", "إنشاء الحساب", "Auth + create_customer_profile", "إنشاء حساب USER دون اشتراك تلقائي"),
        e("C04.BALANCE", "بطاقة", "رصيد النقاط", "points_balance", "عرض رصيد الخادم"),
        e("C04.NOTIFICATIONS", "زر", "الإشعارات", "customer_notification", "الانتقال إلى C14"),
        e("C04.QUICK_ACTIONS", "قائمة", "خدمات العميل", "C05–C13", "فتح الشاشة المطابقة"),
        e("C04.RECENT", "قائمة", "أحدث الأحداث", "operation / points_ledger / purchase", "عرض ملخص أحداث الحساب"),
        e("C05.PHONE", "حقل", "رقم الهاتف", "customer input", "تحقق E.164 واكتشاف البادئة"),
        e("C05.ADD", "زر", "إضافة رقم", "add_customer_number", "إضافة الرقم إلى حسابه", validation = "بادئة نشطة وطول دولي صالح"),
        e("C05.UPDATE", "زر", "تعديل الرقم", "update_customer_number", "تحديث علاقة الرقم غير المحمية"),
        e("C05.DELETE", "زر", "حذف الرقم", "delete_customer_number", "أرشفة الرقم غير المحمي"),
        e("C06.PACKAGE", "اختيار", "الباقة", "points_package", "اختيار باقة نشطة"),
        e("C06.METHOD", "اختيار", "وسيلة الدفع", "payment_method", "اختيار وسيلة نشطة"),
        e("C06.REFERENCE", "حقل", "مرجع التحويل", "customer input", "إدخال المرجع"),
        e("C06.SUBMIT", "زر", "شراء", "submit_points_purchase", "إنشاء شراء PENDING دون إضافة رصيد"),
        e("C07.SEARCH", "حقل", "البحث في الحركة", "points_ledger", "فلترة القائمة الحالية"),
        e("C07.FILTER", "اختيار", "الفلترة", "points_ledger.direction/entry_type", "تصفية الحركة الحالية"),
        e("C07.LIST", "قائمة", "حركة النقاط", "points_ledger", "قراءة قيود دفتر النقاط immutable"),
        e("C08.LIST", "قائمة", "الأرقام المضافة", "customer_number", "قراءة أرقام الحساب"),
        e("C09.LIST", "قائمة", "الأرقام النشطة", "protection_period", "قراءة الحمايات النشطة"),
        e("C09.EXTEND", "زر", "تمديد الحماية", "C12", "الانتقال إلى تمديد الحماية"),
        e("C10.LIST", "قائمة", "الأرقام المنتهية", "protection_period", "قراءة الحمايات المنتهية"),
        e("C10.RENEW", "زر", "تجديد الحماية", "C13", "الانتقال إلى تجديد الحماية"),
        e("C11.TARIFF", "اختيار", "التعرفة والمدة", "protection_tariff", "عرض السعر المعتمد من الخادم"),
        e("C11.CONFIRM", "زر", "تفعيل الحماية", "activate_protection", "تنفيذ ذري مع رصيد النقاط والتدقيق", confirmation = "عرض السعر والرصيد قبل الإرسال"),
        e("C12.CONFIRM", "زر", "تمديد الحماية", "extend_protection", "تنفيذ ذري للحماية والدفتر والتدقيق", confirmation = "عرض التكلفة وتاريخ الانتهاء الجديد"),
        e("C13.CONFIRM", "زر", "تجديد الحماية", "renew_protection", "إنشاء فترة جديدة مع حفظ التاريخ", confirmation = "عرض التكلفة والفترة الجديدة"),
        e("C14.LIST", "قائمة", "الإشعارات", "customer_notification", "فتح الإشعار وعلامة القراءة"),
        e("C15.THREADS", "قائمة", "محادثات الدعم", "support_conversation", "فتح محادثة يملكها العميل"),
        e("C15.MESSAGE", "حقل", "نص الرسالة", "support_message", "كتابة أو إرسال الرد"),
        e("C15.SEND", "زر", "إرسال", "create_support_conversation / send_support_message", "إرسال داخل المحادثة"),
        e("C16.QUERY", "حقل", "البحث", "بيانات العميل المصرح بها", "البحث ضمن بيانات الحساب فقط"),
        e("C16.RESULTS", "قائمة", "النتائج", "get_customer_screen_data", "عرض نتائج ضمن ملكية العميل"),
        e("C17.CATEGORY", "اختيار", "نوع التقرير", "points/purchases/protection/numbers/activity", "اختيار تقرير العميل"),
        e("C17.RANGE", "نطاق", "الفترة", "customer input", "تصفية التقرير بالتاريخ"),
        e("C17.EXPORT", "زر", "تصدير CSV", "نتائج العميل المفلترة", "حفظ ملف يختاره المستخدم"),
        e("C18.NAME", "حقل", "الاسم", "customer_profile.name", "تعديل اسم الحساب فقط"),
        e("C18.SAVE", "زر", "حفظ", "update_customer_profile", "حفظ الاسم مع تدقيق الخادم"),
        e("C18.ID", "قيمة", "معرّف المستخدم", "customer_profile.public_user_code", "العرض أو النسخ"),
        e("C18.LOGOUT", "زر", "تسجيل الخروج", "Supabase Auth", "إنهاء الجلسة ومسح cache المستخدم"),
        e("C19.SECTIONS", "أقسام", "ما هو أمان/الشروط/الخصوصية", "app_content أو نص ثابت معتمد", "التبديل الأفقي والتمرير الرأسي"),
        e("C20.NAME", "حقل", "الاسم", "customer input", "مدخل الاستعادة دون كشف صلاحية الحساب"),
        e("C20.EMAIL", "حقل", "البريد الإلكتروني", "Supabase Auth", "طلب إرسال رسالة استعادة"),
        e("C20.USER_ID", "حقل", "معرّف المستخدم", "customer input", "مدخل الاستعادة دون كشف صلاحية الحساب"),
        e("C20.SUBMIT", "زر", "استعادة الحساب", "Supabase Auth recover", "استجابة عامة مقاومة لتعداد الحسابات"),
    )

    private fun e(
        id: String,
        type: String,
        label: String,
        source: String,
        action: String,
        validation: String = "لا يوجد",
        confirmation: String = "لا يوجد",
    ): CustomerUiElementContract {
        val screen = id.substringBefore('.')
        return CustomerUiElementContract(
            elementId = id,
            screenId = screen,
            parentId = screen,
            type = type,
            label = label,
            visualRole = type,
            dataSource = source,
            action = action,
            validation = validation,
            confirmation = confirmation,
            backend = source,
            databaseEffect = action,
        )
    }
}

fun Modifier.traceElement(id: String): Modifier = testTag(id)
