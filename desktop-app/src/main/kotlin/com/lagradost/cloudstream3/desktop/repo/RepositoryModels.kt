package com.lagradost.cloudstream3.desktop.repo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class Repository(
    @JsonProperty("iconUrl") val iconUrl: String? = null,
    @JsonProperty("name") val name: String,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("manifestVersion") val manifestVersion: Int = 1,
    @JsonProperty("pluginLists") val pluginLists: List<String>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SitePlugin(
    @JsonProperty("name") val name: String,
    @JsonProperty("internalName") val internalName: String,
    @JsonProperty("url") val url: String,
    @JsonProperty("jarUrl") val jarUrl: String? = null,
    @JsonProperty("version") val version: Int,
    @JsonProperty("status") val status: Int = 1,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("iconUrl") val iconUrl: String? = null,
    @JsonProperty("fileHash") val fileHash: String? = null,
    @JsonProperty("fileSize") val fileSize: Long? = null,
    @JsonProperty("language") val language: String? = null,
    @JsonProperty("tvTypes") val tvTypes: List<String>? = null,
)
