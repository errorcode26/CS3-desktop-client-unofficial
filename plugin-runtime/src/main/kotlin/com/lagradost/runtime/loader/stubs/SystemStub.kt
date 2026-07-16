package com.lagradost.runtime.loader.stubs

import com.lagradost.common.logging.AppLogger

object SystemStub {
    @JvmStatic
    fun exit(status: Int) {
        AppLogger.i("Security Sandbox: Blocked System.exit($status)")
    }

    @JvmStatic
    fun loadLibrary(libname: String) {
        AppLogger.i("Security Sandbox: Blocked System.loadLibrary($libname)")
    }

    @JvmStatic
    fun load(filename: String) {
        AppLogger.i("Security Sandbox: Blocked System.load($filename)")
    }

    @JvmStatic
    fun setSecurityManager(s: SecurityManager?) {
        AppLogger.i("Security Sandbox: Blocked System.setSecurityManager()")
    }
}
