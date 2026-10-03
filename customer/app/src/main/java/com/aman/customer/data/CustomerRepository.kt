package com.aman.customer.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class CustomerRepository(private val context: Context, val gateway: SupabaseGateway, private val cache: CustomerCache, private val outbox: CustomerOutbox = CustomerOutbox(context)) {
    fun isOnline(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return manager.activeNetwork?.let { manager.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } == true
    }

    suspend fun load(screen: CustomerScreen, search: String = ""): CustomerScreenData = withContext(Dispatchers.IO) {
        val userId = gateway.currentUserId() ?: throw CustomerContractException("يلزم تسجيل الدخول لقراءة بيانات الحساب.")
        val result = JSONObject()
        val previousPayload = cache.get(userId, screen)?.optJSONObject("payload")
        val problems = mutableListOf<String>()
        suspend fun fetch(key: String, table: String, vararg filters: Pair<String, String>) {
            try { result.put(key, JSONArray(gateway.select(table, filters.toList()))) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                problems += "$key: تعذر تحميل البيانات من الخدمة"
                result.put(key, previousPayload?.optJSONArray(key) ?: JSONArray())
            }
        }
        when (screen) {
            CustomerScreen.HOME -> {
                fetch("profile", "profiles", "id" to "eq.$userId", "select" to "id,full_name,username")
                fetch("balance", "point_balances", "user_id" to "eq.$userId", "select" to "balance_points,updated_at")
                fetch("operations", "operations", "user_id" to "eq.$userId", "select" to "id,operation_type,status,points_delta,money_amount,created_at,phone_number_id", "order" to "created_at.desc", "limit" to "5")
                fetch("unread", "notifications", "user_id" to "eq.$userId", "read_at" to "is.null", "select" to "id", "limit" to "100")
            }
            CustomerScreen.ACTIVE_NUMBERS, CustomerScreen.ACTIVATE, CustomerScreen.EXTEND -> {
                fetch("protections", "protections", "user_id" to "eq.$userId", "select" to "*,phone_numbers(id,phone_e164,normalized_phone),telecom_providers(name,short_name)", "order" to "expires_at.desc", "limit" to "200")
                fetch("balance", "point_balances", "user_id" to "eq.$userId", "select" to "balance_points")
                fetch("subscriber", "subscribers", "user_id" to "eq.$userId", "status" to "eq.active", "select" to "id,status", "limit" to "1")
                if (screen == CustomerScreen.ACTIVATE) {
                    fetch("numbers", "customer_numbers", "user_id" to "eq.$userId", "status" to "eq.active", "select" to "*,phone_numbers(id,phone_e164,normalized_phone,provider_id,telecom_providers(name,short_name))", "order" to "added_at.desc", "limit" to "300")
                    fetch("tariffs", "provider_tariffs", "status" to "eq.active", "select" to "provider_id,points_per_day,effective_from,effective_to", "order" to "effective_from.desc", "limit" to "300")
                }
            }
            CustomerScreen.INACTIVE_NUMBERS -> {
                fetch("numbers", "customer_numbers", "user_id" to "eq.$userId", "status" to "eq.active", "select" to "*,phone_numbers(id,phone_e164,normalized_phone,provider_id,telecom_providers(name,short_name))", "order" to "added_at.desc", "limit" to "300")
                fetch("protections", "protections", "user_id" to "eq.$userId", "status" to "eq.active", "select" to "phone_number_id", "limit" to "300")
            }
            CustomerScreen.POINTS -> {
                fetch("packages", "points_packages", "status" to "eq.active", "select" to "id,name,points_amount,price_amount,currency,display_order", "order" to "display_order.asc", "limit" to "100")
                fetch("methods", "payment_methods", "status" to "eq.active", "select" to "id,name,payment_data,instructions", "order" to "name.asc", "limit" to "100")
                fetch("requests", "points_purchase_requests", "user_id" to "eq.$userId", "select" to "id,request_number,points_amount_snapshot,price_amount_snapshot,currency_snapshot,status,submitted_at,rejection_reason", "order" to "submitted_at.desc", "limit" to "50")
                result.put("outbox", JSONArray().also { array -> outbox.entries(userId).forEach(array::put) })
            }
            CustomerScreen.OPERATIONS -> {
                fetch("operations", "operations", "user_id" to "eq.$userId", "select" to "id,operation_type,status,points_delta,money_amount,phone_number_id,metadata,created_at", "order" to "created_at.desc", "limit" to "300")
                fetch("ledger", "point_ledger", "user_id" to "eq.$userId", "select" to "id,entry_type,amount,balance_after,description,created_at,reference_type,reference_id", "order" to "created_at.desc", "limit" to "300")
                fetch("purchases", "points_purchase_requests", "user_id" to "eq.$userId", "select" to "id,request_number,status,points_amount_snapshot,price_amount_snapshot,currency_snapshot,submitted_at", "order" to "submitted_at.desc", "limit" to "100")
            }
            CustomerScreen.ADD_NUMBER -> {
                fetch("numbers", "customer_numbers", "user_id" to "eq.$userId", "select" to "*,phone_numbers(id,phone_e164,normalized_phone,provider_id,telecom_providers(name,short_name))", "order" to "added_at.desc", "limit" to "300")
                fetch("prefixes", "telecom_prefixes", "status" to "eq.active", "select" to "id,prefix,provider_id,status,telecom_providers(name,short_name)", "limit" to "500")
            }
            CustomerScreen.ADMIN_ALERTS -> fetch("alerts", "notifications", "user_id" to "eq.$userId", "notification_type" to "eq.admin_alert", "select" to "id,title,content,read_at,created_at", "order" to "created_at.desc", "limit" to "200")
            CustomerScreen.SUPPORT -> {
                fetch("threads", "support_threads", "user_id" to "eq.$userId", "select" to "id,subject,status,last_reply_at,created_at,updated_at", "order" to "updated_at.desc", "limit" to "100")
                val messages = JSONArray()
                result.optJSONArray("threads")?.objects().orEmpty().take(30).forEach { thread ->
                    val threadId = thread.optString("id")
                    if (threadId.isNotBlank()) try {
                        JSONArray(gateway.select("support_messages", listOf("thread_id" to "eq.$threadId", "select" to "id,thread_id,sender_id,body,created_at", "order" to "created_at.asc", "limit" to "200"))).objects().forEach(messages::put)
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { problems += "messages: تعذر تحميل بعض الرسائل من الخدمة" }
                }
                result.put("messages", messages)
            }
            CustomerScreen.NOTIFICATIONS -> fetch("notifications", "notifications", "user_id" to "eq.$userId", "select" to "id,notification_type,title,content,read_at,reference_type,reference_id,created_at", "order" to "created_at.desc", "limit" to "200")
            CustomerScreen.REPORTS -> {
                fetch("balance", "point_balances", "user_id" to "eq.$userId", "select" to "balance_points,updated_at")
                fetch("ledger", "point_ledger", "user_id" to "eq.$userId", "select" to "id,entry_type,amount,balance_after,created_at", "order" to "created_at.desc", "limit" to "500")
                fetch("numbers", "customer_numbers", "user_id" to "eq.$userId", "select" to "id,status,added_at,phone_number_id", "limit" to "500")
                fetch("protections", "protections", "user_id" to "eq.$userId", "select" to "id,status,started_at,expires_at,duration_days,total_points_snapshot", "limit" to "500")
                fetch("operations", "operations", "user_id" to "eq.$userId", "select" to "id,operation_type,status,points_delta,money_amount,created_at", "order" to "created_at.desc", "limit" to "500")
                fetch("purchases", "points_purchase_requests", "user_id" to "eq.$userId", "select" to "id,status,points_amount_snapshot,price_amount_snapshot,currency_snapshot,submitted_at", "order" to "submitted_at.desc", "limit" to "500")
            }
            CustomerScreen.ACCOUNT -> {
                fetch("profile", "profiles", "id" to "eq.$userId", "select" to "id,full_name,username,phone,email,account_status,created_at,updated_at", "limit" to "1")
                fetch("subscriber", "subscribers", "user_id" to "eq.$userId", "select" to "id,status,became_subscriber_at,created_at", "limit" to "1")
                fetch("balance", "point_balances", "user_id" to "eq.$userId", "select" to "balance_points")
            }
            CustomerScreen.SEARCH -> {
                val term = search.trim()
                if (term.length >= 2) {
                    val pattern = "%$term%"
                    fetch("numbers", "customer_numbers", "user_id" to "eq.$userId", "select" to "*,phone_numbers(id,phone_e164,normalized_phone)", "limit" to "200")
                    fetch("operations", "operations", "user_id" to "eq.$userId", "select" to "id,operation_type,status,points_delta,created_at", "order" to "created_at.desc", "limit" to "200")
                    fetch("purchases", "points_purchase_requests", "user_id" to "eq.$userId", "select" to "id,request_number,status,points_amount_snapshot,submitted_at", "order" to "submitted_at.desc", "limit" to "200")
                    fetch("alerts", "notifications", "user_id" to "eq.$userId", "notification_type" to "eq.admin_alert", "select" to "id,title,content,created_at", "limit" to "200")
                    fetch("notifications", "notifications", "user_id" to "eq.$userId", "select" to "id,title,content,created_at", "limit" to "200")
                    result.put("term", pattern)
                }
            }
            CustomerScreen.ABOUT -> Unit
        }
        if (problems.isEmpty()) {
            val userScoped = JSONObject().put("user", userId).put("screen", screen.id).put("payload", result)
            cache.put(userId, screen, userScoped)
        }
        build(screen, result, problems, if (problems.isEmpty()) System.currentTimeMillis() else cache.syncedAt(userId, screen))
    }

    fun cached(screen: CustomerScreen): CustomerScreenData? {
        val user = gateway.currentUserId() ?: return null
        val envelope = cache.get(user, screen) ?: return null
        if (envelope.optString("user") != user) return null
        val payload = envelope.optJSONObject("payload") ?: return null
        return build(screen, payload, emptyList(), cache.syncedAt(user, screen))
    }

    suspend fun submitPurchase(packageId: String, methodId: String, reference: String, idempotencyKey: String) {
        gateway.rpc("submit_points_purchase", JSONObject().put("p_package_id", packageId).put("p_payment_method_id", methodId)
            .put("p_payment_reference", reference.trim()).put("p_idempotency_key", idempotencyKey))
    }
    suspend fun queuePurchase(packageId: String, methodId: String, reference: String, idempotencyKey: String) = withContext(Dispatchers.IO) {
        val userId = gateway.currentUserId() ?: throw CustomerContractException("يلزم تسجيل الدخول لحفظ الطلب.")
        outbox.enqueue(userId, packageId, methodId, reference, idempotencyKey)
    }
    fun queuedPurchases(userId: String) = outbox.entries(userId)
    fun removeQueuedPurchase(userId: String, idempotencyKey: String) = outbox.remove(userId, idempotencyKey)
    fun markQueuedPurchaseNeedsAttention(userId: String, idempotencyKey: String) = outbox.markNeedsAttention(userId, idempotencyKey)
    suspend fun activate(phoneId: String, days: Int) {
        gateway.rpc("activate_protection", JSONObject().put("p_phone_number_id", phoneId).put("p_duration_days", days)
            .put("p_operation_key", UUID.randomUUID().toString()))
    }
    suspend fun extend(protectionId: String, days: Int) {
        gateway.rpc("extend_protection", JSONObject().put("p_protection_id", protectionId).put("p_days", days)
            .put("p_operation_key", UUID.randomUUID().toString()))
    }
    suspend fun markRead(id: String) = gateway.markNotificationRead(id)

    private fun build(screen: CustomerScreen, payload: JSONObject, notes: List<String>, loadedAt: Long = System.currentTimeMillis()): CustomerScreenData {
        val related = mutableMapOf<String, JSONArray>()
        val mainKey = when (screen) {
            CustomerScreen.HOME -> "operations"
            CustomerScreen.ACTIVE_NUMBERS, CustomerScreen.ACTIVATE, CustomerScreen.EXTEND -> "protections"
            CustomerScreen.INACTIVE_NUMBERS, CustomerScreen.ADD_NUMBER -> "numbers"
            CustomerScreen.POINTS -> "requests"
            CustomerScreen.OPERATIONS -> "operations"
            CustomerScreen.ADMIN_ALERTS -> "alerts"
            CustomerScreen.SUPPORT -> "threads"
            CustomerScreen.NOTIFICATIONS -> "notifications"
            CustomerScreen.REPORTS -> "ledger"
            CustomerScreen.ACCOUNT -> "profile"
            CustomerScreen.SEARCH -> "numbers"
            CustomerScreen.ABOUT -> ""
        }
        val knownKeys = listOf("profile", "balance", "operations", "unread", "protections", "subscriber", "numbers", "tariffs", "packages", "methods", "requests", "ledger", "purchases", "prefixes", "alerts", "threads", "messages", "notifications", "outbox")
        knownKeys.forEach { key -> payload.optJSONArray(key)?.let { related[key] = it } }
        val rows = payload.optJSONArray(mainKey)?.objects().orEmpty()
        var records = rows.map { row ->
            val src = when (screen) {
                CustomerScreen.HOME, CustomerScreen.OPERATIONS -> "operations"
                CustomerScreen.ACTIVE_NUMBERS, CustomerScreen.ACTIVATE, CustomerScreen.EXTEND -> "protections"
                CustomerScreen.INACTIVE_NUMBERS, CustomerScreen.ADD_NUMBER -> "customer_numbers"
                CustomerScreen.POINTS -> "points_purchase_requests"
                CustomerScreen.ADMIN_ALERTS, CustomerScreen.NOTIFICATIONS -> "notifications"
                CustomerScreen.SUPPORT -> "support_threads"
                CustomerScreen.REPORTS -> "point_ledger"
                CustomerScreen.ACCOUNT -> "profiles"
                CustomerScreen.SEARCH -> "customer_numbers"
                CustomerScreen.ABOUT -> ""
            }
            toCustomerRecord(src, row)
        }
        if (screen == CustomerScreen.ACTIVE_NUMBERS) records = records.filter { it.raw.optString("status") == "active" }
        if (screen == CustomerScreen.INACTIVE_NUMBERS) {
            val protectedIds = payload.optJSONArray("protections")?.objects().orEmpty().map { it.optString("phone_number_id") }.toSet()
            records = records.filter { it.raw.optJSONObject("phone_numbers")?.optString("id").orEmpty() !in protectedIds }
        }
        if (screen == CustomerScreen.SEARCH) {
            val term = payload.optString("term").removePrefix("%").removeSuffix("%")
            val datasets = listOf("numbers" to "customer_numbers", "operations" to "operations", "purchases" to "points_purchase_requests", "alerts" to "notifications", "notifications" to "notifications")
            records = datasets.flatMap { (key, table) -> payload.optJSONArray(key)?.objects().orEmpty().map { toCustomerRecord(table, it) } }
                .filter { (it.title + it.subtitle + it.details.joinToString { p -> p.second }).contains(term, ignoreCase = true) }
        }
        return CustomerScreenData(screen, records, related, notes, loadedAt)
    }
}
