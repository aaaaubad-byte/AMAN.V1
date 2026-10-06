package com.aman.admin.data

/** Element-to-route/backend traceability for the closed V11 admin reference. */
data class AdminTraceElement(
    val elementId: String,
    val screenId: String,
    val type: String,
    val readPermission: String,
    val backendEffect: String,
)

data class AdminTraceGap(
    val gapId: String,
    val screenId: String,
    val detail: String,
)

object AdminUiTraceability {
    val elements: List<AdminTraceElement> = listOf(
        e("A01.HEADER", "A01", "Display", "customers.read", "session/admin_account_info"),
        e("A01.METRICS", "A01", "List", "per-source permission", "permission-filtered counts"),
        e("A01.SECTIONS", "A01", "Navigation", "per-destination permission", "route"),
        e("A01.RECENT", "A01", "List", "audit.read", "operation"),

        e("A02.CUSTOMER_LIST", "A02", "List", "customers.read", "customer_profile"),
        e("A02.USER_LIST", "A02", "List", "users.read", "customer_profile"),
        e("A02.PROFILE", "A02", "Display", "customers.read/users.read", "customer_profile + points_balance"),
        e("A02.NUMBERS_POINTS_HISTORY", "A02", "Tabs", "numbers.read/finance.read/audit.read", "related reads"),
        e("A02.EDIT_STATUS", "A02", "Action", "customers.update/users.update", "admin_update_customer_profile"),

        e("A03.NUMBER_LIST", "A03", "List", "numbers.read", "customer_number + phone_number"),
        e("A03.NUMBER_DETAILS", "A03", "Display", "numbers.read", "phone_number + telecom_company"),
        e("A03.STATUS_ARCHIVE", "A03", "Action", "numbers.update", "admin_set_customer_number_status"),
        e("A03.ACTIVE_PROTECTIONS", "A03", "List", "numbers.read", "protection_period"),

        e("A04.PLANS", "A04", "List", "tasks.read", "task_plan"),
        e("A04.PLAN_TASKS", "A04", "List", "tasks.read", "periodic_task by task_plan_id"),
        e("A04.TASK_ACTIONS", "A04", "Action", "tasks.execute/reschedule/cancel", "admin task RPCs"),
        e("A04.TASK_FILTERS", "A04", "Input", "tasks.read", "status/date/provider filters"),

        e("A05.COMPANIES", "A05", "List", "providers.read", "telecom_company"),
        e("A05.PREFIXES", "A05", "List", "providers.read", "telecom_prefix"),
        e("A05.TARIFFS", "A05", "List", "providers.read", "protection_tariff"),
        e("A05.TASK_CONFIG", "A05", "Form", "task_settings.write", "admin_save_task_configuration"),
        e("A05.COMPANY_PREFIX_TARIFF_WRITE", "A05", "Action", "providers.write", "admin_save_telecom_company/prefix/provider_tariff"),

        e("A06.PACKAGES", "A06", "List/Form", "packages.read/packages.write", "points_package + admin_save_points_package"),
        e("A07.PAYMENT_METHODS", "A07", "List/Form", "payment_methods.read/payment_methods.write", "payment_method + admin_save_payment_method"),
        e("A08.PURCHASES", "A08", "List", "points_purchases.read", "points_purchase"),
        e("A08.REVIEW", "A08", "Action", "points_purchases.approve/reject", "approve_points_purchase/reject_points_purchase"),
        e("A09.PERIODIC", "A09", "List", "tasks.read", "open periodic_task in payment window"),

        e("A10.LEDGER", "A10", "List", "finance.read", "financial_ledger/financial_account"),
        e("A10.EXPENSE", "A10", "Form/Action", "finance.write", "admin_create_expense + ledger debit"),
        e("A11.CONVERSATIONS", "A11", "List", "support.write", "support_conversation"),
        e("A11.MESSAGES", "A11", "List", "support.write", "support_message"),
        e("A11.APPROVE_REQUEST", "A11", "Action", "support.write", "admin_approve_support_request"),
        e("A11.REPLY_CLOSE", "A11", "Action", "support.write", "admin_send_support_reply/admin_close_support_conversation"),
        e("A12.SEARCH", "A12", "Input/List", "per-source permission", "v11 source-table reads"),
        e("A13.REPORTS", "A13", "Filter/List", "reports.read", "v11 report sources"),
        e("A13.EXPORT", "A13", "Action", "reports.export", "local CSV export"),
        e("A14.ACCOUNT", "A14", "Display/Action", "authenticated admin", "admin_account_info/signOut"),

        e("A15.CONTENT", "A15", "Display", "customers.read", "active app_content versions"),
        e("A16.DIAGNOSTICS", "A16", "Display", "admin session", "config/session/connectivity/storage checks"),
        e("A17.LOGIN", "A17", "Form/Action", "Supabase Auth", "password sign-in + admin_identity/RBAC verification"),
        e("A19.RECOVERY", "A19", "Form/Action", "Supabase Auth", "password recovery; generic result"),
        e("A20.NOTIFICATIONS", "A20", "Form/Preview/Action", "notifications.send", "campaign + recipients + notification event + audit"),
    )

    /** Implementations intentionally blocked until an authoritative V11 rule exists. */
    val knownGaps: List<AdminTraceGap> = listOf(
        AdminTraceGap("GAP-DB-016", "A04/A05", "إعادة بناء خطط المهام التاريخية أو المستقبلية غير معرّفة؛ تعرض الواجهة الخطط وتضيف إصدار إعداد جديدًا فقط."),
        AdminTraceGap("GAP-A20-SEGMENTS", "A20", "شرائح expired/need extension/need renewal/dynamic segment لا تملك شروط أهلية مغلقة في عقد V11 الحالي؛ لن تُرسل لها حملة تخمينية."),
    )

    fun ids(): Set<String> = elements.map { it.elementId }.toSet()
    fun coveredRoutes(): Set<String> = elements.map { it.screenId }.toSet()

    private fun e(id: String, screen: String, type: String, permission: String, effect: String) =
        AdminTraceElement(id, screen, type, permission, effect)
}
