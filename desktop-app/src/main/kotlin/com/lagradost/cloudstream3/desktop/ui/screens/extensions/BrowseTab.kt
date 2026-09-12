package com.lagradost.cloudstream3.desktop.ui.screens.extensions

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.components.AppDropdownMenu
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.desktop.ui.components.ExtensionCard
import com.lagradost.cloudstream3.desktop.ui.components.FlagImage
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiEvent
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig

@Composable
fun BrowseTab(
    viewModel: ExtensionsViewModel,
    syncGeneration: Int,
    onNavigateToRepos: () -> Unit = {},
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedLanguages by remember { mutableStateOf(emptySet<String>()) }
    var selectedCategories by remember { mutableStateOf(emptySet<String>()) }
    var selectedRepos by remember { mutableStateOf(emptySet<String>()) }

    val uiState by viewModel.uiState.collectAsState()
    val plugins = uiState.plugins
    val isFetching = uiState.isFetching
    val statusText = uiState.statusText
    val pluginRequiringBypass = uiState.pluginRequiringBypass
    val pluginRequiringPermission = uiState.pluginRequiringPermission

    val isLightMode = LocalDesktopTheme.current.isLightMode

    val languages = remember(plugins) {
        listOf("All") + plugins.mapNotNull { it.second.language?.takeIf { l -> l.isNotBlank() } }.distinct().sorted()
    }
    val categories = remember(plugins) {
        plugins.flatMap { it.second.tvTypes ?: emptyList() }.distinct().sorted()
    }
    val reposList = remember(plugins) {
        listOf("All") + plugins.map { it.first }.distinct().sorted()
    }

    var showLangDropdown by remember { mutableStateOf(false) }
    var showRepoDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(syncGeneration) {
        if (syncGeneration > 0) {
            viewModel.onEvent(ExtensionsUiEvent.OnLoadPluginsFromManager)
        }
    }

    val filteredPlugins = remember(plugins, searchQuery, selectedLanguages, selectedCategories, selectedRepos) {
        plugins.filter {
            val matchesSearch = it.second.name.contains(searchQuery, ignoreCase = true) ||
                it.second.internalName.contains(searchQuery, ignoreCase = true)
            val matchesLang = selectedLanguages.isEmpty() || it.second.language in selectedLanguages
            val matchesCat = selectedCategories.isEmpty() || it.second.tvTypes?.any { t -> t in selectedCategories } == true
            val matchesRepo = selectedRepos.isEmpty() || it.first in selectedRepos
            matchesSearch && matchesLang && matchesCat && matchesRepo
        }
    }

    val posterWidthDp by AppearanceConfig.posterWidthDp.collectAsState()
    val extMinSize = (posterWidthDp * 2.2f).coerceAtLeast(320f).dp

    Column(modifier = Modifier.fillMaxSize()) {
        // Search toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isLightMode) Color(0xFFF0F2F6) else Color.White.copy(alpha = 0.05f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isLightMode) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.10f),
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )

                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search ${plugins.size} plugins by name, language, or provider...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (searchQuery.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = "${filteredPlugins.size} matches",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .clickable { searchQuery = "" },
                        )
                    }
                }
            }

            // Language Filter — multi-select
            Box {
                FilledTonalButton(
                    onClick = { showLangDropdown = true },
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    when {
                        selectedLanguages.isEmpty() -> Text("Lang: All", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        selectedLanguages.size == 1 -> Row(verticalAlignment = Alignment.CenterVertically) {
                            FlagImage(selectedLanguages.first(), modifier = Modifier.padding(end = 4.dp).size(14.dp))
                            Text(selectedLanguages.first().uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        else -> Text("${selectedLanguages.size} Languages", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                AppDropdownMenu(expanded = showLangDropdown, onDismissRequest = { showLangDropdown = false }) {
                    if (selectedLanguages.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Clear", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) },
                            onClick = { selectedLanguages = emptySet() },
                        )
                        HorizontalDivider()
                    }
                    languages.drop(1).forEach { lang ->
                        val isSelected = lang in selectedLanguages
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = isSelected, onCheckedChange = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    FlagImage(lang, modifier = Modifier.padding(end = 6.dp).size(14.dp))
                                    Text(lang.uppercase(), fontSize = 12.sp)
                                }
                            },
                            onClick = {
                                selectedLanguages = if (isSelected) selectedLanguages - lang else selectedLanguages + lang
                            },
                        )
                    }
                }
            }

            // Repository Filter — multi-select
            Box {
                FilledTonalButton(
                    onClick = { showRepoDropdown = true },
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    val repoLabel = when {
                        selectedRepos.isEmpty() -> "Repo: All"
                        selectedRepos.size == 1 -> selectedRepos.first().take(14) + if (selectedRepos.first().length > 14) "…" else ""
                        else -> "${selectedRepos.size} Repos"
                    }
                    Text(repoLabel, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                AppDropdownMenu(expanded = showRepoDropdown, onDismissRequest = { showRepoDropdown = false }) {
                    if (selectedRepos.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Clear", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) },
                            onClick = { selectedRepos = emptySet() },
                        )
                        HorizontalDivider()
                    }
                    reposList.drop(1).forEach { r ->
                        val isSelected = r in selectedRepos
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = isSelected, onCheckedChange = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(r, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            },
                            onClick = {
                                selectedRepos = if (isSelected) selectedRepos - r else selectedRepos + r
                            },
                        )
                    }
                }
            }

            // Fetch Repos Action
            FilledTonalButton(
                onClick = { viewModel.onEvent(ExtensionsUiEvent.OnFetchPlugins) },
                enabled = !isFetching,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(44.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                if (isFetching) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Fetch", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Category filter chips
        if (categories.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item {
                    val isAllSelected = selectedCategories.isEmpty()
                    Surface(
                        onClick = { selectedCategories = emptySet() },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isAllSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isAllSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        ),
                    ) {
                        Text(
                            text = "All",
                            color = if (isAllSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }

                items(categories) { category ->
                    val isSelected = category in selectedCategories
                    Surface(
                        onClick = {
                            selectedCategories = if (isSelected) selectedCategories - category else selectedCategories + category
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        ),
                    ) {
                        Text(
                            text = category,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        // ── Extension Cards Grid (or empty states) ──────────────────
        when {
            // No repos added yet — full onboarding CTA
            plugins.isEmpty() && !isFetching -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Extension,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "No Extensions Yet",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "Extensions are installed from repositories. Add a repository first, then come back here to browse and install.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Spacer(Modifier.height(4.dp))
                            // Step 1
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Surface(
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text(
                                            "1",
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Add a Repository", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        "Repositories are curated collections of extensions",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Button(
                                    onClick = onNavigateToRepos,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                ) {
                                    Text("Go to Repositories", fontSize = 12.sp)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            // Step 2
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Surface(
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.size(30.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text(
                                            "2",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Browse & Install Extensions",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "Come back here to search and install extensions",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Still fetching — show spinner instead of blank
            plugins.isEmpty() && isFetching -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
                        Text(
                            "Fetching extensions from repositories…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            // Filters returned nothing
            filteredPlugins.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Text(
                            "No extensions match your filters",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                        TextButton(onClick = {
                            selectedLanguages = emptySet()
                            selectedCategories = emptySet()
                            selectedRepos = emptySet()
                            searchQuery = ""
                        }) {
                            Text("Clear all filters")
                        }
                    }
                }
            }

            // Normal populated grid
            else -> {
                val installedPluginKeys = remember(uiState.installedPlugins) {
                    val set = mutableSetOf<String>()
                    uiState.installedPlugins.forEach { installed ->
                        val parent = installed.file.parentFile?.name ?: ""
                        set.add("$parent:${installed.internalName}")
                        set.add(installed.internalName)
                    }
                    set
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = extMinSize),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filteredPlugins, key = { "${it.first}-${it.second.internalName}" }) { (repoName, plugin) ->
                        val iconUrl = plugin.iconUrl
                            ?: uiState.remotePluginIcons[plugin.internalName]
                            ?: uiState.remotePluginIcons[plugin.name]

                        val cleanRepo = remember(repoName) { repoName.replace(Regex("[^a-zA-Z0-9.-]"), "_") }
                        val isPluginInstalled = remember(plugin.internalName, cleanRepo, installedPluginKeys) {
                            installedPluginKeys.contains("$cleanRepo:${plugin.internalName}") ||
                                installedPluginKeys.contains("$repoName:${plugin.internalName}") ||
                                installedPluginKeys.contains(plugin.internalName)
                        }
                        val isInstalling = uiState.installingPlugins.contains(plugin.internalName)
                        val installStatus = when {
                            isPluginInstalled -> "Installed"
                            isInstalling -> "Installing..."
                            else -> ""
                        }

                        ExtensionCard(
                            name = plugin.name,
                            internalName = plugin.internalName,
                            version = plugin.version,
                            repoName = repoName,
                            language = plugin.language,
                            tvTypes = plugin.tvTypes,
                            iconUrl = iconUrl,
                            isInstalled = isPluginInstalled,
                            installStatus = installStatus,
                            isInstalling = isInstalling,
                            onInstallClick = {
                                if (!isInstalling && !isPluginInstalled) {
                                    viewModel.onEvent(ExtensionsUiEvent.OnInstallPlugin(repoName, plugin))
                                }
                            },
                            description = plugin.description,
                            fileSize = plugin.fileSize,
                        )
                    }
                }
            }
        }
    }
}
