package com.aman.admin.data

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aman.admin.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class BackendResponseException(val statusCode: Int, message: String) : Exception(message)

internal data class AmanSession(val accessToken: String, val refreshToken: String, val userId: String, val expiresAtMillis: Long)

/** Supabase Auth/PostgREST client. The only accepted API key is the public anon key. */
class SupabaseGateway(context: Context) {
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val preferences by lazy {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(context, "aman_admin_session", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }

    fun isConfigured(): Boolean = baseUrl.startsWith("https://") && anonKey.isNotBlank() && !isPrivilegedKey(anonKey)
    fun configurationMessage(): String? = if (isConfigured()) null else
        "إعدادات خدمة المصادقة غير مهيأة. مرّر SUPABASE_URL وSUPABASE_ANON_KEY وقت البناء؛ لا تضع service_role أو أسرارًا داخل التطبيق."
    fun currentUserId(): String? = readSession()?.userId

    fun canUseCachedAdminSession(): Boolean {
        val session = readSession() ?: return false
        val lastVerified = preferences.getLong("admin_verified_at", 0L)
        return session.expiresAtMillis > System.currentTimeMillis() && lastVerified > System.currentTimeMillis() - 12 * 60 * 60 * 1000L
    }

    suspend fun signIn(email: String, password: String): String = withContext(Dispatchers.IO) {
        ensureConfigured()
        val payload = JSONObject().put("email", email.trim()).put("password", password)
        val json = JSONObject(requestRaw("/auth/v1/token?grant_type=password", "POST", payload.toString(), null))
        val userId = json.optJSONObject("user")?.optString("id").orEmpty()
        persistSession(AmanSession(json.getString("access_token"), json.getString("refresh_token"), userId,
            System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L))
        userId
    }

    suspend fun recoverPassword(email: String) = withContext(Dispatchers.IO) {
        ensureConfigured()
        if (email.isBlank()) throw ContractException("أدخل البريد الإلكتروني أولًا.")
        requestRaw("/auth/v1/recover", "POST", JSONObject().put("email", email.trim()).toString(), null)
    }

    suspend fun isAdmin(): Boolean = withContext(Dispatchers.IO) {
        val response = authenticatedRequest("/rest/v1/rpc/is_admin", "POST", "{}")
        val allowed = response.trim().let { it == "true" || it == "[true]" }
        preferences.edit().putLong("admin_verified_at", if (allowed) System.currentTimeMillis() else 0L).apply()
        allowed
    }

    suspend fun hasPermission(code: String): Boolean {
        val result = authenticatedRequest("/rest/v1/rpc/admin_has_permission", "POST",
            JSONObject().put("p_permission_code", code).toString())
        return result.trim().let { it == "true" || it == "[true]" }
    }

    suspend fun select(table: String, query: String = "select=*&limit=100"): String =
        authenticatedRequest("/rest/v1/$table?$query", "GET", null)
    suspend fun countRows(table: String, filter: String = ""): Long = withContext(Dispatchers.IO) {
        val session = usableSession() ?: throw ContractException("انتهت جلسة الدخول. سجّل الدخول مجددًا.")
        val suffix = if (filter.isBlank()) "" else "&$filter"
        val request = Request.Builder().url("$baseUrl/rest/v1/$table?select=id$suffix")
            .header("apikey", anonKey).header("Authorization", "Bearer ${session.accessToken}")
            .header("Accept", "application/json").header("Prefer", "count=exact")
            .header("Range-Unit", "items").header("Range", "0-0").get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 401) preferences.edit().clear().apply()
                val message = when (response.code) {
                    401 -> "انتهت الجلسة أو لم تعد صالحة. سجّل الدخول مجددًا."
                    403 -> "لا تملك الصلاحية اللازمة لهذا الإجراء."
                    else -> "تعذر قراءة المؤشر من الخدمة."
                }
                throw BackendResponseException(response.code, message)
            }
            val range = response.header("Content-Range") ?: throw ContractException("لم تُرجع الخدمة عدد السجلات المطلوب.")
            range.substringAfterLast('/').toLongOrNull() ?: throw ContractException("تعذر قراءة عدد السجلات من الخدمة.")
        }
    }
    suspend fun rpc(name: String, arguments: JSONObject): String =
        authenticatedRequest("/rest/v1/rpc/$name", "POST", arguments.toString())

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val session = readSession()
        try { if (session != null && isConfigured()) requestRaw("/auth/v1/logout", "POST", "{}", session.accessToken) }
        finally { preferences.edit().clear().apply() }
    }
    suspend fun clearLocalSession() = withContext(Dispatchers.IO) { preferences.edit().clear().apply() }

    private suspend fun authenticatedRequest(path: String, method: String, body: String?): String = withContext(Dispatchers.IO) {
        val session = usableSession() ?: throw ContractException("انتهت جلسة الدخول. سجّل الدخول مجددًا.")
        try {
            requestRaw(path, method, body, session.accessToken)
        } catch (error: BackendResponseException) {
            if (error.statusCode == 401) preferences.edit().clear().apply()
            throw error
        }
    }

    private suspend fun usableSession(): AmanSession? {
        val old = readSession() ?: return null
        if (old.expiresAtMillis > System.currentTimeMillis() + 60_000L) return old
        return try {
            val payload = JSONObject().put("refresh_token", old.refreshToken)
            val json = JSONObject(requestRaw("/auth/v1/token?grant_type=refresh_token", "POST", payload.toString(), null))
            val updated = AmanSession(json.getString("access_token"), json.optString("refresh_token", old.refreshToken),
                json.optJSONObject("user")?.optString("id").takeUnless { it.isNullOrBlank() } ?: old.userId,
                System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L)
            persistSession(updated)
            updated
        } catch (error: Exception) {
            preferences.edit().clear().apply()
            throw ContractException("انتهت جلسة المصادقة أو تعذر تجديدها. سجّل الدخول مجددًا.")
        }
    }

    private suspend fun requestRaw(path: String, method: String, body: String?, bearer: String?): String = withContext(Dispatchers.IO) {
        ensureConfigured()
        val url = "$baseUrl/${path.removePrefix("/")}"
        val builder = Request.Builder().url(url).header("apikey", anonKey).header("Accept", "application/json")
        if (!bearer.isNullOrBlank()) builder.header("Authorization", "Bearer $bearer")
        val content = body?.toRequestBody(jsonType)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(content ?: "{}".toRequestBody(jsonType))
            "PATCH" -> builder.patch(content ?: "{}".toRequestBody(jsonType))
            else -> throw ContractException("طريقة HTTP غير مدعومة.")
        }
        http.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val details = runCatching { JSONObject(text).optString("message").ifBlank { JSONObject(text).optString("msg") } }.getOrNull().orEmpty()
                val safeMessage = when (response.code) {
                    401 -> "انتهت الجلسة أو لم تعد صالحة. سجّل الدخول مجددًا."
                    403 -> "لا تملك الصلاحية اللازمة لهذا الإجراء."
                    409 -> "تعارضت البيانات؛ حدّث السجل ثم أعد المحاولة."
                    400, 422 -> "لم تستوف البيانات شروط التحقق؛ راجع الحقول المطلوبة."
                    else -> "تعذر إكمال الطلب من الخدمة. تحقق من الاتصال والحالة ثم أعد المحاولة."
                }
                throw BackendResponseException(response.code, safeMessage)
            }
            text
        }
    }

    private fun readSession(): AmanSession? {
        val access = preferences.getString("access", null) ?: return null
        val refresh = preferences.getString("refresh", null) ?: return null
        val user = preferences.getString("uid", null) ?: return null
        return AmanSession(access, refresh, user, preferences.getLong("expires", 0L))
    }

    private fun persistSession(session: AmanSession) {
        if (session.userId.isBlank()) throw ContractException("استجابة المصادقة لم تتضمن معرّف المستخدم.")
        preferences.edit().putString("access", session.accessToken).putString("refresh", session.refreshToken)
            .putString("uid", session.userId).putLong("expires", session.expiresAtMillis).apply()
    }

    private fun ensureConfigured() { if (!isConfigured()) throw ContractException(configurationMessage() ?: "Supabase غير مهيأ") }

    private fun isPrivilegedKey(key: String): Boolean {
        if (key.contains("service_role", true) || key.startsWith("sb_secret_", true)) return true
        val tokenPart = key.split('.').getOrNull(1) ?: return false
        return runCatching {
            val json = String(Base64.decode(tokenPart, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
            JSONObject(json).optString("role").equals("service_role", true)
        }.getOrDefault(false)
    }
}
