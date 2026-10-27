package com.lagradost.cloudstream3.desktop.network.webview

data class BrowserFetchResult(
    val statusCode: Int,
    val contentType: String?,
    val body: String,
    val bodyBytes: ByteArray? = null,
)
