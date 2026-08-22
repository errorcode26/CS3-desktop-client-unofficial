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
}
