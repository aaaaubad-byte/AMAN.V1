package com.aman.admin.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.UUID

data class LoadedRecords(val rows: List<JSONObject>, val offlineSnapshot: Boolean = false, val cachedAtMillis: Long? = null, val warningMessage: String? = null)

class AdminRepository(private val gateway: SupabaseGateway, private val cache: LocalSnapshotCache) {
    fun configurationMessage(): String? = gateway.configurationMessage()
    fun currentUserId(): String? = gateway.currentUserId()
    fun canUseCachedAdminSession(): Boolean = gateway.canUseCachedAdminSession()
    suspend fun signIn(email: String, password: String) = gateway.signIn(email, password)
    suspend fun verifyAdmin() = gateway.isAdmin()
    suspend fun signOut() { try { gateway.signOut() } finally { cache.clear() } }
    suspend fun clearLocalSession() { gateway.clearLocalSession(); cache.clear() }

    suspend fun load(section: AdminSection, query: String = ""): LoadedRecords {
        val cacheKey = section.name
        var warningMessage: String? = null
        try {
            val response = when (section) {
                AdminSection.HOME -> loadDashboard().also { warningMessage = it.second }.first
                AdminSection.REPORTS -> gateway.select("operations", "select=*&order=created_at.desc&limit=100")
                AdminSection.SEARCH -> searchAll(query).also { warningMessage = it.second }.first
                AdminSection.ACCOUNT -> {
                    val uid = gateway.currentUserId() ?: throw ContractException("معرّف المستخدم غير متاح في الجلسة.")
                    gateway.select("profiles", "select=*&id=eq.$uid&limit=1")
                }
                else -> {
                    val table = section.table ?: throw ContractException("لا يوجد مصدر بيانات معرّف لهذا القسم.")
                    val ordered = table in setOf("profiles", "subscribers", "customer_numbers", "protections", "points_purchase_requests", "payment_tasks", "operations", "notifications")
                    gateway.select(table, "select=*${if (ordered) "&order=created_at.desc" else ""}&limit=100")
                }
            }
            val rows = JSONArray(response).toObjects()
            if (section != AdminSection.SEARCH) enrich(section, rows)
            if (section != AdminSection.SEARCH) cache.put(cacheKey, JSONArray().apply { rows.forEach(::put) })
            return LoadedRecords(rows, warningMessage = warningMessage)
        } catch (error: IOException) {
            if (section == AdminSection.SEARCH) throw error
            val saved = cache.get(cacheKey) ?: throw error
            return LoadedRecords(saved.toObjects(), offlineSnapshot = true, cachedAtMillis = cache.updatedAt(cacheKey))
        }
    }

