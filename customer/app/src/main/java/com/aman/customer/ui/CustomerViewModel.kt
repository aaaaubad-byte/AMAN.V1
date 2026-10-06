package com.aman.customer.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aman.customer.data.CustomerActionLog
import com.aman.customer.data.CustomerBackendException
import com.aman.customer.data.CustomerCache
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
import org.json.JSONObject

class CustomerViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    val gateway = SupabaseGateway(appContext)
    private val cache = CustomerCache(appContext)
    private val repository = CustomerRepository(appContext, gateway, cache)
    private val actionLog = CustomerActionLog(appContext)
    private val _state = MutableStateFlow(CustomerUiState())
    val state: StateFlow<CustomerUiState> = _state.asStateFlow()
    var selectedCustomerNumberId: String? = null
        private set
    var selectedProtectionPeriodId: String? = null
        private set
    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private val rootTabs = setOf(CustomerScreen.SUPPORT, CustomerScreen.SEARCH, CustomerScreen.HOME, CustomerScreen.REPORTS, CustomerScreen.ACCOUNT)

    init {
        AppContextHolder.context = appContext
        viewModelScope.launch {
            _state.value = _state.value.copy(screen = CustomerScreen.INITIALIZATION, phase = LoadPhase.LOADING)
            delay(250)
            if (gateway.hasSession()) {
                _state.value = _state.value.copy(authenticated = true, screen = CustomerScreen.HOME, phase = LoadPhase.INITIAL)
                scheduleCustomerRefresh(appContext)
                scheduleCustomerOneTimeRefresh(appContext)
                load(CustomerScreen.HOME)
            } else {
                _state.value = _state.value.copy(authenticated = false, screen = CustomerScreen.LOGIN, phase = LoadPhase.LOADED)
            }
        }
    }

    fun isConfigured() = gateway.isConfigured()
    fun configurationMessage() = gateway.configurationMessage()
    fun online() = repository.isOnline()

    fun signIn(email: String, password: String) {
        actionLog.record("auth.signin", "start")
        viewModelScope.launch {
            _state.value = _state.value.copy(authBusy = true, authError = null, authNotice = null)
            try {
                withContext(Dispatchers.IO) { gateway.signIn(email, password) }
                actionLog.record("auth.signin", "success")
                _state.value = _state.value.copy(authenticated = true, authBusy = false, screen = CustomerScreen.HOME, navigationBackStack = emptyList())
                scheduleCustomerRefresh(appContext)
                scheduleCustomerOneTimeRefresh(appContext)
                load(CustomerScreen.HOME)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                actionLog.record("auth.signin", "error", e.message)
                _state.value = _state.value.copy(authBusy = false, screen = CustomerScreen.LOGIN, authError = authMessage(e.message))
            }
        }
    }

    fun signUp(name: String, email: String, password: String, confirmation: String, consentTerms: Boolean, consentPrivacy: Boolean) {
        actionLog.record("auth.signup", "start")
        val validation = when {
            name.isBlank() -> "أدخل الاسم."
            !email.contains("@") -> "أدخل بريدًا إلكترونيًا صحيحًا."
            password.length < 8 -> "كلمة المرور يجب ألا تقل عن 8 أحرف."
            password != confirmation -> "كلمتا المرور غير متطابقتين."
            !consentTerms || !consentPrivacy -> "يلزم الموافقة على الشروط والخصوصية للمتابعة."
            else -> null
        }
        if (validation != null) {
            _state.value = _state.value.copy(authError = validation)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(authBusy = true, authError = null, authNotice = null)
            try {
                withContext(Dispatchers.IO) { gateway.signUp(email, password, name) }
                if (gateway.hasSession()) {
                    withContext(Dispatchers.IO) {
                        gateway.rpc("create_customer_profile", JSONObject().put("p_name", name.trim()).put("p_email", email.trim()))
                    }
                    actionLog.record("auth.signup", "success")
                    _state.value = _state.value.copy(authBusy = false, authenticated = true, screen = CustomerScreen.HOME,
                        authNotice = "تم إنشاء حساب USER. لم يُنشأ اشتراك أو أُضفت نقاط تلقائيًا.")
                    load(CustomerScreen.HOME)
                } else {
                    actionLog.record("auth.signup", "await_email_confirmation")
                    _state.value = _state.value.copy(authBusy = false, authenticated = false, screen = CustomerScreen.LOGIN,
                        authNotice = "تم إرسال طلب إنشاء الحساب. تحقق من بريدك إذا طلبت الخدمة ذلك، ثم سجّل الدخول.")
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                actionLog.record("auth.signup", "error", e.message)
                _state.value = _state.value.copy(authBusy = false, screen = CustomerScreen.SIGN_UP, authError = authMessage(e.message))
            }
        }
    }

    fun requestRecovery(name: String, email: String, userId: String) {
        val validation = when {
            name.isBlank() -> "أدخل الاسم."
            !email.contains("@") -> "أدخل البريد الإلكتروني."
            userId.isBlank() -> "أدخل معرّف العميل كما يظهر في الحساب."
            else -> null
        }
        if (validation != null) {
            _state.value = _state.value.copy(authError = validation, authNotice = null)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(authBusy = true, authError = null, authNotice = null)
            try {
                val verified = withContext(Dispatchers.IO) { gateway.verifyRecoveryIdentity(userId, email, name) }
                if (verified) withContext(Dispatchers.IO) { gateway.sendPasswordRecovery(email) }
                _state.value = _state.value.copy(authBusy = false,
                    authNotice = "إذا كانت البيانات مرتبطة بحساب، فستصلك تعليمات الاستعادة إلى البريد المسجل. افتح الرابط من التطبيق لإكمال التغيير.")
            } catch (_: Exception) {
                // Keep the response generic to prevent account enumeration.
                _state.value = _state.value.copy(authBusy = false,
                    authNotice = "إذا كانت البيانات مرتبطة بحساب، فستصلك تعليمات الاستعادة إلى البريد المسجل.")
            }
        }
    }

    fun acceptRecoveryCallback(link: String?) {
        if (link.isNullOrBlank()) return
        viewModelScope.launch {
            try {
                val accepted = withContext(Dispatchers.IO) { gateway.acceptRecoveryCallback(link) }
                if (accepted) _state.value = _state.value.copy(authenticated = true, passwordChangeRequired = true,
                    screen = CustomerScreen.RECOVERY, authBusy = false, authError = null,
                    authNotice = "تحقق رابط الاسترداد. يلزم تعيين كلمة مرور جديدة للمتابعة.")
            } catch (_: Exception) {
                _state.value = _state.value.copy(authError = "تعذر التحقق من رابط الاسترداد. اطلب رابطًا جديدًا.")
            }
        }
    }

    fun navigate(screen: CustomerScreen) {
        if (!isAllowedDestination(screen)) return
        if (screen in rootTabs) { selectTab(screen); return }
        val current = _state.value
        if (current.screen == screen) return
        loadJob?.cancel()
        _state.value = current.copy(screen = screen, navigationBackStack = current.navigationBackStack + current.screen,
            mutationMessage = null, error = null, authError = null, authNotice = null)
        load(screen)
    }

    fun selectTab(screen: CustomerScreen) {
        if (screen !in rootTabs || !_state.value.authenticated) return
        loadJob?.cancel()
        _state.value = _state.value.copy(screen = screen, navigationBackStack = emptyList(), mutationMessage = null, error = null)
        load(screen)
    }

    fun back() {
        val current = _state.value
        val previous = current.navigationBackStack.lastOrNull() ?: return
        _state.value = current.copy(screen = previous, navigationBackStack = current.navigationBackStack.dropLast(1), mutationMessage = null, error = null)
        load(previous)
    }

    fun editCustomerNumber(id: String) {
        selectedCustomerNumberId = id
        navigate(CustomerScreen.ADD_NUMBER)
    }

    fun beginActivation(id: String) {
        selectedCustomerNumberId = id
        navigate(CustomerScreen.ACTIVATE)
    }

    fun beginExtension(id: String) {
        selectedProtectionPeriodId = id
        navigate(CustomerScreen.EXTEND)
    }

    fun beginRenewal(id: String) {
        selectedProtectionPeriodId = id
        navigate(CustomerScreen.RENEW)
    }

    fun load(screen: CustomerScreen = _state.value.screen, search: String = "", page: Int = 0) {
        if ((!gateway.hasSession() && screen != CustomerScreen.ABOUT) || screen in setOf(CustomerScreen.INITIALIZATION, CustomerScreen.LOGIN, CustomerScreen.SIGN_UP, CustomerScreen.RECOVERY)) return
        loadJob?.cancel()
        val cached = if (search.isBlank()) repository.cached(screen) else null
        if (!repository.isOnline()) {
            _state.value = _state.value.copy(screen = screen, phase = LoadPhase.OFFLINE, data = cached,
                stale = cached != null, error = if (cached == null) "لا يوجد اتصال ولا نسخة محلية لهذه الشاشة." else "أنت غير متصل؛ المعروض آخر نسخة محفوظة وقد تكون قديمة.")
            return
        }
        _state.value = _state.value.copy(screen = screen, phase = LoadPhase.LOADING, data = cached, stale = cached != null, error = null)
        loadJob = viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) { repository.load(screen, search, 50, page.coerceAtLeast(0) * 50) }
                if (_state.value.screen != screen) return@launch
                val profile = data.related["profile"]?.optJSONObject(0)
                val passwordChangeRequired = profile?.optBoolean("password_change_required", false) ?: false
                _state.value = _state.value.copy(data = data,
                    phase = if (data.records.isEmpty() && data.related.values.all { it.length() == 0 }) LoadPhase.EMPTY else LoadPhase.LOADED,
                    stale = false, error = null, passwordChangeRequired = passwordChangeRequired,
                    pageIndex = page.coerceAtLeast(0), pageHasMore = data.hasMore)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (_state.value.screen != screen) return@launch
                if (isSessionExpired(e)) { expireSession(); return@launch }
                val fallback = cached ?: repository.cached(screen)
                _state.value = _state.value.copy(data = fallback,
                    phase = if (!repository.isOnline() || fallback != null) LoadPhase.OFFLINE else LoadPhase.ERROR,
                    stale = fallback != null, error = e.message ?: "تعذر تحميل بيانات الشاشة من الخادم.")
            }
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            if (query.trim().length < 2) {
                _state.value = _state.value.copy(data = CustomerScreenData(CustomerScreen.SEARCH), phase = LoadPhase.EMPTY, stale = false, error = null)
            } else load(CustomerScreen.SEARCH, query)
        }
    }

    fun submitPurchase(packageId: String, methodId: String, reference: String) = mutate("purchase.submit", "وصل طلب الشراء للخادم بحالة قيد المراجعة؛ لم تُضف النقاط بعد.") {
        val key = withContext(Dispatchers.IO) { repository.purchaseIdempotencyKey(packageId, methodId, reference) }
        withContext(Dispatchers.IO) { repository.submitPurchase(packageId, methodId, reference, key) }
    }
    fun addCustomerNumber(phone: String) = mutate("number.add", "تمت إضافة الرقم بعد تأكيد الخادم.") { withContext(Dispatchers.IO) { repository.addCustomerNumber(phone) } }
    fun updateCustomerNumber(id: String, phone: String) = mutate("number.update", "تم تحديث الرقم بعد تأكيد الخادم.") { withContext(Dispatchers.IO) { repository.updateCustomerNumber(id, phone) } }
    fun deleteCustomerNumber(id: String) = mutate("number.delete", "تمت أرشفة الرقم بعد تأكيد الخادم.") { withContext(Dispatchers.IO) { repository.deleteCustomerNumber(id) } }
    fun markNotificationRead(id: String) = mutate("notification.read", "تم تحديث حالة قراءة الإشعار.") { withContext(Dispatchers.IO) { repository.markNotificationRead(id) } }
    fun markAdminMessageRead(id: String) = mutate("admin-message.read", "تم تحديث حالة قراءة الرسالة.") { withContext(Dispatchers.IO) { repository.markAdminMessageRead(id) } }
    fun createSupportConversation(subject: String, body: String) = mutate("support.create", "تم إرسال طلب الدعم؛ ستُفتح المحادثة بعد موافقة الإدارة.") { withContext(Dispatchers.IO) { repository.createSupportConversation(subject, body) } }
    fun sendSupportMessage(id: String, body: String) = mutate("support.send", "تم إرسال الرسالة إلى المحادثة.") { withContext(Dispatchers.IO) { repository.sendSupportMessage(id, body) } }
    fun closeSupportConversation(id: String) = mutate("support.close", "تم إغلاق المحادثة.") { withContext(Dispatchers.IO) { repository.closeSupportConversation(id) } }
    fun updateProfile(name: String) = mutate("profile.update", "تم حفظ الاسم.") { withContext(Dispatchers.IO) { repository.updateCustomerProfile(name) } }

    fun activate(customerNumberId: String, tariffId: String, units: Int) = mutate("protection.activate", "أكد الخادم تفعيل الحماية وتسجيل العملية.") {
        val key = withContext(Dispatchers.IO) { repository.mutationKey("activation", "$customerNumberId|$tariffId", units) }
        withContext(Dispatchers.IO) { repository.activate(customerNumberId, tariffId, units, key) }
    }
    fun extend(protectionId: String, tariffId: String, units: Int) = mutate("protection.extend", "أكد الخادم تمديد الحماية وتسجيل العملية.") {
        val key = withContext(Dispatchers.IO) { repository.mutationKey("extension", "$protectionId|$tariffId", units) }
        withContext(Dispatchers.IO) { repository.extend(protectionId, tariffId, units, key) }
    }
    fun renew(protectionId: String, tariffId: String, units: Int) = mutate("protection.renew", "أكد الخادم تجديد الحماية وتسجيل العملية.") {
        val key = withContext(Dispatchers.IO) { repository.mutationKey("renewal", "$protectionId|$tariffId", units) }
        withContext(Dispatchers.IO) { repository.renew(protectionId, tariffId, units, key) }
    }

    fun updatePassword(password: String, confirmation: String) {
        if (password.length < 8 || password != confirmation) {
            _state.value = _state.value.copy(mutationMessage = if (password.length < 8) "كلمة المرور يجب ألا تقل عن 8 أحرف." else "كلمتا المرور غير متطابقتين.")
            return
        }
        mutate("auth.update_password", "تم تحديث كلمة المرور عبر Supabase Auth.") {
            withContext(Dispatchers.IO) { gateway.updatePassword(password) }
            if (_state.value.passwordChangeRequired) {
                withContext(Dispatchers.IO) { repository.completePasswordRecovery() }
                _state.value = _state.value.copy(passwordChangeRequired = false, screen = CustomerScreen.HOME,
                    authNotice = null, authError = null)
            }
        }
    }

    private fun mutate(actionName: String, success: String, action: suspend () -> Unit) {
        if (!repository.isOnline()) {
            _state.value = _state.value.copy(mutationBusy = false, mutationMessage = "لا يوجد اتصال؛ لم تُنفذ العملية ولم تُسجل كنجاح.")
            return
        }
        actionLog.record(actionName, "start")
        viewModelScope.launch {
            _state.value = _state.value.copy(mutationBusy = true, mutationMessage = null)
            try {
                action()
                actionLog.record(actionName, "success")
                _state.value = _state.value.copy(mutationBusy = false, mutationMessage = success)
                load(_state.value.screen)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (isSessionExpired(e)) { expireSession(); return@launch }
                actionLog.record(actionName, "error", e.message)
                _state.value = _state.value.copy(mutationBusy = false, mutationMessage = mutationMessage(e.message))
            }
        }
    }

    fun signOut() {
        val user = gateway.currentUserId()
        viewModelScope.launch {
            runCatching { gateway.signOut() }
            if (user != null) withContext(Dispatchers.IO) { cache.clearUser(user) }
            actionLog.record("auth.signout", "success")
            _state.value = CustomerUiState(authenticated = false, screen = CustomerScreen.LOGIN, phase = LoadPhase.LOADED)
        }
    }

    private fun isAllowedDestination(screen: CustomerScreen) =
        _state.value.authenticated || screen in setOf(CustomerScreen.LOGIN, CustomerScreen.SIGN_UP, CustomerScreen.RECOVERY, CustomerScreen.ABOUT)

    private fun isSessionExpired(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return message.contains("انتهت جلسة الدخول", true) || message.contains("JWT expired", true) ||
            message.contains("invalid JWT", true) || message.contains("PGRST301", true) || message.contains("401", true)
    }

    private suspend fun expireSession() {
        runCatching { gateway.clearSession() }
        _state.value = CustomerUiState(authenticated = false, screen = CustomerScreen.LOGIN, phase = LoadPhase.LOADED,
            authError = "انتهت جلسة الدخول. سجّل الدخول مجددًا؛ لم يتم تأكيد العملية.")
    }

    private fun authMessage(message: String?): String = when {
        message.isNullOrBlank() -> "تعذر إتمام المصادقة. تحقق من البيانات والاتصال."
        message.contains("invalid login", true) || message.contains("credentials", true) -> "تعذر تسجيل الدخول. تحقق من البريد الإلكتروني وكلمة المرور."
        message.contains("already registered", true) || message.contains("already exists", true) -> "يوجد حساب مسجل بهذا البريد الإلكتروني."
        else -> "تعذر إتمام المصادقة أو الاتصال بالخدمة. تحقق من الإعدادات ثم حاول مجددًا."
    }

    private fun mutationMessage(message: String?): String = when {
        message.isNullOrBlank() -> "تعذر تأكيد العملية من الخادم. حدّث القائمة قبل إعادة المحاولة."
        message.contains("INSUFFICIENT_POINTS", true) -> "رصيد النقاط غير كافٍ؛ لم يتم الخصم."
        message.contains("NOT_FOUND", true) || message.contains("NOT_OWNED", true) -> "السجل غير متاح أو لا يتبع حسابك. حدّث البيانات."
        message.contains("TARIFF", true) -> "تعرفة الحماية غير متاحة أو لم تُحسم؛ لم يُنفذ الخصم."
        message.contains("INVALID_PHONE", true) || message.contains("TELECOM_COMPANY_NOT_DETECTED", true) -> "تعذر التحقق من الرقم أو بادئته النشطة؛ لم يُحفظ الرقم."
        message.contains("FORBIDDEN", true) -> "العملية غير مسموحة لهذا الحساب."
        message.contains("ALREADY", true) || message.contains("CONFLICT", true) -> "تغيرت حالة السجل؛ حدّث البيانات واستخدم المسار المناسب."
        else -> "تعذر تأكيد نتيجة العملية من الخادم. لم تُعرض كنجاح؛ حدّث السجل قبل إعادة المحاولة."
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CustomerViewModel(context.applicationContext) as T
        }
    }
}

object AppContextHolder { lateinit var context: Context }
