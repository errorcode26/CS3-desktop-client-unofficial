package com.lagradost.cloudstream3.desktop.init

import androidx.compose.ui.window.ApplicationScope
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import com.lagradost.cloudstream3.desktop.network.NetworkConfig
import com.lagradost.common.platform.PlatformPaths
import okio.Path.Companion.toOkioPath
import java.io.File

@androidx.compose.runtime.Composable
fun ApplicationScope.initCoil() {
    setSingletonImageLoaderFactory { context ->
        coil3.ImageLoader.Builder(context)
            .memoryCache {
                coil3.memory.MemoryCache.Builder()
                    .maxSizeBytes(128L * 1024 * 1024)
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
                            NetworkConfig.getImageClient().newCall(request)
                        },
                    ),
                )
                add(WrappedImageFallback)
            }
            .crossfade(true)
            .build()
    }
}

/**
 * Interceptor that un-wraps images wrapped by an external proxy parameter
 * (e.g. `https://proxy.example/?url=https://target/image.jpg`) if the proxy returns an error.
 */
private object WrappedImageFallback : coil3.intercept.Interceptor {
    override suspend fun intercept(chain: coil3.intercept.Interceptor.Chain): coil3.request.ImageResult {
        val result = chain.proceed()
        if (result !is coil3.request.ErrorResult) return result
        val address = chain.request.data as? String ?: return result
        val inner = runCatching {
            java.net.URI(address).rawQuery?.split('&')?.firstNotNullOfOrNull { part ->
                java.net.URLDecoder.decode(part.substringAfter('=', ""), "UTF-8").takeIf {
                    part.substringBefore('=') == "url" && it.startsWith("http")
                }
            }
        }.getOrNull() ?: return result
        return chain.withRequest(chain.request.newBuilder().data(inner).build()).proceed()
    }
}
