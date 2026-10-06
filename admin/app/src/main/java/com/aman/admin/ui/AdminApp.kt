package com.aman.admin.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Payment
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aman.admin.R
import com.aman.admin.data.AdminFormKind
import com.aman.admin.data.AdminMutation
import com.aman.admin.data.AdminPermissions
import com.aman.admin.data.AdminSection
import com.aman.admin.data.RelatedListKind
import com.aman.admin.data.ReportType
import com.aman.admin.data.adminSections
import org.json.JSONArray
import org.json.JSONObject

private val PanelShape = RoundedCornerShape(18.dp)

@Composable
fun AdminApp(viewModel: AdminViewModel) {
    val state by viewModel.state.collectAsState()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            state.checkingSession -> CenterProgress("التحقق من الجلسة…")
            !state.authenticated -> LoginScreen(state.email, state.busy, state.error, state.notice, viewModel.configurationMessage,
                viewModel::updateEmail, viewModel::signIn, viewModel::recoverPassword, viewModel::clearMessages)
            else -> AdminShell(state, viewModel)
        }
    }
}

@Composable
private fun LoginScreen(email: String, busy: Boolean, error: String?, notice: String?, configMessage: String?, onEmail: (String) -> Unit,
                       onSignIn: (String) -> Unit, onRecover: () -> Unit, onDismiss: () -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    var showSetup by rememberSaveable { mutableStateOf(false) }
    if (showSetup) AlertDialog(onDismissRequest = { showSetup = false }, title = { Text("A16 — فحص تهيئة التطبيق") },
        text = { Text(configMessage ?: "تم إعداد عنوان Supabase والمفتاح العام. بعد تسجيل الدخول يتحقق التطبيق من admin_identity والدور والصلاحيات من Backend.") },
        confirmButton = { TextButton(onClick = { showSetup = false }) { Text("إغلاق") } })
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.aman_brand_icon), contentDescription = "شعار أمان", modifier = Modifier.size(84.dp))
        Spacer(Modifier.height(18.dp))
        Text("AMAN | أمان", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("تسجيل دخول الإدارة", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))
        OutlinedTextField(value = email, onValueChange = onEmail, modifier = Modifier.fillMaxWidth(), label = { Text("البريد الإلكتروني") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("كلمة المرور") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        if (error != null) MessageCard(error, true, onDismiss, Modifier.padding(top = 14.dp))
        if (notice != null) MessageCard(notice, false, modifier = Modifier.padding(top = 14.dp))
        if (configMessage != null) MessageCard(configMessage, false, modifier = Modifier.padding(top = 14.dp))
        Button(onClick = { onSignIn(password) }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp),
            shape = RoundedCornerShape(14.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text("دخول", fontWeight = FontWeight.SemiBold)
        }
        Text("Supabase Auth + تحقق الدور والصلاحيات على Backend/RLS. لا ينشئ التطبيق حسابات إدارة.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 16.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onRecover, enabled = !busy) { Text("A19 — نسيت كلمة المرور؟") }
        }
        TextButton(onClick = { showSetup = true }) { Text("A16 — فحص التهيئة") }
    }
}

@Composable
private fun AdminShell(state: AdminUiState, viewModel: AdminViewModel) {
    Scaffold(contentWindowInsets = WindowInsets.statusBars, bottomBar = { AdminBottomBar(state.section, viewModel) },
        containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.section == AdminSection.HOME) HomeScreen(state, viewModel) else RecordSectionScreen(state, viewModel)
        }
    }
}

