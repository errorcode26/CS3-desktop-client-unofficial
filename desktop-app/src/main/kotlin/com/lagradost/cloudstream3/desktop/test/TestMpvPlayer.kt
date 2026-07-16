package com.lagradost.cloudstream3.desktop.test

import com.lagradost.cloudstream3.desktop.player.MpvLibrary
import com.sun.jna.Native
import java.awt.Canvas
import java.awt.Color
import java.io.File
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.Timer

fun main() {
    System.setProperty("sun.awt.noerasebackground", "true")

    SwingUtilities.invokeLater {
        val window = JFrame("MPV Test Player")
        window.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        window.setSize(800, 600)

        val canvas = object : Canvas() {
            override fun paint(g: java.awt.Graphics?) {}
            override fun update(g: java.awt.Graphics?) {}
        }
        canvas.background = Color.BLACK
        window.add(canvas)

        window.setLocationRelativeTo(null) // center on screen
        window.isVisible = true

        val hwnd = Native.getComponentID(canvas)
        println("AWT Canvas HWND: $hwnd")

        // Initialize MPV underneath!
        val mpvDir = File("appResources/windows/mpv").absoluteFile
        System.setProperty("jna.library.path", mpvDir.absolutePath)
        val lib = MpvLibrary.INSTANCE
        val mpvHandle = lib.mpv_create()
        if (mpvHandle != null) {
            lib.mpv_set_option_string(mpvHandle, "wid", hwnd.toString())
            lib.mpv_set_option_string(mpvHandle, "vo", "gpu")
            lib.mpv_initialize(mpvHandle)
            lib.mpv_command_string(mpvHandle, "loadfile \"https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/1080/Big_Buck_Bunny_1080_10s_30MB.mp4\"")
            println("MPV initialized and loading Big Buck Bunny!")

            Timer(1000) {
                val posStr = MpvLibrary.getPropertyString(mpvHandle, "time-pos")
                val isCoreIdle = MpvLibrary.getPropertyString(mpvHandle, "core-idle")
                val isIdle = MpvLibrary.getPropertyString(mpvHandle, "idle-active")
                val vid = MpvLibrary.getPropertyString(mpvHandle, "vid")
                println("MPV Status - time-pos: $posStr | core-idle: $isCoreIdle | idle-active: $isIdle | vid: $vid")
            }.start()
        } else {
            println("Failed to initialize MPV.")
        }
    }
}
