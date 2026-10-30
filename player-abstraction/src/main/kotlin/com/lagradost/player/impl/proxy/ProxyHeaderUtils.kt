package com.lagradost.player.impl.proxy

import okhttp3.Request

/**
 * Sanitizes HTTP header values to conform with RFC 7230 / RFC 9110 and prevent
 * OkHttp validation failures from illegal non-ASCII characters or homoglyphs.
 */
internal fun sanitizeHeaderValue(value: String): String {
    var hasNonAscii = false
    for (i in 0 until value.length) {
        if (value[i] > '\u007F') {
            hasNonAscii = true
            break
        }
    }
    if (!hasNonAscii) return value

    val sb = StringBuilder(value.length)
    for (i in 0 until value.length) {
        val c = value[i]
        when (c) {
            '\u0435' -> sb.append('e')
            '\u0415' -> sb.append('E')
            '\u0430' -> sb.append('a')
            '\u0410' -> sb.append('A')
            '\u043E' -> sb.append('o')
            '\u041E' -> sb.append('O')
            '\u0440' -> sb.append('p')
            '\u0420' -> sb.append('P')
            '\u0441' -> sb.append('c')
            '\u0421' -> sb.append('C')
            '\u0443' -> sb.append('y')
            '\u0423' -> sb.append('Y')
            '\u0456' -> sb.append('i')
            '\u0406' -> sb.append('I')
            '\u0455' -> sb.append('s')
            '\u0405' -> sb.append('S')
            '\u0445' -> sb.append('x')
            '\u0425' -> sb.append('X')
            '\u0458' -> sb.append('j')
            '\u0408' -> sb.append('J')
            else -> {
                if (c == '\t' || (c in ' '..'~') || (c.code in 0x80..0xFF)) {
                    sb.append(c)
                }
            }
        }
    }
    return sb.toString()
}

/**
 * Safely sets an HTTP header on an OkHttp Request.Builder, sanitizing value characters
 * and silently dropping headers with invalid names according to RFC 7230.
 */
internal fun Request.Builder.safeHeader(name: String, value: String): Request.Builder {
    if (name.isBlank()) return this
    val isValidName = name.all { it in '!'..'~' && it !in "()<>@,;:\\\"/[]?={}" }
    if (!isValidName) return this
    val cleanValue = sanitizeHeaderValue(value)
    return try {
        this.header(name, cleanValue)
    } catch (_: IllegalArgumentException) {
        val asciiOnly = cleanValue.filter { it == '\t' || it in ' '..'~' }
        try {
            this.header(name, asciiOnly)
        } catch (_: Exception) {
            this
        }
    }
}
