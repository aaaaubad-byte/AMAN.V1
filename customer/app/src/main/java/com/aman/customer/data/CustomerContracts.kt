package com.aman.customer.data

import org.json.JSONArray
import org.json.JSONObject

enum class CustomerScreen(val id: String, val title: String) {
    INITIALIZATION("C01", "التهيئة"),
    LOGIN("C02", "تسجيل الدخول"),
    SIGN_UP("C03", "إنشاء حساب"),
    HOME("C04", "الرئيسية"),
    ADD_NUMBER("C05", "إضافة رقم"),
    BUY_POINTS("C06", "شراء النقاط"),
    POINTS_HISTORY("C07", "حركة النقاط"),
    ADDED_NUMBERS("C08", "الأرقام المضافة"),
    ACTIVE_NUMBERS("C09", "الأرقام النشطة"),
    EXPIRED_NUMBERS("C10", "الأرقام المنتهية"),
    ACTIVATE("C11", "تفعيل الحماية"),
    EXTEND("C12", "تمديد الحماية"),
    RENEW("C13", "تجديد الحماية"),
    NOTIFICATIONS("C14", "إشعارات النظام"),
    SUPPORT("C15", "تواصل أمان"),
    SEARCH("C16", "البحث"),
    REPORTS("C17", "التقارير"),
    ACCOUNT("C18", "الحساب"),
    ABOUT("C19", "عن أمان"),
    RECOVERY("C20", "استعادة الحساب");

    companion object {
        fun fromId(value: String): CustomerScreen = entries.firstOrNull { it.id == value } ?: LOGIN
        // Temporary source-compatibility aliases for older worker/cache code; not separate screens.
        val POINTS get() = BUY_POINTS
        val OPERATIONS get() = POINTS_HISTORY
        val INACTIVE_NUMBERS get() = ADDED_NUMBERS
        val ADMIN_ALERTS get() = NOTIFICATIONS
    }
}

data class CustomerRecord(
    val source: String,
    val id: String,
    val title: String,
    val subtitle: String,
    val details: List<Pair<String, String>>,
    val raw: JSONObject,
)

data class CustomerScreenData(
    val screen: CustomerScreen,
    val records: List<CustomerRecord> = emptyList(),
    val related: Map<String, JSONArray> = emptyMap(),
    val errorNotes: List<String> = emptyList(),
    val loadedAt: Long = System.currentTimeMillis(),
    val hasMore: Boolean = false,
    val selectedThreadId: String? = null,
    val messagesLoading: Boolean = false,
)

enum class LoadPhase { INITIAL, LOADING, LOADED, EMPTY, OFFLINE, ERROR }

data class CustomerUiState(
    val authenticated: Boolean = false,
    val authBusy: Boolean = false,
    val authError: String? = null,
    val authNotice: String? = null,
    val passwordChangeRequired: Boolean = false,
    val screen: CustomerScreen = CustomerScreen.INITIALIZATION,
    val pageIndex: Int = 0,
    val pageHasMore: Boolean = false,
    val phase: LoadPhase = LoadPhase.INITIAL,
    val data: CustomerScreenData? = null,
    val error: String? = null,
    val stale: Boolean = false,
    val mutationBusy: Boolean = false,
    val mutationMessage: String? = null,
    val navigationBackStack: List<CustomerScreen> = emptyList(),
)

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun Map<String, JSONArray>.array(key: String): JSONArray = this[key] ?: JSONArray()
internal fun JSONObject.text(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
    when (val value = opt(key)) {
        null, JSONObject.NULL -> null
        is JSONObject -> value.optString("name").takeIf(String::isNotBlank)
            ?: value.optString("short_name").takeIf(String::isNotBlank)
        is JSONArray -> value.optJSONObject(0)?.optString("name")?.takeIf(String::isNotBlank)
        else -> value.toString().takeIf { it.isNotBlank() && it != "null" }
    }
} ?: ""

internal fun JSONObject.money(key: String): String = opt(key).let {
    if (it == null || it == JSONObject.NULL) "" else "$it ${optString("currency", "")}".trim()
}

internal fun String.displayTime(): String = replace("T", " ").substringBefore(".").removeSuffix("Z")

data class ProviderPrefix(val prefix: String, val providerName: String, val active: Boolean)

fun discoverProvider(phoneDigits: String, prefixes: JSONArray): Pair<String, String>? =
    discoverProvider(phoneDigits, prefixes.objects().map {
        ProviderPrefix(
            it.optString("prefix"),
            it.optJSONObject("telecom_company")?.text("name")
                ?: it.optJSONObject("telecom_companies")?.text("name")
                ?: it.optString("company_name"),
            it.optString("status").equals("ACTIVE", true) || it.optString("status").equals("active", true),
        )
    })

fun discoverProvider(phoneNumber: String, prefixes: List<ProviderPrefix>): Pair<String, String>? =
    prefixes.asSequence()
        .map { it to phoneDigits(it.prefix) }
        .filter { (prefix, normalized) -> prefix.active && normalized.isNotBlank() && phoneDigits(phoneNumber).startsWith(normalized) }
        .maxByOrNull { it.second.length }
        ?.let { it.first.providerName to it.first.prefix }

data class ProtectionQuote(val units: Int, val durationDays: Int, val pointsCost: Long)

