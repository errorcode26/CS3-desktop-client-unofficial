package com.lagradost.cloudstream3.desktop.sandbox.windows

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.BaseTSD
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Launches child processes constrained strictly within a Windows AppContainer and Win32 Job Object.
 * Enforces:
 * - Low-privilege integrity level (AppContainer)
 * - Network isolation (only outbound internet if CAPABILITY_INTERNET_CLIENT is granted)
 * - Filesystem sandboxing (denies access to user profile, documents, and registry)
 * - 1024 MB memory limit and clean auto-termination on parent exit
 */
object WindowsAppContainerLauncher {

    private val logger = LoggerFactory.getLogger(WindowsAppContainerLauncher::class.java)

    private const val EXTENDED_STARTUPINFO_PRESENT = 0x00080000
    private const val CREATE_SUSPENDED = 0x00000004
    private const val STARTF_USESHOWWINDOW = 0x00000001
    private const val STARTF_USESTDHANDLES = 0x00000100
    private val SW_HIDE = WinDef.WORD(0)
    private const val HANDLE_FLAG_INHERIT = 0x00000001

    // ProcThreadAttribute values
    // ProcThreadAttributeSecurityCapabilities = 9 -> ((9 & 0xFFFF) | 0x00020000) = 0x20009
    private const val PROC_THREAD_ATTRIBUTE_SECURITY_CAPABILITIES = 0x00020009L

    @Structure.FieldOrder("StartupInfo", "lpAttributeList")
    open class STARTUPINFOEXW : Structure() {
        @JvmField var StartupInfo: WinBase.STARTUPINFO = WinBase.STARTUPINFO()
        @JvmField var lpAttributeList: Pointer? = null

        init {
            StartupInfo.cb = WinDef.DWORD(size().toLong())
        }
    }

    @Structure.FieldOrder("AppContainerSid", "Capabilities", "CapabilityCount", "Reserved")
    open class SECURITY_CAPABILITIES : Structure() {
        @JvmField var AppContainerSid: Pointer? = null
        @JvmField var Capabilities: Pointer? = null
        @JvmField var CapabilityCount: Int = 0
        @JvmField var Reserved: Int = 0
    }

    @Structure.FieldOrder("nLength", "lpSecurityDescriptor", "bInheritHandle")
    open class SECURITY_ATTRIBUTES : Structure() {
        @JvmField var nLength: Int = size()
        @JvmField var lpSecurityDescriptor: Pointer? = null
        @JvmField var bInheritHandle: Boolean = true
    }

    interface ExtendedKernel32 : StdCallLibrary {
        companion object {
            val INSTANCE: ExtendedKernel32 = Native.load("kernel32", ExtendedKernel32::class.java, W32APIOptions.UNICODE_OPTIONS)
        }

        fun InitializeProcThreadAttributeList(
            lpAttributeList: Pointer?,
            dwAttributeCount: Int,
            dwFlags: Int,
            lpSize: Pointer
        ): Boolean

        fun UpdateProcThreadAttribute(
            lpAttributeList: Pointer,
            dwFlags: Int,
            Attribute: BaseTSD.ULONG_PTR,
            lpValue: Pointer,
            cbSize: BaseTSD.SIZE_T,
            lpPreviousValue: Pointer?,
            lpReturnSize: Pointer?
        ): Boolean

        fun DeleteProcThreadAttributeList(
            lpAttributeList: Pointer
        )

        fun CreateProcessW(
            lpApplicationName: WString?,
            lpCommandLine: Pointer?,
            lpProcessAttributes: Pointer?,
            lpThreadAttributes: Pointer?,
            bInheritHandles: Boolean,
            dwCreationFlags: Int,
            lpEnvironment: Pointer?,
            lpCurrentDirectory: WString?,
            lpStartupInfo: Pointer,
            lpProcessInformation: WinBase.PROCESS_INFORMATION
        ): Boolean

        fun ResumeThread(hThread: WinNT.HANDLE): Int
        fun SetHandleInformation(hObject: WinNT.HANDLE, dwMask: Int, dwFlags: Int): Boolean
        fun CreatePipe(
            hReadPipe: WinNT.HANDLEByReference,
            hWritePipe: WinNT.HANDLEByReference,
            lpPipeAttributes: Pointer,
            nSize: Int
        ): Boolean
    }

