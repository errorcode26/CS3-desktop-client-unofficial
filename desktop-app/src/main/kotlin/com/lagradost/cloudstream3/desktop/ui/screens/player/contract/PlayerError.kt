package com.lagradost.cloudstream3.desktop.ui.screens.player.contract

sealed interface PlayerError {
    /** Represents an error originating from an extractor plugin while loading links */
    data class ExtractorError(val pluginName: String, val message: String, val cause: Throwable? = null) : PlayerError
    
    /** Represents an error parsing a subtitle file */
    data class SubtitleParseError(val message: String) : PlayerError
    
    /** Represents a network timeout or DNS failure when attempting to reach the source */
    data class NetworkError(val url: String, val message: String) : PlayerError
    
    /** Represents an unknown or unhandled exception */
    data class UnknownError(val message: String, val cause: Throwable? = null) : PlayerError
    
    /** Extension property for user-friendly display message */
    val displayMessage: String
        get() = when (this) {
            is ExtractorError -> "Provider failed ($pluginName): $message"
            is SubtitleParseError -> "Failed to parse subtitle: $message"
            is NetworkError -> "Network error reaching $url: $message"
            is UnknownError -> "Unknown error: $message"
        }
}
