package com.aman.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aman.admin.data.AdminFormKind
import com.aman.admin.data.AdminMutation
import com.aman.admin.data.AdminPermissions
import com.aman.admin.data.AdminRepository
import com.aman.admin.data.AdminSection
import com.aman.admin.data.BackendResponseException
import com.aman.admin.data.ContractException
import com.aman.admin.data.LoadedRecords
import com.aman.admin.data.RelatedListKind
import com.aman.admin.data.ReportType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.time.Instant

 data class AdminUiState(
    val checkingSession: Boolean = true,
    val authenticated: Boolean = false,
    val loading: Boolean = false,
    val busy: Boolean = false,
    val offlineSnapshot: Boolean = false,
    val cachedAtMillis: Long? = null,
    val section: AdminSection = AdminSection.HOME,
    val rows: List<JSONObject> = emptyList(),
    val pageIndex: Int = 0,
    val pageHasMore: Boolean = false,
    val selectedIndex: Int = -1,
    val relatedKind: RelatedListKind? = null,
    val relatedRows: List<JSONObject> = emptyList(),
    val relatedSelectedIndex: Int = -1,
    val searchText: String = "",
    val filterStatus: String = "",
    val filterFrom: String = "",
    val filterTo: String = "",
    val filterProviderQuery: String = "",
    val email: String = "",
    val permissions: Set<String> = emptySet(),
    val adminName: String = "",
    val formKind: AdminFormKind? = null,
    val formId: String? = null,
    val formParentId: String? = null,
    val formValues: Map<String, String> = emptyMap(),
    val formOptions: List<JSONObject> = emptyList(),
    val reportTypeId: String = ReportType.all.first().id,
    val reportRows: List<JSONObject> = emptyList(),
    val targetType: String = "all",
    val targetId: String = "",
    val targetName: String = "",
    val targetQuery: String = "",
    val targetRows: List<JSONObject> = emptyList(),
    val notificationTitle: String = "",
    val notificationBody: String = "",
    val error: String? = null,
    val notice: String? = null,
)

class AdminViewModel(private val repository: AdminRepository) : ViewModel() {
    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()
    val configurationMessage: String? get() = repository.configurationMessage()
    private var searchJob: Job? = null
    private var targetSearchJob: Job? = null

    init { restoreSession() }

    private fun restoreSession() {
        viewModelScope.launch {
            if (repository.currentUserId() == null) {
                repository.clearLocalSession()
                _state.value = _state.value.copy(checkingSession = false)
                return@launch
            }
            try {
                if (!repository.verifyAdmin()) {
                    repository.clearLocalSession()
                    _state.value = _state.value.copy(checkingSession = false, error = "الحساب مصادق عليه لكنه لا يملك صلاحية إدارية فعالة.")
                    return@launch
                }
                val info = repository.accountInfo()
                acceptAccount(info)
                _state.value = _state.value.copy(checkingSession = false, authenticated = true)
                reload()
            } catch (error: IOException) {
                if (repository.canUseCachedAdminSession()) {
                    repository.cachedAccountInfo()?.let(::acceptAccount)
                    _state.value = _state.value.copy(checkingSession = false, authenticated = true, offlineSnapshot = true,
                        notice = "تعذر التحقق من الخادم؛ ستظهر فقط اللقطات المشفرة السابقة، والإجراءات متوقفة.")
                    reload()
                } else {
                    repository.clearLocalSession()
                    _state.value = _state.value.copy(checkingSession = false, error = error.message ?: "تعذر التحقق من الجلسة والصلاحية.")
                }
            } catch (error: Exception) {
                repository.clearLocalSession()
                _state.value = _state.value.copy(checkingSession = false, authenticated = false,
                    error = error.message ?: "تعذر التحقق من الجلسة والصلاحية.")
            }
        }
    }

    private fun acceptAccount(info: JSONObject) {
        val raw = info.optJSONArray("permissions")
        val codes = buildSet { if (raw != null) for (i in 0 until raw.length()) add(raw.optString(i)) }
        val profile = info.optJSONObject("profile")
        _state.value = _state.value.copy(permissions = codes,
            adminName = profile?.optString("full_name").orEmpty().ifBlank { profile?.optString("email").orEmpty() })
    }

