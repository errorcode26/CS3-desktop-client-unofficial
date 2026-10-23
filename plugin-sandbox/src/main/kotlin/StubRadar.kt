import org.objectweb.asm.*
import java.io.File
import java.util.zip.ZipFile

data class MissingApi(
    val type: ApiType,
    val owner: String,
    val name: String,
    val descriptor: String,
    val usedByPlugins: MutableSet<String> = mutableSetOf(),
) {
    enum class ApiType { CLASS, CONSTRUCTOR, METHOD, FIELD }
}

data class PluginScanSummary(
    val pluginName: String,
    val jarFile: File,
    val androidClasses: Set<String>,
    val xmlTags: Set<String>,
    val missingApis: List<MissingApi>,
    val conversionError: String? = null,
)

object StubRadar {

    private val stubClassLoader: ClassLoader = this::class.java.classLoader

    private fun isAndroidClass(name: String): Boolean {
        return name.startsWith("android/") ||
            name.startsWith("androidx/") ||
            name.startsWith("com/google/android/material/")
    }

    private fun extractTypesFromDescriptor(descriptor: String): List<String> {
        val types = mutableListOf<String>()
        var i = 0
        while (i < descriptor.length) {
            if (descriptor[i] == 'L') {
                val end = descriptor.indexOf(';', i)
                if (end != -1) {
                    types.add(descriptor.substring(i + 1, end))
                    i = end
                }
            }
            i++
        }
        return types
    }

    private fun parseTypesToClasses(typeList: List<Type>, loader: ClassLoader): Array<Class<*>>? {
        val result = ArrayList<Class<*>>()
        for (t in typeList) {
            when (t.sort) {
                Type.BOOLEAN -> result.add(Boolean::class.javaPrimitiveType ?: java.lang.Boolean.TYPE)
                Type.BYTE -> result.add(Byte::class.javaPrimitiveType ?: java.lang.Byte.TYPE)
                Type.CHAR -> result.add(Char::class.javaPrimitiveType ?: java.lang.Character.TYPE)
                Type.SHORT -> result.add(Short::class.javaPrimitiveType ?: java.lang.Short.TYPE)
                Type.INT -> result.add(Int::class.javaPrimitiveType ?: java.lang.Integer.TYPE)
                Type.LONG -> result.add(Long::class.javaPrimitiveType ?: java.lang.Long.TYPE)
                Type.FLOAT -> result.add(Float::class.javaPrimitiveType ?: java.lang.Float.TYPE)
                Type.DOUBLE -> result.add(Double::class.javaPrimitiveType ?: java.lang.Double.TYPE)
                Type.ARRAY -> {
                    val elementClz = when (t.elementType.sort) {
                        Type.OBJECT -> runCatching { Class.forName(t.elementType.internalName.replace('/', '.'), false, loader) }.getOrNull() ?: return null
                        Type.INT -> java.lang.Integer.TYPE
                        Type.BOOLEAN -> java.lang.Boolean.TYPE
                        Type.BYTE -> java.lang.Byte.TYPE
                        Type.CHAR -> java.lang.Character.TYPE
                        Type.LONG -> java.lang.Long.TYPE
                        Type.FLOAT -> java.lang.Float.TYPE
                        Type.DOUBLE -> java.lang.Double.TYPE
                        else -> Any::class.java
                    }
                    var arrClz: Class<*> = java.lang.reflect.Array.newInstance(elementClz, 0).javaClass
                    for (dim in 1 until t.dimensions) {
                        arrClz = java.lang.reflect.Array.newInstance(arrClz, 0).javaClass
                    }
                    result.add(arrClz)
                }
                Type.OBJECT -> {
                    val clz = runCatching { Class.forName(t.internalName.replace('/', '.'), false, loader) }.getOrNull()
                    if (clz == null) return null
                    result.add(clz)
                }
                else -> return null
            }
        }
        return result.toTypedArray()
    }

