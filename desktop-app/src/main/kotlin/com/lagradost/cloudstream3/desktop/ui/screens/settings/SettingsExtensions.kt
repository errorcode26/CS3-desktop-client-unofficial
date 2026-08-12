package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.ui.components.PluginPlaceholderAvatar
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.*
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiEvent
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SettingsExtensions(
    onNavigate: (Config) -> Unit = {},
    initialTab: Int = 0,
) {
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember { ExtensionsViewModel() }
    val uiState by viewModel.uiState.collectAsState()
    val syncGen = uiState.syncGeneration

    val updatesHistory by DesktopDataStore.pluginUpdatesFlow
        .map { DesktopDataStore.getUpdatesHistory() }
        .flowOn(Dispatchers.IO)
        .collectAsState(initial = emptyList())

    var selectedSubTab by remember { mutableStateOf(initialTab.coerceIn(0, 3)) }
    val tabs = listOf("Browse Catalog", "Installed Plugins", "Repositories & Sources", "Update History")

    var isUpdatingAll by remember { mutableStateOf(false) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }
    var updateSearchQuery by remember { mutableStateOf("") }

    val isLightMode by AppearanceConfig.isLightMode.collectAsState()
    val posterWidthDp by AppearanceConfig.posterWidthDp.collectAsState()
    val unifiedGridMinSize = (posterWidthDp * 1.65f).dp

    LaunchedEffect(Unit) {
        viewModel.onEvent(ExtensionsUiEvent.OnLoadPluginsFromManager)
        viewModel.onEvent(ExtensionsUiEvent.OnRefreshInstalled)
    }

    LaunchedEffect(viewModel.effectFlow) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is ExtensionsUiEffect.ClearActiveProvider -> {
                    withContext(Dispatchers.IO) {
                        DesktopDataStore.removeKey("preferred_provider_name")
                    }
                }
                is ExtensionsUiEffect.ShowNotification -> { /* no-op */ }
            }
        }
    }

    val filteredUpdateHistory = remember(updatesHistory, updateSearchQuery) {
        if (updateSearchQuery.isBlank()) {
            updatesHistory
        } else {
            updatesHistory.filter { update ->
                update.pluginName.contains(updateSearchQuery, ignoreCase = true) ||
                    "v${update.version}".contains(updateSearchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Unified Top Control Bar ─────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: Tab switcher + Update All grouped together
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isLightMode) Color(0xFFE8EAF0) else Color(0xFF1E202A),
                    modifier = Modifier.height(38.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tabs.forEachIndexed { index, title ->
                            val isSelected = selectedSubTab == index
                            Surface(
                                onClick = { selectedSubTab = index },
                                shape = RoundedCornerShape(7.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                ) {
                                    Text(
                                        text = title,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        fontSize = 12.sp,
                                    )
                                    // Installed count badge
                                    if (index == 1 && uiState.installedPlugins.isNotEmpty()) {
                                        Spacer(Modifier.width(5.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        ) {
                                            Text(
                                                text = "${uiState.installedPlugins.size}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                    // Update history count badge
                                    if (index == 3 && updatesHistory.isNotEmpty()) {
                                        Spacer(Modifier.width(5.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                        ) {
                                            Text(
                                                text = "${updatesHistory.size}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Update All — contextually next to tabs, not floating on the far right
                FilledTonalButton(
                    onClick = {
                        if (!isUpdatingAll) {
                            isUpdatingAll = true
                            updateStatusMessage = null
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val newUpdates = DesktopRepositoryManager.autoUpdatePlugins(force = true)
                                    if (newUpdates.isNotEmpty()) {
                                        DesktopDataStore.addUpdateHistory(newUpdates)
                                        updateStatusMessage = "Updated ${newUpdates.size} plugins!"
                                    } else {
                                        updateStatusMessage = "All plugins are up to date"
                                    }
                                    viewModel.onEvent(ExtensionsUiEvent.OnRefreshInstalled)
                                } catch (e: Exception) {
                                    updateStatusMessage = "Update failed: ${e.localizedMessage ?: "Network error"}"
                                } finally {
                                    isUpdatingAll = false
                                }
                            }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(38.dp),
                    enabled = !isUpdatingAll,
                ) {
                    if (isUpdatingAll) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Updating...", fontSize = 12.sp)
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Update All",
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Update All", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Right: Stats pill only
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.height(38.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${uiState.plugins.size} Available",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        text = "${uiState.installedPlugins.size} Installed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // ── Tab Content ─────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedSubTab) {
                0 -> BrowseTab(
                    viewModel = viewModel,
                    syncGeneration = syncGen,
                    onNavigateToRepos = { selectedSubTab = 2 }
                )
                1 -> InstalledTab(
                    viewModel = viewModel,
                    syncGeneration = syncGen,
                    onNavigateToBrowse = { selectedSubTab = 0 }
                )
                2 -> RepositoriesTab(viewModel = viewModel)
                3 -> {
                    // Update History
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (updatesHistory.isNotEmpty()) {
                            OutlinedTextField(
                                value = updateSearchQuery,
                                onValueChange = { updateSearchQuery = it },
                                placeholder = {
                                    Text(
                                        "Filter update history by name or version...",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                trailingIcon = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(end = 8.dp),
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        ) {
                                            Text(
                                                text = "${filteredUpdateHistory.size} of ${updatesHistory.size} logged",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            )
                                        }
                                        if (updateSearchQuery.isNotEmpty()) {
                                            IconButton(onClick = { updateSearchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = if (isLightMode) Color.White else Color.White.copy(alpha = 0.05f),
                                    focusedContainerColor = if (isLightMode) Color.White else Color.White.copy(alpha = 0.08f),
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f),
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }

                        if (updatesHistory.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Outlined.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(34.dp),
                                        )
                                    }
                                    Text(
                                        text = "All plugins up to date",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "When repositories sync and download newer versions, logs will appear here.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = unifiedGridMinSize),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 16.dp),
                            ) {
                                items(filteredUpdateHistory, key = { "${it.pluginName}_${it.version}_${it.timestamp}" }) { update ->
                                    val date = remember(update.timestamp) { Date(update.timestamp) }
                                    val timeString = remember(date) {
                                        SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(date)
                                    }

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                        ),
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            // Top Row: Avatar + Name + Version + Status
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                val iconUrl = update.iconUrl
                                                if (!iconUrl.isNullOrBlank() && !DesktopRepositoryManager.isIconFailed(iconUrl)) {
                                                    AsyncImage(
                                                        model = iconUrl,
                                                        contentDescription = update.pluginName,
                                                        modifier = Modifier
                                                            .size(52.dp)
                                                            .clip(RoundedCornerShape(14.dp)),
                                                    )
                                                } else {
                                                    PluginPlaceholderAvatar(
                                                        name = update.pluginName,
                                                        internalName = update.pluginName,
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(12.dp))

                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    ) {
                                                        Text(
                                                            text = update.pluginName,
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )

                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                                            border = androidx.compose.foundation.BorderStroke(
                                                                1.dp,
                                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                                            ),
                                                        ) {
                                                            Text(
                                                                text = "v${update.version}",
                                                                color = MaterialTheme.colorScheme.primary,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            )
                                                        }
                                                    }

                                                    Text(
                                                        text = "Auto-updated via repository sync",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }

                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                                thickness = 1.dp,
                                            )

                                            // Bottom Row: Timestamp + Installed Pill
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.Schedule,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(13.dp),
                                                    )
                                                    Text(
                                                        text = timeString,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontWeight = FontWeight.Medium,
                                                    )
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color(0xFF4CAF50).copy(alpha = 0.14f),
                                                    border = androidx.compose.foundation.BorderStroke(
                                                        1.dp,
                                                        Color(0xFF4CAF50).copy(alpha = 0.35f),
                                                    ),
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(5.dp)
                                                                .clip(CircleShape)
                                                                .background(Color(0xFF4CAF50)),
                                                        )
                                                        Text(
                                                            text = "Installed",
                                                            color = Color(0xFF4CAF50),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }
}
