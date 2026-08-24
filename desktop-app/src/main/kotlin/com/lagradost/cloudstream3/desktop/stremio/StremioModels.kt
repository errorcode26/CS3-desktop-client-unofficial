package com.lagradost.cloudstream3.desktop.stremio

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Clean data models for external Stremio Addons.
 * Strictly decoupled from CloudStream core engine.
 */

@JsonIgnoreProperties(ignoreUnknown = true)
data class StremioManifest(
    @JsonProperty("id") val id: String = "",
    @JsonProperty("name") val name: String = "",
    @JsonProperty("description") val description: String = "",
    @JsonProperty("version") val version: String = "1.0.0",
    @JsonProperty("logo") val logoUrl: String? = null,
    @JsonProperty("resources") val resources: List<StremioResource> = emptyList(),
    @JsonProperty("types") val types: List<String> = emptyList(),
    @JsonProperty("idPrefixes") val idPrefixes: List<String> = emptyList(),
    @JsonProperty("behaviorHints") val behaviorHints: StremioBehaviorHints = StremioBehaviorHints(),
    val transportUrl: String = "",
) {
    val providesSubtitles: Boolean
        get() = resources.any { it.name.equals("subtitles", ignoreCase = true) }

    val providesMetadata: Boolean
        get() = resources.any { it.name.equals("meta", ignoreCase = true) }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class StremioResource(
    val name: String,
    val types: List<String> = emptyList(),
    val idPrefixes: List<String> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class StremioBehaviorHints(
    @JsonProperty("configurable") val configurable: Boolean = false,
    @JsonProperty("configurationRequired") val configurationRequired: Boolean = false,
    @JsonProperty("adult") val adult: Boolean = false,
    @JsonProperty("p2p") val p2p: Boolean = false,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ManagedStremioAddon(
    @JsonProperty("manifestUrl") val manifestUrl: String,
    @JsonProperty("name") val name: String = "",
    @JsonProperty("description") val description: String = "",
    @JsonProperty("version") val version: String = "1.0.0",
    @JsonProperty("logoUrl") val logoUrl: String? = null,
    @JsonProperty("enabled") val enabled: Boolean = true,
    @JsonProperty("providesSubtitles") val providesSubtitles: Boolean = false,
    @JsonProperty("providesMetadata") val providesMetadata: Boolean = false,
    @JsonProperty("types") val types: List<String> = emptyList(),
    @JsonProperty("idPrefixes") val idPrefixes: List<String> = emptyList(),
    @JsonProperty("errorMessage") val errorMessage: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class StremioSubtitleResponse(
    @JsonProperty("subtitles") val subtitles: List<StremioSubtitleItem>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class StremioSubtitleItem(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("lang") val lang: String? = null,
    @JsonProperty("SubEncoding") val subEncoding: String? = null,
    @JsonProperty("m") val matchType: String? = null,
    @JsonProperty("g") val rating: String? = null,
)
