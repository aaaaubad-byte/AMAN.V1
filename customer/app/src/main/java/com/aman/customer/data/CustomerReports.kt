package com.aman.customer.data

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

enum class CustomerReportCategory(val label: String) {
    POINTS("النقاط"), PURCHASES("المشتريات"), PROTECTION("الحماية"), NUMBERS("الأرقام"), ACTIVITY("النشاط");

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
            val amount = row.opt("amount").plain().ifBlank { row.opt("amount_points").plain() }
            val signed = if (row.optString("direction").equals("DEBIT", true)) "-${amount.removePrefix("-")}" else amount.removePrefix("+")
            CustomerReportRow(row.optString("id"), category.label, row.optString("entry_type"), row.optString("created_at"), "", signed, "", "", row.optString("description"))
        }
        CustomerReportCategory.PURCHASES -> data.related.array("purchases").objects().map { row ->
            CustomerReportRow(
                row.optString("id"), category.label, row.optString("public_purchase_code").ifBlank { "طلب شراء نقاط" },
                row.optString("submitted_at"), row.optString("status"), row.opt("points_snapshot").plain(),
                row.opt("price_snapshot").plain(), row.optString("currency_snapshot"), row.optString("rejection_reason"),
            )
        }
        CustomerReportCategory.PROTECTION -> data.related.array("protections").objects().map { row ->
            CustomerReportRow(
                row.optString("id"), category.label, "فترة حماية", row.optString("start_at"), row.optString("status"),
                row.opt("points_cost_snapshot").plain(), "", row.optString("currency_snapshot"),
                "${row.optString("display_phone")} · ${row.optString("end_at")}",
            )
        }
        CustomerReportCategory.NUMBERS -> data.related.array("numbers").objects().map { row ->
            CustomerReportRow(
                row.optString("id"), category.label, "رقم مرتبط", row.optString("added_at"), row.optString("status"), "", "", "",
                row.optString("display_phone").ifBlank { row.optJSONObject("phone_number")?.optString("display_phone").orEmpty() },
            )
        }
        CustomerReportCategory.ACTIVITY -> data.related.array("operations").objects().map { row ->
            CustomerReportRow(
                row.optString("id"), category.label, row.optString("operation_type"), row.optString("created_at"),
                row.optString("status"), "", "", "", row.optString("entity_type") + " · " + row.optString("result_reference"),
            )
        }
    }
    return rows.filter { inRange(it.timestamp, fromDate, toDate) }.sortedByDescending { parseInstant(it.timestamp) ?: Instant.MIN }
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
private fun Any?.plain(): String = when (this) { null -> ""; JSONObject.NULL -> ""; else -> toString() }
