package com.aman.customer.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
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
import com.aman.customer.ui.CustomerViewModel
import com.aman.customer.data.discoverProvider
import com.aman.customer.data.objects
import com.aman.customer.data.array
import com.aman.customer.data.text
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

@Composable
fun CustomerScreenContent(state: CustomerUiState, vm: CustomerViewModel, modifier: Modifier = Modifier) {
    val data = state.data
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (data == null && state.screen != CustomerScreen.ABOUT) {
            EmptyPanel(if (state.phase.name == "ERROR") "تعذر تحميل بيانات هذه الشاشة." else "جارٍ تحميل بيانات الحساب…")
            OutlinedButton(onClick = { vm.load(state.screen) }) { Text("إعادة المحاولة") }
        } else when (state.screen) {
            CustomerScreen.HOME -> HomeScreen(data!!, vm)
            CustomerScreen.ACTIVE_NUMBERS -> ActiveNumbersScreen(data!!, vm)
            CustomerScreen.INACTIVE_NUMBERS -> InactiveNumbersScreen(data!!, vm)
            CustomerScreen.POINTS -> PointsScreen(data!!, state, vm)
            CustomerScreen.OPERATIONS -> OperationsScreen(data!!)
            CustomerScreen.ADD_NUMBER -> AddNumberScreen(data!!)
            CustomerScreen.ACTIVATE -> ActivateScreen(data!!, state, vm)
            CustomerScreen.EXTEND -> ExtendScreen(data!!, state, vm)
            CustomerScreen.ADMIN_ALERTS -> AlertListScreen(data!!, vm)
            CustomerScreen.SUPPORT -> SupportScreen(data!!)
            CustomerScreen.NOTIFICATIONS -> NotificationScreen(data!!, vm)
            CustomerScreen.REPORTS -> ReportsScreen(data!!)
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
private fun DataCard(record: CustomerRecord, trailing: String? = null, onClick: (() -> Unit)? = null) {
    var showDetails by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Card(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(record.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (record.subtitle.isNotBlank()) Text(record.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                trailing?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
            }
            val summary = record.details.filter { it.first in setOf("expires_at", "duration_days", "points_per_day_snapshot", "total_points_snapshot", "points_amount_snapshot", "price_amount_snapshot", "entry_type", "amount", "balance_after", "added_at", "created_at", "rejection_reason", "content") }.take(4)
            summary.forEach { (key, value) -> if (value.isNotBlank()) Text("${fieldName(key)}: ${value.displayValue()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (record.title.any(Char::isDigit) && record.source in setOf("protections", "customer_numbers"))
                    TextButton(onClick = { clipboard.setText(AnnotatedString(record.title)) }) { Text("نسخ الرقم") }
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
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(profile?.text("full_name", "username")?.takeIf { it.isNotBlank() }?.let { "أهلًا، $it" } ?: "مرحبًا بك في أمان", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("رصيد النقاط", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(balance?.let { "$it نقطة" } ?: "غير متاح حاليًا", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("إشعارات غير مقروءة: $unread", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
    SectionTitle("الأقسام")
    val rows = listOf(
        listOf(CustomerScreen.POINTS, CustomerScreen.ACTIVE_NUMBERS, CustomerScreen.INACTIVE_NUMBERS),
        listOf(CustomerScreen.ADD_NUMBER, CustomerScreen.ACTIVATE, CustomerScreen.EXTEND),
        listOf(CustomerScreen.ADMIN_ALERTS, CustomerScreen.OPERATIONS, CustomerScreen.SUPPORT)
    )
    rows.forEach { line -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        line.forEach { screen -> Card(Modifier.weight(1f).height(78.dp).clickable { vm.navigate(screen) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.Center) {
                Text(screen.id, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                Text(screen.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, maxLines = 2)
            }
        } }
    } }
    SectionTitle("أحدث العمليات", "ملخص فقط؛ افتح سجل العمليات للتفاصيل.")
    if (data.records.isEmpty()) EmptyPanel("لا توجد عمليات مسجلة في الحساب حتى الآن.")
    data.records.forEach { DataCard(it, onClick = { vm.navigate(CustomerScreen.OPERATIONS) }) }
    OutlinedButton(onClick = { vm.navigate(CustomerScreen.NOTIFICATIONS) }) { Text("الإشعارات  ·  $unread") }
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
            DataCard(adjusted, trailing = if (expired) "منتهية" else "حماية")
            OutlinedButton(onClick = { vm.navigate(CustomerScreen.EXTEND) }, modifier = Modifier.fillMaxWidth()) { Text("الانتقال إلى تمديد الحماية") }
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
            DataCard(record, trailing = "غير مرتبط بحماية حسابك")
            OutlinedButton(onClick = { vm.navigate(CustomerScreen.ACTIVATE) }, modifier = Modifier.fillMaxWidth()) { Text("الانتقال إلى تفعيل رقم") }
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
    SelectJsonItem("الباقة", packages, packageId, { it.optString("name") + " · " + it.optString("points_amount") + " نقطة · " + it.optString("price_amount") + " " + it.optString("currency") }, { packageId = it.optString("id") })
    SelectJsonItem("وسيلة الدفع", methods, methodId, { it.optString("name") }, { methodId = it.optString("id") })
    methods.firstOrNull { it.optString("id") == methodId }?.let { method ->
        if (method.optString("instructions").isNotBlank()) Text("التعليمات: ${method.optString("instructions")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        val paymentData = method.optJSONObject("payment_data")
        if (paymentData != null && paymentData.length() > 0) Text("بيانات الدفع: ${paymentData.toString()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    OutlinedTextField(reference, { reference = it }, Modifier.fillMaxWidth(), label = { Text("رقم مرجع التحويل") }, singleLine = true)
    Button(onClick = { confirm = true }, enabled = !state.mutationBusy && packageId.isNotBlank() && methodId.isNotBlank() && reference.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text(if (vm.online()) "مراجعة وإرسال الطلب" else "حفظ الطلب المشفر للإرسال عند الاتصال")
    }
    if (packages.isEmpty() || methods.isEmpty()) EmptyPanel("لا تتوفر حاليًا باقات أو وسائل دفع نشطة من الخادم.")
    data.related.array("requests").objects().forEach { row -> DataCard(com.aman.customer.data.toCustomerRecord("points_purchase_requests", row)) }
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
    SectionTitle("سجل العمليات", "سجل الحساب الفعلي، وليس دفتر النقاط وحده.")
    val events = data.records
    val ledger = data.related.array("ledger").objects().map { com.aman.customer.data.toCustomerRecord("point_ledger", it) }
    if (events.isEmpty() && ledger.isEmpty()) EmptyPanel("لا توجد عمليات أو حركات نقاط مسجلة.")
    events.forEach { DataCard(it) }
    if (ledger.isNotEmpty()) {
        SectionTitle("حركات النقاط")
        ledger.forEach { DataCard(it) }
    }
    data.related.array("purchases").objects().forEach { row -> DataCard(com.aman.customer.data.toCustomerRecord("points_purchase_requests", row)) }
}

@Composable
private fun AddNumberScreen(data: CustomerScreenData) {
    var phone by rememberSaveable { mutableStateOf("") }
    val prefixes = data.related["prefixes"] ?: JSONArray()
    val digits = phone.filter(Char::isDigit)
    val match = discoverProvider(digits, prefixes)
    val duplicate = data.related.array("numbers").objects().any { row ->
        val stored = row.optJSONObject("phone_numbers")?.let { it.optString("normalized_phone").ifBlank { it.optString("phone_e164") } }.orEmpty()
        stored.filter(Char::isDigit) == digits && digits.isNotBlank()
    }
    SectionTitle("إضافة رقم", "تُكتشف الشركة تلقائيًا بأطول بادئة مطابقة. إضافة الرقم لا تخصم نقاطًا ولا تنشئ حماية.")
    OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("رقم الهاتف") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
    when {
        digits.isBlank() -> Text("أدخل رقم الهاتف لبدء اكتشاف الشركة.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        match != null -> Text("الشركة المكتشفة: ${match.first} · البادئة المطابقة: ${match.second}", color = MaterialTheme.colorScheme.primary)
        else -> Text("لم تُكتشف شركة من البادئات النشطة المتاحة.", color = MaterialTheme.colorScheme.error)
    }
    if (duplicate) NoticeBanner("الرقم مرتبط بالفعل بحسابك.", true)
    NoticeBanner("تعذر إتمام الحفظ في هذا الإصدار: مخطط قاعدة البيانات لا يعرّف RPC أو صلاحية كتابة للعميل على customer_numbers. لم يتم إرسال أي طلب كتابة.", true)
    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("الحفظ غير متاح حتى اعتماد عقد Backend") }
    SectionTitle("الأرقام المضافة")
    if (data.records.isEmpty()) EmptyPanel("لا توجد أرقام مضافة مرتبطة بحسابك.")
    data.records.forEach { DataCard(it) }
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
    NoticeBanner("الاختيار المحلي لا يثبت أن الرقم غير محمي لدى مستخدم آخر؛ تحقق الخادم هو المعتمد ولا يحدث خصم عند رفضه.", true)
    SelectJsonItem("رقم غير محمي مرتبط بحسابك", numbers, phoneId, { row -> row.optJSONObject("phone_numbers")?.let { p -> p.optString("phone_e164").ifBlank { p.optString("normalized_phone") } } ?: "رقم غير متاح" }, { row -> phoneId = row.optJSONObject("phone_numbers")?.optString("id").orEmpty() })
    OutlinedTextField(daysText, { daysText = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("مدة الحماية بالأيام") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
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
    if (!enough && cost != null && balance >= 0) OutlinedButton(onClick = { vm.navigate(CustomerScreen.POINTS) }) { Text("الانتقال إلى شراء النقاط") }
    val enabled = !state.mutationBusy && vm.online() && phoneId.isNotBlank() && days > 0 && daily > 0 && enough && subscriberReady
    Button(onClick = { confirm = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(if (!vm.online()) "يلزم اتصال لتفعيل الحماية" else "مراجعة وتأكيد التفعيل") }
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
    SelectJsonItem("حماية نشطة مرتبطة بحسابك", protections, protectionId, { row ->
        val phone = row.optJSONObject("phone_numbers")?.optString("phone_e164").orEmpty()
        "$phone · ينتهي ${row.optString("expires_at").displayDate()}"
    }, { protectionId = it.optString("id") })
    OutlinedTextField(daysText, { daysText = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("أيام التمديد") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
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
        OutlinedButton(onClick = { vm.navigate(CustomerScreen.POINTS) }) { Text("شراء نقاط") }
    }
    Button(onClick = { confirm = true }, enabled = !state.mutationBusy && vm.online() && protectionId.isNotBlank() && days > 0 && daily > 0 && enough, modifier = Modifier.fillMaxWidth()) { Text("مراجعة وتأكيد التمديد") }
    if (protections.isEmpty()) EmptyPanel("لا توجد حماية نشطة يملكها حسابك لتمديدها.")
    if (confirm) ConfirmAction("تأكيد تمديد الحماية؟", "سيخصم الخادم $cost نقطة ويضيف $days يومًا إلى تاريخ الانتهاء ثم يعيد بناء الخطة المستقبلية.", { confirm = false }, {
        confirm = false; vm.extend(protectionId, days)
    }, state.mutationBusy)
}

@Composable
private fun AlertListScreen(data: CustomerScreenData, vm: CustomerViewModel) {
    SectionTitle("تنبيهات الإدارة", "رسائل الإدارة منفصلة عن إشعارات النظام.")
    if (data.records.isEmpty()) EmptyPanel("لا توجد تنبيهات إدارية لحسابك.")
    data.records.forEach { record -> DataCard(record, trailing = if (record.raw.isNull("read_at")) "غير مقروء" else "مقروء", onClick = { if (record.raw.isNull("read_at")) vm.markRead(record.id) }) }
}

@Composable
private fun SupportScreen(data: CustomerScreenData) {
    var subject by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    SectionTitle("تواصل مع الإدارة", "رسائل AMAN الداخلية فقط؛ لا نفترض قناة WhatsApp أو Telegram.")
    OutlinedTextField(subject, { subject = it }, Modifier.fillMaxWidth(), label = { Text("الموضوع") }, singleLine = true)
    OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth().height(125.dp), label = { Text("نص الرسالة") })
    NoticeBanner("إرسال الرسائل غير متاح بعد: الترحيل يمنح قراءة مراسلات العميل فقط ولا يتضمن عقد كتابة/إرسال معتمدًا. لم يتم إرسال رسالة.", true)
    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("الإرسال غير متاح حتى اعتماد عقد Backend") }
    SectionTitle("سجل التواصل")
    if (data.records.isEmpty()) EmptyPanel("لا توجد محادثات دعم مسجلة لحسابك.")
    data.records.forEach { DataCard(it) }
    data.related.array("messages").objects().forEach { message ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(message.optString("body"))
                Text(message.optString("created_at").displayDate(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun NotificationScreen(data: CustomerScreenData, vm: CustomerViewModel) {
    SectionTitle("إشعارات النظام", "فتح إشعار غير مقروء يحدّث read_at على الخادم لمزامنته بين الأجهزة.")
    if (data.records.isEmpty()) EmptyPanel("لا توجد إشعارات نظام لحسابك.")
    data.records.forEach { record -> DataCard(record, trailing = if (record.raw.isNull("read_at")) "جديد" else "مقروء", onClick = { if (record.raw.isNull("read_at")) vm.markRead(record.id) }) }
}

@Composable
private fun ReportsScreen(data: CustomerScreenData) {
    var category by rememberSaveable { mutableStateOf("النقاط") }
    var period by rememberSaveable { mutableStateOf("كل الفترات") }
    val ledger = data.related.array("ledger").objects()
    val filteredLedger = ledger.filter { periodMatch(it.optString("created_at"), period) }
    val netPoints = filteredLedger.sumOf { it.optLong("amount", 0L) }
    val earned = filteredLedger.filter { it.optLong("amount") > 0 }.sumOf { it.optLong("amount") }
    val used = filteredLedger.filter { it.optLong("amount") < 0 }.sumOf { -it.optLong("amount") }
    SectionTitle("تقارير حسابك", "تجميع مشتق من بياناتك الفعلية؛ لا يشمل مهام السداد الداخلية.")
    SimpleMenu("نوع التقرير", listOf("النقاط", "الأرقام", "التفعيل والتمديد", "العمليات", "شراء النقاط"), category) { category = it }
    SimpleMenu("الفترة", listOf("كل الفترات", "آخر 30 يومًا", "آخر 90 يومًا"), period) { period = it }
    when (category) {
        "النقاط" -> {
            KeyValue("إضافات النقاط في النتائج المحملة", "$earned نقطة")
            KeyValue("استخدام النقاط في النتائج المحملة", "$used نقطة")
            KeyValue("صافي حركة النقاط", "$netPoints نقطة")
            data.related["balance"]?.optJSONObject(0)?.opt("balance_points")?.let { KeyValue("الرصيد الحالي", "$it نقطة") }
            if (filteredLedger.isEmpty()) EmptyPanel("لا توجد حركات نقاط ضمن النطاق المختار.")
            filteredLedger.forEach { DataCard(com.aman.customer.data.toCustomerRecord("point_ledger", it)) }
        }
        "الأرقام" -> {
            val numbers = data.related.array("numbers").objects().filter { periodMatch(it.optString("added_at"), period) }
            KeyValue("علاقات الأرقام المضافة", numbers.size.toString())
            val protected = data.related.array("protections").objects().filter { periodMatch(it.optString("started_at"), period) }
            KeyValue("الحمايات المسجلة", protected.size.toString())
            KeyValue("حمايات غير نشطة بحسابك", protected.count { it.optString("status") != "active" }.toString())
        }
        "التفعيل والتمديد" -> {
            data.related.array("protections").objects().forEach { DataCard(com.aman.customer.data.toCustomerRecord("protections", it)) }
            if (data.related.array("protections").length() == 0) EmptyPanel("لا توجد عمليات تفعيل أو تمديد ضمن البيانات المتاحة.")
        }
        "العمليات" -> {
            data.related.array("operations").objects().filter { periodMatch(it.optString("created_at"), period) }.forEach { DataCard(com.aman.customer.data.toCustomerRecord("operations", it)) }
            if (data.related.array("operations").length() == 0) EmptyPanel("لا توجد عمليات ضمن النطاق المختار.")
        }
        else -> {
            data.related.array("purchases").objects().filter { periodMatch(it.optString("submitted_at"), period) }.forEach { DataCard(com.aman.customer.data.toCustomerRecord("points_purchase_requests", it)) }
            if (data.related.array("purchases").length() == 0) EmptyPanel("لا توجد طلبات شراء ضمن النطاق المختار.")
        }
    }
}

@Composable
private fun AccountScreen(data: CustomerScreenData, vm: CustomerViewModel) {
    SectionTitle("حساب العميل", "بيانات الحساب من الملف المرتبط بهوية الدخول.")
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
    Button(onClick = vm::signOut, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth()) { Text("تسجيل الخروج") }
}

@Composable
private fun SearchScreen(data: CustomerScreenData, vm: CustomerViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    SectionTitle("البحث في حسابك", "الأرقام والعمليات وطلبات شراء النقاط والتنبيهات والإشعارات الخاصة بك فقط.")
    OutlinedTextField(query, { query = it; vm.search(it) }, Modifier.fillMaxWidth(), label = { Text("ابحث في بيانات حسابك") }, singleLine = true)
    if (query.trim().length < 2) EmptyPanel("أدخل حرفين على الأقل لبدء البحث ضمن بياناتك.")
    else if (data.records.isEmpty()) EmptyPanel("لا توجد نتائج مطابقة في البيانات المتاحة.")
    data.records.forEach { DataCard(it) }
}

@Composable
private fun AboutScreen() {
    SectionTitle("عن أمان")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("AMAN  |  أمان", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("أمان حماية وضمان", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("خدمة لإدارة حماية أرقام الاتصالات ومتابعة الاشتراك والخدمات المرتبطة بها وفق قواعد AMAN.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            KeyValue("الإصدار", "1.0.0")
            Text("بيانات الجهة وروابط الشروط والخصوصية والتواصل الرسمي غير مضافة إلى المرجع المعتمد.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SelectJsonItem(label: String, options: List<JSONObject>, selectedId: String, display: (JSONObject) -> String, onSelected: (JSONObject) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.optString("id") == selectedId }
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), enabled = options.isNotEmpty()) {
            Text(if (selected == null) "$label · ${if (options.isEmpty()) "لا تتوفر خيارات" else "اختر"}" else display(selected), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { item -> DropdownMenuItem(text = { Text(display(item)) }, onClick = { expanded = false; onSelected(item) }) }
        }
    }
}

@Composable
private fun SimpleMenu(label: String, options: List<String>, selected: String, choose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: $selected") }
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
    "points_delta" to "تغير النقاط", "money_amount" to "المبلغ", "notification_type" to "نوع الإشعار", "title" to "العنوان", "content" to "المحتوى",
    "read_at" to "تاريخ القراءة", "subject" to "الموضوع", "last_reply_at" to "آخر رد", "balance_points" to "الرصيد", "amount" to "النقاط",
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
