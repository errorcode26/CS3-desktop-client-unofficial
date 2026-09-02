package com.lagradost.cloudstream3.desktop.core.preference

import com.lagradost.common.storage.DesktopDataStore
import java.util.concurrent.ConcurrentHashMap

class DesktopPreferenceStore : PreferenceStore {

    private val preferenceCache = ConcurrentHashMap<String, Preference<*>>()

    @Suppress("UNCHECKED_CAST")
    private fun <T> getOrCreate(key: String, create: () -> Preference<T>): Preference<T> {
        return preferenceCache.computeIfAbsent(key) { create() } as Preference<T>
    }

    override fun getString(key: String, defaultValue: String): Preference<String> {
        return getOrCreate(key) {
            DesktopPreference(
                key = key,
                defaultValue = defaultValue,
                readFromStore = { k, d -> DesktopDataStore.getKey<String>(k) ?: d },
                writeToStore = { k, v -> DesktopDataStore.setKey(k, v) },
            )
        }
    }

    override fun getLong(key: String, defaultValue: Long): Preference<Long> {
        return getOrCreate(key) {
            DesktopPreference(
                key = key,
                defaultValue = defaultValue,
                readFromStore = { k, d -> DesktopDataStore.getKey<Long>(k) ?: d },
                writeToStore = { k, v -> DesktopDataStore.setKey(k, v) },
            )
        }
    }

    override fun getInt(key: String, defaultValue: Int): Preference<Int> {
        return getOrCreate(key) {
            DesktopPreference(
                key = key,
                defaultValue = defaultValue,
                readFromStore = { k, d -> DesktopDataStore.getKey<Int>(k) ?: d },
                writeToStore = { k, v -> DesktopDataStore.setKey(k, v) },
            )
        }
    }

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> {
        return getOrCreate(key) {
            DesktopPreference(
                key = key,
                defaultValue = defaultValue,
                readFromStore = { k, d -> (DesktopDataStore.getKey<Number>(k)?.toFloat()) ?: d },
                writeToStore = { k, v -> DesktopDataStore.setKey(k, v) },
            )
        }
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> {
        return getOrCreate(key) {
            DesktopPreference(
                key = key,
                defaultValue = defaultValue,
                readFromStore = { k, d -> DesktopDataStore.getKey<Boolean>(k) ?: d },
                writeToStore = { k, v -> DesktopDataStore.setKey(k, v) },
            )
        }
    }

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> {
        return getOrCreate(key) {
            DesktopPreference(
                key = key,
                defaultValue = defaultValue,
                readFromStore = { k, d -> DesktopDataStore.getKey<List<String>>(k)?.toSet() ?: d },
                writeToStore = { k, v -> DesktopDataStore.setKey(k, v.toList()) },
            )
        }
    }

    override fun <T : Enum<T>> getEnum(key: String, defaultValue: T, enumClass: Class<T>): Preference<T> {
        return getOrCreate(key) {
            DesktopPreference(
                key = key,
                defaultValue = defaultValue,
                readFromStore = { k, d ->
                    val raw = DesktopDataStore.getKey<String>(k)
                    if (raw != null) {
                        try {
                            java.lang.Enum.valueOf(enumClass, raw)
                        } catch (_: Exception) {
                            d
                        }
                    } else {
                        d
                    }
                },
                writeToStore = { k, v -> DesktopDataStore.setKey(k, v.name) },
            )
        }
    }

    override fun <T> getObject(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> {
        return getOrCreate(key) {
            DesktopPreference(
                key = key,
                defaultValue = defaultValue,
                readFromStore = { k, d ->
                    val raw = DesktopDataStore.getKey<String>(k)
                    if (raw != null) {
                        try {
                            deserializer(raw)
                        } catch (_: Exception) {
                            d
                        }
                    } else {
                        d
                    }
                },
                writeToStore = { k, v ->
                    try {
                        DesktopDataStore.setKey(k, serializer(v))
                    } catch (_: Exception) {}
                },
            )
        }
    }
}
