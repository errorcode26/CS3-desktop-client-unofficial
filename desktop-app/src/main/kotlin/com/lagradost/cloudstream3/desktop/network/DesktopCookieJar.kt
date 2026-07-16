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
    private val cookieCache = mutableMapOf<String, MutableMap<String, Cookie>>()
    private val cacheFile: File

    init {
        val cacheDir = File(PlatformPaths.appDataDir, "network")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        cacheFile = File(cacheDir, "cookies.json")
        loadFromDisk()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        
        var changed = false
        val domainCookies = cookieCache.getOrPut(url.host) { mutableMapOf() }
        
        for (cookie in cookies) {
            if (cookie.expiresAt <= System.currentTimeMillis()) {
                if (domainCookies.remove(cookie.name) != null) {
                    changed = true
                }
            } else {
                domainCookies[cookie.name] = cookie
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
        val expiredCookies = mutableListOf<String>()
        
        // Load exact host match and parent domain matches
        val hosts = buildList {
            add(url.host)
            val parts = url.host.split(".")
            if (parts.size > 2) {
                for (i in 1 until parts.size - 1) {
                    add(parts.subList(i, parts.size).joinToString("."))
                }
            }
        }

        var changed = false
        for (host in hosts) {
            val domainCookies = cookieCache[host] ?: continue
            for ((name, cookie) in domainCookies) {
                if (cookie.expiresAt <= System.currentTimeMillis()) {
                    expiredCookies.add(name)
                    changed = true
                } else if (cookie.matches(url)) {
                    validCookies.add(cookie)
                }
            }
            expiredCookies.forEach { domainCookies.remove(it) }
            expiredCookies.clear()
        }

        if (changed) {
            saveToDisk()
        }

        return validCookies
    }

    private fun loadFromDisk() {
        if (!cacheFile.exists()) return
        try {
            val json = cacheFile.readText()
            val serialized: Map<String, List<SerializedCookie>> = mapper.readValue(json)
            
            for ((host, cookies) in serialized) {
                val map = mutableMapOf<String, Cookie>()
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
                    map[sc.name] = builder.build()
                }
                cookieCache[host] = map
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
                        hostOnly = it.hostOnly
                    )
                }
            }
            cacheFile.writeText(mapper.writeValueAsString(serialized))
        } catch (e: Exception) {
            AppLogger.e("Failed to save cookies to disk", e)
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
        val hostOnly: Boolean
    )
}
