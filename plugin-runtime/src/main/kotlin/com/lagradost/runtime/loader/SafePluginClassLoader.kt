package com.lagradost.runtime.loader

class SafePluginClassLoader(parent: ClassLoader) : ClassLoader(parent) {
    private val ghostCache = java.util.concurrent.ConcurrentHashMap<String, Class<*>>()

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        // Prevent loading dangerous packages directly from the plugin bytecode
        if (isBlocked(name)) {
            val pluginName = ExtensionLoader.getCallingPluginName() ?: "Unknown Plugin"
            if (!PermissionManager.hasPermission(pluginName, name)) {
                throw SecurityException("Security Sandbox: Access to class '$name' is blocked.")
            }
        }
        return try {
            super.loadClass(name, resolve)
        } catch (e: ClassNotFoundException) {
            // If the plugin requests an Android API or CloudStream API that we haven't stubbed, generate a ghost stub
            if (name.startsWith("android.") || name.startsWith("androidx.") || name.startsWith("com.android.") || name.startsWith("com.lagradost.") || name.startsWith("com.google.")) {
                generateGhostStub(name)
            } else {
                throw e
            }
        }
    }

    private fun generateGhostStub(name: String): Class<*> {
        ghostCache[name]?.let { return it }

        println("[GhostStub] Dynamically generated stub for missing Android API: $name")

        val internalName = name.replace('.', '/')
        val cw = org.objectweb.asm.ClassWriter(0)

        // Heuristic to detect if it's supposed to be an interface
        val isInterface = name.endsWith("Listener") || name.endsWith("Callback") || name.endsWith("Observer") || name.contains("\$On")

        val access = if (isInterface) {
            org.objectweb.asm.Opcodes.ACC_PUBLIC + org.objectweb.asm.Opcodes.ACC_ABSTRACT + org.objectweb.asm.Opcodes.ACC_INTERFACE
        } else {
            org.objectweb.asm.Opcodes.ACC_PUBLIC + org.objectweb.asm.Opcodes.ACC_SUPER
        }

        cw.visit(
            org.objectweb.asm.Opcodes.V1_8,
            access,
            internalName,
            null,
            "java/lang/Object",
            null,
        )

        if (!isInterface) {
            // default constructor
            val mv1 = cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
            mv1.visitCode()
            mv1.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0)
            mv1.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            mv1.visitInsn(org.objectweb.asm.Opcodes.RETURN)
            mv1.visitMaxs(1, 1)
            mv1.visitEnd()

            // constructor(Context)
            val mv2 = cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC, "<init>", "(Landroid/content/Context;)V", null, null)
            mv2.visitCode()
            mv2.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0)
            mv2.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            mv2.visitInsn(org.objectweb.asm.Opcodes.RETURN)
            mv2.visitMaxs(1, 2)
            mv2.visitEnd()

            // constructor(Context, AttributeSet)
            val mv3 = cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC, "<init>", "(Landroid/content/Context;Landroid/util/AttributeSet;)V", null, null)
            mv3.visitCode()
            mv3.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0)
            mv3.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            mv3.visitInsn(org.objectweb.asm.Opcodes.RETURN)
            mv3.visitMaxs(1, 3)
            mv3.visitEnd()

            // constructor(Context, AttributeSet, int)
            val mv4 = cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC, "<init>", "(Landroid/content/Context;Landroid/util/AttributeSet;I)V", null, null)
            mv4.visitCode()
            mv4.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0)
            mv4.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            mv4.visitInsn(org.objectweb.asm.Opcodes.RETURN)
            mv4.visitMaxs(1, 4)
            mv4.visitEnd()

            // constructor(int) - Used by ColorDrawable and similar resource-based constructors
            val mv5 = cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC, "<init>", "(I)V", null, null)
            mv5.visitCode()
            mv5.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0)
            mv5.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            mv5.visitInsn(org.objectweb.asm.Opcodes.RETURN)
            mv5.visitMaxs(1, 2)
            mv5.visitEnd()
        }

        cw.visitEnd()

        val bytecode = cw.toByteArray()
        val clazz = defineClass(name, bytecode, 0, bytecode.size)
        ghostCache[name] = clazz
        return clazz
    }

    private fun isBlocked(name: String): Boolean {
        // Block file system access, but allow benign streams/readers/writers
        if (name.startsWith("java.io.")) {
            val safeIo = setOf(
                "java.io.InputStream",
                "java.io.OutputStream",
                "java.io.ByteArrayInputStream",
                "java.io.ByteArrayOutputStream",
                "java.io.StringReader",
                "java.io.StringWriter",
                "java.io.InputStreamReader",
                "java.io.OutputStreamWriter",
                "java.io.BufferedReader",
                "java.io.BufferedWriter",
                "java.io.IOException",
                "java.io.EOFException",
                "java.io.FileNotFoundException",
                "java.io.InterruptedIOException",
                "java.io.UnsupportedEncodingException",
                "java.io.FilterInputStream",
                "java.io.FilterOutputStream",
                "java.io.BufferedInputStream",
                "java.io.BufferedOutputStream",
                "java.io.DataInputStream",
                "java.io.DataOutputStream",
                "java.io.Reader",
                "java.io.Writer",
                "java.io.Serializable",
                "java.io.Closeable",
                "java.io.PrintStream",
                "java.io.PrintWriter",
                "java.io.ObjectStreamException",
            )
            if (!safeIo.contains(name)) {
                return true
            }
        }

        // Block unsafe NIO (channels, files) but allow buffers and charsets
        if (name.startsWith("java.nio.")) {
            if (!name.startsWith("java.nio.charset.") && !name.contains("Buffer")) {
                return true
            }
        }

        // Block OS command execution and sandbox escapes
        if (name == "java.lang.ProcessBuilder" || name == "java.lang.Thread" || name == "java.lang.ClassLoader" || name == "java.net.URLClassLoader" || name == "java.security.SecureClassLoader") {
            return true
        }

        // Block ScriptEngine (JavaScript/Nashorn bypasses bytecode sandboxing)
        if (name.startsWith("javax.script.")) {
            return true
        }

        // Block JNDI (Remote class loading bypasses)
        if (name.startsWith("javax.naming.")) {
            return true
        }

        // Reflection is allowed here so JSON parsers (Gson/Jackson) work.
        // It is strictly sandboxed at the bytecode level in PluginBytecodeTransformer.kt

        // Block method handles but ALLOW LambdaMetafactory and StringConcatFactory required for Java 8+ lambdas
        if (name.startsWith("java.lang.invoke.")) {
            val safeInvoke = setOf(
                "java.lang.invoke.LambdaMetafactory",
                "java.lang.invoke.MethodType",
                "java.lang.invoke.CallSite",
                "java.lang.invoke.ConstantCallSite",
                "java.lang.invoke.MutableCallSite",
                "java.lang.invoke.VolatileCallSite",
                "java.lang.invoke.StringConcatFactory",
                "java.lang.invoke.TypeDescriptor",
                "java.lang.invoke.TypeDescriptor\$OfField",
                "java.lang.invoke.TypeDescriptor\$OfMethod",
            )
            if (!safeInvoke.contains(name)) {
                return true
            }
        }

        // Block compiler and unsafe memory access
        if (name.startsWith("sun.misc.") || name.startsWith("jdk.internal.") || name.startsWith("sun.reflect.")) {
            return true
        }

        // Block raw sockets
        if (name == "java.net.Socket" || name == "java.net.ServerSocket" || name == "java.net.DatagramSocket") {
            return true
        }

        return false
    }
}

