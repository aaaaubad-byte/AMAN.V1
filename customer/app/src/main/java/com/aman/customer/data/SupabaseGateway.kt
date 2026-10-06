package com.aman.customer.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aman.customer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class CustomerContractException(message: String) : Exception(message)
class CustomerBackendException(message: String) : Exception(message)
internal data class CustomerSession(val accessToken: String, val refreshToken: String, val userId: String, val expiresAt: Long)

/** Supabase Auth/PostgREST client. The Android app may only use the public anon/publishable key. */
class SupabaseGateway(context: Context) {
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val prefs by lazy {
        val master = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "aman_customer_session", master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun isConfigured() = baseUrl.startsWith("https://") && anonKey.isNotBlank() && !isPrivilegedKey(anonKey)
    fun configurationMessage() = if (isConfigured()) null else "خدمة الدخول غير مهيأة أو تحتوي مفتاحًا غير مسموح. استخدم SUPABASE_URL وSUPABASE_ANON_KEY فقط."
    fun currentUserId(): String? = readSession()?.userId
    fun hasSession() = currentUserId() != null

    suspend fun signIn(email: String, password: String) = withContext(Dispatchers.IO) {
        ensureConfigured()
        persist(fromAuth(JSONObject(raw("/auth/v1/token?grant_type=password", "POST", JSONObject()
            .put("email", email.trim()).put("password", password).toString()))))
    }

    suspend fun signUp(email: String, password: String, fullName: String) = withContext(Dispatchers.IO) {
        ensureConfigured()
        val versions = JSONObject(publicRpc("get_active_consent_versions", JSONObject()))
        val termsVersion = versions.optString("TERMS")
        val privacyVersion = versions.optString("PRIVACY")
        if (termsVersion.isBlank() || privacyVersion.isBlank()) {
            throw CustomerContractException("لا يمكن إنشاء الحساب قبل نشر نسخة الشروط والخصوصية المعتمدة.")
        }
        val payload = JSONObject().put("email", email.trim()).put("password", password)
            .put("data", JSONObject().put("full_name", fullName.trim())
                .put("accepted_terms", true).put("accepted_privacy", true)
                .put("terms_version", termsVersion).put("privacy_version", privacyVersion))
        val result = JSONObject(raw("/auth/v1/signup", "POST", payload.toString()))
        if (result.optString("access_token").isNotBlank()) persist(fromAuth(result))
        result
    }

    suspend fun sendPasswordRecovery(email: String) = withContext(Dispatchers.IO) {
        ensureConfigured()
        raw("/auth/v1/recover", "POST", JSONObject().put("email", email.trim())
            .put("redirect_to", "aman://auth/callback").toString())
    }

    suspend fun verifyRecoveryIdentity(customerId: String, email: String, name: String): Boolean = withContext(Dispatchers.IO) {
        publicRpc("verify_customer_recovery_id", JSONObject().put("p_customer_id", customerId.trim())
            .put("p_email", email.trim()).put("p_name", name.trim())).trim().equals("true", true)
    }

    suspend fun acceptRecoveryCallback(link: String): Boolean = withContext(Dispatchers.IO) {
        ensureConfigured()
        val uri = Uri.parse(link)
        val fragment = uri.encodedFragment.orEmpty()
        val fragmentUri = Uri.parse("https://callback.invalid/?$fragment")
        val type = uri.getQueryParameter("type") ?: fragmentUri.getQueryParameter("type")
        val access = uri.getQueryParameter("access_token") ?: fragmentUri.getQueryParameter("access_token")
        val refresh = uri.getQueryParameter("refresh_token") ?: fragmentUri.getQueryParameter("refresh_token")
        if (!type.equals("recovery", true) || access.isNullOrBlank() || refresh.isNullOrBlank()) return@withContext false
        val user = JSONObject(request("$baseUrl/auth/v1/user", "GET", null, access))
        val userId = user.optString("id")
        if (userId.isBlank()) return@withContext false
        val expires = (uri.getQueryParameter("expires_in") ?: fragmentUri.getQueryParameter("expires_in"))?.toLongOrNull() ?: 3600L
        persist(CustomerSession(access, refresh, userId, System.currentTimeMillis() + expires * 1000L))
        true
    }

    suspend fun updatePassword(newPassword: String) = withContext(Dispatchers.IO) {
        if (newPassword.length < 8) throw CustomerContractException("كلمة المرور الجديدة يجب ألا تقل عن 8 أحرف.")
        request("$baseUrl/auth/v1/user", "PATCH", JSONObject().put("password", newPassword).toString(), authenticatedToken())
    }

    suspend fun select(table: String, params: List<Pair<String, String>>): String = withContext(Dispatchers.IO) {
        val urlBuilder = "$baseUrl/rest/v1/$table".toHttpUrl().newBuilder()
        params.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value) }
        request(urlBuilder.build().toString(), "GET", null, authenticatedToken())
    }

    /** Only explicit V11 customer read/mutation RPCs may pass this gate. */
    suspend fun rpc(name: String, arguments: JSONObject): String = withContext(Dispatchers.IO) {
        val allowed = setOf(
            "create_customer_profile", "get_customer_screen_data", "update_customer_profile",
            "add_customer_number", "update_customer_number", "delete_customer_number",
            "submit_points_purchase", "activate_protection", "extend_protection", "renew_protection",
            "mark_notification_read", "mark_admin_message_read",
            "create_support_conversation", "send_support_message", "close_support_conversation",
            "complete_customer_password_recovery",
        )
        if (name !in allowed) throw CustomerContractException("عملية Backend غير معتمدة في عقد عميل AMAN V11: $name")
        request("$baseUrl/rest/v1/rpc/$name", "POST", arguments.toString(), authenticatedToken())
    }

    suspend fun publicRpc(name: String, arguments: JSONObject): String = withContext(Dispatchers.IO) {
        if (name !in setOf("get_public_content", "get_active_consent_versions", "verify_customer_recovery_id"))
            throw CustomerContractException("Public RPC غير معتمد: $name")
        request("$baseUrl/rest/v1/rpc/$name", "POST", arguments.toString(), null)
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val session = readSession()
        try {
            if (session != null && isConfigured()) request("$baseUrl/auth/v1/logout", "POST", "{}", session.accessToken)
        } finally {
            prefs.edit().clear().apply()
        }
    }

    suspend fun clearSession() = withContext(Dispatchers.IO) { prefs.edit().clear().apply() }

    private suspend fun authenticatedToken(): String {
        val old = readSession() ?: throw CustomerContractException("لا توجد جلسة دخول؛ سجّل الدخول مجددًا.")
        if (old.expiresAt > System.currentTimeMillis() + 60_000L) return old.accessToken
        return try {
            val refreshed = fromAuth(
                JSONObject(raw("/auth/v1/token?grant_type=refresh_token", "POST", JSONObject()
                    .put("refresh_token", old.refreshToken).toString())), old,
            )
            persist(refreshed)
            refreshed.accessToken
        } catch (_: Exception) {
            prefs.edit().clear().apply()
            throw CustomerContractException("انتهت جلسة الدخول وتعذر تجديدها. أعد تسجيل الدخول.")
        }
    }

    private suspend fun raw(path: String, method: String, body: String?): String =
        request("$baseUrl/${path.removePrefix("/")}", method, body, null)

    private suspend fun request(url: String, method: String, body: String?, bearer: String?, prefer: String? = null): String = withContext(Dispatchers.IO) {
        ensureConfigured()
        val builder = Request.Builder().url(url).header("apikey", anonKey).header("Accept", "application/json")
        if (!bearer.isNullOrBlank()) builder.header("Authorization", "Bearer $bearer")
        if (prefer != null) builder.header("Prefer", prefer)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post((body ?: "{}").toRequestBody(jsonType))
            "PATCH" -> builder.patch((body ?: "{}").toRequestBody(jsonType))
            else -> throw CustomerContractException("HTTP method غير مدعوم: $method")
        }
        try {
            http.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val error = runCatching {
                        JSONObject(text).let { it.optString("message").ifBlank { it.optString("msg") }.ifBlank { it.optString("error_description") } }
                    }.getOrNull().orEmpty()
                    throw CustomerBackendException(error.ifBlank { "استجابة الخادم HTTP ${response.code}" })
                }
                text
            }
        } catch (e: IOException) {
            throw e
        }
    }

    private fun fromAuth(obj: JSONObject, previous: CustomerSession? = null): CustomerSession {
        val userId = obj.optJSONObject("user")?.optString("id").takeUnless { it.isNullOrBlank() } ?: previous?.userId.orEmpty()
        if (userId.isBlank() || obj.optString("access_token").isBlank()) throw CustomerContractException("استجابة المصادقة لم تتضمن جلسة مستخدم قابلة للاستخدام.")
        return CustomerSession(
            obj.getString("access_token"), obj.optString("refresh_token", previous?.refreshToken.orEmpty()), userId,
            System.currentTimeMillis() + obj.optLong("expires_in", 3600L) * 1000L,
        )
    }

    private fun persist(session: CustomerSession) {
        prefs.edit().putString("access", session.accessToken).putString("refresh", session.refreshToken)
            .putString("uid", session.userId).putLong("expires", session.expiresAt).apply()
    }

    private fun readSession(): CustomerSession? {
        val access = prefs.getString("access", null) ?: return null
        val refresh = prefs.getString("refresh", null) ?: return null
        val userId = prefs.getString("uid", null) ?: return null
        return CustomerSession(access, refresh, userId, prefs.getLong("expires", 0L))
    }

    private fun ensureConfigured() {
        if (!isConfigured()) throw CustomerContractException(configurationMessage() ?: "Supabase غير مهيأ")
    }

    private fun isPrivilegedKey(key: String): Boolean {
        if (key.contains("service_role", true) || key.startsWith("sb_secret_", true)) return true
        val tokenPart = key.split('.').getOrNull(1) ?: return false
        return runCatching {
            val json = String(Base64.decode(tokenPart, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
            JSONObject(json).optString("role").equals("service_role", true)
        }.getOrDefault(false)
    }
}
