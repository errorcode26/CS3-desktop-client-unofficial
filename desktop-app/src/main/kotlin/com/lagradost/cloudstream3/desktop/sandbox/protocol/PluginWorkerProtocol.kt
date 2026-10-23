package com.lagradost.cloudstream3.desktop.sandbox.protocol

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule

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
    val isCasting: Boolean = false
) {
    companion object {
        const val ACTION_PING = "ping"
        const val ACTION_LOAD_PLUGIN = "loadPlugin"
        const val ACTION_SEARCH = "search"
        const val ACTION_LOAD = "load"
        const val ACTION_LOAD_LINKS = "loadLinks"
        const val ACTION_GET_LOADED_PROVIDERS = "getLoadedProviders"
    }
}

data class WorkerResponse(
    val id: Long,
    val success: Boolean,
    val error: String? = null,
    val payloadJson: String? = null
)

object WorkerProtocolSerializer {
    val mapper: ObjectMapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
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
}
