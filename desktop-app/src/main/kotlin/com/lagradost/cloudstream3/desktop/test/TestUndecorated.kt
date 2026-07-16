package com.lagradost.cloudstream3.desktop.test

import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef.HWND
import java.awt.Window as AwtWindow

fun main() = application {
    var isUndecorated by remember { mutableStateOf(false) }
    var windowRef by remember { mutableStateOf<AwtWindow?>(null) }
    
    LaunchedEffect(isUndecorated) {
        windowRef?.let { w ->
            println("HWND when undecorated=$isUndecorated: ${Native.getComponentPointer(w)}")
        }
    }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        isUndecorated = true
        kotlinx.coroutines.delay(2000)
        exitApplication()
    }
    
    Window(
        onCloseRequest = ::exitApplication,
        undecorated = isUndecorated
    ) {
        windowRef = window
    }
}
