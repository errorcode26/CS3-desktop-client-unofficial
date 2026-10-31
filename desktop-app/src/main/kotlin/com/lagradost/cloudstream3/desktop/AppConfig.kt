package com.lagradost.cloudstream3.desktop

object AppConfig {
    val APP_VERSION: String by lazy {
        val sysProp = System.getProperty("cloudstream.version")?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        if (sysProp != null) return@lazy sysProp

        try {
            AppConfig::class.java.getResourceAsStream("/version.properties")?.use { stream ->
                val props = java.util.Properties()
                props.load(stream)
                props.getProperty("app.version")?.trim()?.takeIf { it.isNotBlank() }
            } ?: "0.0.0-dev"
        } catch (_: Throwable) {
            "0.0.0-dev"
        }
    }

    const val GITHUB_REPO = "errorcode26/CS3-desktop-client-unofficial"
}
