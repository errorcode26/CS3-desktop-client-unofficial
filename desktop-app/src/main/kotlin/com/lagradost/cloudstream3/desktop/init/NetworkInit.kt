package com.lagradost.cloudstream3.desktop.init

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.desktop.network.NetworkConfig
import com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass
import com.lagradost.cloudstream3.desktop.utils.appScope
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Initializes all network-related subsystems:
 * - Global NiceHttp clients (timeouts + HTTP/1.1)
 * - Jackson mapper VerifiedRepo fallback module
 * - WebViewResolver handler binding (Playwright)
 * - CookieManager stub binding (OkHttp CookieJar)
 */
fun initNetwork() {
    // Initialize global NiceHttp clients
    NetworkConfig.updateGlobalNetworkClients()

    // Initialize NewPipe for plugins that depend on it
    org.schabi.newpipe.extractor.NewPipe.init(com.lagradost.cloudstream3.desktop.network.NewPipeDownloader.getInstance())

    // Patch Jackson mapper for dex2jar Kotlin reflection bugs
    val mapper = mapper

    // God help us, this frequently crashes because Kotlin reflection throws KotlinReflectionInternalError on dex2jar'd inner data classes.
    // So here is this monstrosity of a custom deserializer to bypass it.
    // We register a custom deserializer for VerifiedRepo to bypass Jackson's
    // KotlinReflectionInternalError on dex2jar'd inner data classes.
    val fallbackModule = object : com.fasterxml.jackson.databind.module.SimpleModule() {
        override fun setupModule(context: SetupContext) {
            super.setupModule(context)
            context.addDeserializers(object : com.fasterxml.jackson.databind.deser.Deserializers.Base() {
                override fun findBeanDeserializer(
                    type: com.fasterxml.jackson.databind.JavaType,
                    config: com.fasterxml.jackson.databind.DeserializationConfig,
                    beanDesc: com.fasterxml.jackson.databind.BeanDescription,
                ): com.fasterxml.jackson.databind.JsonDeserializer<*>? {
                    if (type.rawClass.name.contains("VerifiedRepo")) {
                        return object : com.fasterxml.jackson.databind.JsonDeserializer<Any>() {
                            override fun deserialize(
                                p: com.fasterxml.jackson.core.JsonParser,
                                ctxt: com.fasterxml.jackson.databind.DeserializationContext,
                            ): Any? {
                                val node = p.codec.readTree<com.fasterxml.jackson.databind.JsonNode>(p)
                                val name = node.get("name")?.asText() ?: ""
                                val url = node.get("url")?.asText() ?: ""

                                try {
                                    val clazz = type.rawClass
                                    val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
                                    unsafeField.isAccessible = true
                                    val unsafe = unsafeField.get(null) as sun.misc.Unsafe
                                    val instance = unsafe.allocateInstance(clazz)

                                    var fieldsSet = 0
                                    clazz.declaredFields.forEach { f ->
                                        f.isAccessible = true
                                        if (f.name == "name" || f.name.contains("name", ignoreCase = true)) {
                                            f.set(instance, name)
                                            fieldsSet++
                                        } else if (f.name == "url" || f.name.contains("url", ignoreCase = true)) {
                                            f.set(instance, url)
                                            fieldsSet++
                                        }
                                    }

                                    // Suppress FATAL log, VerifiedRepo usually only has the url field.
                                    if (fieldsSet == 0) {
                                        AppLogger.e("Warning: Could not set any fields on VerifiedRepo via Unsafe!")
                                    }
                                    return instance
                                } catch (e: Exception) {
                                    AppLogger.e("VerifiedRepo deserialization failed", e)
                                    return null
                                }
                            }
                        }
                    }
                    return null
                }
            })
        }
    }
    mapper.registerModule(fallbackModule)

    // Install universal protection against KotlinReflectionInternalError on dex2jar'd inner data classes
    val currentIntrospector = mapper.deserializationConfig.annotationIntrospector
    if (currentIntrospector != null) {
        mapper.setAnnotationIntrospector(
            com.fasterxml.jackson.databind.introspect.SafeKotlinAnnotationIntrospector(currentIntrospector)
        )
        AppLogger.i("Installed SafeKotlinAnnotationIntrospector on global Jackson mapper")
    }

    // Generic Android WebView stub integration with Desktop CDP manual verification
    android.webkit.WebView.loadUrlHandler = java.util.function.BiConsumer { webView, url ->
        AppLogger.i("WebView.loadUrl: $url")
        val client = webView.webViewClient
        client?.onPageStarted(webView, url, null)
        appScope.launch(Dispatchers.IO) {
            val isBypassAllowed = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(
                com.lagradost.common.storage.DesktopDataStore.PREF_ALLOW_CF_BYPASS,
            ) ?: false
            val httpUrl = url.toHttpUrlOrNull()
            val host = httpUrl?.host ?: ""

            if (!isBypassAllowed || (host.isNotBlank() && CloudflareKiller.isFailed(host))) {
                AppLogger.d("WebView.loadUrlHandler: Suppressed for $url (allowed=$isBypassAllowed, failed=${if (host.isNotBlank()) CloudflareKiller.isFailed(host) else false})")
                try {
                    withContext(Dispatchers.Main) {
                        webView.webViewClient?.onPageFinished(webView, url)
                    }
                } catch (_: Throwable) {
                    webView.webViewClient?.onPageFinished(webView, url)
                }
                return@launch
            }

            try {
                SystemBrowserCdpBypass.launchManualClearance(url, host)
            } catch (e: Exception) {
                AppLogger.e("WebView.loadUrlHandler error: ${e.message}")
            } finally {
                try {
                    withContext(Dispatchers.Main) {
                        webView.webViewClient?.onPageFinished(webView, url)
                    }
                } catch (_: Throwable) {
                    webView.webViewClient?.onPageFinished(webView, url)
                }
            }
        }
    }

    // Bind the CookieManager stub to OkHttp CookieJar and merge with CloudflareKiller clearance cookies
    android.webkit.CookieManager.setCookieHandler = { url, value ->
        val httpUrl = url.toHttpUrlOrNull()
        if (httpUrl != null) {
            val parsedCookie = okhttp3.Cookie.parse(httpUrl, value)
            val cookie = if (parsedCookie != null) {
                parsedCookie
            } else {
                val parts = value.split(";", limit = 2)[0].split("=", limit = 2)
                if (parts.size == 2) {
                    val k = parts[0].trim()
                    val v = parts[1].trim()
                    if (k.isNotEmpty()) {
                        try {
                            okhttp3.Cookie.Builder()
                                .domain(httpUrl.host)
                                .path("/")
                                .name(k)
                                .value(v)
                                .build()
                        } catch (_: Exception) { null }
                    } else null
                } else null
            }
            if (cookie != null) {
                app.baseClient.cookieJar.saveFromResponse(httpUrl, listOf(cookie))
            }
        }
    }

    android.webkit.CookieManager.getCookieHandler = { url ->
        val httpUrl = url.toHttpUrlOrNull()
        if (httpUrl != null) {
            val jarCookies = app.baseClient.cookieJar.loadForRequest(httpUrl)
            val cfCookies = CloudflareKiller.getSavedCookies(httpUrl.host)
            val merged = mutableMapOf<String, String>()
            jarCookies.forEach { merged[it.name] = it.value }
            cfCookies.forEach { (k, v) -> merged[k] = v }
            if (merged.isNotEmpty()) {
                merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
            } else null
        } else {
            null
        }
    }

    android.webkit.CookieManager.removeAllCookiesHandler = { callback ->
        (app.baseClient.cookieJar as? com.lagradost.cloudstream3.desktop.network.DesktopCookieJar)?.removeAll()
        callback?.onReceiveValue(true)
    }

    com.lagradost.cloudstream3.desktop.network.NetworkMonitor.initialize()
}