@Composable
private fun HomeScreen(state: AdminUiState, viewModel: AdminViewModel) {
    Column(Modifier.fillMaxSize()) {
        TopHeader(AdminSection.HOME.title, state.adminName, false, {})
        StatusMessages(state, viewModel)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Card(shape = PanelShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.aman_brand_icon), contentDescription = "شعار أمان", modifier = Modifier.size(48.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("مرحبًا ${state.adminName.ifBlank { "بالمسؤول" }}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("لوحة إدارة AMAN", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            val metrics = state.rows.filter { it.has("_dashboard_metric") }
            if (metrics.isNotEmpty()) {
                Text("مؤشرات حية حسب الصلاحيات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                metrics.chunked(3).forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        line.forEach { metric ->
                            Card(modifier = Modifier.weight(1f).clickable { runCatching { viewModel.open(AdminSection.valueOf(metric.optString("_dashboard_section"))) } },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                                Column(Modifier.fillMaxWidth().padding(11.dp)) {
                                    Text(metric.optString("_dashboard_count"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    Text(metric.optString("_dashboard_metric"), style = MaterialTheme.typography.labelSmall, maxLines = 2)
                                }
                            }
                        }
                        repeat(3 - line.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Text("الأقسام", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 10.dp))
            val cards = adminSections.filter { viewModel.hasPermission(it.section.readPermission) }
            cards.chunked(3).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    line.forEach { card -> DashboardTile(card.section, modifier = Modifier.weight(1f)) { viewModel.open(card.section) } }
                    repeat(3 - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            val extra = listOf(AdminSection.USERS, AdminSection.ACTIVE_NUMBERS, AdminSection.TASK_PLANS, AdminSection.TASK_SETTINGS, AdminSection.COMMUNICATIONS,
                AdminSection.SEARCH, AdminSection.NOTIFICATIONS, AdminSection.REPORTS, AdminSection.ACCOUNT, AdminSection.ABOUT, AdminSection.SETUP)
                .filter { section -> viewModel.hasPermission(section.readPermission) || (section == AdminSection.NOTIFICATIONS && viewModel.hasPermission(AdminPermissions.NOTIFICATIONS_SEND)) }
            Text("المزيد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
            extra.chunked(2).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    line.forEach { section -> OutlinedButton(onClick = { viewModel.open(section) }, modifier = Modifier.weight(1f)) { Text(section.title) } }
                    if (line.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("أحدث العمليات والتنبيهات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = viewModel::reload) { Icon(Icons.Outlined.Refresh, "تحديث", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            val activity = state.rows.filterNot { it.has("_dashboard_metric") }
            if (state.loading) CenterProgress("تحميل البيانات…", Modifier.fillMaxWidth().height(100.dp))
            else if (activity.isEmpty() && state.error == null) EmptyState("لا توجد سجلات متاحة ضمن صلاحياتك.")
            else activity.take(8).forEach { row ->
                val destination = when (row.optString("_aman_source_table")) {
                    "points_purchase" -> AdminSection.PURCHASES
                    "periodic_task" -> AdminSection.PAYMENT_TASKS
                    "operation" -> AdminSection.REPORTS
                    else -> AdminSection.REPORTS
                }
                CompactRecord(row, false, { viewModel.open(destination) }, AdminSection.HOME, Modifier.padding(bottom = 8.dp))
            }
        }
    }
}

@Composable
private fun RecordSectionScreen(state: AdminUiState, viewModel: AdminViewModel) {
    var pendingAction by remember(state.section) { mutableStateOf<AdminMutation?>(null) }
    var actionInput by remember(state.section) { mutableStateOf("") }
    var pendingForm by remember(state.section) { mutableStateOf(false) }
    var confirmLogout by remember(state.section) { mutableStateOf(false) }
    val selected = state.rows.getOrNull(state.selectedIndex)
    val relatedSelected = if (state.relatedKind != null) state.relatedRows.getOrNull(state.relatedSelectedIndex) else null
    val relatedIndexed = if (state.relatedKind == null) emptyList() else state.relatedRows.withIndex()
        .filter { state.searchText.isBlank() || it.value.toString().contains(state.searchText, ignoreCase = true) }
    val displayRows = if (state.relatedKind != null) relatedIndexed.map { it.value } else state.rows
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val reportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(csv(state.reportRows)) }
                ?: error("تعذر فتح ملف التصدير.")
        }.onSuccess { Toast.makeText(context, "تم تصدير ${state.reportRows.size} سجلًا.", Toast.LENGTH_LONG).show() }
            .onFailure { Toast.makeText(context, it.message ?: "تعذر حفظ CSV.", Toast.LENGTH_LONG).show() }
    }

    pendingAction?.let { action ->
        val needsInput = action in setOf(AdminMutation.REJECT_PURCHASE, AdminMutation.EXECUTE_PAYMENT_TASK, AdminMutation.CANCEL_PAYMENT_TASK, AdminMutation.RESCHEDULE_PAYMENT_TASK)
        AlertDialog(onDismissRequest = { pendingAction = null }, title = { Text(actionTitle(action)) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(actionExplanation(action, selected))
                if (needsInput) OutlinedTextField(value = actionInput, onValueChange = { actionInput = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(actionInputLabel(action)) }, singleLine = action != AdminMutation.REJECT_PURCHASE && action != AdminMutation.CANCEL_PAYMENT_TASK,
                    keyboardOptions = KeyboardOptions(keyboardType = if (action == AdminMutation.RESCHEDULE_PAYMENT_TASK) KeyboardType.Ascii else KeyboardType.Text))
            }
        }, confirmButton = { TextButton(onClick = { viewModel.run(action, actionInput.trim()); pendingAction = null }, enabled = !state.busy && (!needsInput || actionInput.isNotBlank())) { Text("تأكيد") } },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("إلغاء") } })
    }
    if (pendingForm && selected != null) AlertDialog(onDismissRequest = { pendingForm = false }, title = { Text("تأكيد تغيير الحالة") },
        text = { Text("سيُرسل التغيير إلى Backend ويُسجل ضمن سجل التدقيق. هل تريد المتابعة؟") },
        confirmButton = { TextButton(onClick = { viewModel.run(if (state.section in setOf(AdminSection.SUBSCRIBERS, AdminSection.USERS)) AdminMutation.SET_SUBSCRIBER_STATUS else AdminMutation.SET_CUSTOMER_NUMBER_STATUS, actionInput); pendingForm = false }) { Text("تأكيد") } },
        dismissButton = { TextButton(onClick = { pendingForm = false }) { Text("إلغاء") } })
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false }, title = { Text("تسجيل الخروج") }, text = { Text("سيُنهي التطبيق الجلسة ويمسح اللقطات المحلية المشفرة.") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; viewModel.signOut() }) { Text("خروج") } }, dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("إلغاء") } })

    Column(Modifier.fillMaxSize()) {
        TopHeader(state.section.title, state.adminName, true) { viewModel.open(AdminSection.HOME) }
        StatusMessages(state, viewModel)
        if (state.section != AdminSection.ACCOUNT && state.section != AdminSection.REPORTS) {
            OutlinedTextField(value = state.searchText, onValueChange = viewModel::updateSearch,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                label = { Text(if (state.section == AdminSection.SEARCH) "البحث في السجلات المتاحة" else "بحث في ${state.section.title}") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = { IconButton(onClick = viewModel::reload) { Icon(Icons.Outlined.Refresh, "تحديث") } }, singleLine = true)
            if (state.section == AdminSection.SEARCH && state.relatedKind == null) SearchTypeFilters(state.filterStatus, viewModel::setFilterStatus)
            else if (state.relatedKind != null) Unit
            else if (state.section in setOf(AdminSection.SUBSCRIBERS, AdminSection.USERS, AdminSection.ADDED_NUMBERS, AdminSection.ACTIVE_NUMBERS,
                    AdminSection.PURCHASES, AdminSection.PAYMENT_TASKS, AdminSection.PERIODIC_PAYMENT, AdminSection.TASK_PLANS,
                    AdminSection.PROVIDERS, AdminSection.PACKAGES, AdminSection.PAYMENT_METHODS, AdminSection.COMMUNICATIONS, AdminSection.NOTIFICATIONS)) {
                StatusFilters(state.filterStatus, state.section, viewModel::setFilterStatus)
            }
            if (state.section in setOf(AdminSection.PAYMENT_TASKS, AdminSection.PERIODIC_PAYMENT) && state.relatedKind == null) PaymentTaskFilters(state, viewModel)
        }
        if (state.section == AdminSection.REPORTS) {
            ReportControls(state, viewModel, export = { viewModel.checkExportPermission { reportLauncher.launch("aman-${state.reportTypeId}.csv") } })
        }
        if (state.section == AdminSection.NOTIFICATIONS) NotificationComposer(state, viewModel)
        if (state.section == AdminSection.ACCOUNT) AccountPanel(state, viewModel) { confirmLogout = true }
        if (state.formKind != null) AdminFormPanel(state, viewModel)
        if (state.section != AdminSection.ACCOUNT && state.section != AdminSection.REPORTS && state.formKind == null) {
            if (state.section == AdminSection.HOME) Unit else if (state.section != AdminSection.NOTIFICATIONS || state.rows.isNotEmpty()) {
                if (state.relatedKind != null) RelatedToolbar(state, viewModel)
                SelectedPanel(state, viewModel, selected, relatedSelected, clipboard, onActionRequest = { action, input -> actionInput = input; pendingAction = action },
                    onStatusRequest = { status -> actionInput = status; pendingForm = true }, onLogout = { confirmLogout = true })
            }
        }
                val rowsForList = if (state.section == AdminSection.REPORTS) state.reportRows else displayRows
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val label = when {
                state.section == AdminSection.REPORTS -> "نتائج التقرير"
                state.relatedKind != null -> state.relatedKind.title
                else -> "${state.section.title}"
            }
            Text("$label (${rowsForList.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            val canAdd = when (state.section) {
                AdminSection.PROVIDERS -> viewModel.can(AdminPermissions.PROVIDERS_MANAGE)
                AdminSection.PACKAGES -> viewModel.can(AdminPermissions.PACKAGES_MANAGE)
                AdminSection.PAYMENT_METHODS -> viewModel.can(AdminPermissions.PAYMENT_METHODS_MANAGE)
                AdminSection.FINANCE -> viewModel.can(AdminPermissions.FINANCE_WRITE)
                AdminSection.TASK_SETTINGS -> viewModel.can(AdminPermissions.TASKS_SETTINGS) && state.rows.getOrNull(state.selectedIndex)?.optString("telecom_company_id").orEmpty().isNotBlank()
                else -> false
            }
            if (canAdd && state.relatedKind == null && state.formKind == null) TextButton(onClick = {
                val kind = when (state.section) {
                    AdminSection.PROVIDERS -> AdminFormKind.PROVIDER
                    AdminSection.PACKAGES -> AdminFormKind.PACKAGE
                    AdminSection.TASK_SETTINGS -> AdminFormKind.TASK_SETTINGS
                    AdminSection.FINANCE -> AdminFormKind.EXPENSE
                    else -> AdminFormKind.PAYMENT_METHOD
                }
                val row = state.rows.getOrNull(state.selectedIndex).takeIf { state.section != AdminSection.FINANCE }
                viewModel.startForm(kind, row)
            }) { Text(if (state.section == AdminSection.FINANCE) "تسجيل مصروف" else if (state.section == AdminSection.TASK_SETTINGS) "إضافة إصدار إعداد" else "إضافة") }
            if (state.relatedKind != null) TextButton(onClick = viewModel::closeRelated) { Text("العودة للقائمة") }
        }
        if (state.relatedKind == null && (state.pageIndex > 0 || state.pageHasMore)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { viewModel.setPage(state.pageIndex - 1) }, enabled = state.pageIndex > 0) { Text("السابق") }
                Text("صفحة ${state.pageIndex + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { viewModel.setPage(state.pageIndex + 1) }, enabled = state.pageHasMore) { Text("التالي") }
            }
        }
        when {
            state.loading -> CenterProgress("تحميل ${state.section.title}…", Modifier.weight(1f).fillMaxWidth())
            rowsForList.isEmpty() && state.error == null -> EmptyState(if (state.section == AdminSection.SEARCH && state.searchText.isBlank()) "أدخل كلمة بحث للعثور على السجلات التي تسمح صلاحيتك برؤيتها." else "لا توجد سجلات مطابقة في المصدر.", Modifier.weight(1f).fillMaxWidth())
            else -> LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(rowsForList, key = { index, row -> row.optString("id").ifBlank { "record-$index" } }) { index, row ->
                    val sourceIndex = if (state.relatedKind != null) relatedIndexed[index].index else index
                    val isSelected = if (state.section == AdminSection.REPORTS) false else if (state.relatedKind != null) sourceIndex == state.relatedSelectedIndex else index == state.selectedIndex
                    CompactRecord(row, isSelected, {
                        if (state.relatedKind != null) viewModel.selectRelated(sourceIndex)
                        else if (state.section == AdminSection.SEARCH) viewModel.open(searchDestination(row), keepSearch = true)
                        else if (state.section != AdminSection.REPORTS) viewModel.select(index)
                    },
                        if (state.section == AdminSection.REPORTS) AdminSection.REPORTS else state.section)
                }
            }
        }
    }
}

