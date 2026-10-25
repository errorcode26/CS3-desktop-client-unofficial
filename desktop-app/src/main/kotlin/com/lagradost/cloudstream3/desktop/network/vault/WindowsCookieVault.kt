package com.lagradost.cloudstream3.desktop.network.vault

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import okhttp3.Cookie
import okhttp3.HttpUrl
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Persistent cookie storage encrypted at rest using Windows DPAPI (CryptProtectData).
 * Ties credential and clearance storage directly to the Windows user profile.
 */
object WindowsCookieVault {
    private const val TAG = "WindowsCookieVault"
    private val mapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class VaultEntry(
        val name: String,
        val value: String,
        val domain: String,
        val path: String = "/",
        val expiresAt: Long = -1L,
        val secure: Boolean = false,
        val httpOnly: Boolean = false,
        val hostOnly: Boolean = false,
    ) {
        @get:JsonIgnore
        val storageKey: String get() = "$name\u0000${domain.lowercase()}\u0000$path"
        fun isExpired(now: Long = System.currentTimeMillis()): Boolean = expiresAt in 1..now

        fun toOkHttpCookie(): Cookie {
            val builder = Cookie.Builder()
                .name(name)
                .value(value)
                .path(path)
            if (hostOnly) builder.hostOnlyDomain(domain) else builder.domain(domain)
            if (expiresAt > 0) builder.expiresAt(expiresAt)
            if (secure) builder.secure()
            if (httpOnly) builder.httpOnly()
            return builder.build()
        }

        companion object {
            fun fromOkHttpCookie(cookie: Cookie): VaultEntry {
                return VaultEntry(
                    name = cookie.name,
                    value = cookie.value,
                    domain = cookie.domain,
                    path = cookie.path,
                    expiresAt = cookie.expiresAt,
                    secure = cookie.secure,
                    httpOnly = cookie.httpOnly,
                    hostOnly = cookie.hostOnly,
                )
            }
        }
    }

    private val vaultFile: File by lazy {
        val dir = File(PlatformPaths.appDataDir, "network")
        if (!dir.exists()) dir.mkdirs()
        File(dir, "cookies.vault")
    }

    private val cache = ConcurrentHashMap<String, VaultEntry>()
    private val isLoaded = AtomicBoolean(false)
    private val writeScheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "cookie-vault-worker").apply { isDaemon = true }
    }
    private val isWritePending = AtomicBoolean(false)

    fun initialize(): List<Cookie> {
        ensureLoaded()
        return getAllUnexpired()
    }

    private fun ensureLoaded() {
        if (!isLoaded.compareAndSet(false, true)) return
        val file = vaultFile
        if (!file.exists() || !file.isFile || file.length() == 0L) return

        try {
            val rawBytes = file.readBytes()
            val decryptedBytes = if (Platform.isWindows()) {
                Crypt32Util.cryptUnprotectData(rawBytes)
            } else {
                rawBytes
            }
            val json = String(decryptedBytes, StandardCharsets.UTF_8)
            val entries: List<VaultEntry> = mapper.readValue(json)
            val now = System.currentTimeMillis()
            var expiredCount = 0

            entries.forEach { entry ->
                if (!entry.isExpired(now) && !com.lagradost.cloudstream3.desktop.network.webview.WindowsWebView2Runtime.isBloatCookie(entry.name)) {
                    cache[entry.storageKey] = entry
                } else {
                    expiredCount++
                }
            }

            AppLogger.i("$TAG: Restored ${cache.size} cookies from secure vault ($expiredCount pruned).")
        } catch (e: Exception) {
            AppLogger.w("$TAG: Failed to read secure cookie vault: ${e.message}")
        }
    }

    fun saveCookies(cookies: Collection<Cookie>) {
        ensureLoaded()
        if (cookies.isEmpty()) return
        var changed = false

        cookies.forEach { cookie ->
            if (com.lagradost.cloudstream3.desktop.network.webview.WindowsWebView2Runtime.isBloatCookie(cookie.name)) return@forEach
            val entry = VaultEntry.fromOkHttpCookie(cookie)
            if (!entry.isExpired()) {
                cache[entry.storageKey] = entry
                changed = true
            }
        }

        if (changed) {
            schedulePersist()
        }
    }

    fun getAllUnexpired(): List<Cookie> {
        ensureLoaded()
        val now = System.currentTimeMillis()
        return cache.values.filter { !it.isExpired(now) }.map { it.toOkHttpCookie() }
    }

    fun getCookiesForUrl(url: HttpUrl): List<Cookie> {
        ensureLoaded()
        val all = getAllUnexpired()
        return all.filter { it.matches(url) }
    }

    fun getClearanceCookie(host: String): Cookie? {
        ensureLoaded()
        val cleanHost = host.lowercase().trim().trimStart('.')
        val now = System.currentTimeMillis()
        return cache.values
            .filter { it.name == "cf_clearance" && !it.isExpired(now) }
            .firstOrNull { cleanHost == it.domain.trimStart('.') || cleanHost.endsWith(".${it.domain.trimStart('.')}") }
            ?.toOkHttpCookie()
    }

    fun getCookieMapForHost(host: String): Map<String, String> {
        ensureLoaded()
        val cleanHost = host.lowercase().trim().trimStart('.')
        val now = System.currentTimeMillis()
        val result = mutableMapOf<String, String>()

        cache.values.filter { !it.isExpired(now) }.forEach { entry ->
            val d = entry.domain.trimStart('.').lowercase()
            if (cleanHost == d || cleanHost.endsWith(".$d")) {
                result[entry.name] = entry.value
            }
        }
        return result
    }

    fun clearAll() {
        ensureLoaded()
        cache.clear()
        persistToDisk()
    }

    fun removeForHost(host: String) {
        ensureLoaded()
        val cleanHost = host.lowercase().trim().trimStart('.')
        val removed = cache.keys.filter { key ->
            val entry = cache[key] ?: return@filter false
            val d = entry.domain.trimStart('.').lowercase()
            cleanHost == d || cleanHost.endsWith(".$d") || d.endsWith(".$cleanHost")
        }
        if (removed.isNotEmpty()) {
            removed.forEach { cache.remove(it) }
            persistToDisk()
        }
    }

    private fun schedulePersist() {
        if (!isWritePending.compareAndSet(false, true)) return
        writeScheduler.schedule({
            isWritePending.set(false)
            persistToDisk()
        }, 1, TimeUnit.SECONDS)
    }

    private fun persistToDisk() {
        try {
            val now = System.currentTimeMillis()
            val validEntries = cache.values.filter { !it.isExpired(now) }
            val json = mapper.writeValueAsString(validEntries)
            val jsonBytes = json.toByteArray(StandardCharsets.UTF_8)

            val protectedBytes = if (Platform.isWindows()) {
                Crypt32Util.cryptProtectData(jsonBytes)
            } else {
                jsonBytes
            }

            val target = vaultFile
            val tempFile = File(target.parentFile, "${target.name}.tmp")
            tempFile.writeBytes(protectedBytes)

            if (!tempFile.renameTo(target)) {
                target.delete()
                tempFile.renameTo(target)
            }
            AppLogger.d("$TAG: Persisted ${validEntries.size} cookies to encrypted vault.")
        } catch (e: Exception) {
            AppLogger.e("$TAG: Failed to encrypt and persist cookies to vault: ${e.message}")
        }
    }
}
