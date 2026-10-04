package com.aman.customer.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aman.customer.data.CustomerCache
import com.aman.customer.data.CustomerBackendException
import com.aman.customer.data.CustomerActionLog
import com.aman.customer.data.CustomerRepository
import com.aman.customer.data.CustomerScreen
import com.aman.customer.data.CustomerScreenData
import com.aman.customer.data.CustomerUiState
import com.aman.customer.data.LoadPhase
import com.aman.customer.data.SupabaseGateway
import com.aman.customer.data.scheduleCustomerRefresh
import com.aman.customer.data.scheduleCustomerOneTimeRefresh
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import org.json.JSONArray

class CustomerViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    val gateway = SupabaseGateway(appContext)
    private val cache = CustomerCache(appContext)
    private val repository = CustomerRepository(appContext, gateway, cache)
    private val actionLog = CustomerActionLog(appContext)
    private val _state = MutableStateFlow(CustomerUiState(authenticated = gateway.hasSession()))
    val state: StateFlow<CustomerUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private val rootTabs = setOf(CustomerScreen.ABOUT, CustomerScreen.SEARCH, CustomerScreen.HOME, CustomerScreen.REPORTS, CustomerScreen.ACCOUNT)

    init {
        AppContextHolder.context = appContext
        if (gateway.hasSession()) {
            scheduleCustomerRefresh(appContext)
            scheduleCustomerOneTimeRefresh(appContext)
            load(CustomerScreen.HOME)
        }
    }

    fun isConfigured() = gateway.isConfigured()
    fun configurationMessage() = gateway.configurationMessage()
    fun online() = repository.isOnline()

    fun setSignUpMode(enabled: Boolean) { _state.value = _state.value.copy(signUpMode = enabled, authError = null, authNotice = null) }
    fun authenticate(email: String, password: String, fullName: String = "") {
        actionLog.record(if (_state.value.signUpMode) "auth.signup" else "auth.signin", "start")
        viewModelScope.launch {
            _state.value = _state.value.copy(authBusy = true, authError = null, authNotice = null)
            try {
                if (_state.value.signUpMode) {
                    if (fullName.isBlank()) throw IllegalArgumentException("أدخل الاسم.")
                    val result = withContext(Dispatchers.IO) { gateway.signUp(email, password, fullName) }
                    if (gateway.hasSession()) {
                        withContext(Dispatchers.IO) {
                            val username = email.substringBefore('@').trim().ifBlank { "customer_${gateway.currentUserId()?.take(8).orEmpty()}" }
                            gateway.rpc("create_profile_if_missing", org.json.JSONObject()
                                .put("p_full_name", fullName.trim())
                                .put("p_username", username)
                                .put("p_email", email.trim()))
                        }
                        actionLog.record("auth", "success")
                        _state.value = _state.value.copy(authenticated = true, authBusy = false,
                            authNotice = "تم إنشاء الحساب وملف العميل ورصيد النقاط عبر العقد الكنسي.")
                        scheduleCustomerRefresh(appContext)
                        scheduleCustomerOneTimeRefresh(appContext)
                        load(CustomerScreen.HOME)
                    } else {
                        _state.value = _state.value.copy(authBusy = false,
                            authNotice = "تم إنشاء الحساب في Auth. تحقق من بريدك إذا طُلب ذلك، ثم سجّل الدخول.")
                    }
                    @Suppress("UNUSED_VARIABLE") val signupResponse = result
                } else {
                    withContext(Dispatchers.IO) { gateway.signIn(email, password) }
                    actionLog.record("auth", "success")
                    _state.value = _state.value.copy(authenticated = true, authBusy = false, authNotice = null)
                    scheduleCustomerRefresh(appContext)
                    scheduleCustomerOneTimeRefresh(appContext)
                    load(CustomerScreen.HOME)
                }
            } catch (e: Exception) {
                actionLog.record("auth", "error", e.message)
                _state.value = _state.value.copy(authBusy = false, authError = authMessage(e.message))
            }
        }
    }

    fun navigate(screen: CustomerScreen) {
        if (screen in rootTabs) return selectTab(screen)
        actionLog.record("navigate.${screen.id}", "start")
        loadJob?.cancel()
        val current = _state.value
        val stack = if (current.screen == screen) current.navigationBackStack else current.navigationBackStack + current.screen
        _state.value = current.copy(screen = screen, navigationBackStack = stack, mutationMessage = null, error = null)
        if (screen == CustomerScreen.ABOUT) {
            _state.value = _state.value.copy(phase = LoadPhase.LOADED, data = CustomerScreenData(screen), stale = false)
        } else load(screen)
    }

    fun selectTab(screen: CustomerScreen) {
        if (screen !in rootTabs) return navigate(screen)
        loadJob?.cancel()
        _state.value = _state.value.copy(screen = screen, navigationBackStack = emptyList(), mutationMessage = null, error = null)
        if (screen == CustomerScreen.ABOUT) _state.value = _state.value.copy(phase = LoadPhase.LOADED, data = CustomerScreenData(screen), stale = false)
        else load(screen)
    }

    fun back() {
        val current = _state.value
        val previous = current.navigationBackStack.lastOrNull() ?: return
        _state.value = current.copy(screen = previous, navigationBackStack = current.navigationBackStack.dropLast(1), mutationMessage = null, error = null)
        if (previous == CustomerScreen.ABOUT) _state.value = _state.value.copy(phase = LoadPhase.LOADED, data = CustomerScreenData(previous), stale = false)
        else load(previous)
    }

    fun search(query: String) {
        actionLog.record("search", "input", "length=${query.length}")
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            if (query.trim().length < 2) {
                _state.value = _state.value.copy(data = CustomerScreenData(CustomerScreen.SEARCH), phase = LoadPhase.EMPTY, stale = false, error = null)
            } else load(CustomerScreen.SEARCH, query)
        }
    }

    fun load(screen: CustomerScreen = _state.value.screen, search: String = "") {
        if (!gateway.hasSession()) return
        loadJob?.cancel()
        val cached = if (search.isBlank()) repository.cached(screen) else null
        val selectedSupportThread = _state.value.data?.takeIf { screen == CustomerScreen.SUPPORT && it.screen == screen }?.selectedThreadId
        if (!repository.isOnline()) {
            _state.value = _state.value.copy(screen = screen, phase = LoadPhase.OFFLINE, data = cached,
                stale = cached != null, error = if (cached == null) "لا يوجد اتصال بالإنترنت ولا توجد نسخة محلية لهذه الشاشة." else "أنت غير متصل؛ هذه آخر نسخة محفوظة وقد تكون قديمة.")
            return
        }
        _state.value = _state.value.copy(screen = screen, phase = LoadPhase.LOADING, data = cached, stale = cached != null, error = null)
        loadJob = viewModelScope.launch {
            try {
                val data = repository.load(screen, search)
                if (_state.value.screen != screen) return@launch
                val displayData = if (selectedSupportThread.isNullOrBlank()) data else data.copy(selectedThreadId = selectedSupportThread)
                _state.value = _state.value.copy(data = displayData,
                    phase = when {
                        data.errorNotes.isNotEmpty() && !repository.isOnline() -> LoadPhase.OFFLINE
                        data.errorNotes.isNotEmpty() -> LoadPhase.ERROR
                        data.records.isEmpty() && data.related.values.all { it.length() == 0 } -> LoadPhase.EMPTY
                        else -> LoadPhase.LOADED
                    },
                    stale = data.errorNotes.isNotEmpty(), error = data.errorNotes.takeIf { it.isNotEmpty() }?.joinToString("\n"))
                if (!selectedSupportThread.isNullOrBlank() && screen == CustomerScreen.SUPPORT) openSupportThread(selectedSupportThread)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (_state.value.screen != screen) return@launch
                if (isSessionExpired(e)) { expireSession(); return@launch }
                val fallback = cached ?: repository.cached(screen)
                _state.value = _state.value.copy(data = fallback, phase = if (!repository.isOnline() || fallback != null) LoadPhase.OFFLINE else LoadPhase.ERROR,
                    stale = fallback != null, error = e.message ?: "تعذر تحميل البيانات.")
            }
        }
    }

    fun submitPurchase(packageId: String, methodId: String, reference: String) {
        actionLog.record("purchase.submit", "start")
        viewModelScope.launch {
            _state.value = _state.value.copy(mutationBusy = true, mutationMessage = null)
            var idempotencyKey = ""
            try {
                idempotencyKey = withContext(Dispatchers.IO) { repository.purchaseIdempotencyKey(packageId, methodId, reference) }
                val userId = gateway.currentUserId()
                val existing = if (userId == null) null else withContext(Dispatchers.IO) {
                    repository.queuedPurchases(userId).firstOrNull { it.optString("package_id") == packageId &&
                        it.optString("payment_method_id") == methodId && it.optString("payment_reference").trim() == reference.trim() }
                }
                if (existing != null) {
                    if (existing.optString("status") == "queued") scheduleCustomerOneTimeRefresh(appContext)
                    _state.value = _state.value.copy(mutationBusy = false, mutationMessage = if (existing.optString("status") == "queued")
                        "هذا المرجع محفوظ محليًا بالفعل ولم يصل بعد إلى الخادم؛ لن ننشئ طلبًا مكررًا." else
                        "المرجع محفوظ محليًا لكنه يحتاج مراجعة؛ لن نعيد إرساله أو نكرر الطلب.")
                    load(_state.value.screen)
                    return@launch
                }
                if (!repository.isOnline()) {
                    repository.queuePurchase(packageId, methodId, reference, idempotencyKey)
                    _state.value = _state.value.copy(mutationBusy = false,
                        mutationMessage = "حُفظ طلب شراء النقاط محليًا بشكل مشفر؛ لم يصل بعد إلى الخادم ولم تُضف نقاط. سيرسل عند توفر الاتصال.")
                    scheduleCustomerOneTimeRefresh(appContext)
                    load(_state.value.screen)
                    return@launch
                }
                repository.submitPurchase(packageId, methodId, reference, idempotencyKey)
                actionLog.record("purchase.submit", "success")
                _state.value = _state.value.copy(mutationBusy = false, mutationMessage = "وصل طلب الشراء إلى الخادم للمراجعة؛ لم تُضف النقاط بعد.")
                load(_state.value.screen)
            } catch (e: CustomerBackendException) {
                if (isSessionExpired(e)) { expireSession(); return@launch }
                actionLog.record("purchase.submit", "error", e.message)
                _state.value = _state.value.copy(mutationBusy = false, mutationMessage = mutationMessage(e.message))
            } catch (e: IOException) {
                actionLog.record("purchase.submit", "offline", e.message)
                repository.queuePurchase(packageId, methodId, reference, idempotencyKey)
                _state.value = _state.value.copy(mutationBusy = false,
                    mutationMessage = "انقطع الاتصال قبل تأكيد نتيجة الخادم؛ حُفظ الطلب محليًا بنفس مفتاح التكرار ولم تُضف نقاط. تحقق من سجل الطلبات بعد المزامنة.")
                scheduleCustomerOneTimeRefresh(appContext)
                load(_state.value.screen)
            } catch (e: Exception) {
                if (isSessionExpired(e)) { expireSession(); return@launch }
                actionLog.record("purchase.submit", "error", e.message)
                _state.value = _state.value.copy(mutationBusy = false, mutationMessage = mutationMessage(e.message))
            }
        }
    }
    fun activate(activatedNumberId: String, days: Int) = mutate("activate_protection", "أكد الخادم تفعيل الحماية وتسجيل العملية.") { repository.activate(activatedNumberId, days) }
    fun extend(protectionId: String, days: Int) = mutate("extend_protection", "أكد الخادم تمديد الحماية وتسجيل العملية.") { repository.extend(protectionId, days) }
    fun renew(activatedNumberId: String, days: Int) = mutate("renew_protection", "تم تجديد الحماية بعد تأكيد الخادم.") { repository.renew(activatedNumberId, days) }
    fun cancelPurchase(requestId: String) = mutate("cancel_points_purchase", "تم إلغاء طلب الشراء بعد تأكيد الخادم.") { repository.cancelPurchase(requestId) }
    fun resubmitPurchase(requestId: String, packageId: String, methodId: String, reference: String) = mutate("resubmit_points_purchase", "أعيد إرسال الطلب للمراجعة مع الاحتفاظ برقم الطلب نفسه.") { repository.resubmitPurchase(requestId, packageId, methodId, reference) }
    fun markRead(notificationId: String) = mutate("mark_notification_read", "تم تحديث حالة قراءة الإشعار.") { repository.markRead(notificationId) }
    fun addCustomerNumber(phoneE164: String) = mutate("add_customer_number", "تمت إضافة الرقم إلى حسابك.") { repository.addCustomerNumber(phoneE164) }
    fun updateCustomerNumber(customerNumberId: String, phoneE164: String) = mutate("update_customer_number", "تم تحديث الرقم بعد تأكيد الخادم.") { repository.updateCustomerNumber(customerNumberId, phoneE164) }
    fun archiveCustomerNumber(customerNumberId: String) = mutate("archive_customer_number", "تمت أرشفة الرقم بعد تأكيد الخادم.") { repository.archiveCustomerNumber(customerNumberId) }
    fun updateProfile(fullName: String, username: String, phone: String) = mutate("update_customer_profile", "تم حفظ بيانات الملف الشخصي.") { repository.updateCustomerProfile(fullName, username, phone) }
    fun updatePassword(password: String) = mutate("auth.update_password", "تم تحديث كلمة المرور عبر Supabase Auth.") { gateway.updatePassword(password) }
    fun createSupportThread(subject: String, body: String) = mutate("create_support_thread", "تم إرسال الرسالة وإنشاء محادثة الدعم.") { repository.createSupportThread(subject, body) }
    fun sendSupportMessage(threadId: String, body: String) = mutate("send_support_message", "تم إرسال الرسالة إلى المحادثة.") { repository.sendSupportMessage(threadId, body) }

    fun openSupportThread(threadId: String) {
        actionLog.record("support.open_thread", "start")
        val current = _state.value.data ?: return
        if (_state.value.screen != CustomerScreen.SUPPORT || threadId.isBlank()) return
        _state.value = _state.value.copy(data = current.copy(selectedThreadId = threadId, messagesLoading = true), error = null)
        if (!repository.isOnline()) {
            _state.value = _state.value.copy(data = _state.value.data?.copy(messagesLoading = false))
            return
        }
        viewModelScope.launch {
            try {
                val messages = withContext(Dispatchers.IO) { repository.loadSupportMessages(threadId) }
                if (_state.value.screen == CustomerScreen.SUPPORT && _state.value.data?.selectedThreadId == threadId) {
                    val data = _state.value.data ?: return@launch
                    _state.value = _state.value.copy(data = data.copy(related = data.related + ("messages" to messages), messagesLoading = false))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (isSessionExpired(e)) { expireSession(); return@launch }
                if (_state.value.screen == CustomerScreen.SUPPORT && _state.value.data?.selectedThreadId == threadId) {
                    _state.value = _state.value.copy(data = _state.value.data?.copy(messagesLoading = false), error = "تعذر تحميل رسائل المحادثة. تحقق من الاتصال ثم أعد المحاولة.")
                }
            }
        }
    }

    fun clearSupportSelection() {
        val current = _state.value.data ?: return
        if (_state.value.screen == CustomerScreen.SUPPORT) _state.value = _state.value.copy(data = current.copy(selectedThreadId = null))
    }

    private fun mutate(actionName: String, success: String, action: suspend () -> Unit) {
        actionLog.record(actionName, "start")
        viewModelScope.launch {
            if (!repository.isOnline()) {
                actionLog.record(actionName, "offline")
                _state.value = _state.value.copy(mutationMessage = "لا يمكن تنفيذ عملية حساسة دون اتصال. لم تُسجل العملية محليًا.")
                return@launch
            }
            _state.value = _state.value.copy(mutationBusy = true, mutationMessage = null)
            try {
                action()
                actionLog.record(actionName, "success")
                _state.value = _state.value.copy(mutationBusy = false, mutationMessage = success)
                load(_state.value.screen)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (isSessionExpired(e)) { expireSession(); return@launch }
                actionLog.record(actionName, "error", e.message)
                _state.value = _state.value.copy(mutationBusy = false, mutationMessage = mutationMessage(e.message))
            }
        }
    }

    fun signOut() {
        actionLog.record("auth.signout", "start")
        viewModelScope.launch {
            val user = gateway.currentUserId()
            runCatching { gateway.signOut() }
            if (user != null) withContext(Dispatchers.IO) { cache.clearUser(user) }
            actionLog.record("auth.signout", "success")
            _state.value = CustomerUiState()
        }
    }

    private fun isSessionExpired(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return message.contains("انتهت جلسة الدخول", true) || message.contains("JWT expired", true) ||
            message.contains("invalid JWT", true) || message.contains("PGRST301", true)
    }

    private suspend fun expireSession() {
        runCatching { gateway.clearSession() }
        actionLog.record("auth.session", "expired")
        _state.value = CustomerUiState(authenticated = false, authError = "انتهت جلسة الدخول. سجّل الدخول مجددًا؛ لم يتم تأكيد العملية.")
    }

    private fun authMessage(message: String?): String = when {
        message.isNullOrBlank() -> "تعذر إتمام المصادقة. تحقق من البيانات والاتصال."
        message.contains("invalid login", true) || message.contains("credentials", true) -> "تعذر تسجيل الدخول. تحقق من البريد الإلكتروني وكلمة المرور."
        message.contains("already registered", true) || message.contains("already exists", true) -> "يوجد حساب مسجل بهذا البريد الإلكتروني."
        else -> "تعذر إتمام المصادقة أو الاتصال بالخدمة. تحقق من البيانات والإعدادات ثم حاول مجددًا."
    }

    private fun mutationMessage(message: String?): String = when {
        message.isNullOrBlank() -> "تعذر تنفيذ العملية. لم يتم تأكيد نجاحها؛ حاول مجددًا."
        message.contains("insufficient_points", true) -> "رصيد النقاط غير كافٍ لإتمام العملية. لم يتم الخصم."
        message.contains("phone_already_protected", true) || message.contains("activation_already_used_use_renewal", true) -> "سبق تفعيل الرقم؛ استخدم مسار التمديد أو التجديد المناسب."
        message.contains("active_subscriber_required", true) -> "يلزم وجود اشتراك عميل نشط لإتمام التفعيل."
        message.contains("protection_not_owned_or_inactive", true) -> "تعذر التحقق من ملكية الحماية أو حالتها؛ لم يتم التمديد."
        message.contains("tariff_not_found", true) -> "تعرفة الحماية غير متاحة؛ لم يتم تنفيذ العملية."
        message.contains("phone_not_found", true) -> "الرقم غير متاح؛ لم يتم تنفيذ العملية."
        message.contains("package_unavailable", true) -> "الباقة لم تعد متاحة. حدّث القائمة واختر باقة أخرى."
        message.contains("payment_method_unavailable", true) -> "وسيلة الدفع لم تعد متاحة. حدّث القائمة واختر وسيلة أخرى."
        message.contains("invalid_phone", true) -> "صيغة الرقم غير صالحة. استخدم رقمًا دوليًا يبدأ بـ +."
        message.contains("unknown_phone_prefix", true) -> "لم يتعرف الخادم على بادئة الرقم النشطة؛ لم يُحفظ الرقم."
        message.contains("protected_number_cannot_change", true) || message.contains("protected_number_cannot_archive", true) -> "لا يمكن تغيير هوية رقم عليه حماية نشطة أو أرشفته."
        message.contains("customer_number_not_found", true) -> "تعذر العثور على علاقة الرقم في حسابك. حدّث القائمة."
        message.contains("activated_number_not_found", true) || message.contains("number_not_owned_or_inactive", true) -> "الرقم غير متاح أو لم يعد مرتبطًا بحسابك. حدّث القائمة."
        message.contains("activated_number_identity_immutable", true) -> "لا يمكن تغيير رقم له سجل حماية سابق حفاظًا على سجل التفعيل."
        message.contains("duplicate_customer_number", true) -> "هذا الرقم مرتبط بالفعل بحسابك."
        message.contains("protection_expired_use_renewal", true) || message.contains("renewal_requires_expired_protection", true) -> "حالة الحماية تغيرت؛ حدّث البيانات واستخدم المسار المناسب."
        message.contains("support_thread_not_owned_or_closed", true) || message.contains("support_thread_not_found_or_closed", true) -> "المحادثة غير متاحة أو مغلقة؛ حدّث سجل التواصل."
        message.contains("support_subject_required", true) || message.contains("support_body_required", true) || message.contains("support_subject_and_body_required", true) -> "أدخل موضوع المحادثة والرسالة المطلوبة."
        message.contains("invalid_duration", true) || message.contains("invalid_extension_days", true) -> "المدة المدخلة غير صالحة."
        else -> "تعذر تأكيد نتيجة العملية من الخادم. حدّث السجل قبل إعادة المحاولة لتجنب تكرارها."
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CustomerViewModel(context.applicationContext) as T
        }
    }
}

object AppContextHolder { lateinit var context: Context }
