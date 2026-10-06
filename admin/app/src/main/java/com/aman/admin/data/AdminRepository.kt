package com.aman.admin.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.math.BigDecimal
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

class ContractException(message: String) : Exception(message)

data class LoadedRecords(
    val rows: List<JSONObject>,
    val offlineSnapshot: Boolean = false,
    val cachedAtMillis: Long? = null,
    val warningMessage: String? = null,
    val pageIndex: Int = 0,
    val pageHasMore: Boolean = false,
)

private data class SearchPage(
    val rows: List<JSONObject>,
    val warningMessage: String?,
    val hasMore: Boolean,
)

/** V11-only data access. Sensitive writes are RPC-only; never mutate PostgREST tables directly. */
class AdminRepository(private val gateway: SupabaseGateway, private val cache: LocalSnapshotCache) {
    fun configurationMessage(): String? = gateway.configurationMessage()
    fun currentUserId(): String? = gateway.currentUserId()
    fun canUseCachedAdminSession(): Boolean = gateway.canUseCachedAdminSession()
    suspend fun signIn(email: String, password: String) = gateway.signIn(email, password)
    suspend fun recoverPassword(email: String) = gateway.recoverPassword(email)
    suspend fun expenseTypes(): List<JSONObject> = selectRows("expense_type", "select=id,code,name&status=eq.ACTIVE&order=name.asc&limit=100")
    suspend fun verifyAdmin(): Boolean = gateway.isAdmin()
    suspend fun accountInfo(): JSONObject = JSONObject(gateway.rpc("admin_account_info", JSONObject())).also { info ->
        gateway.currentUserId()?.let { cache.put("$it:account", JSONArray().put(info)) }
    }
    fun cachedAccountInfo(): JSONObject? = gateway.currentUserId()?.let { cache.get("$it:account")?.optJSONObject(0) }
    suspend fun signOut() { try { gateway.signOut() } finally { cache.clear() } }
    suspend fun clearLocalSession() { gateway.clearLocalSession(); cache.clear() }

    suspend fun load(
        section: AdminSection, query: String = "", status: String = "", dateFrom: String = "",
        dateTo: String = "", providerQuery: String = "", page: Int = 0,
    ): LoadedRecords {
        val uid = gateway.currentUserId() ?: throw ContractException("انتهت جلسة الدخول. سجّل الدخول مجددًا.")
        val cacheKey = "$uid:${section.name}"
        try {
            var warning: String? = null
            var searchHasMore = false
            val rawRows = when (section) {
                AdminSection.HOME -> loadDashboard().also { warning = it.second }.first
                AdminSection.SEARCH -> searchAll(query, status, page).also {
                    warning = it.warningMessage
                    searchHasMore = it.hasMore
                }.rows
                AdminSection.ACCOUNT -> listOf(accountInfo())
                AdminSection.REPORTS -> emptyList()
                AdminSection.RECOVERY, AdminSection.LOGIN -> emptyList()
                AdminSection.ABOUT -> selectRows("app_content", "select=*&status=eq.ACTIVE&order=content_key.asc&limit=100")
                AdminSection.SETUP -> selectRows("maintenance_config", "select=*&limit=20")
                else -> loadSectionRows(section, query, status, dateFrom, dateTo, providerQuery, page)
            }
            val pageable = section !in setOf(AdminSection.HOME, AdminSection.ACCOUNT, AdminSection.REPORTS, AdminSection.ABOUT, AdminSection.SETUP)
            val pageHasMore = if (section == AdminSection.SEARCH) searchHasMore else pageable && rawRows.size > 50
            val rows = if (pageable && section != AdminSection.SEARCH) rawRows.take(50) else rawRows
            if (section !in setOf(AdminSection.SEARCH, AdminSection.ACCOUNT, AdminSection.RECOVERY, AdminSection.LOGIN)) enrich(section, rows)
            cache.put(cacheKey, JSONArray().apply { rows.forEach(::put) })
            return LoadedRecords(rows, warningMessage = warning, pageIndex = page.coerceAtLeast(0), pageHasMore = pageHasMore)
        } catch (error: IOException) {
            val saved = cache.get(cacheKey) ?: throw error
            return LoadedRecords(filterCachedRows(saved.toObjects(), section, query, status, dateFrom, dateTo, providerQuery),
                offlineSnapshot = true, cachedAtMillis = cache.updatedAt(cacheKey))
        }
    }

    private fun filterCachedRows(rows: List<JSONObject>, section: AdminSection, query: String, status: String, from: String, to: String, provider: String): List<JSONObject> {
        val token = query.trim().lowercase()
        val dateField = when (section) {
            AdminSection.PURCHASES -> "submitted_at"
            AdminSection.PAYMENT_TASKS, AdminSection.PERIODIC_PAYMENT -> "due_at"
            AdminSection.ADDED_NUMBERS -> "added_at"
            else -> "created_at"
        }
        val minimum = parseDate(from, false)?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val maximum = parseDate(to, true)?.let { runCatching { Instant.parse(it) }.getOrNull() }
        return rows.filter { row ->
            val rowStatus = row.optString(if (section in setOf(AdminSection.SUBSCRIBERS, AdminSection.USERS)) "account_status" else "status")
            val rightType = when (section) {
                AdminSection.SUBSCRIBERS -> row.optString("account_type") == "SUBSCRIBER"
                AdminSection.USERS -> row.optString("account_type") == "USER"
                else -> true
            }
            val rightSource = section != AdminSection.SEARCH || status.isBlank() || row.optString("_aman_source_table") == status
            val textMatch = token.isBlank() || row.toString().contains(token, ignoreCase = true)
            val providerMatch = provider.isBlank() || row.optString("company_name").contains(provider, ignoreCase = true)
            val rowDate = runCatching { Instant.parse(row.optString(dateField)) }.getOrNull()
            rightType && rightSource && textMatch && providerMatch && (status.isBlank() || section == AdminSection.SEARCH || rowStatus.equals(status, true)) &&
                (minimum == null || rowDate?.let { !it.isBefore(minimum) } == true) && (maximum == null || rowDate?.let { !it.isAfter(maximum) } == true)
        }
    }

