package com.lagradost.cloudstream3.desktop.sandbox.protocol

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TorrentSearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType

/**
 * Line-delimited JSON-RPC protocol models exchanged over standard anonymous pipes
 * between the parent Compose desktop app and the low-privilege sandboxed JVM worker.
 */
data class WorkerRequest(
    val id: Long,
    val action: String,
    val pluginPath: String? = null,
    val providerName: String? = null,
    val query: String? = null,
    val url: String? = null,
    val data: String? = null,
    val isCasting: Boolean = false,
    val page: Int? = null,
    val requestName: String? = null,
    val requestData: String? = null,
    val horizontalImages: Boolean? = null,
    val cookies: List<SerializableCookie>? = null,
    val settingsMap: Map<String, String>? = null,
    val forceRetry: Boolean = false
) {
    companion object {
        const val ACTION_PING = "ping"
        const val ACTION_LOAD_PLUGIN = "loadPlugin"
        const val ACTION_SEARCH = "search"
        const val ACTION_LOAD = "load"
        const val ACTION_LOAD_LINKS = "loadLinks"
        const val ACTION_MAIN_PAGE = "mainPage"
        const val ACTION_GET_LOADED_PROVIDERS = "getLoadedProviders"
        const val ACTION_SYNC_COOKIES = "syncCookies"
        const val ACTION_SYNC_SETTINGS = "syncSettings"
        const val ACTION_UNLOAD_PLUGIN = "unloadPlugin"
        const val ACTION_SECURITY_AUDIT = "securityAudit"
    }
}

data class TypedItem(
    val className: String,
    val json: JsonNode
)

data class SandboxedMainPageData(
    val name: String,
    val data: String,
    val horizontalImages: Boolean = false
)

data class SandboxedProviderMetadata(
    val name: String,
    val mainUrl: String,
    val lang: String,
    val supportedTypes: Set<String>,
    val sourcePlugin: String? = null,
    val hasMainPage: Boolean = false,
    val hasQuickSearch: Boolean = false,
    val mainPage: List<SandboxedMainPageData> = emptyList()
)

data class SerializableSettingSchema(
    val pluginPrefName: String,
    val key: String,
    val type: String,
    val defaultValue: Any? = null,
    val isGlobal: Boolean = false,
    val options: Map<String, String>? = null,
    val storageKey: String? = null,
)

data class SandboxedPluginLoadResult(
    val providers: List<SandboxedProviderMetadata>,
    val schemas: List<SerializableSettingSchema> = emptyList(),
)

data class TypedHomePageList(
    val name: String,
    val list: List<TypedItem>,
    val isHorizontalImages: Boolean = false
)

data class TypedHomePageResponse(
    val items: List<TypedHomePageList>,
    val hasNext: Boolean = false
)

data class TypedSearchResponse(
    val items: List<TypedItem> = emptyList(),
    val hasNext: Boolean = false
)

data class WorkerResponse(
    val id: Long,
    val success: Boolean,
    val error: String? = null,
    val payloadJson: String? = null
)

data class SerializableCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String = "/",
    val expiresAt: Long? = null,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
)

data class WorkerCallbackRequest(
    val callbackId: Long,
    val type: String = TYPE_CALLBACK_REQUEST,
    val action: String,
    val url: String? = null,
    val host: String? = null,
    val headers: Map<String, String>? = null,
    val method: String? = null,
    val body: String? = null,
    val requestId: Long? = null,
    val linkJson: String? = null,
    val subtitleJson: String? = null,
    val key: String? = null,
    val valueJson: String? = null,
) {
    companion object {
        const val TYPE_CALLBACK_REQUEST = "CALLBACK_REQUEST"
        const val ACTION_SOLVE_CLOUDFLARE = "solveCloudflare"
        const val ACTION_PROXY_FETCH = "proxyFetch"
        const val ACTION_EMIT_LINK = "emitLink"
        const val ACTION_EMIT_SUBTITLE = "emitSubtitle"
        const val ACTION_SAVE_SETTING = "saveSetting"
        const val ACTION_REMOVE_SETTING = "removeSetting"
    }
}

