package com.lagradost.cloudstream3.desktop.utils

import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.io.File

object NativeFileDialog {
    /**
     * Spawns a native file dialog properly attached to the active window owner.
     * Prevents AWT Tree Lock deadlocks and Compose Skia EDT freezes.
     */
    fun open(
        title: String,
        mode: Int = FileDialog.LOAD,
        allowedExtensions: List<String> = emptyList(),
    ): File? {
        val activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
        val parentFrame = activeWindow as? Frame ?: Frame()
        val isDummyFrame = activeWindow !is Frame

        try {
            val dialog = FileDialog(parentFrame, title, mode).apply {
                if (allowedExtensions.isNotEmpty()) {
                    setFilenameFilter { _, name ->
                        val lower = name.lowercase()
                        allowedExtensions.any { ext ->
                            val cleanExt = if (ext.startsWith(".")) ext.lowercase() else ".${ext.lowercase()}"
                            lower.endsWith(cleanExt)
                        }
                    }
                }
            }
            dialog.isVisible = true

            val dir = dialog.directory
            val file = dialog.file
            return if (dir != null && file != null) File(dir, file) else null
        } finally {
            if (isDummyFrame) {
                parentFrame.dispose()
            }
        }
    }
}
