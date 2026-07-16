package com.lagradost.cloudstream3.desktop.models

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Data model for cloned sites / custom overridden URLs.
 * Mimics the Android SettingsGeneral.CustomSite model to maintain parity.
 */
data class CustomSite(
    @JsonProperty("parentJavaClass")
    val parentJavaClass: String,
    @JsonProperty("name")
    val name: String,
    @JsonProperty("url")
    val url: String,
    @JsonProperty("lang")
    val lang: String,
)
