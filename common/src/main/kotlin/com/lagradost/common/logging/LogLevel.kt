package com.lagradost.common.logging

enum class LogLevel(val priority: Int, val shortLabel: String) {
    VERBOSE(2, "V"),
    DEBUG(3, "D"),
    INFO(4, "I"),
    WARN(5, "W"),
    ERROR(6, "E"),
    ;

    fun isAtLeast(minLevel: LogLevel): Boolean = this.priority >= minLevel.priority
}
