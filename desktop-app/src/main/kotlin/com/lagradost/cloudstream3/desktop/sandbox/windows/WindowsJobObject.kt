package com.lagradost.cloudstream3.desktop.sandbox.windows

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.BaseTSD
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import org.slf4j.LoggerFactory
import java.io.Closeable

/**
 * Windows Job Object wrapper for constraining child worker processes.
 * Enforces:
 * 1. Hard memory ceiling (default 1024 MB via JOB_OBJECT_LIMIT_JOB_MEMORY)
 * 2. Automatic termination of child processes when the parent desktop app closes (JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE)
 */
class WindowsJobObject(
    val jobName: String? = null,
    val memoryLimitBytes: Long = 1024L * 1024L * 1024L
) : Closeable {

    private val logger = LoggerFactory.getLogger(WindowsJobObject::class.java)

    var handle: WinNT.HANDLE? = null
        private set

    init {
        initJobObject()
    }

    private fun initJobObject() {
        val hJob = JobKernel32.INSTANCE.CreateJobObjectW(
            null,
            jobName?.let { WString(it) }
        )

        if (hJob == null || WinNT.INVALID_HANDLE_VALUE == hJob) {
            val err = Kernel32.INSTANCE.GetLastError()
            logger.error("Failed to create Win32 Job Object '$jobName'. Error code: $err")
            return
        }

        this.handle = hJob

        val extendedLimits = JOBOBJECT_EXTENDED_LIMIT_INFORMATION().apply {
            BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE or JOB_OBJECT_LIMIT_JOB_MEMORY
            JobMemoryLimit = BaseTSD.SIZE_T(memoryLimitBytes)
        }
        extendedLimits.write()

        val success = JobKernel32.INSTANCE.SetInformationJobObject(
            hJob,
            JobObjectExtendedLimitInformation,
            extendedLimits.pointer,
            extendedLimits.size()
        )

        if (!success) {
            val err = Kernel32.INSTANCE.GetLastError()
            logger.error("Failed to set memory/lifecycle limits on Job Object. Error code: $err")
        } else {
            logger.info("Win32 Job Object initialized successfully (Cap: ${memoryLimitBytes / (1024 * 1024)} MB, KILL_ON_JOB_CLOSE: true)")
        }
    }

    /**
     * Assigns an active process to this Job Object.
     */
    fun assignProcess(process: Process): Boolean {
        val hJob = handle ?: return false
        val pid = try {
            process.pid().toInt()
        } catch (e: Throwable) {
            logger.error("Failed to get process PID", e)
            return false
        }

        val hProcess = Kernel32.INSTANCE.OpenProcess(
            PROCESS_SET_QUOTA or PROCESS_TERMINATE,
            false,
            pid
        )

        if (hProcess == null || WinNT.INVALID_HANDLE_VALUE == hProcess) {
            val err = Kernel32.INSTANCE.GetLastError()
            logger.error("Failed to OpenProcess for PID $pid. Error code: $err")
            return false
        }

        return try {
            val assigned = JobKernel32.INSTANCE.AssignProcessToJobObject(hJob, hProcess)
            if (!assigned) {
                val err = Kernel32.INSTANCE.GetLastError()
                logger.error("Failed to AssignProcessToJobObject for PID $pid. Error code: $err")
            } else {
                logger.info("Successfully bound PID $pid to Job Object")
            }
            assigned
        } finally {
            Kernel32.INSTANCE.CloseHandle(hProcess)
        }
    }

    /**
     * Assigns a process handle directly to this Job Object.
     */
    fun assignProcessHandle(hProcess: WinNT.HANDLE): Boolean {
        val hJob = handle ?: return false
        return JobKernel32.INSTANCE.AssignProcessToJobObject(hJob, hProcess)
    }

    override fun close() {
        handle?.let {
            if (it != WinNT.INVALID_HANDLE_VALUE) {
                Kernel32.INSTANCE.CloseHandle(it)
            }
            handle = null
        }
    }

    companion object {
        const val JOB_OBJECT_LIMIT_JOB_MEMORY = 0x00000200
        const val JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x00002000
        const val JobObjectExtendedLimitInformation = 9

        const val PROCESS_TERMINATE = 0x0001
        const val PROCESS_SET_QUOTA = 0x0100
    }
}