@Composable
private fun SelectedPanel(state: AdminUiState, viewModel: AdminViewModel, row: JSONObject?, relatedRow: JSONObject?, clipboard: androidx.compose.ui.platform.ClipboardManager,
                          onActionRequest: (AdminMutation, String) -> Unit, onStatusRequest: (String) -> Unit, onLogout: () -> Unit) {
    val section = state.section
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), shape = PanelShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.fillMaxWidth().padding(14.dp).heightIn(max = 285.dp).verticalScroll(rememberScrollState())) {
            Text(if (row == null) "التفاصيل" else "${section.title} — السجل المحدد", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (row == null) Text("اختر سجلًا من القائمة لعرض تفاصيله وإجراءاته هنا.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            else {
                DisplayFields(section, row)
                if (state.relatedKind != null && relatedRow != null) {
                    Text("${state.relatedKind.title}: ${recordTitle(section, relatedRow)}", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                    if (state.relatedKind == RelatedListKind.SUPPORT_MESSAGES) {
                        FieldLine("المرسل", relatedRow.optString("sender_type")); FieldLine("الرسالة", relatedRow.optString("body")); FieldLine("التاريخ", relatedRow.optString("sent_at"))
                    } else DisplayFields(if (state.relatedKind in setOf(RelatedListKind.PROVIDER_PREFIXES, RelatedListKind.PROVIDER_TARIFFS)) AdminSection.PROVIDERS else section, relatedRow)
                }
                val canMutate = !state.offlineSnapshot && !state.busy
                val phone = row.optString("phone_e164").ifBlank { row.optString("normalized_phone") }
                if (phone.isNotBlank()) OutlinedButton(onClick = { clipboard.setText(AnnotatedString(phone)) }, enabled = canMutate) {
                    Icon(Icons.Outlined.ContentCopy, null); Text("نسخ الرقم", Modifier.padding(start = 6.dp))
                }
                when (section) {
                    AdminSection.SUBSCRIBERS -> {
                        RelatedButtons(listOf(RelatedListKind.SUBSCRIBER_NUMBERS, RelatedListKind.SUBSCRIBER_POINTS, RelatedListKind.SUBSCRIBER_HISTORY), viewModel)
                        if (viewModel.can(AdminPermissions.SUBSCRIBERS_UPDATE)) OutlinedButton(onClick = { viewModel.startForm(AdminFormKind.USER, row) }, enabled = canMutate) { Text("تعديل الملف") }
                        if (viewModel.can(AdminPermissions.SUBSCRIBERS_UPDATE)) OutlinedButton(onClick = { onStatusRequest(if (row.optString("account_status").equals("active", true)) "SUSPENDED" else "ACTIVE") }, enabled = canMutate) { Text(if (row.optString("account_status").equals("active", true)) "إيقاف الحساب" else "إعادة التفعيل") }
                    }
                    AdminSection.USERS -> if (viewModel.can(AdminPermissions.USERS_UPDATE)) {
                        OutlinedButton(onClick = { viewModel.startForm(AdminFormKind.USER, row) }, enabled = canMutate) { Text("تعديل الملف والحالة") }
                        OutlinedButton(onClick = { onStatusRequest(if (row.optString("account_status").equals("active", true)) "SUSPENDED" else "ACTIVE") }, enabled = canMutate) { Text(if (row.optString("account_status").equals("active", true)) "إيقاف الحساب" else "إعادة التفعيل") }
                    }
                    AdminSection.ADDED_NUMBERS -> {
                        Text("إدارة الأرقام هنا للعرض والنسخ والأرشفة/الحالة فقط؛ لا يستطيع المسؤول تعديل رقم الهاتف.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (viewModel.can(AdminPermissions.NUMBERS_UPDATE)) Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf("ACTIVE" to "تفعيل", "INACTIVE" to "تعطيل", "ARCHIVED" to "أرشفة").forEach { (status, label) ->
                                if (!row.optString("status").equals(status, true)) OutlinedButton(onClick = { onStatusRequest(status) }, enabled = canMutate) { Text(label) }
                            }
                        }
                    }
                    AdminSection.ACTIVE_NUMBERS -> RelatedButtons(listOf(RelatedListKind.PROTECTION_TASKS), viewModel)
                    AdminSection.PURCHASES -> if (row.optString("status").equals("PENDING", true)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (viewModel.can(AdminPermissions.PURCHASES_APPROVE)) Button(onClick = { onActionRequest(AdminMutation.APPROVE_PURCHASE, "") }, enabled = canMutate) { Icon(Icons.Outlined.CheckCircle, null); Text("اعتماد", Modifier.padding(start = 5.dp)) }
                        if (viewModel.can(AdminPermissions.PURCHASES_REJECT)) OutlinedButton(onClick = { onActionRequest(AdminMutation.REJECT_PURCHASE, "") }, enabled = canMutate) { Text("رفض مع سبب") }
                    }
                    AdminSection.PAYMENT_TASKS, AdminSection.PERIODIC_PAYMENT -> if (row.optString("status").equals("OPEN", true)) {
                        if (viewModel.can(AdminPermissions.TASKS_EXECUTE)) OutlinedButton(onClick = { onActionRequest(AdminMutation.EXECUTE_PAYMENT_TASK, "") }, enabled = canMutate) { Text("تسجيل السداد الخارجي") }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (viewModel.can(AdminPermissions.TASKS_RESCHEDULE)) OutlinedButton(onClick = { onActionRequest(AdminMutation.RESCHEDULE_PAYMENT_TASK, "") }, enabled = canMutate) { Text("إعادة جدولة") }
                            if (viewModel.can(AdminPermissions.TASKS_CANCEL)) OutlinedButton(onClick = { onActionRequest(AdminMutation.CANCEL_PAYMENT_TASK, "") }, enabled = canMutate) { Text("إلغاء بسبب") }
                        }
                    }
                    AdminSection.TASK_PLANS -> {
                        RelatedButtons(listOf(RelatedListKind.PLAN_TASKS), viewModel)
                        Text("عرض خطط وإصدارات التشغيل التاريخية فقط. لا يعيد التطبيق بناء الخطط أو تغيير المهام التابعة.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AdminSection.PROVIDERS -> {
                        if (viewModel.can(AdminPermissions.PROVIDERS_MANAGE)) {
                            OutlinedButton(onClick = { viewModel.startForm(AdminFormKind.PROVIDER, row) }, enabled = canMutate) { Text("تعديل الشركة") }
                            RelatedButtons(listOf(RelatedListKind.PROVIDER_PREFIXES, RelatedListKind.PROVIDER_TARIFFS), viewModel)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { viewModel.startForm(AdminFormKind.PREFIX) }, enabled = canMutate) { Text("إضافة بادئة") }
                                OutlinedButton(onClick = { viewModel.startForm(AdminFormKind.TARIFF) }, enabled = canMutate) { Text("إضافة تعرفة") }
                            }
                            OutlinedButton(onClick = { viewModel.open(AdminSection.TASK_SETTINGS) }, enabled = canMutate) { Text("فتح إعدادات مهام A05") }
                            OutlinedButton(onClick = { viewModel.startForm(AdminFormKind.TASK_SETTINGS, row.optJSONObject("task_settings")) }, enabled = canMutate && viewModel.can(AdminPermissions.TASKS_SETTINGS)) { Text("تعديل/إنشاء إعدادات الشركة") }
                        }
                        val child = state.relatedRows.getOrNull(state.relatedSelectedIndex)
                        if (state.relatedKind in setOf(RelatedListKind.PROVIDER_PREFIXES, RelatedListKind.PROVIDER_TARIFFS) && child != null && viewModel.can(AdminPermissions.PROVIDERS_MANAGE)) {
                            OutlinedButton(onClick = { viewModel.startForm(if (state.relatedKind == RelatedListKind.PROVIDER_PREFIXES) AdminFormKind.PREFIX else AdminFormKind.TARIFF, child) }, enabled = canMutate) { Text("تعديل السجل المحدد") }
                        }
                    }
                    AdminSection.PACKAGES -> if (viewModel.can(AdminPermissions.PACKAGES_MANAGE)) OutlinedButton(onClick = { viewModel.startForm(AdminFormKind.PACKAGE, row) }, enabled = canMutate) { Text("تعديل / إظهار / إخفاء") }
                    AdminSection.PAYMENT_METHODS -> if (viewModel.can(AdminPermissions.PAYMENT_METHODS_MANAGE)) OutlinedButton(onClick = { viewModel.startForm(AdminFormKind.PAYMENT_METHOD, row) }, enabled = canMutate) { Text("تعديل / إظهار / إخفاء") }
                    AdminSection.TASK_SETTINGS -> if (viewModel.can(AdminPermissions.TASKS_SETTINGS)) OutlinedButton(onClick = { viewModel.startForm(AdminFormKind.TASK_SETTINGS, row) }, enabled = canMutate) { Text("إضافة إصدار إعدادات مهام") }
                    AdminSection.COMMUNICATIONS -> {
                        RelatedButtons(listOf(RelatedListKind.SUPPORT_MESSAGES), viewModel)
                        if (row.optString("request_status") == "PENDING" && viewModel.can(AdminPermissions.SUPPORT_READ)) {
                            OutlinedButton(onClick = { onActionRequest(AdminMutation.APPROVE_SUPPORT_REQUEST, "") }, enabled = canMutate) { Text("اعتماد طلب الدعم") }
                        }
                        if (row.optString("request_status").let { it.isBlank() || it == "APPROVED" } && row.optString("status").equals("OPEN", true) && viewModel.can(AdminPermissions.SUPPORT_READ)) {
                            OutlinedButton(onClick = { onActionRequest(AdminMutation.SEND_SUPPORT_REPLY, "") }, enabled = canMutate) { Text("الرد على المحادثة") }
                            OutlinedButton(onClick = { onActionRequest(AdminMutation.CLOSE_SUPPORT, "تم الإغلاق من الإدارة") }, enabled = canMutate) { Text("إغلاق المحادثة") }
                        }
                    }
                    AdminSection.FINANCE -> if (viewModel.can(AdminPermissions.FINANCE_WRITE)) OutlinedButton(onClick = { viewModel.startForm(AdminFormKind.EXPENSE) }, enabled = canMutate) { Text("تسجيل مصروف") }
                    AdminSection.ACCOUNT -> OutlinedButton(onClick = onLogout, enabled = canMutate) { Icon(Icons.AutoMirrored.Outlined.Logout, null); Text("تسجيل الخروج", Modifier.padding(start = 6.dp)) }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun RelatedButtons(kinds: List<RelatedListKind>, viewModel: AdminViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
        kinds.filter { viewModel.hasPermission(relatedReadPermission(it)) }.forEach { kind ->
            OutlinedButton(onClick = { viewModel.openRelated(kind) }, modifier = Modifier.weight(1f)) { Text(kind.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun RelatedToolbar(state: AdminUiState, viewModel: AdminViewModel) {
    Text("العناصر المرتبطة: ${state.relatedKind?.title}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp))
}

@Composable
private fun AdminFormPanel(state: AdminUiState, viewModel: AdminViewModel) {
    val kind = state.formKind ?: return
    var confirmSave by remember(state.formKind, state.formId) { mutableStateOf(false) }
    if (confirmSave) AlertDialog(onDismissRequest = { confirmSave = false }, title = { Text(if (kind == AdminFormKind.EXPENSE) "تأكيد ترحيل مصروف" else "تأكيد حفظ إعداد الإدارة") },
        text = { Text(when (kind) {
            AdminFormKind.TASK_SETTINGS -> "سيُضاف إصدار إعداد جديد للشركة دون إعادة بناء المهام أو الخطط السابقة. تحقق من بيانات الإصدار والسريان قبل المتابعة."
            AdminFormKind.EXPENSE -> "سيُسجل مصروف بقيمة ${state.formValues["amount"].orEmpty()} ${state.formValues["currency"].orEmpty()}، الوصف: ${state.formValues["description"].orEmpty()}، المرجع: ${state.formValues["reference"].orEmpty()}. العملية append-only وتؤثر في الرصيد الدفتري."
            else -> "سيُحفظ التغيير عبر RPC مدقق وقد يؤثر في حل البادئات/التعرفة للعمليات المستقبلية. اللقطات التاريخية لا يعاد احتسابها."
        }) },
        confirmButton = { TextButton(onClick = { confirmSave = false; viewModel.saveForm() }, enabled = !state.busy) { Text("تأكيد الحفظ") } },
        dismissButton = { TextButton(onClick = { confirmSave = false }) { Text("إلغاء") } })
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), shape = PanelShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) {
        Column(Modifier.fillMaxWidth().padding(14.dp).heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (state.formId == null) "إضافة ${formTitle(kind)}" else "تعديل ${formTitle(kind)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(formContractNote(kind), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            formFields(kind).forEach { (key, label, keyboard) ->
                if (key == "expense_type_id" && kind == AdminFormKind.EXPENSE) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.formOptions.isEmpty()) Text("لا توجد أنواع مصروف نشطة أو لا تتوفر صلاحية قراءتها.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        state.formOptions.forEach { option ->
                            val id = option.optString("id")
                            OutlinedButton(onClick = { viewModel.updateForm("expense_type_id", id) }) {
                                Text(if (state.formValues[key] == id) "✓ ${option.optString("name")}" else option.optString("name"))
                            }
                        }
                    }
                } else if (key in setOf("status", "account_status", "allow_reschedule", "allow_post_expiry_creation", "create_first_task_on_activation", "create_first_task_on_renewal", "is_visible", "is_active", "tariff_mode")) {
                    val options = when (key) {
                        "allow_reschedule", "allow_post_expiry_creation", "create_first_task_on_activation", "create_first_task_on_renewal", "is_visible", "is_active" -> listOf("true", "false")
                        "account_status" -> listOf("ACTIVE", "SUSPENDED", "DISABLED")
                        "tariff_mode" -> listOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")
                        else -> if (kind == AdminFormKind.USER) listOf("ACTIVE", "SUSPENDED", "DISABLED") else listOf("ACTIVE", "INACTIVE", "ARCHIVED").let { if (kind in setOf(AdminFormKind.PROVIDER, AdminFormKind.PREFIX, AdminFormKind.TARIFF)) it.take(2) else it }
                    }
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { options.forEach { option ->
                        val chosen = state.formValues[key] == option
                        OutlinedButton(onClick = { viewModel.updateForm(key, option) }, colors = if (chosen) ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary) else ButtonDefaults.outlinedButtonColors()) { Text(optionLabel(option)) }
                    } }
                } else OutlinedTextField(value = state.formValues[key].orEmpty(), onValueChange = { viewModel.updateForm(key, it) },
                    modifier = Modifier.fillMaxWidth(), label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                    singleLine = key !in setOf("transfer_instructions", "description", "reason", "reference"), minLines = if (key in setOf("transfer_instructions", "description", "reason", "reference")) 2 else 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (kind in setOf(AdminFormKind.TASK_SETTINGS, AdminFormKind.PROVIDER, AdminFormKind.PREFIX, AdminFormKind.TARIFF, AdminFormKind.EXPENSE)) confirmSave = true else viewModel.saveForm() }, enabled = !state.busy) { if (state.busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(if (kind == AdminFormKind.EXPENSE) "ترحيل المصروف" else "حفظ عبر Backend") }
                OutlinedButton(onClick = viewModel::cancelForm, enabled = !state.busy) { Text("إلغاء") }
            }
        }
    }
}

