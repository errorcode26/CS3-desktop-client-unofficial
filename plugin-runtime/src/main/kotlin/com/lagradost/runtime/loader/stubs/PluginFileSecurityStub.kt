package com.lagradost.runtime.loader.stubs

import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import com.lagradost.runtime.loader.ExtensionLoader
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/**
 * Bytecode-injected security stub that intercepts and enforces filesystem access boundaries
 * for CloudStream Desktop plugins. Restricts plugins to their isolated storage directory
 * unless explicitly granted by the user.
 */
object PluginFileSecurityStub {
    private const val TAG = "PluginFileSecurity"

    // Thread-safe map of user-granted paths: pluginName -> Set<File>
    private val grantedPaths = ConcurrentHashMap<String, MutableSet<File>>()

    // Allow tests or custom environments to override the base directory
    @Volatile
    var customBaseDir: File? = null

    fun grantAllowedPath(pluginName: String, path: File) {
        grantedPaths.computeIfAbsent(pluginName) { ConcurrentHashMap.newKeySet() }
            .add(path.canonicalFile)
        AppLogger.i("$TAG: Granted path '${path.canonicalPath}' to plugin '$pluginName'")
    }

    fun revokeAllowedPath(pluginName: String, path: File) {
        grantedPaths[pluginName]?.remove(path.canonicalFile)
        AppLogger.i("$TAG: Revoked path '${path.canonicalPath}' from plugin '$pluginName'")
    }

    fun clearGrantedPaths(pluginName: String? = null) {
        if (pluginName != null) {
            grantedPaths.remove(pluginName)
        } else {
            grantedPaths.clear()
        }
    }

    fun getStorageRootForPlugin(pluginName: String): File {
        val base = customBaseDir ?: PlatformPaths.appDataDir
        val safePluginDirName = pluginName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val root = File(base, "Extensions/$safePluginDirName/storage").canonicalFile
        if (!root.exists()) {
            root.mkdirs()
        }
        return root
    }

    /**
     * Checks whether [target] canonical path lies inside [jailRoot] directory.
     */
    private fun isPathInside(target: File, jailRoot: File): Boolean {
        var curr: File? = target
        while (curr != null) {
            if (curr == jailRoot) return true
            curr = curr.parentFile
        }
        return false
    }

    /**
     * Validates that [rawPath] points inside the calling plugin's storage directory
     * or a user-granted path. Relative paths are automatically resolved against
     * the plugin's storage root. Returns the validated canonical/absolute path.
     */
    @JvmStatic
    fun checkPath(rawPath: String): String {
        val pluginName = ExtensionLoader.getCallingPluginName() ?: "UnknownPlugin"
        val storageRoot = getStorageRootForPlugin(pluginName)

        val rawFile = File(rawPath)
        val target = if (rawFile.isAbsolute) {
            rawFile.canonicalFile
        } else {
            File(storageRoot, rawPath).canonicalFile
        }

        // Check if inside default plugin storage
        if (isPathInside(target, storageRoot)) {
            return target.absolutePath
        }

        // Check if inside any explicitly granted paths for this plugin
        val pluginGrants = grantedPaths[pluginName]
        if (pluginGrants != null) {
            for (granted in pluginGrants) {
                if (isPathInside(target, granted)) {
                    return target.absolutePath
                }
            }
        }

        AppLogger.w("$TAG: Blocked unauthorized file access to '$rawPath' (resolved: '$target') by plugin '$pluginName'")
        throw SecurityException(
            "Plugin File Security: Access to '$rawPath' (resolved: '$target') is blocked. " +
            "Plugin '$pluginName' is restricted to its storage directory '$storageRoot'."
        )
    }

    /**
     * Validates an existing [File] object before stream creation or filesystem inspection.
     */
    @JvmStatic
    fun checkFile(file: File): File {
        val verifiedPath = checkPath(file.path)
        return File(verifiedPath)
    }

    /**
     * Validates a parent-child path combination.
     */
    @JvmStatic
    fun checkParentChild(parent: String, child: String): String {
        return checkPath(File(parent, child).path)
    }

    /**
     * Validates a File parent and String child combination.
     */
    @JvmStatic
    fun checkFileChild(parent: File, child: String): File {
        val combined = File(parent, child)
        return File(checkPath(combined.path))
    }

    @JvmStatic
    fun checkFileChildPath(parent: File, child: String): String {
        return checkPath(File(parent, child).path)
    }

    /**
     * Validates a URI before File creation.
     */
    @JvmStatic
    fun checkUri(uri: URI): URI {
        val file = File(uri)
        checkPath(file.path)
        return uri
    }

    /**
     * Intercepts java.nio.file.Paths.get and Path.of.
     */
    @JvmStatic
    fun getPath(first: String, vararg more: String): Path {
        val rawPath = if (more.isEmpty()) {
            first
        } else {
            Paths.get(first, *more).toString()
        }
        val safePath = checkPath(rawPath)
        return Paths.get(safePath)
    }
}
