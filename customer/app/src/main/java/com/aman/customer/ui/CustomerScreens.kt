package com.aman.customer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aman.customer.data.CustomerRecord
import com.aman.customer.data.CustomerReportCategory
import com.aman.customer.data.CustomerScreen
import com.aman.customer.data.CustomerScreenData
import com.aman.customer.data.CustomerUiState
import com.aman.customer.data.array
import com.aman.customer.data.buildCustomerReportRows
import com.aman.customer.data.calculateProtectionQuote
import com.aman.customer.data.customerReportCsv
import com.aman.customer.data.discoverProvider
import com.aman.customer.data.isValidCustomerReportRange
import com.aman.customer.data.normalizePhoneE164
import com.aman.customer.data.objects
import com.aman.customer.data.phoneDigits
import com.aman.customer.data.text
import com.aman.customer.data.toCustomerRecord
import com.aman.customer.data.traceElement
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun CustomerScreenContent(state: CustomerUiState, vm: CustomerViewModel, modifier: Modifier = Modifier) {
    val data = state.data
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).traceElement(state.screen.id),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (data == null && state.screen != CustomerScreen.ABOUT) {
            EmptyPanel(if (state.phase.name == "ERROR") "تعذر تحميل هذه الشاشة." else "جارٍ تحميل بيانات الحساب…")
            OutlinedButton(onClick = { vm.load(state.screen) }) { Text("إعادة المحاولة") }
        } else when (state.screen) {
            CustomerScreen.HOME -> HomeScreen(data!!, vm)
            CustomerScreen.ADD_NUMBER -> AddNumberScreen(data!!, state, vm)
            CustomerScreen.BUY_POINTS -> BuyPointsScreen(data!!, state, vm)
            CustomerScreen.POINTS_HISTORY -> PointsHistoryScreen(data!!)
            CustomerScreen.ADDED_NUMBERS -> AddedNumbersScreen(data!!, vm)
            CustomerScreen.ACTIVE_NUMBERS -> ProtectionListScreen(data!!, vm, expired = false)
            CustomerScreen.EXPIRED_NUMBERS -> ProtectionListScreen(data!!, vm, expired = true)
            CustomerScreen.ACTIVATE -> ProtectionActionScreen(data!!, state, vm, CustomerScreen.ACTIVATE)
            CustomerScreen.EXTEND -> ProtectionActionScreen(data!!, state, vm, CustomerScreen.EXTEND)
            CustomerScreen.RENEW -> ProtectionActionScreen(data!!, state, vm, CustomerScreen.RENEW)
            CustomerScreen.NOTIFICATIONS -> NotificationsScreen(data!!, vm)
            CustomerScreen.SUPPORT -> SupportScreen(data!!, state, vm)
            CustomerScreen.SEARCH -> SearchScreen(data!!, vm)
            CustomerScreen.REPORTS -> ReportsScreen(data!!, state, vm)
            CustomerScreen.ACCOUNT -> AccountScreen(data!!, state, vm)
            CustomerScreen.ABOUT -> AboutScreen(data)
            else -> EmptyPanel("هذه الشاشة مخصصة لتدفق المصادقة.")
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(3.dp).height(if (subtitle == null) 22.dp else 34.dp)
            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    if (value.isNotBlank()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.42f))
        Text(value, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.58f))
    }
}