@Composable
private fun NotificationComposer(state: AdminUiState, viewModel: AdminViewModel) {
    var confirmSend by remember { mutableStateOf(false) }
    if (confirmSend) AlertDialog(onDismissRequest = { confirmSend = false }, title = { Text("تأكيد إرسال الإشعار") },
        text = { Text("سيُرسل الإشعار إلى ${when (state.targetType) { "all" -> "جميع الحسابات النشطة"; "all_subscribers" -> "جميع المشتركين النشطين"; else -> "${state.targetName} (${if (state.targetType == "subscriber") "مشترك" else "مستخدم"})" }}، ولن يمكن سحبه بعد الإرسال. معاينة: ${state.notificationTitle.trim()} — ${state.notificationBody.trim()}") },
        confirmButton = { TextButton(onClick = { confirmSend = false; viewModel.sendNotification() }, enabled = !state.busy) { Text("إرسال") } },
        dismissButton = { TextButton(onClick = { confirmSend = false }) { Text("إلغاء") } })
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Card(shape = PanelShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("إنشاء إشعار إداري", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("all" to "عام", "all_subscribers" to "كل المشتركين", "subscriber" to "مشترك محدد", "user" to "مستخدم محدد").forEach { (id, label) ->
                    OutlinedButton(onClick = { viewModel.setTargetType(id) }, enabled = !state.busy) { Text(if (state.targetType == id) "✓ $label" else label) }
                } }
                if (state.targetType in setOf("subscriber", "user")) {
                    OutlinedTextField(value = state.targetQuery, onValueChange = viewModel::updateTargetQuery, modifier = Modifier.fillMaxWidth(), label = { Text("ابحث عن المستهدف بالاسم/المعرف") }, singleLine = true)
                    if (state.targetName.isNotBlank()) Text("المستهدف: ${state.targetName} (${state.targetId})", style = MaterialTheme.typography.bodySmall)
                    state.targetRows.take(4).forEach { target -> TextButton(onClick = { viewModel.selectTarget(target) }) { Text("${target.optString("_recipient_name").ifBlank { target.optString("email") }} · ${target.optString("_recipient_id")}") } }
                }
                OutlinedTextField(value = state.notificationTitle, onValueChange = viewModel::updateNotificationTitle, modifier = Modifier.fillMaxWidth(), label = { Text("العنوان (حتى 160 حرفًا)") }, singleLine = true)
                OutlinedTextField(value = state.notificationBody, onValueChange = viewModel::updateNotificationBody, modifier = Modifier.fillMaxWidth(), label = { Text("المحتوى (حتى 4000 حرف)") }, minLines = 2)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(10.dp)) {
                    Text("معاينة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(state.notificationTitle.ifBlank { "عنوان الإشعار" }, fontWeight = FontWeight.SemiBold)
                    Text(state.notificationBody.ifBlank { "سيظهر محتوى الإشعار هنا." }, style = MaterialTheme.typography.bodySmall)
                } }
                Button(onClick = { confirmSend = true }, enabled = viewModel.can(AdminPermissions.NOTIFICATIONS_SEND) && !state.busy && state.notificationTitle.isNotBlank() && state.notificationBody.isNotBlank() && (state.targetType == "all" || state.targetId.isNotBlank())) { Text("إرسال وحفظ السجل") }
                if (!viewModel.can(AdminPermissions.NOTIFICATIONS_SEND)) Text("الإرسال مخفي/معطل لعدم وجود صلاحية admin_notifications.send.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ReportControls(state: AdminUiState, viewModel: AdminViewModel, export: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("التقارير تُشتق من الجداول الفعلية ولا تمثل مصدر الحقيقة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            ReportType.all.forEach { type ->
                val isSelected = state.reportTypeId == type.id
                OutlinedButton(onClick = { viewModel.setReportType(type.id) },
                    colors = if (isSelected) ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) else ButtonDefaults.outlinedButtonColors(),
                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
                    Text(if (isSelected) "✓ ${type.label}" else type.label, maxLines = 1)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = state.filterFrom, onValueChange = viewModel::setFilterFrom, modifier = Modifier.weight(1f), label = { Text("من YYYY-MM-DD") }, singleLine = true)
            OutlinedTextField(value = state.filterTo, onValueChange = viewModel::setFilterTo, modifier = Modifier.weight(1f), label = { Text("إلى YYYY-MM-DD") }, singleLine = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::loadReport, enabled = viewModel.can(AdminPermissions.REPORTS_READ) && !state.loading) { Text("عرض التقرير") }
            if (viewModel.can(AdminPermissions.REPORTS_EXPORT) && state.reportRows.isNotEmpty()) OutlinedButton(onClick = export) { Text("تصدير CSV") }
        }
        if (!viewModel.hasPermission(AdminPermissions.REPORTS_EXPORT)) Text("التصدير غير متاح: الصلاحية admin_reports.export غير ممنوحة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AccountPanel(state: AdminUiState, viewModel: AdminViewModel, onLogout: () -> Unit) {
    val row = state.rows.firstOrNull()
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = PanelShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("الحساب الإداري", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (row == null) Text("لا توجد معلومات حساب متاحة.") else {
                val profile = row.optJSONObject("profile")
                FieldLine("الاسم", profile?.optString("full_name").orEmpty())
                FieldLine("البريد", profile?.optString("email").orEmpty())
                val roles = row.optJSONArray("roles")
                FieldLine("الدور", if (roles == null) "" else (0 until roles.length()).joinToString { roles.optJSONObject(it)?.optString("name").orEmpty() })
                FieldLine("إصدار النظام", row.optString("system_version"))
                Text("الصلاحيات الفعلية من Backend", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                Text(state.permissions.sorted().joinToString(" · ").ifBlank { "لا توجد صلاحيات مخزنة" }, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onLogout, enabled = !state.busy, modifier = Modifier.padding(top = 10.dp)) { Icon(Icons.AutoMirrored.Outlined.Logout, null); Text("تسجيل الخروج", Modifier.padding(start = 6.dp)) }
        }
    }
}

@Composable
private fun StatusFilters(selected: String, section: AdminSection, onSelect: (String) -> Unit) {
    val statuses = if (section in setOf(AdminSection.USERS, AdminSection.SUBSCRIBERS)) listOf("" to "الكل", "ACTIVE" to "نشط", "SUSPENDED" to "موقوف", "DISABLED" to "معطل") else if (section in setOf(AdminSection.PACKAGES, AdminSection.PAYMENT_METHODS))
        listOf("" to "الكل", "ACTIVE" to "مفعّل", "INACTIVE" to "معطل", "VISIBLE" to "مرئي", "HIDDEN" to "مخفي") else
        listOf("" to "الكل", "PENDING" to "قيد المراجعة", "OPEN" to "مفتوحة", "ACTIVE" to "نشط", "INACTIVE" to "غير نشط", "ARCHIVED" to "مؤرشف", "APPROVED" to "معتمد", "REJECTED" to "مرفوض", "COMPLETED" to "مكتمل", "CANCELLED" to "ملغى", "EXPIRED" to "منتهية")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        statuses.forEach { (value, label) ->
            val isSelected = selected == value
            OutlinedButton(onClick = { onSelect(value) },
                colors = if (isSelected) ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) else ButtonDefaults.outlinedButtonColors(),
                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
                Text(if (isSelected) "✓ $label" else label, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SearchTypeFilters(selected: String, onSelect: (String) -> Unit) {
    val types = listOf("" to "الكل", "customer_profile" to "المستخدمون والعملاء", "phone_number" to "الأرقام", "points_purchase" to "طلبات الشراء",
        "telecom_company" to "الشركات", "operation" to "العمليات", "periodic_task" to "المهام", "admin_notification_campaign" to "الإشعارات",
        "points_package" to "الباقات", "payment_method" to "وسائل الدفع", "task_configuration" to "إعدادات التشغيل")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        types.forEach { (value, label) ->
            val isSelected = selected == value
            OutlinedButton(onClick = { onSelect(value) },
                colors = if (isSelected) ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) else ButtonDefaults.outlinedButtonColors(),
                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
                Text(if (isSelected) "✓ $label" else label, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PaymentTaskFilters(state: AdminUiState, viewModel: AdminViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        OutlinedTextField(value = state.filterProviderQuery, onValueChange = viewModel::setFilterProvider, modifier = Modifier.fillMaxWidth(),
            label = { Text("تصفية حسب اسم الشركة أو رمزها") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = state.filterFrom, onValueChange = viewModel::setFilterFrom, modifier = Modifier.weight(1f), label = { Text("استحقاق من YYYY-MM-DD") }, singleLine = true)
            OutlinedTextField(value = state.filterTo, onValueChange = viewModel::setFilterTo, modifier = Modifier.weight(1f), label = { Text("إلى YYYY-MM-DD") }, singleLine = true)
        }
        OutlinedButton(onClick = viewModel::applyFilters) { Text("تطبيق فلاتر المهام") }
    }
}

@Composable
private fun DisplayFields(section: AdminSection, row: JSONObject) {
    val keys = when (section) {
        AdminSection.SUBSCRIBERS, AdminSection.USERS -> listOf("name", "email", "public_user_code", "account_type", "account_status", "balance_points", "number_count", "protection_count", "public_subscriber_code", "activated_at", "created_at")
        AdminSection.ADDED_NUMBERS -> listOf("display_phone", "provider_name", "customer_name", "public_added_number_code", "status", "added_at")
        AdminSection.ACTIVE_NUMBERS -> listOf("phone_e164", "customer_name", "provider_name", "status", "start_at", "end_at", "duration_unit_days_snapshot", "points_per_unit_snapshot", "units_snapshot", "points_cost_snapshot", "remaining_days", "extension_warning_days", "needs_extension")
        AdminSection.PURCHASES -> listOf("public_purchase_code", "customer_name", "package_name_snapshot", "points_snapshot", "price_snapshot", "currency_snapshot", "payment_method_name_snapshot", "payment_method_details_snapshot", "transfer_reference", "submitted_at", "status", "rejection_reason")
        AdminSection.PAYMENT_TASKS, AdminSection.PERIODIC_PAYMENT -> listOf("public_task_code", "phone_e164", "customer_name", "company_snapshot", "due_at", "amount", "currency", "task_attention", "status", "execution_reference")
        AdminSection.TASK_PLANS -> listOf("protection_period_id", "task_configuration_id", "status", "anchor_at", "version", "created_at", "updated_at")
        AdminSection.PROVIDERS -> when {
            row.has("prefix") -> listOf("prefix", "status", "created_at")
            row.has("effective_from") -> listOf("tariff_mode", "duration_unit_days", "points_per_unit", "rate", "currency", "effective_from", "effective_to", "status")
            else -> listOf("name", "code", "status", "prefix_count")
        }
        AdminSection.PACKAGES -> listOf("code", "name", "points", "price", "currency", "display_order", "is_visible", "is_active")
        AdminSection.PAYMENT_METHODS -> listOf("code", "name", "type", "receiving_account", "transfer_instructions", "display_order", "is_visible", "is_active")
        AdminSection.TASK_SETTINGS -> listOf("provider_name", "interval_days", "task_amount", "currency", "visibility_days_before", "allow_reschedule", "allow_post_expiry_creation", "post_expiry_grace_days", "create_first_task_on_activation", "create_first_task_on_renewal", "effective_from", "effective_to")
        AdminSection.FINANCE -> listOf("entry_type", "direction", "amount", "currency", "description", "source_type", "source_id", "created_at")
        AdminSection.COMMUNICATIONS -> listOf("subject", "customer_name", "status", "created_at", "updated_at")
        AdminSection.ABOUT -> listOf("content_key", "version", "status", "title", "body")
        AdminSection.SETUP -> listOf("enabled", "customer_message", "updated_by", "updated_at")
        AdminSection.NOTIFICATIONS -> listOf("target_type", "target_definition", "title", "body", "status", "recipient_count", "sent_at")
        else -> listOf("_record_type", "id", "status", "created_at")
    }
    keys.forEach { key ->
        val value = fieldValue(row, key)
        if (value.isNotBlank() && value != "null") FieldLine(fieldLabel(key), value)
    }
}

@Composable
private fun DashboardTile(section: AdminSection, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val icon = sectionIcon(section)
    Card(modifier = modifier.heightIn(min = 104.dp).clickable(onClick = onClick), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(section.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CompactRecord(row: JSONObject, selected: Boolean, onClick: () -> Unit, section: AdminSection, modifier: Modifier = Modifier) {
    val title = recordTitle(section, row)
    val subtitle = recordSubtitle(row)
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onClick), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.background, shape = CircleShape, modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Outlined.ReceiptLong, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val status = row.optString("status")
            if (status.isNotBlank()) StatusChip(status)
        }
    }
}

@Composable
private fun StatusChip(value: String) {
    val good = value.lowercase() in setOf("active", "approved", "completed", "succeeded")
    val bad = value.lowercase() in setOf("rejected", "failed", "cancelled", "suspended", "archived")
    Surface(color = when { good -> MaterialTheme.colorScheme.tertiary.copy(alpha = .16f); bad -> MaterialTheme.colorScheme.error.copy(alpha = .14f); else -> MaterialTheme.colorScheme.primary.copy(alpha = .12f) }, shape = RoundedCornerShape(20.dp)) {
        Text(statusLabel(value), color = when { good -> MaterialTheme.colorScheme.tertiary; bad -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.primary },
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), maxLines = 1)
    }
}

@Composable
private fun AdminBottomBar(section: AdminSection, viewModel: AdminViewModel) {
    val candidates = listOf(AdminSection.ACCOUNT, AdminSection.REPORTS, AdminSection.HOME, AdminSection.NOTIFICATIONS, AdminSection.SEARCH)
        .filter { viewModel.hasPermission(it.readPermission) }
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, windowInsets = WindowInsets.navigationBars) {
        candidates.forEach { destination -> NavigationBarItem(selected = section == destination, onClick = { viewModel.open(destination) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            icon = { Icon(sectionIcon(destination), contentDescription = destination.title) }, label = { Text(destination.title, maxLines = 1) }, alwaysShowLabel = true) }
    }
}

@Composable
private fun TopHeader(title: String, adminName: String, showBack: Boolean, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "رجوع") }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (adminName.isNotBlank()) Text(adminName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Image(painterResource(R.drawable.aman_brand_icon), contentDescription = "شعار أمان", modifier = Modifier.size(25.dp))
            Text("AMAN", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .55f))
}

@Composable
private fun StatusMessages(state: AdminUiState, viewModel: AdminViewModel) {
    if (state.error != null) MessageCard(state.error, true, viewModel::clearMessages, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    if (state.notice != null) MessageCard(state.notice, false, viewModel::clearMessages, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    if (state.offlineSnapshot) MessageCard("وضع Offline: المعروض لقطة مشفرة قديمة${state.cachedAtMillis?.let { " (تحديث ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))})" }.orEmpty()}؛ جميع التغييرات معطلة.", false, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

@Composable
private fun FieldLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(.4f))
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(.6f), maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MessageCard(message: String, isError: Boolean, onDismiss: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isError) MaterialTheme.colorScheme.error.copy(alpha = .12f) else MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, if (isError) MaterialTheme.colorScheme.error.copy(alpha = .4f) else MaterialTheme.colorScheme.outline)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (onDismiss != null) TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    }
}

@Composable
private fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(22.dp), contentAlignment = Alignment.Center) { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun CenterProgress(message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
    }
}