    /**
     * Spawns a sandboxed process into the specified AppContainer with Job Object resource enforcement.
     */
    fun launch(
        commandLine: String,
        containerName: String,
        workingDir: File? = null,
        capabilities: List<String> = listOf(
            WindowsAppContainerApi.CAPABILITY_INTERNET_CLIENT
        ),
        jobObject: WindowsJobObject? = null
    ): SandboxedProcess {
        val sidString = WindowsAppContainerApi.getOrCreateAppContainer(
            containerName = containerName,
            capabilities = capabilities
        ) ?: throw IOException("Failed to obtain AppContainer SID for '$containerName'")

        val pSidRef = PointerByReference()
        if (!WindowsAppContainerApi.Advapi32Sid.INSTANCE.ConvertStringSidToSidW(WString(sidString), pSidRef)) {
            throw IOException("Failed to convert SID string '$sidString' to binary SID")
        }
        val appContainerSid = pSidRef.value

        // Grant Window Station (WinSta0) and Desktop (Default) access to this container SID
        // Required for DLL/CRT initialization (user32.dll) in sandboxed AppContainer processes
        WindowsSandboxAcl.grantWindowStationAndDesktopAccess(sidString)

        val pSecDesc = PointerByReference()
        // SDDL grants Generic All (GA) to World (WD) and ALL APPLICATION PACKAGES (AC)
        // Ensures the low-integrity AppContainer child process can inherit and access stdin/stdout/stderr pipes
        val sddl = "D:(A;OICI;GA;;;WD)(A;OICI;GA;;;AC)"
        val sddlConverted = WindowsSandboxAcl.Advapi32Acl.INSTANCE.ConvertStringSecurityDescriptorToSecurityDescriptorW(
            WString(sddl),
            1, // SDDL_REVISION_1
            pSecDesc,
            null
        )

        val sa = SECURITY_ATTRIBUTES().apply {
            bInheritHandle = true
            lpSecurityDescriptor = if (sddlConverted) pSecDesc.value else null
        }
        sa.write()

        // Create Anonymous Pipes for Stdin, Stdout, Stderr
        val hChildStdinRead = WinNT.HANDLEByReference()
        val hParentStdinWrite = WinNT.HANDLEByReference()
        val hParentStdoutRead = WinNT.HANDLEByReference()
        val hChildStdoutWrite = WinNT.HANDLEByReference()
        val hParentStderrRead = WinNT.HANDLEByReference()
        val hChildStderrWrite = WinNT.HANDLEByReference()

        val pipeSuccess = ExtendedKernel32.INSTANCE.CreatePipe(hChildStdinRead, hParentStdinWrite, sa.pointer, 0) &&
            ExtendedKernel32.INSTANCE.CreatePipe(hParentStdoutRead, hChildStdoutWrite, sa.pointer, 0) &&
            ExtendedKernel32.INSTANCE.CreatePipe(hParentStderrRead, hChildStderrWrite, sa.pointer, 0)

        if (sddlConverted && pSecDesc.value != null) {
            Kernel32.INSTANCE.LocalFree(pSecDesc.value)
        }

        if (!pipeSuccess) {
            throw IOException("Failed to create standard I/O pipes for AppContainer process: error ${Native.getLastError()}")
        }

        // Parent ends must NOT be inheritable
        ExtendedKernel32.INSTANCE.SetHandleInformation(hParentStdinWrite.value, HANDLE_FLAG_INHERIT, 0)
        ExtendedKernel32.INSTANCE.SetHandleInformation(hParentStdoutRead.value, HANDLE_FLAG_INHERIT, 0)
        ExtendedKernel32.INSTANCE.SetHandleInformation(hParentStderrRead.value, HANDLE_FLAG_INHERIT, 0)

        // Capabilities SIDs
        val capPointers = mutableListOf<Pointer>()
        var capabilitiesMem: Memory? = null

        val secCap = SECURITY_CAPABILITIES().apply {
            this.AppContainerSid = appContainerSid
        }

        if (capabilities.isNotEmpty()) {
            val itemSize = Structure.newInstance(WindowsAppContainerApi.SID_AND_ATTRIBUTES::class.java).size()
            val mem = Memory((itemSize * capabilities.size).toLong())
            for (i in capabilities.indices) {
                val ref = PointerByReference()
                if (WindowsAppContainerApi.Advapi32Sid.INSTANCE.ConvertStringSidToSidW(WString(capabilities[i]), ref)) {
                    capPointers.add(ref.value)
                    val item = WindowsAppContainerApi.SID_AND_ATTRIBUTES(mem.share((i * itemSize).toLong()))
                    item.Sid = ref.value
                    item.Attributes = WindowsAppContainerApi.SID_AND_ATTRIBUTES.SE_GROUP_ENABLED
                    item.write()
                }
            }
            capabilitiesMem = mem
            secCap.Capabilities = mem
            secCap.CapabilityCount = capPointers.size
        }
        secCap.write()

        // Setup ProcThreadAttributeList
        val attrSizeRef = Memory(Native.POINTER_SIZE.toLong())
        ExtendedKernel32.INSTANCE.InitializeProcThreadAttributeList(null, 1, 0, attrSizeRef)

        val attrListSize = if (Native.POINTER_SIZE == 8) attrSizeRef.getLong(0) else attrSizeRef.getInt(0).toLong()
        val attrMem = Memory(attrListSize)
        if (!ExtendedKernel32.INSTANCE.InitializeProcThreadAttributeList(attrMem, 1, 0, attrSizeRef)) {
            throw IOException("InitializeProcThreadAttributeList failed: error ${Native.getLastError()}")
        }

        val updateSuccess = ExtendedKernel32.INSTANCE.UpdateProcThreadAttribute(
            attrMem,
            0,
            BaseTSD.ULONG_PTR(PROC_THREAD_ATTRIBUTE_SECURITY_CAPABILITIES),
            secCap.pointer,
            BaseTSD.SIZE_T(secCap.size().toLong()),
            null,
            null
        )

        if (!updateSuccess) {
            ExtendedKernel32.INSTANCE.DeleteProcThreadAttributeList(attrMem)
            throw IOException("UpdateProcThreadAttribute failed: error ${Native.getLastError()}")
        }

        val startupInfoEx = STARTUPINFOEXW().apply {
            StartupInfo.dwFlags = STARTF_USESTDHANDLES or STARTF_USESHOWWINDOW
            StartupInfo.wShowWindow = SW_HIDE
            StartupInfo.lpDesktop = "winsta0\\default"
            StartupInfo.hStdInput = hChildStdinRead.value
            StartupInfo.hStdOutput = hChildStdoutWrite.value
            StartupInfo.hStdError = hChildStderrWrite.value
            lpAttributeList = attrMem
        }
        startupInfoEx.write()

        val processInfo = WinBase.PROCESS_INFORMATION()

        // Win32 CreateProcessW requires a writable memory buffer for lpCommandLine
        val cmdMem = Memory(((commandLine.length + 1) * 2).toLong())
        cmdMem.setWideString(0, commandLine)

        // Ensure working directory is accessible by the AppContainer
        // If not specified, default to System32 (accessible by ALL APPLICATION PACKAGES)
        val effectiveWorkingDir = workingDir ?: File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32")

        val creationFlags = EXTENDED_STARTUPINFO_PRESENT or CREATE_SUSPENDED
        val created = ExtendedKernel32.INSTANCE.CreateProcessW(
            null,
            cmdMem,
            null,
            null,
            true,
            creationFlags,
            null,
            WString(effectiveWorkingDir.absolutePath),
            startupInfoEx.pointer,
            processInfo
        )
        val creationError = if (!created) Native.getLastError() else 0

        // Close child ends in parent process
        Kernel32.INSTANCE.CloseHandle(hChildStdinRead.value)
        Kernel32.INSTANCE.CloseHandle(hChildStdoutWrite.value)
        Kernel32.INSTANCE.CloseHandle(hChildStderrWrite.value)

        // Cleanup attribute list & SIDs
        ExtendedKernel32.INSTANCE.DeleteProcThreadAttributeList(attrMem)
        for (p in capPointers) {
            Kernel32.INSTANCE.LocalFree(p)
        }
        Kernel32.INSTANCE.LocalFree(appContainerSid)

        if (!created) {
            Kernel32.INSTANCE.CloseHandle(hParentStdinWrite.value)
            Kernel32.INSTANCE.CloseHandle(hParentStdoutRead.value)
            Kernel32.INSTANCE.CloseHandle(hParentStderrRead.value)
            throw IOException("CreateProcessW in AppContainer failed for '$commandLine' with error code: $creationError")
        }

        val hProcess = processInfo.hProcess
        val hThread = processInfo.hThread
        val pid = processInfo.dwProcessId.toInt()

        // Assign to Job Object before resuming execution (prevents unbound memory spikes)
        jobObject?.assignProcessHandle(hProcess)

        // Resume main thread
        ExtendedKernel32.INSTANCE.ResumeThread(hThread)
        Kernel32.INSTANCE.CloseHandle(hThread)

        logger.info("Successfully launched AppContainer process (PID: $pid) inside container '$containerName'")

        return SandboxedProcess(
            processHandle = hProcess,
            pid = pid,
            stdin = Win32HandleOutputStream(hParentStdinWrite.value),
            stdout = Win32HandleInputStream(hParentStdoutRead.value),
            stderr = Win32HandleInputStream(hParentStderrRead.value),
            jobObject = jobObject
        )
    }