data class WorkerCallbackResponse(
    val callbackId: Long,
    val type: String = TYPE_CALLBACK_RESPONSE,
    val success: Boolean,
    val cookies: List<SerializableCookie>? = null,
    val userAgent: String? = null,
    val error: String? = null,
    val statusCode: Int? = null,
    val contentType: String? = null,
    val body: String? = null,
) {
    companion object {
        const val TYPE_CALLBACK_RESPONSE = "CALLBACK_RESPONSE"
    }
}

class SearchResponseDeserializer : JsonDeserializer<SearchResponse>() {
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext
    ): SearchResponse? {
        val node = p.codec.readTree<JsonNode>(p) ?: return null
        val typeStr = node.get("type")?.asText()
        val tvType = typeStr?.let { runCatching { TvType.valueOf(it) }.getOrNull() }

        val targetClass = when {
            node.has("episodes") && node.get("episodes").isObject -> AnimeSearchResponse::class.java
            node.has("episodes") && node.get("episodes").isIntegralNumber -> TvSeriesSearchResponse::class.java
            tvType == TvType.Anime || tvType == TvType.AnimeMovie || tvType == TvType.OVA -> AnimeSearchResponse::class.java
            tvType == TvType.TvSeries -> TvSeriesSearchResponse::class.java
            tvType == TvType.Torrent -> TorrentSearchResponse::class.java
            tvType == TvType.Live -> LiveSearchResponse::class.java
            else -> MovieSearchResponse::class.java
        }

        return try {
            p.codec.treeToValue(node, targetClass)
        } catch (_: Throwable) {
            val name = node.get("name")?.asText() ?: "Unknown"
            val url = node.get("url")?.asText() ?: ""
            val apiName = node.get("apiName")?.asText() ?: "Unknown"
            val posterUrl = node.get("posterUrl")?.asText()
            val year = node.get("year")?.asInt()
            @Suppress("DEPRECATION_ERROR")
            MovieSearchResponse(
                name = name,
                url = url,
                apiName = apiName,
                type = tvType ?: TvType.Movie,
                posterUrl = posterUrl,
                year = year
            )
        }
    }
}

object WorkerProtocolSerializer {
    val mapper: ObjectMapper = com.lagradost.cloudstream3.mapper.copy().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val module = SimpleModule().apply {
            addDeserializer(SearchResponse::class.java, SearchResponseDeserializer())
        }
        registerModule(module)
    }

    fun serializeRequest(request: WorkerRequest): String {
        return mapper.writeValueAsString(request)
    }

    fun deserializeRequest(json: String): WorkerRequest {
        return mapper.readValue(json, WorkerRequest::class.java)
    }

    fun serializeResponse(response: WorkerResponse): String {
        return mapper.writeValueAsString(response)
    }

    fun deserializeResponse(json: String): WorkerResponse {
        return mapper.readValue(json, WorkerResponse::class.java)
    }

    fun serializeCallbackRequest(request: WorkerCallbackRequest): String {
        return mapper.writeValueAsString(request)
    }

    fun deserializeCallbackRequest(json: String): WorkerCallbackRequest {
        return mapper.readValue(json, WorkerCallbackRequest::class.java)
    }

    fun serializeCallbackResponse(response: WorkerCallbackResponse): String {
        return mapper.writeValueAsString(response)
    }

    fun deserializeCallbackResponse(json: String): WorkerCallbackResponse {
        return mapper.readValue(json, WorkerCallbackResponse::class.java)
    }
}

data class SandboxAuditReport(
    val subprocessBlocked: Boolean,
    val winTempWriteBlocked: Boolean,
    val startupWriteBlocked: Boolean,
    val extensionTamperBlocked: Boolean,
    val sensitiveReadBlocked: Boolean,
    val lanAccessBlocked: Boolean,
    val sandboxTmpAllowed: Boolean,
    val details: Map<String, String> = emptyMap()
) {
    val isFullySecure: Boolean
        get() = subprocessBlocked && winTempWriteBlocked && startupWriteBlocked &&
                extensionTamperBlocked && sensitiveReadBlocked && lanAccessBlocked && sandboxTmpAllowed
}
