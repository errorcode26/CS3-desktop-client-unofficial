package com.lagradost.cloudstream3.desktop.core.preference

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface Preference<T> {
    fun key(): String
    fun get(): T
    fun set(value: T)
    fun isSet(): Boolean
    fun delete()
    fun defaultValue(): T
    fun changes(): Flow<T>
    fun stateIn(scope: CoroutineScope): StateFlow<T>
}