    suspend fun loadReport(type: ReportType, from: String, to: String): LoadedRecords {
        if (!gateway.hasPermission(AdminPermissions.REPORTS_READ)) throw ContractException("لا تملك صلاحية قراءة التقارير.")
        val params = mutableListOf("select=*", "order=${type.dateColumn}.desc", "limit=500")
        parseDate(from, false)?.let { params += "${type.dateColumn}=gte.${filterValue(it)}" }
        parseDate(to, true)?.let { params += "${type.dateColumn}=lte.${filterValue(it)}" }
        when (type.id) {
            "users" -> params += "account_type=eq.USER"
            "subscribers" -> params += "account_type=eq.SUBSCRIBER"
        }
        val rows = selectRows(type.table, params.joinToString("&"))
        enrichReport(type, rows)
        return LoadedRecords(rows)
    }

    suspend fun canExportReports(): Boolean = gateway.hasPermission(AdminPermissions.REPORTS_EXPORT)

    suspend fun loadRelated(kind: RelatedListKind, parentId: String): List<JSONObject> {
        val (table, column, order) = when (kind) {
            RelatedListKind.SUBSCRIBER_NUMBERS -> Triple("customer_number", "customer_id", "added_at")
            RelatedListKind.SUBSCRIBER_POINTS -> Triple("points_ledger", "customer_id", "created_at")
            RelatedListKind.SUBSCRIBER_HISTORY -> Triple("operation", "entity_id", "created_at")
            RelatedListKind.PROTECTION_TASKS -> Triple("periodic_task", "protection_period_id", "due_at")
            RelatedListKind.PROVIDER_PREFIXES -> Triple("telecom_prefix", "telecom_company_id", "created_at")
            RelatedListKind.PROVIDER_TARIFFS -> Triple("protection_tariff", "telecom_company_id", "effective_from")
            RelatedListKind.SUPPORT_MESSAGES -> Triple("support_message", "conversation_id", "sent_at")
            RelatedListKind.PLAN_TASKS -> Triple("periodic_task", "task_plan_id", "due_at")
        }
        val rows = selectRows(table, "select=*&$column=eq.${filterValue(parentId)}&order=$order.desc&limit=300")
        when (kind) {
            RelatedListKind.SUBSCRIBER_NUMBERS -> enrich(AdminSection.ADDED_NUMBERS, rows)
            RelatedListKind.PROTECTION_TASKS, RelatedListKind.PLAN_TASKS -> enrich(AdminSection.PAYMENT_TASKS, rows)
            RelatedListKind.PROVIDER_PREFIXES, RelatedListKind.PROVIDER_TARIFFS, RelatedListKind.SUPPORT_MESSAGES -> Unit
            else -> Unit
        }
        return rows
    }

    suspend fun findRecipients(type: String, query: String): List<JSONObject> {
        if (type == "all" || query.trim().length < 2) return emptyList()
        val token = filterValue("*${query.trim()}*")
        val params = mutableListOf("select=id,name,email,public_user_code,account_type,account_status", "account_status=eq.ACTIVE",
            "or=(name.ilike.$token,email.ilike.$token,public_user_code.ilike.$token)", "limit=30")
        if (type == "subscriber") params += "account_type=eq.SUBSCRIBER" else params += "account_type=eq.USER"
        return selectRows("customer_profile", params.joinToString("&")).map { row ->
            row.put("_recipient_type", type).put("_recipient_id", row.optString("id"))
                .put("_recipient_name", row.optString("name").ifBlank { row.optString("email") }.ifBlank { row.optString("public_user_code") })
        }
    }

    private suspend fun loadDashboard(): Pair<List<JSONObject>, String?> {
        val merged = mutableListOf<JSONObject>()
        val warnings = mutableListOf<String>()
        val metrics = listOf(
            Triple(AdminPermissions.CUSTOMERS_READ, "customer_profile", "العملاء" to AdminSection.SUBSCRIBERS),
            Triple(AdminPermissions.NUMBERS_READ, "customer_number", "الأرقام المضافة" to AdminSection.ADDED_NUMBERS),
            Triple(AdminPermissions.TASKS_READ, "periodic_task", "المهام المفتوحة" to AdminSection.PAYMENT_TASKS),
            Triple(AdminPermissions.PROVIDERS_READ, "telecom_company", "الشركات" to AdminSection.PROVIDERS),
            Triple(AdminPermissions.PURCHASES_READ, "points_purchase", "طلبات الشراء المعلقة" to AdminSection.PURCHASES),
            Triple(AdminPermissions.FINANCE_READ, "financial_ledger", "قيود المالية" to AdminSection.FINANCE),
        )
        for ((permission, table, metric) in metrics) {
            if (!runCatching { gateway.hasPermission(permission) }.getOrDefault(false)) continue
            try {
                val filter = when (table) { "periodic_task" -> "status=eq.OPEN"; "points_purchase" -> "status=eq.PENDING"; else -> "" }
                val count = gateway.countRows(table, filter)
                merged += JSONObject().put("_dashboard_metric", metric.first).put("_dashboard_count", count).put("_dashboard_section", metric.second.name)
            } catch (_: Exception) { warnings += "${metric.first}" }
        }
        val sources = listOf(
            Triple("points_purchase", "select=*&status=eq.PENDING&order=submitted_at.desc&limit=20", AdminPermissions.PURCHASES_READ),
            Triple("periodic_task", "select=*&status=eq.OPEN&order=due_at.asc&limit=30", AdminPermissions.TASKS_READ),
            Triple("operation", "select=*&order=created_at.desc&limit=20", AdminPermissions.OPERATIONS_READ),
        )
        for ((table, query, permission) in sources) {
            if (!runCatching { gateway.hasPermission(permission) }.getOrDefault(false)) continue
            try {
                selectRows(table, query).forEach { row ->
                    row.put("_aman_source_table", table)
                    row.put("_attention_kind", when (table) { "points_purchase" -> "purchase"; "periodic_task" -> "task"; else -> "operation" })
                    merged += row
                }
            } catch (_: Exception) { warnings += table }
        }
        if (merged.isEmpty() && warnings.isNotEmpty()) throw ContractException("تعذر تحميل لوحة الإدارة من الجداول المصرح بها.")
        return merged to warnings.takeIf { it.isNotEmpty() }?.joinToString("، ") { "مصدر غير متاح: $it" }
    }

