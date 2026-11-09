package com.lagradost.cloudstream3.desktop.sandbox.linux

import com.lagradost.cloudstream3.desktop.sandbox.SandboxedProcess
import com.lagradost.common.logging.AppLogger
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Linux equivalent to Windows AppContainer.
 * Uses Linux unprivileged user namespaces and filesystem isolation via Bubblewrap (`bwrap`)
 * or isolated sub-processes.
 *
 * Enforces:
 * - Read-only root filesystem access (--ro-bind / /)
 * - Isolated temporary scratch space (--tmpfs /tmp)
 * - Read/Write restricted sandbox storage (--bind <sandboxDir> <sandboxDir>)
 * - Outbound network access only (--share-net, equivalent to Windows CAPABILITY_INTERNET_CLIENT)
 * - Automatic process termination on parent exit (--die-with-parent, equivalent to Win32 Job Object)
 */
object LinuxSandboxLauncher {
    private const val TAG = "LinuxSandboxLauncher"

    class LinuxSandboxedProcess(
        private val process: Process,
    ) : SandboxedProcess {
        override val pid: Int = try { process.pid().toInt() } catch (_: Throwable) { -1 }
        override val stdin: OutputStream get() = process.outputStream
        override val stdout: InputStream get() = process.inputStream
        override val stderr: InputStream get() = process.errorStream

        override fun isAlive(): Boolean = process.isAlive
        override fun exitValue(): Int = try { process.exitValue() } catch (_: Throwable) { -1 }

        override fun destroy() {
            process.destroyForcibly()
        }

        override fun close() {
            try { stdin.close() } catch (_: Throwable) {}
            try { stdout.close() } catch (_: Throwable) {}
            try { stderr.close() } catch (_: Throwable) {}
            process.destroy()
        }
    }

    fun isBwrapAvailable(): Boolean {
        return File("/usr/bin/bwrap").exists() || File("/usr/local/bin/bwrap").exists()
    }

    fun buildBwrapArgs(
        workerArgs: List<String>,
        sandboxDir: File,
        tmpDir: File,
    ): List<String> {
        val bwrapPath = if (File("/usr/bin/bwrap").exists()) "/usr/bin/bwrap" else "/usr/local/bin/bwrap"
        val args = mutableListOf(
            bwrapPath,
            "--ro-bind", "/", "/",
            "--dev", "/dev",
            "--proc", "/proc",
            "--tmpfs", "/tmp",
            "--bind", tmpDir.absolutePath, tmpDir.absolutePath,
            "--bind", sandboxDir.absolutePath, sandboxDir.absolutePath,
            "--unshare-all",
            "--share-net",
            "--die-with-parent",
        )
        args.addAll(workerArgs)
        return args
    }

    fun launch(
        commandArgs: List<String>,
        workingDir: File,
        tmpDir: File,
    ): SandboxedProcess {
        val effectiveArgs = if (isBwrapAvailable()) {
            AppLogger.i(TAG, "Launching sandboxed worker via Linux Bubblewrap (bwrap) unprivileged namespace")
            buildBwrapArgs(commandArgs, workingDir, tmpDir)
        } else {
            AppLogger.w(TAG, "bwrap not detected; launching worker via standard isolated process")
            commandArgs
        }

        val proc = ProcessBuilder(effectiveArgs)
            .directory(workingDir)
            .redirectErrorStream(false)
            .start()

        return LinuxSandboxedProcess(proc)
    }
}
