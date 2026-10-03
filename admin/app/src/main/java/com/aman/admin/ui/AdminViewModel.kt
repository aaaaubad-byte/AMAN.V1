package com.aman.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aman.admin.data.AdminMutation
import com.aman.admin.data.AdminRepository
import com.aman.admin.data.AdminSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException

data class AdminUiState(
    val checkingSession: Boolean = true,
    val authenticated: Boolean = false,
    val loading: Boolean = false,
    val busy: Boolean = false,
    val offlineSnapshot: Boolean = false,
    val section: AdminSection = AdminSection.HOME,
    val rows: List<JSONObject> = emptyList(),
    val selectedIndex: Int = -1,
    val searchText: String = "",
    val email: String = "",
    val error: String? = null,
    val notice: String? = null,
)

class AdminViewModel(private val repository: AdminRepository) : ViewModel() {
    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()
    val configurationMessage: String? get() = repository.configurationMessage()

    init {
        viewModelScope.launch {
            if (repository.currentUserId() == null) {
                repository.clearLocalSession()
                _state.value = _state.value.copy(checkingSession = false)
                return@launch
            }
            try {
                if (repository.verifyAdmin()) {
                    _state.value = _state.value.copy(checkingSession = false, authenticated = true)
                    reload()
                } else {
                    repository.clearLocalSession()
                    _state.value = _state.value.copy(checkingSession = false, error = "الحساب مصادق عليه لكنه لا يملك دورًا إداريًا فعالًا.")
                }
            } catch (error: IOException) {
                if (repository.canUseCachedAdminSession()) {
                    _state.value = _state.value.copy(checkingSession = false, authenticated = true, offlineSnapshot = true)
                    reload()
                } else {
                    repository.clearLocalSession()
                    _state.value = _state.value.copy(checkingSession = false, error = error.message ?: "تعذر التحقق من الصلاحية دون اتصال.")
                }
            } catch (error: Exception) {
                repository.clearLocalSession()
                _state.value = _state.value.copy(checkingSession = false, error = error.message ?: "تعذر التحقق من الجلسة والصلاحية.")
            }
        }
    }

    fun updateEmail(value: String) { _state.value = _state.value.copy(email = value, error = null) }

    fun signIn(password: String) {
        val email = _state.value.email.trim()
        if (email.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(error = "أدخل البريد الإلكتروني وكلمة المرور.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            try {
                repository.signIn(email, password)
                if (!repository.verifyAdmin()) {
                    repository.signOut()
                    throw IllegalStateException("نجح التحقق من الهوية، لكن الحساب لا يملك دورًا إداريًا فعالًا.")
                }
                _state.value = _state.value.copy(authenticated = true, busy = false, offlineSnapshot = false, section = AdminSection.HOME, notice = null)
                reload()
            } catch (error: Exception) {
                runCatching { repository.clearLocalSession() }
                _state.value = _state.value.copy(busy = false, authenticated = false, error = error.message ?: "تعذر تسجيل الدخول.")
            }
        }
    }

    fun open(section: AdminSection) {
        _state.value = _state.value.copy(section = section, searchText = "", error = null, notice = null, selectedIndex = -1)
        reload()
    }

    fun reload() {
        val section = _state.value.section
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val result = repository.load(section, _state.value.searchText)
                _state.value = _state.value.copy(rows = result.rows, selectedIndex = if (result.rows.isEmpty()) -1 else 0,
                    loading = false, offlineSnapshot = result.offlineSnapshot, notice = result.warningMessage ?: _state.value.notice)
            } catch (error: Exception) {
                _state.value = _state.value.copy(rows = emptyList(), selectedIndex = -1, loading = false,
                    error = error.message ?: "تعذر تحميل بيانات المصدر.", offlineSnapshot = false)
            }
        }
    }

    fun select(index: Int) {
        if (index in _state.value.rows.indices) _state.value = _state.value.copy(selectedIndex = index, error = null)
    }
    fun updateSearch(value: String) { _state.value = _state.value.copy(searchText = value) }

    fun run(action: AdminMutation, actionInput: String? = null) {
        val current = _state.value
        val row = current.rows.getOrNull(current.selectedIndex)
        if (row == null) { _state.value = current.copy(error = "حدد سجلًا أولًا."); return }
        if (current.offlineSnapshot) { _state.value = current.copy(error = "الإجراءات الحساسة غير متاحة على بيانات مخزنة Offline."); return }
        if (action == AdminMutation.REJECT_PURCHASE && actionInput.isNullOrBlank()) {
            _state.value = current.copy(error = "أدخل سبب رفض الطلب."); return
        }
        if (action == AdminMutation.EXECUTE_PAYMENT_TASK && actionInput.isNullOrBlank()) {
            _state.value = current.copy(error = "أدخل مرجع السداد الخارجي بعد إتمامه."); return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, notice = null)
            try {
                repository.perform(current.section, action, row, actionInput)
                val message = when (action) {
                    AdminMutation.APPROVE_PURCHASE -> "اعتمد Backend طلب الشراء."
                    AdminMutation.REJECT_PURCHASE -> "رفض Backend الطلب وسجل السبب."
                    AdminMutation.EXECUTE_PAYMENT_TASK -> "سُجلت المهمة بعد السداد الخارجي ومرجع العملية."
                }
                _state.value = _state.value.copy(busy = false, notice = message)
                reload()
            } catch (error: Exception) {
                _state.value = _state.value.copy(busy = false, error = error.message ?: "فشل الإجراء في Backend.")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            runCatching { repository.signOut() }
            _state.value = AdminUiState(checkingSession = false, authenticated = false, busy = false)
        }
    }

    fun clearMessages() { _state.value = _state.value.copy(error = null, notice = null) }
}