    private suspend fun loadSectionRows(section: AdminSection, query: String, status: String, dateFrom: String, dateTo: String, providerQuery: String, page: Int): List<JSONObject> {
        if (section == AdminSection.ACCOUNT) return listOf(accountInfo())
        val table = section.table ?: return emptyList()
        val dateColumn = when (section) {
            AdminSection.PURCHASES -> "submitted_at"
            AdminSection.PAYMENT_TASKS, AdminSection.PERIODIC_PAYMENT -> "due_at"
            AdminSection.TASK_PLANS -> "anchor_at"
            AdminSection.ADDED_NUMBERS -> "added_at"
            AdminSection.ACTIVE_NUMBERS -> "end_at"
            AdminSection.TASK_SETTINGS -> "effective_from"
            else -> "created_at"
        }
        val args = mutableListOf("select=*", "order=$dateColumn.${if (section == AdminSection.PERIODIC_PAYMENT) "asc" else "desc"},id.asc", "limit=51", "offset=${page.coerceAtLeast(0) * 50}")
        val q = query.trim()
        val statusColumn = if (section in setOf(AdminSection.SUBSCRIBERS, AdminSection.USERS)) "account_status" else "status"
        if (status.isNotBlank() && section != AdminSection.SEARCH) {
            if (section in setOf(AdminSection.PACKAGES, AdminSection.PAYMENT_METHODS)) {
                when (status.uppercase()) {
                    "ACTIVE" -> args += "is_active=eq.true"
                    "INACTIVE" -> args += "is_active=eq.false"
                    "VISIBLE" -> args += "is_visible=eq.true"
                    "HIDDEN" -> args += "is_visible=eq.false"
                }
            } else args += "$statusColumn=eq.${filterValue(status.uppercase())}"
        }
        when (section) {
            AdminSection.SUBSCRIBERS -> args += "account_type=eq.SUBSCRIBER"
            AdminSection.USERS -> args += "account_type=eq.USER"
            AdminSection.PERIODIC_PAYMENT -> args += "status=eq.OPEN"
            AdminSection.PAYMENT_TASKS -> if (status.isBlank()) args += "status=in.(OPEN,COMPLETED)"
            else -> Unit
        }
        parseDate(dateFrom, false)?.let { args += "$dateColumn=gte.${filterValue(it)}" }
        parseDate(dateTo, true)?.let { args += "$dateColumn=lte.${filterValue(it)}" }
        if (section in setOf(AdminSection.PAYMENT_TASKS, AdminSection.PERIODIC_PAYMENT) && providerQuery.isNotBlank()) {
            val companies = selectRows("telecom_company", "select=id&or=(name.ilike.${filterValue("*${providerQuery.trim()}*")},code.ilike.${filterValue("*${providerQuery.trim()}*")})&limit=50")
            val ids = companies.map { it.optString("id") }.filter(String::isNotBlank)
            if (ids.isEmpty()) return emptyList()
            args += "telecom_company_id=in.(${ids.joinToString(",")})"
        }
        if (q.isNotBlank()) {
            val clause = when (section) {
                AdminSection.SUBSCRIBERS, AdminSection.USERS -> "or=(name.ilike.${filterValue("*$q*")},email.ilike.${filterValue("*$q*")},public_user_code.ilike.${filterValue("*$q*")})"
                AdminSection.ADDED_NUMBERS -> {
                    val phones = selectRows("phone_number", "select=id&or=(normalized_phone.ilike.${filterValue("*$q*")},display_phone.ilike.${filterValue("*$q*")})&limit=300")
                    val ids = phones.map { it.optString("id") }.filter(String::isNotBlank)
                    if (ids.isEmpty()) return emptyList()
                    "phone_number_id=in.(${ids.joinToString(",")})"
                }
                AdminSection.ACTIVE_NUMBERS -> ""
                AdminSection.PAYMENT_TASKS, AdminSection.PERIODIC_PAYMENT -> "or=(public_task_code.ilike.${filterValue("*$q*")},company_snapshot.ilike.${filterValue("*$q*")})"
                AdminSection.PROVIDERS -> "or=(name.ilike.${filterValue("*$q*")},code.ilike.${filterValue("*$q*")})"
                AdminSection.PACKAGES -> "or=(name.ilike.${filterValue("*$q*")},code.ilike.${filterValue("*$q*")})"
                AdminSection.PAYMENT_METHODS -> "or=(name.ilike.${filterValue("*$q*")},code.ilike.${filterValue("*$q*")},type.ilike.${filterValue("*$q*")})"
                AdminSection.PURCHASES -> "or=(public_purchase_code.ilike.${filterValue("*$q*")},transfer_reference.ilike.${filterValue("*$q*")})"
                AdminSection.FINANCE -> "or=(description.ilike.${filterValue("*$q*")},source_type.ilike.${filterValue("*$q*")})"
                AdminSection.COMMUNICATIONS -> "subject.ilike.${filterValue("*$q*")}"
                AdminSection.NOTIFICATIONS -> "or=(title.ilike.${filterValue("*$q*")},body.ilike.${filterValue("*$q*")},target_type.ilike.${filterValue("*$q*")})"
                AdminSection.TASK_SETTINGS -> "currency.ilike.${filterValue("*$q*")}"
                else -> ""
            }
            if (clause.isNotBlank()) args += clause
        }
        val rows = selectRows(table, args.joinToString("&"))
        return if (section == AdminSection.PERIODIC_PAYMENT) filterPaymentWindow(rows) else rows
    }

    private suspend fun filterPaymentWindow(rows: List<JSONObject>): List<JSONObject> {
        val configs = runCatching { selectRows("task_configuration", "select=telecom_company_id,visibility_days_before,status,effective_from,effective_to&status=eq.ACTIVE&limit=1000") }.getOrDefault(emptyList())
        val now = Instant.now()
        val activeConfigs = configs.filter { config ->
            val from = runCatching { Instant.parse(config.optString("effective_from")) }.getOrNull()
            val toText = config.optString("effective_to")
            val to = if (toText.isBlank()) null else {
                runCatching { Instant.parse(toText) }.getOrNull() ?: return@filter false
            }
            from != null && !from.isAfter(now) && (to == null || to.isAfter(now))
        }
        val visibilityByCompany = activeConfigs.groupBy { it.optString("telecom_company_id") }
            .mapValues { (_, values) -> values.maxByOrNull { it.optString("effective_from") }?.optInt("visibility_days_before", -1) ?: -1 }
        return rows.filter { row ->
            val due = runCatching { Instant.parse(row.optString("due_at")) }.getOrNull() ?: return@filter false
            val window = visibilityByCompany[row.optString("telecom_company_id")] ?: return@filter false
            window >= 0 && row.optString("status") == "OPEN" && !due.isAfter(now.plusSeconds(window.toLong() * 86_400L))
        }
    }

