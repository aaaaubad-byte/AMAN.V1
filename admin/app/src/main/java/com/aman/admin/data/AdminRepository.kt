package com.aman.admin.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.math.BigDecimal
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

class ContractException(message: String) : Exception(message)

data class LoadedRecords(
    val rows: List<JSONObject>,
    val offlineSnapshot: Boolean = false,
    val cachedAtMillis: Long? = null,
    val warningMessage: String? = null,
)

class AdminRepository(private val gateway: SupabaseGateway, private val cache: LocalSnapshotCache) {
    fun configurationMessage(): String? = gateway.configurationMessage()
    fun currentUserId(): String? = gateway.currentUserId()
    fun canUseCachedAdminSession(): Boolean = gateway.canUseCachedAdminSession()
    suspend fun signIn(email: String, password: String) = gateway.signIn(email, password)
    suspend fun verifyAdmin(): Boolean = gateway.isAdmin()
    suspend fun accountInfo(): JSONObject = JSONObject(gateway.rpc("admin_account_info", JSONObject())).also { info ->
        gateway.currentUserId()?.let { cache.put("$it:account", JSONArray().put(info)) }
    }
    fun cachedAccountInfo(): JSONObject? = gateway.currentUserId()?.let { cache.get("$it:account")?.optJSONObject(0) }
    suspend fun signOut() { try { gateway.signOut() } finally { cache.clear() } }
    suspend fun clearLocalSession() { gateway.clearLocalSession(); cache.clear() }

    suspend fun load(section: AdminSection, query: String = "", status: String = "", dateFrom: String = "", dateTo: String = "", providerQuery: String = ""): LoadedRecords {
        val uid = gateway.currentUserId() ?: throw ContractException("انتهت جلسة الدخول. سجّل الدخول مجددًا.")
        val cacheKey = "$uid:${section.name}"
        try {
            var warning: String? = null
            val response = when (section) {
                AdminSection.HOME -> loadDashboard().also { warning = it.second }.first
                AdminSection.SEARCH -> searchAll(query, status).also { warning = it.second }.first
                AdminSection.ACCOUNT -> gateway.rpc("admin_account_info", JSONObject()).let { "[$it]" }
                AdminSection.REPORTS -> "[]"
                else -> loadSectionRows(section, query, status, dateFrom, dateTo, providerQuery)
            }
            val rows = JSONArray(response).toObjects()
            if (section !in setOf(AdminSection.SEARCH, AdminSection.ACCOUNT)) enrich(section, rows)
            cache.put(cacheKey, JSONArray().apply { rows.forEach(::put) })
            return LoadedRecords(rows, warningMessage = warning)
        } catch (error: IOException) {
            val saved = cache.get(cacheKey) ?: throw error
            return LoadedRecords(filterCachedRows(saved.toObjects(), section, query, status, dateFrom, dateTo, providerQuery), offlineSnapshot = true, cachedAtMillis = cache.updatedAt(cacheKey))
        }
    }

    private fun filterCachedRows(rows: List<JSONObject>, section: AdminSection, query: String, status: String, from: String, to: String, provider: String): List<JSONObject> {
        val token = query.trim().lowercase()
        val sourceDate = when (section) {
            AdminSection.PAYMENT_TASKS -> "due_at"
            AdminSection.PURCHASES -> "submitted_at"
            AdminSection.ADDED_NUMBERS -> "added_at"
            AdminSection.ACTIVE_NUMBERS -> "started_at"
            else -> "created_at"
        }
        val minimum = parseDate(from, false)?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val maximum = parseDate(to, true)?.let { runCatching { Instant.parse(it) }.getOrNull() }
        return rows.filter { row ->
            val rowStatus = if (section == AdminSection.USERS) row.optString("account_status") else row.optString("status")
            val correctType = section != AdminSection.SEARCH || status.isBlank() || row.optString("_aman_source_table") == status
            val searchMatch = token.isBlank() || row.toString().contains(token, ignoreCase = true)
            val providerMatch = provider.isBlank() || row.optString("provider_name").contains(provider, ignoreCase = true)
            val rowDate = runCatching { Instant.parse(row.optString(sourceDate)) }.getOrNull()
            val fromMatch = minimum == null || rowDate?.let { !it.isBefore(minimum) } == true
            val toMatch = maximum == null || rowDate?.let { !it.isAfter(maximum) } == true
            correctType && searchMatch && providerMatch && (status.isBlank() || section == AdminSection.SEARCH || rowStatus == status) && fromMatch && toMatch
        }
    }

