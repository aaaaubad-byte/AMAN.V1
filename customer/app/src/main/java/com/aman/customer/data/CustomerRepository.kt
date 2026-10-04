package com.aman.customer.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class CustomerRepository(private val context: Context, val gateway: SupabaseGateway, private val cache: CustomerCache, private val outbox: CustomerOutbox = CustomerOutbox(context)) {
    private val operationKeys = CustomerOperationKeyStore(context)

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
            try {
                val rows = JSONArray(gateway.select(table, filters.toList()))
                if (table == "protections") rows.objects().forEach(::normalizeProtection)
                if (table == "activated_numbers") rows.objects().forEach(::normalizeActivation)
                result.put(key, rows)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                problems += "$key: تعذر تحميل البيانات من العقد الكنسي"
                result.put(key, previousPayload?.optJSONArray(key) ?: JSONArray())
            }
        }
        val protectionSelect = "id,activated_number_id,subscriber_id,provider_id,tariff_id,status,started_at,expires_at,duration_days,points_per_day_snapshot,total_points_snapshot,created_at,updated_at,activated_numbers!inner(id,activation_code,customer_number_id,customer_numbers!inner(user_id,phone_number_id,phone_numbers(id,phone_e164,normalized_phone)),subscribers(user_id)),telecom_providers(name,short_name)"
        val numberSelect = "id,added_number_code,user_id,phone_number_id,status,added_at,updated_at,phone_numbers(id,phone_e164,normalized_phone,provider_id,telecom_providers(name,short_name))"
        when (screen) {
            CustomerScreen.HOME -> {
                fetch("profile", "profiles", "id" to "eq.$userId", "select" to "id,user_code,full_name,username")
                fetch("balance", "point_balances", "user_id" to "eq.$userId", "select" to "balance_points,updated_at")
                fetch("operations", "operations", "user_id" to "eq.$userId", "select" to "id,operation_type,status,points_delta,money_amount,created_at,phone_number_id", "order" to "created_at.desc", "limit" to "5")
                try { result.put("task_summaries", JSONArray(gateway.rpc("get_customer_task_summaries", JSONObject()))) } catch (e: CancellationException) { throw e } catch (_: Exception) { problems += "task_summaries: ملخص المهام غير متاح حاليًا" }
                fetch("unread", "system_notifications", "user_id" to "eq.$userId", "is_read" to "eq.false", "select" to "id", "limit" to "100")
                fetch("admin_alerts", "system_notifications", "user_id" to "eq.$userId", "type" to "eq.admin_alert", "is_read" to "eq.false", "select" to "id,title,created_at", "order" to "created_at.desc", "limit" to "100")
                fetch("protections", "protections", "activated_numbers.customer_numbers.user_id" to "eq.$userId", "select" to "id,status,expires_at,$protectionSelect", "order" to "expires_at.desc", "limit" to "200")
            }
            CustomerScreen.ACTIVE_NUMBERS, CustomerScreen.ACTIVATE, CustomerScreen.EXTEND -> {
                fetch("protections", "protections", "activated_numbers.customer_numbers.user_id" to "eq.$userId", "select" to protectionSelect, "order" to "expires_at.desc", "limit" to "200")
                fetch("balance", "point_balances", "user_id" to "eq.$userId", "select" to "balance_points")
                fetch("subscriber", "subscribers", "user_id" to "eq.$userId", "status" to "eq.active", "select" to "id,subscriber_code,status", "limit" to "1")
                try { result.put("task_summaries", JSONArray(gateway.rpc("get_customer_task_summaries", JSONObject()))) } catch (e: CancellationException) { throw e } catch (_: Exception) { problems += "task_summaries: ملخص المهام غير متاح حاليًا" }
                if (screen == CustomerScreen.ACTIVATE) {
                    fetch("numbers", "activated_numbers", "customer_numbers.user_id" to "eq.$userId", "select" to "id,activation_code,customer_number_id,subscriber_id,status,activated_at,customer_numbers!inner(id,added_number_code,user_id,phone_number_id,status,added_at,phone_numbers(id,phone_e164,normalized_phone,provider_id,telecom_providers(name,short_name)))", "order" to "activated_at.desc.nullslast", "limit" to "300")
                    fetch("tariffs", "provider_tariffs", "status" to "eq.active", "select" to "provider_id,points_per_day,effective_from,effective_to,status", "order" to "effective_from.desc", "limit" to "300")
                }
            }
            CustomerScreen.INACTIVE_NUMBERS -> {
                fetch("numbers", "customer_numbers", "user_id" to "eq.$userId", "status" to "eq.active", "select" to numberSelect, "order" to "added_at.desc", "limit" to "300")
                fetch("protections", "protections", "activated_numbers.customer_numbers.user_id" to "eq.$userId", "status" to "eq.active", "select" to "activated_number_id,$protectionSelect", "limit" to "300")
            }
            CustomerScreen.POINTS -> {
                fetch("balance", "point_balances", "user_id" to "eq.$userId", "select" to "balance_points,updated_at")
                fetch("packages", "points_packages", "status" to "eq.active", "select" to "id,name,points_amount,price_amount,currency,display_order", "order" to "display_order.asc", "limit" to "100")
                fetch("methods", "payment_methods", "status" to "eq.active", "select" to "id,name,type,account_identifier,instructions,display_order", "order" to "display_order.asc", "limit" to "100")
                fetch("requests", "points_purchase_requests", "user_id" to "eq.$userId", "select" to "id,request_number,package_id,payment_method_id,points_amount_snapshot,price_amount_snapshot,currency_snapshot,payment_method_name_snapshot,payment_reference,status,submitted_at,rejection_reason", "order" to "submitted_at.desc", "limit" to "50")
                result.put("outbox", JSONArray().also { array -> outbox.entries(userId).forEach(array::put) })
            }
            CustomerScreen.OPERATIONS -> {
                fetch("operations", "operations", "user_id" to "eq.$userId", "select" to "id,operation_type,status,reference_type,reference_id,points_delta,money_amount,phone_number_id,metadata,created_at", "order" to "created_at.desc", "limit" to "300")
                fetch("ledger", "point_ledger", "user_id" to "eq.$userId", "select" to "id,entry_type,direction,amount_points,balance_after,description,created_at,reference_type,reference_id", "order" to "created_at.desc", "limit" to "300")
                fetch("purchases", "points_purchase_requests", "user_id" to "eq.$userId", "select" to "id,request_number,status,points_amount_snapshot,price_amount_snapshot,currency_snapshot,submitted_at", "order" to "submitted_at.desc", "limit" to "100")
                fetch("numbers", "customer_numbers", "user_id" to "eq.$userId", "select" to numberSelect, "order" to "added_at.desc", "limit" to "300")
            }
            CustomerScreen.ADD_NUMBER -> {
                fetch("numbers", "customer_numbers", "user_id" to "eq.$userId", "status" to "eq.active", "select" to numberSelect, "order" to "added_at.desc", "limit" to "300")
                fetch("activation_history", "activated_numbers", "customer_numbers.user_id" to "eq.$userId", "select" to "id,customer_number_id,activation_code,status", "limit" to "500")
                fetch("prefixes", "telecom_prefixes", "status" to "eq.active", "select" to "id,prefix,provider_id,status,telecom_providers(name,short_name)", "limit" to "500")
                fetch("protections", "protections", "activated_numbers.customer_numbers.user_id" to "eq.$userId", "status" to "eq.active", "select" to "activated_number_id,$protectionSelect", "limit" to "300")
            }
            CustomerScreen.ADMIN_ALERTS -> fetch("alerts", "system_notifications", "user_id" to "eq.$userId", "type" to "eq.admin_alert", "select" to "id,title,body,is_read,read_at,created_at", "order" to "created_at.desc", "limit" to "200")
            CustomerScreen.SUPPORT -> {
                fetch("threads", "support_threads", "user_id" to "eq.$userId", "select" to "id,subject,status,created_at,updated_at", "order" to "updated_at.desc", "limit" to "100")
                val messages = JSONArray()
                result.optJSONArray("threads")?.objects().orEmpty().take(30).forEach { thread ->
                    val threadId = thread.optString("id")
                    if (threadId.isNotBlank()) try {
                        JSONArray(gateway.select("support_messages", listOf("thread_id" to "eq.$threadId", "select" to "id,thread_id,sender_id,sender_type,body,created_at", "order" to "created_at.asc", "limit" to "200"))).objects().forEach(messages::put)
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { problems += "messages: تعذر تحميل بعض الرسائل" }
                }
                result.put("messages", messages)
            }
            CustomerScreen.NOTIFICATIONS -> fetch("notifications", "system_notifications", "user_id" to "eq.$userId", "select" to "id,type,title,body,is_read,read_at,reference_type,reference_id,created_at", "order" to "created_at.desc", "limit" to "200")
            CustomerScreen.REPORTS -> {
                fetch("balance", "point_balances", "user_id" to "eq.$userId", "select" to "balance_points,updated_at")
                fetch("ledger", "point_ledger", "user_id" to "eq.$userId", "select" to "id,entry_type,direction,amount_points,balance_after,created_at,description", "order" to "created_at.desc", "limit" to "500")
                fetch("numbers", "customer_numbers", "user_id" to "eq.$userId", "select" to numberSelect, "limit" to "500")
                fetch("protections", "protections", "activated_numbers.customer_numbers.user_id" to "eq.$userId", "select" to protectionSelect, "limit" to "500")
                fetch("operations", "operations", "user_id" to "eq.$userId", "select" to "id,operation_type,status,points_delta,money_amount,phone_number_id,metadata,created_at", "order" to "created_at.desc", "limit" to "500")
                fetch("purchases", "points_purchase_requests", "user_id" to "eq.$userId", "select" to "id,status,points_amount_snapshot,price_amount_snapshot,currency_snapshot,submitted_at", "order" to "submitted_at.desc", "limit" to "500")
            }
            CustomerScreen.ACCOUNT -> {
                fetch("profile", "profiles", "id" to "eq.$userId", "select" to "id,user_code,full_name,username,phone,email,account_status,created_at,updated_at", "limit" to "1")
                fetch("subscriber", "subscribers", "user_id" to "eq.$userId", "select" to "id,subscriber_code,status,became_subscriber_at,created_at", "limit" to "1")
                fetch("balance", "point_balances", "user_id" to "eq.$userId", "select" to "balance_points")
            }
            CustomerScreen.SEARCH -> {
                val term = search.trim().filter { it.isLetterOrDigit() || it in " ._+-@" }.take(80)
                if (term.length >= 2) {
                    val pattern = "*$term*"
                    fetch("numbers", "customer_numbers", "user_id" to "eq.$userId", "select" to "id,added_number_code,user_id,phone_number_id,status,added_at,phone_numbers!inner(id,phone_e164,normalized_phone)", "phone_numbers.phone_e164" to "ilike.$pattern", "limit" to "200")
                    fetch("operations", "operations", "user_id" to "eq.$userId", "select" to "id,operation_type,status,points_delta,money_amount,created_at,metadata", "or" to "(operation_type.ilike.$pattern,status.ilike.$pattern)", "order" to "created_at.desc", "limit" to "200")
                    fetch("purchases", "points_purchase_requests", "user_id" to "eq.$userId", "select" to "id,request_number,status,points_amount_snapshot,price_amount_snapshot,currency_snapshot,submitted_at", "or" to "(request_number.ilike.$pattern,status.ilike.$pattern)", "order" to "submitted_at.desc", "limit" to "200")
                    fetch("notifications", "system_notifications", "user_id" to "eq.$userId", "select" to "id,type,title,body,created_at", "or" to "(title.ilike.$pattern,body.ilike.$pattern)", "order" to "created_at.desc", "limit" to "200")
                    result.put("term", term)
                }
            }
            CustomerScreen.ABOUT -> Unit
        }
        if (problems.isEmpty()) cache.put(userId, screen, JSONObject().put("user", userId).put("screen", screen.id).put("payload", result))
        build(screen, result, problems, if (problems.isEmpty()) System.currentTimeMillis() else cache.syncedAt(userId, screen))
    }

    fun cached(screen: CustomerScreen): CustomerScreenData? {
        val user = gateway.currentUserId() ?: return null
        val envelope = cache.get(user, screen) ?: return null
        if (envelope.optString("user") != user) return null
        return envelope.optJSONObject("payload")?.let { build(screen, it, emptyList(), cache.syncedAt(user, screen)) }
    }

    fun purchaseIdempotencyKey(packageId: String, methodId: String, reference: String): String {
        val userId = gateway.currentUserId() ?: throw CustomerContractException("يلزم تسجيل الدخول لإنشاء طلب شراء.")
        return operationKeys.getOrCreate(userId, "purchase", "$packageId|$methodId|${reference.trim()}")
    }
    suspend fun submitPurchase(packageId: String, methodId: String, reference: String, idempotencyKey: String) {
        if (gateway.currentUserId() == null) throw CustomerContractException("يلزم تسجيل الدخول لإرسال طلب شراء.")
        gateway.rpc("submit_points_purchase_request", JSONObject().put("p_package_id", packageId).put("p_payment_method_id", methodId).put("p_payment_reference", reference.trim()).put("p_idempotency_key", idempotencyKey))
    }
    suspend fun cancelPurchase(requestId: String) {
        if (requestId.isBlank()) throw CustomerContractException("معرّف طلب الشراء غير متاح.")
        gateway.rpc("cancel_points_purchase", JSONObject().put("p_request_id", requestId))
    }
    suspend fun resubmitPurchase(requestId: String, packageId: String, methodId: String, reference: String) {
        if (requestId.isBlank() || packageId.isBlank() || methodId.isBlank() || reference.isBlank()) throw CustomerContractException("اختر الباقة ووسيلة الدفع وأدخل مرجع التحويل.")
        gateway.rpc("resubmit_points_purchase", JSONObject().put("p_request_id", requestId).put("p_package_id", packageId).put("p_payment_method_id", methodId).put("p_payment_reference", reference.trim()))
    }
    suspend fun queuePurchase(packageId: String, methodId: String, reference: String, idempotencyKey: String) = withContext(Dispatchers.IO) {
        val userId = gateway.currentUserId() ?: throw CustomerContractException("يلزم تسجيل الدخول لحفظ الطلب.")
        outbox.enqueue(userId, packageId, methodId, reference, idempotencyKey)
    }
    fun queuedPurchases(userId: String) = outbox.entries(userId)
    fun removeQueuedPurchase(userId: String, idempotencyKey: String) = outbox.remove(userId, idempotencyKey)
    fun markQueuedPurchaseNeedsAttention(userId: String, idempotencyKey: String) = outbox.markNeedsAttention(userId, idempotencyKey)

    suspend fun addCustomerNumber(phoneE164: String) {
        val normalized = normalizePhoneE164(phoneE164) ?: throw CustomerContractException("أدخل رقمًا دوليًا بصيغة + ورقم من 7 إلى 15 خانة.")
        gateway.rpc("add_customer_number", JSONObject().put("p_phone_e164", normalized))
    }
    suspend fun updateCustomerProfile(fullName: String, username: String, phone: String) {
        if (fullName.isBlank() || username.isBlank()) throw CustomerContractException("أدخل اسم العميل واسم المستخدم.")
        if (phone.isNotBlank() && normalizePhoneE164(phone) == null) throw CustomerContractException("أدخل رقم الهاتف بصيغة دولية صحيحة أو اتركه فارغًا.")
        gateway.rpc("update_customer_profile", JSONObject().put("p_full_name", fullName.trim()).put("p_username", username.trim()).put("p_phone", if (phone.isBlank()) JSONObject.NULL else phone.trim()))
    }
    suspend fun updateCustomerNumber(customerNumberId: String, phoneE164: String) {
        val normalized = normalizePhoneE164(phoneE164) ?: throw CustomerContractException("أدخل رقمًا دوليًا بصيغة + ورقم من 7 إلى 15 خانة.")
        if (customerNumberId.isBlank()) throw CustomerContractException("معرّف الرقم غير متاح؛ حدّث القائمة.")
        gateway.rpc("update_customer_number", JSONObject().put("p_customer_number_id", customerNumberId).put("p_phone_e164", normalized))
    }
    suspend fun archiveCustomerNumber(customerNumberId: String) {
        if (customerNumberId.isBlank()) throw CustomerContractException("معرّف الرقم غير متاح؛ حدّث القائمة.")
        gateway.rpc("archive_customer_number", JSONObject().put("p_customer_number_id", customerNumberId))
    }

    suspend fun createSupportThread(subject: String, body: String) {
        if (subject.isBlank() || body.isBlank()) throw CustomerContractException("أدخل موضوع المحادثة ونص الرسالة.")
        gateway.rpc("create_support_thread", JSONObject().put("p_subject", subject.trim()).put("p_body", body.trim()))
    }
    suspend fun sendSupportMessage(threadId: String, body: String) {
        if (threadId.isBlank() || body.isBlank()) throw CustomerContractException("اختر محادثة مفتوحة واكتب الرسالة.")
        gateway.rpc("send_support_message", JSONObject().put("p_thread_id", threadId).put("p_body", body.trim()))
    }
    suspend fun loadSupportMessages(threadId: String): JSONArray = JSONArray(gateway.select("support_messages", listOf("thread_id" to "eq.$threadId", "select" to "id,thread_id,sender_id,sender_type,body,created_at", "order" to "created_at.asc", "limit" to "300")))

    suspend fun activate(activatedNumberId: String, days: Int) {
        val userId = gateway.currentUserId() ?: throw CustomerContractException("يلزم تسجيل الدخول للتفعيل.")
        if (activatedNumberId.isBlank() || days <= 0) throw CustomerContractException("اختر رقمًا ومدة صحيحة.")
        val payload = "$activatedNumberId|$days"; val key = operationKeys.getOrCreate(userId, "activation", payload)
        gateway.rpc("activate_protection", JSONObject().put("p_activated_number_id", activatedNumberId).put("p_duration_days", days).put("p_idempotency_key", key))
        operationKeys.clear(userId, "activation", payload)
    }
    suspend fun extend(protectionId: String, days: Int) {
        val userId = gateway.currentUserId() ?: throw CustomerContractException("يلزم تسجيل الدخول للتمديد.")
        if (protectionId.isBlank() || days <= 0) throw CustomerContractException("اختر حماية ومدة صحيحة.")
        val payload = "$protectionId|$days"; val key = operationKeys.getOrCreate(userId, "extension", payload)
        gateway.rpc("extend_protection", JSONObject().put("p_protection_id", protectionId).put("p_extension_days", days).put("p_idempotency_key", key))
        operationKeys.clear(userId, "extension", payload)
    }
    suspend fun renew(activatedNumberId: String, days: Int) {
        val userId = gateway.currentUserId() ?: throw CustomerContractException("يلزم تسجيل الدخول للتجديد.")
        if (activatedNumberId.isBlank() || days <= 0) throw CustomerContractException("اختر رقمًا ومدة تجديد صحيحة.")
        val payload = "$activatedNumberId|$days"; val key = operationKeys.getOrCreate(userId, "renewal", payload)
        gateway.rpc("renew_protection", JSONObject().put("p_activated_number_id", activatedNumberId).put("p_duration_days", days).put("p_confirmed", true).put("p_idempotency_key", key))
        operationKeys.clear(userId, "renewal", payload)
    }
    suspend fun markRead(id: String) {
        if (id.isBlank()) throw CustomerContractException("معرّف الإشعار غير متاح.")
        gateway.rpc("mark_notification_read", JSONObject().put("p_notification_id", id))
    }

    private fun normalizeProtection(row: JSONObject) {
        val activation = firstObject(row.opt("activated_numbers"))
        val customerNumber = firstObject(activation?.opt("customer_numbers"))
        val phone = firstObject(customerNumber?.opt("phone_numbers"))
        if (activation != null) row.put("activation_code", activation.optString("activation_code"))
        if (customerNumber != null) {
            row.put("user_id", customerNumber.optString("user_id")); row.put("phone_number_id", customerNumber.optString("phone_number_id"))
        }
        if (phone != null) row.put("phone_numbers", phone)
    }
    private fun normalizeActivation(row: JSONObject) {
        val customerNumber = firstObject(row.opt("customer_numbers"))
        val phone = firstObject(customerNumber?.opt("phone_numbers"))
        if (customerNumber != null) {
            row.put("user_id", customerNumber.optString("user_id"))
            row.put("phone_number_id", customerNumber.optString("phone_number_id"))
            row.put("added_number_code", customerNumber.optString("added_number_code"))
        }
        if (phone != null) row.put("phone_numbers", phone)
    }
    private fun firstObject(value: Any?): JSONObject? = when (value) { is JSONObject -> value; is JSONArray -> value.optJSONObject(0); else -> null }

    private fun build(screen: CustomerScreen, payload: JSONObject, notes: List<String>, loadedAt: Long = System.currentTimeMillis()): CustomerScreenData {
        val related = mutableMapOf<String, JSONArray>(); val mainKey = when (screen) {
            CustomerScreen.HOME -> "operations"; CustomerScreen.ACTIVE_NUMBERS, CustomerScreen.ACTIVATE, CustomerScreen.EXTEND -> "protections"
            CustomerScreen.INACTIVE_NUMBERS, CustomerScreen.ADD_NUMBER -> "numbers"; CustomerScreen.POINTS -> "requests"; CustomerScreen.OPERATIONS -> "operations"
            CustomerScreen.ADMIN_ALERTS -> "alerts"; CustomerScreen.SUPPORT -> "threads"; CustomerScreen.NOTIFICATIONS -> "notifications"; CustomerScreen.REPORTS -> "ledger"
            CustomerScreen.ACCOUNT -> "profile"; CustomerScreen.SEARCH -> "numbers"; CustomerScreen.ABOUT -> ""
        }
        val knownKeys = listOf("profile","balance","operations","unread","admin_alerts","protections","subscriber","numbers","tariffs","packages","methods","requests","ledger","purchases","prefixes","alerts","threads","messages","notifications","outbox","task_summaries","activation_history")
        knownKeys.forEach { key -> payload.optJSONArray(key)?.let { related[key] = it } }
        var records = payload.optJSONArray(mainKey)?.objects().orEmpty().map { row ->
            val source = when (screen) { CustomerScreen.HOME, CustomerScreen.OPERATIONS -> "operations"; CustomerScreen.ACTIVE_NUMBERS, CustomerScreen.ACTIVATE, CustomerScreen.EXTEND -> "protections"; CustomerScreen.INACTIVE_NUMBERS, CustomerScreen.ADD_NUMBER -> "customer_numbers"; CustomerScreen.POINTS -> "points_purchase_requests"; CustomerScreen.ADMIN_ALERTS, CustomerScreen.NOTIFICATIONS -> "system_notifications"; CustomerScreen.SUPPORT -> "support_threads"; CustomerScreen.REPORTS -> "point_ledger"; CustomerScreen.ACCOUNT -> "profiles"; CustomerScreen.SEARCH -> "customer_numbers"; CustomerScreen.ABOUT -> "" }
            toCustomerRecord(source, row)
        }
        if (screen == CustomerScreen.ACTIVE_NUMBERS) records = records.filter { it.raw.optString("status") == "active" }
        if (screen == CustomerScreen.INACTIVE_NUMBERS) {
            val protectedIds = payload.optJSONArray("protections")?.objects().orEmpty().map { it.optString("phone_number_id") }.toSet()
            records = records.filter { it.raw.optJSONObject("phone_numbers")?.optString("id").orEmpty() !in protectedIds }
        }
        if (screen == CustomerScreen.SEARCH) {
            val term = payload.optString("term"); val datasets = listOf("numbers" to "customer_numbers", "operations" to "operations", "purchases" to "points_purchase_requests", "notifications" to "system_notifications")
            records = datasets.flatMap { (key, table) -> payload.optJSONArray(key)?.objects().orEmpty().map { toCustomerRecord(table, it) } }.filter { (it.title + it.subtitle + it.details.joinToString { p -> p.second }).contains(term, ignoreCase = true) }
        }
        return CustomerScreenData(screen, records, related, notes, loadedAt)
    }
}
