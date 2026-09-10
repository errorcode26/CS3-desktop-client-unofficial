package com.lagradost.common.storage

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PluginSettingsSchemaRegistryTest {

    @BeforeTest
    fun setUp() {
        PluginSettingsSchemaRegistry.schemas.clear()
        PluginSettingsSchemaRegistry.schemaUpdates.value = 0
    }

    @Test
    fun testRegisterAndRetrieveSettings() {
        PluginSettingsSchemaRegistry.register(
            pluginPrefName = "SamplePlugin_",
            key = "concurrency_limit",
            type = "Int",
            defaultValue = 4,
        )

        val settings = PluginSettingsSchemaRegistry.getSettingsForPlugin("SamplePlugin_")
        assertEquals(1, settings.size)
        assertEquals("concurrency_limit", settings[0].key)
        assertEquals("Int", settings[0].type)
        assertEquals(4, settings[0].defaultValue)
    }

    @Test
    fun testHiddenKeyFiltering() {
        val pref = "TestPlugin_"
        // Register various internal bookkeeping keys
        PluginSettingsSchemaRegistry.register(pref, "cookie_session", "String", "xyz")
        PluginSettingsSchemaRegistry.register(pref, "cf_clearance_token", "String", "token")
        PluginSettingsSchemaRegistry.register(pref, "provider_order", "String", "[1, 2]")
        PluginSettingsSchemaRegistry.register(pref, "seen_history", "StringSet", emptySet<String>())
        PluginSettingsSchemaRegistry.register(pref, "api_cache", "String", "cached")
        PluginSettingsSchemaRegistry.register(pref, "_internal_flag", "Boolean", true)
        PluginSettingsSchemaRegistry.register(pref, "dunder__state", "String", "internal")

        // Register a human-configurable setting
        PluginSettingsSchemaRegistry.register(pref, "scrape_concurrency", "Int", 8)

        val settings = PluginSettingsSchemaRegistry.getSettingsForPlugin(pref)
        assertEquals(1, settings.size, "All internal bookkeeping keys must be filtered out")
        assertEquals("scrape_concurrency", settings[0].key)

        // Verify hasSettings returns true when real settings exist
        assertTrue(PluginSettingsSchemaRegistry.hasSettings(pref))

        // When only hidden keys exist, hasSettings should return false
        PluginSettingsSchemaRegistry.schemas.clear()
        PluginSettingsSchemaRegistry.register(pref, "cookie_session", "String", "xyz")
        assertFalse(PluginSettingsSchemaRegistry.hasSettings(pref), "hasSettings must be false if only hidden keys exist")
    }

    @Test
    fun testOptionsRegistrationAndPreservation() {
        val options = mapOf(
            "Primary Mirror" to "https://api.example.com",
            "Secondary Mirror" to "https://backup.example.com",
        )

        PluginSettingsSchemaRegistry.register(
            pluginPrefName = "MirrorPlugin_",
            key = "server_endpoint",
            type = "String",
            defaultValue = "https://api.example.com",
            options = options,
        )

        val settings = PluginSettingsSchemaRegistry.getSettingsForPlugin("MirrorPlugin_")
        assertEquals(1, settings.size)
        assertNotNull(settings[0].options)
        assertEquals(2, settings[0].options?.size)
        assertEquals("https://api.example.com", settings[0].options?.get("Primary Mirror"))
    }

    @Test
    fun testProvisionalProbeAndTypeRefinement() {
        val pref = "ProbePlugin_"

        // Step 1: Probe via contains() registers as provisional Boolean
        PluginSettingsSchemaRegistry.register(pref, "custom_timeout", "Boolean", true)
        var settings = PluginSettingsSchemaRegistry.getSettingsForPlugin(pref)
        assertEquals(1, settings.size)
        assertEquals("Boolean", settings[0].type)

        // Step 2: Plugin performs typed read getInt() -> schema refines to Int
        PluginSettingsSchemaRegistry.register(pref, "custom_timeout", "Int", 30)
        settings = PluginSettingsSchemaRegistry.getSettingsForPlugin(pref)
        assertEquals(1, settings.size)
        assertEquals("Int", settings[0].type)
        assertEquals(30, settings[0].defaultValue)
    }
}
