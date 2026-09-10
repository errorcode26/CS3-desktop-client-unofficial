package com.lagradost.runtime.loader.stubs

import com.lagradost.common.logging.AppLogger

object SystemStub {
    @JvmStatic
    fun exit(status: Int) {
        AppLogger.i("Plugin Security: Blocked System.exit($status)")
    }

    @JvmStatic
    fun loadLibrary(libname: String) {
        AppLogger.i("Plugin Security: Blocked System.loadLibrary($libname)")
    }

    @JvmStatic
    fun load(filename: String) {
        AppLogger.i("Plugin Security: Blocked System.load($filename)")
    }

    @JvmStatic
    fun setSecurityManager(s: SecurityManager?) {
        AppLogger.i("Plugin Security: Blocked System.setSecurityManager()")
    }

    @JvmStatic
    fun getProperty(key: String): String? {
        return when (key) {
            "line.separator" -> "\n"
            "file.separator" -> "/"
            "path.separator" -> ":"
            "java.version" -> "17"
            else -> null
        }
    }

    @JvmStatic
    fun getProperty(key: String, def: String?): String? {
        return getProperty(key) ?: def
    }

    @JvmStatic
    fun getenv(name: String): String? = null

    @JvmStatic
    fun getenv(): Map<String, String> = emptyMap()

    @JvmStatic
    fun getProperties(): java.util.Properties {
        val props = java.util.Properties()
        props["line.separator"] = "\n"
        props["file.separator"] = "/"
        props["path.separator"] = ":"
        props["java.version"] = "17"
        return props
    }
}
