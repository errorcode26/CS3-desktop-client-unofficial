package com.lagradost.runtime.loader.stubs

import com.lagradost.cloudstream3.app
import com.lagradost.common.logging.AppLogger
import com.lagradost.nicehttp.Requests

object RequestsStub {
    private const val TAG = "RequestsStub"

    // Track all registered plugin clients weakly so global network updates propagate
    val syncedClients: MutableSet<Requests> = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<Requests, Boolean>()
    )

    @JvmStatic
    fun attachGlobalBaseClient(requests: Requests?) {
        if (requests == null) return
        try {
            val globalBase = app.baseClient
            val currentClient = requests.baseClient
            val hasCfKiller = currentClient.interceptors.any {
                it.javaClass.name.contains("CloudflareKiller")
            }
            if (!hasCfKiller) {
                requests.baseClient = globalBase
                syncedClients.add(requests)
                AppLogger.d("$TAG: Attached global baseClient (CloudflareKiller + SettledPageCache + DesktopCookieJar) to Requests instance.")
            }
        } catch (t: Throwable) {
            AppLogger.w("$TAG: Failed to attach global baseClient: ${t.message}")
        }
    }

    fun syncAllKnownClients() {
        val globalBase = app.baseClient
        val iterator = syncedClients.iterator()
        while (iterator.hasNext()) {
            try {
                val client = iterator.next()
                client.baseClient = globalBase
            } catch (_: Throwable) {}
        }
    }
}
