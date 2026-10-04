package com.aman.customer.data

import org.json.JSONArray
import org.json.JSONObject

/** C01–C15 are kept as an independent customer navigation contract. */
enum class CustomerScreen(val id: String, val title: String) {
    HOME("C01", "الرئيسية"), ACTIVE_NUMBERS("C02", "الأرقام المفعلة"), INACTIVE_NUMBERS("C03", "الأرقام غير المفعلة"),
    POINTS("C04", "إضافة نقاط"), OPERATIONS("C05", "العمليات"), ADD_NUMBER("C06", "إضافة رقم"),
    ACTIVATE("C07", "تفعيل رقم"), EXTEND("C08", "تمديد رقم"), ADMIN_ALERTS("C09", "تنبيهات الإدارة"),
    SUPPORT("C10", "تواصل مع الإدارة"), NOTIFICATIONS("C11", "إشعارات النظام"), REPORTS("C12", "التقارير"),
    ACCOUNT("C13", "الحساب"), SEARCH("C14", "البحث"), ABOUT("C15", "عن أمان");

    companion object { fun fromId(value: String) = entries.firstOrNull { it.id == value } ?: HOME }
}

data class CustomerRecord(val source: String, val id: String, val title: String, val subtitle: String, val details: List<Pair<String, String>>, val raw: JSONObject)
data class CustomerScreenData(
    val screen: CustomerScreen,
    val records: List<CustomerRecord> = emptyList(),
    val related: Map<String, JSONArray> = emptyMap(),
    val errorNotes: List<String> = emptyList(),
    val loadedAt: Long = System.currentTimeMillis(),
    val selectedThreadId: String? = null,
    val messagesLoading: Boolean = false,
)

enum class LoadPhase { INITIAL, LOADING, LOADED, EMPTY, OFFLINE, ERROR }
data class CustomerUiState(
    val authenticated: Boolean = false,
    val authBusy: Boolean = false,
    val authError: String? = null,
    val authNotice: String? = null,
    val signUpMode: Boolean = false,
    val screen: CustomerScreen = CustomerScreen.HOME,
    val phase: LoadPhase = LoadPhase.INITIAL,
    val data: CustomerScreenData? = null,
    val error: String? = null,
    val stale: Boolean = false,
    val mutationBusy: Boolean = false,
    val mutationMessage: String? = null,
)

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun Map<String, JSONArray>.array(key: String): JSONArray = this[key] ?: JSONArray()
internal fun JSONObject.text(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
    when (val value = opt(key)) {
        null, JSONObject.NULL -> null
        is JSONObject -> value.optString("name").takeIf(String::isNotBlank) ?: value.optString("short_name").takeIf(String::isNotBlank)
        is JSONArray -> value.optJSONObject(0)?.optString("name")?.takeIf(String::isNotBlank)
        else -> value.toString().takeIf { it.isNotBlank() && it != "null" }
    }
} ?: ""
internal fun JSONObject.money(key: String): String = opt(key).let { if (it == null || it == JSONObject.NULL) "" else "$it ${optString("currency", "")}".trim() }
internal fun String.displayTime(): String = replace("T", " ").substringBefore(".").removeSuffix("Z")

private val customerOwnedFields = setOf("user_id", "subscriber_id", "id", "phone_number_id", "phone_e164", "normalized_phone", "provider_id", "phone", "full_name", "username", "email", "account_status", "status", "added_at", "created_at", "updated_at", "started_at", "expires_at", "duration_days", "points_per_day_snapshot", "total_points_snapshot", "points_amount_snapshot", "price_amount_snapshot", "currency_snapshot", "request_number", "payment_method_name_snapshot", "payment_reference", "submitted_at", "reviewed_at", "rejection_reason", "operation_type", "operation_status", "points_delta", "money_amount", "metadata", "notification_type", "title", "content", "read_at", "subject", "last_reply_at", "balance_points", "amount", "balance_after", "entry_type", "description", "reference_type", "reference_id", "name", "points_amount", "price_amount", "currency", "instructions", "payment_data", "prefix", "points_per_day")

