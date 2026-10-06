package com.aman.customer.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aman.customer.R
import com.aman.customer.data.CustomerScreen
import com.aman.customer.data.CustomerUiState
import com.aman.customer.data.LoadPhase
import com.aman.customer.data.traceElement

private fun customerTabIcon(destination: CustomerScreen): ImageVector = when (destination) {
    CustomerScreen.SUPPORT -> Icons.Outlined.Email
    CustomerScreen.SEARCH -> Icons.Outlined.Search
    CustomerScreen.HOME -> Icons.Outlined.Home
    CustomerScreen.REPORTS -> Icons.Outlined.Assessment
    else -> Icons.Outlined.AccountCircle
}

@Composable
fun AmanCustomerApp(context: Context, recoveryLink: String? = null) {
    AppContextHolder.context = context.applicationContext
    val vm: CustomerViewModel = viewModel(factory = CustomerViewModel.factory(context))
    val state by vm.state.collectAsState()
    LaunchedEffect(recoveryLink) { vm.acceptRecoveryCallback(recoveryLink) }

    if (state.authenticated && state.passwordChangeRequired) {
        RequiredPasswordChangeScreen(state, vm)
        return
    }

    if (!state.authenticated) {
        when (state.screen) {
            CustomerScreen.INITIALIZATION -> InitializationScreen()
            CustomerScreen.SIGN_UP -> SignUpScreen(state, vm)
            CustomerScreen.RECOVERY -> RecoveryScreen(state, vm)
            CustomerScreen.ABOUT -> {
                TextButton(onClick = { vm.back() }, modifier = Modifier.traceElement("C19.GUEST.BACK")) { Text("عودة") }
                CustomerScreenContent(state, vm, Modifier.fillMaxSize())
            }
            else -> LoginScreen(state, vm)
        }
        return
    }

    BackHandler(enabled = state.navigationBackStack.isNotEmpty()) { vm.back() }
    val tabs = listOf(CustomerScreen.SUPPORT, CustomerScreen.SEARCH, CustomerScreen.HOME, CustomerScreen.REPORTS, CustomerScreen.ACCOUNT)
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            tabs.forEach { destination ->
                NavigationBarItem(
                    selected = state.screen == destination,
                    onClick = { vm.selectTab(destination) },
                    modifier = Modifier.traceElement("C04.NAV.${destination.id}"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    icon = { Icon(customerTabIcon(destination), contentDescription = destination.title) },
                    label = { Text(destination.title, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            val unreadCount = state.data?.related?.get("notifications")?.let { array ->
                (0 until array.length()).count { array.optJSONObject(it)?.optBoolean("is_read", false) == false }
            } ?: 0
            BrandHeader(
                title = state.screen.title,
                unreadCount = unreadCount.takeIf { state.screen == CustomerScreen.HOME },
                onNotifications = if (state.screen == CustomerScreen.HOME) ({ vm.navigate(CustomerScreen.NOTIFICATIONS) }) else null,
                onAbout = { vm.navigate(CustomerScreen.ABOUT) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
            if (state.navigationBackStack.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.back() }, modifier = Modifier.traceElement("${state.screen.id}.HEADER.BACK")) { Text("رجوع") }
                    TextButton(onClick = { vm.load(state.screen) }, modifier = Modifier.traceElement("${state.screen.id}.HEADER.REFRESH")) { Text("تحديث") }
                }
            }
            if (state.stale) NoticeBanner("المعروض نسخة محفوظة قديمة · آخر تحديث ${state.data?.loadedAt?.takeIf { it > 0L }?.let { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) } ?: "غير معروف"}", warning = true)
            if (state.phase == LoadPhase.LOADING && state.data == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                state.error?.let { NoticeBanner(it, true) }
                state.mutationMessage?.let { NoticeBanner(it, warning = it.startsWith("تعذر") || it.startsWith("لا يوجد")) }
                CustomerScreenContent(state, vm, Modifier.weight(1f).fillMaxWidth())
                if (state.pageIndex > 0 || state.pageHasMore) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.load(state.screen, page = state.pageIndex - 1) }, enabled = state.pageIndex > 0) { Text("السابق") }
                        Text("صفحة ${state.pageIndex + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { vm.load(state.screen, page = state.pageIndex + 1) }, enabled = state.pageHasMore) { Text("التالي") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequiredPasswordChangeScreen(state: CustomerUiState, vm: CustomerViewModel) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    AuthScaffold(title = "تعيين كلمة مرور جديدة", screenId = "C20.PASSWORD") {
        Text("لأمان حسابك، يجب تعيين كلمة مرور جديدة قبل متابعة استخدام التطبيق.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth().traceElement("C20.NEW_PASSWORD"),
            label = { Text("كلمة المرور الجديدة") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        OutlinedTextField(confirmation, { confirmation = it }, Modifier.fillMaxWidth().traceElement("C20.CONFIRM_PASSWORD"),
            label = { Text("تأكيد كلمة المرور") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        state.authNotice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        state.authError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.mutationMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { vm.updatePassword(password, confirmation) }, modifier = Modifier.fillMaxWidth().traceElement("C20.SET_PASSWORD"),
            enabled = !state.mutationBusy && !state.authBusy && password.length >= 8 && password == confirmation) {
            if (state.mutationBusy) CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
            else Text("حفظ كلمة المرور الجديدة")
        }
        Text("لن تتغير كلمة المرور إلا عبر Supabase Auth.", color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BrandHeader(title: String, unreadCount: Int?, onNotifications: (() -> Unit)?, onAbout: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painterResource(R.drawable.aman_brand_icon), contentDescription = "شعار أمان", modifier = Modifier.size(38.dp))
            Column {
                Text("AMAN | أمان", Modifier.traceElement("C04.HEADER.BRAND"), color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (unreadCount != null && onNotifications != null) {
            IconButton(onClick = onNotifications, modifier = Modifier.traceElement("C04.NOTIFICATIONS")) {
                Icon(Icons.Outlined.Notifications, contentDescription = if (unreadCount > 0) "الإشعارات، $unreadCount غير مقروء" else "الإشعارات")
            }
        }
        TextButton(onClick = onAbout, modifier = Modifier.traceElement("C19.OPEN")) { Text("عن أمان") }
    }
}

@Composable
private fun InitializationScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(painterResource(R.drawable.aman_brand_icon), contentDescription = "شعار أمان",
                modifier = Modifier.size(88.dp).traceElement("C01.BRAND"))
            Text("أمان حماية وضمان", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            CircularProgressIndicator(Modifier.traceElement("C01.LOADING"))
            Text("جارٍ التحقق من الجلسة وإعدادات الحساب", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LoginScreen(state: CustomerUiState, vm: CustomerViewModel) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    AuthScaffold(title = "تسجيل الدخول", screenId = "C02") {
        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth().traceElement("C02.EMAIL"), label = { Text("البريد الإلكتروني") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth().traceElement("C02.PASSWORD"), label = { Text("كلمة المرور") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        AuthFeedback(state, vm)
        Button(onClick = { vm.signIn(email, password) }, modifier = Modifier.fillMaxWidth().traceElement("C02.SUBMIT"),
            enabled = !state.authBusy && email.contains("@") && password.isNotBlank() && vm.isConfigured()) {
            if (state.authBusy) CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp) else Text("تسجيل الدخول")
        }
        TextButton(onClick = { vm.navigate(CustomerScreen.SIGN_UP) }, modifier = Modifier.traceElement("C02.SIGNUP")) { Text("إنشاء حساب جديد") }
        TextButton(onClick = { vm.navigate(CustomerScreen.RECOVERY) }, modifier = Modifier.traceElement("C02.RECOVERY")) { Text("نسيت كلمة المرور؟") }
        Text("تدار كلمات المرور بواسطة Supabase Auth ولا تُخزن في قاعدة AMAN.", textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SignUpScreen(state: CustomerUiState, vm: CustomerViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var terms by rememberSaveable { mutableStateOf(false) }
    var privacy by rememberSaveable { mutableStateOf(false) }
    AuthScaffold(title = "إنشاء حساب", screenId = "C03") {
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().traceElement("C03.NAME"), label = { Text("الاسم") }, singleLine = true)
        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth().traceElement("C03.EMAIL"), label = { Text("البريد الإلكتروني") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth().traceElement("C03.PASSWORD"), label = { Text("كلمة المرور") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        OutlinedTextField(confirm, { confirm = it }, Modifier.fillMaxWidth().traceElement("C03.CONFIRM"), label = { Text("تأكيد كلمة المرور") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        TextButton(onClick = { terms = !terms }, modifier = Modifier.fillMaxWidth().traceElement("C03.CONSENT.TERMS")) { Text("${if (terms) "☑" else "□"} أوافق على الشروط") }
        TextButton(onClick = { privacy = !privacy }, modifier = Modifier.fillMaxWidth().traceElement("C03.CONSENT.PRIVACY")) { Text("${if (privacy) "☑" else "□"} أوافق على سياسة الخصوصية") }
        TextButton(onClick = { vm.navigate(CustomerScreen.ABOUT) }) { Text("قراءة الشروط والخصوصية") }
        AuthFeedback(state, vm)
        Button(onClick = { vm.signUp(name, email, password, confirm, terms, privacy) }, modifier = Modifier.fillMaxWidth().traceElement("C03.SUBMIT"),
            enabled = !state.authBusy && vm.isConfigured()) {
            if (state.authBusy) CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp) else Text("إنشاء الحساب")
        }
        TextButton(onClick = { vm.navigate(CustomerScreen.LOGIN) }) { Text("لديك حساب؟ تسجيل الدخول") }
        Text("الحساب الجديد من النوع USER. لا ينشأ اشتراك أو رصيد نقاط تلقائيًا.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RecoveryScreen(state: CustomerUiState, vm: CustomerViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var userId by rememberSaveable { mutableStateOf("") }
    AuthScaffold(title = "استعادة الحساب", screenId = "C20") {
        Text("يُطابق الخادم الاسم والبريد ومعرّف العميل، ثم يرسل رابطًا إلى البريد المسجل عند تطابقها. تبقى نتيجة الطلب عامة لحماية الحسابات.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().traceElement("C20.NAME"), label = { Text("الاسم") }, singleLine = true)
        OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth().traceElement("C20.EMAIL"), label = { Text("البريد الإلكتروني") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(userId, { userId = it }, Modifier.fillMaxWidth().traceElement("C20.USER_ID"), label = { Text("معرّف العميل") }, singleLine = true)
        AuthFeedback(state, vm)
        Button(onClick = { vm.requestRecovery(name, email, userId) }, modifier = Modifier.fillMaxWidth().traceElement("C20.SUBMIT"),
            enabled = !state.authBusy && vm.isConfigured()) {
            if (state.authBusy) CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp) else Text("إرسال تعليمات الاستعادة")
        }
        TextButton(onClick = { vm.navigate(CustomerScreen.LOGIN) }) { Text("العودة إلى تسجيل الدخول") }
    }
}

@Composable
private fun AuthScaffold(title: String, screenId: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(painterResource(R.drawable.aman_brand_icon), contentDescription = "شعار أمان", modifier = Modifier.size(52.dp))
            Text("AMAN | أمان", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black, modifier = Modifier.traceElement("$screenId.BRAND"))
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun AuthFeedback(state: CustomerUiState, vm: CustomerViewModel) {
    if (!vm.isConfigured()) NoticeBanner(vm.configurationMessage() ?: "إعداد Supabase غير متاح.", true)
    state.authError?.let { NoticeBanner(it, true) }
    state.authNotice?.let { NoticeBanner(it, false) }
}

@Composable
fun NoticeBanner(message: String, warning: Boolean) {
    val container = if (warning) MaterialTheme.colorScheme.error.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant
    val accent = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Text(message, Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
        .border(1.dp, accent.copy(alpha = 0.45f), MaterialTheme.shapes.small)
        .background(container, MaterialTheme.shapes.small).padding(12.dp), color = if (warning) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall)
}
