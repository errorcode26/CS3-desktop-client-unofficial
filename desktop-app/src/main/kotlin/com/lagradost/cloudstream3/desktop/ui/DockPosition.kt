package com.lagradost.cloudstream3.desktop.ui

enum class DockPosition(val label: String) {
    LEFT("Left"),
    RIGHT("Right"),
    TOP("Top"),
    BOTTOM("Bottom"),
    ;

    companion object {
        fun fromString(value: String?): DockPosition {
            if (value.isNullOrBlank()) return LEFT
            return entries.firstOrNull { 
                it.label.equals(value.trim(), ignoreCase = true) || it.name.equals(value.trim(), ignoreCase = true) 
            } ?: LEFT
        }
    }
}