    private suspend fun searchAll(query: String, tableFilter: String = "", page: Int = 0): SearchPage {
        if (query.trim().isEmpty()) return SearchPage(emptyList(), null, false)
        val sources = listOf(
            Triple("customer_profile", "name,email,public_user_code", AdminPermissions.CUSTOMERS_READ),
            Triple("phone_number", "display_phone,normalized_phone", AdminPermissions.NUMBERS_READ),
            Triple("points_purchase", "public_purchase_code,transfer_reference", AdminPermissions.PURCHASES_READ),
            Triple("telecom_company", "name,code", AdminPermissions.PROVIDERS_READ),
            Triple("operation", "operation_type,entity_type", AdminPermissions.OPERATIONS_READ),
            Triple("periodic_task", "public_task_code,company_snapshot", AdminPermissions.TASKS_READ),
            Triple("admin_notification_campaign", "title,body,target_type", AdminPermissions.NOTIFICATIONS_READ),
            Triple("points_package", "name,code", AdminPermissions.PACKAGES_READ),
            Triple("payment_method", "name,type", AdminPermissions.PAYMENT_METHODS_READ),
            Triple("task_configuration", "currency", AdminPermissions.TASK_SETTINGS_READ),
        ).filter { (table, _, permission) -> (tableFilter.isBlank() || table == tableFilter) && runCatching { gateway.hasPermission(permission) }.getOrDefault(false) }
        val merged = mutableListOf<JSONObject>()
        val issues = mutableListOf<String>()
        var hasMore = false
        val offset = page.coerceAtLeast(0) * 50
        for ((table, columns, _) in sources) {
            try {
                val term = filterValue("*${query.trim()}*")
                val orClause = columns.split(',').joinToString(",") { "$it.ilike.$term" }
                val sourceRows = selectRows(table, "select=*&or=($orClause)&order=id.asc&limit=51&offset=$offset")
                if (sourceRows.size > 50) hasMore = true
                sourceRows.take(50).forEach { row ->
                    row.put("_aman_source_table", table).put("_record_type", tableLabel(table)); merged += row
                }
            } catch (_: Exception) { issues += table }
        }
        if (merged.isEmpty() && issues.size == sources.size && sources.isNotEmpty()) throw ContractException("تعذر تنفيذ البحث ضمن مصادر الصلاحية.")
        return SearchPage(merged, issues.takeIf { it.isNotEmpty() }?.joinToString("، ") { "مصدر غير متاح: $it" }, hasMore)
    }

