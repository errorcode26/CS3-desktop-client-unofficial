package com.lagradost.cloudstream3.desktop.ui.screens.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
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
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.LibraryUiState
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.SortOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryActionBar(
    uiState: LibraryUiState,
    isCompact: Boolean = false,
    onSearch: (String) -> Unit,
    onSortChange: (SortOption) -> Unit,
    onProviderChange: (String?) -> Unit,
) {
    var sortExpanded by remember { mutableStateOf(false) }
    var providerExpanded by remember { mutableStateOf(false) }

    if (isCompact) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearch,
                placeholder = { Text("Search library...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    val selIcon = remember(uiState.selectedProvider) {
                        DesktopRepositoryManager.getPluginIcon(uiState.selectedProvider)
                    }
                    OutlinedButton(
                        onClick = { providerExpanded = true },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        if (selIcon != null) {
                            AsyncImage(
                                model = selIcon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp).clip(CircleShape),
                            )
                            Spacer(Modifier.width(6.dp))
                        } else {
                            Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(uiState.selectedProvider ?: "All Providers", maxLines = 1, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false), overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("All Providers", fontWeight = if (uiState.selectedProvider == null) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                onProviderChange(null)
                                providerExpanded = false
                            },
                        )
                        uiState.availableProviders.forEach { prov ->
                            val provIcon = DesktopRepositoryManager.getPluginIcon(prov)
                            DropdownMenuItem(
                                leadingIcon = {
                                    if (provIcon != null) {
                                        AsyncImage(
                                            model = provIcon,
                                            contentDescription = prov,
                                            modifier = Modifier.size(20.dp).clip(CircleShape),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = prov.take(1).uppercase(),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                            )
                                        }
                                    }
                                },
                                text = { Text(prov, fontWeight = if (uiState.selectedProvider == prov) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    onProviderChange(prov)
                                    providerExpanded = false
                                },
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { sortExpanded = true },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(uiState.sortOption.title, maxLines = 1, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false), overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                        SortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.title) },
                                onClick = {
                                    onSortChange(option)
                                    sortExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearch,
                placeholder = { Text("Search library...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                modifier = Modifier.weight(1f).height(52.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )

            Box {
                OutlinedButton(
                    onClick = { providerExpanded = true },
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(uiState.selectedProvider ?: "All Providers", maxLines = 1)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("All Providers") },
                        onClick = {
                            onProviderChange(null)
                            providerExpanded = false
                        },
                    )
                    uiState.availableProviders.forEach { prov ->
                        DropdownMenuItem(
                            text = { Text(prov) },
                            onClick = {
                                onProviderChange(prov)
                                providerExpanded = false
                            },
                        )
                    }
                }
            }

            Box {
                OutlinedButton(
                    onClick = { sortExpanded = true },
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(uiState.sortOption.title, maxLines = 1)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                    SortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.title) },
                            onClick = {
                                onSortChange(option)
                                sortExpanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}
