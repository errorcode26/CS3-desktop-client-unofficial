package com.lagradost.cloudstream3.ui.settings.extensions

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Stub for Android's RepositoryData class.
 * Used by aggregator extensions to register repositories.
 */
data class RepositoryData(
    @JsonProperty("iconUrl") val iconUrl: String?,
    @JsonProperty("name") val name: String,
    @JsonProperty("url") val url: String,
) {
    constructor(name: String, url: String) : this(null, name, url)
}
