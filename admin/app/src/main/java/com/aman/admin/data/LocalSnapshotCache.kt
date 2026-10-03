package com.aman.admin.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray

/** Encrypted, app-private read cache. It is never treated as the authoritative write source. */
class LocalSnapshotCache(context: Context) {
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
    fun clear() { preferences.edit().clear().apply() }
}
