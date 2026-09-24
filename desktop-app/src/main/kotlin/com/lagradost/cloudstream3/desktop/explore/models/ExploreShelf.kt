package com.lagradost.cloudstream3.desktop.explore.models

import androidx.compose.runtime.Immutable

@Immutable
data class ExploreShelf(
    val catalog: ManifestCatalogDescriptor,
    val items: List<ExploreItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
