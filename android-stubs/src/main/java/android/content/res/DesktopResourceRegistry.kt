package android.content.res

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object DesktopResourceRegistry {
    private val layoutById = ConcurrentHashMap<Int, String>()
    private val layoutByName = ConcurrentHashMap<String, String>()
    private val layoutByPluginAndName = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()

    private val xmlById = ConcurrentHashMap<Int, String>()
    private val xmlByName = ConcurrentHashMap<String, String>()
    private val xmlByPluginAndName = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()

    private val nameToId = ConcurrentHashMap<String, Int>()
    private val idToName = ConcurrentHashMap<Int, String>()
    private val nextSyntheticId = AtomicInteger(0x7f0a0000)

    fun normalizePluginKey(key: String): String {
        var clean = key.trim().lowercase()
        while (clean.endsWith("_")) {
            clean = clean.dropLast(1)
        }
        if (clean.length > 8 && clean.endsWith("provider")) {
            clean = clean.removeSuffix("provider")
        }
        return clean
    }

    fun registerPluginLayout(pluginId: String, id: Int, name: String, xmlContent: String) {
        val cleanPlugin = normalizePluginKey(pluginId)
        val map = layoutByPluginAndName.computeIfAbsent(cleanPlugin) { ConcurrentHashMap() }
        map[name] = xmlContent
        map[name.lowercase()] = xmlContent

        layoutByName["${cleanPlugin}_$name"] = xmlContent
        layoutByName["${cleanPlugin}__$name"] = xmlContent
        layoutByName["${pluginId}_$name"] = xmlContent

        if (id != 0) {
            layoutById[id] = xmlContent
            idToName[id] = name
            nameToId[name] = id
            nameToId[name.lowercase()] = id
        }
    }

    fun registerPluginXml(pluginId: String, id: Int, name: String, xmlContent: String) {
        val cleanPlugin = normalizePluginKey(pluginId)
        val map = xmlByPluginAndName.computeIfAbsent(cleanPlugin) { ConcurrentHashMap() }
        map[name] = xmlContent
        map[name.lowercase()] = xmlContent

        xmlByName["${cleanPlugin}_$name"] = xmlContent
        xmlByName["${cleanPlugin}__$name"] = xmlContent
        xmlByName["${pluginId}_$name"] = xmlContent

        if (id != 0) {
            xmlById[id] = xmlContent
            idToName[id] = name
            nameToId[name] = id
            nameToId[name.lowercase()] = id
        }
    }

    fun registerLayout(id: Int, name: String, xmlContent: String) {
        if (id != 0) {
            layoutById[id] = xmlContent
            idToName[id] = name
            nameToId[name] = id
            nameToId[name.lowercase()] = id
        }
        layoutByName[name] = xmlContent
        layoutByName[name.lowercase()] = xmlContent
    }

    fun registerXml(id: Int, name: String, xmlContent: String) {
        if (id != 0) {
            xmlById[id] = xmlContent
            idToName[id] = name
            nameToId[name] = id
            nameToId[name.lowercase()] = id
        }
        xmlByName[name] = xmlContent
        xmlByName[name.lowercase()] = xmlContent
    }

    fun registerId(name: String, id: Int) {
        val clean = name.removePrefix("@+id/").removePrefix("@id/")
        nameToId[clean] = id
        nameToId[clean.lowercase()] = id
        idToName[id] = clean
    }

    fun resolveId(name: String): Int {
        val clean = name.removePrefix("@+id/").removePrefix("@id/")
        return nameToId[clean] ?: nameToId[clean.lowercase()] ?: run {
            val gen = nextSyntheticId.incrementAndGet()
            nameToId[clean] = gen
            idToName[gen] = clean
            gen
        }
    }

    fun getLayoutXml(id: Int): String? = layoutById[id] ?: idToName[id]?.let { layoutByName[it] }

    fun getLayoutXml(name: String, pluginId: String? = null): String? {
        if (pluginId != null) {
            val cleanPlugin = normalizePluginKey(pluginId)
            val scoped = layoutByPluginAndName[cleanPlugin]?.get(name)
                ?: layoutByPluginAndName[cleanPlugin]?.get(name.lowercase())
                ?: layoutByName["${cleanPlugin}_$name"]
            if (scoped != null) return scoped
        }
        return layoutByName[name] ?: layoutByName[name.lowercase()]
    }

    fun getLayoutParser(id: Int): XmlResourceParser? {
        val sessionName = com.lagradost.cloudstream3.desktop.shadowui.ShadowUi.sessionPluginId.value
        val sessionKey = sessionName?.let { normalizePluginKey(it) }

        // 1. Scoped lookup for active session plugin
        val scopedXml = sessionKey?.let { k ->
            val pluginMap = layoutByPluginAndName[k]
            val named = idToName[id]?.lowercase()
            if (named != null) {
                pluginMap?.get(named)
            } else {
                pluginMap?.get("settings") ?: pluginMap?.get("settings_fragment")
            } ?: layoutByName["${k}_settings"] ?: layoutByName["${k}__settings"]
        }

        // 2. Direct ID or mapped lookup
        val xml = scopedXml
            ?: layoutById[id]
            ?: idToName[id]?.let { layoutByName[it] }
            ?: return null

        return AndroidXmlResourceParser(xml)
    }

    fun getXmlParser(id: Int): XmlResourceParser? {
        val sessionName = com.lagradost.cloudstream3.desktop.shadowui.ShadowUi.sessionPluginId.value
        val sessionKey = sessionName?.let { normalizePluginKey(it) }

        val scopedXml = sessionKey?.let { k ->
            val pluginMap = xmlByPluginAndName[k]
            val named = idToName[id]?.lowercase()
            if (named != null) {
                pluginMap?.get(named)
            } else {
                pluginMap?.get("settings")
            }
        }

        val xml = scopedXml
            ?: xmlById[id]
            ?: idToName[id]?.let { xmlByName[it] }
            ?: layoutById[id]
            ?: return null

        return AndroidXmlResourceParser(xml)
    }
}