    suspend fun loadReport(type: ReportType, from: String, to: String): LoadedRecords {
        val required = when (type.id) {
            "users" -> AdminPermissions.USERS_READ
            "subscribers" -> AdminPermissions.SUBSCRIBERS_READ
            "numbers" -> AdminPermissions.NUMBERS_READ
            "purchases" -> AdminPermissions.PURCHASES_READ
            "points" -> AdminPermissions.POINTS_READ
            "protections" -> AdminPermissions.PROTECTIONS_READ
            "tasks" -> AdminPermissions.TASKS_READ
            "finance", "operations" -> AdminPermissions.REPORTS_READ
            else -> AdminPermissions.REPORTS_READ
        }
        if (!gateway.hasPermission(required)) throw ContractException("لا تملك صلاحية قراءة هذا النوع من التقارير.")
        val response = loadReportRows(type, from, to)
        val rows = JSONArray(response).toObjects()
        enrichReport(type, rows)
        return LoadedRecords(rows)
    }

    suspend fun canExportReports(): Boolean = gateway.hasPermission(AdminPermissions.REPORTS_EXPORT)

    private suspend fun loadReportRows(type: ReportType, from: String, to: String): String {
        val params = mutableListOf("select=*", "order=${type.dateColumn}.desc", "limit=500")
        parseDate(from, endOfDay = false)?.let { params += "${type.dateColumn}=gte.${filterValue(it)}" }
        parseDate(to, endOfDay = true)?.let { params += "${type.dateColumn}=lte.${filterValue(it)}" }
        return gateway.select(type.table, params.joinToString("&"))
    }

    suspend fun loadRelated(kind: RelatedListKind, parentId: String): List<JSONObject> {
        val (table, column, value) = when (kind) {
            RelatedListKind.SUBSCRIBER_NUMBERS -> Triple("customer_numbers", "user_id", parentId)
            RelatedListKind.SUBSCRIBER_POINTS -> Triple("point_ledger", "user_id", parentId)
            RelatedListKind.SUBSCRIBER_HISTORY -> Triple("operations", "user_id", parentId)
            RelatedListKind.PROTECTION_TASKS -> Triple("payment_tasks", "protection_id", parentId)
            RelatedListKind.PROVIDER_PREFIXES -> Triple("telecom_prefixes", "provider_id", parentId)
            RelatedListKind.PROVIDER_TARIFFS -> Triple("provider_tariffs", "provider_id", parentId)
        }
        val order = if (kind == RelatedListKind.PROTECTION_TASKS) "due_at" else "created_at"
        val rows = JSONArray(gateway.select(table, "select=*&$column=eq.${filterValue(value)}&order=$order.desc&limit=300")).toObjects()
        when (kind) {
            RelatedListKind.SUBSCRIBER_NUMBERS -> enrich(AdminSection.ADDED_NUMBERS, rows)
            RelatedListKind.PROTECTION_TASKS -> enrich(AdminSection.PAYMENT_TASKS, rows)
            else -> Unit
        }
        return rows
    }

    suspend fun findRecipients(type: String, query: String): List<JSONObject> {
        if (type == "all" || query.trim().length < 2) return emptyList()
        val search = filterValue("*${query.trim()}*")
        val profiles = JSONArray(gateway.select("profiles", "select=id,full_name,username,email,phone,account_status&or=(full_name.ilike.$search,username.ilike.$search,email.ilike.$search,phone.ilike.$search)&limit=30")).toObjects()
        val profileById = profiles.associateBy { it.optString("id") }
        val rows = if (type == "subscriber") {
            val ids = profiles.map { it.optString("id") }
            if (ids.isEmpty()) emptyList() else JSONArray(gateway.select("subscribers", "select=id,user_id,status,became_subscriber_at&user_id=in.(${ids.joinToString(",")})&limit=30")).toObjects()
        } else profiles
        return rows.map { row ->
            val userId = if (type == "subscriber") row.optString("user_id") else row.optString("id")
            val profile = profileById[userId]
            row.put("_recipient_type", type).put("_recipient_id", row.optString("id"))
                .put("_recipient_name", profile?.optString("full_name").orEmpty().ifBlank { profile?.optString("email").orEmpty() }.ifBlank { profile?.optString("username").orEmpty() })
        }
    }

