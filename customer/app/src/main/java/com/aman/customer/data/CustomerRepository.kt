package com.aman.customer.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

class CustomerRepository(
    private val context: Context,
    val gateway: SupabaseGateway,
    private val cache: CustomerCache,
) {
    private val operationKeys = CustomerOperationKeyStore(context)

    fun isOnline(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return manager.activeNetwork?.let {
            manager.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } == true
    }

    suspend fun load(screen: CustomerScreen, search: String = "", pageSize: Int = 50, pageOffset: Int = 0): CustomerScreenData = withContext(Dispatchers.IO) {
        val userId = gateway.currentUserId() ?: if (screen == CustomerScreen.ABOUT) "public" else throw CustomerContractException("يلزم تسجيل الدخول لقراءة بيانات الحساب.")
        if (screen in setOf(CustomerScreen.INITIALIZATION, CustomerScreen.LOGIN, CustomerScreen.SIGN_UP, CustomerScreen.RECOVERY)) {
            return@withContext CustomerScreenData(screen)
        }
        val payload = try {
            if (screen == CustomerScreen.ABOUT && userId == "public") {
                JSONObject(gateway.publicRpc("get_public_content", JSONObject().put("p_content_key", "ALL")))
            } else JSONObject(gateway.rpc("get_customer_screen_data", JSONObject()
                .put("p_screen_id", screen.id)
                .put("p_query", search.trim().take(100))
                .put("p_page_size", pageSize.coerceIn(1, 100))
                .put("p_page_offset", pageOffset.coerceAtLeast(0))))
        } catch (e: CancellationException) {
            throw e
        }
        if (screen in setOf(CustomerScreen.ACTIVE_NUMBERS, CustomerScreen.EXPIRED_NUMBERS, CustomerScreen.EXTEND, CustomerScreen.RENEW)) {
            enrichProtectionWarnings(payload)
        }
        if (userId != "public" && pageOffset == 0) cache.put(userId, screen, JSONObject().put("user", userId).put("screen", screen.id).put("payload", payload))
        build(screen, payload, search = search)
    }

    fun cached(screen: CustomerScreen): CustomerScreenData? {
        val userId = gateway.currentUserId() ?: return null
        val envelope = cache.get(userId, screen) ?: return null
        if (envelope.optString("user") != userId || envelope.optString("screen") != screen.id) return null
        return envelope.optJSONObject("payload")?.let { build(screen, it, loadedAt = cache.syncedAt(userId, screen)) }
    }

    fun purchaseIdempotencyKey(packageId: String, methodId: String, reference: String): String {
        val userId = gateway.currentUserId() ?: throw CustomerContractException("يلزم تسجيل الدخول لإنشاء طلب شراء.")
        return operationKeys.getOrCreate(userId, "submit-points-purchase", "$packageId|$methodId|${reference.trim()}")
    }

    suspend fun submitPurchase(packageId: String, methodId: String, reference: String, idempotencyKey: String) {
        if (packageId.isBlank() || methodId.isBlank() || reference.isBlank() || idempotencyKey.isBlank()) {
            throw CustomerContractException("اختر باقة ووسيلة دفع وأدخل مرجع التحويل.")
        }
        gateway.rpc("submit_points_purchase", JSONObject()
            .put("p_package_id", packageId)
            .put("p_payment_method_id", methodId)
            .put("p_transfer_reference", reference.trim())
            .put("p_idempotency_key", idempotencyKey))
    }

    suspend fun completePasswordRecovery() {
        gateway.rpc("complete_customer_password_recovery", JSONObject())
    }

    suspend fun addCustomerNumber(phone: String) {
        val normalized = normalizePhoneE164(phone) ?: throw CustomerContractException("أدخل رقمًا دوليًا بصيغة + ورقم من 7 إلى 15 خانة.")
        val userId = gateway.currentUserId() ?: throw CustomerContractException("يلزم تسجيل الدخول لإضافة رقم.")
        val key = operationKeys.getOrCreate(userId, "add-customer-number", normalized)
        gateway.rpc("add_customer_number", JSONObject().put("p_phone", normalized).put("p_idempotency_key", key))
    }

    suspend fun updateCustomerNumber(customerNumberId: String, phone: String) {
        val normalized = normalizePhoneE164(phone) ?: throw CustomerContractException("أدخل رقمًا دوليًا صالحًا بصيغة +.")
        if (customerNumberId.isBlank()) throw CustomerContractException("معرّف الرقم غير متاح؛ حدّث القائمة.")
        val userId = gateway.currentUserId() ?: throw CustomerContractException("يلزم تسجيل الدخول.")
        val key = operationKeys.getOrCreate(userId, "update-customer-number", "$customerNumberId|$normalized")
        gateway.rpc("update_customer_number", JSONObject()
            .put("p_customer_number_id", customerNumberId)
            .put("p_phone", normalized)
            .put("p_idempotency_key", key))
    }

    suspend fun deleteCustomerNumber(customerNumberId: String) {
        if (customerNumberId.isBlank()) throw CustomerContractException("معرّف الرقم غير متاح؛ حدّث القائمة.")
        val userId = gateway.currentUserId() ?: throw CustomerContractException("يلزم تسجيل الدخول.")
        val key = operationKeys.getOrCreate(userId, "delete-customer-number", customerNumberId)
        gateway.rpc("delete_customer_number", JSONObject()
            .put("p_customer_number_id", customerNumberId)
            .put("p_idempotency_key", key))
    }

    suspend fun updateCustomerProfile(name: String) {
        if (name.isBlank()) throw CustomerContractException("أدخل اسمًا صحيحًا.")
        val userId = gateway.currentUserId() ?: throw CustomerContractException("يلزم تسجيل الدخول.")
        val key = operationKeys.getOrCreate(userId, "update-customer-profile", name.trim())
        gateway.rpc("update_customer_profile", JSONObject().put("p_name", name.trim()).put("p_idempotency_key", key))
    }

    suspend fun createSupportConversation(subject: String, body: String) {
        if (subject.isBlank() || body.isBlank()) throw CustomerContractException("أدخل موضوع المحادثة ونص الرسالة.")
        val userId = gateway.currentUserId() ?: throw CustomerContractException("يلزم تسجيل الدخول.")
        val key = operationKeys.getOrCreate(userId, "create-support-conversation", "${subject.trim()}|${body.trim()}")
        gateway.rpc("create_support_conversation", JSONObject()
            .put("p_subject", subject.trim())
            .put("p_body", body.trim())
            .put("p_idempotency_key", key))
    }

    suspend fun sendSupportMessage(conversationId: String, body: String) {
        if (conversationId.isBlank() || body.isBlank()) throw CustomerContractException("اختر محادثة مفتوحة واكتب الرسالة.")
        val userId = gateway.currentUserId() ?: throw CustomerContractException("يلزم تسجيل الدخول.")
        val key = operationKeys.getOrCreate(userId, "send-support-message", "$conversationId|${body.trim()}")
        gateway.rpc("send_support_message", JSONObject()
            .put("p_conversation_id", conversationId)
            .put("p_body", body.trim())
            .put("p_idempotency_key", key))
    }

    suspend fun closeSupportConversation(conversationId: String) {
        if (conversationId.isBlank()) throw CustomerContractException("اختر محادثة صالحة.")
        val userId = gateway.currentUserId() ?: throw CustomerContractException("يلزم تسجيل الدخول.")
        val key = operationKeys.getOrCreate(userId, "close-support-conversation", conversationId)
        gateway.rpc("close_support_conversation", JSONObject()
            .put("p_conversation_id", conversationId)
            .put("p_idempotency_key", key))
    }

    suspend fun markNotificationRead(notificationId: String) {
        if (notificationId.isBlank()) throw CustomerContractException("معرّف الإشعار غير متاح.")
        gateway.rpc("mark_notification_read", JSONObject().put("p_notification_id", notificationId))
    }

    suspend fun markAdminMessageRead(messageId: String) {
        if (messageId.isBlank()) throw CustomerContractException("معرّف الرسالة غير متاح.")
        gateway.rpc("mark_admin_message_read", JSONObject().put("p_message_id", messageId))
    }

    suspend fun activate(customerNumberId: String, tariffId: String, units: Int, idempotencyKey: String) {
        if (customerNumberId.isBlank() || tariffId.isBlank() || units !in 1..120 || idempotencyKey.isBlank()) throw CustomerContractException("اختر رقمًا وتعرفة وعدد وحدات صحيحًا.")
        gateway.rpc("activate_protection", JSONObject().put("p_customer_number_id", customerNumberId)
            .put("p_tariff_id", tariffId).put("p_units", units).put("p_idempotency_key", idempotencyKey))
    }

    suspend fun extend(protectionPeriodId: String, tariffId: String, units: Int, idempotencyKey: String) {
        if (protectionPeriodId.isBlank() || tariffId.isBlank() || units !in 1..120 || idempotencyKey.isBlank()) throw CustomerContractException("اختر حماية نشطة وتعرفة وعدد وحدات صحيحًا.")
        gateway.rpc("extend_protection", JSONObject().put("p_protection_period_id", protectionPeriodId)
            .put("p_tariff_id", tariffId).put("p_units", units).put("p_idempotency_key", idempotencyKey))
    }

    suspend fun renew(protectionPeriodId: String, tariffId: String, units: Int, idempotencyKey: String) {
        if (protectionPeriodId.isBlank() || tariffId.isBlank() || units !in 1..120 || idempotencyKey.isBlank()) throw CustomerContractException("اختر حماية منتهية وتعرفة وعدد وحدات صحيحًا.")
        gateway.rpc("renew_protection", JSONObject().put("p_protection_period_id", protectionPeriodId)
            .put("p_tariff_id", tariffId).put("p_units", units).put("p_idempotency_key", idempotencyKey))
    }

    fun mutationKey(operation: String, identity: String, days: Int): String {
        val userId = gateway.currentUserId() ?: throw CustomerContractException("يلزم تسجيل الدخول.")
        return operationKeys.getOrCreate(userId, operation, "$identity|$days")
    }

    suspend fun loadSupportMessages(conversationId: String): JSONArray {
        val screen = load(CustomerScreen.SUPPORT)
        return screen.related.array("messages").let { all ->
            JSONArray().also { out -> all.objects().filter { it.optString("conversation_id") == conversationId }.forEach(out::put) }
        }
    }

    private fun build(screen: CustomerScreen, payload: JSONObject, loadedAt: Long = System.currentTimeMillis(), search: String = ""): CustomerScreenData {
        val keys = listOf(
            "profile", "balance", "operations", "numbers", "candidates", "protections", "tariffs", "packages", "methods",
            "purchases", "ledger", "notifications", "threads", "messages", "admin_messages", "content", "maintenance", "prefixes", "extensions",
        )
        val related = keys.mapNotNull { key -> payload.optJSONArray(key)?.let { key to it } }.toMap()
        val mainKey = when (screen) {
            CustomerScreen.HOME -> "operations"
            CustomerScreen.ADD_NUMBER, CustomerScreen.ADDED_NUMBERS -> "numbers"
            CustomerScreen.BUY_POINTS -> "purchases"
            CustomerScreen.POINTS_HISTORY -> "ledger"
            CustomerScreen.ACTIVE_NUMBERS, CustomerScreen.EXPIRED_NUMBERS -> "protections"
            CustomerScreen.ACTIVATE -> "candidates"
            CustomerScreen.EXTEND, CustomerScreen.RENEW -> "protections"
            CustomerScreen.NOTIFICATIONS -> "notifications"
            CustomerScreen.SUPPORT -> "threads"
            CustomerScreen.SEARCH -> ""
            CustomerScreen.REPORTS -> "operations"
            CustomerScreen.ACCOUNT -> "profile"
            CustomerScreen.ABOUT -> "content"
            else -> ""
        }
        val records = if (screen == CustomerScreen.SEARCH) {
            val query = search.trim()
            listOf("numbers", "purchases", "ledger", "protections", "notifications", "operations")
                .flatMap { key -> related.array(key).objects().filter { it.toString().contains(query, true) }.map { toCustomerRecord(key, it) } }
        } else related.array(mainKey).objects().map { toCustomerRecord(mainKey, it) }
        return CustomerScreenData(screen = screen, records = records, related = related, loadedAt = loadedAt,
            hasMore = payload.optJSONObject("page_info")?.optBoolean("has_more", false) ?: false)
    }

    private suspend fun enrichProtectionWarnings(payload: JSONObject) {
        val rows = payload.optJSONArray("protections") ?: return
        val protectionIds = rows.objects().map { it.optString("id") }.filter(String::isNotBlank).distinct()
        if (protectionIds.isEmpty()) return
        val companyIds = rows.objects().map { it.optString("telecom_company_id") }
            .filter(String::isNotBlank).distinct()
        val companies = if (companyIds.isEmpty()) emptyMap() else {
            val filter = "in.(${companyIds.joinToString(",")})"
            JSONArray(gateway.select("telecom_company", listOf(
                "select" to "id,extension_warning_days",
                "id" to filter,
            ))).objects().associateBy { it.optString("id") }
        }
        val extensionFilter = "in.(${protectionIds.joinToString(",")})"
        val extensions = JSONArray(gateway.select("protection_extension", listOf(
            "select" to "id,protection_period_id,days_added,points_cost,created_at,units_added",
            "protection_period_id" to extensionFilter,
            "order" to "created_at.asc",
            "limit" to "1000",
        ))).objects()
        val histories = extensions.groupBy { it.optString("protection_period_id") }
        val now = Instant.now()
        rows.objects().forEach { row ->
            val warningDays = companies[row.optString("telecom_company_id")]?.optInt("extension_warning_days", 0) ?: 0
            val remainingDays = runCatching {
                Duration.between(now, Instant.parse(row.optString("end_at"))).toDays().coerceAtLeast(0)
            }.getOrNull()
            row.put("extension_warning_days", warningDays)
            row.put("remaining_days", remainingDays ?: JSONObject.NULL)
            row.put("needs_extension", warningDays > 0 && remainingDays != null && remainingDays <= warningDays)
            val history = histories[row.optString("id")].orEmpty()
            val end = runCatching { Instant.parse(row.optString("end_at")) }.getOrNull()
            if (end != null && history.isNotEmpty()) {
                var priorEnd = end.minus(Duration.ofDays(history.sumOf { it.optLong("days_added", 0L) }))
                history.forEach { extension ->
                    val nextEnd = priorEnd.plus(Duration.ofDays(extension.optLong("days_added", 0L)))
                    extension.put("previous_end_at", priorEnd.toString()).put("new_end_at", nextEnd.toString())
                    priorEnd = nextEnd
                }
            }
            row.put("extension_history", JSONArray().apply { history.forEach(::put) })
        }
        payload.put("extensions", JSONArray().apply { extensions.forEach(::put) })
    }
}
