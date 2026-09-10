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
                            NetworkConfig.getImageClient().newCall(request)
                        },
                    ),
                )
            }
            .crossfade(true)
            .build()
    }
}