    private suspend fun loadDashboard(): Pair<String, String?> {
        val merged = JSONArray()
        val failures = mutableListOf<String>()
        val metrics = listOf(
            Triple(AdminPermissions.USERS_READ, "profiles", "المستخدمون" to AdminSection.USERS),
            Triple(AdminPermissions.SUBSCRIBERS_READ, "subscribers", "المشتركون" to AdminSection.SUBSCRIBERS),
            Triple(AdminPermissions.NUMBERS_READ, "customer_numbers", "الأرقام المضافة" to AdminSection.ADDED_NUMBERS),
            Triple(AdminPermissions.PROTECTIONS_READ, "protections?status=eq.active", "الحمايات النشطة" to AdminSection.ACTIVE_NUMBERS),
            Triple(AdminPermissions.PURCHASES_READ, "points_purchase_requests?status=eq.pending", "طلبات الشراء المعلقة" to AdminSection.PURCHASES),
            Triple(AdminPermissions.TASKS_READ, "payment_tasks?status=eq.open", "مهام السداد المفتوحة" to AdminSection.PAYMENT_TASKS),
        )
        for ((permission, source, metric) in metrics) {
            if (!runCatching { gateway.hasPermission(permission) }.getOrDefault(false)) continue
            try {
                val table = source.substringBefore('?')
                val filter = source.substringAfter('?', "").replace("status=eq.", "status=eq.")
                val count = gateway.countRows(table, filter)
                merged.put(JSONObject().put("_dashboard_metric", metric.first).put("_dashboard_count", count).put("_dashboard_section", metric.second.name))
            } catch (_: Exception) { failures += "مؤشر ${metric.first}" }
        }
        val sources = listOf(
            Triple("points_purchase_requests", "select=*&status=eq.pending&order=submitted_at.desc&limit=25", "purchase"),
            Triple("payment_tasks", "select=*&status=eq.open&order=due_at.asc&limit=50", "task"),
            Triple("operations", "select=*&order=created_at.desc&limit=40", "operation"),
        )
        val phones = rowsById(optionalRows("phone_numbers"))
        val providers = rowsById(optionalRows("telecom_providers"))
        for ((table, query, kind) in sources) {
            try {
                val rows = JSONArray(gateway.select(table, query))
                for (index in 0 until rows.length()) {
                    val row = rows.getJSONObject(index)
                    row.put("_aman_source_table", table)
                    row.put("_attention_kind", kind)
                    if (table == "payment_tasks") {
                        row.put("phone_e164", phones[row.optString("phone_number_id")]?.optString("phone_e164").orEmpty())
                        row.put("provider_name", providers[row.optString("provider_id")].displayName())
                        val due = row.optString("due_at")
                        row.put("task_attention", if (runCatching { Instant.parse(due).isBefore(Instant.now()) }.getOrDefault(false)) "متأخرة" else "قادمة")
                    }
                    merged.put(row)
                }
            } catch (error: Exception) { failures += "$table: ${error.message ?: "غير متاح"}" }
        }
        if (merged.length() == 0 && failures.isNotEmpty()) throw ContractException("تعذر تحميل بيانات الرئيسية: ${failures.joinToString("؛ ")}")
        return merged.toString() to failures.takeIf { it.isNotEmpty() }?.joinToString("؛ ") { "تعذر تحميل مصدر — $it" }
    }

    private suspend fun loadSectionRows(section: AdminSection, query: String, status: String, dateFrom: String, dateTo: String, providerQuery: String): String {
        val q = query.trim()
        val table = section.table ?: throw ContractException("لا يوجد مصدر بيانات معرف لهذا القسم.")
        val dateColumn = when (section) {
            AdminSection.PURCHASES -> "submitted_at"
            AdminSection.PAYMENT_TASKS -> "due_at"
            AdminSection.ADDED_NUMBERS -> "added_at"
            AdminSection.ACTIVE_NUMBERS -> "started_at"
            AdminSection.TASK_SETTINGS -> "updated_at"
            else -> "created_at"
        }
        val args = mutableListOf("select=*", "order=$dateColumn.desc", "limit=200")
        if (status.isNotBlank()) args += "${if (section == AdminSection.USERS) "account_status" else "status"}=eq.${filterValue(status)}"
        if (section == AdminSection.PAYMENT_TASKS) {
            parseDate(dateFrom, endOfDay = false)?.let { args += "due_at=gte.${filterValue(it)}" }
            parseDate(dateTo, endOfDay = true)?.let { args += "due_at=lte.${filterValue(it)}" }
            if (providerQuery.isNotBlank()) {
                val providerRows = JSONArray(gateway.select("telecom_providers", "select=id&or=(name.ilike.${filterValue("*${providerQuery.trim()}*")},code.ilike.${filterValue("*${providerQuery.trim()}*")})&limit=30")).toObjects()
                val providerIds = providerRows.map { it.optString("id") }
                if (providerIds.isEmpty()) return "[]"
                args += "provider_id=in.(${providerIds.joinToString(",")})"
            }
        }
        if (q.isNotBlank() && section in setOf(AdminSection.SUBSCRIBERS, AdminSection.ADDED_NUMBERS, AdminSection.ACTIVE_NUMBERS, AdminSection.TASK_SETTINGS)) {
            val ids = if (section == AdminSection.SUBSCRIBERS) {
                JSONArray(gateway.select("profiles", "select=id&or=(full_name.ilike.${filterValue("*$q*")},username.ilike.${filterValue("*$q*")},email.ilike.${filterValue("*$q*")},phone.ilike.${filterValue("*$q*")})&limit=200")).toObjects().map { it.optString("id") }
            } else if (section == AdminSection.TASK_SETTINGS) {
                JSONArray(gateway.select("telecom_providers", "select=id&or=(name.ilike.${filterValue("*$q*")},short_name.ilike.${filterValue("*$q*")},code.ilike.${filterValue("*$q*")})&limit=100")).toObjects().map { it.optString("id") }
            } else {
                JSONArray(gateway.select("phone_numbers", "select=id&or=(phone_e164.ilike.${filterValue("*$q*")},normalized_phone.ilike.${filterValue("*$q*")})&limit=200")).toObjects().map { it.optString("id") }
            }
            if (ids.isEmpty()) return "[]"
            val column = when (section) { AdminSection.SUBSCRIBERS -> "user_id"; AdminSection.TASK_SETTINGS -> "provider_id"; else -> "phone_number_id" }
            args += "$column=in.(${ids.joinToString(",")})"
            return gateway.select(table, args.joinToString("&"))
        }
        if (q.isNotBlank()) searchClause(section, q)?.let(args::add)
        val results = JSONArray(gateway.select(table, args.joinToString("&"))).toObjects()
        if (q.isBlank() || searchClause(section, q) != null) return JSONArray().apply { results.forEach(::put) }.toString()
        return JSONArray().apply { results.filter { it.toString().contains(q, ignoreCase = true) }.forEach(::put) }.toString()
    }