@Structure.FieldOrder(
    "PerProcessUserTimeLimit",
    "PerJobUserTimeLimit",
    "LimitFlags",
    "MinimumWorkingSetSize",
    "MaximumWorkingSetSize",
    "ActiveProcessLimit",
    "Affinity",
    "PriorityClass",
    "SchedulingClass"
)
open class JOBOBJECT_BASIC_LIMIT_INFORMATION : Structure() {
    @JvmField var PerProcessUserTimeLimit: Long = 0
    @JvmField var PerJobUserTimeLimit: Long = 0
    @JvmField var LimitFlags: Int = 0
    @JvmField var MinimumWorkingSetSize: BaseTSD.SIZE_T = BaseTSD.SIZE_T(0)
    @JvmField var MaximumWorkingSetSize: BaseTSD.SIZE_T = BaseTSD.SIZE_T(0)
    @JvmField var ActiveProcessLimit: Int = 0
    @JvmField var Affinity: BaseTSD.ULONG_PTR = BaseTSD.ULONG_PTR(0)
    @JvmField var PriorityClass: Int = 0
    @JvmField var SchedulingClass: Int = 0
}

@Structure.FieldOrder(
    "ReadOperationCount",
    "WriteOperationCount",
    "OtherOperationCount",
    "ReadTransferCount",
    "WriteTransferCount",
    "OtherTransferCount"
)
open class IO_COUNTERS : Structure() {
    @JvmField var ReadOperationCount: Long = 0
    @JvmField var WriteOperationCount: Long = 0
    @JvmField var OtherOperationCount: Long = 0
    @JvmField var ReadTransferCount: Long = 0
    @JvmField var WriteTransferCount: Long = 0
    @JvmField var OtherTransferCount: Long = 0
}

@Structure.FieldOrder(
    "BasicLimitInformation",
    "IoInfo",
    "ProcessMemoryLimit",
    "JobMemoryLimit",
    "PeakProcessMemoryUsed",
    "PeakJobMemoryUsed"
)
open class JOBOBJECT_EXTENDED_LIMIT_INFORMATION : Structure() {
    @JvmField var BasicLimitInformation: JOBOBJECT_BASIC_LIMIT_INFORMATION = JOBOBJECT_BASIC_LIMIT_INFORMATION()
    @JvmField var IoInfo: IO_COUNTERS = IO_COUNTERS()
    @JvmField var ProcessMemoryLimit: BaseTSD.SIZE_T = BaseTSD.SIZE_T(0)
    @JvmField var JobMemoryLimit: BaseTSD.SIZE_T = BaseTSD.SIZE_T(0)
    @JvmField var PeakProcessMemoryUsed: BaseTSD.SIZE_T = BaseTSD.SIZE_T(0)
    @JvmField var PeakJobMemoryUsed: BaseTSD.SIZE_T = BaseTSD.SIZE_T(0)
}

interface JobKernel32 : StdCallLibrary {
    companion object {
        val INSTANCE: JobKernel32 = Native.load("kernel32", JobKernel32::class.java, W32APIOptions.UNICODE_OPTIONS)
    }

    fun CreateJobObjectW(lpJobAttributes: Pointer?, lpName: WString?): WinNT.HANDLE?

    fun SetInformationJobObject(
        hJob: WinNT.HANDLE,
        JobObjectInformationClass: Int,
        lpJobObjectInformation: Pointer,
        cbJobObjectInformationLength: Int
    ): Boolean

    fun AssignProcessToJobObject(hJob: WinNT.HANDLE, hProcess: WinNT.HANDLE): Boolean
}
