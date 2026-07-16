package com.lagradost.runtime.permission

/**
 * Type-safe contract representing capabilities a CloudStream Desktop plugin may request.
 */
enum class PluginPermission(
    val id: String,
    val displayName: String,
    val description: String
) {
    NETWORK_SOCKETS(
        "network_sockets",
        "Network Sockets",
        "Allows opening TCP/UDP sockets and direct HTTP connections outside the standard app HTTP client."
    ),
    ADVANCED_JS_EVAL(
        "js_eval",
        "Advanced Script Eval",
        "Allows evaluating dynamic JavaScript engines (e.g. for CAPTCHA solving or complex script challenges)."
    ),
    PERSISTENT_STORAGE(
        "storage",
        "Persistent Storage",
        "Allows reading and writing isolated plugin database/preferences."
    );

    companion object {
        /**
         * Resolves a PluginPermission from its internal ID or user-facing display name.
         * Useful for bridging legacy string checks or manifest declarations.
         */
        fun fromIdOrName(query: String): PluginPermission? {
            return values().firstOrNull { 
                it.id.equals(query, ignoreCase = true) || it.displayName.equals(query, ignoreCase = true) 
            }
        }
    }
}
