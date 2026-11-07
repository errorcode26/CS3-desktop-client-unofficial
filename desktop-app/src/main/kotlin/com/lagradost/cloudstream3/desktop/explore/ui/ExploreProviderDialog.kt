package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.cloudstream3.desktop.explore.models.ProviderMatch
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.common.storage.WatchHistory

@Composable
fun ExploreProviderDialog(
    item: ExploreItem?,
    matches: List<ProviderMatch>,
    isSearching: Boolean,
    watchHistory: WatchHistory? = null,
    characterOrJob: String? = null,
    onDismissRequest: () -> Unit,
    onSelectMatch: ((ProviderMatch) -> Unit)? = null,
    onOpenDetails: ((providerName: String, url: String, title: String) -> Unit)? = null,
    onOpenAddonMode: ((ExploreItem) -> Unit)? = null,
    onNavigateToExtensions: ((initialTab: Int) -> Unit)? = null,
) {
    if (item == null) return

    val theme = LocalDesktopTheme.current
    val dialogBg = if (theme.isAmoled) Color.Black else theme.Background

    val handleSelect: (ProviderMatch) -> Unit = { match ->
        onSelectMatch?.invoke(match) ?: onOpenDetails?.invoke(match.providerName, match.searchResponse.url, match.displayTitle)
    }

    CloudstreamCustomDialog(
        show = true,
        onDismissRequest = onDismissRequest,
        containerColor = dialogBg,
        modifier = Modifier
            .fillMaxWidth(0.80f)
            .fillMaxHeight(0.84f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(dialogBg)
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ExploreProviderMediaPane(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
                item = item,
                watchHistory = watchHistory,
                characterOrJob = characterOrJob,
                onOpenAddonMode = onOpenAddonMode,
                onNavigateToExtensions = onNavigateToExtensions,
                onDismissRequest = onDismissRequest,
            )

            ExploreProviderSourcesPane(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight(),
                item = item,
                matches = matches,
                isSearching = isSearching,
                onDismissRequest = onDismissRequest,
                onSelectMatch = handleSelect,
                onOpenAddonMode = onOpenAddonMode,
                onNavigateToExtensions = onNavigateToExtensions,
            )
        }
    }
}