private fun actionTitle(action: AdminMutation) = when (action) {
    AdminMutation.APPROVE_PURCHASE -> "اعتماد الشراء بعد التحقق من المرجع"
    AdminMutation.REJECT_PURCHASE -> "رفض طلب الشراء"
    AdminMutation.EXECUTE_PAYMENT_TASK -> "تسجيل سداد تم خارجيًا"
    AdminMutation.RESCHEDULE_PAYMENT_TASK -> "إعادة جدولة المهمة"
    AdminMutation.CANCEL_PAYMENT_TASK -> "إلغاء المهمة"
    AdminMutation.SEND_SUPPORT_REPLY -> "إرسال رد الدعم"
    AdminMutation.APPROVE_SUPPORT_REQUEST -> "اعتماد طلب الدعم"
    AdminMutation.CLOSE_SUPPORT -> "إغلاق محادثة الدعم"
    else -> "تأكيد الإجراء"
}
private fun actionInputLabel(action: AdminMutation) = when (action) {
    AdminMutation.REJECT_PURCHASE -> "سبب الرفض"
    AdminMutation.EXECUTE_PAYMENT_TASK -> "مرجع السداد الخارجي"
    AdminMutation.RESCHEDULE_PAYMENT_TASK -> "الموعد الجديد ISO-8601 (مثال 2026-10-04T12:00:00Z)"
    AdminMutation.CANCEL_PAYMENT_TASK -> "سبب الإلغاء"
    AdminMutation.SEND_SUPPORT_REPLY -> "نص الرد"
    AdminMutation.CLOSE_SUPPORT -> "سبب الإغلاق"
    else -> "البيانات المطلوبة"
}
private fun actionExplanation(action: AdminMutation, row: JSONObject?) = when (action) {
    AdminMutation.APPROVE_PURCHASE -> "تحقق خارجيًا من رقم المرجع ومطابقته للطلب والمبلغ قبل الاعتماد. بعد تأكيدك، سيعتمد Backend الطلب ويضيف النقاط ويسجل ledger والإشعار والتدقيق. رقم الطلب: ${row?.optString("public_purchase_code").orEmpty()} · المرجع: ${row?.optString("transfer_reference").orEmpty()}"
    AdminMutation.REJECT_PURCHASE -> "أدخل سببًا؛ لن تُضاف نقاط، وسيسجل Backend القرار ويرسل إشعارًا للمستخدم."
    AdminMutation.EXECUTE_PAYMENT_TASK -> "نفّذ السداد عبر القناة الخارجية الرسمية أولًا. التطبيق لا يحول أموالًا؛ يسجل فقط نتيجة السداد ومرجعه."
    AdminMutation.RESCHEDULE_PAYMENT_TASK -> "يجب أن يكون الموعد مستقبلًا وضمن فترة الحماية. لا نفترض إعادة بناء خطة المهام؛ GAP-DB-016 ما زال مفتوحًا."
    AdminMutation.CANCEL_PAYMENT_TASK -> "سيُلغي Backend المهمة المفتوحة ويسجل سبب الإلغاء والتدقيق."
    AdminMutation.SEND_SUPPORT_REPLY -> "سيظهر الرد للعميل في سجل المحادثة باسم حساب الإدارة المسجل."
    AdminMutation.APPROVE_SUPPORT_REQUEST -> "سيعتمد Backend الطلب ويفتح المحادثة ويضيف إشعارًا للعميل ويسجل التدقيق."
    AdminMutation.CLOSE_SUPPORT -> "سيغلق Backend المحادثة المفتوحة مع حفظ السبب والتدقيق."
    else -> "سيتحقق Backend من الصلاحيات والحالة ويسجل الأثر."
}
private fun actionInput(action: AdminMutation): String = actionInputLabel(action)
private fun formTitle(kind: AdminFormKind) = when (kind) {
    AdminFormKind.USER -> "الملف الشخصي"; AdminFormKind.PROVIDER -> "الشركة"; AdminFormKind.PREFIX -> "بادئة رقم"; AdminFormKind.TARIFF -> "التعرفة"
    AdminFormKind.PACKAGE -> "باقة نقاط"; AdminFormKind.PAYMENT_METHOD -> "وسيلة دفع"; AdminFormKind.TASK_SETTINGS -> "إعدادات المهام"
    AdminFormKind.EXPENSE -> "مصروف"
}
private fun formContractNote(kind: AdminFormKind) = when (kind) {
    AdminFormKind.PREFIX -> "الإضافة والتعديل والحالة عبر admin_save_telecom_prefix، مع تدقيق الخادم. لا حذف صلب."
    AdminFormKind.TARIFF -> "تُحفظ أيام الوحدة وسعرها بالنقاط مع القيمة النقدية والعملة المطلوبة في V11. خصم العميل يعتمد نقاط الوحدة فقط؛ أدخل وقتًا ISO-8601."
    AdminFormKind.PROVIDER -> "يحفظ أيام تنبيه العميل قبل انتهاء الحماية ضمن إعداد الشركة، عبر Backend مع سجل تدقيق."
    AdminFormKind.USER -> "تحديث customer_profile عبر admin_update_customer_profile؛ لا يغير هوية الدخول ولا ينشئ دورًا."
    AdminFormKind.PACKAGE -> "تُحفظ النقاط والسعر والظهور والتفعيل في أعمدة points_package الفعلية."
    AdminFormKind.PAYMENT_METHOD -> "تُحفظ وسيلة الدفع في أعمدتها الفعلية receiving_account وtransfer_instructions وغيرها."
    AdminFormKind.TASK_SETTINGS -> "يحفظ الترحيل نسخة إعداد سارية جديدة ولا يعيد كتابة المهام أو الخطط التاريخية؛ إعادة البناء غير مخولة."
    AdminFormKind.EXPENSE -> "إنشاء مصروف append-only مع قيد مالي وفحص الرصيد وسجل تدقيق؛ لا تعديل أو حذف لقيد مرحّل."
}
private data class FieldSpec(val key: String, val label: String, val keyboard: KeyboardType = KeyboardType.Text)
private fun formFields(kind: AdminFormKind): List<FieldSpec> = when (kind) {
    AdminFormKind.USER -> listOf(FieldSpec("name", "الاسم"), FieldSpec("email", "البريد"), FieldSpec("account_status", "حالة الحساب"), FieldSpec("reason", "سبب التغيير"))
    AdminFormKind.PROVIDER -> listOf(FieldSpec("code", "رمز الشركة"), FieldSpec("name", "الاسم"), FieldSpec("status", "الحالة"), FieldSpec("extension_warning_days", "أيام التحذير قبل الانتهاء", KeyboardType.Number))
    AdminFormKind.PREFIX -> listOf(FieldSpec("prefix", "البادئة (أرقام)"), FieldSpec("status", "الحالة"))
    AdminFormKind.TARIFF -> listOf(FieldSpec("tariff_mode", "وحدة التعرفة"), FieldSpec("duration_unit_days", "أيام الوحدة", KeyboardType.Number), FieldSpec("points_per_unit", "نقاط لكل وحدة", KeyboardType.Number), FieldSpec("rate", "القيمة النقدية للوحدة", KeyboardType.Decimal), FieldSpec("currency", "عملة القيمة النقدية"), FieldSpec("effective_from", "تاريخ السريان ISO-8601"), FieldSpec("effective_to", "تاريخ الانتهاء ISO-8601 (اختياري)"), FieldSpec("status", "الحالة"))
    AdminFormKind.PACKAGE -> listOf(FieldSpec("code", "رمز الباقة"), FieldSpec("name", "اسم الباقة"), FieldSpec("points", "عدد النقاط", KeyboardType.Number), FieldSpec("price", "السعر", KeyboardType.Decimal), FieldSpec("currency", "العملة"), FieldSpec("display_order", "ترتيب العرض", KeyboardType.Number), FieldSpec("is_visible", "مرئية"), FieldSpec("is_active", "مفعّلة"))
    AdminFormKind.PAYMENT_METHOD -> listOf(FieldSpec("code", "رمز وسيلة الدفع"), FieldSpec("name", "اسم وسيلة الدفع"), FieldSpec("type", "النوع"), FieldSpec("receiving_account", "حساب الاستلام"), FieldSpec("transfer_instructions", "التعليمات"), FieldSpec("display_order", "ترتيب العرض", KeyboardType.Number), FieldSpec("is_visible", "مرئية"), FieldSpec("is_active", "مفعّلة"))
    AdminFormKind.TASK_SETTINGS -> listOf(FieldSpec("interval_days", "الفاصل بالأيام", KeyboardType.Number), FieldSpec("task_amount", "قيمة المهمة", KeyboardType.Decimal), FieldSpec("currency", "العملة"), FieldSpec("visibility_days_before", "أيام الظهور قبل الاستحقاق", KeyboardType.Number), FieldSpec("allow_reschedule", "السماح بإعادة الجدولة"), FieldSpec("allow_post_expiry_creation", "السماح بالإنشاء بعد الانتهاء"), FieldSpec("post_expiry_grace_days", "أيام السماح بعد الانتهاء", KeyboardType.Number), FieldSpec("create_first_task_on_activation", "إنشاء أول مهمة عند التفعيل"), FieldSpec("create_first_task_on_renewal", "إنشاء أول مهمة عند التجديد"), FieldSpec("effective_from", "بدء السريان ISO-8601"))
    AdminFormKind.EXPENSE -> listOf(FieldSpec("expense_type_id", "معرّف نوع المصروف"), FieldSpec("amount", "المبلغ", KeyboardType.Decimal), FieldSpec("currency", "العملة"), FieldSpec("description", "الوصف"), FieldSpec("reference", "مرجع (اختياري)"))
}.map { FieldSpec(it.key, it.label, it.keyboard) }
private fun optionLabel(value: String) = when (value) {
    "DAILY" -> "يومي"; "WEEKLY" -> "أسبوعي"; "MONTHLY" -> "شهري"; "YEARLY" -> "سنوي"
    "active", "ACTIVE" -> "نشط"; "inactive", "INACTIVE" -> "غير نشط"; "archived", "ARCHIVED" -> "مؤرشف"; "suspended", "SUSPENDED" -> "موقوف"; "DISABLED" -> "معطل"; "true" -> "نعم"; "false" -> "لا"; else -> value
}
private fun relatedReadPermission(kind: RelatedListKind) = when (kind) {
    RelatedListKind.SUBSCRIBER_NUMBERS -> AdminPermissions.NUMBERS_READ
    RelatedListKind.SUBSCRIBER_POINTS -> AdminPermissions.POINTS_READ
    RelatedListKind.SUBSCRIBER_HISTORY -> AdminPermissions.OPERATIONS_READ
    RelatedListKind.PROTECTION_TASKS -> AdminPermissions.TASKS_READ
    RelatedListKind.PROVIDER_PREFIXES, RelatedListKind.PROVIDER_TARIFFS -> AdminPermissions.PROVIDERS_READ
    RelatedListKind.SUPPORT_MESSAGES -> AdminPermissions.SUPPORT_READ
    RelatedListKind.PLAN_TASKS -> AdminPermissions.TASKS_READ
}
private fun sectionIcon(section: AdminSection): ImageVector = when (section) {
    AdminSection.HOME -> Icons.Outlined.Home; AdminSection.SUBSCRIBERS -> Icons.Outlined.People; AdminSection.USERS -> Icons.Outlined.Person
    AdminSection.ADDED_NUMBERS -> Icons.Outlined.PhoneAndroid; AdminSection.ACTIVE_NUMBERS -> Icons.Outlined.VerifiedUser
    AdminSection.PURCHASES -> Icons.AutoMirrored.Outlined.ReceiptLong; AdminSection.PAYMENT_TASKS -> Icons.Outlined.TaskAlt
    AdminSection.PROVIDERS -> Icons.Outlined.Business; AdminSection.PACKAGES -> Icons.Outlined.Toll; AdminSection.PAYMENT_METHODS -> Icons.Outlined.Payment
    AdminSection.TASK_SETTINGS -> Icons.Outlined.Settings; AdminSection.SEARCH -> Icons.Outlined.Search; AdminSection.NOTIFICATIONS -> Icons.Outlined.Notifications
    AdminSection.REPORTS -> Icons.Outlined.Assessment; AdminSection.ACCOUNT -> Icons.Outlined.AccountCircle
    AdminSection.PERIODIC_PAYMENT -> Icons.Outlined.Payment; AdminSection.TASK_PLANS -> Icons.Outlined.TaskAlt; AdminSection.FINANCE -> Icons.Outlined.Assessment
    AdminSection.COMMUNICATIONS -> Icons.Outlined.People; AdminSection.ABOUT -> Icons.Outlined.Shield
    AdminSection.SETUP -> Icons.Outlined.Settings; AdminSection.LOGIN -> Icons.Outlined.Person
    AdminSection.RECOVERY -> Icons.Outlined.Person
}
private fun fieldValue(row: JSONObject, key: String): String {
    val value = row.opt(key)
    return when (value) { null, JSONObject.NULL -> ""; is JSONObject, is JSONArray -> value.toString(); else -> value.toString() }
}
private fun fieldLabel(key: String) = when (key) {
    "full_name", "name" -> "الاسم"; "username" -> "اسم المستخدم"; "phone" -> "الهاتف"; "email" -> "البريد"; "status" -> "الحالة"
    "account_type" -> "نوع الحساب"; "account_status" -> "حالة الحساب"; "subscriber_status" -> "الاشتراك"; "balance_points" -> "رصيد النقاط"; "number_count" -> "عدد الأرقام"
    "protection_count" -> "الحمايات النشطة"; "phone_e164", "display_phone" -> "الرقم"; "provider_name" -> "الشركة"; "customer_name" -> "العميل"
    "public_added_number_code" -> "رمز الرقم المضاف"; "public_user_code" -> "رمز المستخدم"; "public_subscriber_code" -> "رمز المشترك"
    "added_at" -> "تاريخ الإضافة"; "start_at", "started_at" -> "البداية"; "end_at", "expires_at" -> "الانتهاء"; "duration_days" -> "المدة بالأيام"
    "tariff_mode" -> "وحدة التعرفة"; "tariff_duration_unit_days" -> "أيام وحدة التعرفة"; "tariff_points_per_unit" -> "نقاط لكل وحدة"; "duration_unit_days" -> "أيام الوحدة"; "duration_unit_days_snapshot" -> "أيام الوحدة"; "points_per_unit" -> "نقاط لكل وحدة"; "points_per_unit_snapshot" -> "نقاط لكل وحدة"; "rate" -> "القيمة النقدية"; "currency" -> "العملة"; "total_points_snapshot" -> "النقاط المسجلة"; "points_cost_snapshot" -> "تكلفة النقاط"; "units_snapshot" -> "عدد الوحدات"; "remaining_days" -> "الأيام المتبقية"; "extension_warning_days" -> "أيام التنبيه للتمديد"; "needs_extension" -> "يحتاج تمديد"
    "task_plan_interval_days" -> "فاصل المهام"; "next_task_due" -> "المهمة القادمة"; "next_task_status" -> "حالة المهمة"; "following_task_due" -> "المهمة التالية"
    "public_purchase_code", "request_number" -> "رقم الطلب"; "package_name", "package_name_snapshot" -> "الباقة"; "points", "points_snapshot", "points_amount_snapshot" -> "النقاط"; "price", "price_snapshot", "price_amount_snapshot" -> "السعر"
    "currency_snapshot" -> "العملة"; "payment_method_name_snapshot" -> "وسيلة الدفع"; "payment_method_details", "payment_method_details_snapshot" -> "بيانات وتعليمات الدفع"
    "payment_reference", "transfer_reference" -> "المرجع"; "submitted_at" -> "تاريخ الطلب"; "rejection_reason" -> "سبب الرفض"
    "public_task_code" -> "رمز المهمة"; "task_type" -> "نوع المهمة"; "due_at" -> "موعد الاستحقاق"; "amount_snapshot", "amount" -> "المبلغ"; "task_attention" -> "التوقيت"
    "external_payment_reference", "execution_reference" -> "مرجع السداد"; "short_name" -> "الاسم المختصر"; "code" -> "الرمز"
    "prefix_count" -> "عدد البادئات"; "operational_settings" -> "إعدادات التشغيل"
    "prefix" -> "البادئة"; "country_code" -> "رمز الدولة"; "number_length" -> "طول الرقم"; "effective_from" -> "سريان من"; "effective_to" -> "سريان حتى"
    "display_order" -> "ترتيب العرض"; "type" -> "النوع"; "receiving_account" -> "حساب الاستلام"; "transfer_instructions" -> "التعليمات"; "is_visible" -> "مرئي"; "is_active" -> "مفعّل"
    "payment_data" -> "بيانات الدفع"; "instructions" -> "التعليمات"; "interval_days" -> "الفاصل"
    "task_amount" -> "قيمة المهمة"; "visibility_days_before" -> "أيام الظهور"; "create_first_task_on_activation" -> "مهمة عند التفعيل"
    "create_first_task_on_renewal" -> "مهمة عند التجديد"; "allow_reschedule" -> "إعادة الجدولة"; "allow_post_expiry_creation" -> "بعد الانتهاء"; "post_expiry_grace_days" -> "أيام السماح بعد الانتهاء"
    "target_type" -> "نوع المستهدف"; "target_id" -> "المستهدف"; "target_definition" -> "تعريف المستهدف"; "title" -> "العنوان"; "body" -> "المحتوى"; "recipient_count" -> "عدد المستلمين"; "sent_at" -> "الإرسال"
    "entry_type" -> "نوع القيد"; "direction" -> "الاتجاه"; "description" -> "الوصف"; "source_type" -> "نوع المصدر"; "source_id" -> "معرّف المصدر"
    "subject" -> "الموضوع"; "sender_type" -> "نوع المرسل"; "content_key" -> "مفتاح المحتوى"; "version" -> "الإصدار"
    else -> key
}
private fun searchDestination(row: JSONObject): AdminSection = when (row.optString("_aman_source_table")) {
    "customer_profile" -> if (row.optString("account_type") == "SUBSCRIBER") AdminSection.SUBSCRIBERS else AdminSection.USERS
    "phone_number", "customer_number" -> AdminSection.ADDED_NUMBERS
    "points_purchase" -> AdminSection.PURCHASES
    "periodic_task" -> AdminSection.PAYMENT_TASKS
    "telecom_company" -> AdminSection.PROVIDERS
    "points_package" -> AdminSection.PACKAGES
    "payment_method" -> AdminSection.PAYMENT_METHODS
    "task_configuration" -> AdminSection.TASK_SETTINGS
    "task_plan" -> AdminSection.TASK_PLANS
    "admin_notification_campaign" -> AdminSection.NOTIFICATIONS
    "financial_ledger" -> AdminSection.FINANCE
    else -> AdminSection.REPORTS
}

