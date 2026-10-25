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

/**
 * Windows AppContainer native API bindings and management service.
 * Isolates extension execution inside a low-privilege OS AppContainer sandbox.
 */
object WindowsAppContainerApi {

    private val logger = LoggerFactory.getLogger(WindowsAppContainerApi::class.java)

    // Capability SIDs
    // internetClient: S-1-15-3-1 (Outbound HTTP/HTTPS to public internet)
    const val CAPABILITY_INTERNET_CLIENT = "S-1-15-3-1"
    // internetClientServer: S-1-15-3-2 (Inbound and outbound HTTP/HTTPS to public internet)
    const val CAPABILITY_INTERNET_CLIENT_SERVER = "S-1-15-3-2"
    // privateNetworkClientServer: S-1-15-3-3 (Inbound and outbound to local network, router DNS & localhost)
    const val CAPABILITY_PRIVATE_NETWORK_CLIENT_SERVER = "S-1-15-3-3"

    // Error codes
    private const val S_OK = 0
    private const val ERROR_ALREADY_EXISTS_HRESULT = -2147024713 // 0x800700B7: HRESULT_FROM_WIN32(ERROR_ALREADY_EXISTS)

    @Structure.FieldOrder("Sid", "Attributes")
    open class SID_AND_ATTRIBUTES : Structure {
        @JvmField var Sid: Pointer? = null
        @JvmField var Attributes: Int = SE_GROUP_ENABLED

        constructor() : super()
        constructor(p: Pointer) : super(p)

        companion object {
            const val SE_GROUP_ENABLED = 0x00000004
        }
    }

    interface Userenv : StdCallLibrary {
        companion object {
            val INSTANCE: Userenv = Native.load("userenv", Userenv::class.java, W32APIOptions.UNICODE_OPTIONS)
        }

        fun CreateAppContainerProfile(
            pszAppContainerName: WString,
            pszDisplayName: WString,
            pszDescription: WString,
            pCapabilities: Pointer?,
            dwCapabilityCount: Int,
            ppSidMapping: PointerByReference
        ): Int

        fun DeriveAppContainerSidFromAppContainerName(
            pszAppContainerName: WString,
            ppsidAppContainerSid: PointerByReference
        ): Int

        fun DeleteAppContainerProfile(
            pszAppContainerName: WString
        ): Int
    }

    interface Advapi32Sid : StdCallLibrary {
        companion object {
            val INSTANCE: Advapi32Sid = Native.load("advapi32", Advapi32Sid::class.java, W32APIOptions.UNICODE_OPTIONS)
        }

        fun ConvertSidToStringSidW(pSid: Pointer, stringSid: PointerByReference): Boolean
        fun ConvertStringSidToSidW(stringSid: WString, pSid: PointerByReference): Boolean
        fun FreeSid(pSid: Pointer): Pointer?
    }

    /**
     * Derives or creates an AppContainer profile with the specified capabilities.
     * Returns the string representation of the container SID (e.g. "S-1-15-2-...").
     */
    fun getOrCreateAppContainer(
        containerName: String,
        displayName: String = containerName,
        description: String = "CloudStream Sandboxed Worker",
        capabilities: List<String> = listOf(CAPABILITY_INTERNET_CLIENT)
    ): String? {
        logger.info("Registering or fetching AppContainer profile for '$containerName'...")

        // Convert capability strings to SIDs
        val sidPointers = mutableListOf<Pointer>()
        var capabilitiesArrayPointer: Pointer? = null

        try {
            if (capabilities.isNotEmpty()) {
                val itemSize = Structure.newInstance(SID_AND_ATTRIBUTES::class.java).size()
                val memory = com.sun.jna.Memory((itemSize * capabilities.size).toLong())

                for (i in capabilities.indices) {
                    val capSidRef = PointerByReference()
                    val converted = Advapi32Sid.INSTANCE.ConvertStringSidToSidW(WString(capabilities[i]), capSidRef)
                    if (converted && capSidRef.value != null) {
                        sidPointers.add(capSidRef.value)

                        val item = SID_AND_ATTRIBUTES(memory.share((i * itemSize).toLong()))
                        item.Sid = capSidRef.value
                        item.Attributes = SID_AND_ATTRIBUTES.SE_GROUP_ENABLED
                        item.write()
                    }
                }
                capabilitiesArrayPointer = memory
            }

            val ppSid = PointerByReference()
            val hr = Userenv.INSTANCE.CreateAppContainerProfile(
                WString(containerName),
                WString(displayName),
                WString(description),
                capabilitiesArrayPointer,
                sidPointers.size,
                ppSid
            )

            if (hr == S_OK && ppSid.value != null) {
                val sidString = sidToString(ppSid.value)
                Advapi32Sid.INSTANCE.FreeSid(ppSid.value)
                logger.info("Successfully created AppContainer '$containerName' -> $sidString")
                return sidString
            } else if (hr == ERROR_ALREADY_EXISTS_HRESULT) {
                logger.info("AppContainer profile '$containerName' already exists. Deriving SID...")
                return deriveAppContainerSid(containerName)
            } else {
                logger.error("CreateAppContainerProfile failed for '$containerName' with HRESULT: 0x${Integer.toHexString(hr)}")
                return null
            }
        } finally {
            for (p in sidPointers) {
                Kernel32.INSTANCE.LocalFree(p)
            }
        }
    }

    /**
     * Derives the deterministic AppContainer SID for a given container name.
     */
    fun deriveAppContainerSid(containerName: String): String? {
        val ppSid = PointerByReference()
        val hr = Userenv.INSTANCE.DeriveAppContainerSidFromAppContainerName(WString(containerName), ppSid)
        if (hr != S_OK || ppSid.value == null) {
            return null
        }

        return try {
            sidToString(ppSid.value)
        } finally {
            Advapi32Sid.INSTANCE.FreeSid(ppSid.value)
        }
    }

    /**
     * Deletes an AppContainer profile from the system.
     */
    fun deleteAppContainer(containerName: String): Boolean {
        val hr = Userenv.INSTANCE.DeleteAppContainerProfile(WString(containerName))
        val success = (hr == S_OK)
        if (success) {
            logger.info("Deleted AppContainer profile '$containerName'")
        } else {
            logger.warn("Failed to delete AppContainer profile '$containerName'. HRESULT: 0x${Integer.toHexString(hr)}")
        }
        return success
    }

    private fun sidToString(pSid: Pointer): String? {
        val strRef = PointerByReference()
        if (!Advapi32Sid.INSTANCE.ConvertSidToStringSidW(pSid, strRef)) {
            return null
        }
        return try {
            strRef.value.getWideString(0)
        } finally {
            Kernel32.INSTANCE.LocalFree(strRef.value)
        }
    }
}
