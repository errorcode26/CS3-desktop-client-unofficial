package com.lagradost.cloudstream3.desktop.init

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.FrameWindowScope
import com.lagradost.cloudstream3.desktop.ui.FullscreenController
import java.awt.Color
import java.awt.Dimension
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.JFrame

class FullscreenHelperState(
    val controller: FullscreenController,
    val onKeyEvent: (KeyEvent) -> Boolean,
    val attachToWindow: (Window) -> Unit,
)

@Composable
fun rememberFullscreenHelper(): FullscreenHelperState {
    val windowRef = remember { AtomicReference<Window?>(null) }
    val controller = remember {
        FullscreenController(
            isFullscreen = false,
            toggle = { },
            popupKey = 0,
            contentAreaPx = Pair(0, 0),
        )
    }

    // Snapshot the drawable content area before hiding the title bar.
    var savedContentPxBeforeFullscreen = remember<Pair<Int, Int>?> { null }

    val toggleFunc = remember(controller) {
        {
            val w = windowRef.get() as? JFrame
            if (w != null) {
                if (controller.isFullscreen) {
                    savedContentPxBeforeFullscreen?.let { saved ->
                        controller.contentAreaPx = saved
                    }
                    exitWindowsFullscreen(w)
                    controller.isFullscreen = false
                } else {
                    val pane = w.contentPane
                    savedContentPxBeforeFullscreen = Pair(pane.width, pane.height)
                    enterWindowsFullscreen(w)
                    controller.isFullscreen = true
                }
            }
        }
    }

    if (controller.toggle != toggleFunc) {
        controller.toggle = toggleFunc
    }

    val onKeyEvent = remember(controller, toggleFunc) {
        { keyEvent: KeyEvent ->
            if (keyEvent.key == Key.F11 && keyEvent.type == KeyEventType.KeyDown) {
                toggleFunc()
                true
            } else if (keyEvent.key == Key.F12 && keyEvent.type == KeyEventType.KeyDown) {
                com.lagradost.cloudstream3.desktop.ui.screens.dev.DevStudioState.toggle()
                true
            } else if (keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown && controller.isFullscreen) {
                toggleFunc()
                true
            } else if (keyEvent.key == Key.F5 && keyEvent.type == KeyEventType.KeyDown) {
                com.lagradost.cloudstream3.desktop.ui.GlobalRefreshHandler.triggerRefresh()
                true
            } else if (keyEvent.isCtrlPressed && keyEvent.key == Key.R && keyEvent.type == KeyEventType.KeyDown) {
                com.lagradost.cloudstream3.desktop.ui.GlobalRefreshHandler.triggerRefresh()
                true
            } else {
                false
            }
        }
    }

    val attachFunc = remember(controller) {
        { window: Window ->
            windowRef.set(window)
            if (controller.mainFrame != window) {
                controller.mainFrame = window
            }
        }
    }

    return remember(controller, onKeyEvent, attachFunc) {
        FullscreenHelperState(controller, onKeyEvent, attachFunc)
    }
}

@Composable
fun FrameWindowScope.setupWindowBackgroundAndListeners(fullscreenController: FullscreenController) {
    SideEffect {
        window.minimumSize = Dimension(1000, 700)
        val black = Color(0x0D, 0x0D, 0x0D)
        window.background = black
        window.rootPane.background = black
        window.contentPane.background = black
        (window.contentPane as? JComponent)?.isOpaque = true
        setWindowsDarkMode(window)
    }

    DisposableEffect(Unit) {
        onDispose {
            val w = window as? JFrame
            if (w != null && fullscreenController.isFullscreen) {
                exitWindowsFullscreen(w)
            }
        }
    }

    DisposableEffect(Unit) {
        val contentPane = (window as? JFrame)?.contentPane
        val listener = object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                fullscreenController.contentAreaPx = Pair(e.component.width, e.component.height)
            }
        }
        contentPane?.addComponentListener(listener)
        if (contentPane != null) {
            fullscreenController.contentAreaPx = Pair(contentPane.width, contentPane.height)
        }
        onDispose { contentPane?.removeComponentListener(listener) }
    }
}