@Composable
private fun EmptyPanel(message: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Text(message, Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DataCard(record: CustomerRecord, traceId: String? = null, onClick: (() -> Unit)? = null) {
    var expanded by rememberSaveable(record.id) { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (traceId != null) Modifier.traceElement(traceId) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(record.title.ifBlank { "سجل" }, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (record.subtitle.isNotBlank()) Text(record.subtitle, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            val keys = setOf("display_phone", "public_user_code", "public_purchase_code", "points_snapshot", "points_cost_snapshot", "duration_days", "start_at", "end_at", "created_at", "submitted_at", "added_at", "balance_after", "description", "rejection_reason", "body", "status")
            record.details.filter { (key, value) -> key in keys && value.isNotBlank() }.take(if (expanded) 12 else 3).forEach { (key, value) ->
                KeyValue(fieldName(key), displayValue(value))
            }
            if (record.details.size > 3) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "إخفاء التفاصيل" else "التفاصيل") }
        }
    }
}

@Composable
private fun HomeScreen(data: CustomerScreenData, vm: CustomerViewModel) {
    val profile = data.related.array("profile").optJSONObject(0)
    val balance = data.related.array("balance").optJSONObject(0)?.let { it.opt("balance") ?: it.opt("balance_points") }?.toString()
    val unread = data.related.array("notifications").objects().count { !it.optBoolean("is_read", false) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(profile?.text("name")?.let { "أهلًا، $it" } ?: "مرحبًا بك في أمان", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("رصيد النقاط", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(balance?.let { "$it نقطة" } ?: "غير متاح حاليًا", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("إشعارات غير مقروءة: $unread", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    SectionTitle("خدماتك")
    val tiles = listOf(
        CustomerScreen.ADD_NUMBER, CustomerScreen.BUY_POINTS, CustomerScreen.POINTS_HISTORY,
        CustomerScreen.ADDED_NUMBERS, CustomerScreen.ACTIVE_NUMBERS, CustomerScreen.EXPIRED_NUMBERS,
        CustomerScreen.ACTIVATE, CustomerScreen.EXTEND, CustomerScreen.RENEW,
    )
    tiles.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { screen ->
                Card(Modifier.weight(1f).heightIn(min = 82.dp).clickable { vm.navigate(screen) }.traceElement("C04.QUICK_ACTIONS.${screen.id}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.Center) {
                        Text(screen.id, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        Text(screen.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, maxLines = 2)
                    }
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { vm.navigate(CustomerScreen.NOTIFICATIONS) }, Modifier.weight(1f).traceElement("C04.NOTIFICATIONS")) { Text("الإشعارات") }
        OutlinedButton(onClick = { vm.navigate(CustomerScreen.SUPPORT) }, Modifier.weight(1f)) { Text("تواصل أمان") }
    }
    SectionTitle("أحدث الأحداث", "ملخص للأحداث المسجلة؛ لا يُنشأ له سجل أعمال مستقل.")
    val latest = data.records.sortedByDescending { it.raw.optString("created_at") }.take(5)
    if (latest.isEmpty()) EmptyPanel("لا توجد أحداث مسجلة في الحساب حتى الآن.")
    latest.forEach { DataCard(it, traceId = "C04.RECENT") }
}

@Composable
private fun AddNumberScreen(data: CustomerScreenData, state: CustomerUiState, vm: CustomerViewModel) {
    val editId = vm.selectedCustomerNumberId.orEmpty()
    val existing = data.related.array("numbers").objects().firstOrNull { it.optString("id") == editId }
    val initialPhone = existing?.optString("display_phone").orEmpty().ifBlank { existing?.optJSONObject("phone_number")?.optString("display_phone").orEmpty() }
    var phone by rememberSaveable(editId) { mutableStateOf(initialPhone) }
    var removeId by rememberSaveable { mutableStateOf("") }
    var confirmSave by remember { mutableStateOf(false) }
    val prefixes = data.related.array("prefixes")
    val normalized = normalizePhoneE164(phone)
    val match = discoverProvider(phoneDigits(phone), prefixes)
    val alreadyOwned = data.related.array("numbers").objects().any { row ->
        row.optString("id") != editId && phoneDigits(row.optString("display_phone").ifBlank { row.optJSONObject("phone_number")?.optString("display_phone").orEmpty() }) == phoneDigits(phone) && phone.isNotBlank()
    }
    LaunchedEffect(state.mutationMessage) {
        if (state.mutationMessage?.startsWith("تمت إضافة") == true || state.mutationMessage?.startsWith("تم تحديث الرقم") == true) {
            phone = ""
        }
    }
    SectionTitle(if (editId.isBlank()) "إضافة رقم" else "تعديل رقم", "تُكتشف الشركة بأطول بادئة نشطة. لا تخصم الإضافة نقاطًا ولا تنشئ حماية.")
    OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth().traceElement("C05.PHONE"), label = { Text("رقم دولي بصيغة +…") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
    when {
        phone.isBlank() -> Text("أدخل الرقم لعرض الشركة المكتشفة.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        normalized == null -> NoticeBanner("الصيغة غير مكتملة: استخدم + ثم 7 إلى 15 رقمًا.", true)
        alreadyOwned -> NoticeBanner("هذا الرقم مرتبط بالفعل بحسابك.", true)
        match != null -> Text("الشركة: ${match.first} · البادئة: ${match.second}", color = MaterialTheme.colorScheme.primary)
        else -> NoticeBanner("لم تُكتشف شركة من البادئات النشطة المتاحة.", true)
    }
    Button(onClick = { confirmSave = true }, modifier = Modifier.fillMaxWidth().traceElement(if (editId.isBlank()) "C05.ADD" else "C05.UPDATE"),
        enabled = !state.mutationBusy && normalized != null && !alreadyOwned && match != null) {
        Text(if (editId.isBlank()) "إضافة الرقم" else "حفظ تعديل الرقم")
    }
    if (editId.isNotBlank()) TextButton(onClick = { vm.navigate(CustomerScreen.ADDED_NUMBERS) }) { Text("إلغاء التعديل") }
    if (data.related.array("numbers").length() > 0) {
        SectionTitle("أرقام الحساب", "يمكن تعديل الرقم أو أرشفته قبل إنشاء سجل حماية.")
        data.related.array("numbers").objects().forEach { row ->
            val record = toCustomerRecord("customer_number", row)
            DataCard(record)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.editCustomerNumber(row.optString("id")) }, enabled = !state.mutationBusy, modifier = Modifier.traceElement("C05.UPDATE")) { Text("تعديل") }
                TextButton(onClick = { removeId = row.optString("id") }, enabled = !state.mutationBusy, modifier = Modifier.traceElement("C05.DELETE")) { Text("أرشفة") }
            }
        }
    }
    if (confirmSave) ConfirmAction(
        if (editId.isBlank()) "تأكيد إضافة الرقم؟" else "تأكيد تعديل الرقم؟",
        "سيعيد الخادم التحقق من الملكية والبادئة وحالة الحماية قبل الحفظ.",
        { confirmSave = false },
        { confirmSave = false; if (editId.isBlank()) vm.addCustomerNumber(phone) else vm.updateCustomerNumber(editId, phone) },
        state.mutationBusy,
    )
    if (removeId.isNotBlank()) ConfirmAction("أرشفة الرقم؟", "لن يُحذف سجل تاريخي؛ يرفض الخادم الأرشفة إذا سبق إنشاء حماية لهذا الرقم.",
        { removeId = "" }, { val id = removeId; removeId = ""; vm.deleteCustomerNumber(id) }, state.mutationBusy)
}

@Composable
private fun BuyPointsScreen(data: CustomerScreenData, state: CustomerUiState, vm: CustomerViewModel) {
    var packageId by rememberSaveable { mutableStateOf("") }
    var methodId by rememberSaveable { mutableStateOf("") }
    var reference by rememberSaveable { mutableStateOf("") }
    var packageQuery by rememberSaveable { mutableStateOf("") }
    var purchaseQuery by rememberSaveable { mutableStateOf("") }
    var purchaseStatus by rememberSaveable { mutableStateOf("الكل") }
    var confirm by remember { mutableStateOf(false) }
    val packages = data.related.array("packages").objects().filter { (it.optString("name") + it.optString("description")).contains(packageQuery.trim(), true) }
    val purchases = data.related.array("purchases").objects().filter { row ->
        (row.optString("public_purchase_code") + row.optString("status") + row.optString("submitted_at")).contains(purchaseQuery.trim(), true) &&
            (purchaseStatus == "الكل" || row.optString("status").equals(when (purchaseStatus) { "قيد المراجعة" -> "PENDING"; "معتمد" -> "APPROVED"; else -> "REJECTED" }, true))
    }
    val methods = data.related.array("methods").objects()
    SectionTitle("شراء النقاط", "يُنشأ الطلب بحالة PENDING. لا يُضاف الرصيد قبل اعتماد الطلب من الإدارة.")
    val balance = data.related.array("balance").optJSONObject(0)?.let { it.opt("balance") ?: it.opt("balance_points") }?.toString().orEmpty()
    KeyValue("الرصيد الحالي", if (balance.isBlank()) "غير متاح" else "$balance نقطة")
    OutlinedTextField(packageQuery, { packageQuery = it }, Modifier.fillMaxWidth().traceElement("C06.PACKAGE_SEARCH"), label = { Text("بحث في الباقات") }, singleLine = true)
    JsonItemMenu("الباقة", packages, packageId, { row -> "${row.optString("name")} · ${row.optString("points")} نقطة · ${row.optString("price")} ${row.optString("currency")}" }, { packageId = it.optString("id") }, "C06.PACKAGE")
    JsonItemMenu("وسيلة الدفع", methods, methodId, { it.optString("name") }, { methodId = it.optString("id") }, "C06.METHOD")
    methods.firstOrNull { it.optString("id") == methodId }?.let { method ->
        if (method.optString("transfer_instructions").isNotBlank()) KeyValue("التعليمات", method.optString("transfer_instructions"))
        if (method.optString("receiving_account").isNotBlank()) KeyValue("بيانات الاستلام", method.optString("receiving_account"))
    }
    OutlinedTextField(reference, { reference = it }, Modifier.fillMaxWidth().traceElement("C06.REFERENCE"), label = { Text("مرجع التحويل") }, singleLine = true)
    Button(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().traceElement("C06.SUBMIT"),
        enabled = !state.mutationBusy && packageId.isNotBlank() && methodId.isNotBlank() && reference.isNotBlank() && vm.online()) { Text("مراجعة وإرسال الطلب") }
    if (packages.isEmpty() || methods.isEmpty()) EmptyPanel("لا تتوفر باقات أو وسائل دفع نشطة.")
    SectionTitle("طلبات الشراء")
    OutlinedTextField(purchaseQuery, { purchaseQuery = it }, Modifier.fillMaxWidth().traceElement("C06.PURCHASE_SEARCH"), label = { Text("بحث في الطلبات") }, singleLine = true)
    SimpleMenu("حالة الطلب", listOf("الكل", "قيد المراجعة", "معتمد", "مرفوض"), purchaseStatus, { purchaseStatus = it }, "C06.PURCHASE_FILTER")
    if (purchases.isEmpty()) EmptyPanel(if (data.related.array("purchases").length() == 0) "لا توجد طلبات شراء مسجلة." else "لا توجد طلبات مطابقة.")
    purchases.forEach { row -> DataCard(toCustomerRecord("points_purchase", row), traceId = "C06.PURCHASES") }
    if (confirm) ConfirmAction("تأكيد طلب شراء النقاط؟", "تأكد من إتمام التحويل خارجيًا. الطلب لا يضيف نقاطًا إلا بعد الاعتماد.",
        { confirm = false }, { confirm = false; vm.submitPurchase(packageId, methodId, reference) }, state.mutationBusy)
}

@Composable
private fun PointsHistoryScreen(data: CustomerScreenData) {
    var query by rememberSaveable { mutableStateOf("") }
    var direction by rememberSaveable { mutableStateOf("الكل") }
    var selected by rememberSaveable { mutableStateOf("") }
    val rows = data.related.array("ledger").objects().filter { row ->
        val searchable = listOf(row.optString("entry_type"), row.optString("description"), row.optString("source_type"), row.optString("amount"), row.optString("created_at")).joinToString(" ")
        searchable.contains(query.trim(), true) && (direction == "الكل" || (direction == "إضافة" && row.optString("direction").equals("CREDIT", true)) || (direction == "استخدام" && row.optString("direction").equals("DEBIT", true)))
    }
    SectionTitle("حركة النقاط", "سجل تاريخي غير قابل للتعديل؛ التصفية تخص القائمة المعروضة فقط.")
    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().traceElement("C07.SEARCH"), label = { Text("بحث في الحركة") }, singleLine = true)
    SimpleMenu("نوع الحركة", listOf("الكل", "إضافة", "استخدام"), direction, { direction = it }, "C07.FILTER")
    if (rows.isEmpty()) EmptyPanel(if (data.related.array("ledger").length() == 0) "لا توجد حركات نقاط." else "لا توجد نتائج مطابقة.")
    rows.forEach { row ->
        val record = toCustomerRecord("points_ledger", row)
        DataCard(record, traceId = "C07.LIST", onClick = { selected = row.optString("id") })
        if (selected == row.optString("id")) Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionTitle("تفاصيل الحركة")
                KeyValue("النوع", row.optString("entry_type"))
                KeyValue("الاتجاه", row.optString("direction"))
                KeyValue("المبلغ", row.optString("amount"))
                KeyValue("الرصيد قبل", row.optString("balance_before"))
                KeyValue("الرصيد بعد", row.optString("balance_after"))
                KeyValue("التاريخ", formatDate(row.optString("created_at")))
                KeyValue("المرجع", row.optString("source_type") + " · " + row.optString("source_id"))
            }
        }
    }
}

@Composable
private fun AddedNumbersScreen(data: CustomerScreenData, vm: CustomerViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf("الكل") }
    val rows = data.related.array("numbers").objects().filter { row ->
        (row.optString("display_phone") + row.optString("public_added_number_code") + row.optString("status")).contains(query.trim(), true) &&
            (statusFilter == "الكل" || row.optString("status").equals(when (statusFilter) { "نشط" -> "ACTIVE"; "مؤرشف" -> "ARCHIVED"; else -> "INACTIVE" }, true))
    }
    SectionTitle("الأرقام المضافة", "قائمة قراءة فقط؛ التعديل والأرشفة والتفعيل تتم من الإجراء المرتبط بالسجل.")
    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().traceElement("C08.SEARCH"), label = { Text("بحث في الأرقام") }, singleLine = true)
    SimpleMenu("الحالة", listOf("الكل", "نشط", "غير نشط", "مؤرشف"), statusFilter, { statusFilter = it }, "C08.FILTER")
    if (rows.isEmpty()) EmptyPanel(if (data.related.array("numbers").length() == 0) "لا توجد أرقام مضافة." else "لا توجد نتائج مطابقة.")
    rows.forEach { row ->
        DataCard(toCustomerRecord("customer_number", row), traceId = "C08.LIST")
        KeyValue("حالة الرقم", row.optString("status").ifBlank { "غير متاحة" })
        KeyValue("حالة الحماية", if (row.optBoolean("active_protection", false)) "حماية نشطة" else "لا توجد حماية نشطة")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { vm.editCustomerNumber(row.optString("id")) }, modifier = Modifier.weight(1f)) { Text("تعديل · C05") }
            if (row.optString("status").equals("ACTIVE", true) && !row.optBoolean("active_protection", false)) {
                TextButton(onClick = { vm.beginActivation(row.optString("id")) }, modifier = Modifier.weight(1f)) { Text("تفعيل · C11") }
            }
        }
    }
}

