package com.aman.customer.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

class CustomerCache(context: Context) {
    private val preferences by lazy {
        val master = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(context, "aman_customer_cache", master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    fun get(userId: String, screen: CustomerScreen): JSONObject? = runCatching {
        val raw = preferences.getString(key(userId, screen), null) ?: return null
        JSONObject(raw)
    }.getOrNull()
    fun put(userId: String, screen: CustomerScreen, content: JSONObject) {
        preferences.edit().putString(key(userId, screen), content.toString()).putLong("synced:$userId:${screen.id}", System.currentTimeMillis()).apply()
    }
    fun syncedAt(userId: String, screen: CustomerScreen) = preferences.getLong("synced:$userId:${screen.id}", 0L)
    fun clearUser(userId: String) {
        val editor = preferences.edit()
        CustomerScreen.entries.forEach { editor.remove(key(userId, it)); editor.remove("synced:$userId:${it.id}") }
        editor.apply()
    }
    private fun key(userId: String, screen: CustomerScreen) = "payload:$userId:${screen.id}"
}
