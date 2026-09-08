package com.lagradost.cloudstream3.desktop.network

import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.mapper
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.io.File

/**
 * A persistent CookieJar that saves Cloudflare clearance tokens and session
 * cookies across application restarts using a local JSON file.
 */
class DesktopCookieJar : CookieJar {
    companion object {
        @Volatile
        var activeInstance: DesktopCookieJar? = null
            internal set
    }

    private val cookieCache = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, Cookie>>()
    private val cacheFile: File

    init {
        val cacheDir = File(PlatformPaths.appDataDir, "network")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        cacheFile = File(cacheDir, "cookies.json")
        loadFromDisk()
        activeInstance = this
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        saveCookiesInternal(cookies)
    }

    /**
     * Direct injection of fully qualified OkHttp Cookies (e.g. captured from CDP).
     */
    @Synchronized
    fun saveCookies(cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        saveCookiesInternal(cookies)
    }

    private fun saveCookiesInternal(cookies: List<Cookie>) {
        var changed = false
        val now = System.currentTimeMillis()
        for (cookie in cookies) {
            val domain = cookie.domain.lowercase().trimStart('.')
            val domainCookies = cookieCache.getOrPut(domain) { java.util.concurrent.ConcurrentHashMap() }
            val cookieKey = "${cookie.name}#${cookie.path}"
            if (cookie.expiresAt <= now) {
                if (domainCookies.remove(cookieKey) != null) {
                    changed = true
                }
            } else {
                domainCookies[cookieKey] = cookie
                changed = true
            }
        }
        if (changed) {
            saveToDisk()
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val validCookies = mutableListOf<Cookie>()
        val now = System.currentTimeMillis()

        // Build candidate domains matching RFC 6265 hierarchy (e.g. tv.sub.domain.com -> tv.sub.domain.com, sub.domain.com, domain.com)
        val candidateDomains = buildList {
            val host = url.host.lowercase()
            add(host)
            val parts = host.split(".")
            if (parts.size > 2) {
                for (i in 1 until parts.size - 1) {
                    add(parts.subList(i, parts.size).joinToString("."))
                }
            }
        }

        var changed = false
        for (domain in candidateDomains) {
            val domainCookies = cookieCache[domain] ?: continue
            val iterator = domainCookies.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val cookie = entry.value
                if (cookie.expiresAt <= now) {
                    iterator.remove()
                    changed = true
                } else if (cookie.matches(url)) {
                    validCookies.add(cookie)
                }
            }
        }

        if (changed) {
            saveToDisk()
        }

        return validCookies
    }

    @Synchronized
    fun getAllStoredCookies(): Map<String, List<Cookie>> {
        val now = System.currentTimeMillis()
        val result = mutableMapOf<String, List<Cookie>>()
        for ((domain, map) in cookieCache) {
            val valid = map.values.filter { it.expiresAt > now }
            if (valid.isNotEmpty()) {
                result[domain] = valid
            }
        }
        return result
    }

    @Synchronized
    fun removeCookiesForDomain(domain: String) {
        val canonical = domain.lowercase().trimStart('.')
        cookieCache.remove(canonical)
        saveToDisk()
    }

    @Synchronized
    fun removeAll() {
        cookieCache.clear()
        saveToDisk()
    }

    private fun loadFromDisk() {
        if (!cacheFile.exists()) return
        try {
            val json = cacheFile.readText()
            val serialized: Map<String, List<SerializedCookie>> = mapper.readValue(json)

            for ((domain, cookies) in serialized) {
                val canonicalDomain = domain.lowercase().trimStart('.')
                val map = cookieCache.getOrPut(canonicalDomain) { java.util.concurrent.ConcurrentHashMap() }
                for (sc in cookies) {
                    val builder = Cookie.Builder()
                        .name(sc.name)
                        .value(sc.value)
                        .domain(sc.domain)
                        .path(sc.path)
                        .expiresAt(sc.expiresAt)
                    if (sc.secure) builder.secure()
                    if (sc.httpOnly) builder.httpOnly()
                    if (sc.hostOnly) builder.hostOnlyDomain(sc.domain)
                    val cookie = builder.build()
                    map["${sc.name}#${sc.path}"] = cookie
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Failed to load cookies from disk", e)
        }
    }

    private fun saveToDisk() {
        try {
            val serialized = cookieCache.mapValues { (_, cookies) ->
                cookies.values.map {
                    SerializedCookie(
                        name = it.name,
                        value = it.value,
                        domain = it.domain,
                        path = it.path,
                        expiresAt = it.expiresAt,
                        secure = it.secure,
                        httpOnly = it.httpOnly,
                        hostOnly = it.hostOnly,
                    )
                }
            }
            val tmpFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
            tmpFile.writeText(mapper.writeValueAsString(serialized))
            java.nio.file.Files.move(
                tmpFile.toPath(),
                cacheFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (e: Exception) {
            // Fallback non-atomic write if atomic move is unsupported on the underlying filesystem
            try {
                val serialized = cookieCache.mapValues { (_, cookies) ->
                    cookies.values.map {
                        SerializedCookie(
                            name = it.name,
                            value = it.value,
                            domain = it.domain,
                            path = it.path,
                            expiresAt = it.expiresAt,
                            secure = it.secure,
                            httpOnly = it.httpOnly,
                            hostOnly = it.hostOnly,
                        )
                    }
                }
                cacheFile.writeText(mapper.writeValueAsString(serialized))
            } catch (fallbackError: Exception) {
                AppLogger.e("Failed to save cookies to disk", fallbackError)
            }
        }
    }

    data class SerializedCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val expiresAt: Long,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean,
    )
}
