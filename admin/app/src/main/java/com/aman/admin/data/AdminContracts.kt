package com.aman.admin.data

/** Admin routes and database bindings derived from the checked-in migration. */
enum class AdminSection(
    val title: String,
    val table: String?,
    val supportsList: Boolean = true,
    val contractNote: String? = null,
) {
    HOME("الرئيسية", "operations", contractNote = null),
    SUBSCRIBERS("المشتركون", "subscribers"),
    USERS("المستخدمون", "profiles"),
    ADDED_NUMBERS("الأرقام المضافة", "customer_numbers"),
    ACTIVE_NUMBERS("الأرقام المفعلة", "protections", contractNote = "يعرّف الترحيل 002 منح القراءة الإدارية للتعرفة والمهام؛ يلزم تطبيقه في مرحلة قاعدة البيانات."),
    PURCHASES("طلبات الشراء", "points_purchase_requests", contractNote = "يعرّف الترحيل 002 صلاحيات إدارية وRPC للرفض؛ اعتماد/رفض الطلبين يتطلبان تطبيقه."),
    PAYMENT_TASKS("مهام السداد", "payment_tasks", contractNote = "يعرّف الترحيل 002 منح القراءة وإعادة الجدولة والإلغاء وتسجيل مرجع الدفع الخارجي؛ يلزم تطبيقه."),
    PROVIDERS("إدارة الشركات", "telecom_providers", contractNote = "يعرّف الترحيل 002 صلاحيات وRPC حفظ الشركة؛ إدارة البادئات والتعرفة ما زالت تحتاج ربطًا."),
    PACKAGES("إدارة الشحن", "points_packages", contractNote = "يعرّف الترحيل 002 صلاحيات وRPC حفظ الباقات؛ يلزم تطبيقه وربطه بالنموذج."),
    PAYMENT_METHODS("وسائل الدفع", "payment_methods", contractNote = "يعرّف الترحيل 002 صلاحيات وRPC حفظ وسائل الدفع؛ يلزم تطبيقه وربطه بالنموذج."),
    TASK_SETTINGS("إعدادات المهام", "task_settings", contractNote = "يعرّف الترحيل 002 صلاحيات وRPC الإعداد وإعادة بناء المستقبل؛ يلزم تطبيقه وربطه بالنموذج."),
    SEARCH("البحث", null, contractNote = "البحث يستخدم فقط جداول الترحيل المتاحة وصلاحيات المستخدم الحالي."),
    NOTIFICATIONS("الإشعارات", "notifications", contractNote = "يعرّف الترحيل 002 سجل الإشعارات الإدارية وRPC الإرسال؛ يلزم تطبيقه وربطه بالنموذج."),
    REPORTS("التقارير", "operations", contractNote = "النتائج تُقرأ من الجداول الفعلية؛ لا يوجد عقد تصدير تقارير في الترحيل الحالي."),
    ACCOUNT("الحساب", "profiles", contractNote = "الملف للقراءة وتسجيل الخروج؛ لا توجد عمليات تعديل ملف/أمان إدارية في الترحيل الحالي."),
}

data class SectionCard(val section: AdminSection, val icon: String)

val adminSections = listOf(
    SectionCard(AdminSection.SUBSCRIBERS, "people"),
    SectionCard(AdminSection.USERS, "person"),
    SectionCard(AdminSection.ADDED_NUMBERS, "phone"),
    SectionCard(AdminSection.ACTIVE_NUMBERS, "verified"),
    SectionCard(AdminSection.PURCHASES, "receipt"),
    SectionCard(AdminSection.PAYMENT_TASKS, "task"),
    SectionCard(AdminSection.PROVIDERS, "business"),
    SectionCard(AdminSection.PACKAGES, "points"),
    SectionCard(AdminSection.PAYMENT_METHODS, "payment"),
)

/** Admin RPC operations currently wired by the Android client. */
enum class AdminMutation(val rpcName: String) {
    APPROVE_PURCHASE("approve_points_purchase"),
    REJECT_PURCHASE("reject_points_purchase"),
    EXECUTE_PAYMENT_TASK("execute_payment_task"),
}
