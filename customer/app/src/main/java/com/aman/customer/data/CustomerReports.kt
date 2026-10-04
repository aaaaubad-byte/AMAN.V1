package com.aman.customer.data

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

enum class CustomerReportCategory(val label: String) {
    POINTS("النقاط"), PURCHASES("شراء النقاط"), PROTECTIONS("التفعيل والتمديد"), FINANCIAL("العمليات المالية"), NUMBERS("الأرقام");

    companion object {
        fun fromLabel(label: String): CustomerReportCategory = entries.firstOrNull { it.label == label } ?: POINTS
    }
}

data class CustomerReportRow(
    val id: String,
    val category: String,
    val type: String,
    val timestamp: String,
    val status: String,
    val points: String,
    val amount: String,
    val currency: String,
    val details: String,
)

fun isValidCustomerReportRange(fromDate: String, toDate: String): Boolean {
    if (fromDate.isNotBlank() && parseDate(fromDate) == null) return false
    if (toDate.isNotBlank() && parseDate(toDate) == null) return false
    if (fromDate.isBlank() || toDate.isBlank()) return true
    return runCatching { !LocalDate.parse(fromDate).isAfter(LocalDate.parse(toDate)) }.getOrDefault(false)
}

fun buildCustomerReportRows(
    data: CustomerScreenData,
    category: CustomerReportCategory,
    fromDate: String = "",
    toDate: String = "",
): List<CustomerReportRow> {
    if (!isValidCustomerReportRange(fromDate, toDate)) return emptyList()
    val rows = when (category) {
        CustomerReportCategory.POINTS -> data.related.array("ledger").objects().map { row ->
            CustomerReportRow(row.optString("id"), category.label, row.optString("entry_type"), row.optString("created_at"), "", row.opt("amount_points").plain(), "", "", row.optString("description").ifBlank { row.optString("reference_type") })
        }
        CustomerReportCategory.PURCHASES -> data.related.array("purchases").objects().map { row ->
            CustomerReportRow(row.optString("id"), category.label, row.optString("request_number").ifBlank { "طلب شراء نقاط" }, row.optString("submitted_at"), row.optString("status"), row.optString("points_amount_snapshot"), row.optString("price_amount_snapshot"), row.optString("currency_snapshot"), row.optString("rejection_reason"))
        }
        CustomerReportCategory.PROTECTIONS -> data.related.array("operations").objects()
            .filter { it.optString("operation_type") in setOf("protection_activation", "protection_extension") }
            .map { row ->
                CustomerReportRow(row.optString("id"), category.label, operationArabic(row.optString("operation_type")), row.optString("created_at"), row.optString("status"), row.opt("points_delta").plain(), "", "", listOf(row.optString("phone_number_id"), row.optJSONObject("metadata")?.toString().orEmpty()).filter(String::isNotBlank).joinToString(" · "))
            }
        CustomerReportCategory.FINANCIAL -> data.related.array("operations").objects()
            .filter { it.has("money_amount") && !it.isNull("money_amount") && it.optDouble("money_amount", 0.0) != 0.0 }
            .map { row ->
                CustomerReportRow(row.optString("id"), category.label, operationArabic(row.optString("operation_type")), row.optString("created_at"), row.optString("status"), row.opt("points_delta").plain(), row.opt("money_amount").plain(), row.optString("currency", ""), row.optJSONObject("metadata")?.toString().orEmpty())
            }
        CustomerReportCategory.NUMBERS -> data.related.array("numbers").objects().map { row ->
            val phone = row.optJSONObject("phone_numbers")
            CustomerReportRow(row.optString("id"), category.label, "رقم مضاف", row.optString("added_at"), row.optString("status"), "", "", "", phone?.optString("phone_e164").orEmpty().ifBlank { phone?.optString("normalized_phone").orEmpty() })
        }
    }
    return rows.filter { row -> inRange(row.timestamp, fromDate, toDate) }.sortedByDescending { parseInstant(it.timestamp) ?: Instant.MIN }
}

fun customerReportCsv(rows: List<CustomerReportRow>): String {
    val output = StringBuilder()
    output.append(listOf("الفئة", "النوع", "التاريخ", "الحالة", "النقاط", "المبلغ", "العملة", "التفاصيل").joinToString(",") { csvCell(it) })
    output.append("\r\n")
    rows.forEach { row ->
        val values = listOf(row.category, row.type, row.timestamp, row.status, row.points, row.amount, row.currency, row.details)
        output.append(values.mapIndexed { index, value -> csvCell(value, numeric = index == 4 || index == 5) }.joinToString(","))
        output.append("\r\n")
    }
    return output.toString()
}

private fun csvCell(value: String, numeric: Boolean = false): String {
    val clean = value.replace("\u0000", "")
    val formulaLike = clean.firstOrNull()?.let { it in listOf('=', '+', '-', '@', '\t', '\r') } == true
    val protected = if (!numeric && formulaLike) "'$clean" else clean
    return "\"${protected.replace("\"", "\"\"")}\""
}

private fun inRange(timestamp: String, fromDate: String, toDate: String): Boolean {
    val instant = parseInstant(timestamp) ?: return fromDate.isBlank() && toDate.isBlank()
    val zone = ZoneId.systemDefault()
    val from = parseDate(fromDate)?.let { LocalDate.parse(fromDate).atStartOfDay(zone).toInstant() }
    val until = parseDate(toDate)?.let { LocalDate.parse(toDate).plusDays(1).atStartOfDay(zone).toInstant() }
    return (from == null || !instant.isBefore(from)) && (until == null || instant.isBefore(until))
}

private fun parseDate(value: String): LocalDate? = if (value.isBlank()) null else runCatching { LocalDate.parse(value) }.getOrNull()
private fun parseInstant(value: String): Instant? = if (value.isBlank()) null else try { Instant.parse(value) } catch (_: DateTimeParseException) { null }
private fun Any?.plain(): String = when (this) { null -> ""; org.json.JSONObject.NULL -> ""; else -> toString() }
private fun operationArabic(value: String): String = when (value) {
    "protection_activation" -> "تفعيل الحماية"
    "protection_extension" -> "تمديد الحماية"
    "points_purchase" -> "شراء نقاط"
    "points_approval" -> "اعتماد شراء النقاط"
    "points_rejection" -> "رفض شراء النقاط"
    else -> value.ifBlank { "عملية" }
}
