package com.lagradost.runtime.security

import com.lagradost.common.logging.AppLogger
import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory

/**
 * Hardens Rhino JavaScript execution against Java reflection escapes.
 * By attaching a global ClassShutter that denies all Java class access from JS scripts,
 * plugins executing embedded JavaScript scrapers cannot reflect into host JVM classes.
 */
object RhinoSecurity {

    private var initialized = false

    fun init() {
        if (initialized) return
        try {
            ContextFactory.initGlobal(object : ContextFactory() {
                override fun makeContext(): Context {
                    val context = super.makeContext()
                    // Block all reflection and access to Java classes from JavaScript
                    context.setClassShutter(ClassShutter { false })
                    return context
                }
            })
            initialized = true
            AppLogger.i("RhinoSecurity: Global ClassShutter registered successfully.")
        } catch (t: Throwable) {
            AppLogger.w("RhinoSecurity: ContextFactory already initialized or unavailable: ${t.message}")
        }
    }
}