    fun scanJar(jarFile: File, cs3File: File? = null): PluginScanSummary {
        val pluginName = cs3File?.name ?: jarFile.name
        val androidClasses = mutableSetOf<String>()
        val missingApis = mutableListOf<MissingApi>()
        val classCache = mutableMapOf<String, Class<*>?>()

        fun loadTargetClass(internalName: String): Class<*>? {
            return classCache.getOrPut(internalName) {
                runCatching {
                    Class.forName(internalName.replace('/', '.'), false, stubClassLoader)
                }.getOrNull()
            }
        }

        fun checkMethodResolves(owner: String, name: String, descriptor: String, isSpecial: Boolean): Boolean {
            val clz = loadTargetClass(owner) ?: return false
            val argTypes = Type.getArgumentTypes(descriptor)
            val paramClasses = parseTypesToClasses(argTypes.toList(), stubClassLoader) ?: return false

            if (name == "<init>") {
                return clz.declaredConstructors.any { c ->
                    c.parameterTypes.contentEquals(paramClasses)
                }
            }

            var curr: Class<*>? = clz
            while (curr != null) {
                if (curr.declaredMethods.any { m -> m.name == name && m.parameterTypes.contentEquals(paramClasses) }) {
                    return true
                }
                curr = curr.superclass
            }
            return clz.methods.any { m -> m.name == name && m.parameterTypes.contentEquals(paramClasses) }
        }

        fun checkFieldResolves(owner: String, name: String): Boolean {
            val clz = loadTargetClass(owner) ?: return false
            var curr: Class<*>? = clz
            while (curr != null) {
                if (curr.declaredFields.any { f -> f.name == name }) return true
                curr = curr.superclass
            }
            return clz.fields.any { f -> f.name == name }
        }

        ZipFile(jarFile).use { zip ->
            for (entry in zip.entries()) {
                if (entry.name.endsWith(".class")) {
                    zip.getInputStream(entry).use { input ->
                        val reader = ClassReader(input)
                        reader.accept(
                            object : ClassVisitor(Opcodes.ASM9) {
                                override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
                                    if (superName != null && isAndroidClass(superName)) {
                                        androidClasses.add(superName)
                                        if (loadTargetClass(superName) == null) {
                                            missingApis.add(MissingApi(MissingApi.ApiType.CLASS, superName, "", ""))
                                        }
                                    }
                                    interfaces?.forEach { iface ->
                                        if (isAndroidClass(iface)) {
                                            androidClasses.add(iface)
                                            if (loadTargetClass(iface) == null) {
                                                missingApis.add(MissingApi(MissingApi.ApiType.CLASS, iface, "", ""))
                                            }
                                        }
                                    }
                                    super.visit(version, access, name, signature, superName, interfaces)
                                }

                                override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                                    extractTypesFromDescriptor(descriptor).forEach { if (isAndroidClass(it)) androidClasses.add(it) }

                                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                                    return object : MethodVisitor(Opcodes.ASM9, mv) {
                                        override fun visitMethodInsn(opcode: Int, owner: String, methodName: String, methodDesc: String, isInterface: Boolean) {
                                            if (isAndroidClass(owner)) {
                                                androidClasses.add(owner)
                                                val resolves = checkMethodResolves(owner, methodName, methodDesc, opcode == Opcodes.INVOKESPECIAL)
                                                if (!resolves) {
                                                    val type = if (methodName == "<init>") MissingApi.ApiType.CONSTRUCTOR else MissingApi.ApiType.METHOD
                                                    missingApis.add(MissingApi(type, owner, methodName, methodDesc))
                                                }
                                            }
                                            extractTypesFromDescriptor(methodDesc).forEach { if (isAndroidClass(it)) androidClasses.add(it) }
                                            super.visitMethodInsn(opcode, owner, methodName, methodDesc, isInterface)
                                        }

                                        override fun visitFieldInsn(opcode: Int, owner: String, fieldName: String, fieldDesc: String) {
                                            if (isAndroidClass(owner)) {
                                                androidClasses.add(owner)
                                                if (!checkFieldResolves(owner, fieldName)) {
                                                    missingApis.add(MissingApi(MissingApi.ApiType.FIELD, owner, fieldName, fieldDesc))
                                                }
                                            }
                                            super.visitFieldInsn(opcode, owner, fieldName, fieldDesc)
                                        }
                                    }
                                }
                            },
                            0,
                        )
                    }
                }
            }
        }

        // Scan XML layout tags from .cs3 archive
        val xmlTags = mutableSetOf<String>()
        val archiveToScan = cs3File ?: jarFile
        if (archiveToScan.exists()) {
            runCatching {
                ZipFile(archiveToScan).use { zip ->
                    for (entry in zip.entries()) {
                        if (entry.name.startsWith("res/layout/") && entry.name.endsWith(".xml")) {
                            val bytes = zip.getInputStream(entry).readBytes()
                            extractTagsFromAxml(bytes, xmlTags)
                        }
                    }
                }
            }
        }

        val dedupedMissing = missingApis.distinctBy { "${it.type}:${it.owner}:${it.name}:${it.descriptor}" }
        return PluginScanSummary(
            pluginName = pluginName,
            jarFile = jarFile,
            androidClasses = androidClasses,
            xmlTags = xmlTags,
            missingApis = dedupedMissing,
        )
    }

    private fun extractTagsFromAxml(data: ByteArray, outTags: MutableSet<String>) {
        var i = 0
        while (i < data.size - 4) {
            val b = data[i].toInt()
            if ((b in 'A'.code..'Z'.code) || (b in 'a'.code..'z'.code)) {
                val start = i
                while (i < data.size) {
                    val c = data[i].toInt()
                    if (c == '.'.code || (c in 'A'.code..'Z'.code) || (c in 'a'.code..'z'.code)) {
                        i++
                    } else {
                        break
                    }
                }
                val tagCandidate = String(data, start, i - start)
                if (tagCandidate.endsWith("View") || tagCandidate.endsWith("Layout") || tagCandidate.endsWith("Button") ||
                    tagCandidate.endsWith("Bar") || tagCandidate.endsWith("Group") || tagCandidate in setOf("Switch", "CheckBox", "RadioButton", "Space")
                ) {
                    outTags.add(tagCandidate)
                }
            } else {
                i++
            }
        }
    }

    fun generateEcosystemReport(summaries: List<PluginScanSummary>): String {
        val sb = StringBuilder()
        sb.append("# Ecosystem Android Compatibility Matrix\n\n")
        sb.append("- **Total Plugins Scanned:** ${summaries.size}\n")

        val pluginsWithAndroid = summaries.filter { it.androidClasses.isNotEmpty() }
        sb.append("- **Plugins using Android APIs:** ${pluginsWithAndroid.size}\n")

        val pluginsWithMissing = summaries.filter { it.missingApis.isNotEmpty() }
        sb.append("- **Plugins with Missing APIs:** ${pluginsWithMissing.size}\n\n")

        // Missing APIs grouped
        val globalMissing = mutableMapOf<String, MissingApi>()
        summaries.forEach { s ->
            s.missingApis.forEach { m ->
                val key = "${m.type}:${m.owner}:${m.name}:${m.descriptor}"
                val existing = globalMissing.getOrPut(key) { m.copy(usedByPlugins = mutableSetOf()) }
                existing.usedByPlugins.add(s.pluginName)
            }
        }

        if (globalMissing.isEmpty()) {
            sb.append("### Result: 100% Complete Parity\n")
            sb.append("All Android and AndroidX classes, constructors, methods, and fields called by all scanned plugins resolve successfully against `:android-stubs`!\n\n")
        } else {
            sb.append("### Missing APIs Breakdown (${globalMissing.size})\n\n")
            sb.append("| Type | Class / Method | Signature | Used by Plugins |\n")
            sb.append("| :--- | :--- | :--- | :--- |\n")
            globalMissing.values.sortedBy { it.owner }.forEach { m ->
                val label = if (m.name.isNotEmpty()) "${m.owner}.${m.name}" else m.owner
                sb.append("| ${m.type} | `$label` | `${m.descriptor}` | ${m.usedByPlugins.joinToString(", ")} |\n")
            }
            sb.append("\n")
        }

        // XML Layout widgets used
        val allXmlTags = summaries.flatMap { it.xmlTags }.toSet().sorted()
        if (allXmlTags.isNotEmpty()) {
            sb.append("### XML Layout Elements Found Across Corpus (${allXmlTags.size})\n\n")
            allXmlTags.forEach { tag ->
                sb.append("- `$tag`\n")
            }
            sb.append("\n")
        }

        // Summary table per plugin
        sb.append("### Per-Plugin Summary\n\n")
        sb.append("| Plugin | Android Classes | Missing APIs | Layout Tags |\n")
        sb.append("| :--- | :--- | :--- | :--- |\n")
        summaries.sortedBy { it.pluginName }.forEach { s ->
            val status = if (s.missingApis.isEmpty()) "0 (Clean)" else "${s.missingApis.size} missing"
            sb.append("| `${s.pluginName}` | ${s.androidClasses.size} | $status | ${s.xmlTags.size} |\n")
        }

        return sb.toString()
    }
}