/** Customer selects whole admin-defined units; days and points are both server-configured per unit. */
fun calculateProtectionQuote(unitDays: Int, pointsPerUnit: Long, units: Int): ProtectionQuote? {
    if (unitDays <= 0 || pointsPerUnit <= 0 || units !in 1..120) return null
    val days = runCatching { Math.multiplyExact(unitDays, units) }.getOrNull()?.takeIf { it <= 3_650_000 } ?: return null
    val points = runCatching { Math.multiplyExact(pointsPerUnit, units.toLong()) }.getOrNull() ?: return null
    return ProtectionQuote(units, days, points)
}

fun toCustomerRecord(table: String, row: JSONObject): CustomerRecord {
    val phone = row.optJSONObject("phone_number") ?: row.optJSONObject("phone_numbers")
    val company = row.optJSONObject("telecom_company") ?: row.optJSONObject("telecom_companies")
        ?: row.optJSONObject("telecom_providers") ?: phone?.optJSONObject("telecom_company")
        ?: phone?.optJSONObject("telecom_providers")
    val status = row.text("status")
    val title = when (table) {
        "customer_profile", "profile" -> row.text("name", "public_user_code").ifBlank { "حساب العميل" }
        "points_balance", "balance" -> "رصيد النقاط"
        "points_purchase", "purchases", "requests" -> row.text("public_purchase_code", "request_number").ifBlank { "طلب شراء نقاط" }
        "protection_period", "protection", "protections" -> phone?.text("display_phone", "normalized_phone")
            ?: row.text("display_phone", "phone").ifBlank { "رقم هاتف" }
        "customer_number", "customer_numbers", "numbers" -> phone?.text("display_phone", "normalized_phone")
            ?: row.text("display_phone", "phone").ifBlank { "رقم هاتف" }
        "customer_notification", "notifications", "alerts" -> row.text("title").ifBlank { "إشعار" }
        "support_conversation", "support_threads", "threads" -> row.text("subject").ifBlank { "محادثة دعم" }
        "admin_message", "admin_messages" -> row.text("title").ifBlank { "رسالة من أمان" }
        "points_ledger", "ledger" -> row.text("description", "entry_type").ifBlank { "حركة نقاط" }
        else -> row.text("operation_type", "name", "entry_type", "title").ifBlank { table }
    }
    val subtitle = when (table) {
        "protection_period", "protection", "protections" -> "${statusLabel(status)} · ${row.text("end_at", "expires_at").displayTime()}"
        "points_purchase", "purchases", "requests" -> "${statusLabel(status)} · ${row.text("points_snapshot", "points_amount_snapshot")} نقطة"
        "operation", "operations" -> "${row.text("operation_type")} · ${statusLabel(status)}"
        "customer_notification", "notifications", "alerts" -> row.text("notification_type", "type")
        "support_conversation", "support_threads", "threads" -> statusLabel(status)
        "customer_number", "customer_numbers", "numbers" -> row.text("added_at").displayTime()
        else -> row.text("created_at", "submitted_at", "sent_at", "updated_at", "status").let { statusLabel(it) }
    }.trim(' ', '·')
    val detailKeys = listOf(
        "public_user_code", "name", "email", "public_added_number_code", "public_activation_code", "public_purchase_code",
        "display_phone", "normalized_phone", "account_type", "account_status", "status", "added_at", "created_at", "updated_at",
        "start_at", "end_at", "duration_days", "tariff_mode_snapshot", "tariff_value_snapshot", "points_cost_snapshot",
        "points_snapshot", "price_snapshot", "currency_snapshot", "payment_method_name_snapshot", "transfer_reference",
        "rejection_reason", "operation_type", "amount", "amount_points", "balance_before", "balance_after", "direction",
        "entry_type", "description", "source_type", "source_id", "title", "body", "is_read", "subject", "rate", "currency",
    )
    val details = detailKeys.mapNotNull { key ->
        row.opt(key).takeIf { it != null && it != JSONObject.NULL }?.let { key to it.toString() }
    } + listOfNotNull(
        phone?.text("display_phone", "normalized_phone")?.takeIf(String::isNotBlank)?.let { "رقم الهاتف" to it },
        company?.text("name")?.takeIf(String::isNotBlank)?.let { "شركة الاتصالات" to it },
    )
    val id = row.optString("id").ifBlank { row.optString("entity_id") }
    return CustomerRecord(table, id, title, subtitle, details, row)
}

private fun statusLabel(value: String): String = when (value.uppercase()) {
    "ACTIVE", "OPEN", "COMPLETED", "APPROVED", "SENT" -> when (value.uppercase()) {
        "ACTIVE" -> "نشط"; "OPEN" -> "مفتوح"; "COMPLETED" -> "مكتمل"; "APPROVED" -> "معتمد"; else -> "مرسل"
    }
    "INACTIVE", "INACTIVE_NUMBER" -> "غير نشط"
    "ARCHIVED" -> "مؤرشف"
    "PENDING", "STARTED" -> "قيد المراجعة"
    "REJECTED" -> "مرفوض"
    "CANCELLED" -> "ملغى"
    "EXPIRED" -> "منتهٍ"
    "CLOSED" -> "مغلق"
    "FAILED" -> "فشل"
    "SUCCEEDED", "SUCCESS" -> "ناجح"
    else -> value
}
