package com.aman.admin.data

/** Logical administration views. Several views are tabs/subsets of one canonical A-screen. */
enum class AdminSection(
    val screenId: String,
    val title: String,
    val table: String?,
    val readPermission: String,
) {
    HOME("A01", "الرئيسية", null, AdminPermissions.CUSTOMERS_READ),
    SUBSCRIBERS("A02", "إدارة العملاء", "customer_profile", AdminPermissions.CUSTOMERS_READ),
    USERS("A02", "المستخدمون", "customer_profile", AdminPermissions.USERS_READ),
    ADDED_NUMBERS("A03", "إدارة الأرقام", "customer_number", AdminPermissions.NUMBERS_READ),
    ACTIVE_NUMBERS("A03", "الحمايات", "protection_period", AdminPermissions.NUMBERS_READ),
    PAYMENT_TASKS("A04", "إدارة المهام والخطط", "periodic_task", AdminPermissions.TASKS_READ),
    PROVIDERS("A05", "إدارة الشركات", "telecom_company", AdminPermissions.PROVIDERS_READ),
    PACKAGES("A06", "إدارة الباقات", "points_package", AdminPermissions.PACKAGES_READ),
    PAYMENT_METHODS("A07", "إدارة الدفع", "payment_method", AdminPermissions.PAYMENT_METHODS_READ),
    PURCHASES("A08", "طلبات شراء النقاط", "points_purchase", AdminPermissions.PURCHASES_READ),
    PERIODIC_PAYMENT("A09", "السداد الدوري", "periodic_task", AdminPermissions.TASKS_READ),
    TASK_PLANS("A04", "خطط المهام", "task_plan", AdminPermissions.TASKS_READ),
    FINANCE("A10", "دفتر المالية", "financial_ledger", AdminPermissions.FINANCE_READ),
    COMMUNICATIONS("A11", "تواصل أمان", "support_conversation", AdminPermissions.SUPPORT_READ),
    SEARCH("A12", "البحث", null, AdminPermissions.CUSTOMERS_READ),
    REPORTS("A13", "التقارير", "operation", AdminPermissions.REPORTS_READ),
    ACCOUNT("A14", "الحساب", null, AdminPermissions.CUSTOMERS_READ),
    ABOUT("A15", "عن أمان", "app_content", AdminPermissions.CUSTOMERS_READ),
    SETUP("A16", "التهيئة", "maintenance_config", AdminPermissions.CUSTOMERS_READ),
    LOGIN("A17", "تسجيل الدخول", null, ""),
    RECOVERY("A19", "استعادة الحساب", null, ""),
    NOTIFICATIONS("A20", "كتابة إشعار إداري", "admin_notification_campaign", AdminPermissions.NOTIFICATIONS_READ),
    TASK_SETTINGS("A05", "إعدادات المهام", "task_configuration", AdminPermissions.TASK_SETTINGS_READ),
}

data class AdminRoute(val screenId: String, val title: String)

/** Canonical V11 screen inventory; login/setup/recovery are routed outside the signed-in shell. */
val adminRoutes = listOf(
    AdminRoute("A01", "الرئيسية"), AdminRoute("A02", "إدارة العملاء"),
    AdminRoute("A03", "إدارة الأرقام"), AdminRoute("A04", "إدارة المهام / خطط المهام"),
    AdminRoute("A05", "إدارة الشركات"), AdminRoute("A06", "إدارة الباقات"),
    AdminRoute("A07", "إدارة الدفع"), AdminRoute("A08", "طلبات شراء النقاط"),
    AdminRoute("A09", "السداد الدوري"), AdminRoute("A10", "دفتر المالية"),
    AdminRoute("A11", "تواصل أمان"), AdminRoute("A12", "البحث"),
    AdminRoute("A13", "التقارير"), AdminRoute("A14", "الحساب"),
    AdminRoute("A15", "عن أمان"), AdminRoute("A16", "التهيئة"),
    AdminRoute("A17", "تسجيل الدخول"), AdminRoute("A19", "استعادة الحساب"),
    AdminRoute("A20", "كتابة إشعار إداري"),
)

data class SectionCard(val section: AdminSection, val icon: String)

val adminSections = listOf(
    SectionCard(AdminSection.SUBSCRIBERS, "people"),
    SectionCard(AdminSection.ADDED_NUMBERS, "phone"),
    SectionCard(AdminSection.PAYMENT_TASKS, "task"),
    SectionCard(AdminSection.PROVIDERS, "business"),
    SectionCard(AdminSection.PACKAGES, "points"),
    SectionCard(AdminSection.PAYMENT_METHODS, "payment"),
    SectionCard(AdminSection.PURCHASES, "receipt"),
    SectionCard(AdminSection.PERIODIC_PAYMENT, "task"),
    SectionCard(AdminSection.FINANCE, "assessment"),
)