fun toCustomerRecord(table: String, row: JSONObject): CustomerRecord {
    val phone = row.optJSONObject("phone_numbers")
    val provider = row.optJSONObject("telecom_providers") ?: phone?.optJSONObject("telecom_providers")
    val title = when {
        table == "profiles" -> row.text("full_name", "username", "email").ifBlank { "حساب العميل" }
        table == "point_balances" -> "رصيد النقاط"
        table == "points_purchase_requests" -> row.text("request_number").ifBlank { "طلب شراء نقاط" }
        table == "protections" || table == "customer_numbers" -> phone?.text("phone_e164", "normalized_phone") ?: row.text("phone_e164", "normalized_phone").ifBlank { "رقم هاتف" }
        table == "notifications" -> row.text("title").ifBlank { "إشعار" }
        table == "support_threads" -> row.text("subject").ifBlank { "محادثة دعم" }
        else -> row.text("operation_type", "name", "entry_type", "title").ifBlank { table }
    }
    val subtitle = when (table) {
        "protections" -> row.text("status")
        "points_purchase_requests" -> "${row.text("status")} · ${row.text("points_amount_snapshot")} نقطة"
        "operations" -> operationLabel(row.text("operation_type")) + " · " + row.text("status")
        "notifications" -> row.text("notification_type")
        "support_threads" -> row.text("status")
        "customer_numbers" -> row.text("added_at")
        else -> row.text("created_at", "submitted_at", "updated_at", "status")
    }.trim(' ', '·').split(" · ").joinToString(" · ") { statusLabel(it) }
    val detailKeys = listOf("id", "phone_e164", "full_name", "username", "email", "account_status", "status", "added_at", "created_at", "updated_at", "started_at", "expires_at", "duration_days", "points_per_day_snapshot", "total_points_snapshot", "points_amount_snapshot", "price_amount_snapshot", "currency_snapshot", "payment_method_name_snapshot", "request_number", "submitted_at", "reviewed_at", "rejection_reason", "operation_type", "points_delta", "money_amount", "metadata", "notification_type", "title", "content", "read_at", "subject", "last_reply_at", "balance_points", "amount", "balance_after", "entry_type", "description", "reference_type", "reference_id", "name", "points_amount", "price_amount", "currency", "instructions", "prefix", "points_per_day")
    val pairs = detailKeys.mapNotNull { key -> row.opt(key).takeIf { it != null && it != JSONObject.NULL }?.let { key to it.toString() } }
    val providerName = provider?.text("name", "short_name").orEmpty()
    val phoneValue = phone?.text("phone_e164", "normalized_phone").orEmpty()
    val details = pairs + listOfNotNull(phoneValue.takeIf(String::isNotBlank)?.let { "phone_e164" to it }) +
        listOfNotNull(providerName.takeIf(String::isNotBlank)?.let { "شركة الاتصالات" to it })
    return CustomerRecord(table, row.optString("id", row.optString("user_id")), title, subtitle, details, row)
}

data class ProviderPrefix(val prefix: String, val providerName: String, val active: Boolean)

fun discoverProvider(phoneDigits: String, prefixes: JSONArray): Pair<String, String>? =
    discoverProvider(phoneDigits, prefixes.objects().map { row ->
        ProviderPrefix(row.optString("prefix"), row.optJSONObject("telecom_providers")?.text("name", "short_name").orEmpty(), row.optString("status") == "active")
    })

fun discoverProvider(phoneNumber: String, prefixes: List<ProviderPrefix>): Pair<String, String>? =
    prefixes.asSequence().map { it to phoneDigits(it.prefix) }
        .filter { (prefix, normalized) -> prefix.active && normalized.isNotBlank() && phoneDigits(phoneNumber).startsWith(normalized) }
        .maxByOrNull { it.second.length }?.let { it.first.providerName to it.first.prefix }

fun activationCost(days: Int, dailyPoints: Int): Long? = if (days <= 0 || dailyPoints <= 0) null else days.toLong() * dailyPoints.toLong()

private fun statusLabel(value: String): String = when (value) {
    "active" -> "نشط"; "inactive" -> "غير نشط"; "archived" -> "مؤرشف"
    "pending" -> "قيد المراجعة"; "approved" -> "معتمد"; "rejected" -> "مرفوض"
    "expired" -> "منتهٍ"; "cancelled" -> "ملغى"; "open" -> "مفتوح"; "closed" -> "مغلق"
    "succeeded" -> "ناجح"; "failed" -> "فشل"; "processing" -> "قيد التنفيذ"; else -> value
}
private fun operationLabel(value: String): String = when (value) {
    "points_purchase" -> "شراء نقاط"; "points_approval" -> "اعتماد/إضافة نقاط"; "points_rejection" -> "رفض طلب نقاط"
    "number_added" -> "إضافة رقم"; "protection_activation" -> "تفعيل حماية"; "protection_extension" -> "تمديد حماية"
    "payment_task" -> "عملية مرتبطة بالخدمة"; else -> value
}
