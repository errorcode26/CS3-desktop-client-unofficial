package com.lagradost.cloudstream3.desktop.sandbox.windows

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Manages Windows Access Control Lists (DACLs) for sandboxed AppContainer execution.
 * Grants the low-privilege AppContainer SID read/write access to isolated directories.
 */
object WindowsSandboxAcl {

    private val logger = LoggerFactory.getLogger(WindowsSandboxAcl::class.java)

    // Security Information flags
    private const val DACL_SECURITY_INFORMATION = 0x00000004
    private const val SE_FILE_OBJECT = 1
    private const val SE_WINDOW_OBJECT = 7

    // Access rights
    private const val FILE_GENERIC_READ = 0x00120089
    private const val FILE_GENERIC_WRITE = 0x00120116
    private const val FILE_GENERIC_EXECUTE = 0x001200A0
    private const val FILE_ALL_ACCESS = 0x001F01FF
    private const val DELETE = 0x00010000
    private const val FILE_DELETE_CHILD = 0x00000040
    // Data-only read/write mask: explicitly excludes FILE_EXECUTE (0x00000020)
    private const val FILE_DATA_READ_WRITE = FILE_GENERIC_READ or FILE_GENERIC_WRITE or DELETE or FILE_DELETE_CHILD

    // Inheritance flags: OBJECT_INHERIT_ACE (1) | CONTAINER_INHERIT_ACE (2)
    private const val SUB_CONTAINERS_AND_OBJECTS_INHERIT = 0x03

    // Access mode: GRANT_ACCESS = 1
    private const val GRANT_ACCESS = 1

    // Trustee Form: TRUSTEE_IS_SID = 0
    private const val TRUSTEE_IS_SID = 0
    private const val TRUSTEE_IS_UNKNOWN = 0

    @Structure.FieldOrder("pMultipleTrustee", "MultipleTrusteeOperation", "TrusteeForm", "TrusteeType", "ptstrName")
    open class TRUSTEE_W : Structure() {
        @JvmField var pMultipleTrustee: Pointer? = null
        @JvmField var MultipleTrusteeOperation: Int = 0
        @JvmField var TrusteeForm: Int = TRUSTEE_IS_SID
        @JvmField var TrusteeType: Int = TRUSTEE_IS_UNKNOWN
        @JvmField var ptstrName: Pointer? = null // Holds PSID when TrusteeForm == TRUSTEE_IS_SID
    }

    @Structure.FieldOrder("grfAccessPermissions", "grfAccessMode", "grfInheritance", "Trustee")
    open class EXPLICIT_ACCESS_W : Structure() {
        @JvmField var grfAccessPermissions: Int = FILE_ALL_ACCESS
        @JvmField var grfAccessMode: Int = GRANT_ACCESS
        @JvmField var grfInheritance: Int = SUB_CONTAINERS_AND_OBJECTS_INHERIT
        @JvmField var Trustee: TRUSTEE_W = TRUSTEE_W()
    }

    interface Advapi32Acl : StdCallLibrary {
        companion object {
            val INSTANCE: Advapi32Acl = Native.load("advapi32", Advapi32Acl::class.java, W32APIOptions.UNICODE_OPTIONS)
        }

        fun GetNamedSecurityInfoW(
            pObjectName: WString,
            ObjectType: Int,
            SecurityInfo: Int,
            ppsidOwner: PointerByReference?,
            ppsidGroup: PointerByReference?,
            ppDacl: PointerByReference?,
            ppSacl: PointerByReference?,
            ppSecurityDescriptor: PointerByReference?
        ): Int

        fun SetEntriesInAclW(
            cCountOfExplicitEntries: Int,
            pListOfExplicitEntries: Pointer,
            OldAcl: Pointer?,
            NewAcl: PointerByReference
        ): Int

        fun SetNamedSecurityInfoW(
            pObjectName: WString,
            ObjectType: Int,
            SecurityInfo: Int,
            psidOwner: Pointer?,
            psidGroup: Pointer?,
            pDacl: Pointer?,
            pSacl: Pointer?
        ): Int

        fun GetSecurityInfo(
            handle: Pointer,
            ObjectType: Int,
            SecurityInfo: Int,
            ppsidOwner: PointerByReference?,
            ppsidGroup: PointerByReference?,
            ppDacl: PointerByReference?,
            ppSacl: PointerByReference?,
            ppSecurityDescriptor: PointerByReference?
        ): Int

        fun SetSecurityInfo(
            handle: Pointer,
            ObjectType: Int,
            SecurityInfo: Int,
            psidOwner: Pointer?,
            psidGroup: Pointer?,
            pDacl: Pointer?,
            pSacl: Pointer?
        ): Int

        fun ConvertStringSecurityDescriptorToSecurityDescriptorW(
            StringSecurityDescriptor: WString,
            StringSDRevision: Int,
            SecurityDescriptor: PointerByReference,
            SecurityDescriptorSize: Pointer?
        ): Boolean
    }

