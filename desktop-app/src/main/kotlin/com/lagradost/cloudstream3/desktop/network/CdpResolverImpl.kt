package com.lagradost.cloudstream3.desktop.network

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import okhttp3.*

object CdpResolverImpl {

    private val mutex = Mutex()
    private val mapper: ObjectMapper = jacksonObjectMapper()

    suspend fun resolve(request: Request, requestCallBack: (Request) -> Boolean): Pair<Request?, List<Request>> = withContext(Dispatchers.IO) {
        // TODO: This headless CDP solver has been disabled for future implementations.
        // Modern Cloudflare (Turnstile/hCaptcha) detects headless automation and prevents solving.
        // We will add a manual CF link grabbing and manual popup CF clear and key grabbing UI
        // using JCEF or WebView2 in the future where the user can manually solve the captcha.
        AppLogger.w("CdpResolver is disabled. Automatic Cloudflare bypass skipped.")
        return@withContext null to emptyList()
    }
}