@Composable
private fun ProtectionListScreen(data: CustomerScreenData, vm: CustomerViewModel, expired: Boolean) {
    var query by rememberSaveable(expired) { mutableStateOf("") }
    var companyFilter by rememberSaveable(expired) { mutableStateOf("كل الشركات") }
    val allRows = data.related.array("protections").objects().filter { it.optString("effective_status").equals(if (expired) "EXPIRED" else "ACTIVE", true) }
    val companies = allRows.map { it.optString("company_name").ifBlank { "غير محددة" } }.distinct().sorted()
    val rows = data.related.array("protections").objects().filter { row ->
        row.optString("effective_status").equals(if (expired) "EXPIRED" else "ACTIVE", true) &&
            (row.optString("display_phone") + row.optString("company_name") + row.optString("public_activation_code")).contains(query.trim(), true) &&
            (companyFilter == "كل الشركات" || row.optString("company_name").ifBlank { "غير محددة" } == companyFilter)
    }
    SectionTitle(if (expired) "الأرقام المنتهية" else "الأرقام النشطة", "الحالة وتاريخ الانتهاء مصدرهما الخادم.")
    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().traceElement(if (expired) "C10.SEARCH" else "C09.SEARCH"), label = { Text("بحث") }, singleLine = true)
    SimpleMenu("شركة الاتصالات", listOf("كل الشركات") + companies, companyFilter, { companyFilter = it }, if (expired) "C10.FILTER" else "C09.FILTER")
    if (rows.isEmpty()) EmptyPanel(if (expired) "لا توجد حماية منتهية." else "لا توجد حماية نشطة.")
    rows.forEach { row ->
        DataCard(toCustomerRecord("protection_period", row), traceId = if (expired) "C10.LIST" else "C09.LIST")
        Text("الشركة: ${row.optString("company_name").ifBlank { "غير متاحة" }} · المدة: ${row.optString("duration_days")} يوم", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!expired && row.optBoolean("needs_extension", false)) {
            NoticeBanner("يحتاج هذا الرقم إلى التمديد وفق إعداد الشركة: ${row.optInt("remaining_days")} يومًا متبقيًا، وحد التنبيه ${row.optInt("extension_warning_days")} يومًا.", true)
        }
        ExtensionHistory(row.optString("id"), data.related.array("extensions").objects())
        TextButton(onClick = { if (expired) vm.beginRenewal(row.optString("id")) else vm.beginExtension(row.optString("id")) },
            modifier = Modifier.traceElement(if (expired) "C10.RENEW" else "C09.EXTEND")) {
            Text(if (expired) "تجديد الحماية · C13" else "تمديد الحماية · C12")
        }
    }
}

