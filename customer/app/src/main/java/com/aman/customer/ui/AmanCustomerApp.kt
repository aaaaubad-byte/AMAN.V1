package com.aman.customer.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aman.customer.data.CustomerScreen
import com.aman.customer.data.LoadPhase

@Composable
fun AmanCustomerApp(context: Context) {
    AppContextHolder.context = context.applicationContext
    val vm: CustomerViewModel = viewModel(factory = CustomerViewModel.factory(context))
    val state by vm.state.collectAsState()
    if (!state.authenticated) {
        AuthScreen(vm)
        return
    }
    val tabs = listOf(CustomerScreen.ABOUT, CustomerScreen.SEARCH, CustomerScreen.HOME, CustomerScreen.REPORTS, CustomerScreen.ACCOUNT)
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            tabs.forEach { destination ->
                NavigationBarItem(selected = state.screen == destination, onClick = { vm.navigate(destination) },
                    icon = { Text(when (destination) { CustomerScreen.ABOUT -> "أ"; CustomerScreen.SEARCH -> "⌕"; CustomerScreen.HOME -> "⌂"; CustomerScreen.REPORTS -> "▤"; else -> "◉" }) },
                    label = { Text(destination.title, style = MaterialTheme.typography.labelSmall) })
            }
        }
    }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            BrandHeader(state.screen.title)
            if (state.stale) NoticeBanner("تعرض بيانات قديمة أو جزئية · آخر تحديث ${state.data?.loadedAt?.takeIf { it > 0L }?.let { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) } ?: "غير معروف"}", warning = true)
            if (state.phase == LoadPhase.LOADING && state.data == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                if (state.error != null) NoticeBanner(state.error!!, warning = true)
                if (state.mutationMessage != null) NoticeBanner(state.mutationMessage!!, warning = !state.mutationMessage!!.startsWith("تم"))
                CustomerScreenContent(state, vm, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun BrandHeader(title: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("AMAN  |  أمان", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Text("◆", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun AuthScreen(vm: CustomerViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 25.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(30.dp))
        Box(Modifier.background(MaterialTheme.colorScheme.surface, CircleShape).padding(24.dp)) {
            Text("أمان", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(14.dp))
        Text("أمان حماية وضمان", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(if (state.signUpMode) "إنشاء حساب عميل" else "تسجيل الدخول", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        if (state.signUpMode) {
            OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("اسم العميل") }, singleLine = true)
            Spacer(Modifier.height(10.dp))
        }
        OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("البريد الإلكتروني") }, singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("كلمة المرور") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(14.dp))
        if (!vm.isConfigured()) NoticeBanner(vm.configurationMessage() ?: "إعداد Supabase غير متاح.", true)
        state.authError?.let { NoticeBanner(it, true) }
        state.authNotice?.let { NoticeBanner(it, false) }
        Button(onClick = { vm.authenticate(email, password, name) }, enabled = !state.authBusy && email.contains("@") && password.isNotBlank() && vm.isConfigured(),
            modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
            if (state.authBusy) CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
            else Text(if (state.signUpMode) "إنشاء الحساب" else "دخول")
        }
        TextButton(onClick = { vm.setSignUpMode(!state.signUpMode) }) { Text(if (state.signUpMode) "لديك حساب؟ سجّل الدخول" else "إنشاء حساب عميل") }
        Text("تتم إدارة كلمة المرور بواسطة Supabase Auth ولا تُخزن في قاعدة AMAN.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
fun NoticeBanner(message: String, warning: Boolean) {
    val color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Text(message, Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(12.dp), color = color, style = MaterialTheme.typography.bodySmall)
}
