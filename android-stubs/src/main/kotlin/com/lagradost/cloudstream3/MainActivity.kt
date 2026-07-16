package com.lagradost.cloudstream3

/**
 * Stub for CloudStream Android's MainActivity.
 * Required by plugins that reflectively access MainActivity.Companion.
 */
class MainActivity {
    companion object {
        val afterPluginsLoadedEvent = com.lagradost.cloudstream3.utils.Event<Boolean>()
    }
}