private fun recordTitle(section: AdminSection, row: JSONObject): String = when {
    row.has("_record_type") -> "${row.optString("_record_type")} · ${row.optString("name").ifBlank { row.optString("full_name") }.ifBlank { row.optString("phone_e164") }.ifBlank { row.optString("public_purchase_code") }.ifBlank { row.optString("request_number") }.ifBlank { row.optString("operation_type") }.ifBlank { row.optString("id") }}"
    section == AdminSection.PROVIDERS -> row.optString("name").ifBlank { row.optString("code") }
    section == AdminSection.PACKAGES || section == AdminSection.PAYMENT_METHODS -> row.optString("name")
    section == AdminSection.USERS || section == AdminSection.SUBSCRIBERS -> row.optString("name").ifBlank { row.optString("email") }.ifBlank { row.optString("public_user_code") }
    section == AdminSection.ADDED_NUMBERS || section == AdminSection.ACTIVE_NUMBERS -> row.optString("phone_e164").ifBlank { row.optString("display_phone") }.ifBlank { row.optString("id") }
    section == AdminSection.PURCHASES -> row.optString("public_purchase_code").ifBlank { row.optString("id") }
    section == AdminSection.PAYMENT_TASKS || section == AdminSection.PERIODIC_PAYMENT -> "${row.optString("phone_e164").ifBlank { row.optString("public_task_code") }} · ${row.optString("due_at")}"
    section == AdminSection.TASK_PLANS -> "خطة v${row.optInt("version")} · ${row.optString("anchor_at")}"
    section == AdminSection.NOTIFICATIONS -> row.optString("title")
    section == AdminSection.COMMUNICATIONS -> row.optString("subject").ifBlank { row.optString("id") }
    section == AdminSection.FINANCE -> "${row.optString("entry_type")} · ${row.optString("amount")} ${row.optString("currency")}"
    else -> row.optString("operation_type").ifBlank { row.optString("id") }
}
private fun recordSubtitle(row: JSONObject): String = listOf(row.optString("customer_name"), row.optString("provider_name"), row.optString("created_at"), row.optString("due_at")).firstOrNull(String::isNotBlank).orEmpty()
private fun statusLabel(value: String) = when (value.lowercase()) {
    "active" -> "نشط"; "inactive" -> "غير نشط"; "archived" -> "مؤرشف"; "pending" -> "قيد المراجعة"; "approved" -> "معتمد"; "rejected" -> "مرفوض"
    "open" -> "مفتوحة"; "completed" -> "مكتملة"; "cancelled" -> "ملغاة"; "failed" -> "فشلت"; "succeeded" -> "ناجحة"; "suspended" -> "موقوفة"; "disabled" -> "معطلة"; "expired" -> "منتهية"; else -> value
}
private fun csv(rows: List<JSONObject>): String {
    if (rows.isEmpty()) return ""
    val keys = rows.flatMap { row -> row.keys().asSequence().filter { !it.startsWith("_") }.toList() }.distinct()
    fun escape(value: String): String = "\"${value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ")}\""
    return buildString {
        appendLine(keys.joinToString(",", transform = ::escape))
        rows.forEach { row -> appendLine(keys.joinToString(",") { key -> escape(fieldValue(row, key)) }) }
    }
}
