package com.lagradost.cloudstream3.desktop.player

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class MpvEventTest {
    @Test
    fun testMpvEventLoopAndJnaMapping() {
        System.setProperty("jna.library.path", System.getProperty("user.dir") + "/..")
        println("Loaded MPV Library instance: ${MpvLibrary.INSTANCE}")
        val mpv = MpvLibrary.INSTANCE.mpv_create()
        println("mpv_create returned: $mpv")
        if (mpv == null) {
            throw RuntimeException("mpv_create failed! Returning null.")
        }
        
        val initRes = MpvLibrary.INSTANCE.mpv_initialize(mpv)
        assertTrue(initRes >= 0, "mpv_initialize failed")

        // 1 is MPV_FORMAT_STRING
        MpvLibrary.INSTANCE.mpv_observe_property(mpv, 42L, "volume", 1)

        // Change the volume to trigger an event
        MpvLibrary.INSTANCE.mpv_set_property_string(mpv, "volume", "50.0")

        var foundVolumeEvent = false
        for (i in 0..10) {
            val eventPtr = MpvLibrary.INSTANCE.mpv_wait_event(mpv, 1.0)
            if (eventPtr == null) break
            
            val event = MpvLibrary.MpvEvent(eventPtr)
            println("Received Event ID: ${event.event_id}, userdata: ${event.reply_userdata}")
            
            // MPV_EVENT_PROPERTY_CHANGE = 22
            if (event.event_id == 22 && event.reply_userdata == 42L) {
                val propPtr = event.data
                if (propPtr != null) {
                    val prop = MpvLibrary.MpvEventProperty(propPtr)
                    println("Property name: ${prop.name}, format: ${prop.format}")
                    if (prop.name == "volume") {
                        foundVolumeEvent = true
                        // Data is char** for MPV_FORMAT_STRING
                        if (prop.format == 1 && prop.data != null) {
                            val stringPtr = prop.data!!.getPointer(0)
                            val value = stringPtr.getString(0)
                            println("Volume value string: $value")
                        }
                    }
                }
            }
        }
        
        MpvLibrary.INSTANCE.mpv_terminate_destroy(mpv)
        assertTrue(foundVolumeEvent, "Failed to catch MPV_EVENT_PROPERTY_CHANGE for volume")
    }
}
