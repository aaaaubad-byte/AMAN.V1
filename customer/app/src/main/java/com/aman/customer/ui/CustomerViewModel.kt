package com.aman.customer.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aman.customer.data.CustomerCache
import com.aman.customer.data.CustomerBackendException
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
import java.util.UUID
import java.io.IOException

class CustomerViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    val gateway = SupabaseGateway(appContext)
    private val cache = CustomerCache(appContext)
    private val repository = CustomerRepository(appContext, gateway, cache)
    private val _state = MutableStateFlow(CustomerUiState(authenticated = gateway.hasSession()))
    val state: StateFlow<CustomerUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private val purchaseIntentKeys = mutableMapOf<String, String>()

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
        viewModelScope.launch {
            _state.value = _state.value.copy(authBusy = true, authError = null, authNotice = null)
            try {
                if (_state.value.signUpMode) {
                    if (fullName.isBlank()) throw IllegalArgumentException("أدخل الاسم.")
                    val result = withContext(Dispatchers.IO) { gateway.signUp(email, password, fullName) }
                    if (gateway.hasSession()) {
                        _state.value = _state.value.copy(authenticated = true, authBusy = false,
                            authNotice = "تم إنشاء جلسة المصادقة. قد يتطلب ملف العميل تهيئة خلفية منفصلة؛ راجع شاشة الحساب بعد الدخول.")
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
                    _state.value = _state.value.copy(authenticated = true, authBusy = false, authNotice = null)
                    scheduleCustomerRefresh(appContext)
                    scheduleCustomerOneTimeRefresh(appContext)
                    load(CustomerScreen.HOME)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(authBusy = false, authError = authMessage(e.message))
            }
        }
    }

    fun navigate(screen: CustomerScreen) {
        loadJob?.cancel()
        _state.value = _state.value.copy(screen = screen, mutationMessage = null, error = null)
        if (screen == CustomerScreen.ABOUT) {
            _state.value = _state.value.copy(phase = LoadPhase.LOADED, data = CustomerScreenData(screen), stale = false)
        } else load(screen)
    }

    fun search(query: String) {
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
        _state.value = _state.value.copy(screen = screen, phase = LoadPhase.LOADING, data = cached, stale = cached != null, error = null)
        loadJob = viewModelScope.launch {
            try {
                val data = repository.load(screen, search)
                if (_state.value.screen != screen) return@launch
                _state.value = _state.value.copy(data = data,
                    phase = if (data.records.isEmpty() && data.related.values.all { it.length() == 0 }) LoadPhase.EMPTY else LoadPhase.LOADED,
                    stale = data.errorNotes.isNotEmpty(), error = data.errorNotes.takeIf { it.isNotEmpty() }?.joinToString("\n"))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (_state.value.screen != screen) return@launch
                val fallback = cached ?: repository.cached(screen)
                _state.value = _state.value.copy(data = fallback, phase = if (fallback != null) LoadPhase.OFFLINE else LoadPhase.ERROR,
                    stale = fallback != null, error = e.message ?: "تعذر تحميل البيانات.")
            }
        }
    }

    fun submitPurchase(packageId: String, methodId: String, reference: String) {
        val intent = "$packageId|$methodId|${reference.trim()}"
        val key = purchaseIntentKeys.getOrPut(intent) { UUID.randomUUID().toString() }
        viewModelScope.launch {
            _state.value = _state.value.copy(mutationBusy = true, mutationMessage = null)
            try {
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
                    repository.queuePurchase(packageId, methodId, reference, key)
                    _state.value = _state.value.copy(mutationBusy = false,
                        mutationMessage = "حُفظ طلب شراء النقاط محليًا بشكل مشفر؛ لم يصل بعد إلى الخادم ولم تُضف نقاط. سيرسل عند توفر الاتصال.")
                    scheduleCustomerOneTimeRefresh(appContext)
                    load(_state.value.screen)
                    return@launch
                }
                repository.submitPurchase(packageId, methodId, reference, key)
                _state.value = _state.value.copy(mutationBusy = false, mutationMessage = "وصل طلب الشراء إلى الخادم للمراجعة؛ لم تُضف النقاط بعد.")
                load(_state.value.screen)
            } catch (e: CustomerBackendException) {
                _state.value = _state.value.copy(mutationBusy = false, mutationMessage = mutationMessage(e.message))
            } catch (e: IOException) {
                repository.queuePurchase(packageId, methodId, reference, key)
                _state.value = _state.value.copy(mutationBusy = false,
                    mutationMessage = "انقطع الاتصال قبل تأكيد نتيجة الخادم؛ حُفظ الطلب محليًا بنفس مفتاح التكرار ولم تُضف نقاط. تحقق من سجل الطلبات بعد المزامنة.")
                scheduleCustomerOneTimeRefresh(appContext)
                load(_state.value.screen)
            } catch (e: Exception) {
                _state.value = _state.value.copy(mutationBusy = false, mutationMessage = mutationMessage(e.message))
            }
        }
    }
    fun activate(phoneId: String, days: Int) = mutate("تم التحقق من طلب التفعيل وتنفيذه عبر الخادم.") { repository.activate(phoneId, days) }
    fun extend(protectionId: String, days: Int) = mutate("تم التحقق من طلب التمديد وتنفيذه عبر الخادم.") { repository.extend(protectionId, days) }
    fun markRead(notificationId: String) = mutate("تم تحديث حالة قراءة الإشعار.") { repository.markRead(notificationId) }

    private fun mutate(success: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            if (!repository.isOnline()) {
                _state.value = _state.value.copy(mutationMessage = "لا يمكن تنفيذ عملية حساسة دون اتصال. لم تُسجل العملية محليًا.")
                return@launch
            }
            _state.value = _state.value.copy(mutationBusy = true, mutationMessage = null)
            try {
                action()
                _state.value = _state.value.copy(mutationBusy = false, mutationMessage = success)
                load(_state.value.screen)
            } catch (e: Exception) {
                _state.value = _state.value.copy(mutationBusy = false, mutationMessage = mutationMessage(e.message))
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            val user = gateway.currentUserId()
            runCatching { gateway.signOut() }
            if (user != null) withContext(Dispatchers.IO) { cache.clearUser(user) }
            _state.value = CustomerUiState()
        }
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
        message.contains("phone_already_protected", true) -> "للرقم حماية نشطة بالفعل. لم يتم الخصم."
        message.contains("active_subscriber_required", true) -> "يلزم وجود اشتراك عميل نشط لإتمام التفعيل."
        message.contains("protection_not_owned_or_inactive", true) -> "تعذر التحقق من ملكية الحماية أو حالتها؛ لم يتم التمديد."
        message.contains("tariff_not_found", true) -> "تعرفة الحماية غير متاحة؛ لم يتم تنفيذ العملية."
        message.contains("phone_not_found", true) -> "الرقم غير متاح؛ لم يتم تنفيذ العملية."
        message.contains("package_unavailable", true) -> "الباقة لم تعد متاحة. حدّث القائمة واختر باقة أخرى."
        message.contains("payment_method_unavailable", true) -> "وسيلة الدفع لم تعد متاحة. حدّث القائمة واختر وسيلة أخرى."
        message.contains("invalid_duration", true) || message.contains("invalid_extension_days", true) -> "المدة المدخلة غير صالحة."
        else -> "تعذر تأكيد نتيجة العملية من الخادم. لا تعِد إرسال تفعيل أو تمديد عند انقطاع الاتصال قبل التأكد من سجل العمليات أو التواصل مع الإدارة."
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CustomerViewModel(context.applicationContext) as T
        }
    }
}

object AppContextHolder { lateinit var context: Context }