    private suspend fun enrich(section: AdminSection, rows: List<JSONObject>) {
        if (rows.isEmpty()) return
        when (section) {
            AdminSection.SUBSCRIBERS, AdminSection.USERS -> {
                val ids = rows.map { it.optString("id") }.filter(String::isNotBlank)
                val idFilter = inFilter(ids)
                val subscribers = if (ids.isEmpty()) emptyMap() else safeRows("subscriber_identity", "select=id,customer_profile_id,public_subscriber_code,activated_at&customer_profile_id=$idFilter&limit=1000").associateBy { it.optString("customer_profile_id") }
                val balances = if (ids.isEmpty()) emptyMap() else safeRows("points_balance", "select=customer_id,balance&customer_id=$idFilter&limit=1000").associateBy { it.optString("customer_id") }
                val numbers = if (ids.isEmpty()) emptyList() else safeRows("customer_number", "select=id,customer_id,status,added_at&customer_id=$idFilter&limit=2000")
                val protections = if (ids.isEmpty()) emptyList() else safeRows("protection_period", "select=id,customer_id,status,end_at&customer_id=$idFilter&limit=2000")
                rows.forEach { row ->
                    val id = row.optString("id")
                    val relatedNumbers = numbers.filter { it.optString("customer_id") == id }
                    val relatedProtections = protections.filter { it.optString("customer_id") == id }
                    row.put("full_name", row.optString("name")).put("balance_points", balances[id]?.optLong("balance") ?: JSONObject.NULL)
                        .put("subscriber_code", subscribers[id]?.optString("public_subscriber_code").orEmpty())
                        .put("became_subscriber_at", subscribers[id]?.optString("activated_at").orEmpty())
                        .put("number_count", relatedNumbers.size).put("protection_count", relatedProtections.count { it.optString("status") == "ACTIVE" })
                        .put("active_number_count", relatedNumbers.count { it.optString("status") == "ACTIVE" })
                        .put("expired_protection_count", relatedProtections.count { it.optString("status") == "EXPIRED" })
                        .put("account_status", row.optString("account_status").lowercase())
                        .put("status", row.optString("account_status").lowercase())
                }
            }
            AdminSection.ADDED_NUMBERS -> {
                val phoneIds = rows.map { it.optString("phone_number_id") }.filter(String::isNotBlank).distinct()
                val customerIds = rows.map { it.optString("customer_id") }.filter(String::isNotBlank).distinct()
                val phones = rowsById(safeRows("phone_number", "select=*&id=${inFilter(phoneIds)}&limit=1000"))
                val customers = rowsById(safeRows("customer_profile", "select=*&id=${inFilter(customerIds)}&limit=1000"))
                val companies = rowsById(safeRows("telecom_company", "select=*&limit=1000"))
                val identities = rows.associate { row ->
                    val id = row.optString("id")
                    id to safeRows("number_protection_identity", "select=id,customer_number_id,public_activation_code&customer_number_id=eq.${filterValue(id)}&limit=1").firstOrNull()
                }
                val protections = identities.mapNotNull { (_, identity) -> identity?.optString("id")?.takeIf(String::isNotBlank) }.flatMap { pid ->
                    safeRows("protection_period", "select=*&protection_identity_id=eq.${filterValue(pid)}&order=end_at.desc&limit=5")
                }
                rows.forEach { row ->
                    val phone = phones[row.optString("phone_number_id")]
                    val customer = customers[row.optString("customer_id")]
                    row.put("phone_e164", phone?.optString("normalized_phone").orEmpty())
                        .put("display_phone", phone?.optString("display_phone").orEmpty())
                        .put("company_name", companies[phone?.optString("telecom_company_id")].displayName())
                        .put("provider_name", companies[phone?.optString("telecom_company_id")].displayName())
                        .put("customer_name", customer.displayName()).put("protection_status", protections.firstOrNull { it.optString("customer_id") == row.optString("customer_id") }?.optString("status").orEmpty())
                }
            }
            AdminSection.ACTIVE_NUMBERS -> {
                val identityIds = rows.map { it.optString("protection_identity_id") }.filter(String::isNotBlank).distinct()
                val identities = rowsById(safeRows("number_protection_identity", "select=*&id=${inFilter(identityIds)}&limit=1000"))
                val customerIds = rows.map { it.optString("customer_id") }.distinct()
                val customers = rowsById(safeRows("customer_profile", "select=*&id=${inFilter(customerIds)}&limit=1000"))
                val tariffs = rowsById(safeRows("protection_tariff", "select=*&limit=1000"))
                val numbers = rowsById(safeRows("customer_number", "select=*&limit=1000"))
                val phoneIds = numbers.values.map { it.optString("phone_number_id") }.distinct()
                val phones = rowsById(safeRows("phone_number", "select=*&id=${inFilter(phoneIds)}&limit=1000"))
                val companies = rowsById(safeRows("telecom_company", "select=*&limit=1000"))
                rows.forEach { row ->
                    val identity = identities[row.optString("protection_identity_id")]
                    val number = numbers[identity?.optString("customer_number_id")]
                    val phone = phones[number?.optString("phone_number_id")]
                    val tariff = tariffs[row.optString("tariff_id")]
                    val company = companies[phone?.optString("telecom_company_id")]
                    val remainingDays = runCatching { java.time.Duration.between(Instant.now(), Instant.parse(row.optString("end_at"))).toDays().coerceAtLeast(0) }.getOrNull()
                    val warningDays = company?.optInt("extension_warning_days", 0) ?: 0
                    row.put("phone_e164", phone?.optString("normalized_phone").orEmpty()).put("display_phone", phone?.optString("display_phone").orEmpty())
                        .put("customer_name", customers[row.optString("customer_id")].displayName())
                        .put("provider_name", company.displayName())
                        .put("duration_unit_days_snapshot", row.optInt("duration_unit_days_snapshot"))
                        .put("points_per_unit_snapshot", row.optLong("points_per_unit_snapshot"))
                        .put("tariff_mode", tariff?.optString("tariff_mode").orEmpty())
                        .put("task_plan_interval_days", JSONObject.NULL)
                        .put("remaining_days", remainingDays ?: JSONObject.NULL)
                        .put("extension_warning_days", warningDays)
                        .put("needs_extension", warningDays > 0 && remainingDays != null && remainingDays <= warningDays)
                }
            }
            AdminSection.PAYMENT_TASKS, AdminSection.PERIODIC_PAYMENT -> {
                val customers = rowsById(safeRows("customer_profile", "select=id,name,email,public_user_code&limit=1000"))
                val numbers = rowsById(safeRows("customer_number", "select=*&limit=2000"))
                val phoneIds = numbers.values.map { it.optString("phone_number_id") }.distinct()
                val phones = rowsById(safeRows("phone_number", "select=*&id=${inFilter(phoneIds)}&limit=2000"))
                val companies = rowsById(safeRows("telecom_company", "select=*&limit=1000"))
                val executions = rows.associate { row -> row.optString("id") to safeRows("task_execution", "select=*&task_id=eq.${filterValue(row.optString("id"))}&limit=1").firstOrNull() }
                rows.forEach { row ->
                    val number = numbers[row.optString("customer_number_id")]
                    val phone = phones[number?.optString("phone_number_id")]
                    row.put("phone_e164", phone?.optString("normalized_phone").orEmpty()).put("display_phone", phone?.optString("display_phone").orEmpty())
                        .put("customer_name", customers[row.optString("customer_id")].displayName())
                        .put("company_name", companies[row.optString("telecom_company_id")].displayName())
                        .put("provider_name", companies[row.optString("telecom_company_id")].displayName())
                        .put("task_attention", if (runCatching { Instant.parse(row.optString("due_at")).isBefore(Instant.now()) }.getOrDefault(false) && row.optString("status") == "OPEN") "متأخرة" else "قادمة")
                        .put("execution_reference", executions[row.optString("id")]?.optString("external_reference").orEmpty())
                }
            }
            AdminSection.PROVIDERS -> {
                val prefixes = groupRows(safeRows("telecom_prefix", "select=*&limit=2000"), "telecom_company_id")
                val tariffs = groupRows(safeRows("protection_tariff", "select=*&limit=2000"), "telecom_company_id")
                val configs = groupRows(safeRows("task_configuration", "select=*&limit=2000"), "telecom_company_id")
                rows.forEach { row ->
                    val id = row.optString("id")
                    row.put("_prefixes", JSONArray().apply { prefixes[id].orEmpty().forEach(::put) })
                        .put("_tariffs", JSONArray().apply { tariffs[id].orEmpty().forEach(::put) })
                        .put("prefix_count", prefixes[id].orEmpty().size)
                        .put("duration_unit_days", JSONObject.NULL).put("points_per_unit", JSONObject.NULL)
                        .put("task_settings", configs[id].orEmpty().maxByOrNull { it.optString("effective_from") } ?: JSONObject.NULL)
                }
            }
            AdminSection.TASK_SETTINGS -> {
                val companies = rowsById(safeRows("telecom_company", "select=*&limit=1000"))
                rows.forEach { row ->
                    row.put("provider_name", companies[row.optString("telecom_company_id")].displayName())
                    row.put("auto_create", row.optBoolean("create_first_task_on_activation"))
                    row.put("post_expiry_creation_limit_days", row.optInt("post_expiry_grace_days"))
                }
            }
            AdminSection.PACKAGES, AdminSection.PAYMENT_METHODS -> {
                rows.forEach { row -> row.put("status", if (row.optBoolean("is_active")) "ACTIVE" else "INACTIVE") }
            }
            AdminSection.PURCHASES -> {
                val customers = rowsById(safeRows("customer_profile", "select=*&limit=2000"))
                rows.forEach { row ->
                    row.put("customer_name", customers[row.optString("customer_id")].displayName())
                    row.put("request_number", row.optString("public_purchase_code"))
                    row.put("points_amount_snapshot", row.optLong("points_snapshot"))
                    row.put("price_amount_snapshot", row.optString("price_snapshot"))
                    row.put("payment_reference", row.optString("transfer_reference"))
                    row.put("payment_method_details_snapshot", listOf(row.optString("receiving_account_snapshot"), row.optString("payment_instructions_snapshot"))
                        .filter(String::isNotBlank).joinToString("\n"))
                }
            }
            AdminSection.NOTIFICATIONS -> {
                val counts = safeRows("admin_notification_recipient", "select=campaign_id,delivery_status&limit=5000").groupingBy { it.optString("campaign_id") }.eachCount()
                rows.forEach { it.put("recipient_count", counts[it.optString("id")] ?: 0).put("sent_at", it.optString("sent_at")) }
            }
            AdminSection.COMMUNICATIONS -> {
                val customers = rowsById(safeRows("customer_profile", "select=id,name,email,public_user_code&limit=2000"))
                rows.forEach { it.put("customer_name", customers[it.optString("customer_id")].displayName()) }
            }
            else -> Unit
        }
    }

