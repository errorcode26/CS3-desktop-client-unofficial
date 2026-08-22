package com.lagradost.runtime.security

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import java.io.File
import java.util.zip.ZipFile

object PluginSecurityVerifier {

    @Throws(SecurityException::class)
    fun verifyJar(jarFile: File, pluginInternalName: String, isTrusted: Boolean = false) {
        if (isTrusted) {
            // User explicitly trusts this plugin/repository: bypass static verification
            return
        }

        ZipFile(jarFile).use { zip ->
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
                                if (insn is MethodInsnNode) {
                                    val owner = insn.owner // internal name e.g. java/lang/Runtime

                                    // Enforce Default Deny (Whitelist-Only) policy on ASM owner
                                    if (!com.lagradost.runtime.security.PluginSecurityPolicy.isAsmOwnerAllowed(owner, isTrusted)) {
                                        throw SecurityException("Disallowed class '${owner.replace('/', '.')}' referenced in ${classNode.name.replace('/', '.')}.${method.name}()")
                                    }

                                    // Fallback block for dangerous Runtime calls (in case the bytecode transformer missed them)
                                    if (owner == "java/lang/Runtime") {
                                        if (insn.name == "exec" || insn.name == "loadLibrary" || insn.name == "load" || insn.name == "exit" || insn.name == "halt") {
                                            throw SecurityException("Blocked Runtime.${insn.name}() invocation in ${classNode.name.replace('/', '.')}.${method.name}()")
                                        }
                                    }

                                    // GAP FIX #1: Block URL.openStream() and URL.openConnection()
                                    // A plugin could do URL("file:///C:/Users/...").openStream() to read
                                    // arbitrary files from disk. Plugins never legitimately need raw URL
                                    // streams — they always use the `app` NiceHttp object instead.
                                    if (owner == "java/net/URL") {
                                        if (insn.name == "openStream" || insn.name == "openConnection") {
                                            throw SecurityException("Illegal URL.${insn.name}() call in ${classNode.name.replace('/', '.')}. Use the NiceHttp 'app' object for network requests.")
                                        }
                                    }

                                    // GAP FIX #2: Block Class.forName(String, boolean, ClassLoader)
                                    if (owner == "java/lang/Class" && insn.name == "forName") {
                                        if (insn.desc.contains("ClassLoader")) {
                                            throw SecurityException("Illegal ClassLoader injection via Class.forName(...) in ${classNode.name.replace('/', '.')}")
                                        }
                                    }

                                    // GAP FIX #3: Block raw HttpURLConnection / URLConnection
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

                                    // Block TimeZone and Region (Locale) access
                                    if (!isTrusted) {
                                        if (owner == "java/util/TimeZone" && insn.name == "getDefault") {
                                            throw SecurityException("Blocked TimeZone.getDefault() access in ${classNode.name.replace('/', '.')}")
                                        }
                                        if (owner == "java/time/ZoneId" && insn.name == "systemDefault") {
                                            throw SecurityException("Blocked ZoneId.systemDefault() access in ${classNode.name.replace('/', '.')}")
                                        }
                                        if (owner == "java/util/Locale" && insn.name == "getDefault") {
                                            throw SecurityException("Blocked Locale.getDefault() access in ${classNode.name.replace('/', '.')}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class RequiresPermissionException(val permissionName: String, message: String) : SecurityException(message)