object PermissionManager {
    private val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
    private val prefs = java.util.prefs.Preferences.userRoot().node("cloudstream_desktop_permissions")

    fun hasPermission(pluginName: String, className: String): Boolean {
        val grantedMap = getGrantedPermissions()
        val pluginPermissions = grantedMap[pluginName] ?: return false
        return pluginPermissions.contains(className) || pluginPermissions.contains("Network Sockets")
    }

    fun grantPermission(pluginName: String, permission: String) {
        val grantedMap = getGrantedPermissions()
        val pluginPermissions = grantedMap[pluginName] ?: mutableListOf()
        if (!pluginPermissions.contains(permission)) {
            pluginPermissions.add(permission)
            grantedMap[pluginName] = pluginPermissions
            saveGrantedPermissions(grantedMap)
            println("[Security] User granted permission '$permission' to plugin $pluginName")
        }
    }

    private fun getGrantedPermissions(): java.util.concurrent.ConcurrentHashMap<String, MutableList<String>> {
        val json = prefs.get("granted", "{}")
        return try {
            mapper.readValue(json, object : com.fasterxml.jackson.core.type.TypeReference<java.util.concurrent.ConcurrentHashMap<String, MutableList<String>>>() {})
        } catch (e: Exception) {
            java.util.concurrent.ConcurrentHashMap()
        }
    }

    private fun saveGrantedPermissions(map: java.util.concurrent.ConcurrentHashMap<String, MutableList<String>>) {
        try {
            prefs.put("granted", mapper.writeValueAsString(map))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
