package com.lagradost.cloudstream3.desktop.init

import com.lagradost.player.impl.proxy.LocalStreamProxy

/**
 * Starts the local stream proxy server used for media playback.
 */
fun initProxy() {
    LocalStreamProxy.clearanceUaProvider = { host ->
        com.lagradost.cloudstream3.network.CloudflareKiller.getSavedUserAgent(host)
    }
    LocalStreamProxy.start()
}
