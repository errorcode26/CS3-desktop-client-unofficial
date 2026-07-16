package com.lagradost.cloudstream3.ui.settings

/**
 * Stub for CloudStream Android's Globals object.
 * Required by plugins that expect to access Globals.INSTANCE and its layout methods.
 */
object Globals {
    const val PHONE: Int = 0b001
    const val TV: Int = 0b010
    const val EMULATOR: Int = 0b100

    // Desktop is always "TV" layout (landscape, non-touch)
    fun isLayout(flags: Int): Boolean {
        return (TV and flags) != 0
    }

    fun isLandscape(): Boolean = true
}
