package com.aman.customer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aman.customer.data.CustomerRecord
import com.aman.customer.data.CustomerScreen
import com.aman.customer.data.CustomerScreenData
import com.aman.customer.data.CustomerUiState
import com.aman.customer.data.CustomerReportCategory
import com.aman.customer.data.buildCustomerReportRows
import com.aman.customer.data.customerReportCsv
import com.aman.customer.data.isValidCustomerReportRange
import com.aman.customer.data.normalizePhoneE164
import com.aman.customer.data.phoneDigits
import com.aman.customer.data.traceElement
import com.aman.customer.ui.CustomerViewModel
import com.aman.customer.data.discoverProvider
import com.aman.customer.data.objects
import com.aman.customer.data.array
import com.aman.customer.data.text
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.io.OutputStreamWriter

@Composable
fun CustomerScreenContent(state: CustomerUiState, vm: CustomerViewModel, modifier: Modifier = Modifier) {
    val data = state.data
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).traceElement(state.screen.id), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (data == null && state.screen != CustomerScreen.ABOUT) {
            EmptyPanel(if (state.phase.name == "ERROR") "تعذر تحميل بيانات هذه الشاشة." else "جارٍ تحميل بيانات الحساب…")
            OutlinedButton(onClick = { vm.load(state.screen) }) { Text("إعادة المحاولة") }
        } else when (state.screen) {
            CustomerScreen.HOME -> HomeScreen(data!!, vm)
            CustomerScreen.ACTIVE_NUMBERS -> ActiveNumbersScreen(data!!, vm)
            CustomerScreen.INACTIVE_NUMBERS -> InactiveNumbersScreen(data!!, vm)
            CustomerScreen.POINTS -> PointsScreen(data!!, state, vm)
            CustomerScreen.OPERATIONS -> OperationsScreen(data!!)
            CustomerScreen.ADD_NUMBER -> AddNumberScreen(data!!, state, vm)
            CustomerScreen.ACTIVATE -> ActivateScreen(data!!, state, vm)
            CustomerScreen.EXTEND -> ExtendScreen(data!!, state, vm)
            CustomerScreen.ADMIN_ALERTS -> AlertListScreen(data!!, vm)
            CustomerScreen.SUPPORT -> SupportScreen(data!!, state, vm)
            CustomerScreen.NOTIFICATIONS -> NotificationScreen(data!!, vm)
            CustomerScreen.REPORTS -> ReportsScreen(data!!, state, vm)
            CustomerScreen.ACCOUNT -> AccountScreen(data!!, vm)
            CustomerScreen.SEARCH -> SearchScreen(data!!, vm)
            CustomerScreen.ABOUT -> AboutScreen()
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun DataCard(record: CustomerRecord, trailing: String? = null, onClick: (() -> Unit)? = null, traceId: String? = null, copyTraceId: String? = null) {
    var showDetails by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Card(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).then(if (traceId == null) Modifier else Modifier.traceElement(traceId)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(record.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (record.subtitle.isNotBlank()) Text(record.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                trailing?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
            }
            val summary = record.details.filter { it.first in setOf("expires_at", "duration_days", "points_per_day_snapshot", "total_points_snapshot", "points_amount_snapshot", "price_amount_snapshot", "entry_type", "amount_points", "balance_after", "added_at", "created_at", "rejection_reason", "body") }.take(4)
            summary.forEach { (key, value) -> if (value.isNotBlank()) Text("${fieldName(key)}: ${value.displayValue()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (record.title.any(Char::isDigit) && record.source in setOf("protections", "customer_numbers"))
                    TextButton(onClick = { clipboard.setText(AnnotatedString(record.title)) }, modifier = if (copyTraceId == null) Modifier else Modifier.traceElement(copyTraceId)) { Text("نسخ الرقم") }
                if (record.details.isNotEmpty() || record.raw.length() > 0) TextButton(onClick = { showDetails = true }) { Text("التفاصيل") }
            }
        }
    }
    if (showDetails) AlertDialog(onDismissRequest = { showDetails = false }, confirmButton = { TextButton(onClick = { showDetails = false }) { Text("إغلاق") } },
        title = { Text(record.title) }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            if (record.subtitle.isNotBlank()) Text(record.subtitle, color = MaterialTheme.colorScheme.primary)
            record.details.forEach { (key, value) -> if (value.isNotBlank()) Text("${fieldName(key)}: ${value.displayValue()}", Modifier.padding(vertical = 3.dp)) }
        } })
}