@Composable
private fun ExtensionHistory(protectionId: String, extensions: List<JSONObject>) {
    val rows = extensions.filter { it.optString("protection_period_id") == protectionId }
    if (rows.isNotEmpty()) {
        SectionTitle("سجل التمديد", "يبقى تاريخ الانتهاء السابق محفوظًا مع كل عملية تمديد مؤكدة.")
        rows.forEach { extension ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    KeyValue("الانتهاء قبل التمديد", formatDate(extension.optString("previous_end_at")))
                    KeyValue("الانتهاء بعد التمديد", formatDate(extension.optString("new_end_at")))
                    KeyValue("الأيام المضافة", extension.optString("days_added"))
                    KeyValue("تكلفة التمديد", extension.optString("points_cost"))
                }
            }
        }
    }
}

@Composable
private fun ProtectionActionScreen(data: CustomerScreenData, state: CustomerUiState, vm: CustomerViewModel, mode: CustomerScreen) {
    val isActivation = mode == CustomerScreen.ACTIVATE
    val isExtension = mode == CustomerScreen.EXTEND
    val code = mode.id
    val chosenId = if (isActivation) vm.selectedCustomerNumberId.orEmpty() else vm.selectedProtectionPeriodId.orEmpty()
    var targetQuery by rememberSaveable(code) { mutableStateOf("") }
    val candidates = data.related.array(if (isActivation) "candidates" else "protections").objects()
        .filter { row ->
            if (isActivation) true else row.optString("effective_status").equals(if (isExtension) "ACTIVE" else "EXPIRED", true)
        }
        .filter { row -> (row.optString("display_phone") + row.optString("company_name") + row.optString("public_activation_code")).contains(targetQuery.trim(), true) }
    var targetId by rememberSaveable(code) { mutableStateOf(chosenId) }
    var tariffId by rememberSaveable(code) { mutableStateOf("") }
    var units by rememberSaveable(code) { mutableStateOf("1") }
    var confirm by remember { mutableStateOf(false) }
    val target = candidates.firstOrNull { it.optString(if (isActivation) "customer_number_id" else "id") == targetId }
    val tariffs = data.related.array("tariffs").objects().filter { tariff ->
        target == null || tariff.optString("telecom_company_id").isBlank() || tariff.optString("telecom_company_id") == target.optString("telecom_company_id")
    }
    val tariff = tariffs.firstOrNull { it.optString("id") == tariffId } ?: tariffs.firstOrNull()
    val unitCount = units.toIntOrNull()?.takeIf { it in 1..120 }
    val unitDays = tariff?.optInt("duration_unit_days", 0) ?: 0
    val pointsPerUnit = tariff?.optLong("points_per_unit", 0L) ?: 0L
    val quote = if (unitCount != null) calculateProtectionQuote(unitDays, pointsPerUnit, unitCount) else null
    val durationDays = quote?.durationDays
    val cost = quote?.pointsCost
    val balance = data.related.array("balance").optJSONObject(0)?.optLong("balance", -1L) ?: -1L
    val endPreview = target?.optString("end_at").orEmpty().takeIf(String::isNotBlank)?.let { oldEnd ->
        runCatching {
            val start = if (isExtension) java.time.Instant.parse(oldEnd).atZone(java.time.ZoneId.systemDefault()).toLocalDate() else LocalDate.now()
            start.plusDays((durationDays ?: 0).toLong()).toString()
        }.getOrNull()
    }
    val title = when (mode) { CustomerScreen.ACTIVATE -> "تفعيل الحماية"; CustomerScreen.EXTEND -> "تمديد الحماية"; else -> "تجديد الحماية" }
    SectionTitle(title, "تختار عدد وحدات كاملة فقط؛ الأيام والتكلفة تُحسب من تعرفة الشركة المحفوظة على الخادم.")
    if (candidates.isEmpty()) EmptyPanel(if (targetQuery.isNotBlank()) "لا توجد نتائج مطابقة للبحث." else when (mode) {
        CustomerScreen.ACTIVATE -> "لا توجد أرقام مؤهلة للتفعيل. أضف رقمًا أولًا، وتأكد من أهلية الحساب."
        CustomerScreen.EXTEND -> "لا توجد حماية نشطة مؤهلة للتمديد."
        else -> "لا توجد حماية منتهية مؤهلة للتجديد."
    })
    if (candidates.isNotEmpty()) {
        OutlinedTextField(targetQuery, { targetQuery = it }, Modifier.fillMaxWidth().traceElement("$code.TARGET_SEARCH"), label = { Text("بحث في الأرقام") }, singleLine = true)
        JsonItemMenu(if (isActivation) "رقم غير محمي" else "فترة الحماية", candidates,
            targetId, { row -> row.optString("display_phone").ifBlank { row.optString("public_activation_code") } + " · " + row.optString("company_name") },
            { row -> targetId = row.optString(if (isActivation) "customer_number_id" else "id") }, "$code.TARGET")
        if (target != null) {
            KeyValue("الشركة", target.optString("company_name"))
            if (target.optString("start_at").isNotBlank()) KeyValue("بداية الفترة", formatDate(target.optString("start_at")))
            if (target.optString("end_at").isNotBlank()) KeyValue("الانتهاء الحالي", formatDate(target.optString("end_at")))
            if (!isActivation) ExtensionHistory(targetId, data.related.array("extensions").objects())
        }
        if (tariffs.isEmpty()) NoticeBanner("لا توجد تعرفة نشطة ومهيأة لهذه الشركة.", true)
        else {
            JsonItemMenu("تعرفة الوحدة", tariffs, tariff?.optString("id").orEmpty(), { row ->
                "${row.optString("tariff_mode")} · ${row.optString("duration_unit_days")} يوم · ${row.optString("points_per_unit")} نقطة"
            }, { tariffId = it.optString("id") }, "$code.TARIFF")
            OutlinedTextField(units, { units = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth().traceElement("$code.UNITS"),
                label = { Text("عدد وحدات التعرفة (1–120)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            KeyValue("مدة الوحدة", if (unitDays > 0) "$unitDays يوم" else "غير مهيأة")
            KeyValue("سعر الوحدة", if (pointsPerUnit > 0) "$pointsPerUnit نقطة" else "غير مهيأ")
            KeyValue("المدة الإجمالية", durationDays?.let { "$it يوم" } ?: "أدخل عدد وحدات صحيحًا")
            KeyValue("التكلفة الإجمالية", cost?.let { "$it نقطة" } ?: "التعرفة غير مكتملة")
            KeyValue("رصيد النقاط", if (balance >= 0) "$balance نقطة" else "غير متاح")
            if (isExtension || mode == CustomerScreen.RENEW) endPreview?.let { KeyValue("تاريخ الانتهاء المتوقع", it) }
        }
        if (cost != null && balance >= 0 && cost > balance) NoticeBanner("رصيد النقاط غير كافٍ؛ لم يُرسل أي طلب خصم.", true)
        val ready = !state.mutationBusy && targetId.isNotBlank() && tariff?.optString("id").orEmpty().isNotBlank() &&
            unitCount != null && durationDays != null && cost != null && balance >= cost && vm.online()
        Button(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().traceElement("$code.CONFIRM"), enabled = ready) { Text("${title} · مراجعة التكلفة") }
        if (confirm) ConfirmAction(
            "تأكيد $title؟",
            "الوحدة ${tariff?.optString("tariff_mode")} · $unitCount وحدة · $durationDays يوم · $cost نقطة. سيعيد الخادم التحقق من التعرفة والرصيد والملكية قبل أي تغيير.",
            { confirm = false },
            {
                confirm = false
                when (mode) {
                    CustomerScreen.ACTIVATE -> vm.activate(targetId, tariff?.optString("id").orEmpty(), unitCount ?: 0)
                    CustomerScreen.EXTEND -> vm.extend(targetId, tariff?.optString("id").orEmpty(), unitCount ?: 0)
                    else -> vm.renew(targetId, tariff?.optString("id").orEmpty(), unitCount ?: 0)
                }
            }, state.mutationBusy,
        )
    }
}

@Composable
private fun NotificationsScreen(data: CustomerScreenData, vm: CustomerViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var readFilter by rememberSaveable { mutableStateOf("الكل") }
    val rows = data.related.array("notifications").objects().filter { row ->
        (row.optString("title") + row.optString("body")).contains(query.trim(), true) &&
            (readFilter == "الكل" || row.optBoolean("is_read", false) == (readFilter == "مقروء"))
    }
    SectionTitle("إشعارات النظام", "كل إشعار يشير إلى مصدره التجاري ولا يحل محله.")
    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().traceElement("C14.SEARCH"), label = { Text("بحث في الإشعارات") }, singleLine = true)
    SimpleMenu("حالة القراءة", listOf("الكل", "مقروء", "غير مقروء"), readFilter, { readFilter = it }, "C14.FILTER")
    if (rows.isEmpty()) EmptyPanel("لا توجد إشعارات مطابقة.")
    rows.forEach { row ->
        DataCard(toCustomerRecord("customer_notification", row), traceId = "C14.LIST")
        if (!row.optBoolean("is_read", false)) TextButton(onClick = { vm.markNotificationRead(row.optString("id")) }) { Text("تحديد كمقروء") }
    }
}

@Composable
private fun SupportScreen(data: CustomerScreenData, state: CustomerUiState, vm: CustomerViewModel) {
    var selectedId by rememberSaveable { mutableStateOf("") }
    var newConversation by rememberSaveable { mutableStateOf(false) }
    var subject by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var threadQuery by rememberSaveable { mutableStateOf("") }
    var threadStatus by rememberSaveable { mutableStateOf("الكل") }
    val messages = data.related.array("messages").objects()
    val thread = data.related.array("threads").objects().firstOrNull { it.optString("id") == selectedId }
    val threadRows = data.related.array("threads").objects().filter { row ->
        val requestStatus = row.optString("request_status")
        val statusMatches = when (threadStatus) {
            "قيد المراجعة" -> requestStatus == "PENDING"
            "مفتوحة" -> requestStatus != "PENDING" && row.optString("status").equals("OPEN", true)
            "مغلقة" -> row.optString("status").equals("CLOSED", true)
            else -> true
        }
        (row.optString("subject") + row.optString("status") + requestStatus + row.optString("id")).contains(threadQuery.trim(), true) && statusMatches
    }
    LaunchedEffect(state.mutationMessage) {
        when (state.mutationMessage) {
            "تم إرسال طلب الدعم؛ ستُفتح المحادثة بعد موافقة الإدارة." -> { newConversation = false; subject = ""; body = "" }
            "تم إرسال الرسالة إلى المحادثة." -> body = ""
            "تم إغلاق المحادثة." -> selectedId = ""
        }
    }
    SectionTitle("تواصل أمان", "الدعم ورسائل الإدارة قناتان مختلفتان عن إشعارات النظام.")
    val inbox = data.related.array("admin_messages").objects()
    if (inbox.isNotEmpty()) {
        SectionTitle("رسائل الإدارة")
        inbox.forEach { message ->
            DataCard(toCustomerRecord("admin_message", message), traceId = "C15.ADMIN_MESSAGES")
            if (!message.optBoolean("is_read", false)) TextButton(onClick = { vm.markAdminMessageRead(message.optString("id")) }) { Text("تحديد كمقروء") }
        }
    }
    OutlinedButton(onClick = { vm.load(CustomerScreen.SUPPORT) }, Modifier.fillMaxWidth(), enabled = !state.mutationBusy) { Text("تحديث المحادثات والرسائل") }
    if (!newConversation && threadRows.isNotEmpty()) {
        SectionTitle("محادثات الدعم")
        OutlinedTextField(threadQuery, { threadQuery = it }, Modifier.fillMaxWidth().traceElement("C15.THREAD_SEARCH"), label = { Text("بحث في المحادثات") }, singleLine = true)
        SimpleMenu("حالة المحادثة", listOf("الكل", "قيد المراجعة", "مفتوحة", "مغلقة"), threadStatus, { threadStatus = it }, "C15.THREAD_FILTER")
        threadRows.forEach { row ->
            DataCard(toCustomerRecord("support_conversation", row), traceId = "C15.THREADS", onClick = { selectedId = row.optString("id"); newConversation = false })
        }
        OutlinedButton(onClick = { selectedId = ""; newConversation = true; subject = ""; body = "" }) { Text("طلب دعم جديد") }
    } else if (!newConversation && data.related.array("threads").length() > 0) {
        OutlinedTextField(threadQuery, { threadQuery = it }, Modifier.fillMaxWidth().traceElement("C15.THREAD_SEARCH"), label = { Text("بحث في المحادثات") }, singleLine = true)
        SimpleMenu("حالة المحادثة", listOf("الكل", "قيد المراجعة", "مفتوحة", "مغلقة"), threadStatus, { threadStatus = it }, "C15.THREAD_FILTER")
        EmptyPanel("لا توجد محادثات مطابقة.")
        OutlinedButton(onClick = { selectedId = ""; newConversation = true; subject = ""; body = "" }) { Text("طلب دعم جديد") }
    } else if (!newConversation) {
        EmptyPanel("لا توجد محادثات دعم. يمكنك بدء طلب جديد.")
        OutlinedButton(onClick = { newConversation = true; subject = ""; body = "" }) { Text("طلب دعم جديد") }
    }
    if (thread != null && !newConversation) {
        val requestApproved = thread.optString("request_status").let { it.isBlank() || it == "APPROVED" }
        SectionTitle(thread.optString("subject").ifBlank { "محادثة الدعم" }, "الحالة: ${thread.optString("status")}")
        if (!requestApproved) NoticeBanner("طلب الدعم قيد مراجعة الإدارة؛ ستُفتح المحادثة بعد الموافقة.", true)
        val currentMessages = messages.filter { it.optString("conversation_id") == selectedId }
        if (currentMessages.isEmpty()) EmptyPanel("لا توجد رسائل محملة في المحادثة.")
        currentMessages.forEach { message ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (message.optString("sender_type").equals("CUSTOMER", true)) "أنت" else "أمان", color = MaterialTheme.colorScheme.primary)
                    Text(message.optString("body"))
                    Text(formatDate(message.optString("sent_at")), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (requestApproved && thread.optString("status").equals("OPEN", true)) {
            TextButton(onClick = { vm.closeSupportConversation(selectedId) }) { Text("إغلاق المحادثة") }
        } else if (requestApproved) NoticeBanner("المحادثة مغلقة ولا يمكن إرسال رد جديد.", true)
    }
    if (newConversation) OutlinedTextField(subject, { subject = it }, Modifier.fillMaxWidth().traceElement("C15.SUBJECT"), label = { Text("موضوع الطلب") }, singleLine = true)
    if (newConversation || thread?.let { activeThread ->
            activeThread.optString("status").equals("OPEN", true) &&
                activeThread.optString("request_status").let { it.isBlank() || it == "APPROVED" }
        } == true) {
        OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth().height(130.dp).traceElement("C15.MESSAGE"), label = { Text(if (newConversation) "الرسالة" else "الرد") })
        Button(onClick = {
            if (newConversation) vm.createSupportConversation(subject, body) else vm.sendSupportMessage(selectedId, body)
        }, modifier = Modifier.fillMaxWidth().traceElement("C15.SEND"),
            enabled = !state.mutationBusy && vm.online() && body.isNotBlank() && (!newConversation || subject.isNotBlank())) { Text("إرسال") }
    }
}

@Composable
private fun SearchScreen(data: CustomerScreenData, vm: CustomerViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { vm.search(query) }
    SectionTitle("البحث في حسابك", "يبحث ضمن الأرقام والمشتريات وحركة النقاط والحماية والإشعارات المملوكة لحسابك فقط.")
    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().traceElement("C16.QUERY"), label = { Text("اكتب رقمًا أو رمزًا أو وصفًا") }, singleLine = true)
    if (query.trim().length < 2) EmptyPanel("أدخل حرفين على الأقل لبدء البحث.")
    else if (data.records.isEmpty()) EmptyPanel("لا توجد نتائج مطابقة.")
    data.records.forEach { record ->
        DataCard(record, traceId = "C16.RESULTS", onClick = {
            val route = when (record.source) {
                "customer_number", "numbers" -> CustomerScreen.ADDED_NUMBERS
                "points_purchase" -> CustomerScreen.BUY_POINTS
                "points_ledger" -> CustomerScreen.POINTS_HISTORY
                "protection_period" -> if (record.raw.optString("effective_status").equals("EXPIRED", true)) CustomerScreen.EXPIRED_NUMBERS else CustomerScreen.ACTIVE_NUMBERS
                "customer_notification" -> CustomerScreen.NOTIFICATIONS
                else -> CustomerScreen.POINTS_HISTORY
            }
            vm.navigate(route)
        })
    }
}

@Composable
private fun ReportsScreen(data: CustomerScreenData, state: CustomerUiState, vm: CustomerViewModel) {
    var categoryLabel by rememberSaveable { mutableStateOf(CustomerReportCategory.POINTS.label) }
    var appliedCategory by rememberSaveable { mutableStateOf(CustomerReportCategory.POINTS.label) }
    var fromDate by rememberSaveable { mutableStateOf("") }
    var toDate by rememberSaveable { mutableStateOf("") }
    var appliedFrom by rememberSaveable { mutableStateOf("") }
    var appliedTo by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var exportNotice by remember { mutableStateOf("") }
    val context = LocalContext.current
    val rows = buildCustomerReportRows(data, CustomerReportCategory.fromLabel(appliedCategory), appliedFrom, appliedTo)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) exportNotice = "تم إلغاء التصدير."
        else runCatching {
            val output = requireNotNull(context.contentResolver.openOutputStream(uri)) { "تعذر فتح ملف التصدير." }
            OutputStreamWriter(output, Charsets.UTF_8).use { writer -> writer.write("\uFEFF"); writer.write(customerReportCsv(rows)) }
        }.onSuccess { exportNotice = "تم تصدير ${rows.size} سجلًا إلى الملف الذي اخترته." }
            .onFailure { exportNotice = "تعذر حفظ التقرير: ${it.message.orEmpty()}" }
    }
    SectionTitle("تقارير حسابك", "نقاط ومشتريات وحماية وأرقام ونشاط؛ لا تعرض دفاتر مالية إدارية.")
    SimpleMenu("نوع التقرير", CustomerReportCategory.entries.map { it.label }, categoryLabel, { categoryLabel = it }, "C17.CATEGORY")
    SimpleMenu("فترة سريعة", listOf("كل الفترات", "آخر 30 يومًا", "آخر 90 يومًا"), if (fromDate.isBlank()) "كل الفترات" else "فترة محددة", {
        val days = when (it) { "آخر 30 يومًا" -> 30L; "آخر 90 يومًا" -> 90L; else -> 0L }
        fromDate = if (days == 0L) "" else LocalDate.now().minusDays(days - 1).toString()
        toDate = if (days == 0L) "" else LocalDate.now().toString()
    }, "C17.PERIOD")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(fromDate, { fromDate = it.take(10) }, Modifier.weight(1f), label = { Text("من YYYY-MM-DD") }, singleLine = true)
        OutlinedTextField(toDate, { toDate = it.take(10) }, Modifier.weight(1f), label = { Text("إلى YYYY-MM-DD") }, singleLine = true)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            if (isValidCustomerReportRange(fromDate, toDate)) { appliedCategory = categoryLabel; appliedFrom = fromDate; appliedTo = toDate; error = "" }
            else error = "أدخل نطاقًا صحيحًا بصيغة YYYY-MM-DD."
        }, Modifier.weight(1f).traceElement("C17.VIEW"), enabled = !state.mutationBusy) { Text("عرض التقرير") }
        OutlinedButton(onClick = { vm.load(CustomerScreen.REPORTS) }, Modifier.weight(1f), enabled = !state.mutationBusy) { Text("تحديث") }
    }
    if (error.isNotBlank()) NoticeBanner(error, true)
    if (exportNotice.isNotBlank()) NoticeBanner(exportNotice, exportNotice.startsWith("تعذر"))
    KeyValue("عدد السجلات", rows.size.toString())
    if (rows.isEmpty()) EmptyPanel("لا توجد بيانات فعلية ضمن الفئة والفترة المختارتين.")
    rows.forEach { row -> Card(Modifier.fillMaxWidth().traceElement("C17.RESULTS"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(row.type.ifBlank { row.category }, fontWeight = FontWeight.SemiBold)
            KeyValue("التاريخ", formatDate(row.timestamp)); KeyValue("الحالة", row.status); KeyValue("النقاط", row.points)
            if (row.details.isNotBlank()) Text(row.details, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    } }
    OutlinedButton(onClick = { launcher.launch("aman-customer-report-${LocalDate.now()}.csv") }, modifier = Modifier.fillMaxWidth().traceElement("C17.EXPORT"),
        enabled = !state.mutationBusy && isValidCustomerReportRange(appliedFrom, appliedTo)) { Text("تصدير CSV") }
}

@Composable
private fun AccountScreen(data: CustomerScreenData, state: CustomerUiState, vm: CustomerViewModel) {
    val profile = data.related.array("profile").optJSONObject(0)
    val clipboard = LocalClipboardManager.current
    if (profile == null) { EmptyPanel("لم يُعثر على ملف عميل مرتبط بجلسة الدخول."); return }
    var name by rememberSaveable(profile.optString("id")) { mutableStateOf(profile.optString("name")) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var saveConfirm by remember { mutableStateOf(false) }
    var passwordConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(state.mutationMessage) {
        if (state.mutationMessage == "تم حفظ الاسم.") saveConfirm = false
        if (state.mutationMessage == "تم تحديث كلمة المرور عبر Supabase Auth.") { newPassword = ""; confirmPassword = ""; passwordConfirm = false }
    }
    SectionTitle("الحساب")
    KeyValue("معرّف المستخدم", profile.optString("public_user_code"))
    NoticeBanner("احفظ معرّف المستخدم في مكان آمن؛ ستحتاج إليه مع البريد الإلكتروني واسمك عند استعادة الحساب. لا يمكن تعديل هذا المعرّف.", true)
    KeyValue("البريد الإلكتروني", profile.optString("email"))
    KeyValue("نوع الحساب", profile.optString("account_type"))
    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(profile.optString("public_user_code"))) }, Modifier.traceElement("C18.ID")) { Text("نسخ معرّف المستخدم") }
    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().traceElement("C18.NAME"), label = { Text("الاسم") }, singleLine = true)
    Button(onClick = { saveConfirm = true }, modifier = Modifier.fillMaxWidth().traceElement("C18.SAVE"), enabled = name.isNotBlank() && !state.mutationBusy) { Text("حفظ الاسم") }
    if (saveConfirm) ConfirmAction("حفظ الاسم؟", "سيُحدّث الاسم المرتبط بحساب الدخول فقط.", { saveConfirm = false }, { saveConfirm = false; vm.updateProfile(name) }, state.mutationBusy)
    SectionTitle("الأمان")
    OutlinedTextField(newPassword, { newPassword = it }, Modifier.fillMaxWidth().traceElement("C18.PASSWORD"), label = { Text("كلمة مرور جديدة") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
    OutlinedTextField(confirmPassword, { confirmPassword = it }, Modifier.fillMaxWidth(), label = { Text("تأكيد كلمة المرور") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
    Button(onClick = { passwordConfirm = true }, enabled = !state.mutationBusy && newPassword.length >= 8 && newPassword == confirmPassword) { Text("تحديث كلمة المرور") }
    if (passwordConfirm) ConfirmAction("تحديث كلمة المرور؟", "تُحدّث في Supabase Auth ولا تُخزن في قاعدة AMAN.", { passwordConfirm = false }, { vm.updatePassword(newPassword, confirmPassword) }, state.mutationBusy)
    TextButton(onClick = { vm.navigate(CustomerScreen.ABOUT) }) { Text("عن أمان والشروط والخصوصية · C19") }
    OutlinedButton(onClick = vm::signOut, Modifier.fillMaxWidth().traceElement("C18.LOGOUT"), enabled = !state.mutationBusy) { Text("تسجيل الخروج") }
}

@Composable
private fun AboutScreen(data: CustomerScreenData?) {
    var section by rememberSaveable { mutableStateOf("ABOUT") }
    val keys = listOf("ABOUT", "TERMS", "PRIVACY")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        keys.forEach { key -> TextButton(onClick = { section = key }, modifier = Modifier.weight(1f).traceElement("C19.SECTIONS.$key")) { Text(when (key) { "TERMS" -> "الشروط"; "PRIVACY" -> "الخصوصية"; else -> "ما هو أمان" }) } }
    }
    val row = data?.related?.array("content")?.objects()?.firstOrNull { it.optString("content_key").equals(section, true) }
    SectionTitle(row?.optString("title").orEmpty().ifBlank { when (section) { "TERMS" -> "الشروط"; "PRIVACY" -> "الخصوصية"; else -> "ما هو أمان" } })
    if (row == null) EmptyPanel("المحتوى المعتمد لهذا القسم غير منشور حاليًا.") else Text(row.optString("body"), Modifier.fillMaxWidth().traceElement("C19.CONTENT").padding(vertical = 8.dp))
}

@Composable
private fun SimpleMenu(label: String, options: List<String>, selected: String, choose: (String) -> Unit, elementId: String) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, Modifier.fillMaxWidth().traceElement(elementId)) { Text("$label: $selected") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { choose(option); expanded = false }) }
        }
    }
}