    interface User32Ext : StdCallLibrary {
        companion object {
            val INSTANCE: User32Ext = Native.load("user32", User32Ext::class.java, W32APIOptions.UNICODE_OPTIONS)
        }

        fun OpenWindowStationW(lpszWinSta: WString, fInherit: Boolean, dwDesiredAccess: Int): Pointer?
        fun CloseWindowStation(hWinSta: Pointer): Boolean
        fun GetProcessWindowStation(): Pointer?

        fun OpenDesktopW(lpszDesktop: WString, dwFlags: Int, fInherit: Boolean, dwDesiredAccess: Int): Pointer?
        fun CloseDesktop(hDesktop: Pointer): Boolean
        fun GetThreadDesktop(dwThreadId: Int): Pointer?
    }

    /**
     * Grants the specified AppContainer SID full read/write access to the target folder and all sub-items.
     */
    fun grantFolderAccess(folder: File, sidString: String, readOnly: Boolean = false, allowExecute: Boolean = false): Boolean {
        if (!folder.exists()) {
            folder.mkdirs()
        }

        val pSidRef = PointerByReference()
        val converted = WindowsAppContainerApi.Advapi32Sid.INSTANCE.ConvertStringSidToSidW(
            WString(sidString),
            pSidRef
        )

        if (!converted || pSidRef.value == null) {
            logger.error("Failed to convert string SID '$sidString' to binary SID for ACL assignment")
            return false
        }

        val pSid = pSidRef.value
        val pathStr = WString(folder.absolutePath)
        val pSecDescRef = PointerByReference()
        val pOldDaclRef = PointerByReference()

        try {
            // Get existing DACL
            val getResult = Advapi32Acl.INSTANCE.GetNamedSecurityInfoW(
                pathStr,
                SE_FILE_OBJECT,
                DACL_SECURITY_INFORMATION,
                null,
                null,
                pOldDaclRef,
                null,
                pSecDescRef
            )

            if (getResult != 0) {
                logger.warn("GetNamedSecurityInfoW returned $getResult for '${folder.absolutePath}'. Will create fresh DACL.")
            }

            // Create explicit access entry for the container SID
            val ea = EXPLICIT_ACCESS_W().apply {
                grfAccessPermissions = if (readOnly) {
                    if (allowExecute) FILE_GENERIC_READ or FILE_GENERIC_EXECUTE else FILE_GENERIC_READ
                } else {
                    if (allowExecute) FILE_ALL_ACCESS else FILE_DATA_READ_WRITE
                }
                grfAccessMode = GRANT_ACCESS
                grfInheritance = SUB_CONTAINERS_AND_OBJECTS_INHERIT
                Trustee.TrusteeForm = TRUSTEE_IS_SID
                Trustee.TrusteeType = TRUSTEE_IS_UNKNOWN
                Trustee.ptstrName = pSid
            }
            ea.write()

            val pNewDaclRef = PointerByReference()
            val aclResult = Advapi32Acl.INSTANCE.SetEntriesInAclW(
                1,
                ea.pointer,
                pOldDaclRef.value,
                pNewDaclRef
            )

            if (aclResult != 0 || pNewDaclRef.value == null) {
                logger.error("SetEntriesInAclW failed with error code $aclResult")
                return false
            }

            try {
                // Apply new DACL to the folder
                val setResult = Advapi32Acl.INSTANCE.SetNamedSecurityInfoW(
                    pathStr,
                    SE_FILE_OBJECT,
                    DACL_SECURITY_INFORMATION,
                    null,
                    null,
                    pNewDaclRef.value,
                    null
                )

                if (setResult != 0) {
                    if (setResult == 5) {
                        logger.debug("SetNamedSecurityInfoW returned ACCESS_DENIED (5) for '${folder.absolutePath}'. Assuming default system permissions.")
                    } else {
                        logger.error("SetNamedSecurityInfoW failed with error code $setResult for '${folder.absolutePath}'")
                    }
                    return false
                }

                logger.info("Successfully granted AppContainer ACL to '${folder.absolutePath}' for SID $sidString")
                return true
            } finally {
                Kernel32.INSTANCE.LocalFree(pNewDaclRef.value)
            }
        } finally {
            if (pSecDescRef.value != null) {
                Kernel32.INSTANCE.LocalFree(pSecDescRef.value)
            }
            Kernel32.INSTANCE.LocalFree(pSid)
        }
    }

    /**
     * Grants the specified AppContainer SID and ALL APPLICATION PACKAGES access to the current
     * interactive Window Station (WinSta0) and Desktop (Default).
     * Required for Win32 subsystem/CRT initialization in sandboxed AppContainer processes.
     */
    fun grantWindowStationAndDesktopAccess(sidString: String): Boolean {
        val sids = listOf(sidString, "S-1-15-2-1")
        var allOk = true
        for (sid in sids) {
            allOk = grantSingleSidToWindowStationAndDesktop(sid) && allOk
        }
        return allOk
    }

