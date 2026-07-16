package com.lagradost.runtime.loader.stubs

import com.lagradost.common.logging.AppLogger

object RuntimeStub {
    @JvmStatic
    fun exec(runtime: Runtime, command: String): Process? {
        AppLogger.i("Security Sandbox: Blocked Runtime.exec($command)")
        return null
    }

    @JvmStatic
    fun exec(runtime: Runtime, cmdarray: Array<String>): Process? {
        AppLogger.i("Security Sandbox: Blocked Runtime.exec(${cmdarray.joinToString()})")
        return null
    }

    @JvmStatic
    fun exec(runtime: Runtime, cmdarray: Array<String>, envp: Array<String>?): Process? {
        AppLogger.i("Security Sandbox: Blocked Runtime.exec(${cmdarray.joinToString()})")
        return null
    }

    @JvmStatic
    fun exec(runtime: Runtime, cmdarray: Array<String>, envp: Array<String>?, dir: java.io.File?): Process? {
        AppLogger.i("Security Sandbox: Blocked Runtime.exec(${cmdarray.joinToString()})")
        return null
    }

    @JvmStatic
    fun exec(runtime: Runtime, command: String, envp: Array<String>?): Process? {
        AppLogger.i("Security Sandbox: Blocked Runtime.exec($command)")
        return null
    }

    @JvmStatic
    fun exec(runtime: Runtime, command: String, envp: Array<String>?, dir: java.io.File?): Process? {
        AppLogger.i("Security Sandbox: Blocked Runtime.exec($command)")
        return null
    }

    @JvmStatic
    fun loadLibrary(runtime: Runtime, libname: String) {
        AppLogger.i("Security Sandbox: Blocked Runtime.loadLibrary($libname)")
    }

    @JvmStatic
    fun load(runtime: Runtime, filename: String) {
        AppLogger.i("Security Sandbox: Blocked Runtime.load($filename)")
    }

    @JvmStatic
    fun exit(runtime: Runtime, status: Int) {
        AppLogger.i("Security Sandbox: Blocked Runtime.exit($status)")
    }

    @JvmStatic
    fun halt(runtime: Runtime, status: Int) {
        AppLogger.i("Security Sandbox: Blocked Runtime.halt($status)")
    }
}
