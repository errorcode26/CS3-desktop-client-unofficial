package com.lagradost.cloudstream3.desktop.ui

import java.util.concurrent.CopyOnWriteArrayList

enum class NavScrollTarget {
    HOME,
    EXPLORE,
}

object GlobalScrollToTopHandler {
    private val listeners = CopyOnWriteArrayList<Pair<NavScrollTarget, () -> Unit>>()

    fun register(target: NavScrollTarget, listener: () -> Unit): () -> Unit {
        val entry = target to listener
        listeners.add(entry)
        return { listeners.remove(entry) }
    }

    fun trigger(target: NavScrollTarget) {
        listeners.filter { it.first == target }.forEach { it.second.invoke() }
    }
}