    private suspend fun enrichReport(type: ReportType, rows: List<JSONObject>) {
        when (type.id) {
            "numbers" -> enrich(AdminSection.ADDED_NUMBERS, rows)
            "tasks" -> enrich(AdminSection.PAYMENT_TASKS, rows)
            "protections" -> enrich(AdminSection.ACTIVE_NUMBERS, rows)
            "users" -> enrich(AdminSection.USERS, rows)
            "subscribers" -> enrich(AdminSection.SUBSCRIBERS, rows)
            "purchases" -> enrich(AdminSection.PURCHASES, rows)
            else -> Unit
        }
    }

    private suspend fun safeRows(table: String, query: String): List<JSONObject> = runCatching { selectRows(table, query) }.getOrDefault(emptyList())
    private suspend fun selectRows(table: String, query: String): List<JSONObject> = JSONArray(gateway.select(table, query)).toObjects()

    suspend fun perform(section: AdminSection, action: AdminMutation, row: JSONObject, input: String = "") {
        val id = row.optString("id").takeIf(String::isNotBlank) ?: throw ContractException("السجل المحدد لا يحتوي معرّفًا صالحًا.")
        val requestIdentity = "${action.name}|$id|${input.trim()}|$row"
        val key = cache.getOrCreateIdempotencyKey("admin-mutation", requestIdentity)
        val args = when (action) {
            AdminMutation.APPROVE_PURCHASE -> JSONObject().put("p_purchase_id", id).put("p_idempotency_key", key)
            AdminMutation.REJECT_PURCHASE -> JSONObject().put("p_purchase_id", id).put("p_reason", input.trim()).put("p_idempotency_key", key)
            AdminMutation.EXECUTE_PAYMENT_TASK -> JSONObject().put("p_task_id", id).put("p_external_reference", input.trim()).put("p_idempotency_key", key)
            AdminMutation.RESCHEDULE_PAYMENT_TASK -> JSONObject().put("p_task_id", id).put("p_new_due_at", normalizeInstant(input)).put("p_reason", "").put("p_idempotency_key", key)
            AdminMutation.CANCEL_PAYMENT_TASK -> JSONObject().put("p_task_id", id).put("p_reason", input.trim()).put("p_idempotency_key", key)
            AdminMutation.SET_SUBSCRIBER_STATUS, AdminMutation.UPDATE_PROFILE -> JSONObject()
                .put("p_customer_id", id).put("p_name", row.optString("name").ifBlank { row.optString("full_name") })
                .put("p_email", row.optString("email")).put("p_account_status", input.uppercase())
                .put("p_reason", "تغيير حالة من تطبيق الإدارة").put("p_idempotency_key", key)
            AdminMutation.SET_CUSTOMER_NUMBER_STATUS -> JSONObject().put("p_customer_number_id", id).put("p_status", input.uppercase())
                .put("p_reason", "تغيير حالة علاقة الرقم من تطبيق الإدارة").put("p_idempotency_key", key)
            AdminMutation.APPROVE_SUPPORT_REQUEST -> JSONObject().put("p_conversation_id", id).put("p_reason", input.trim()).put("p_idempotency_key", key)
            AdminMutation.SEND_SUPPORT_REPLY -> JSONObject().put("p_conversation_id", id).put("p_body", input.trim()).put("p_idempotency_key", key)
            AdminMutation.CLOSE_SUPPORT -> JSONObject().put("p_conversation_id", id).put("p_reason", input.trim()).put("p_idempotency_key", key)
            else -> throw ContractException("الإجراء غير متاح لهذا السجل.")
        }
        gateway.rpc(action.rpcName, args)
        cache.completeIdempotencyKey("admin-mutation", requestIdentity, key)
    }