    private fun grantSingleSidToWindowStationAndDesktop(sidString: String): Boolean {
        val pSidRef = PointerByReference()
        if (!WindowsAppContainerApi.Advapi32Sid.INSTANCE.ConvertStringSidToSidW(WString(sidString), pSidRef)) {
            logger.warn("Failed to convert SID string '$sidString' to binary SID")
            return false
        }
        val pSid = pSidRef.value ?: return false

        val dwDesiredAccess = 0x00060000 // READ_CONTROL | WRITE_DAC

        val openedWinSta = try {
            User32Ext.INSTANCE.OpenWindowStationW(WString("winsta0"), false, dwDesiredAccess)
        } catch (t: Throwable) {
            logger.warn("OpenWindowStationW failed: ${t.message}")
            null
        }
        val hWinSta = openedWinSta ?: try {
            User32Ext.INSTANCE.GetProcessWindowStation()
        } catch (t: Throwable) {
            logger.warn("GetProcessWindowStation failed: ${t.message}")
            null
        }

        val openedDesk = try {
            User32Ext.INSTANCE.OpenDesktopW(WString("default"), 0, false, dwDesiredAccess)
        } catch (t: Throwable) {
            logger.warn("OpenDesktopW failed: ${t.message}")
            null
        }
        val hDesk = openedDesk ?: try {
            User32Ext.INSTANCE.GetThreadDesktop(Kernel32.INSTANCE.GetCurrentThreadId())
        } catch (t: Throwable) {
            logger.warn("GetThreadDesktop failed: ${t.message}")
            null
        }

        try {
            var ok = true
            if (hWinSta != null) {
                ok = grantSidToHandle(hWinSta, pSid, SE_WINDOW_OBJECT) && ok
            } else {
                logger.warn("Window station handle could not be resolved for SID $sidString")
            }
            if (hDesk != null) {
                ok = grantSidToHandle(hDesk, pSid, SE_WINDOW_OBJECT) && ok
            } else {
                logger.warn("Desktop handle could not be resolved for SID $sidString")
            }
            return ok
        } finally {
            if (openedWinSta != null) {
                try { User32Ext.INSTANCE.CloseWindowStation(openedWinSta) } catch (_: Throwable) {}
            }
            if (openedDesk != null) {
                try { User32Ext.INSTANCE.CloseDesktop(openedDesk) } catch (_: Throwable) {}
            }
            Kernel32.INSTANCE.LocalFree(pSid)
        }
    }

    private fun grantSidToHandle(handle: Pointer, pSid: Pointer, objectType: Int): Boolean {
        val pOldDaclRef = PointerByReference()
        val pSecDescRef = PointerByReference()

        val getResult = Advapi32Acl.INSTANCE.GetSecurityInfo(
            handle,
            objectType,
            DACL_SECURITY_INFORMATION,
            null,
            null,
            pOldDaclRef,
            null,
            pSecDescRef
        )
        if (getResult != 0) {
            logger.warn("GetSecurityInfo failed for window object handle with error code $getResult")
            return false
        }

        val ea = EXPLICIT_ACCESS_W().apply {
            grfAccessPermissions = 0x10000000 // GENERIC_ALL
            grfAccessMode = GRANT_ACCESS
            grfInheritance = 0
            Trustee.TrusteeForm = TRUSTEE_IS_SID
            Trustee.TrusteeType = TRUSTEE_IS_UNKNOWN
            Trustee.ptstrName = pSid
        }
        ea.write()

        val pNewDaclRef = PointerByReference()
        val aclResult = Advapi32Acl.INSTANCE.SetEntriesInAclW(
            1,
            ea.pointer,
            pOldDaclRef.value,
            pNewDaclRef
        )
        if (aclResult != 0 || pNewDaclRef.value == null) {
            logger.warn("SetEntriesInAclW failed for window object handle with error code $aclResult")
            if (pSecDescRef.value != null) Kernel32.INSTANCE.LocalFree(pSecDescRef.value)
            return false
        }

        try {
            val setResult = Advapi32Acl.INSTANCE.SetSecurityInfo(
                handle,
                objectType,
                DACL_SECURITY_INFORMATION,
                null,
                null,
                pNewDaclRef.value,
                null
            )
            if (setResult != 0) {
                logger.warn("SetSecurityInfo failed for window object handle with error code $setResult")
                return false
            }
            return true
        } finally {
            Kernel32.INSTANCE.LocalFree(pNewDaclRef.value)
            if (pSecDescRef.value != null) {
                Kernel32.INSTANCE.LocalFree(pSecDescRef.value)
            }
        }
    }
}
