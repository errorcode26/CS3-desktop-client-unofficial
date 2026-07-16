package com.lagradost.runtime.permission

import java.util.concurrent.ConcurrentHashMap
import java.util.prefs.Preferences
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.core.type.TypeReference

/**
 * Clean management interface for inspecting and modifying plugin security permissions.
 */
interface PluginPermissionHandler {
    fun hasPermission(pluginId: String, permission: PluginPermission): Boolean
    fun hasPermission(pluginId: String, permissionName: String): Boolean
    fun grantPermission(pluginId: String, permission: PluginPermission)
    fun grantPermission(pluginId: String, permissionName: String)
    fun revokePermission(pluginId: String, permission: PluginPermission)
    fun getGrantedPermissions(pluginId: String): Set<PluginPermission>
}

/**
 * Singleton implementation of PluginPermissionHandler backed by thread-safe persistent preferences.
 */
object PluginPermissionAPI : PluginPermissionHandler {
    private val mapper = jacksonObjectMapper()
    private val prefs = Preferences.userRoot().node("cloudstream_desktop_permissions")

    override fun hasPermission(pluginId: String, permission: PluginPermission): Boolean {
        val grantedMap = getGrantedMap()
        val pluginPermissions = grantedMap[pluginId] ?: return false
        return pluginPermissions.contains(permission.id) || pluginPermissions.contains(permission.displayName)
    }

    override fun hasPermission(pluginId: String, permissionName: String): Boolean {
        val perm = PluginPermission.fromIdOrName(permissionName)
        if (perm != null) {
            return hasPermission(pluginId, perm)
        }
        // Fallback for custom or legacy string permissions not yet in enum
        val grantedMap = getGrantedMap()
        val pluginPermissions = grantedMap[pluginId] ?: return false
        return pluginPermissions.contains(permissionName) || pluginPermissions.contains("Network Sockets")
    }

    override fun grantPermission(pluginId: String, permission: PluginPermission) {
        grantPermissionInternal(pluginId, permission.id)
        println("[Security API] Granted permission '${permission.displayName}' to plugin $pluginId")
    }

    override fun grantPermission(pluginId: String, permissionName: String) {
        val perm = PluginPermission.fromIdOrName(permissionName)
        if (perm != null) {
            grantPermission(pluginId, perm)
        } else {
            grantPermissionInternal(pluginId, permissionName)
            println("[Security API] Granted legacy permission '$permissionName' to plugin $pluginId")
        }
    }

    override fun revokePermission(pluginId: String, permission: PluginPermission) {
        val grantedMap = getGrantedMap()
        val pluginPermissions = grantedMap[pluginId] ?: return
        if (pluginPermissions.remove(permission.id) || pluginPermissions.remove(permission.displayName)) {
            grantedMap[pluginId] = pluginPermissions
            saveGrantedMap(grantedMap)
            println("[Security API] Revoked permission '${permission.displayName}' from plugin $pluginId")
        }
    }

    override fun getGrantedPermissions(pluginId: String): Set<PluginPermission> {
        val grantedMap = getGrantedMap()
        val rawList = grantedMap[pluginId] ?: return emptySet()
        return rawList.mapNotNull { PluginPermission.fromIdOrName(it) }.toSet()
    }

    private fun grantPermissionInternal(pluginId: String, permString: String) {
        val grantedMap = getGrantedMap()
        val pluginPermissions = grantedMap[pluginId] ?: mutableListOf()
        if (!pluginPermissions.contains(permString)) {
            pluginPermissions.add(permString)
            grantedMap[pluginId] = pluginPermissions
            saveGrantedMap(grantedMap)
        }
    }

    private fun getGrantedMap(): ConcurrentHashMap<String, MutableList<String>> {
        val json = prefs.get("granted", "{}")
        return try {
            mapper.readValue(json, object : TypeReference<ConcurrentHashMap<String, MutableList<String>>>() {})
        } catch (e: Exception) {
            ConcurrentHashMap()
        }
    }

    private fun saveGrantedMap(map: ConcurrentHashMap<String, MutableList<String>>) {
        try {
            prefs.put("granted", mapper.writeValueAsString(map))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