@Composable
private fun HomeScreen(data: CustomerScreenData, vm: CustomerViewModel) {
    val profile = data.related["profile"]?.optJSONObject(0)
    val balance = data.related["balance"]?.optJSONObject(0)?.opt("balance_points")?.toString()
    val unread = data.related["unread"]?.length() ?: 0
    val protectionRows = data.related.array("protections").objects()
    val activeProtections = protectionRows.filter { it.optString("status") == "active" && runCatching { java.time.Instant.parse(it.optString("expires_at")).isAfter(java.time.Instant.now()) }.getOrDefault(false) }
    val adminAlertCount = data.related.array("admin_alerts").length()
    Card(Modifier.traceElement("C01.BALANCE.CARD"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(profile?.text("full_name", "username")?.takeIf { it.isNotBlank() }?.let { "أهلًا، $it" } ?: "مرحبًا بك في أمان", Modifier.traceElement("C01.HEADER.USER"), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("رصيد النقاط", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(balance?.let { "$it نقطة" } ?: "غير متاح حاليًا", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("إشعارات غير مقروءة: $unread", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
    Card(Modifier.fillMaxWidth().traceElement("C01.PROTECTION.STATUS").clickable { vm.navigate(CustomerScreen.ACTIVE_NUMBERS) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("حالة الحماية", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Text(
                when {
                    data.errorNotes.any { it.startsWith("protections:") } -> "غير متاحة حاليًا"
                    activeProtections.isNotEmpty() -> "${activeProtections.size} حماية نشطة"
                    protectionRows.isEmpty() -> "لا توجد حماية مرتبطة بالحساب"
                    else -> "لا توجد حماية نشطة الآن"
                },
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
    SectionTitle("الأقسام")
    val rows = listOf(
        listOf(CustomerScreen.POINTS, CustomerScreen.ACTIVE_NUMBERS, CustomerScreen.INACTIVE_NUMBERS),
        listOf(CustomerScreen.ADD_NUMBER, CustomerScreen.ACTIVATE, CustomerScreen.EXTEND),
        listOf(CustomerScreen.ADMIN_ALERTS, CustomerScreen.OPERATIONS, CustomerScreen.SUPPORT)
    )
    rows.forEach { line -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        line.forEach { screen ->
            val traceId = when (screen) {
                CustomerScreen.POINTS -> "C01.GRID.ADD_POINTS"; CustomerScreen.ACTIVE_NUMBERS -> "C01.GRID.ACTIVE"
                CustomerScreen.INACTIVE_NUMBERS -> "C01.GRID.INACTIVE"; CustomerScreen.ADD_NUMBER -> "C01.GRID.ADD_NUMBER"
                CustomerScreen.ACTIVATE -> "C01.GRID.ACTIVATE"; CustomerScreen.EXTEND -> "C01.GRID.EXTEND"
                CustomerScreen.ADMIN_ALERTS -> "C01.GRID.ADMIN_ALERTS"; CustomerScreen.OPERATIONS -> "C01.GRID.OPERATIONS"
                else -> "C01.GRID.SUPPORT"
            }
            Card(Modifier.weight(1f).height(78.dp).clickable { vm.navigate(screen) }.traceElement(traceId), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.Center) {
                Text(screen.id, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                Text(screen.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, maxLines = 2)
                if (screen == CustomerScreen.ADMIN_ALERTS) Text("$adminAlertCount تنبيه غير مقروء", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.traceElement("C01.GRID.ADMIN_ALERTS.COUNT"))
            }
        } }
    } }
    SectionTitle("أحدث العمليات", "ملخص فقط؛ افتح سجل العمليات للتفاصيل.")
    if (data.records.isEmpty()) EmptyPanel("لا توجد عمليات مسجلة في الحساب حتى الآن.")
    data.records.forEach { record -> RecentOperationCard(record, onClick = { vm.navigate(CustomerScreen.OPERATIONS) }) }
}

@Composable
private fun RecentOperationCard(record: CustomerRecord, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick).traceElement("C01.RECENT.LIST"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(record.title, fontWeight = FontWeight.SemiBold)
            KeyValue("الحالة", record.subtitle.ifBlank { "غير متاحة" })
            record.raw.optString("created_at").takeIf(String::isNotBlank)?.let { KeyValue("الوقت", it.displayDate()) }
            record.raw.opt("points_delta")?.takeIf { it != JSONObject.NULL }?.let { KeyValue("النقاط", it.toString()) }
            record.raw.opt("money_amount")?.takeIf { it != JSONObject.NULL }?.let { KeyValue("المبلغ", it.toString()) }
        }
    }
}

@Composable
private fun ActiveNumbersScreen(data: CustomerScreenData, vm: CustomerViewModel) {
    SectionTitle("حمايات أرقامك", "المهام التشغيلية الداخلية وتفاصيل مبالغ السداد غير معروضة للعميل.")
    val now = System.currentTimeMillis()
    if (data.records.isEmpty()) EmptyPanel("لا توجد حماية مرتبطة بحسابك.")
    data.records.forEach { record ->
        val expiry = record.raw.optString("expires_at")
        val expired = runCatching { java.time.Instant.parse(expiry).toEpochMilli() <= now }.getOrDefault(false)
        val remaining = if (expired) "منتهية" else runCatching {
            "${((java.time.Instant.parse(expiry).toEpochMilli() - now) / (24L * 60 * 60 * 1000) + 1).coerceAtLeast(0)} يومًا"
        }.getOrDefault("غير متاح")
        val adjusted = record.copy(
            subtitle = if (expired) "انتهت المدة · حالة الخادم: ${record.raw.optString("status")}" else record.subtitle,
            details = record.details + ("المدة المتبقية" to remaining)
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DataCard(adjusted, trailing = if (expired) "منتهية" else "حماية", traceId = "C02.LIST", copyTraceId = "C02.ACTION.COPY")
        }
    }
}

@Composable
private fun InactiveNumbersScreen(data: CustomerScreenData, vm: CustomerViewModel) {
    SectionTitle("أرقامك غير المفعلة", "إضافة الرقم لا تمنح ملكية حصرية ولا تبدأ حماية.")
    NoticeBanner("حالة القائمة مرتبطة بحمايات حسابك فقط؛ قد يكون الرقم محميًا لعميل آخر ولا تكشف RLS هذه المعلومة. يتحقق الخادم نهائيًا عند محاولة التفعيل.", true)
    if (data.records.isEmpty()) EmptyPanel("لا توجد أرقام غير مفعلة في قائمة أرقامك.")
    data.records.forEach { record ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DataCard(record, trailing = "غير مرتبط بحماية حسابك", traceId = "C03.LIST", copyTraceId = "C03.ACTION.COPY")
        }
    }
}

@Composable
private fun PointsScreen(data: CustomerScreenData, state: CustomerUiState, vm: CustomerViewModel) {
    var packageId by rememberSaveable { mutableStateOf("") }
    var methodId by rememberSaveable { mutableStateOf("") }
    var reference by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val packages = data.related.array("packages").objects()
    val methods = data.related.array("methods").objects()
    SectionTitle("شراء النقاط", "شراء النقاط مستقل عن تفعيل الحماية. لا تضاف النقاط إلا بعد اعتماد الطلب.")
    SelectJsonItem("الباقة", packages, packageId, { it.optString("name") + " · " + it.optString("points_amount") + " نقطة · " + it.optString("price_amount") + " " + it.optString("currency") }, { packageId = it.optString("id") }, "C04.PACKAGE.SELECTOR")
    SelectJsonItem("وسيلة الدفع", methods, methodId, { it.optString("name") }, { methodId = it.optString("id") }, "C04.PAYMENT.METHOD")
    methods.firstOrNull { it.optString("id") == methodId }?.let { method ->
        if (method.optString("instructions").isNotBlank()) Text("التعليمات: ${method.optString("instructions")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        val paymentData = method.optString("account_identifier")
        if (paymentData.isNotBlank()) Text("بيانات الدفع: $paymentData", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    OutlinedTextField(reference, { reference = it }, Modifier.fillMaxWidth().traceElement("C04.TRANSFER.REFERENCE"), label = { Text("رقم مرجع التحويل") }, singleLine = true)
    Button(onClick = { confirm = true }, enabled = !state.mutationBusy && packageId.isNotBlank() && methodId.isNotBlank() && reference.isNotBlank(), modifier = Modifier.fillMaxWidth().traceElement("C04.ACTION.SUBMIT")) {
        Text(if (vm.online()) "مراجعة وإرسال الطلب" else "حفظ الطلب المشفر للإرسال عند الاتصال")
    }
    if (packages.isEmpty() || methods.isEmpty()) EmptyPanel("لا تتوفر حاليًا باقات أو وسائل دفع نشطة من الخادم.")
    data.related.array("requests").objects().forEach { row -> DataCard(com.aman.customer.data.toCustomerRecord("points_purchase_requests", row), traceId = "C04.REQUEST.LIST") }
    val outbox = data.related.array("outbox").objects()
    if (outbox.isNotEmpty()) {
        SectionTitle("طلبات محلية لم تصل إلى الخادم")
        outbox.forEach { item ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (item.optString("status") == "queued") "بانتظار توفر الاتصال" else "يتطلب مراجعة قبل إعادة المحاولة", fontWeight = FontWeight.Bold)
                    KeyValue("مرجع الدفع", "••••${item.optString("payment_reference").takeLast(4)}")
                    KeyValue("وقت الحفظ المحلي", item.optString("queued_at").displayDate())
                    Text("هذا ليس طلبًا مسجلًا في الخادم، ولم تُضف نقاط. لا تُعد الدفع بسبب وجود هذا السجل المحلي.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (confirm) ConfirmAction("تأكيد إرسال طلب شراء النقاط؟", "بعد إتمام التحويل خارجيًا، يُرسل الطلب فور الاتصال أو يُحفظ مشفرًا للإرسال لاحقًا. لا تُضاف نقاط قبل اعتماد الخادم.", { confirm = false }, {
        confirm = false; vm.submitPurchase(packageId, methodId, reference)
    }, state.mutationBusy)
}

@Composable
private fun OperationsScreen(data: CustomerScreenData) {
    var search by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf("الكل") }
    var statusFilter by rememberSaveable { mutableStateOf("") }
    var fromDate by rememberSaveable { mutableStateOf("") }
    var toDate by rememberSaveable { mutableStateOf("") }
    var appliedFrom by rememberSaveable { mutableStateOf("") }
    var appliedTo by rememberSaveable { mutableStateOf("") }
    var dateError by rememberSaveable { mutableStateOf("") }

    val operationRefs = data.records.map { it.raw.optString("reference_id") }.filter(String::isNotBlank).toSet()
    val events = buildList {
        addAll(data.records)
        data.related.array("ledger").objects()
            .filter { it.optString("reference_id").isBlank() || it.optString("reference_id") !in operationRefs }
            .forEach { add(com.aman.customer.data.toCustomerRecord("point_ledger", it)) }
        data.related.array("purchases").objects()
            .filter { it.optString("id") !in operationRefs }
            .forEach { add(com.aman.customer.data.toCustomerRecord("points_purchase_requests", it)) }
        data.related.array("numbers").objects()
            .forEach { add(com.aman.customer.data.toCustomerRecord("customer_numbers", it)) }
    }.sortedByDescending { customerRecordTimestamp(it) }

    val typeOptions = listOf("الكل", "شراء نقاط", "الحماية", "إضافة رقم", "أخرى")
    val statusOptions = listOf(
        "الكل" to "", "قيد المراجعة" to "pending", "معتمد" to "approved", "مرفوض" to "rejected",
        "ناجح" to "succeeded", "فشل" to "failed", "نشط" to "active", "مؤرشف" to "archived",
    )
    val statusLabel = statusOptions.firstOrNull { it.second == statusFilter }?.first ?: "الكل"
    SectionTitle("سجل العمليات", "عمليات الحساب وحركات النقاط والطلبات والأرقام المرتبطة به.")
    OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().traceElement("C05.SEARCH"), label = { Text("بحث في السجل") }, singleLine = true)
    SimpleMenu("نوع العملية", typeOptions, typeFilter, { typeFilter = it }, "C05.FILTER.TYPE")
    SimpleMenu("الحالة", statusOptions.map { it.first }, statusLabel, { chosen -> statusFilter = statusOptions.firstOrNull { it.first == chosen }?.second.orEmpty() }, "C05.FILTER.STATUS")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(fromDate, { fromDate = it }, Modifier.weight(1f), label = { Text("من YYYY-MM-DD") }, singleLine = true)
        OutlinedTextField(toDate, { toDate = it }, Modifier.weight(1f).traceElement("C05.FILTER.DATE"), label = { Text("إلى YYYY-MM-DD") }, singleLine = true)
    }
    Button(onClick = {
        val from = runCatching { if (fromDate.isBlank()) null else java.time.LocalDate.parse(fromDate.trim()) }.getOrNull()
        val to = runCatching { if (toDate.isBlank()) null else java.time.LocalDate.parse(toDate.trim()) }.getOrNull()
        dateError = when {
            (fromDate.isNotBlank() && from == null) || (toDate.isNotBlank() && to == null) -> "أدخل التاريخ بصيغة YYYY-MM-DD."
            from != null && to != null && from.isAfter(to) -> "تاريخ البداية يجب ألا يتجاوز تاريخ النهاية."
            else -> ""
        }
        if (dateError.isBlank()) { appliedFrom = fromDate.trim(); appliedTo = toDate.trim() }
    }, modifier = Modifier.fillMaxWidth().traceElement("C05.FILTER.DATE.APPLY")) { Text("تطبيق نطاق التاريخ") }
    if (dateError.isNotBlank()) NoticeBanner(dateError, true)

    val filtered = events.filter { record ->
        val category = customerOperationCategory(record)
        val status = record.raw.optString("status")
        val searchable = (listOf(record.id, record.title, record.subtitle) + record.details.map { it.second }).joinToString(" ")
        (typeFilter == "الكل" || category == typeFilter) &&
            (statusFilter.isBlank() || status == statusFilter) &&
            searchable.contains(search.trim(), ignoreCase = true) &&
            customerDateInRange(customerRecordTimestamp(record), appliedFrom, appliedTo)
    }
    Column(Modifier.fillMaxWidth().traceElement("C05.LIST"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (filtered.isEmpty()) EmptyPanel(if (events.isEmpty()) "لا توجد عمليات أو حركات نقاط مسجلة." else "لا توجد نتائج مطابقة لعوامل البحث والتصفية.")
        filtered.forEach { DataCard(it, traceId = "C05.LIST.ROW") }
    }
}

private fun customerOperationCategory(record: com.aman.customer.data.CustomerRecord): String = when (record.source) {
    "points_purchase_requests" -> "شراء نقاط"
    "customer_numbers" -> "إضافة رقم"
    "point_ledger" -> when (record.raw.optString("entry_type")) {
        "activation_debit", "extension_debit" -> "الحماية"
        "purchase_credit" -> "شراء نقاط"
        else -> "أخرى"
    }
    "operations" -> when (record.raw.optString("operation_type")) {
        "points_approval", "points_rejection", "points_purchase" -> "شراء نقاط"
        "protection_activation", "protection_extension" -> "الحماية"
        "number_added" -> "إضافة رقم"
        else -> "أخرى"
    }
    else -> "أخرى"
}

private fun customerRecordTimestamp(record: com.aman.customer.data.CustomerRecord): String =
    record.raw.optString("created_at").ifBlank { record.raw.optString("submitted_at").ifBlank { record.raw.optString("added_at") } }

private fun customerDateInRange(timestamp: String, fromDate: String, toDate: String): Boolean {
    if (fromDate.isBlank() && toDate.isBlank()) return true
    val day = timestamp.substringBefore('T').takeIf { Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$").matches(it) } ?: return false
    return (fromDate.isBlank() || day >= fromDate) && (toDate.isBlank() || day <= toDate)
}

@Composable
private fun AddNumberScreen(data: CustomerScreenData, state: CustomerUiState, vm: CustomerViewModel) {
    var phone by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf("") }
    var saveConfirmation by remember { mutableStateOf(false) }
    var archiveConfirmation by remember { mutableStateOf("") }
    val prefixes = data.related["prefixes"] ?: JSONArray()
    val digits = phoneDigits(phone)
    val e164 = normalizePhoneE164(phone)
    val match = discoverProvider(digits, prefixes)
    val numbers = data.related.array("numbers").objects()
    val editing = numbers.firstOrNull { it.optString("id") == editingId }
    val duplicate = numbers.any { row ->
        val stored = row.optJSONObject("phone_numbers")?.let { it.optString("normalized_phone").ifBlank { it.optString("phone_e164") } }.orEmpty()
        row.optString("id") != editingId && phoneDigits(stored) == digits && digits.isNotBlank() &&
            (editing != null || row.optString("status") == "active")
    }
    val activeProtectedPhoneIds = data.related.array("protections").objects().filter { it.optString("status") == "active" }.map { it.optString("phone_number_id") }.toSet()
    LaunchedEffect(state.mutationMessage) {
        if (state.mutationMessage == "تمت إضافة الرقم إلى حسابك." || state.mutationMessage == "تم تحديث الرقم.") {
            editingId = ""; phone = ""; saveConfirmation = false
        }
        if (state.mutationMessage == "تمت أرشفة الرقم.") archiveConfirmation = ""
    }
    SectionTitle("إضافة رقم", "أدخل الرقم الدولي بصيغة E.164 مثل +[رمز الدولة][الرقم]. تُكتشف الشركة بأطول بادئة نشطة. الإضافة لا تخصم نقاطًا ولا تنشئ حماية.")
    OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth().traceElement("C06.PHONE.INPUT"), label = { Text("رقم الهاتف الدولي (+...) ") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
    when {
        phone.isBlank() -> Text("أدخل رقم الهاتف لبدء اكتشاف الشركة.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.traceElement("C06.PROVIDER.AUTO"))
        e164 == null -> Text("الصيغة غير مكتملة: يلزم + ثم 7 إلى 15 رقمًا.", color = MaterialTheme.colorScheme.error, modifier = Modifier.traceElement("C06.PROVIDER.AUTO"))
        match != null -> Text("الشركة المكتشفة: ${match.first} · البادئة المطابقة: ${match.second}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.traceElement("C06.PROVIDER.AUTO"))
        else -> Text("لم تُكتشف شركة من البادئات النشطة المتاحة.", color = MaterialTheme.colorScheme.error, modifier = Modifier.traceElement("C06.PROVIDER.AUTO"))
    }
    if (duplicate) NoticeBanner("الرقم مرتبط بالفعل بحسابك.", true)
    if (editing != null) {
        NoticeBanner("تعديل علاقة الرقم لا يغير بيانات رقم محمي. الخادم يعيد التحقق من عدم وجود حماية نشطة قبل الحفظ.", false)
        TextButton(onClick = { editingId = ""; phone = "" }) { Text("إلغاء التعديل") }
    }
    val saveAllowed = e164 != null && match != null && !duplicate && !state.mutationBusy && vm.online()
    Button(onClick = { saveConfirmation = true }, enabled = saveAllowed, modifier = Modifier.fillMaxWidth().traceElement(if (editing == null) "C06.ACTION.ADD" else "C06.ACTION.SAVE")) {
        Text(if (editing == null) "إضافة الرقم إلى حسابي" else "حفظ تعديل الرقم")
    }
    if (!vm.online()) NoticeBanner("يلزم اتصال مباشر لتنفيذ تغيير الرقم؛ لم يُحفظ طلب كتابة محليًا.", true)
    SectionTitle("أرقام الحساب")
    if (numbers.isEmpty()) EmptyPanel("لا توجد أرقام مرتبطة بحسابك.")
    numbers.forEach { row ->
        val record = com.aman.customer.data.toCustomerRecord("customer_numbers", row)
        val phoneNumberId = row.optJSONObject("phone_numbers")?.optString("id").orEmpty()
        val protected = phoneNumberId in activeProtectedPhoneIds
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().traceElement("C06.LIST").padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DataCard(record, trailing = if (row.optString("status") == "active") "نشط" else "مؤرشف")
                if (protected) NoticeBanner("لا يمكن تعديل هذا الرقم أو أرشفته ما دامت عليه حماية نشطة.", true)
                if (row.optString("status") == "active" && !protected) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NoticeBanner("التعديل والأرشفة متوقفان: SQL الجديد لا يعرّف RPC كنسيًا لهما (DATABASE_CONTRACT_GAP).", true)
                    }
                }
            }
        }
    }
    if (saveConfirmation) ConfirmAction(
        if (editing == null) "تأكيد إضافة الرقم؟" else "تأكيد تعديل الرقم؟",
        "سيعيد الخادم التحقق من الصيغة والبادئة والملكية وحالة الحماية قبل الحفظ. الرقم: ${e164.orEmpty()}",
        { saveConfirmation = false },
        { saveConfirmation = false; if (editing == null) vm.addCustomerNumber(e164.orEmpty()) else vm.updateCustomerNumber(editingId, e164.orEmpty()) },
        state.mutationBusy,
    )
    if (archiveConfirmation.isNotBlank()) ConfirmAction(
        "أرشفة الرقم؟", "ستتغير حالة علاقة الرقم إلى مؤرشف دون حذف سجل الرقم. يتحقق الخادم من عدم وجود حماية نشطة.",
        { archiveConfirmation = "" }, { val id = archiveConfirmation; archiveConfirmation = ""; vm.archiveCustomerNumber(id) }, state.mutationBusy,
    )
}

@Composable
private fun ActivateScreen(data: CustomerScreenData, state: CustomerUiState, vm: CustomerViewModel) {
    var phoneId by rememberSaveable { mutableStateOf("") }
    var daysText by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val protections = data.related.array("protections").objects().filter { it.optString("status") == "active" }.map { it.optString("phone_number_id") }.toSet()
    val numbers = data.related.array("numbers").objects().filter { it.optJSONObject("phone_numbers")?.optString("id").orEmpty() !in protections }
    val balance = data.related["balance"]?.optJSONObject(0)?.optLong("balance_points", -1L) ?: -1L
    val subscriberReady = data.related["subscriber"]?.length() ?: 0 > 0
    val selected = numbers.firstOrNull { it.optJSONObject("phone_numbers")?.optString("id") == phoneId }
    val providerId = selected?.optJSONObject("phone_numbers")?.optString("provider_id").orEmpty()
    val tariff = data.related.array("tariffs").objects().filter { it.optString("provider_id") == providerId && it.optString("status", "active") == "active" }
        .maxByOrNull { it.optString("effective_from") }
    val daily = tariff?.optInt("points_per_day", 0) ?: 0
    val days = daysText.toIntOrNull() ?: 0
    val cost = com.aman.customer.data.activationCost(days, daily)
    SectionTitle("تفعيل حماية رقم", "اختيار رقم غير محمي · مدة بالأيام · التكلفة تُحتسب وفق التعرفة المتاحة، ويعيد الخادم التحقق ذريًا.")
    NoticeBanner("SQL_REVISION_REQUIRED: عقد V7 يتطلب إنشاء activated_numbers عند أول تفعيل، بينما activate_protection في SQL الحالي يستقبل activated_number_id ولا ينشئ المرشح من customer_numbers. أوقفنا الزر لمنع إرسال phone_number_id بعقد خاطئ.", true)
    NoticeBanner("الاختيار المحلي لا يثبت أن الرقم غير محمي لدى مستخدم آخر؛ تحقق الخادم هو المعتمد ولا يحدث خصم عند رفضه.", true)
    SelectJsonItem("رقم غير محمي مرتبط بحسابك", numbers, phoneId, { row -> row.optJSONObject("phone_numbers")?.let { p -> p.optString("phone_e164").ifBlank { p.optString("normalized_phone") } } ?: "رقم غير متاح" }, { row -> phoneId = row.optJSONObject("phone_numbers")?.optString("id").orEmpty() }, "C07.PHONE.SELECT")
    OutlinedTextField(daysText, { daysText = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().traceElement("C07.DURATION.INPUT"), label = { Text("مدة الحماية بالأيام") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    KeyValue("الرصيد الحالي", if (balance < 0) "غير متاح" else "$balance نقطة")
    KeyValue("تعرفة النقاط اليومية", if (daily <= 0) "غير متاحة" else "$daily نقطة/يوم")
    KeyValue("التكلفة التقديرية", cost?.let { "$it نقطة" } ?: "أدخل رقمًا ومدة صالحة")
    KeyValue("الرصيد بعد العملية", if (balance >= 0 && cost != null && balance >= cost) "${balance - cost} نقطة" else "غير متاح / رصيد غير كافٍ")
    val estimatedStart = java.time.Instant.now()
    KeyValue("بداية الحماية المتوقعة", if (days > 0) estimatedStart.toString().displayDate() else "بعد اعتماد الخادم")
    KeyValue("نهاية الحماية المتوقعة", if (days > 0) runCatching { estimatedStart.plus(days.toLong(), java.time.temporal.ChronoUnit.DAYS).toString().displayDate() }.getOrDefault("غير متاح") else "أدخل مدة صالحة")
    Text("التاريخ والتكلفة النهائيان يحددهما الخادم عند التأكيد.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    if (!subscriberReady) NoticeBanner("يلزم اشتراك عميل نشط حسب عقد activate_protection.", true)
    if (data.errorNotes.any { it.startsWith("tariffs:") }) NoticeBanner("تعذر قراءة التعرفة. قد يتطلب الوصول تطبيق عقد القراءة الإضافي الموجود في database/002_admin_contracts.sql.", true)
    val enough = balance >= 0 && cost != null && balance >= cost
    if (cost != null && balance >= 0 && !enough) NoticeBanner("الرصيد غير كافٍ: تحتاج $cost نقطة، والمتاح $balance. اشتر نقاطًا أولًا.", true)
    if (!enough && cost != null && balance >= 0) OutlinedButton(onClick = { vm.navigate(CustomerScreen.POINTS) }, modifier = Modifier.traceElement("C07.ACTION.BUY_POINTS")) { Text("الانتقال إلى شراء النقاط") }
    val canonicalActivationContractReady = false
    val enabled = canonicalActivationContractReady && !state.mutationBusy && vm.online() && phoneId.isNotBlank() && days > 0 && daily > 0 && enough && subscriberReady
    Button(onClick = { confirm = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().traceElement("C07.ACTION.CONFIRM")) { Text(if (!vm.online()) "يلزم اتصال لتفعيل الحماية" else "مراجعة وتأكيد التفعيل") }
    if (numbers.isEmpty()) EmptyPanel("لا توجد أرقام غير محمية مرتبطة بحسابك. أضف رقمًا بعد توفر عقد الحفظ.")
    if (confirm) ConfirmAction("تأكيد تفعيل الحماية؟", "سيعيد الخادم احتساب التكلفة والرصيد، ثم يخصم النقاط وينشئ الحماية وخطة المهام في معاملة واحدة. التكلفة المقدرة: ${cost ?: "—"} نقطة.", { confirm = false }, {
        confirm = false; vm.activate(phoneId, days)
    }, state.mutationBusy)
}

@Composable
private fun ExtendScreen(data: CustomerScreenData, state: CustomerUiState, vm: CustomerViewModel) {
    var protectionId by rememberSaveable { mutableStateOf("") }
    var daysText by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val protections = data.related.array("protections").objects().filter { it.optString("status") == "active" }
    val balance = data.related["balance"]?.optJSONObject(0)?.optLong("balance_points", -1L) ?: -1L
    val selected = protections.firstOrNull { it.optString("id") == protectionId }
    val daily = selected?.optInt("points_per_day_snapshot", 0) ?: 0
    val days = daysText.toIntOrNull() ?: 0
    val cost = com.aman.customer.data.activationCost(days, daily)
    val remaining = selected?.optString("expires_at").orEmpty()
    SectionTitle("تمديد الحماية", "الأيام تضاف إلى تاريخ الانتهاء الحالي، والتكلفة من Snapshot التعرفة المحفوظ للحماية.")
    NoticeBanner("SQL_REVISION_REQUIRED: RPC extend_protection موجود بالاسم الكنسي لكن جسمه الحالي UNRESOLVED، لذلك لا نعرض تأكيدًا يوحي بنجاح الخصم أو التمديد.", true)
    SelectJsonItem("حماية نشطة مرتبطة بحسابك", protections, protectionId, { row ->
        val phone = row.optJSONObject("phone_numbers")?.optString("phone_e164").orEmpty()
        "$phone · ينتهي ${row.optString("expires_at").displayDate()}"
    }, { protectionId = it.optString("id") }, "C08.PROTECTION.SELECT")
    OutlinedTextField(daysText, { daysText = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().traceElement("C08.DURATION.INPUT"), label = { Text("أيام التمديد") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    KeyValue("تاريخ الانتهاء الحالي", remaining.displayDate().ifBlank { "غير متاح" })
    KeyValue("التعرفة اليومية", if (daily > 0) "$daily نقطة/يوم" else "غير متاحة")
    KeyValue("تكلفة التمديد", cost?.let { "$it نقطة" } ?: "أدخل مدة صالحة")
    KeyValue("الرصيد بعد العملية", if (balance >= 0 && cost != null && balance >= cost) "${balance - cost} نقطة" else "غير متاح / رصيد غير كافٍ")
    KeyValue("تاريخ الانتهاء الجديد المتوقع", if (days > 0 && remaining.isNotBlank()) runCatching {
        java.time.Instant.parse(remaining).plus(days.toLong(), java.time.temporal.ChronoUnit.DAYS).toString().displayDate()
    }.getOrDefault("غير متاح") else "أدخل مدة صالحة")
    Text("التاريخ والتكلفة الفعليان يؤكدهما الخادم ضمن عملية التمديد الذرية.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    val enough = balance >= 0 && cost != null && balance >= cost
    if (cost != null && balance >= 0 && !enough) {
        NoticeBanner("الرصيد غير كافٍ. تحتاج $cost نقطة والمتاح $balance.", true)
        OutlinedButton(onClick = { vm.navigate(CustomerScreen.POINTS) }, modifier = Modifier.traceElement("C08.ACTION.BUY_POINTS")) { Text("شراء نقاط") }
    }
    val canonicalExtensionContractReady = false
    Button(onClick = { confirm = true }, enabled = canonicalExtensionContractReady && !state.mutationBusy && vm.online() && protectionId.isNotBlank() && days > 0 && daily > 0 && enough, modifier = Modifier.fillMaxWidth().traceElement("C08.ACTION.CONFIRM")) { Text("مراجعة وتأكيد التمديد") }
    if (protections.isEmpty()) EmptyPanel("لا توجد حماية نشطة يملكها حسابك لتمديدها.")
    if (confirm) ConfirmAction("تأكيد تمديد الحماية؟", "سيخصم الخادم $cost نقطة ويضيف $days يومًا إلى تاريخ الانتهاء ثم يعيد بناء الخطة المستقبلية.", { confirm = false }, {
        confirm = false; vm.extend(protectionId, days)
    }, state.mutationBusy)
}

@Composable
private fun AlertListScreen(data: CustomerScreenData, vm: CustomerViewModel) {
    SectionTitle("تنبيهات الإدارة", "رسائل الإدارة منفصلة عن إشعارات النظام.")
    if (data.records.isEmpty()) EmptyPanel("لا توجد تنبيهات إدارية لحسابك.")
    data.records.forEach { record -> DataCard(record, trailing = if (!record.raw.optBoolean("is_read", false)) "غير مقروء" else "مقروء", onClick = { if (!record.raw.optBoolean("is_read", false)) vm.markRead(record.id) }, traceId = "C09.LIST") }
}

@Composable
private fun SupportScreen(data: CustomerScreenData, state: CustomerUiState, vm: CustomerViewModel) {
    var subject by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var newConversation by rememberSaveable { mutableStateOf(data.records.isEmpty()) }
    var verificationRequired by rememberSaveable { mutableStateOf(false) }
    var uncertainAt by rememberSaveable { mutableStateOf(data.loadedAt) }
    LaunchedEffect(state.mutationMessage) {
        if (state.mutationMessage?.startsWith("تعذر تأكيد نتيجة العملية") == true || state.mutationMessage?.startsWith("تعذر تنفيذ العملية") == true) {
            verificationRequired = true
            uncertainAt = data.loadedAt
        }
    }
    LaunchedEffect(data.loadedAt) { if (verificationRequired && data.loadedAt > uncertainAt) verificationRequired = false }
    val selectedId = if (newConversation) "" else data.selectedThreadId ?: data.records.firstOrNull()?.id.orEmpty()
    val selectedThread = data.records.firstOrNull { it.id == selectedId }
    LaunchedEffect(state.mutationMessage) {
        when (state.mutationMessage) {
            "تم إرسال الرسالة وإنشاء محادثة الدعم." -> { newConversation = false; subject = ""; body = "" }
            "تم إرسال الرسالة إلى المحادثة." -> body = ""
        }
    }
    SectionTitle("تواصل مع الإدارة", "رسائل AMAN الداخلية فقط؛ لا نفترض قناة خارجية.")
    if (!vm.online()) NoticeBanner("لا يوجد اتصال. لم تُرسل الرسالة ولم تُحفظ في outbox؛ عقد الدعم لا يوفر مفتاح idempotency آمنًا لإعادة الإرسال تلقائيًا.", true)
    else NoticeBanner("إذا انقطع الاتصال أثناء الإرسال وظهرت نتيجة غير مؤكدة، حدّث المحادثة وتحقق من ظهور الرسالة قبل المحاولة مجددًا لتجنب التكرار.", false)
    if (verificationRequired) NoticeBanner("إعادة الإرسال موقوفة مؤقتًا: حدّث سجل الخادم وتأكد أولًا من عدم وصول الرسالة السابقة.", true)
    OutlinedButton(onClick = { vm.load(CustomerScreen.SUPPORT) }, modifier = Modifier.fillMaxWidth().traceElement("C10.ACTION.REFRESH"), enabled = !state.mutationBusy) { Text("تحديث سجل المحادثات") }
    if (data.records.isNotEmpty()) {
        SectionTitle("محادثات الدعم")
        data.records.forEach { record -> DataCard(record, trailing = record.subtitle, onClick = { newConversation = false; vm.openSupportThread(record.id) }, traceId = "C10.THREAD.LIST") }
        OutlinedButton(onClick = { newConversation = true; vm.clearSupportSelection(); subject = ""; body = "" }, modifier = Modifier.fillMaxWidth().traceElement("C10.ACTION.NEW")) { Text("بدء محادثة جديدة") }
    }
    if (selectedThread != null && !newConversation) {
        SectionTitle(selectedThread.title, if (selectedThread.raw.optString("status") == "open") "المحادثة مفتوحة" else "المحادثة مغلقة")
        val messages = data.related.array("messages").objects().filter { it.optString("thread_id") == selectedId }
        if (data.messagesLoading) CircularProgressIndicator()
        if (messages.isEmpty() && !data.messagesLoading) EmptyPanel("لا توجد رسائل محملة في هذه المحادثة.")
        Column(Modifier.fillMaxWidth().traceElement("C10.THREAD.DETAIL"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            messages.forEach { message ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(if (message.optString("sender_type") == "admin") "الإدارة" else "أنت", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(message.optString("body"))
                        Text(message.optString("created_at").displayDate(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    } else if (data.records.isEmpty()) EmptyPanel("لا توجد محادثات دعم مسجلة؛ يمكنك بدء محادثة جديدة.")
    if (newConversation) OutlinedTextField(subject, { subject = it }, Modifier.fillMaxWidth().traceElement("C10.SUBJECT.INPUT"), label = { Text("الموضوع") }, singleLine = true)
    OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth().height(125.dp).traceElement("C10.MESSAGE.INPUT"), label = { Text(if (newConversation) "نص الرسالة الأولى" else "اكتب ردك") })
    if (state.mutationBusy) Text("جارٍ إرسال الرسالة…", color = MaterialTheme.colorScheme.primary)
    val canReply = newConversation || selectedThread?.raw?.optString("status") == "open"
    Button(
        onClick = { if (newConversation) vm.createSupportThread(subject, body) else vm.sendSupportMessage(selectedId, body) },
        enabled = !state.mutationBusy && !verificationRequired && vm.online() && body.isNotBlank() && canReply && (!newConversation || subject.isNotBlank()),
        modifier = Modifier.fillMaxWidth().traceElement("C10.ACTION.SEND"),
    ) { Text(if (newConversation) "إنشاء المحادثة وإرسال" else "إرسال الرد") }
    if (!newConversation && selectedThread?.raw?.optString("status") != "open") NoticeBanner("هذه المحادثة مغلقة؛ لا يمكن إرسال رد جديد.", true)
}

@Composable
private fun NotificationScreen(data: CustomerScreenData, vm: CustomerViewModel) {
    SectionTitle("إشعارات النظام", "فتح إشعار غير مقروء يحدّث is_read/read_at عبر RPC الكنسي لمزامنته بين الأجهزة.")
    if (data.records.isEmpty()) EmptyPanel("لا توجد إشعارات نظام لحسابك.")
    data.records.forEach { record -> DataCard(record, trailing = if (!record.raw.optBoolean("is_read", false)) "جديد" else "مقروء", onClick = { if (!record.raw.optBoolean("is_read", false)) vm.markRead(record.id) }, traceId = "C11.LIST") }
}

@Composable
private fun ReportsScreen(data: CustomerScreenData, state: CustomerUiState, vm: CustomerViewModel) {
    var categoryLabel by rememberSaveable { mutableStateOf(CustomerReportCategory.POINTS.label) }
    var appliedCategoryLabel by rememberSaveable { mutableStateOf(CustomerReportCategory.POINTS.label) }
    var fromDate by rememberSaveable { mutableStateOf("") }
    var toDate by rememberSaveable { mutableStateOf("") }
    var appliedFromDate by rememberSaveable { mutableStateOf("") }
    var appliedToDate by rememberSaveable { mutableStateOf("") }
    var filterError by remember { mutableStateOf<String?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val reportRows = buildCustomerReportRows(data, CustomerReportCategory.fromLabel(appliedCategoryLabel), appliedFromDate, appliedToDate)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) {
            exportMessage = "تم إلغاء التصدير."
        } else {
            runCatching {
                val output = context.contentResolver.openOutputStream(uri) ?: error("تعذر فتح ملف التصدير.")
                OutputStreamWriter(output, Charsets.UTF_8).use { writer -> writer.write("\uFEFF"); writer.write(customerReportCsv(reportRows)) }
            }.onSuccess { exportMessage = "تم تصدير ${reportRows.size} سجلًا إلى الملف المحدد." }
                .onFailure { exportMessage = "تعذر حفظ التقرير: ${it.message ?: "خطأ في مزود الملفات"}" }
        }
    }
    SectionTitle("تقارير حسابك", "مصادر مملوكة للحساب وتسمح بها RLS؛ لا تُقرأ دفاتر الإدارة أو مهام السداد الداخلية.")
    SimpleMenu("نوع التقرير", CustomerReportCategory.entries.map { it.label }, categoryLabel,
        choose = { categoryLabel = it }, elementId = "C12.REPORT.CATEGORY")
    SimpleMenu("نطاق سريع", listOf("كل الفترات", "آخر 30 يومًا", "آخر 90 يومًا"),
        if (fromDate.isBlank() && toDate.isBlank()) "كل الفترات" else if (fromDate == java.time.LocalDate.now().minusDays(29).toString()) "آخر 30 يومًا" else "آخر 90 يومًا",
        choose = { range ->
            val days = when (range) { "آخر 30 يومًا" -> 30L; "آخر 90 يومًا" -> 90L; else -> 0L }
            fromDate = if (days == 0L) "" else java.time.LocalDate.now().minusDays(days - 1).toString()
            toDate = if (days == 0L) "" else java.time.LocalDate.now().toString()
        }, elementId = "C12.PERIOD.QUICK")
    OutlinedTextField(fromDate, { fromDate = it.take(10); filterError = null }, Modifier.fillMaxWidth().traceElement("C12.DATE.FROM"), label = { Text("من تاريخ (YYYY-MM-DD، اختياري)") }, singleLine = true)
    OutlinedTextField(toDate, { toDate = it.take(10); filterError = null }, Modifier.fillMaxWidth().traceElement("C12.DATE.TO"), label = { Text("إلى تاريخ (YYYY-MM-DD، اختياري)") }, singleLine = true)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            if (isValidCustomerReportRange(fromDate, toDate)) {
                appliedCategoryLabel = categoryLabel; appliedFromDate = fromDate; appliedToDate = toDate; filterError = null
            } else filterError = "أدخل تاريخين بصيغة YYYY-MM-DD، واجعل تاريخ البداية قبل أو يساوي النهاية."
        }, modifier = Modifier.weight(1f).traceElement("C12.ACTION.VIEW"), enabled = !state.mutationBusy) { Text("عرض التقرير") }
        OutlinedButton(onClick = { vm.load(CustomerScreen.REPORTS) }, modifier = Modifier.weight(1f).traceElement("C12.ACTION.REFRESH"), enabled = !state.mutationBusy) { Text("تحديث البيانات") }
    }
    filterError?.let { NoticeBanner(it, true) }
    exportMessage?.let { NoticeBanner(it, it.startsWith("تعذر")) }
    val category = CustomerReportCategory.fromLabel(appliedCategoryLabel)
    when (category) {
        CustomerReportCategory.POINTS -> {
            val pointValues = reportRows.mapNotNull { it.points.toLongOrNull() }
            KeyValue("إضافات النقاط", "${pointValues.filter { it > 0 }.sum()} نقطة")
            KeyValue("استخدام النقاط", "${-pointValues.filter { it < 0 }.sum()} نقطة")
            KeyValue("صافي حركة النقاط", "${pointValues.sum()} نقطة")
            data.related["balance"]?.optJSONObject(0)?.opt("balance_points")?.let { KeyValue("الرصيد الحالي", "$it نقطة") }
        }
        CustomerReportCategory.PURCHASES -> {
            KeyValue("طلبات الشراء ضمن الفترة", reportRows.size.toString())
            KeyValue("النقاط المطلوبة", "${reportRows.sumOf { it.points.toLongOrNull() ?: 0L }} نقطة")
        }
        CustomerReportCategory.PROTECTIONS -> KeyValue("عمليات التفعيل/التمديد", reportRows.size.toString())
        CustomerReportCategory.FINANCIAL -> KeyValue("عمليات مالية مسجلة للحساب", reportRows.size.toString())
        CustomerReportCategory.NUMBERS -> KeyValue("علاقات أرقام الحساب", reportRows.size.toString())
    }
    if (reportRows.isEmpty()) EmptyPanel("لا توجد بيانات فعلية ضمن الفئة والفترة المختارتين.")
    reportRows.forEach { ReportRowCard(it) }
    OutlinedButton(
        onClick = {
            if (!isValidCustomerReportRange(appliedFromDate, appliedToDate)) filterError = "نطاق التقرير غير صالح."
            else exportLauncher.launch("aman-report-${category.name.lowercase()}-${java.time.LocalDate.now()}.csv")
        },
        enabled = !state.mutationBusy,
        modifier = Modifier.fillMaxWidth().traceElement("C12.ACTION.EXPORT"),
    ) { Text("تصدير التقرير الحالي CSV (${reportRows.size} سجل)") }
}

@Composable
private fun ReportRowCard(row: com.aman.customer.data.CustomerReportRow) {
    Card(Modifier.fillMaxWidth().traceElement("C12.RESULTS"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(row.type.ifBlank { row.category }, fontWeight = FontWeight.SemiBold)
            if (row.timestamp.isNotBlank()) KeyValue("التاريخ", row.timestamp.displayDate())
            if (row.status.isNotBlank()) KeyValue("الحالة", row.status)
            if (row.points.isNotBlank()) KeyValue("النقاط", row.points)
            if (row.amount.isNotBlank()) KeyValue("المبلغ", "${row.amount} ${row.currency}".trim())
            if (row.details.isNotBlank()) Text(row.details, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AccountScreen(data: CustomerScreenData, vm: CustomerViewModel) {
    SectionTitle("حساب العميل", "بيانات الحساب من الملف المرتبط بهوية الدخول.")
    Text("ملف الحساب", Modifier.traceElement("C13.PROFILE"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    Text("إعدادات الحساب", Modifier.traceElement("C13.ACCOUNT.SETTINGS"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    NoticeBanner("DATABASE_CONTRACT_GAP: V7 يعرّف قسم إعدادات الحساب وإعدادات الأمان، لكن SQL الكنسي الحالي لا يعرّف RPC أو جداول آمنة لتغييرهما؛ لا نعرض أفعالًا وهمية.", true)
    Text("إعدادات الأمان", Modifier.traceElement("C13.SECURITY.SETTINGS"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    val profile = data.related["profile"]?.optJSONObject(0)
    val subscriber = data.related["subscriber"]?.optJSONObject(0)
    val balance = data.related["balance"]?.optJSONObject(0)?.optString("balance_points")
    if (profile == null) EmptyPanel("لم يُعثر على ملف profiles مرتبط بحساب المصادقة. تحقق من إعداد عقد إنشاء profile في الخلفية.")
    profile?.let {
        KeyValue("الاسم", it.optString("full_name").ifBlank { "غير محدد" })
        KeyValue("اسم المستخدم", it.optString("username").ifBlank { "غير محدد" })
        KeyValue("البريد الإلكتروني", it.optString("email").ifBlank { "غير متاح" })
        KeyValue("رقم الهاتف", it.optString("phone").ifBlank { "غير متاح" })
        KeyValue("تاريخ الإنشاء", it.optString("created_at").displayDate())
        KeyValue("حالة الحساب", it.optString("account_status").ifBlank { "غير متاحة" })
    }
    KeyValue("حالة الاشتراك", subscriber?.optString("status")?.ifBlank { "غير متاح" } ?: "غير مشترك/غير متاح")
    KeyValue("رصيد النقاط", balance?.let { "$it نقطة" } ?: "غير متاح")
    NoticeBanner("إعدادات كلمة المرور/الأمان غير معروضة لعدم وجود تدفق تغيير معتمد داخل المرجع. المصادقة وإدارة كلمات المرور عبر Supabase Auth.", false)
    Button(onClick = vm::signOut, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth().traceElement("C13.ACTION.LOGOUT")) { Text("تسجيل الخروج") }
}

@Composable
private fun SearchScreen(data: CustomerScreenData, vm: CustomerViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    SectionTitle("البحث في حسابك", "الأرقام والعمليات وطلبات شراء النقاط والتنبيهات والإشعارات الخاصة بك فقط.")
    OutlinedTextField(query, { query = it; vm.search(it) }, Modifier.fillMaxWidth().traceElement("C14.SEARCH.INPUT"), label = { Text("ابحث في بيانات حسابك") }, singleLine = true)
    TextButton(onClick = { query = ""; vm.search("") }, modifier = Modifier.traceElement("C14.SEARCH.CLEAR")) { Text("مسح البحث") }
    if (query.trim().length < 2) EmptyPanel("أدخل حرفين على الأقل لبدء البحث ضمن بياناتك.")
    else if (data.records.isEmpty()) EmptyPanel("لا توجد نتائج مطابقة في البيانات المتاحة.")
    data.records.forEach { DataCard(it, traceId = "C14.RESULT.LIST") }
}

@Composable
private fun AboutScreen() {
    SectionTitle("عن أمان")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("AMAN  |  أمان", Modifier.traceElement("C15.LOGO"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("أمان حماية وضمان", Modifier.traceElement("C15.SERVICE.INFO"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("خدمة لإدارة حماية أرقام الاتصالات ومتابعة الاشتراك والخدمات المرتبطة بها وفق قواعد AMAN.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            KeyValue("الإصدار", "1.0.0")
            Text("بيانات الجهة وروابط الشروط والخصوصية والتواصل الرسمي غير مضافة إلى المرجع المعتمد.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SelectJsonItem(label: String, options: List<JSONObject>, selectedId: String, display: (JSONObject) -> String, onSelected: (JSONObject) -> Unit, elementId: String? = null) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.optString("id") == selectedId }
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().then(if (elementId == null) Modifier else Modifier.traceElement(elementId)), enabled = options.isNotEmpty()) {
            Text(if (selected == null) "$label · ${if (options.isEmpty()) "لا تتوفر خيارات" else "اختر"}" else display(selected), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { item -> DropdownMenuItem(text = { Text(display(item)) }, onClick = { expanded = false; onSelected(item) }) }
        }
    }
}

@Composable
private fun SimpleMenu(label: String, options: List<String>, selected: String, choose: (String) -> Unit, elementId: String? = null) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().then(if (elementId == null) Modifier else Modifier.traceElement(elementId))) { Text("$label: $selected") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; choose(option) }) }
        }
    }
}

@Composable
private fun ConfirmAction(title: String, message: String, cancel: () -> Unit, confirm: () -> Unit, busy: Boolean) {
    AlertDialog(onDismissRequest = { if (!busy) cancel() }, title = { Text(title) }, text = { Text(message) },
        confirmButton = { Button(onClick = confirm, enabled = !busy) { if (busy) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp) else Text("تأكيد") } },
        dismissButton = { TextButton(onClick = cancel, enabled = !busy) { Text("إلغاء") } })
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(value, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun EmptyPanel(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp)) {
        Text(message, Modifier.fillMaxWidth().padding(15.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun fieldName(key: String) = mapOf(
    "status" to "الحالة", "expires_at" to "تاريخ الانتهاء", "started_at" to "تاريخ البداية", "duration_days" to "المدة بالأيام",
    "points_per_day_snapshot" to "التعرفة وقت التفعيل", "total_points_snapshot" to "إجمالي نقاط الحماية", "points_amount_snapshot" to "النقاط المطلوبة",
    "price_amount_snapshot" to "المبلغ", "currency_snapshot" to "العملة", "payment_method_name_snapshot" to "وسيلة الدفع", "request_number" to "رقم الطلب",
    "submitted_at" to "تاريخ الإرسال", "reviewed_at" to "تاريخ المراجعة", "rejection_reason" to "سبب الرفض", "operation_type" to "نوع العملية",
    "points_delta" to "تغير النقاط", "money_amount" to "المبلغ", "type" to "نوع الإشعار", "title" to "العنوان", "body" to "المحتوى",
    "read_at" to "تاريخ القراءة", "subject" to "الموضوع", "updated_at" to "آخر تحديث", "balance_points" to "الرصيد", "amount_points" to "النقاط",
    "balance_after" to "الرصيد بعد الحركة", "entry_type" to "نوع الحركة", "description" to "التفاصيل", "name" to "الاسم", "points_amount" to "عدد النقاط",
    "price_amount" to "السعر", "currency" to "العملة", "instructions" to "التعليمات", "prefix" to "بادئة الشركة", "points_per_day" to "نقاط/يوم", "added_at" to "تاريخ الإضافة"
)[key] ?: key
private fun String.displayValue() = if (this.contains("T") && this.length > 12) displayDate() else this
private fun String.displayDate() = replace("T", " ").substringBefore(".").removeSuffix("Z")
private fun periodMatch(value: String, period: String): Boolean {
    if (period == "كل الفترات") return true
    val timestamp = runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
    val days = if (period.contains("30")) 30 else 90
    return timestamp >= System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
}