enum class AdminMutation(val rpcName: String) {
    APPROVE_PURCHASE("approve_points_purchase"),
    REJECT_PURCHASE("reject_points_purchase"),
    EXECUTE_PAYMENT_TASK("admin_execute_periodic_task"),
    RESCHEDULE_PAYMENT_TASK("admin_reschedule_periodic_task"),
    CANCEL_PAYMENT_TASK("admin_cancel_periodic_task"),
    UPDATE_PROFILE("admin_update_customer_profile"),
    SET_SUBSCRIBER_STATUS("admin_update_customer_profile"),
    SET_CUSTOMER_NUMBER_STATUS("admin_set_customer_number_status"),
    SAVE_PROVIDER("admin_save_telecom_company"),
    SAVE_PREFIX("admin_save_telecom_prefix"),
    SAVE_TARIFF("admin_save_provider_tariff"),
    SAVE_PACKAGE("admin_save_points_package"),
    SAVE_PAYMENT_METHOD("admin_save_payment_method"),
    SAVE_TASK_SETTINGS("admin_save_task_configuration"),
    CREATE_EXPENSE("admin_create_expense"),
    GRANT_POINTS("admin_grant_points"),
    APPROVE_SUPPORT_REQUEST("admin_approve_support_request"),
    SEND_SUPPORT_REPLY("admin_send_support_reply"),
    CLOSE_SUPPORT("admin_close_support_conversation"),
    SEND_NOTIFICATION("admin_send_notification"),
}

enum class AdminFormKind {
    USER, PROVIDER, PREFIX, TARIFF, PACKAGE, PAYMENT_METHOD, TASK_SETTINGS, EXPENSE
}

enum class RelatedListKind(val title: String) {
    SUBSCRIBER_NUMBERS("أرقام العميل"),
    SUBSCRIBER_POINTS("حركات النقاط"),
    SUBSCRIBER_HISTORY("سجل العميل"),
    PROTECTION_TASKS("خطة المهام"),
    PROVIDER_PREFIXES("بادئات الشركة"),
    PROVIDER_TARIFFS("تعرفات الشركة"),
    SUPPORT_MESSAGES("رسائل المحادثة"),
    PLAN_TASKS("مهام الخطة"),
}

data class ReportType(val id: String, val label: String, val table: String, val dateColumn: String) {
    companion object {
        val all = listOf(
            ReportType("users", "المستخدمون", "customer_profile", "created_at"),
            ReportType("subscribers", "المشتركون", "customer_profile", "created_at"),
            ReportType("numbers", "الأرقام", "customer_number", "added_at"),
            ReportType("purchases", "طلبات شراء النقاط", "points_purchase", "submitted_at"),
            ReportType("points", "حركة النقاط", "points_ledger", "created_at"),
            ReportType("protections", "الحماية", "protection_period", "created_at"),
            ReportType("operations", "العمليات", "operation", "created_at"),
            ReportType("finance", "المالية", "financial_ledger", "created_at"),
            ReportType("tasks", "التشغيل", "periodic_task", "due_at"),
        )
    }
}

object AdminPermissions {
    const val CUSTOMERS_READ = "customers.read"
    const val CUSTOMERS_UPDATE = "customers.update"
    const val USERS_READ = "users.read"
    const val USERS_UPDATE = "users.update"
    const val USERS_READ_ALIAS = USERS_READ
    const val SUBSCRIBERS_READ = CUSTOMERS_READ
    const val SUBSCRIBERS_UPDATE = CUSTOMERS_UPDATE
    const val NUMBERS_READ = "numbers.read"
    const val NUMBERS_UPDATE = "numbers.update"
    const val PROTECTIONS_READ = NUMBERS_READ
    const val POINTS_READ = "finance.read"
    const val PURCHASES_READ = "points_purchases.read"
    const val PURCHASES_APPROVE = "points_purchases.approve"
    const val PURCHASES_REJECT = "points_purchases.reject"
    const val TASKS_READ = "tasks.read"
    const val TASKS_EXECUTE = "tasks.execute"
    const val TASKS_RESCHEDULE = "tasks.reschedule"
    const val TASKS_CANCEL = "tasks.cancel"
    const val TASKS_SETTINGS = "task_settings.write"
    const val TASK_SETTINGS_READ = "task_settings.read"
    const val PROVIDERS_READ = "providers.read"
    const val PROVIDERS_MANAGE = "providers.write"
    const val PACKAGES_READ = "packages.read"
    const val PACKAGES_MANAGE = "packages.write"
    const val PAYMENT_METHODS_READ = "payment_methods.read"
    const val PAYMENT_METHODS_MANAGE = "payment_methods.write"
    const val NOTIFICATIONS_READ = "notifications.read"
    const val NOTIFICATIONS_SEND = "notifications.send"
    const val OPERATIONS_READ = "audit.read"
    const val REPORTS_READ = "reports.read"
    const val REPORTS_EXPORT = "reports.export"
    const val FINANCE_READ = "finance.read"
    const val FINANCE_WRITE = "finance.write"
    const val SUPPORT_READ = "support.write"
    const val ACCOUNT_READ = CUSTOMERS_READ
}
