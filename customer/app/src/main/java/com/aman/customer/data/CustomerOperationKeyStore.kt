package com.aman.customer.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.util.UUID

/** Keeps an operation key across process/network retries until the server has acknowledged success. */
class CustomerOperationKeyStore(context: Context) {
    private val preferences by lazy {
        val appContext = context.applicationContext
        val master = MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(appContext, "aman_customer_operation_keys", master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }

    @Synchronized
    fun getOrCreate(userId: String, operation: String, payload: String): String {
        val storageKey = storageKey(userId, operation, payload)
        return preferences.getString(storageKey, null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString(storageKey, it).apply()
        }
    }

    @Synchronized
    fun clear(userId: String, operation: String, payload: String) {
        preferences.edit().remove(storageKey(userId, operation, payload)).apply()
    }

    fun clearUser(userId: String) {
        val prefix = "intent:$userId:"
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        editor.apply()
    }

    private fun storageKey(userId: String, operation: String, payload: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return "intent:$userId:$operation:$digest"
    }
}