    private suspend fun searchAll(query: String, typeFilter: String = ""): Pair<String, String?> {
        if (query.trim().isEmpty()) return "[]" to null
        val sources = listOf(
            Triple("profiles", "full_name,username,email,phone", "المستخدم"),
            Triple("phone_numbers", "phone_e164,normalized_phone", "رقم"),
            Triple("points_purchase_requests", "request_number,payment_reference", "طلب شراء"),
            Triple("telecom_providers", "name,short_name,code", "شركة"),
            Triple("operations", "operation_type,reference_type", "عملية"),
            Triple("payment_tasks", "external_payment_reference", "مهمة"),
        )
        val filteredSources = sources.filter { typeFilter.isBlank() || it.first == typeFilter }
        val merged = JSONArray()
        val issues = mutableListOf<String>()
        for ((table, columns, label) in filteredSources) {
            try {
                val search = filterValue("*${query.trim()}*")
                val or = columns.split(',').joinToString(",") { "$it.ilike.$search" }
                val rows = JSONArray(gateway.select(table, "select=*&or=($or)&limit=100"))
                for (i in 0 until rows.length()) rows.getJSONObject(i).apply {
                    put("_aman_source_table", table); put("_record_type", label); merged.put(this)
                }
            } catch (error: Exception) { issues += "$table" }
        }
        if (merged.length() == 0 && issues.size == filteredSources.size) throw ContractException("تعذر تنفيذ البحث ضمن الصلاحيات الحالية.")
        val warning = issues.takeIf { it.isNotEmpty() }?.joinToString("، ") { "مصدر غير متاح: $it" }
        return merged.toString() to warning
    }

    private fun searchClause(section: AdminSection, query: String): String? {
        val fields = when (section) {
            AdminSection.SUBSCRIBERS -> emptyList()
            AdminSection.USERS -> listOf("full_name", "username", "email", "phone")
            AdminSection.ADDED_NUMBERS -> emptyList()
            AdminSection.ACTIVE_NUMBERS -> emptyList()
            AdminSection.PURCHASES -> listOf("request_number", "payment_reference")
            AdminSection.PAYMENT_TASKS -> listOf("external_payment_reference")
            AdminSection.PROVIDERS -> listOf("name", "short_name", "code")
            AdminSection.PACKAGES, AdminSection.PAYMENT_METHODS -> listOf("name")
            AdminSection.TASK_SETTINGS -> emptyList()
            AdminSection.NOTIFICATIONS -> listOf("title", "body", "target_type")
            else -> emptyList()
        }
        if (fields.isEmpty()) return null
        val token = filterValue("*${query.trim()}*")
        return "or=(${fields.joinToString(",") { "$it.ilike.$token" }})"
    }

