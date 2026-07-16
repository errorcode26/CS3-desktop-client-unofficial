package com.lagradost.runtime.loader

class SafePluginClassLoader(parent: ClassLoader, private val isTrusted: Boolean = false) : ClassLoader(parent) {
    private val ghostCache = java.util.concurrent.ConcurrentHashMap<String, Class<*>>()

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        // Enforce Default Deny (Whitelist-Only) security policy
        val pluginName = ExtensionLoader.getCallingPluginName() ?: "Unknown Plugin"
        val hasSocketPerm = com.lagradost.runtime.permission.PluginPermissionAPI.hasPermission(pluginName, com.lagradost.runtime.permission.PluginPermission.NETWORK_SOCKETS) || com.lagradost.runtime.permission.PluginPermissionAPI.hasPermission(pluginName, name)
        if (!com.lagradost.runtime.security.SandboxSecurityPolicy.isClassAllowed(name, hasSocketPerm, isTrusted)) {
            if (!com.lagradost.runtime.permission.PluginPermissionAPI.hasPermission(pluginName, name)) {
                throw SecurityException("Security Sandbox: Access to class '$name' is blocked by Default Deny policy.")
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
}

@Deprecated("Use com.lagradost.runtime.permission.PluginPermissionAPI instead.")
object PermissionManager {
    fun hasPermission(pluginName: String, className: String): Boolean {
        return com.lagradost.runtime.permission.PluginPermissionAPI.hasPermission(pluginName, className)
    }

    fun grantPermission(pluginName: String, permission: String) {
        com.lagradost.runtime.permission.PluginPermissionAPI.grantPermission(pluginName, permission)
    }
}
