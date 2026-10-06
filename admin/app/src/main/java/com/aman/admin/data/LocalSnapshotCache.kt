package com.aman.admin.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import java.security.MessageDigest
import java.util.UUID

/** Encrypted, app-private read cache. It is never treated as the authoritative write source. */
class LocalSnapshotCache(context: Context) {
    private val idempotencyLock = Any()
    private val preferences by lazy {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "aman_admin_read_cache",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun put(key: String, records: JSONArray) {
        preferences.edit().putString("records:$key", records.toString())
            .putLong("updated:$key", System.currentTimeMillis()).apply()
    }

    fun get(key: String): JSONArray? = preferences.getString("records:$key", null)?.let(::JSONArray)
    fun updatedAt(key: String): Long? = preferences.getLong("updated:$key", -1L).takeIf { it >= 0L }

    fun getOrCreateIdempotencyKey(scope: String, requestIdentity: String): String = synchronized(idempotencyLock) {
        val storageKey = idempotencyStorageKey(scope, requestIdentity)
        preferences.getString(storageKey, null) ?: UUID.randomUUID().toString().also { key ->
            check(preferences.edit().putString(storageKey, key).commit()) { "تعذر حفظ مفتاح منع تكرار العملية." }
        }
    }

    fun completeIdempotencyKey(scope: String, requestIdentity: String, key: String) = synchronized(idempotencyLock) {
        val storageKey = idempotencyStorageKey(scope, requestIdentity)
        if (preferences.getString(storageKey, null) == key) preferences.edit().remove(storageKey).commit()
    }

    private fun idempotencyStorageKey(scope: String, requestIdentity: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(requestIdentity.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return "idempotency:$scope:$digest"
    }

    fun clear() { preferences.edit().clear().apply() }
}