    private suspend fun loadDashboard(): Pair<String, String?> {
        val merged = JSONArray()
        val sources = listOf(
            "operations" to "select=*&order=created_at.desc&limit=20",
            "points_purchase_requests" to "select=*&status=eq.pending&order=submitted_at.desc&limit=20",
            "payment_tasks" to "select=*&status=eq.open&order=due_at.asc&limit=20",
        )
        val failures = mutableListOf<String>()
        val phones = rowsById(optionalRows("phone_numbers"))
        val providers = rowsById(optionalRows("telecom_providers"))
        sources.forEach { (table, query) ->
            try {
                val records = JSONArray(gateway.select(table, query))
                for (index in 0 until records.length()) {
                    val row = records.getJSONObject(index)
                    row.put("_aman_source_table", table)
                    if (table == "payment_tasks") {
                        val phone = phones[row.optString("phone_number_id")]
                        row.put("phone_e164", phone?.optString("phone_e164").orEmpty())
                        row.put("provider_name", providers[row.optString("provider_id")].displayName())
                        val dueAt = row.optString("due_at")
                        val overdue = runCatching { Instant.parse(dueAt).isBefore(Instant.now()) }.getOrDefault(false)
                        row.put("task_attention", if (overdue) "متأخرة" else "قادمة")
                    }
                    merged.put(row)
                }
            } catch (error: Exception) {
                failures += "$table: ${error.message}"
            }
        }
        if (merged.length() == 0 && failures.isNotEmpty()) throw ContractException("تعذر تحميل مصادر لوحة الإدارة: ${failures.joinToString("؛ ")}")
        val warning = failures.takeIf { it.isNotEmpty() }?.joinToString("؛ ") { "مصدر غير متاح — $it" }
        return merged.toString() to warning
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
                rows.forEach { row ->
                    val phone = phones[row.optString("phone_number_id")]
                    row.put("phone_e164", phone?.optString("phone_e164").orEmpty())
                    row.put("normalized_phone", phone?.optString("normalized_phone").orEmpty())
                    row.put("provider_name", providers[phone?.optString("provider_id")].displayName())
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
                    val nextTask = tasks[row.optString("id")].orEmpty().filter { it.optString("status") == "open" }.minByOrNull { it.optString("due_at") }
                    row.put("phone_e164", phone?.optString("phone_e164").orEmpty())
                    row.put("customer_name", profiles[row.optString("user_id")].displayName())
                    row.put("provider_name", providers[row.optString("provider_id")].displayName())
                    row.put("tariff_points_per_day", tariff?.optInt("points_per_day") ?: JSONObject.NULL)
                    row.put("task_plan_interval_days", plan?.optInt("interval_days") ?: JSONObject.NULL)
                    row.put("next_task_due", nextTask?.optString("due_at").orEmpty())
                    row.put("next_task_status", nextTask?.optString("status").orEmpty())
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
                }
            }
            AdminSection.PROVIDERS -> {
                val prefixes = groupRows(optionalRows("telecom_prefixes"), "provider_id")
                val tariffs = groupRows(optionalRows("provider_tariffs"), "provider_id")
                rows.forEach { row ->
                    val id = row.optString("id")
                    row.put("active_prefixes", prefixes[id].orEmpty().count { it.optString("status") == "active" })
                    row.put("prefix_data", JSONArray().apply { prefixes[id].orEmpty().forEach { put(it.optString("prefix")) } })
                    val activeTariff = tariffs[id].orEmpty().filter { it.optString("status") == "active" }.maxByOrNull { it.optString("effective_from") }
                    row.put("points_per_day", activeTariff?.optInt("points_per_day") ?: JSONObject.NULL)
                }
            }
            else -> Unit
        }
    }

    private suspend fun optionalRows(table: String): List<JSONObject> = try {
        JSONArray(gateway.select(table, "select=*&limit=1000")).toObjects()
    } catch (_: BackendResponseException) {
        emptyList()
    }

    private suspend fun searchAll(@Suppress("UNUSED_PARAMETER") query: String): Pair<String, String?> {
        val tables = listOf("profiles", "subscribers", "customer_numbers", "protections", "points_purchase_requests", "operations", "payment_tasks", "telecom_providers")
        val merged = JSONArray()
        val issues = mutableListOf<String>()
        for (table in tables) {
            try {
                val rows = JSONArray(gateway.select(table, "select=*&limit=100"))
                for (i in 0 until rows.length()) rows.getJSONObject(i).also { it.put("_aman_source_table", table); merged.put(it) }
            } catch (error: Exception) { issues += "$table: ${error.message}" }
        }
        if (merged.length() == 0 && issues.isNotEmpty()) throw ContractException("تعذر البحث في مصادر البيانات المتاحة: ${issues.joinToString("؛ ")}")
        val warning = issues.takeIf { it.isNotEmpty() }?.joinToString("؛ ") { "مصدر بحث غير متاح — $it" }
        return merged.toString() to warning
    }

    suspend fun perform(section: AdminSection, action: AdminMutation, row: JSONObject, actionInput: String? = null) {
        val id = row.optString("id").takeIf { it.isNotBlank() } ?: throw ContractException("السجل المحدد لا يحتوي معرّفًا صالحًا.")
        when (section to action) {
            AdminSection.PURCHASES to AdminMutation.APPROVE_PURCHASE -> gateway.rpc(action.rpcName,
                JSONObject().put("p_request_id", id).put("p_idempotency_key", UUID.randomUUID().toString()))
            AdminSection.PURCHASES to AdminMutation.REJECT_PURCHASE -> gateway.rpc(action.rpcName,
                JSONObject().put("p_request_id", id).put("p_reason", actionInput?.trim().orEmpty())
                    .put("p_idempotency_key", UUID.randomUUID().toString()))
            AdminSection.PAYMENT_TASKS to AdminMutation.EXECUTE_PAYMENT_TASK -> gateway.rpc(action.rpcName,
                JSONObject().put("p_task_id", id).put("p_execution_key", UUID.randomUUID().toString())
                    .put("p_external_reference", actionInput?.trim().orEmpty()))
            else -> throw ContractException("لا يوجد ربط RPC لهذا الإجراء في مخطط قاعدة البيانات الحالي.")
        }
    }

    private fun addProfile(row: JSONObject, profile: JSONObject?) {
        row.put("full_name", profile?.optString("full_name").orEmpty())
        row.put("username", profile?.optString("username").orEmpty())
        row.put("phone", profile?.optString("phone").orEmpty())
        row.put("email", profile?.optString("email").orEmpty())
        row.put("account_status", profile?.optString("account_status").orEmpty())
    }
    private fun JSONObject?.displayName(): String = this?.let { optString("full_name").ifBlank { optString("name") }.ifBlank { optString("username") } }.orEmpty()
    private fun rowsById(rows: List<JSONObject>): Map<String, JSONObject> = rows.associateBy { it.optString("id") }
    private fun groupRows(rows: List<JSONObject>, key: String): Map<String, List<JSONObject>> = rows.groupBy { it.optString(key) }
    private fun JSONArray.toObjects(): List<JSONObject> = List(length()) { getJSONObject(it) }
}
