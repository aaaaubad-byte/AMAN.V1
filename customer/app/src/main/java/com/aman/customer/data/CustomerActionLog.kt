package com.aman.customer.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Non-sensitive action/error trail for diagnosing silent Customer failures. */
class CustomerActionLog(context: Context) {
    private val file = File(context.filesDir, "customer-action-errors.jsonl")

    fun record(action: String, phase: String, details: String? = null) {
        val payload = JSONObject()
            .put("at", Instant.now().toString())
            .put("action", action.take(120))
            .put("phase", phase.take(40))
            .put("details", details.orEmpty().replace(Regex("(?i)(token|password|key|reference)\\s*[:=]\\s*[^,} ]+"), "[redacted]").take(500))
        runCatching { file.appendText(payload.toString() + "\n") }
        Log.d("AMAN.CustomerAction", payload.toString())
    }

    fun recent(limit: Int = 100): List<String> = runCatching { file.readLines().takeLast(limit.coerceIn(1, 500)) }.getOrDefault(emptyList())
}