    /**
     * Managed representation of a running AppContainer sandboxed process.
     */
    class SandboxedProcess(
        val processHandle: WinNT.HANDLE,
        val pid: Int,
        val stdin: OutputStream,
        val stdout: InputStream,
        val stderr: InputStream,
        val jobObject: WindowsJobObject?
    ) : Closeable {

        fun isAlive(): Boolean {
            val exitCode = IntByReference()
            if (!Kernel32.INSTANCE.GetExitCodeProcess(processHandle, exitCode)) {
                return false
            }
            return exitCode.value == STILL_ACTIVE
        }

        fun waitFor(timeoutMs: Long = WinBase.INFINITE.toLong()): Boolean {
            val result = Kernel32.INSTANCE.WaitForSingleObject(processHandle, timeoutMs.toInt())
            return result == WAIT_OBJECT_0
        }

        fun exitValue(): Int {
            val exitCode = IntByReference()
            if (!Kernel32.INSTANCE.GetExitCodeProcess(processHandle, exitCode)) {
                throw IOException("Failed to get process exit code")
            }
            if (exitCode.value == STILL_ACTIVE) {
                throw IllegalThreadStateException("Process has not exited yet")
            }
            return exitCode.value
        }

        fun destroy() {
            Kernel32.INSTANCE.TerminateProcess(processHandle, 1)
            close()
        }

        override fun close() {
            try { stdin.close() } catch (_: Throwable) {}
            try { stdout.close() } catch (_: Throwable) {}
            try { stderr.close() } catch (_: Throwable) {}
            Kernel32.INSTANCE.CloseHandle(processHandle)
        }

        companion object {
            private const val STILL_ACTIVE = 259
            private const val WAIT_OBJECT_0 = 0
        }
    }