@Composable
private fun JsonItemMenu(label: String, rows: List<JSONObject>, selected: String, text: (JSONObject) -> String, choose: (JSONObject) -> Unit, elementId: String) {
    var expanded by remember { mutableStateOf(false) }
    val selectedRow = rows.firstOrNull { it.optString("id") == selected }
    Column {
        OutlinedButton(onClick = { expanded = true }, Modifier.fillMaxWidth().traceElement(elementId), enabled = rows.isNotEmpty()) {
            Text("$label: ${selectedRow?.let(text) ?: "اختر"}", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            rows.forEach { row -> DropdownMenuItem(text = { Text(text(row)) }, onClick = { choose(row); expanded = false }) }
        }
    }
}

@Composable
private fun ConfirmAction(title: String, body: String, onCancel: () -> Unit, onConfirm: () -> Unit, busy: Boolean) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, Modifier.weight(1f), enabled = !busy) { Text("إلغاء") }
                Button(onClick = onConfirm, Modifier.weight(1f), enabled = !busy) {
                    if (busy) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp) else Text("تأكيد")
                }
            }
        }
    }
}

private fun fieldName(key: String): String = when (key) {
    "display_phone" -> "رقم الهاتف"; "public_user_code" -> "معرّف المستخدم"; "public_purchase_code" -> "رمز الطلب"
    "points_snapshot" -> "النقاط المطلوبة"; "price_snapshot" -> "السعر"; "points_cost_snapshot" -> "التكلفة بالنقاط"
    "duration_days" -> "المدة بالأيام"; "start_at" -> "البداية"; "end_at" -> "الانتهاء"
    "balance_after" -> "الرصيد بعد الحركة"; "created_at", "submitted_at", "sent_at", "added_at" -> "التاريخ"
    "entry_type" -> "نوع الحركة"; "source_type" -> "نوع المصدر"; "status" -> "الحالة"; "description" -> "الوصف"
    "rejection_reason" -> "سبب الرفض"; "tariff_mode_snapshot" -> "وحدة التعرفة"; "tariff_value_snapshot" -> "تعرفة الفترة"
    else -> key.replace('_', ' ')
}

private fun displayValue(value: String): String = when (value.uppercase()) {
    "ACTIVE" -> "نشط"; "INACTIVE" -> "غير نشط"; "ARCHIVED" -> "مؤرشف"; "PENDING" -> "قيد المراجعة"
    "APPROVED" -> "معتمد"; "REJECTED" -> "مرفوض"; "EXPIRED" -> "منتهٍ"; "OPEN" -> "مفتوح"; "CLOSED" -> "مغلق"
    "CREDIT" -> "إضافة"; "DEBIT" -> "استخدام"; else -> formatDate(value)
}

private fun formatDate(value: String): String = if (value.isBlank()) "" else value.replace('T', ' ').substringBefore('.').removeSuffix("Z")
