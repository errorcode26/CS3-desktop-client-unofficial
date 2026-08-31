package com.lagradost.cloudstream3.desktop.utils

import com.lagradost.common.storage.DesktopDataStore
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object NativeFileDialog {
    enum class Category(val key: String) {
        WALLPAPER("pref_last_dir_wallpaper"),
        FONT("pref_last_dir_font"),
        MEDIA("pref_last_dir_media"),
        DOWNLOADS("pref_last_dir_downloads"),
        SUBTITLES("pref_last_dir_subtitles"),
        EXTENSIONS("pref_last_dir_extensions"),
        BACKUP("pref_last_dir_backup"),
        GENERAL("pref_last_dir_general"),
    }

    /**
     * Spawns a native file dialog properly attached to the active window owner with persistent directory memory per category.
     * Prevents AWT Tree Lock deadlocks and Compose Skia EDT freezes.
     */
    fun open(
        title: String,
        mode: Int = FileDialog.LOAD,
        allowedExtensions: List<String> = emptyList(),
        category: Category = Category.GENERAL,
        initialDirectory: String? = null,
    ): File? {
        val activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
        val parentFrame = activeWindow as? Frame ?: Frame()
        val isDummyFrame = activeWindow !is Frame

        try {
            val dialog = FileDialog(parentFrame, title, mode).apply {
                val savedDir = initialDirectory ?: DesktopDataStore.getKey<String>(category.key)
                if (savedDir != null && File(savedDir).exists()) {
                    directory = savedDir
                }
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
            if (dir != null) {
                appScope.launch(Dispatchers.IO) {
                    DesktopDataStore.setKey(category.key, dir)
                }
            }
            return if (dir != null && file != null) File(dir, file) else null
        } finally {
            if (isDummyFrame) {
                parentFrame.dispose()
            }
        }
    }

    /**
     * Opens a directory picker with persistent directory memory per category.
     */
    fun chooseDirectory(
        title: String,
        category: Category = Category.DOWNLOADS,
        initialDirectory: String? = null,
    ): File? {
        val savedDir = initialDirectory ?: DesktopDataStore.getKey<String>(category.key)
        val chooser = javax.swing.JFileChooser().apply {
            fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
            dialogTitle = title
            if (savedDir != null && File(savedDir).exists()) {
                currentDirectory = File(savedDir)
            }
        }
        val result = chooser.showOpenDialog(null)
        if (result == javax.swing.JFileChooser.APPROVE_OPTION && chooser.selectedFile != null) {
            val selected = chooser.selectedFile
            appScope.launch(Dispatchers.IO) {
                DesktopDataStore.setKey(category.key, selected.absolutePath)
            }
            return selected
        }
        return null
    }
}
