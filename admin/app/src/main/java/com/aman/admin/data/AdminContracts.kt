package com.aman.admin.data

/** Stable A01–A15 screen and table bindings. Customer screens are deliberately absent. */
enum class AdminSection(
    val screenId: String,
    val title: String,
    val table: String?,
    val readPermission: String,
) {
    HOME("A01", "الرئيسية", "operations", "admin_dashboard.read"),
    SUBSCRIBERS("A02", "المشتركون", "subscribers", "admin_subscribers.read"),
    USERS("A03", "المستخدمون", "profiles", "admin_users.read"),
    ADDED_NUMBERS("A04", "الأرقام المضافة", "customer_numbers", "admin_numbers.read"),
    ACTIVE_NUMBERS("A05", "الأرقام المفعلة", "protections", "admin_protections.read"),
    PURCHASES("A06", "طلبات الشراء", "points_purchase_requests", "admin_purchases.read"),
    PAYMENT_TASKS("A07", "مهام السداد", "payment_tasks", "admin_tasks.read"),
    PROVIDERS("A08", "إدارة الشركات", "telecom_providers", "admin_providers.read"),
    PACKAGES("A09", "إدارة الشحن", "points_packages", "admin_packages.read"),
    PAYMENT_METHODS("A10", "وسائل الدفع", "payment_methods", "admin_payment_methods.read"),
    TASK_SETTINGS("A11", "إعدادات المهام", "task_settings", "admin_tasks.settings"),
    SEARCH("A12", "البحث", null, "admin_dashboard.read"),
    NOTIFICATIONS("A13", "الإشعارات", "admin_notifications", "admin_notifications.read"),
    REPORTS("A14", "التقارير", "operations", "admin_reports.read"),
    ACCOUNT("A15", "الحساب", "profiles", "admin_account.read"),
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

enum class AdminMutation(val rpcName: String) {
    APPROVE_PURCHASE("approve_points_purchase"),
    REJECT_PURCHASE("reject_points_purchase"),
    EXECUTE_PAYMENT_TASK("execute_payment_task"),
    RESCHEDULE_PAYMENT_TASK("reschedule_payment_task"),
    CANCEL_PAYMENT_TASK("cancel_payment_task"),
    UPDATE_PROFILE("admin_update_profile"),
    SET_SUBSCRIBER_STATUS("admin_set_subscriber_status"),
    SET_CUSTOMER_NUMBER_STATUS("admin_set_customer_number_status"),
    SAVE_PROVIDER("admin_save_provider"),
    SAVE_PREFIX("admin_save_telecom_prefix"),
    SAVE_TARIFF("admin_save_provider_tariff"),
    SAVE_PACKAGE("admin_save_points_package"),
    SAVE_PAYMENT_METHOD("admin_save_payment_method"),
    SAVE_TASK_SETTINGS("admin_save_task_settings"),
    SEND_NOTIFICATION("send_admin_notification"),
}

enum class AdminFormKind {
    USER, PROVIDER, PREFIX, TARIFF, PACKAGE, PAYMENT_METHOD, TASK_SETTINGS
}

enum class RelatedListKind(val title: String) {
    SUBSCRIBER_NUMBERS("أرقام المشترك"),
    SUBSCRIBER_POINTS("حركات النقاط"),
    SUBSCRIBER_HISTORY("سجل المشترك"),
    PROTECTION_TASKS("خطة المهام"),
    PROVIDER_PREFIXES("بادئات الشركة"),
    PROVIDER_TARIFFS("تعرفات الشركة"),
}

data class ReportType(val id: String, val label: String, val table: String, val dateColumn: String) {
    companion object {
        val all = listOf(
            ReportType("users", "المستخدمون", "profiles", "created_at"),
            ReportType("subscribers", "المشتركون", "subscribers", "created_at"),
            ReportType("numbers", "الأرقام", "customer_numbers", "added_at"),
            ReportType("purchases", "طلبات شراء النقاط", "points_purchase_requests", "submitted_at"),
            ReportType("points", "النقاط", "point_ledger", "created_at"),
            ReportType("protections", "الحماية", "protections", "created_at"),
            ReportType("operations", "العمليات", "operations", "created_at"),
            ReportType("finance", "المالية", "financial_ledger", "created_at"),
            ReportType("tasks", "التشغيل", "payment_tasks", "due_at"),
        )
    }
}

object AdminPermissions {
    const val USERS_READ = "admin_users.read"
    const val USERS_UPDATE = "admin_users.update"
    const val SUBSCRIBERS_READ = "admin_subscribers.read"
    const val SUBSCRIBERS_UPDATE = "admin_subscribers.update"
    const val NUMBERS_READ = "admin_numbers.read"
    const val NUMBERS_UPDATE = "admin_numbers.update"
    const val PROTECTIONS_READ = "admin_protections.read"
    const val POINTS_READ = "admin_points.read"
    const val PURCHASES_READ = "admin_purchases.read"
    const val PURCHASES_APPROVE = "admin_purchases.approve"
    const val PURCHASES_REJECT = "admin_purchases.reject"
    const val TASKS_READ = "admin_tasks.read"
    const val TASKS_EXECUTE = "admin_tasks.execute"
    const val TASKS_RESCHEDULE = "admin_tasks.reschedule"
    const val TASKS_CANCEL = "admin_tasks.cancel"
    const val TASKS_SETTINGS = "admin_tasks.settings"
    const val PROVIDERS_READ = "admin_providers.read"
    const val PROVIDERS_MANAGE = "admin_providers.manage"
    const val PACKAGES_READ = "admin_packages.read"
    const val PACKAGES_MANAGE = "admin_packages.manage"
    const val PAYMENT_METHODS_READ = "admin_payment_methods.read"
    const val PAYMENT_METHODS_MANAGE = "admin_payment_methods.manage"
    const val NOTIFICATIONS_READ = "admin_notifications.read"
    const val NOTIFICATIONS_SEND = "admin_notifications.send"
    const val OPERATIONS_READ = "admin_operations.read"
    const val REPORTS_READ = "admin_reports.read"
    const val REPORTS_EXPORT = "admin_reports.export"
    const val ACCOUNT_READ = "admin_account.read"
}
