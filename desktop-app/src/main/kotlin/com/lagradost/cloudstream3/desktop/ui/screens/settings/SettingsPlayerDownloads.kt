package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.downloader.DesktopDownloadManager
import com.lagradost.cloudstream3.desktop.player.PlayerConfig
import com.lagradost.cloudstream3.desktop.ui.components.AppToastManager
import com.lagradost.cloudstream3.desktop.utils.NativeFileDialog
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Downloads and storage engine settings screen.
 */
@Composable
fun SettingsPlayerDownloadsScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var currentPath by remember {
        mutableStateOf(DesktopDataStore.getKey<String>(DesktopDataStore.PREF_DOWNLOAD_PATH) ?: DesktopDownloadManager.downloadsDir.absolutePath)
    }

    val downloadThreads = uiState.floatSettings[DesktopDataStore.PREF_DOWNLOAD_THREADS] ?: (DesktopDataStore.getKey<Float>(DesktopDataStore.PREF_DOWNLOAD_THREADS) ?: 8f)
    val maxConcurrent = uiState.floatSettings[DesktopDataStore.PREF_DOWNLOAD_MAX_CONCURRENT] ?: (DesktopDataStore.getKey<Float>(DesktopDataStore.PREF_DOWNLOAD_MAX_CONCURRENT) ?: 2f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsGroupCard(title = "Download Visibility & Storage Location") {
            MviSettingsToggle(
                key = DesktopDataStore.PREF_ENABLE_DOWNLOAD_BUTTONS,
                label = "Show Download Buttons",
                subtitle = "Display direct 1-click download buttons on movie details, episode cards, and right-click menus",
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = true,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Download Storage Directory",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = currentPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = {
                            val selected = NativeFileDialog.chooseDirectory(
                                title = "Select Download Directory",
                                category = NativeFileDialog.Category.DOWNLOADS,
                                initialDirectory = currentPath,
                            )
                            if (selected != null) {
                                val newPath = selected.absolutePath
                                currentPath = newPath
                                scope.launch(Dispatchers.IO) {
                                    DesktopDataStore.setKey(DesktopDataStore.PREF_DOWNLOAD_PATH, newPath)
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Change Folder...")
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                java.awt.Desktop.getDesktop().open(File(currentPath))
                            } catch (e: Exception) {
                                AppLogger.e("Failed to open download folder in explorer", e)
                                AppToastManager.showError("Unable to open folder in system explorer")
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Open in Explorer")
                    }
                }
            }
        }

        SettingsGroupCard(title = "Turbo Acceleration & Queue Limits") {
            MviSettingsSlider(
                key = DesktopDataStore.PREF_DOWNLOAD_THREADS,
                label = "Parallel Turbo Download Threads (Chunks)",
                subtitle = "${downloadThreads.toInt()} parallel chunk workers per file",
                valueRange = 1f..16f,
                steps = 14,
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = 8f,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            MviSettingsSlider(
                key = DesktopDataStore.PREF_DOWNLOAD_MAX_CONCURRENT,
                label = "Maximum Concurrent Active Downloads",
                subtitle = "${maxConcurrent.toInt()} simultaneous downloading tasks",
                valueRange = 1f..5f,
                steps = 3,
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = 2f,
            )
        }

        SettingsGroupCard(title = "Screenshots & Media Capture") {
            var currentScreenshotPath by remember {
                mutableStateOf(PlatformPaths.screenshotsDir.absolutePath)
            }
            val captureScope = rememberCoroutineScope()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Screenshot Output Directory",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = currentScreenshotPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = {
                            val selected = NativeFileDialog.chooseDirectory(
                                title = "Select Screenshot Directory",
                                category = NativeFileDialog.Category.GENERAL,
                                initialDirectory = currentScreenshotPath,
                            )
                            if (selected != null) {
                                val newPath = selected.absolutePath
                                currentScreenshotPath = newPath
                                captureScope.launch(Dispatchers.IO) {
                                    DesktopDataStore.setKey(PlayerConfig.PREF_SCREENSHOT_DIR, newPath)
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Change Folder...")
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                java.awt.Desktop.getDesktop().open(File(currentScreenshotPath))
                            } catch (e: Exception) {
                                AppLogger.e("Failed to open screenshot folder in explorer", e)
                                AppToastManager.showError("Unable to open folder in system explorer")
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Open in Explorer")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            MviSettingsDropdown(
                key = PlayerConfig.PREF_SCREENSHOT_FORMAT,
                label = "Screenshot Format",
                subtitle = "Format used when saving video captures (Shortcuts: Shift+S or Ctrl+S)",
                options = listOf(
                    "png" to "PNG (Lossless Quality)",
                    "jpg" to "JPEG (High Quality, Compact)",
                    "webp" to "WebP (Modern Compact)",
                ),
                uiState = uiState,
                onEvent = viewModel::onEvent,
                defaultValue = "png",
            )
        }
    }
}
