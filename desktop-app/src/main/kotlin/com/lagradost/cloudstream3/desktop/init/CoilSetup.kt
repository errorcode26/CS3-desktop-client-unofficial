package com.lagradost.cloudstream3.desktop.init

import androidx.compose.ui.window.ApplicationScope
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import com.lagradost.cloudstream3.desktop.network.AutoRetryInterceptor
import com.lagradost.cloudstream3.desktop.network.DevNetworkInterceptor
import com.lagradost.cloudstream3.desktop.network.RateLimitInterceptor
import com.lagradost.common.platform.PlatformPaths
import okhttp3.Interceptor
import okio.Path.Companion.toOkioPath
import java.io.File
import java.util.concurrent.TimeUnit

@androidx.compose.runtime.Composable
fun ApplicationScope.initCoil() {
    setSingletonImageLoaderFactory { context ->
        val imageClient = com.lagradost.cloudstream3.app.baseClient.newBuilder()
            .apply {
                interceptors().removeAll { it is RateLimitInterceptor || it is AutoRetryInterceptor || it is DevNetworkInterceptor }
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(Interceptor { chain ->
                var request = chain.request()
                val urlStr = request.url.toString()
                if (urlStr.startsWith("//")) {
                    request = request.newBuilder().url("https:$urlStr").build()
                }
                chain.proceed(request)
            })
            .build()

        coil3.ImageLoader.Builder(context)
            .memoryCache {
                coil3.memory.MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                coil3.disk.DiskCache.Builder()
                    .directory(File(PlatformPaths.appDataDir, "image_cache").also { it.mkdirs() }.toOkioPath())
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            .components {
                add(coil3.svg.SvgDecoder.Factory())
                add(
                    coil3.network.okhttp.OkHttpNetworkFetcherFactory(
                        callFactory = { request ->
                            imageClient.newCall(request)
                        },
                    ),
                )
            }
            .crossfade(true)
            .build()
    }
}
