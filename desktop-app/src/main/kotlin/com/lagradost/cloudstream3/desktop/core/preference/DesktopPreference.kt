package com.lagradost.cloudstream3.desktop.core.preference

import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class DesktopPreference<T>(
    private val key: String,
    private val defaultValue: T,
    private val readFromStore: (String, T) -> T,
    private val writeToStore: (String, T) -> Unit,
) : Preference<T> {

    private val stateFlow: MutableStateFlow<T> by lazy {
        MutableStateFlow(readFromStore(key, defaultValue))
    }

    override fun key(): String = key

    override fun defaultValue(): T = defaultValue

    override fun get(): T = stateFlow.value

    override fun set(value: T) {
        stateFlow.value = value
        writeToStore(key, value)
    }

    override fun isSet(): Boolean = DesktopDataStore.containsKey(key)

    override fun delete() {
        stateFlow.value = defaultValue
        DesktopDataStore.removeKey(key)
    }

    override fun changes(): Flow<T> = stateFlow.asStateFlow()

    override fun stateIn(scope: CoroutineScope): StateFlow<T> = stateFlow.asStateFlow()
}
