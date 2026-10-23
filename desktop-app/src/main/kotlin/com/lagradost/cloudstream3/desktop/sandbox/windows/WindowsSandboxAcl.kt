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

    // Access rights
    private const val FILE_GENERIC_READ = 0x00120089
    private const val FILE_GENERIC_WRITE = 0x00120116
    private const val FILE_GENERIC_EXECUTE = 0x001200A0
    private const val FILE_ALL_ACCESS = 0x001F01FF

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
    }

    /**
     * Grants the specified AppContainer SID full read/write access to the target folder and all sub-items.
     */
    fun grantFolderAccess(folder: File, sidString: String, readOnly: Boolean = false): Boolean {
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
                    FILE_GENERIC_READ or FILE_GENERIC_EXECUTE
                } else {
                    FILE_ALL_ACCESS
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
                    logger.error("SetNamedSecurityInfoW failed with error code $setResult for '${folder.absolutePath}'")
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
}
