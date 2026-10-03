package com.aman.admin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aman.admin.data.AdminMutation
import com.aman.admin.data.AdminSection
import com.aman.admin.data.adminSections
import org.json.JSONObject

private val PanelShape = RoundedCornerShape(18.dp)

@Composable
fun AdminApp(viewModel: AdminViewModel) {
    val state by viewModel.state.collectAsState()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            state.checkingSession -> CenterProgress("التحقق من الجلسة…")
            !state.authenticated -> LoginScreen(
                email = state.email,
                busy = state.busy,
                error = state.error,
                configMessage = viewModel.configurationMessage,
                onEmailChange = viewModel::updateEmail,
                onSignIn = viewModel::signIn,
                onDismiss = viewModel::clearMessages,
            )
            else -> AdminShell(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun LoginScreen(
    email: String,
    busy: Boolean,
    error: String?,
    configMessage: String?,
    onEmailChange: (String) -> Unit,
    onSignIn: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(76.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("AMAN | أمان", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("تسجيل دخول الإدارة", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("البريد الإلكتروني") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("كلمة المرور") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        if (error != null) {
            MessageCard(error, isError = true, onDismiss = onDismiss, modifier = Modifier.padding(top = 14.dp))
        }
        if (configMessage != null) {
            MessageCard(configMessage, isError = false, modifier = Modifier.padding(top = 14.dp))
        }
        Button(
            onClick = { onSignIn(password) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(14.dp),
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text("دخول", fontWeight = FontWeight.SemiBold)
        }
        Text(
            "المصادقة بالبريد وكلمة المرور، والتحقق الإداري عبر Backend/RLS.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun AdminShell(state: AdminUiState, viewModel: AdminViewModel) {
    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        bottomBar = { AdminBottomBar(state.section, viewModel::open) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.section == AdminSection.HOME) {
                HomeScreen(state, viewModel)
            } else {
                RecordSectionScreen(state, viewModel)
            }
        }
    }
}

@Composable
private fun HomeScreen(state: AdminUiState, viewModel: AdminViewModel) {
    Column(Modifier.fillMaxSize()) {
        TopHeader(title = AdminSection.HOME.title, showBack = false, onBack = {})
        if (state.error != null) MessageCard(state.error, isError = true, onDismiss = viewModel::clearMessages, modifier = Modifier.padding(horizontal = 16.dp))
        if (state.notice != null) MessageCard(state.notice, isError = false, onDismiss = viewModel::clearMessages, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        if (state.offlineSnapshot) MessageCard("وضع Offline: هذه لقطة مخزنة ومشفرة وقديمة؛ لا تُنفذ إجراءات حساسة دون اتصال.", isError = false, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Card(
                shape = PanelShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("مساحة الإدارة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("البيانات المعروضة تُقرأ من قاعدة البيانات المتصلة.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("الأقسام", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 10.dp))
            adminSections.chunked(3).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    rowItems.forEach { card ->
                        DashboardTile(card.section, modifier = Modifier.weight(1f)) { viewModel.open(card.section) }
                    }
                    repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("أحدث العمليات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = viewModel::reload) { Icon(Icons.Outlined.Refresh, contentDescription = "تحديث", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (state.loading) CenterProgress("تحميل العمليات…", modifier = Modifier.fillMaxWidth().height(100.dp))
            else if (state.rows.isEmpty() && state.error == null) EmptyState("لا توجد عمليات في المصدر")
            else state.rows.take(8).forEach { row ->
                CompactRecord(row = row, selected = false, onClick = { viewModel.open(AdminSection.REPORTS) }, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
    }
}

@Composable
private fun RecordSectionScreen(state: AdminUiState, viewModel: AdminViewModel) {
    val visibleRows = remember(state.rows, state.searchText, state.section) {
        val query = state.searchText.trim().lowercase()
        if (query.isBlank()) state.rows else state.rows.filter { row -> row.toString().lowercase().contains(query) }
    }
    val selected = state.rows.getOrNull(state.selectedIndex)
    Column(Modifier.fillMaxSize()) {
        TopHeader(title = state.section.title, showBack = true, onBack = { viewModel.open(AdminSection.HOME) })
        if (state.error != null) MessageCard(state.error, isError = true, onDismiss = viewModel::clearMessages, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        if (state.notice != null) MessageCard(state.notice, isError = false, onDismiss = viewModel::clearMessages, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        if (state.offlineSnapshot) MessageCard("وضع Offline: المعروض لقطة مخزنة ومشفرة وقديمة؛ جميع التغييرات تتطلب Backend متصلًا.", isError = false, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        if (!state.section.contractNote.isNullOrBlank()) {
            MessageCard(state.section.contractNote, isError = false, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        SelectedPanel(
            section = state.section,
            row = selected,
            busy = state.busy,
            onAction = { action, input -> viewModel.run(action, input) },
            onSignOut = viewModel::signOut,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        OutlinedTextField(
            value = state.searchText,
            onValueChange = viewModel::updateSearch,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            label = { Text(if (state.section == AdminSection.SEARCH) "البحث في السجلات المتاحة" else "بحث في ${state.section.title}") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = { IconButton(onClick = viewModel::reload) { Icon(Icons.Outlined.Refresh, contentDescription = "تحديث") } },
            singleLine = true,
        )
        Text("السجلات (${visibleRows.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
        when {
            state.loading -> CenterProgress("تحميل ${state.section.title}…", modifier = Modifier.weight(1f).fillMaxWidth())
            visibleRows.isEmpty() && state.error == null -> EmptyState("لا توجد سجلات مطابقة في مصدر البيانات.", modifier = Modifier.weight(1f).fillMaxWidth())
            else -> LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(visibleRows, key = { index, row -> row.optString("id").ifBlank { "row-$index" } }) { index, row ->
                    val selectedIndex = state.rows.indexOf(row)
                    CompactRecord(row, selected = selectedIndex == state.selectedIndex, onClick = { viewModel.select(selectedIndex) })
                }
            }
        }
    }
}

@Composable
private fun SelectedPanel(
    section: AdminSection,
    row: JSONObject?,
    busy: Boolean,
    onAction: (AdminMutation, String?) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    var pendingAction by remember(section, row?.optString("id")) { mutableStateOf<AdminMutation?>(null) }
    var pendingInput by remember(section, row?.optString("id")) { mutableStateOf("") }
    pendingAction?.let { action ->
        val isPaymentExecution = action == AdminMutation.EXECUTE_PAYMENT_TASK
        val isRejection = action == AdminMutation.REJECT_PURCHASE
        val needsInput = isPaymentExecution || isRejection
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(when { isPaymentExecution -> "تأكيد تسجيل السداد"; isRejection -> "رفض طلب الشراء"; else -> "تأكيد اعتماد الطلب" }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(when {
                        isPaymentExecution -> "نفّذ السداد عبر القناة الخارجية الرسمية أولًا. أدخل مرجع العملية؛ التطبيق لا ينفّذ الدفع الخارجي."
                        isRejection -> "أدخل سبب الرفض. لن تُضاف نقاط، وسيسجل Backend القرار ويرسل إشعارًا للمستخدم."
                        else -> "سيعتمد Backend الطلب ويضيف النقاط ويسجل الحركة المالية. هل تريد المتابعة؟"
                    })
                    if (needsInput) OutlinedTextField(
                        value = pendingInput,
                        onValueChange = { pendingInput = it },
                        label = { Text(if (isPaymentExecution) "مرجع السداد الخارجي" else "سبب الرفض") },
                        singleLine = isPaymentExecution,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingAction = null; onAction(action, pendingInput.trim().takeIf { needsInput }) },
                    enabled = !busy && (!needsInput || pendingInput.isNotBlank())) {
                    Text(when { isPaymentExecution -> "تم السداد؛ سجّل النتيجة"; isRejection -> "تأكيد الرفض"; else -> "اعتماد الطلب" })
                }
            },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("إلغاء") } },
        )
    }
    Card(
        modifier = modifier,
        shape = PanelShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (row == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("${section.title} — السجل المحدد", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (section == AdminSection.ACCOUNT) {
                OutlinedButton(onClick = onSignOut, enabled = !busy, modifier = Modifier.padding(top = 10.dp), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                    Text("تسجيل الخروج", modifier = Modifier.padding(start = 6.dp))
                }
            }
            if (row == null) {
                Text("اختر سجلًا من القائمة لعرض بياناته هنا.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            } else {
                val fields = displayFields(row)
                fields.take(7).forEach { (key, value) ->
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(key, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.38f))
                        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.62f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                val status = row.optString("status")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    val phone = row.optString("phone_e164").ifBlank { row.optString("normalized_phone") }
                    if (phone.isNotBlank()) {
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(phone)) }, shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                            Text("نسخ الرقم", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    if (section == AdminSection.PURCHASES && status == "pending") {
                        Button(onClick = { pendingInput = ""; pendingAction = AdminMutation.APPROVE_PURCHASE }, enabled = !busy, shape = RoundedCornerShape(12.dp)) {
                            if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                            Text("اعتماد الطلب", modifier = Modifier.padding(start = 6.dp))
                        }
                        OutlinedButton(onClick = { pendingInput = ""; pendingAction = AdminMutation.REJECT_PURCHASE }, enabled = !busy, shape = RoundedCornerShape(12.dp)) {
                            Text("رفض الطلب")
                        }
                    }
                    if (section == AdminSection.PAYMENT_TASKS && status == "open") {
                        Button(onClick = { pendingInput = ""; pendingAction = AdminMutation.EXECUTE_PAYMENT_TASK }, enabled = !busy, shape = RoundedCornerShape(12.dp)) {
                            if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.TaskAlt, contentDescription = null)
                            Text("تسجيل التنفيذ", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardTile(section: AdminSection, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val icon = sectionIcon(section)
    Card(
        modifier = modifier.height(104.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(section.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CompactRecord(row: JSONObject, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val title = recordTitle(row)
    val subtitle = recordSubtitle(row)
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.background, shape = CircleShape, modifier = Modifier.size(38.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
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
    Surface(color = when (value.lowercase()) {
        "active", "approved", "completed", "succeeded" -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
        "rejected", "failed", "cancelled", "suspended" -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    }, shape = RoundedCornerShape(20.dp)) {
        Text(statusLabel(value), color = when (value.lowercase()) {
            "active", "approved", "completed", "succeeded" -> MaterialTheme.colorScheme.tertiary
            "rejected", "failed", "cancelled", "suspended" -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        }, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), maxLines = 1)
    }
}

@Composable
private fun AdminBottomBar(section: AdminSection, onNavigate: (AdminSection) -> Unit) {
    val destinations = listOf(
        AdminSection.ACCOUNT to Icons.Outlined.AccountCircle,
        AdminSection.REPORTS to Icons.Outlined.Assessment,
        AdminSection.HOME to Icons.Outlined.Home,
        AdminSection.NOTIFICATIONS to Icons.Outlined.Notifications,
        AdminSection.SEARCH to Icons.Outlined.Search,
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, windowInsets = WindowInsets.navigationBars) {
        destinations.forEach { (destination, icon) ->
            NavigationBarItem(
                selected = section == destination,
                onClick = { onNavigate(destination) },
                icon = { Icon(icon, contentDescription = destination.title) },
                label = { Text(destination.title, maxLines = 1) },
                alwaysShowLabel = true,
            )
        }
    }
}

@Composable
private fun TopHeader(title: String, showBack: Boolean, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "رجوع") }
        else Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp).size(23.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("AMAN", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
}

@Composable
private fun MessageCard(message: String, isError: Boolean, onDismiss: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (onDismiss != null) TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    }
}

@Composable
private fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(22.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CenterProgress(message: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
    }
}

private fun sectionIcon(section: AdminSection): ImageVector = when (section) {
    AdminSection.SUBSCRIBERS -> Icons.Outlined.People
    AdminSection.USERS -> Icons.Outlined.Person
    AdminSection.ADDED_NUMBERS -> Icons.Outlined.PhoneAndroid
    AdminSection.ACTIVE_NUMBERS -> Icons.Outlined.VerifiedUser
    AdminSection.PURCHASES -> Icons.AutoMirrored.Outlined.ReceiptLong
    AdminSection.PAYMENT_TASKS -> Icons.Outlined.TaskAlt
    AdminSection.PROVIDERS -> Icons.Outlined.Business
        AdminSection.PACKAGES -> Icons.Outlined.Toll
    AdminSection.PAYMENT_METHODS -> Icons.Outlined.Payment
    AdminSection.TASK_SETTINGS -> Icons.Outlined.Settings
    AdminSection.HOME -> Icons.Outlined.Home
    AdminSection.REPORTS -> Icons.Outlined.Assessment
    AdminSection.ACCOUNT -> Icons.Outlined.AccountCircle
    AdminSection.NOTIFICATIONS -> Icons.Outlined.Notifications
    AdminSection.SEARCH -> Icons.Outlined.Search
}

private fun recordTitle(row: JSONObject): String {
    val keys = listOf("request_number", "customer_name", "name", "full_name", "username", "phone_e164", "normalized_phone", "package_name", "code", "subject", "title", "id")
    return keys.firstNotNullOfOrNull { key -> row.optString(key).takeIf { it.isNotBlank() && it != "null" } } ?: "سجل من قاعدة البيانات"
}

private fun recordSubtitle(row: JSONObject): String {
    val keys = listOf("task_attention", "status", "email", "phone_e164", "provider_name", "created_at", "due_at", "operation_type", "_aman_source_table")
    return keys.mapNotNull { key -> row.optString(key).takeIf { it.isNotBlank() && it != "null" }?.let { "${fieldLabel(key)}: ${if (key == "status") statusLabel(it) else it}" } }.take(2).joinToString("  •  ")
}

private fun displayFields(row: JSONObject): List<Pair<String, String>> {
    val prioritized = listOf("request_number", "phone_e164", "normalized_phone", "customer_name", "full_name", "username", "phone", "email", "account_status", "subscriber_status", "balance_points", "number_count", "protection_count", "provider_name", "package_name", "payment_method_name_snapshot", "payment_reference", "points_amount_snapshot", "price_amount_snapshot", "tariff_points_per_day", "task_plan_interval_days", "next_task_due", "next_task_status", "active_prefixes", "points_per_day", "task_attention", "amount_snapshot", "due_at", "expires_at", "status", "created_at", "user_id", "phone_number_id", "id")
    val keys = prioritized.filter { row.has(it) } + row.keys().asSequence().filter { it !in prioritized && !it.startsWith("_") }.sorted().toList()
    return keys.distinct().mapNotNull { key ->
        val value = row.opt(key)
        if (value == null || value == JSONObject.NULL || value is JSONObject || value.toString().isBlank()) null
        else fieldLabel(key) to (if (key == "status") statusLabel(value.toString()) else value.toString().take(180))
    }
}

private fun statusLabel(value: String): String = when (value.lowercase()) {
    "active" -> "نشط"
    "inactive" -> "غير نشط"
    "archived" -> "مؤرشف"
    "suspended" -> "موقوف"
    "closed" -> "مغلق"
    "pending" -> "قيد الانتظار"
    "processing" -> "قيد المعالجة"
    "approved" -> "مقبول"
    "rejected" -> "مرفوض"
    "open" -> "مفتوحة"
    "completed" -> "مكتملة"
    "cancelled" -> "ملغاة"
    "succeeded" -> "ناجحة"
    "failed" -> "فاشلة"
    "متأخرة", "قادمة" -> value
    else -> value
}

private fun fieldLabel(key: String): String = when (key) {
    "request_number" -> "رقم الطلب"
    "phone_e164", "normalized_phone" -> "رقم الهاتف"
    "customer_name", "full_name" -> "الاسم"
    "username" -> "اسم المستخدم"
    "phone" -> "الهاتف"
    "email" -> "البريد الإلكتروني"
    "account_status" -> "حالة الحساب"
    "subscriber_status" -> "حالة الاشتراك"
    "balance_points" -> "رصيد النقاط"
    "number_count" -> "عدد الأرقام"
    "protection_count" -> "الحمايات النشطة"
    "provider_name" -> "الشركة"
    "package_name" -> "الباقة"
    "payment_method_name_snapshot" -> "وسيلة الدفع"
    "payment_method_details" -> "تعليمات الدفع"
    "payment_reference" -> "مرجع الدفع"
    "points_amount_snapshot" -> "النقاط"
    "price_amount_snapshot" -> "السعر"
    "tariff_points_per_day", "points_per_day" -> "تعرفة النقاط/يوم"
    "task_plan_interval_days" -> "فاصل المهام (أيام)"
    "next_task_due", "due_at" -> "الاستحقاق"
    "next_task_status" -> "حالة المهمة القادمة"
    "active_prefixes" -> "البادئات النشطة"
    "task_attention" -> "تنبيه المهمة"
    "amount_snapshot" -> "قيمة المهمة"
    "expires_at" -> "انتهاء الحماية"
    "status" -> "الحالة"
    "created_at" -> "تاريخ الإنشاء"
    "user_id" -> "معرّف المستخدم"
    "phone_number_id" -> "معرّف الرقم"
    "id" -> "المعرّف"
    "operation_type" -> "نوع العملية"
    "_aman_source_table" -> "مصدر السجل"
    else -> key
}
