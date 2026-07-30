package com.lagradost.cloudstream3.desktop.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.utils.DesktopStrings

@Composable
fun ProviderCatalogsDialog(
    show: Boolean,
    provider: MainAPI,
    disabledCatalogs: Set<String>,
    onToggleCatalog: (String, Boolean) -> Unit,
    onDismissRequest: () -> Unit
) {
    CloudstreamCustomDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth(0.5f).fillMaxHeight(0.6f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp)
        ) {
            Text(
                text = "Manage ${provider.name} Catalogs",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(provider.mainPage.size) { index ->
                    val catalog = provider.mainPage[index]
                    val isEnabled = !disabledCatalogs.contains(catalog.name)
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isEnabled,
                            onCheckedChange = { checked ->
                                onToggleCatalog(catalog.name, checked)
                            }
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = catalog.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text("Close")
                }
            }
        }
    }
}
