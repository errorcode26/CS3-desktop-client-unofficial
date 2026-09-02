package com.lagradost.cloudstream3.desktop.core.preference

interface PreferenceStore {
    fun getString(key: String, defaultValue: String = ""): Preference<String>
    fun getLong(key: String, defaultValue: Long = 0L): Preference<Long>
    fun getInt(key: String, defaultValue: Int = 0): Preference<Int>
    fun getFloat(key: String, defaultValue: Float = 0f): Preference<Float>
    fun getBoolean(key: String, defaultValue: Boolean = false): Preference<Boolean>
    fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): Preference<Set<String>>
    fun <T : Enum<T>> getEnum(key: String, defaultValue: T, enumClass: Class<T>): Preference<T>
    fun <T> getObject(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T>
}
