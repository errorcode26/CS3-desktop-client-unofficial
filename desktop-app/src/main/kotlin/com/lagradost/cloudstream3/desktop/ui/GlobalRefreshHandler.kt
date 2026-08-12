package com.lagradost.cloudstream3.desktop.ui

import java.util.concurrent.CopyOnWriteArrayList

object GlobalRefreshHandler {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun register(listener: () -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    fun triggerRefresh() {
        listeners.forEach { it.invoke() }
    }
}
