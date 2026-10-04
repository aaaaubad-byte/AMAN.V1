package com.aman.customer.data

/** Normalize a user-entered international number to the E.164 shape required by AMAN-1 RPCs. */
fun normalizePhoneE164(input: String): String? {
    val result = StringBuilder()
    for (character in input.trim()) {
        val digit = character.digitToIntOrNull()
        when {
            digit != null -> result.append(digit)
            character == '+' -> result.append('+')
            character.isWhitespace() || character in "-()." -> Unit
            else -> return null
        }
    }
    val normalized = result.toString()
    return normalized.takeIf { Regex("^\\+[0-9]{7,15}$").matches(it) }
}

fun phoneDigits(input: String): String = buildString {
    input.forEach { character -> character.digitToIntOrNull()?.let { append(it) } }
}
