package com.aman.customer.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Encrypted, per-user outbox for the idempotent purchase-request submission RPC only. */
class CustomerOutbox(context: Context) {
    private val preferences by lazy {
        val master = MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(context.applicationContext, "aman_customer_outbox", master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }

    @Synchronized
    fun entries(userId: String): List<JSONObject> = runCatching {
        val stored = preferences.getString(key(userId), null) ?: return emptyList()
        val json = JSONArray(stored)
        (0 until json.length()).mapNotNull { json.optJSONObject(it) }
    }.getOrDefault(emptyList())

    @Synchronized
    fun enqueue(userId: String, packageId: String, methodId: String, reference: String, idempotencyKey: String) {
        val existing = entries(userId)
        if (existing.any { it.optString("idempotency_key") == idempotencyKey ||
                (it.optString("package_id") == packageId && it.optString("payment_method_id") == methodId &&
                    it.optString("payment_reference").trim() == reference.trim()) }) return
        val updated = JSONArray()
        existing.forEach(updated::put)
        updated.put(JSONObject().put("package_id", packageId).put("payment_method_id", methodId)
            .put("payment_reference", reference.trim()).put("idempotency_key", idempotencyKey)
            .put("queued_at", java.time.Instant.now().toString()).put("status", "queued"))
        preferences.edit().putString(key(userId), updated.toString()).apply()
    }

    @Synchronized
    fun remove(userId: String, idempotencyKey: String) = update(userId) { row -> row.optString("idempotency_key") != idempotencyKey }

    @Synchronized
    fun markNeedsAttention(userId: String, idempotencyKey: String) = update(userId) { row ->
        if (row.optString("idempotency_key") == idempotencyKey) row.put("status", "needs_attention")
        true
    }

    private fun update(userId: String, keep: (JSONObject) -> Boolean) {
        val updated = JSONArray()
        entries(userId).filter(keep).forEach(updated::put)
        preferences.edit().putString(key(userId), updated.toString()).apply()
    }

    private fun key(userId: String) = "outbox:$userId"
}
