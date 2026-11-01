package com.lagradost.runtime.security

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Handle
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import java.io.File
import java.util.zip.ZipFile

object PluginSecurityVerifier {

    private val verifiedJarSignatures = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private val DISALLOWED_REFLECTION_TARGETS = setOf(
        "java.lang.Runtime",
        "java.lang.System",
        "java.lang.ProcessBuilder",
        "java.lang.Process",
        "java.lang.ClassLoader",
        "java.lang.Compiler",
        "java.lang.SecurityManager",
        "java.lang.invoke.MethodHandles",
        "java.lang.invoke.MethodHandle",
        "java.lang.invoke.VarHandle",
        "java.lang.reflect.Method",
        "java.lang.reflect.Field",
        "java.lang.reflect.Constructor",
        "sun.misc.Unsafe",
        "jdk.internal.misc.Unsafe",
    )

    @Throws(SecurityException::class)
    fun verifyJar(jarFile: File, pluginInternalName: String, isTrusted: Boolean = false) {
        if (isTrusted) {
            // User explicitly trusts this plugin/repository: bypass static verification
            return
        }

        val cacheKey = try {
            jarFile.canonicalPath
        } catch (_: Throwable) {
            jarFile.absolutePath
        }
        val lastMod = jarFile.lastModified()
        if (verifiedJarSignatures[cacheKey] == lastMod) {
            return
        }

        ZipFile(jarFile).use { zip ->
            val pluginJarClasses = mutableSetOf<String>()
            val initialEntries = zip.entries()
            while (initialEntries.hasMoreElements()) {
                val entry = initialEntries.nextElement()
                if (entry.name.endsWith(".class")) {
                    pluginJarClasses.add(entry.name.removeSuffix(".class"))
                }
            }

            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.endsWith(".class")) {
                    zip.getInputStream(entry).use { input ->
                        val reader = ClassReader(input)
                        val classNode = ClassNode()
                        reader.accept(classNode, 0)

                        for (method in classNode.methods) {
                            for (insn in method.instructions) {
                                // 1. Method Invocations
                                if (insn is MethodInsnNode) {
                                    val owner = insn.owner // internal name e.g. java/lang/Runtime

                                    // Enforce Default Deny (Whitelist-Only) policy on ASM owner
                                    if (!pluginJarClasses.contains(owner) && !com.lagradost.runtime.security.PluginSecurityPolicy.isAsmOwnerAllowed(owner, isTrusted)) {
                                        throw SecurityException("Disallowed class '${owner.replace('/', '.')}' referenced in ${classNode.name.replace('/', '.')}.${method.name}()")
                                    }

                                    // Fallback block for dangerous Runtime calls (in case the bytecode transformer missed them)
                                    if (owner == "java/lang/Runtime") {
                                        if (insn.name == "exec" || insn.name == "loadLibrary" || insn.name == "load" || insn.name == "exit" || insn.name == "halt") {
                                            throw SecurityException("Blocked Runtime.${insn.name}() invocation in ${classNode.name.replace('/', '.')}.${method.name}()")
                                        }
                                    }

                                    // Block URL.openStream() and URL.openConnection()
                                    if (owner == "java/net/URL") {
                                        if (insn.name == "openStream" || insn.name == "openConnection") {
                                            throw SecurityException("Illegal URL.${insn.name}() call in ${classNode.name.replace('/', '.')}. Use the NiceHttp 'app' object for network requests.")
                                        }
                                    }

                                    // Block Class.forName(String, boolean, ClassLoader)
                                    if (owner == "java/lang/Class" && insn.name == "forName") {
                                        if (insn.desc.contains("ClassLoader")) {
                                            throw SecurityException("Illegal ClassLoader injection via Class.forName(...) in ${classNode.name.replace('/', '.')}")
                                        }
                                    }

                                    // Block reflection by string targeting dangerous system internals
                                    if ((owner == "java/lang/Class" && (insn.name == "forName" || insn.name == "getMethod" || insn.name == "getDeclaredMethod" || insn.name == "getField" || insn.name == "getDeclaredField")) ||
                                        (owner == "java/lang/ClassLoader" && insn.name == "loadClass")
                                    ) {
                                        val prev = insn.previous
                                        if (prev is LdcInsnNode && prev.cst is String) {
                                            val targetName = (prev.cst as String).trim().replace('/', '.')
                                            if (DISALLOWED_REFLECTION_TARGETS.any { targetName.equals(it, ignoreCase = true) || targetName.startsWith("$it.") || targetName.startsWith("$it$") }) {
                                                throw SecurityException("Disallowed reflective invocation ${owner.replace('/', '.')}.${insn.name}(\"$targetName\") in ${classNode.name.replace('/', '.')}.${method.name}()")
                                            }
                                        }
                                    }

                                    // Block raw HttpURLConnection / URLConnection
                                    if (owner == "java/net/HttpURLConnection" ||
                                        owner == "java/net/URLConnection" ||
                                        owner == "javax/net/ssl/HttpsURLConnection"
                                    ) {
                                        if (!com.lagradost.runtime.permission.PluginPermissionAPI.hasPermission(pluginInternalName, com.lagradost.runtime.permission.PluginPermission.NETWORK_SOCKETS)) {
                                            throw RequiresPermissionException(com.lagradost.runtime.permission.PluginPermission.NETWORK_SOCKETS.displayName, "Raw HTTP connection in ${classNode.name.replace('/', '.')}. Requires '${com.lagradost.runtime.permission.PluginPermission.NETWORK_SOCKETS.displayName}' permission.")
                                        }
                                    }

                                    // Detect raw Sockets (e.g. for proxy servers)
                                    if (owner == "java/net/Socket" || owner == "java/net/ServerSocket" || owner == "java/net/DatagramSocket") {
                                        if (!com.lagradost.runtime.permission.PluginPermissionAPI.hasPermission(pluginInternalName, com.lagradost.runtime.permission.PluginPermission.NETWORK_SOCKETS)) {
                                            throw RequiresPermissionException(com.lagradost.runtime.permission.PluginPermission.NETWORK_SOCKETS.displayName, "Raw socket access in ${classNode.name.replace('/', '.')}. Requires '${com.lagradost.runtime.permission.PluginPermission.NETWORK_SOCKETS.displayName}' permission.")
                                        }
                                    }

                                    // Block specific dangerous System calls
                                    if (owner == "java/lang/System") {
                                        if (insn.name == "exit" || insn.name == "loadLibrary" || insn.name == "load" || insn.name == "setSecurityManager" || insn.name == "getProperty" || insn.name == "getProperties" || insn.name == "getenv") {
                                            throw SecurityException("Disallowed System.${insn.name}() call in ${classNode.name.replace('/', '.')}")
                                        }
                                    }
                                }

                                // 2. Field Instructions
                                if (insn is FieldInsnNode) {
                                    val owner = insn.owner
                                    if (!pluginJarClasses.contains(owner) && !com.lagradost.runtime.security.PluginSecurityPolicy.isAsmOwnerAllowed(owner, isTrusted)) {
                                        throw SecurityException("Disallowed class '${owner.replace('/', '.')}' referenced in ${classNode.name.replace('/', '.')}.${method.name}()")
                                    }
                                }

                                // 3. Dynamic Invocation (invokedynamic & bootstrap methods)
                                if (insn is InvokeDynamicInsnNode) {
                                    val bsmOwner = insn.bsm.owner
                                    if (!pluginJarClasses.contains(bsmOwner) && !com.lagradost.runtime.security.PluginSecurityPolicy.isAsmOwnerAllowed(bsmOwner, isTrusted)) {
                                        throw SecurityException("Disallowed invokedynamic bootstrap method owner '${bsmOwner.replace('/', '.')}' in ${classNode.name.replace('/', '.')}.${method.name}()")
                                    }

                                    for (arg in insn.bsmArgs) {
                                        if (arg is Handle) {
                                            val hOwner = arg.owner
                                            if (!pluginJarClasses.contains(hOwner) && !com.lagradost.runtime.security.PluginSecurityPolicy.isAsmOwnerAllowed(hOwner, isTrusted)) {
                                                throw SecurityException("Disallowed MethodHandle '${hOwner.replace('/', '.')}' in invokedynamic arguments in ${classNode.name.replace('/', '.')}.${method.name}()")
                                            }
                                        } else if (arg is Type) {
                                            if (arg.sort == Type.OBJECT) {
                                                val tOwner = arg.internalName
                                                if (!pluginJarClasses.contains(tOwner) && !com.lagradost.runtime.security.PluginSecurityPolicy.isAsmOwnerAllowed(tOwner, isTrusted)) {
                                                    throw SecurityException("Disallowed Type '${tOwner.replace('/', '.')}' in invokedynamic arguments in ${classNode.name.replace('/', '.')}.${method.name}()")
                                                }
                                            }
                                        }
                                    }
                                }

                                // 4. Constant Pool Loads (LDC MethodHandle, Type, or String reflection targets)
                                if (insn is LdcInsnNode) {
                                    val cst = insn.cst
                                    if (cst is Handle) {
                                        val hOwner = cst.owner
                                        if (!pluginJarClasses.contains(hOwner) && !com.lagradost.runtime.security.PluginSecurityPolicy.isAsmOwnerAllowed(hOwner, isTrusted)) {
                                            throw SecurityException("Disallowed MethodHandle '${hOwner.replace('/', '.')}' loaded via LDC in ${classNode.name.replace('/', '.')}.${method.name}()")
                                        }
                                    } else if (cst is Type) {
                                        if (cst.sort == Type.OBJECT) {
                                            val tOwner = cst.internalName
                                            if (!pluginJarClasses.contains(tOwner) && !com.lagradost.runtime.security.PluginSecurityPolicy.isAsmOwnerAllowed(tOwner, isTrusted)) {
                                                throw SecurityException("Disallowed Type '${tOwner.replace('/', '.')}' loaded via LDC in ${classNode.name.replace('/', '.')}.${method.name}()")
                                            }
                                        }
                                    } else if (cst is String) {
                                        val targetStr = cst.trim().replace('/', '.')
                                        if (DISALLOWED_REFLECTION_TARGETS.any { targetStr.equals(it, ignoreCase = true) || targetStr.startsWith("$it.") || targetStr.startsWith("$it$") }) {
                                            throw SecurityException("Disallowed reflective class reference '$targetStr' in ${classNode.name.replace('/', '.')}.${method.name}()")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        verifiedJarSignatures[cacheKey] = lastMod
    }
}

class RequiresPermissionException(val permissionName: String, message: String) : SecurityException(message)
