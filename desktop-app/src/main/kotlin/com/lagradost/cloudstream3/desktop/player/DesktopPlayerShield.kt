package com.lagradost.cloudstream3.desktop.player

import java.awt.Color
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import java.awt.Window
import java.lang.ref.WeakReference
import javax.swing.JWindow
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import javax.swing.Timer

object DesktopPlayerShield {
    private var shieldWindow: WeakReference<JWindow>? = null
    private var hideTimer: Timer? = null

    fun showForActiveWindow() {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        if (!isWindows) return

        SwingUtilities.invokeLater {
            val owner = activeOwnerWindow() ?: return@invokeLater
            val bounds = ownerContentBounds(owner) ?: return@invokeLater
            hideTimer?.stop()
            hideTimer = null

            val currentShield = shieldWindow?.get()
            val shield = if (currentShield != null && currentShield.owner === owner) {
                currentShield
            } else {
                currentShield?.dispose()
                JWindow(owner).also { window ->
                    window.background = Color.BLACK
                    window.contentPane.background = Color.BLACK
                    window.focusableWindowState = false
                    window.setType(Window.Type.POPUP)
                    shieldWindow = WeakReference(window)
                }
            }
            shield.bounds = bounds
            if (!shield.isVisible) {
                shield.isVisible = true
            }
            shield.toFront()
        }
    }

    fun hideAfter(delayMs: Int = 100) {
        SwingUtilities.invokeLater {
            hideTimer?.stop()
            hideTimer = Timer(delayMs) {
                hideNow()
            }.apply {
                isRepeats = false
                start()
            }
        }
    }

    fun hide() {
        SwingUtilities.invokeLater {
            hideTimer?.stop()
            hideTimer = null
            hideNow()
        }
    }

    private fun hideNow() {
        val shield = shieldWindow?.get() ?: return
        if (shield.isVisible) {
            shield.isVisible = false
        }
        shield.dispose()
        shieldWindow = null
    }

    private fun activeOwnerWindow(): Window? {
        val active = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
        if (active?.isShowing == true) return active
        return Window.getWindows()
            .firstOrNull { it.isShowing && it !is JWindow }
    }

    private fun ownerContentBounds(owner: Window): Rectangle? {
        val content = (owner as? RootPaneContainer)?.contentPane
        return runCatching {
            if (content != null && content.isShowing) {
                val location = content.locationOnScreen
                Rectangle(location.x, location.y, content.width, content.height)
            } else {
                val location = owner.locationOnScreen
                Rectangle(location.x, location.y, owner.width, owner.height)
            }
        }.getOrNull()
    }
}
