package com.lagradost.cloudstream3

import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

/**
 * Provides generic, privacy-preserving mock data for plugins querying system locale and timezone.
 * Intercepted via ASM bytecode remapping in CompatPluginClassLoader.
 */
object PrivacySpoofer {
    @JvmStatic
    fun getSpoofedLocale(): Locale = Locale.US

    @JvmStatic
    fun getSpoofedTimeZone(): TimeZone = TimeZone.getTimeZone("UTC")

    @JvmStatic
    fun getSpoofedZoneId(): ZoneId = ZoneId.of("UTC")
}
