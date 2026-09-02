package com.lagradost.cloudstream3.desktop.core.preference

inline fun <reified T, R : T> Preference<T>.getAndSet(crossinline block: (T) -> R) = set(
    block(get()),
)

fun Preference<Boolean>.toggle() = set(!get())

operator fun <T> Preference<Set<T>>.plusAssign(item: T) {
    set(get() + item)
}

operator fun <T> Preference<Set<T>>.minusAssign(item: T) {
    set(get() - item)
}
