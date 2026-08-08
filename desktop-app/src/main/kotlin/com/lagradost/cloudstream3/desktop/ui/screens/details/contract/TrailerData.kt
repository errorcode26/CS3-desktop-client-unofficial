package com.lagradost.cloudstream3.desktop.ui.screens.details.contract

data class TrailerData(
    val id: String,
    val name: String,
    val url: String,
    val rawKey: String? = null,
    val thumbnailUrl: String? = null,
    val site: String = "YouTube",
    val isOfficial: Boolean = true,
    val publishedAt: String? = null,
)
