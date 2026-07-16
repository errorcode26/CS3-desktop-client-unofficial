package com.lagradost.cloudstream3.desktop.test

import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import javax.swing.JFrame

fun main() {
    val frame = JFrame("Test Window")
    frame.setSize(800, 600)
    frame.isVisible = true 
    
    Thread.sleep(1000)
    
    try {
        val hwnd = HWND(Native.getComponentPointer(frame))
        val user32 = User32.INSTANCE
        
        val style = user32.GetWindowLong(hwnd, WinUser.GWL_STYLE)
        val newStyle = style and (WinUser.WS_CAPTION or WinUser.WS_THICKFRAME).inv()
        user32.SetWindowLong(hwnd, WinUser.GWL_STYLE, newStyle)
        
        val screen = frame.graphicsConfiguration.bounds
        user32.SetWindowPos(
            hwnd,
            HWND(com.sun.jna.Pointer(0)),
            screen.x, screen.y,
            screen.width, screen.height,
            WinUser.SWP_FRAMECHANGED or WinUser.SWP_NOACTIVATE or WinUser.SWP_SHOWWINDOW
        )
        println("Success")
    } catch (e: Exception) {
        println("Failed: ${e.message}")
        e.printStackTrace()
    }
    
    Thread.sleep(3000)
    System.exit(0)
}