    suspend fun saveForm(kind: AdminFormKind, id: String?, parentId: String?, values: Map<String, String>) {
        val requestIdentity = "${kind.name}|${id.orEmpty()}|${parentId.orEmpty()}|${values.toSortedMap()}"
        val key = cache.getOrCreateIdempotencyKey("admin-form", requestIdentity)
        val args: JSONObject
        val rpc: String
        when (kind) {
            AdminFormKind.USER -> {
                validateForm(kind, id, parentId, values)
                args = JSONObject().put("p_customer_id", id).put("p_name", values["name"].orEmpty())
                    .put("p_email", values["email"].orEmpty()).put("p_account_status", values["account_status"].orEmpty().uppercase())
                    .put("p_reason", values["reason"].orEmpty()).put("p_idempotency_key", key)
                rpc = "admin_update_customer_profile"
            }
            AdminFormKind.PROVIDER -> {
                validateForm(kind, id, parentId, values)
                args = JSONObject().put("p_id", id ?: JSONObject.NULL).put("p_code", values["code"].orEmpty())
                    .put("p_name", values["name"].orEmpty()).put("p_status", values["status"].orEmpty().uppercase())
                    .put("p_extension_warning_days", values["extension_warning_days"]?.toInt() ?: 0).put("p_idempotency_key", key)
                rpc = "admin_save_telecom_company"
            }
            AdminFormKind.PREFIX -> {
                validateForm(kind, id, parentId, values)
                args = JSONObject().put("p_id", id ?: JSONObject.NULL).put("p_telecom_company_id", values["telecom_company_id"]?.takeIf(String::isNotBlank) ?: parentId ?: JSONObject.NULL)
                    .put("p_prefix", values["prefix"].orEmpty()).put("p_status", values["status"].orEmpty().uppercase()).put("p_idempotency_key", key)
                rpc = "admin_save_telecom_prefix"
            }
            AdminFormKind.TARIFF -> {
                validateForm(kind, id, parentId, values)
                args = JSONObject().put("p_id", id ?: JSONObject.NULL).put("p_provider_id", values["telecom_company_id"]?.takeIf(String::isNotBlank) ?: parentId ?: JSONObject.NULL)
                    .put("p_tariff_mode", values["tariff_mode"].orEmpty().uppercase()).put("p_duration_unit_days", values["duration_unit_days"]?.toInt() ?: 0)
                    .put("p_points_per_unit", values["points_per_unit"]?.toLong() ?: 0L).put("p_rate", decimal(values["rate"]))
                    .put("p_currency", values["currency"].orEmpty()).put("p_effective_from", normalizeInstant(values["effective_from"].orEmpty()))
                    .put("p_effective_to", values["effective_to"]?.takeIf(String::isNotBlank)?.let(::normalizeInstant) ?: JSONObject.NULL)
                    .put("p_status", values["status"].orEmpty().uppercase())
                rpc = "admin_save_provider_tariff"
            }
            AdminFormKind.PACKAGE -> {
                validateForm(kind, id, parentId, values)
                args = JSONObject().put("p_id", id ?: JSONObject.NULL).put("p_code", values["code"].orEmpty()).put("p_name", values["name"].orEmpty())
                    .put("p_points", values["points"]?.toLong() ?: 0L).put("p_price", decimal(values["price"]))
                    .put("p_currency", values["currency"].orEmpty()).put("p_display_order", values["display_order"]?.toIntOrNull() ?: 0)
                    .put("p_is_visible", values["is_visible"].toBoolean()).put("p_is_active", values["is_active"].toBoolean()).put("p_idempotency_key", key)
                rpc = "admin_save_points_package"
            }
            AdminFormKind.PAYMENT_METHOD -> {
                validateForm(kind, id, parentId, values)
                args = JSONObject().put("p_id", id ?: JSONObject.NULL).put("p_code", values["code"].orEmpty()).put("p_name", values["name"].orEmpty())
                    .put("p_type", values["type"].orEmpty()).put("p_receiving_account", values["receiving_account"].orEmpty())
                    .put("p_transfer_instructions", values["transfer_instructions"].orEmpty()).put("p_display_order", values["display_order"]?.toIntOrNull() ?: 0)
                    .put("p_is_visible", values["is_visible"].toBoolean()).put("p_is_active", values["is_active"].toBoolean()).put("p_idempotency_key", key)
                rpc = "admin_save_payment_method"
            }
            AdminFormKind.TASK_SETTINGS -> {
                validateForm(kind, id, parentId, values)
                args = JSONObject().put("p_telecom_company_id", values["telecom_company_id"]?.takeIf(String::isNotBlank) ?: parentId ?: JSONObject.NULL)
                    .put("p_interval_days", values["interval_days"]?.toInt() ?: 0).put("p_task_amount", decimal(values["task_amount"]))
                    .put("p_currency", values["currency"].orEmpty()).put("p_visibility_days_before", values["visibility_days_before"]?.toIntOrNull() ?: 0)
                    .put("p_allow_reschedule", values["allow_reschedule"].toBoolean()).put("p_allow_post_expiry_creation", values["allow_post_expiry_creation"].toBoolean())
                    .put("p_post_expiry_grace_days", values["post_expiry_grace_days"]?.toIntOrNull() ?: 0)
                    .put("p_create_first_task_on_activation", values["create_first_task_on_activation"].toBoolean())
                    .put("p_create_first_task_on_renewal", values["create_first_task_on_renewal"].toBoolean())
                    .put("p_effective_from", normalizeInstant(values["effective_from"].orEmpty())).put("p_idempotency_key", key)
                rpc = "admin_save_task_configuration"
            }
            AdminFormKind.EXPENSE -> {
                validateForm(kind, id, parentId, values)
                args = JSONObject().put("p_expense_type_id", values["expense_type_id"].orEmpty()).put("p_amount", decimal(values["amount"]))
                    .put("p_currency", values["currency"].orEmpty()).put("p_description", values["description"].orEmpty())
                    .put("p_reference", values["reference"].orEmpty()).put("p_idempotency_key", key)
                rpc = "admin_create_expense"
            }
        }
        gateway.rpc(rpc, args)
        cache.completeIdempotencyKey("admin-form", requestIdentity, key)
    }