    private class Win32HandleInputStream(val handle: WinNT.HANDLE) : InputStream() {
        override fun read(): Int {
            val b = ByteArray(1)
            val count = read(b, 0, 1)
            return if (count <= 0) -1 else b[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            val bytesRead = IntByReference()
            val buf = if (off == 0 && len == b.size) b else ByteArray(len)
            val ok = Kernel32.INSTANCE.ReadFile(handle, buf, len, bytesRead, null)
            if (!ok || bytesRead.value == 0) {
                return -1
            }
            if (buf !== b) {
                System.arraycopy(buf, 0, b, off, bytesRead.value)
            }
            return bytesRead.value
        }

        override fun close() {
            Kernel32.INSTANCE.CloseHandle(handle)
        }
    }

    private class Win32HandleOutputStream(val handle: WinNT.HANDLE) : OutputStream() {
        override fun write(b: Int) {
            write(byteArrayOf(b.toByte()), 0, 1)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (len == 0) return
            val bytesWritten = IntByReference()
            val buf = if (off == 0 && len == b.size) b else b.copyOfRange(off, off + len)
            val ok = Kernel32.INSTANCE.WriteFile(handle, buf, len, bytesWritten, null)
            if (!ok) {
                throw IOException("WriteFile to pipe failed: error ${Native.getLastError()}")
            }
        }

        override fun close() {
            Kernel32.INSTANCE.CloseHandle(handle)
        }
    }
}
