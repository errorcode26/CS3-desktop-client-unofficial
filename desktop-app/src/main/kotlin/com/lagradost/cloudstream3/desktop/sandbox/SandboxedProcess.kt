package com.lagradost.cloudstream3.desktop.sandbox

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

/**
 * Common cross-platform contract for a sandboxed worker process.
 */
interface SandboxedProcess : Closeable {
    val pid: Int
    val stdin: OutputStream
    val stdout: InputStream
    val stderr: InputStream
    fun isAlive(): Boolean
    fun exitValue(): Int
    fun destroy()
}