    private fun validateForm(kind: AdminFormKind, id: String?, parentId: String?, values: Map<String, String>) {
        fun required(key: String, label: String) { if (values[key].isNullOrBlank()) throw ContractException("أدخل $label.") }
        fun positiveLong(key: String, label: String) { val n = values[key]?.toLongOrNull() ?: throw ContractException("أدخل رقمًا صحيحًا في $label."); if (n <= 0) throw ContractException("يجب أن يكون $label أكبر من صفر.") }
        fun integer(key: String, label: String, min: Int = 0) { val n = values[key]?.toIntOrNull() ?: throw ContractException("أدخل عددًا صحيحًا في $label."); if (n < min) throw ContractException("يجب ألا تقل قيمة $label عن $min.") }
        fun amount(key: String, label: String, allowZero: Boolean = true) { val n = runCatching { BigDecimal(values[key].orEmpty().trim()) }.getOrElse { throw ContractException("أدخل قيمة مالية صالحة في $label.") }; if (n < BigDecimal.ZERO || (!allowZero && n == BigDecimal.ZERO)) throw ContractException("تحقق من قيمة $label.") }
        when (kind) {
            AdminFormKind.USER -> { if (id.isNullOrBlank()) throw ContractException("لا يُنشأ عميل من لوحة الإدارة."); required("name", "الاسم"); required("account_status", "حالة الحساب") }
            AdminFormKind.PROVIDER -> { required("code", "رمز الشركة"); required("name", "اسم الشركة"); integer("extension_warning_days", "أيام التحذير"); validateGenericStatus(values["status"]) }
            AdminFormKind.PREFIX -> { if (values["telecom_company_id"].isNullOrBlank() && parentId.isNullOrBlank()) throw ContractException("حدد الشركة."); required("prefix", "بادئة الهاتف"); validateGenericStatus(values["status"]) }
            AdminFormKind.TARIFF -> {
                if (values["telecom_company_id"].isNullOrBlank() && parentId.isNullOrBlank()) throw ContractException("حدد الشركة.")
                if (values["tariff_mode"]?.uppercase() !in setOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")) throw ContractException("اختر وحدة تعرفة صالحة.")
                integer("duration_unit_days", "أيام الوحدة", 1); positiveLong("points_per_unit", "النقاط لكل وحدة")
                amount("rate", "التعرفة"); required("currency", "العملة"); required("effective_from", "بداية السريان"); normalizeInstant(values["effective_from"].orEmpty())
                values["effective_to"]?.takeIf(String::isNotBlank)?.let(::normalizeInstant); validateGenericStatus(values["status"])
            }
            AdminFormKind.PACKAGE -> { required("code", "رمز الباقة"); required("name", "اسم الباقة"); positiveLong("points", "النقاط"); amount("price", "السعر", false); required("currency", "العملة"); integer("display_order", "ترتيب العرض"); required("is_visible", "حالة الظهور"); required("is_active", "حالة التفعيل") }
            AdminFormKind.PAYMENT_METHOD -> { required("code", "رمز وسيلة الدفع"); required("name", "اسم وسيلة الدفع"); required("type", "نوع وسيلة الدفع"); required("receiving_account", "حساب الاستلام"); integer("display_order", "ترتيب العرض"); required("is_visible", "حالة الظهور"); required("is_active", "حالة التفعيل") }
            AdminFormKind.TASK_SETTINGS -> { if (values["telecom_company_id"].isNullOrBlank() && parentId.isNullOrBlank()) throw ContractException("حدد الشركة."); integer("interval_days", "فاصل المهام", 1); amount("task_amount", "قيمة المهمة"); required("currency", "العملة"); integer("visibility_days_before", "أيام الظهور"); integer("post_expiry_grace_days", "أيام السماح بعد الانتهاء"); required("effective_from", "تاريخ بدء السريان"); normalizeInstant(values["effective_from"].orEmpty()) }
            AdminFormKind.EXPENSE -> { required("expense_type_id", "نوع المصروف"); amount("amount", "المبلغ", false); required("currency", "العملة"); required("description", "الوصف") }
        }
    }

    private fun validateGenericStatus(value: String?) {
        if (value?.uppercase() !in setOf("ACTIVE", "INACTIVE")) throw ContractException("اختر حالة صالحة.")
    }

    suspend fun sendNotification(targetType: String, targetId: String?, title: String, body: String) {
        val kind = targetType.uppercase()
        if (kind in setOf("USER", "SUBSCRIBER") && targetId.isNullOrBlank()) throw ContractException("اختر مستهدفًا من نتائج البحث.")
        if (title.trim().isEmpty() || title.trim().length > 160 || body.trim().isEmpty() || body.trim().length > 4000) throw ContractException("تحقق من عنوان ومحتوى الإشعار.")
        val requestIdentity = "${kind}|${targetId.orEmpty()}|${title.trim()}|${body.trim()}"
        val key = cache.getOrCreateIdempotencyKey("admin-notification", requestIdentity)
        gateway.rpc("admin_send_notification", JSONObject().put("p_target_type", kind)
            .put("p_target_id", targetId?.takeIf(String::isNotBlank) ?: JSONObject.NULL)
            .put("p_title", title.trim()).put("p_body", body.trim()).put("p_idempotency_key", key))
        cache.completeIdempotencyKey("admin-notification", requestIdentity, key)
    }

    private fun parseDate(date: String, endOfDay: Boolean): String? {
        if (date.isBlank()) return null
        return runCatching {
            val clean = date.trim()
            if (clean.contains('T')) Instant.parse(clean).toString()
            else if (endOfDay) java.time.LocalDate.parse(clean).plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).minusNanos(1).toInstant().toString()
            else java.time.LocalDate.parse(clean).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toString()
        }.getOrElse { throw ContractException("أدخل التاريخ بصيغة YYYY-MM-DD أو طابع زمني ISO.") }
    }

    private fun normalizeInstant(value: String): String = runCatching { Instant.parse(value.trim()).toString() }
        .getOrElse { runCatching { java.time.LocalDate.parse(value.trim()).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toString() }
            .getOrElse { throw ContractException("أدخل وقتًا/تاريخًا صالحًا بصيغة ISO-8601.") } }
    private fun decimal(value: String?): String = runCatching { BigDecimal(value.orEmpty().trim()).toPlainString() }
        .getOrElse { throw ContractException("أدخل مبلغًا رقميًا صالحًا.") }
    private fun filterValue(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
    private fun inFilter(ids: List<String>): String = if (ids.isEmpty()) "in.(00000000-0000-0000-0000-000000000000)" else "in.(${ids.joinToString(",")})"
    private fun tableLabel(table: String): String = when (table) {
        "customer_profile" -> "عميل"; "phone_number" -> "رقم"; "points_purchase" -> "طلب شراء"; "telecom_company" -> "شركة"
        "operation" -> "عملية"; "periodic_task" -> "مهمة"; "admin_notification_campaign" -> "إشعار"; "points_package" -> "باقة"
            "payment_method" -> "وسيلة دفع"; "task_configuration" -> "إعداد تشغيل"; "task_plan" -> "خطة مهام"; else -> table
    }
    private fun JSONObject?.displayName(): String = this?.let { optString("name").ifBlank { optString("display_phone") }.ifBlank { optString("email") }.ifBlank { optString("public_user_code") } }.orEmpty()
    private fun rowsById(rows: List<JSONObject>): Map<String, JSONObject> = rows.associateBy { it.optString("id") }
    private fun groupRows(rows: List<JSONObject>, key: String): Map<String, List<JSONObject>> = rows.groupBy { it.optString(key) }
    private fun JSONArray.toObjects(): List<JSONObject> = List(length()) { getJSONObject(it) }
}
