package com.lagradost.common.logging

enum class LogSubsystem(val displayName: String) {
    ALL("All Subsystems"),
    PLUGINS("Plugins & Scrapers"),
    PROXY_NETWORK("Network & Proxy"),
    PLAYER_MPV("Player & MPV"),
    DATABASE("Database & Storage"),
    SYSTEM_UI("UI & System"),
    GENERAL("General");

    companion object {
        fun fromTag(tag: String): LogSubsystem {
            val lower = tag.lowercase()
            return when {
                lower.contains("plugin") || lower.contains("extension") || lower.contains("dex2jar") ||
                        lower.contains("bytecode") || lower.contains("invoker") || lower.contains("scraper") ||
                        lower.contains("provider") -> PLUGINS

                lower.contains("proxy") || lower.contains("network") || lower.contains("http") ||
                        lower.contains("dns") || lower.contains("tmdb") || lower.contains("enrich") ||
                        lower.contains("extractor") -> PROXY_NETWORK

                lower.contains("player") || lower.contains("mpv") || lower.contains("video") ||
                        lower.contains("subtitle") || lower.contains("audio") || lower.contains("playback") ||
                        lower.contains("track") || lower.contains("shader") -> PLAYER_MPV

                lower.contains("db") || lower.contains("database") || lower.contains("datastore") ||
                        lower.contains("sqldelight") || lower.contains("cache") || lower.contains("history") ||
                        lower.contains("sqlite") -> DATABASE

                lower.contains("ui") || lower.contains("compose") || lower.contains("window") ||
                        lower.contains("screen") || lower.contains("navigation") || lower.contains("viewmodel") ||
                        lower.contains("updater") || lower.contains("theme") -> SYSTEM_UI

                else -> GENERAL
            }
        }
    }
}
