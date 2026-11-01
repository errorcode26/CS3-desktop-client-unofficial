package com.lagradost.cloudstream3.desktop.sandbox

import com.lagradost.common.storage.DesktopDataStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SandboxedDataStoreWorkerTest {

    private val originalWorkerMode = DesktopDataStore.isWorkerMode
    private val originalPreCacheLoaded = DesktopDataStore.isPreCacheLoaded

    @BeforeEach
    fun setUp() {
        DesktopDataStore.isWorkerMode = true
        DesktopDataStore.isPreCacheLoaded = true
        DesktopDataStore.rawKeyCache.clear()
    }

    @AfterEach
    fun tearDown() {
        DesktopDataStore.isWorkerMode = originalWorkerMode
        DesktopDataStore.isPreCacheLoaded = originalPreCacheLoaded
        DesktopDataStore.onPersistKeyOverride = null
        DesktopDataStore.onRemoveKeyOverride = null
        DesktopDataStore.rawKeyCache.clear()
    }

    @Test
    fun testMissingKeyReturnsNullWithoutSQLiteAccess() {
        val result = DesktopDataStore.getKey<String>("non_existent_key_12345")
        assertNull(result, "Missing key in worker mode should return null without invoking SQLite")
    }

    @Test
    fun testTypedMissingKeyReturnsNullWithoutSQLiteAccess() {
        val result = DesktopDataStore.getKey("non_existent_typed_key", Integer::class.java)
        assertNull(result, "Typed missing key in worker mode should return null without invoking SQLite")
    }

    @Test
    fun testSetKeyPersistsToMemoryAndTriggersOverride() {
        var overriddenKey: String? = null
        var overriddenJson: String? = null

        DesktopDataStore.onPersistKeyOverride = { key, json ->
            overriddenKey = key
            overriddenJson = json
        }

        DesktopDataStore.setKey("sample_setting", "hello_world")

        assertEquals("sample_setting", overriddenKey)
        assertTrue(overriddenJson?.contains("hello_world") == true)
        assertEquals("hello_world", DesktopDataStore.getKey<String>("sample_setting"))
    }

    @Test
    fun testRemoveKeyRemovesFromMemoryAndTriggersOverride() {
        var removedKey: String? = null

        DesktopDataStore.onRemoveKeyOverride = { key ->
            removedKey = key
        }

        DesktopDataStore.setKey("to_be_removed", 42)
        assertEquals(42, DesktopDataStore.getKey<Int>("to_be_removed"))

        DesktopDataStore.removeKey("to_be_removed")
        assertEquals("to_be_removed", removedKey)
        assertNull(DesktopDataStore.getKey<Int>("to_be_removed"))
        assertFalse(DesktopDataStore.containsKey("to_be_removed"))
    }

    @Test
    fun testGetAllKeysWithPrefixFiltersInMemory() {
        DesktopDataStore.setKey("ext_prefix_1", "v1")
        DesktopDataStore.setKey("ext_prefix_2", "v2")
        DesktopDataStore.setKey("other_prefix", "v3")

        val matched = DesktopDataStore.getAllKeysWithPrefix("ext_prefix_")
        assertEquals(2, matched.size)
        assertTrue(matched.contains("ext_prefix_1"))
        assertTrue(matched.contains("ext_prefix_2"))
        assertFalse(matched.contains("other_prefix"))
    }
}