    private suspend fun enrich(section: AdminSection, rows: List<JSONObject>) {
        if (rows.isEmpty()) return
        when (section) {
            AdminSection.SUBSCRIBERS -> {
                val profiles = rowsById(optionalRows("profiles"))
                val balances = optionalRows("point_balances").associateBy { it.optString("user_id") }
                val numbers = groupRows(optionalRows("customer_numbers"), "user_id")
                val protections = groupRows(optionalRows("protections"), "user_id")
                rows.forEach { row ->
                    val uid = row.optString("user_id")
                    addProfile(row, profiles[uid])
                    row.put("balance_points", balances[uid]?.optLong("balance_points") ?: JSONObject.NULL)
                    row.put("number_count", numbers[uid]?.size ?: 0)
                    row.put("protection_count", protections[uid]?.count { it.optString("status") == "active" } ?: 0)
                }
            }
            AdminSection.USERS -> {
                val balances = optionalRows("point_balances").associateBy { it.optString("user_id") }
                val subscribers = optionalRows("subscribers").associateBy { it.optString("user_id") }
                rows.forEach { row ->
                    val uid = row.optString("id")
                    row.put("balance_points", balances[uid]?.optLong("balance_points") ?: JSONObject.NULL)
                    row.put("subscriber_status", subscribers[uid]?.optString("status") ?: "غير مشترك")
                }
            }
            AdminSection.ADDED_NUMBERS -> {
                val phones = rowsById(optionalRows("phone_numbers"))
                val providers = rowsById(optionalRows("telecom_providers"))
                val profiles = rowsById(optionalRows("profiles"))
                rows.forEach { row ->
                    val phone = phones[row.optString("phone_number_id")]
                    row.put("phone_e164", phone?.optString("phone_e164").orEmpty())
                    row.put("provider_name", providers[phone?.optString("provider_id")].displayName())
                    row.put("customer_name", profiles[row.optString("user_id")].displayName())
                }
            }
            AdminSection.ACTIVE_NUMBERS -> {
                val phones = rowsById(optionalRows("phone_numbers"))
                val profiles = rowsById(optionalRows("profiles"))
                val providers = rowsById(optionalRows("telecom_providers"))
                val tariffs = rowsById(optionalRows("provider_tariffs"))
                val plans = optionalRows("protection_task_plans").associateBy { it.optString("protection_id") }
                val tasks = groupRows(optionalRows("payment_tasks"), "protection_id")
                rows.forEach { row ->
                    val phone = phones[row.optString("phone_number_id")]
                    val tariff = tariffs[row.optString("tariff_id")]
                    val plan = plans[row.optString("id")]
                    val upcoming = tasks[row.optString("id")].orEmpty().filter { it.optString("status") == "open" }.sortedBy { it.optString("due_at") }
                    row.put("phone_e164", phone?.optString("phone_e164").orEmpty())
                    row.put("customer_name", profiles[row.optString("user_id")].displayName())
                    row.put("provider_name", providers[row.optString("provider_id")].displayName())
                    row.put("tariff_points_per_day", tariff?.optInt("points_per_day") ?: row.optInt("points_per_day_snapshot"))
                    row.put("task_plan_interval_days", plan?.optInt("interval_days") ?: JSONObject.NULL)
                    row.put("next_task_due", upcoming.firstOrNull()?.optString("due_at").orEmpty())
                    row.put("next_task_status", upcoming.firstOrNull()?.optString("status").orEmpty())
                    row.put("following_task_due", upcoming.getOrNull(1)?.optString("due_at").orEmpty())
                    row.put("remaining_days", runCatching { java.time.Duration.between(Instant.now(), Instant.parse(row.optString("expires_at"))).toDays().coerceAtLeast(0) }.getOrNull() ?: JSONObject.NULL)
                    row.put("_task_rows", JSONArray().apply { upcoming.forEach(::put) })
                }
            }
            AdminSection.PURCHASES -> {
                val profiles = rowsById(optionalRows("profiles"))
                val packages = rowsById(optionalRows("points_packages"))
                val methods = rowsById(optionalRows("payment_methods"))
                rows.forEach { row ->
                    row.put("customer_name", profiles[row.optString("user_id")].displayName())
                    row.put("package_name", packages[row.optString("package_id")]?.optString("name").orEmpty())
                    row.put("payment_method_details", methods[row.optString("payment_method_id")]?.optString("instructions").orEmpty())
                }
            }
            AdminSection.PAYMENT_TASKS -> {
                val phones = rowsById(optionalRows("phone_numbers"))
                val subscribers = rowsById(optionalRows("subscribers"))
                val profiles = rowsById(optionalRows("profiles"))
                val providers = rowsById(optionalRows("telecom_providers"))
                rows.forEach { row ->
                    val phone = phones[row.optString("phone_number_id")]
                    val subscriber = subscribers[row.optString("subscriber_id")]
                    row.put("phone_e164", phone?.optString("phone_e164").orEmpty())
                    row.put("customer_name", profiles[subscriber?.optString("user_id")].displayName())
                    row.put("provider_name", providers[row.optString("provider_id")].displayName())
                    row.put("task_attention", if (runCatching { Instant.parse(row.optString("due_at")).isBefore(Instant.now()) }.getOrDefault(false) && row.optString("status") == "open") "متأخرة" else "قادمة")
                }
            }
            AdminSection.PROVIDERS -> {
                val prefixes = groupRows(optionalRows("telecom_prefixes"), "provider_id")
                val tariffs = groupRows(optionalRows("provider_tariffs"), "provider_id")
                val settings = optionalRows("task_settings").associateBy { it.optString("provider_id") }
                rows.forEach { row ->
                    val id = row.optString("id")
                    row.put("_prefixes", JSONArray().apply { prefixes[id].orEmpty().forEach(::put) })
                    row.put("_tariffs", JSONArray().apply { tariffs[id].orEmpty().forEach(::put) })
                    row.put("prefix_count", prefixes[id].orEmpty().size)
                    val tariff = tariffs[id].orEmpty().filter { it.optString("status") == "active" }.maxByOrNull { it.optString("effective_from") }
                    row.put("points_per_day", tariff?.optInt("points_per_day") ?: JSONObject.NULL)
                    row.put("task_settings", settings[id] ?: JSONObject.NULL)
                }
            }
            AdminSection.TASK_SETTINGS -> {
                val providers = rowsById(optionalRows("telecom_providers"))
                rows.forEach { row -> row.put("provider_name", providers[row.optString("provider_id")].displayName()) }
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

    private suspend fun optionalRows(table: String, filter: String = ""): List<JSONObject> = try {
        val suffix = if (filter.isBlank()) "" else "&$filter"
        JSONArray(gateway.select(table, "select=*&limit=1000$suffix")).toObjects()
    } catch (_: Exception) { emptyList() }

    suspend fun perform(section: AdminSection, action: AdminMutation, row: JSONObject, input: String = "") {
        val id = row.optString("id").takeIf(String::isNotBlank) ?: throw ContractException("السجل المحدد لا يحتوي معرّفًا صالحًا.")
        val args = when (action) {
            AdminMutation.APPROVE_PURCHASE -> JSONObject().put("p_request_id", id).put("p_idempotency_key", UUID.randomUUID().toString())
            AdminMutation.REJECT_PURCHASE -> JSONObject().put("p_request_id", id).put("p_reason", input.trim()).put("p_idempotency_key", UUID.randomUUID().toString())
            AdminMutation.EXECUTE_PAYMENT_TASK -> JSONObject().put("p_task_id", id).put("p_execution_key", UUID.randomUUID().toString()).put("p_external_reference", input.trim())
            AdminMutation.RESCHEDULE_PAYMENT_TASK -> JSONObject().put("p_task_id", id).put("p_new_due_at", normalizeInstant(input)).put("p_idempotency_key", UUID.randomUUID().toString())
            AdminMutation.CANCEL_PAYMENT_TASK -> JSONObject().put("p_task_id", id).put("p_reason", input.trim())
            AdminMutation.SET_SUBSCRIBER_STATUS -> JSONObject().put("p_subscriber_id", id).put("p_status", input)
            AdminMutation.SET_CUSTOMER_NUMBER_STATUS -> JSONObject().put("p_customer_number_id", id).put("p_status", input)
            else -> throw ContractException("الإجراء غير متاح لهذا القسم.")
        }
        gateway.rpc(action.rpcName, args)
    }

    suspend fun saveForm(kind: AdminFormKind, id: String?, parentId: String?, values: Map<String, String>) {
        val key = id?.takeIf(String::isNotBlank)
        validateForm(kind, key, parentId, values)
        val args = when (kind) {
            AdminFormKind.USER -> JSONObject()
                .put("p_user_id", key ?: JSONObject.NULL)
                .put("p_full_name", values["full_name"].orEmpty())
                .put("p_username", values["username"].orEmpty())
                .put("p_phone", values["phone"].orEmpty())
                .put("p_status", values["account_status"] ?: "active")
            AdminFormKind.PROVIDER -> JSONObject()
                .put("p_id", key ?: JSONObject.NULL).put("p_code", values["code"].orEmpty())
                .put("p_name", values["name"].orEmpty()).put("p_short_name", values["short_name"].orEmpty())
                .put("p_status", values["status"] ?: "active")
                .put("p_operational_settings", parseJsonObject(values["operational_settings"].orEmpty().ifBlank { "{}" }, "إعدادات التشغيل"))
            AdminFormKind.PREFIX -> JSONObject()
                .put("p_id", key ?: JSONObject.NULL).put("p_provider_id", values["provider_id"]?.takeIf(String::isNotBlank) ?: parentId ?: JSONObject.NULL)
                .put("p_prefix", values["prefix"].orEmpty()).put("p_country_code", values["country_code"].orEmpty())
                .put("p_number_length", values["number_length"]?.takeIf(String::isNotBlank)?.toInt() ?: JSONObject.NULL)
                .put("p_status", values["status"] ?: "active")
            AdminFormKind.TARIFF -> JSONObject()
                .put("p_id", key ?: JSONObject.NULL).put("p_provider_id", values["provider_id"]?.takeIf(String::isNotBlank) ?: parentId ?: JSONObject.NULL)
                .put("p_points_per_day", values["points_per_day"]?.toInt() ?: 0)
                .put("p_effective_from", normalizeInstant(values["effective_from"].orEmpty()))
                .put("p_effective_to", values["effective_to"]?.takeIf(String::isNotBlank)?.let(::normalizeInstant) ?: JSONObject.NULL)
                .put("p_status", values["status"] ?: "active")
            AdminFormKind.PACKAGE -> JSONObject()
                .put("p_id", key ?: JSONObject.NULL).put("p_name", values["name"].orEmpty())
                .put("p_points_amount", values["points_amount"]?.toInt() ?: 0)
                .put("p_price_amount", decimal(values["price_amount"]))
                .put("p_currency", values["currency"].orEmpty()).put("p_display_order", values["display_order"]?.toIntOrNull() ?: 0)
                .put("p_status", values["status"] ?: "active")
            AdminFormKind.PAYMENT_METHOD -> {
                val paymentData = parseJsonObject(values["payment_data"].orEmpty().ifBlank { "{}" }, "بيانات الدفع")
                values["method_type"]?.takeIf(String::isNotBlank)?.let { paymentData.put("type", it) }
                values["display_order"]?.toIntOrNull()?.let { paymentData.put("display_order", it) }
                JSONObject().put("p_id", key ?: JSONObject.NULL).put("p_name", values["name"].orEmpty()).put("p_payment_data", paymentData)
                    .put("p_instructions", values["instructions"].orEmpty()).put("p_status", values["status"] ?: "active")
            }
            AdminFormKind.TASK_SETTINGS -> JSONObject()
                .put("p_provider_id", values["provider_id"]?.takeIf(String::isNotBlank) ?: parentId ?: JSONObject.NULL)
                .put("p_interval_days", values["interval_days"]?.toInt() ?: 0)
                .put("p_task_amount", decimal(values["task_amount"]))
                .put("p_currency", values["currency"].orEmpty())
                .put("p_visibility_days_before", values["visibility_days_before"]?.toIntOrNull() ?: 0)
                .put("p_auto_create", values["auto_create"].toBoolean())
                .put("p_allow_reschedule", values["allow_reschedule"].toBoolean())
                .put("p_allow_post_expiry_creation", values["allow_post_expiry_creation"].toBoolean())
                .put("p_post_expiry_creation_limit_days", values["post_expiry_creation_limit_days"]?.takeIf(String::isNotBlank)?.toIntOrNull() ?: JSONObject.NULL)
        }
        val rpc = when (kind) {
            AdminFormKind.USER -> "admin_update_profile"
            AdminFormKind.PROVIDER -> "admin_save_provider"
            AdminFormKind.PREFIX -> "admin_save_telecom_prefix"
            AdminFormKind.TARIFF -> "admin_save_provider_tariff"
            AdminFormKind.PACKAGE -> "admin_save_points_package"
            AdminFormKind.PAYMENT_METHOD -> "admin_save_payment_method"
            AdminFormKind.TASK_SETTINGS -> "admin_save_task_settings"
        }
        gateway.rpc(rpc, args)
    }

    private fun validateForm(kind: AdminFormKind, id: String?, parentId: String?, values: Map<String, String>) {
        fun required(key: String, label: String) { if (values[key].isNullOrBlank()) throw ContractException("أدخل $label.") }
        fun integer(key: String, label: String, min: Int, optional: Boolean = false) {
            val value = values[key].orEmpty().trim()
            if (optional && value.isBlank()) return
            val parsed = value.toIntOrNull() ?: throw ContractException("أدخل عددًا صحيحًا في $label.")
            if (parsed < min) throw ContractException("يجب ألا تقل قيمة $label عن $min.")
        }
        fun nonNegativeDecimal(key: String, label: String) {
            val amount = runCatching { BigDecimal(values[key].orEmpty().trim()) }.getOrElse { throw ContractException("أدخل مبلغًا صالحًا في $label.") }
            if (amount < BigDecimal.ZERO) throw ContractException("يجب ألا يكون $label سالبًا.")
        }
        when (kind) {
            AdminFormKind.USER -> { if (id.isNullOrBlank()) throw ContractException("لا يمكن إنشاء مستخدم دون مسار Auth إداري معتمد."); required("full_name", "الاسم الكامل"); required("username", "اسم المستخدم") }
            AdminFormKind.PROVIDER -> { required("code", "رمز الشركة"); required("name", "اسم الشركة"); parseJsonObject(values["operational_settings"].orEmpty().ifBlank { "{}" }, "إعدادات التشغيل"); validateRecordStatus(values["status"]) }
            AdminFormKind.PREFIX -> { if (values["provider_id"].isNullOrBlank() && parentId.isNullOrBlank()) throw ContractException("حدد الشركة."); required("prefix", "بادئة الهاتف"); integer("number_length", "طول الرقم", 1, optional = true); validateRecordStatus(values["status"]) }
            AdminFormKind.TARIFF -> { if (values["provider_id"].isNullOrBlank() && parentId.isNullOrBlank()) throw ContractException("حدد الشركة."); integer("points_per_day", "النقاط لكل يوم", 1); required("effective_from", "بداية السريان"); normalizeInstant(values["effective_from"].orEmpty()); values["effective_to"]?.takeIf(String::isNotBlank)?.let(::normalizeInstant); validateRecordStatus(values["status"]) }
            AdminFormKind.PACKAGE -> { required("name", "اسم الباقة"); integer("points_amount", "عدد النقاط", 1); nonNegativeDecimal("price_amount", "السعر"); required("currency", "العملة"); integer("display_order", "ترتيب العرض", 0); validateRecordStatus(values["status"]) }
            AdminFormKind.PAYMENT_METHOD -> { required("name", "اسم وسيلة الدفع"); parseJsonObject(values["payment_data"].orEmpty().ifBlank { "{}" }, "بيانات الدفع"); integer("display_order", "ترتيب العرض", 0); validateRecordStatus(values["status"]) }
            AdminFormKind.TASK_SETTINGS -> { if (values["provider_id"].isNullOrBlank() && parentId.isNullOrBlank()) throw ContractException("حدد الشركة."); integer("interval_days", "فاصل المهام", 1); nonNegativeDecimal("task_amount", "قيمة المهمة"); required("currency", "العملة"); integer("visibility_days_before", "أيام الظهور", 0); integer("post_expiry_creation_limit_days", "حد الإنشاء بعد الانتهاء", 0, optional = true) }
        }
    }

    private fun validateRecordStatus(value: String?) {
        if (value !in setOf("active", "inactive", "archived")) throw ContractException("اختر حالة سجل صالحة.")
    }

    private fun parseJsonObject(value: String, label: String): JSONObject = try {
        JSONObject(value).also { if (value.trim().firstOrNull() != '{') throw ContractException("$label يجب أن يكون كائن JSON.") }
    } catch (error: ContractException) { throw error } catch (_: Exception) { throw ContractException("$label بصيغة JSON غير صالحة.") }

    suspend fun sendNotification(targetType: String, targetId: String?, title: String, body: String) {
        if (targetType != "all" && targetId.isNullOrBlank()) throw ContractException("اختر مستهدفًا من نتائج البحث.")
        gateway.rpc("send_admin_notification", JSONObject().put("p_target_type", targetType)
            .put("p_target_id", targetId?.takeIf(String::isNotBlank) ?: JSONObject.NULL).put("p_title", title.trim()).put("p_body", body.trim()))
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
    private fun addProfile(row: JSONObject, profile: JSONObject?) {
        row.put("full_name", profile?.optString("full_name").orEmpty())
        row.put("username", profile?.optString("username").orEmpty())
        row.put("phone", profile?.optString("phone").orEmpty())
        row.put("email", profile?.optString("email").orEmpty())
        row.put("account_status", profile?.optString("account_status").orEmpty())
    }
    private fun JSONObject?.displayName(): String = this?.let { optString("full_name").ifBlank { optString("name") }.ifBlank { optString("username") }.ifBlank { optString("email") } }.orEmpty()
    private fun rowsById(rows: List<JSONObject>): Map<String, JSONObject> = rows.associateBy { it.optString("id") }
    private fun groupRows(rows: List<JSONObject>, key: String): Map<String, List<JSONObject>> = rows.groupBy { it.optString(key) }
    private fun JSONArray.toObjects(): List<JSONObject> = List(length()) { getJSONObject(it) }
}
