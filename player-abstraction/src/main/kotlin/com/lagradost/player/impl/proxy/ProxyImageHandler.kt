package com.lagradost.player.impl.proxy

import com.lagradost.cloudstream3.app
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import okhttp3.Cache
import okhttp3.Request
import java.io.File
import java.net.URI
import java.util.Base64

internal object ProxyImageHandler {

    private val imageProxyClient by lazy {
        val cacheDir = File(PlatformPaths.appDataDir, "image_cache_http").also { it.mkdirs() }
        app.baseClient.newBuilder()
            .fastFallback(false)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .cache(Cache(cacheDir, 256L * 1024 * 1024))
            .apply {
                interceptors().removeAll {
                    it.javaClass.simpleName == "RateLimitInterceptor" ||
                        it.javaClass.simpleName == "DevNetworkInterceptor"
                }
            }
            .build()
    }

    fun buildImageUrl(port: Int, url: String): String {
        val encodedUrl = Base64.getUrlEncoder().withoutPadding().encodeToString(url.toByteArray(Charsets.UTF_8))
        return "http://127.0.0.1:$port/image?u=$encodedUrl"
    }

    suspend fun handleImageRequest(call: ApplicationCall) {
        try {
            val encodedUrl = call.request.queryParameters["u"]
            if (encodedUrl == null) {
                call.respond(HttpStatusCode.NotFound)
                return
            }
            var url = String(Base64.getUrlDecoder().decode(encodedUrl), Charsets.UTF_8).trim()
            if (url.startsWith("//")) {
                url = "https:$url"
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                call.respond(HttpStatusCode.BadRequest)
                return
            }

            val requestBuilder = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")

            val isThirdPartyCdn = url.contains("image.tmdb.org", ignoreCase = true) ||
                url.contains("anilist.co", ignoreCase = true) ||
                url.contains("kitsu.app", ignoreCase = true) ||
                url.contains("kitsu.io", ignoreCase = true) ||
                url.contains("fanart.tv", ignoreCase = true) ||
                url.contains("imgur.com", ignoreCase = true)

            if (!isThirdPartyCdn) {
                try {
                    val uri = URI(url)
                    requestBuilder.header("Referer", "${uri.scheme}://${uri.host}/")
                } catch (_: Exception) {}
            }

            val response = imageProxyClient.newCall(requestBuilder.build()).await()
            if (!response.isSuccessful) {
                response.body?.close()
                call.respond(HttpStatusCode.fromValue(response.code))
                return
            }
            val contentType = response.header("Content-Type") ?: "image/jpeg"
            val bytes = response.body?.bytes()
            if (bytes != null) {
                call.respondBytes(bytes, ContentType.parse(contentType), HttpStatusCode.fromValue(response.code))
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        } catch (e: Exception) {
            AppLogger.e("Proxy:LocalStream", "Image proxy failed", e)
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
}