    fun updateEmail(value: String) { _state.value = _state.value.copy(email = value, error = null) }

    fun recoverPassword() {
        val email = _state.value.email.trim()
        if (email.isBlank()) { _state.value = _state.value.copy(error = "أدخل البريد الإلكتروني أولًا."); return }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, notice = null)
            try {
                repository.recoverPassword(email)
                _state.value = _state.value.copy(busy = false, notice = "إذا كان البريد مسجلًا، فستصلك تعليمات الاستعادة.")
            } catch (error: Exception) {
                _state.value = _state.value.copy(busy = false, error = error.message ?: "تعذر بدء استعادة الحساب.")
            }
        }
    }

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
                if (!repository.verifyAdmin()) throw ContractException("نجح التحقق من الهوية، لكن الحساب لا يملك صلاحية إدارية فعالة.")
                val info = repository.accountInfo()
                acceptAccount(info)
                _state.value = _state.value.copy(authenticated = true, busy = false, offlineSnapshot = false,
                    section = AdminSection.HOME, notice = null, error = null)
                reload()
            } catch (error: Exception) {
                runCatching { repository.clearLocalSession() }
                _state.value = _state.value.copy(busy = false, authenticated = false, error = error.message ?: "تعذر تسجيل الدخول.")
            }
        }
    }

    fun open(section: AdminSection, keepSearch: Boolean = false) {
        val preservedSearch = _state.value.searchText.takeIf { keepSearch }.orEmpty()
        _state.value = _state.value.copy(section = section, searchText = preservedSearch, filterStatus = "", filterFrom = "", filterTo = "", filterProviderQuery = "",
            pageIndex = 0, pageHasMore = false, selectedIndex = -1, relatedKind = null, relatedRows = emptyList(), relatedSelectedIndex = -1, formKind = null,
            reportRows = emptyList(), error = null, notice = null)
        if (section == AdminSection.REPORTS) _state.value = _state.value.copy(reportTypeId = ReportType.all.first().id)
        reload()
    }

    fun reload() {
        val snapshot = _state.value
        val section = snapshot.section
        if (snapshot.offlineSnapshot && repository.currentUserId() == null) return
        if (snapshot.relatedKind != null) {
            val parentRow = snapshot.rows.getOrNull(snapshot.selectedIndex)
            val parentId = when (snapshot.relatedKind) {
                RelatedListKind.SUBSCRIBER_NUMBERS, RelatedListKind.SUBSCRIBER_POINTS, RelatedListKind.SUBSCRIBER_HISTORY -> parentRow?.optString("id")
                RelatedListKind.PROTECTION_TASKS, RelatedListKind.PROVIDER_PREFIXES, RelatedListKind.PROVIDER_TARIFFS, RelatedListKind.SUPPORT_MESSAGES, RelatedListKind.PLAN_TASKS -> parentRow?.optString("id")
            }.orEmpty()
            if (parentId.isBlank()) return
            viewModelScope.launch {
                _state.value = _state.value.copy(loading = true, error = null)
                try {
                    val rows = repository.loadRelated(snapshot.relatedKind, parentId)
                    _state.value = _state.value.copy(relatedRows = rows, relatedSelectedIndex = rows.indexOfFirst { it.optString("id") == snapshot.relatedRows.getOrNull(snapshot.relatedSelectedIndex)?.optString("id") }.takeIf { it >= 0 } ?: if (rows.isEmpty()) -1 else 0, loading = false)
                } catch (error: Exception) { _state.value = _state.value.copy(loading = false, error = error.message ?: "تعذر تحديث القائمة المرتبطة.") }
            }
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val result = repository.load(section, snapshot.searchText, snapshot.filterStatus, snapshot.filterFrom, snapshot.filterTo, snapshot.filterProviderQuery, snapshot.pageIndex)
                applyRows(result)
                if (result.warningMessage != null) _state.value = _state.value.copy(notice = result.warningMessage)
            } catch (error: Exception) {
                if (error is BackendResponseException && error.statusCode == 401) {
                    repository.clearLocalSession()
                    _state.value = _state.value.copy(authenticated = false, permissions = emptySet(), loading = false,
                        error = "انتهت الجلسة. أعد تسجيل الدخول.")
                } else _state.value = _state.value.copy(loading = false,
                    error = error.message ?: "تعذر تحميل بيانات المصدر.", offlineSnapshot = false)
            }
        }
    }

    private fun applyRows(result: LoadedRecords) {
        val previous = currentRow()?.optString("id")
        val index = if (result.rows.isEmpty()) -1 else result.rows.indexOfFirst { it.optString("id") == previous }.takeIf { it >= 0 } ?: 0
        _state.value = _state.value.copy(rows = result.rows, selectedIndex = index, loading = false,
            offlineSnapshot = result.offlineSnapshot, cachedAtMillis = result.cachedAtMillis,
            pageIndex = result.pageIndex, pageHasMore = result.pageHasMore,
            notice = result.warningMessage ?: _state.value.notice)
    }

    fun select(index: Int) {
        if (index in _state.value.rows.indices) _state.value = _state.value.copy(selectedIndex = index, error = null)
    }
    fun selectRelated(index: Int) {
        if (index in _state.value.relatedRows.indices) _state.value = _state.value.copy(relatedSelectedIndex = index, error = null)
    }

    fun updateSearch(value: String) {
        _state.value = _state.value.copy(searchText = value, pageIndex = 0)
        searchJob?.cancel()
        searchJob = viewModelScope.launch { delay(300); if (_state.value.section !in setOf(AdminSection.REPORTS) && _state.value.relatedKind == null) reload() }
    }
    fun setFilterStatus(value: String) { _state.value = _state.value.copy(filterStatus = value, pageIndex = 0); reload() }
    fun setFilterFrom(value: String) { _state.value = _state.value.copy(filterFrom = value) }
    fun setFilterTo(value: String) { _state.value = _state.value.copy(filterTo = value) }
    fun setFilterProvider(value: String) { _state.value = _state.value.copy(filterProviderQuery = value) }
    fun applyFilters() { _state.value = _state.value.copy(pageIndex = 0); reload() }
    fun setPage(page: Int) {
        val target = page.coerceAtLeast(0)
        if (target > _state.value.pageIndex && !_state.value.pageHasMore) return
        _state.value = _state.value.copy(pageIndex = target)
        reload()
    }
    fun hasPermission(permission: String): Boolean = permission in _state.value.permissions
    fun can(permission: String): Boolean = hasPermission(permission) && !_state.value.offlineSnapshot

    fun openRelated(kind: RelatedListKind) {
        val current = _state.value
        val parentRow = current.rows.getOrNull(current.selectedIndex)
        val parent = when (kind) {
            RelatedListKind.SUBSCRIBER_NUMBERS, RelatedListKind.SUBSCRIBER_POINTS, RelatedListKind.SUBSCRIBER_HISTORY -> parentRow?.optString("id")
            RelatedListKind.PROTECTION_TASKS, RelatedListKind.PROVIDER_PREFIXES, RelatedListKind.PROVIDER_TARIFFS, RelatedListKind.SUPPORT_MESSAGES, RelatedListKind.PLAN_TASKS -> parentRow?.optString("id")
        }.orEmpty()
        if (parent.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, relatedKind = kind, searchText = "")
            try {
                val rows = repository.loadRelated(kind, parent)
                _state.value = _state.value.copy(loading = false, relatedRows = rows, relatedSelectedIndex = if (rows.isEmpty()) -1 else 0)
            } catch (error: Exception) { _state.value = _state.value.copy(loading = false, error = error.message ?: "تعذر تحميل السجلات المرتبطة.") }
        }
    }
    fun closeRelated() { _state.value = _state.value.copy(relatedKind = null, relatedRows = emptyList(), relatedSelectedIndex = -1, searchText = "") }

    fun startForm(kind: AdminFormKind, row: JSONObject? = null) {
        if (_state.value.offlineSnapshot) { _state.value = _state.value.copy(error = "لا يمكن تعديل لقطة Offline."); return }
        val current = _state.value
        val selected = current.rows.getOrNull(current.selectedIndex)
        val parent = when (kind) {
            AdminFormKind.PREFIX, AdminFormKind.TARIFF -> row?.optString("telecom_company_id")?.takeIf(String::isNotBlank) ?: selected?.optString("id")
            AdminFormKind.TASK_SETTINGS -> row?.optString("telecom_company_id")?.takeIf(String::isNotBlank)
                ?: if (current.section == AdminSection.PROVIDERS) selected?.optString("id") else selected?.optString("telecom_company_id")
            else -> null
        }
        val values = when (kind) {
            AdminFormKind.USER -> mapOf("name" to row?.optString("name").orEmpty(), "email" to row?.optString("email").orEmpty(),
                "account_type" to row?.optString("account_type").orEmpty(),
                "account_status" to row?.optString("account_status").orEmpty().ifBlank { "ACTIVE" }, "reason" to "")
            AdminFormKind.PROVIDER -> mapOf("code" to row?.optString("code").orEmpty(), "name" to row?.optString("name").orEmpty(),
                "status" to row?.optString("status").orEmpty().ifBlank { "ACTIVE" },
                "extension_warning_days" to row?.optInt("extension_warning_days", 0)?.toString().orEmpty().ifBlank { "0" })
            AdminFormKind.PREFIX -> mapOf("telecom_company_id" to (row?.optString("telecom_company_id") ?: parent).orEmpty(), "prefix" to row?.optString("prefix").orEmpty(),
                "status" to row?.optString("status").orEmpty().ifBlank { "ACTIVE" })
            AdminFormKind.TARIFF -> mapOf("telecom_company_id" to (row?.optString("telecom_company_id")?.takeIf(String::isNotBlank) ?: parent).orEmpty(),
                "tariff_mode" to row?.optString("tariff_mode").orEmpty().ifBlank { "MONTHLY" },
                "duration_unit_days" to row?.optString("duration_unit_days").orEmpty().ifBlank { "30" },
                "points_per_unit" to row?.optString("points_per_unit").orEmpty(),
                "rate" to row?.optString("rate").orEmpty(), "currency" to row?.optString("currency").orEmpty(),
                "effective_from" to row?.optString("effective_from").orEmpty().ifBlank { Instant.now().toString() },
                "effective_to" to row?.takeIf { it.has("effective_to") && !it.isNull("effective_to") }?.optString("effective_to").orEmpty(),
                "status" to row?.optString("status").orEmpty().ifBlank { "ACTIVE" })
            AdminFormKind.PACKAGE -> mapOf("code" to row?.optString("code").orEmpty(), "name" to row?.optString("name").orEmpty(), "points" to row?.optLong("points")?.toString().orEmpty(),
                "price" to row?.optString("price").orEmpty(), "currency" to row?.optString("currency").orEmpty().ifBlank { "SAR" },
                "display_order" to row?.optInt("display_order")?.toString().orEmpty(), "is_visible" to row?.optBoolean("is_visible", true).toString(), "is_active" to row?.optBoolean("is_active", true).toString())
            AdminFormKind.PAYMENT_METHOD -> {
                mapOf("code" to row?.optString("code").orEmpty(), "name" to row?.optString("name").orEmpty(), "type" to row?.optString("type").orEmpty(),
                    "receiving_account" to row?.optString("receiving_account").orEmpty(), "transfer_instructions" to row?.optString("transfer_instructions").orEmpty(),
                    "display_order" to row?.optInt("display_order")?.toString().orEmpty(), "is_visible" to row?.optBoolean("is_visible", true).toString(), "is_active" to row?.optBoolean("is_active", true).toString())
            }
            AdminFormKind.TASK_SETTINGS -> {
                val data = row ?: current.relatedRows.getOrNull(current.relatedSelectedIndex)
                mapOf("telecom_company_id" to data?.optString("telecom_company_id").orEmpty().ifBlank { parent.orEmpty() },
                    "interval_days" to data?.optInt("interval_days")?.takeIf { data.has("interval_days") }?.toString().orEmpty().ifBlank { "30" },
                    "task_amount" to data?.optString("task_amount").orEmpty().ifBlank { "0" }, "currency" to data?.optString("currency").orEmpty().ifBlank { "SAR" },
                    "visibility_days_before" to data?.optInt("visibility_days_before")?.takeIf { data.has("visibility_days_before") }?.toString().orEmpty().ifBlank { "5" },
                    "allow_reschedule" to data?.optBoolean("allow_reschedule", true).toString(),
                    "allow_post_expiry_creation" to data?.optBoolean("allow_post_expiry_creation", false).toString(),
                    "post_expiry_grace_days" to data?.optInt("post_expiry_grace_days")?.toString().orEmpty(),
                    "create_first_task_on_activation" to data?.optBoolean("create_first_task_on_activation", true).toString(),
                    "create_first_task_on_renewal" to data?.optBoolean("create_first_task_on_renewal", false).toString(),
                    "effective_from" to data?.optString("effective_from").orEmpty().ifBlank { Instant.now().toString() })
            }
            AdminFormKind.EXPENSE -> mapOf("expense_type_id" to "", "amount" to "", "currency" to "SAR", "description" to "", "reference" to "")
        }
        val formId = row?.optString("id")?.takeIf(String::isNotBlank)
        _state.value = current.copy(formKind = kind, formId = if (kind == AdminFormKind.TASK_SETTINGS) null else formId,
            formParentId = parent, formValues = values, formOptions = emptyList(), error = null)
        if (kind == AdminFormKind.EXPENSE) viewModelScope.launch {
            try { _state.value = _state.value.copy(formOptions = repository.expenseTypes()) }
            catch (error: Exception) { _state.value = _state.value.copy(error = error.message ?: "تعذر تحميل أنواع المصروف النشطة.") }
        }
    }

    fun updateForm(field: String, value: String) {
        _state.value = _state.value.copy(formValues = _state.value.formValues + (field to value), error = null)
    }
    fun cancelForm() { _state.value = _state.value.copy(formKind = null, formId = null, formParentId = null, formValues = emptyMap(), formOptions = emptyList()) }

    fun saveForm() {
        val s = _state.value
        val kind = s.formKind ?: return
        if (s.offlineSnapshot) { _state.value = s.copy(error = "لا يمكن حفظ تغييرات دون اتصال."); return }
        val permission = when (kind) {
            AdminFormKind.USER -> if (s.formValues["account_type"] == "SUBSCRIBER") AdminPermissions.CUSTOMERS_UPDATE else AdminPermissions.USERS_UPDATE
            AdminFormKind.PROVIDER, AdminFormKind.PREFIX, AdminFormKind.TARIFF -> AdminPermissions.PROVIDERS_MANAGE
            AdminFormKind.PACKAGE -> AdminPermissions.PACKAGES_MANAGE
            AdminFormKind.PAYMENT_METHOD -> AdminPermissions.PAYMENT_METHODS_MANAGE
            AdminFormKind.TASK_SETTINGS -> AdminPermissions.TASKS_SETTINGS
            AdminFormKind.EXPENSE -> AdminPermissions.FINANCE_WRITE
        }
        if (!can(permission)) { _state.value = s.copy(error = "لا تملك صلاحية حفظ هذا السجل."); return }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            try {
                repository.saveForm(kind, s.formId, s.formParentId, s.formValues)
                _state.value = _state.value.copy(busy = false, formKind = null, notice = "حُفظ التغيير من خلال عقد Backend المدقق.")
                reload()
                s.relatedKind?.let(::openRelated)
            } catch (error: Exception) { _state.value = _state.value.copy(busy = false, error = error.message ?: "تعذر حفظ التغيير.") }
        }
    }

    fun run(action: AdminMutation, actionInput: String = "") {
        val s = _state.value
        val row = currentRow()
        if (row == null) { _state.value = s.copy(error = "حدد سجلًا أولًا."); return }
        if (s.offlineSnapshot) { _state.value = s.copy(error = "الإجراءات الحساسة غير متاحة على بيانات Offline."); return }
        val permission = when (action) {
            AdminMutation.APPROVE_PURCHASE -> AdminPermissions.PURCHASES_APPROVE
            AdminMutation.REJECT_PURCHASE -> AdminPermissions.PURCHASES_REJECT
            AdminMutation.EXECUTE_PAYMENT_TASK -> AdminPermissions.TASKS_EXECUTE
            AdminMutation.RESCHEDULE_PAYMENT_TASK -> AdminPermissions.TASKS_RESCHEDULE
            AdminMutation.CANCEL_PAYMENT_TASK -> AdminPermissions.TASKS_CANCEL
            AdminMutation.SET_SUBSCRIBER_STATUS -> if (row.optString("account_type") == "SUBSCRIBER") AdminPermissions.CUSTOMERS_UPDATE else AdminPermissions.USERS_UPDATE
            AdminMutation.SET_CUSTOMER_NUMBER_STATUS -> AdminPermissions.NUMBERS_UPDATE
            AdminMutation.APPROVE_SUPPORT_REQUEST, AdminMutation.SEND_SUPPORT_REPLY, AdminMutation.CLOSE_SUPPORT -> AdminPermissions.SUPPORT_READ
            else -> ""
        }
        if (permission.isNotEmpty() && !can(permission)) { _state.value = s.copy(error = "لا تملك صلاحية هذا الإجراء."); return }
        when (action) {
            AdminMutation.REJECT_PURCHASE, AdminMutation.EXECUTE_PAYMENT_TASK, AdminMutation.CANCEL_PAYMENT_TASK,
            AdminMutation.SEND_SUPPORT_REPLY, AdminMutation.CLOSE_SUPPORT -> if (actionInput.isBlank()) {
                _state.value = s.copy(error = "أدخل سبب الرفض أو مرجع السداد/الإلغاء المطلوب."); return
            }
            AdminMutation.RESCHEDULE_PAYMENT_TASK -> if (runCatching { Instant.parse(actionInput) }.isFailure) {
                _state.value = s.copy(error = "أدخل موعدًا جديدًا بصيغة ISO-8601."); return
            }
            else -> Unit
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, notice = null)
            try {
                repository.perform(s.section, action, row, actionInput)
                val message = when (action) {
                    AdminMutation.APPROVE_PURCHASE -> "اعتمد Backend طلب الشراء وسجل الأثر المالي والنقاط والتدقيق."
                    AdminMutation.REJECT_PURCHASE -> "رفض Backend الطلب وسجل السبب."
                    AdminMutation.EXECUTE_PAYMENT_TASK -> "سُجلت المهمة بعد السداد الخارجي ومرجع العملية."
                    AdminMutation.RESCHEDULE_PAYMENT_TASK -> "أعاد Backend جدولة المهمة وحدّث الخطة."
                    AdminMutation.CANCEL_PAYMENT_TASK -> "ألغى Backend المهمة وسجل سبب الإلغاء."
                    AdminMutation.SET_SUBSCRIBER_STATUS -> "تغيرت حالة المشترك وسجل Backend التدقيق."
                    AdminMutation.SET_CUSTOMER_NUMBER_STATUS -> "تغيرت حالة علاقة الرقم؛ لا يحرر تطبيق الإدارة رقم الهاتف."
                    AdminMutation.SEND_SUPPORT_REPLY -> "سُجل الرد من حساب الإدارة داخل المحادثة."
                    AdminMutation.CLOSE_SUPPORT -> "أغلقت المحادثة وسُجلت العملية."
                    AdminMutation.APPROVE_SUPPORT_REQUEST -> "اعتمد Backend طلب الدعم وفتح المحادثة وأشعر العميل."
                    else -> "تم الإجراء."
                }
                _state.value = _state.value.copy(busy = false, notice = message)
                if (_state.value.relatedKind == null) reload() else openRelated(_state.value.relatedKind!!)
            } catch (error: Exception) { _state.value = _state.value.copy(busy = false, error = error.message ?: "فشل الإجراء في Backend.") }
        }
    }

    fun setReportType(id: String) { _state.value = _state.value.copy(reportTypeId = id, reportRows = emptyList()) }
    fun loadReport() {
        val s = _state.value
        if (!can(AdminPermissions.REPORTS_READ)) { _state.value = s.copy(error = "لا تملك صلاحية قراءة التقارير."); return }
        val type = ReportType.all.firstOrNull { it.id == s.reportTypeId } ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val result = repository.loadReport(type, s.filterFrom, s.filterTo)
                _state.value = _state.value.copy(reportRows = result.rows, loading = false,
                    notice = "نتائج فعلية من ${type.table} ضمن الفترة المحددة (${result.rows.size} سجل).")
            } catch (error: Exception) { _state.value = _state.value.copy(loading = false, reportRows = emptyList(), error = error.message ?: "تعذر إنشاء التقرير.") }
        }
    }

    fun checkExportPermission(onAllowed: () -> Unit) {
        if (_state.value.offlineSnapshot) { _state.value = _state.value.copy(error = "التصدير يتطلب اتصالًا وصلاحية Backend حديثة."); return }
        viewModelScope.launch {
            try {
                if (repository.canExportReports()) onAllowed()
                else _state.value = _state.value.copy(error = "لا تملك صلاحية reports.export.")
            } catch (error: Exception) { _state.value = _state.value.copy(error = error.message ?: "تعذر التحقق من صلاحية التصدير.") }
        }
    }

    fun setTargetType(type: String) { _state.value = _state.value.copy(targetType = type, targetId = "", targetName = "", targetRows = emptyList()) }
    fun updateTargetQuery(value: String) {
        _state.value = _state.value.copy(targetQuery = value)
        targetSearchJob?.cancel()
        if (value.trim().length < 2 || _state.value.targetType == "all") return
        targetSearchJob = viewModelScope.launch {
            delay(250)
            try { val rows = repository.findRecipients(_state.value.targetType, value); _state.value = _state.value.copy(targetRows = rows, error = null) }
            catch (error: Exception) { _state.value = _state.value.copy(targetRows = emptyList(), error = error.message ?: "تعذر البحث عن المستهدف.") }
        }
    }
    fun selectTarget(row: JSONObject) {
        _state.value = _state.value.copy(targetId = row.optString("_recipient_id"), targetName = row.optString("_recipient_name"), targetRows = emptyList())
    }
    fun updateNotificationTitle(value: String) { _state.value = _state.value.copy(notificationTitle = value) }
    fun updateNotificationBody(value: String) { _state.value = _state.value.copy(notificationBody = value) }
    fun sendNotification() {
        val s = _state.value
        if (!can(AdminPermissions.NOTIFICATIONS_SEND)) { _state.value = s.copy(error = "لا تملك صلاحية إرسال الإشعارات."); return }
        if (s.notificationTitle.trim().isEmpty() || s.notificationBody.trim().isEmpty()) { _state.value = s.copy(error = "أدخل عنوان الإشعار ومحتواه."); return }
        if (s.targetType in setOf("subscriber", "user") && s.targetId.isBlank()) { _state.value = s.copy(error = "اختر مستهدفًا حقيقيًا من نتائج البحث."); return }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            try {
                repository.sendNotification(s.targetType, s.targetId.takeIf(String::isNotBlank), s.notificationTitle, s.notificationBody)
                _state.value = _state.value.copy(busy = false, notice = "أرسل Backend الإشعار وسجل المستلمين والتدقيق.", notificationTitle = "", notificationBody = "", targetId = "", targetName = "")
                reload()
            } catch (error: Exception) { _state.value = _state.value.copy(busy = false, error = error.message ?: "تعذر إرسال الإشعار.") }
        }
    }

    private fun currentRow(): JSONObject? {
        val s = _state.value
        return if (s.relatedKind != null) s.relatedRows.getOrNull(s.relatedSelectedIndex) else s.rows.getOrNull(s.selectedIndex)
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
