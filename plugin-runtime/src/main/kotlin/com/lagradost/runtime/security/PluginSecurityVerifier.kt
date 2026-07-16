package com.lagradost.runtime.security

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import java.io.File
import java.util.zip.ZipFile

object PluginSecurityVerifier {

    @Throws(SecurityException::class)
    fun verifyJar(jarFile: File, pluginInternalName: String, isTrusted: Boolean = false) {
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
                                    if (!com.lagradost.runtime.security.SandboxSecurityPolicy.isAsmOwnerAllowed(owner, isTrusted)) {
                                        throw SecurityException("Security Sandbox: Unsafe class '$owner' detected in ${classNode.name} method ${method.name} by Default Deny policy.")
                                    }

                                    // Fallback block for dangerous Runtime calls (in case the bytecode transformer missed them)
                                    if (owner == "java/lang/Runtime") {
                                        if (insn.name == "exec" || insn.name == "loadLibrary" || insn.name == "load" || insn.name == "exit" || insn.name == "halt") {
                                            throw SecurityException("Security Sandbox: Potentially unsafe code detected in class ${classNode.name} method ${method.name}. Illegal invocation: $owner.${insn.name}")
                                        }
                                    }

                                    // GAP FIX #1: Block URL.openStream() and URL.openConnection()
                                    // A plugin could do URL("file:///C:/Users/...").openStream() to read
                                    // arbitrary files from disk. Plugins never legitimately need raw URL
                                    // streams — they always use the `app` NiceHttp object instead.
                                    if (owner == "java/net/URL") {
                                        if (insn.name == "openStream" || insn.name == "openConnection") {
                                            throw SecurityException("Security Sandbox: Illegal URL.${ insn.name}() call in ${classNode.name}. Use the NiceHttp `app` object for network requests.")
                                        }
                                    }

                                    // GAP FIX #2: Block Class.forName(String, boolean, ClassLoader)
                                    // The 3-arg version lets a plugin supply ANY classloader, bypassing
                                    // SafePluginClassLoader entirely and loading blocked classes freely.
                                    // The 1-arg version is safe (uses the calling class's own loader which
                                    // goes through SafePluginClassLoader), so we leave it allowed.
                                    if (owner == "java/lang/Class" && insn.name == "forName") {
                                        // Distinguish by descriptor: 3-arg version has descriptor
                                        // (Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;
                                        if (insn.desc.contains("ClassLoader")) {
                                            throw SecurityException("Security Sandbox: Illegal Class.forName(String, boolean, ClassLoader) in ${classNode.name}. ClassLoader injection is not permitted.")
                                        }
                                    }

                                    // GAP FIX #3: Block raw HttpURLConnection / URLConnection
                                    // Prevents plugins from making untracked, unmetered HTTP requests
                                    if (owner == "java/net/HttpURLConnection" ||
                                        owner == "java/net/URLConnection" ||
                                        owner == "javax/net/ssl/HttpsURLConnection"
                                    ) {
                                        if (!com.lagradost.runtime.permission.PluginPermissionAPI.hasPermission(pluginInternalName, com.lagradost.runtime.permission.PluginPermission.NETWORK_SOCKETS)) {
                                            throw RequiresPermissionException(com.lagradost.runtime.permission.PluginPermission.NETWORK_SOCKETS.displayName, "Security Sandbox: Illegal raw HTTP connection in ${classNode.name}. Plugin requires '${com.lagradost.runtime.permission.PluginPermission.NETWORK_SOCKETS.displayName}' permission.")
                                        }
                                    }

                                    // Detect raw Sockets (e.g. for proxy servers)
                                    if (owner == "java/net/Socket" || owner == "java/net/ServerSocket" || owner == "java/net/DatagramSocket") {
                                        if (!com.lagradost.runtime.permission.PluginPermissionAPI.hasPermission(pluginInternalName, com.lagradost.runtime.permission.PluginPermission.NETWORK_SOCKETS)) {
                                            throw RequiresPermissionException(com.lagradost.runtime.permission.PluginPermission.NETWORK_SOCKETS.displayName, "Security Sandbox: Raw socket access detected in ${classNode.name}. Plugin requires '${com.lagradost.runtime.permission.PluginPermission.NETWORK_SOCKETS.displayName}' permission.")
                                        }
                                    }

                                    // Block specific dangerous System calls
                                    if (owner == "java/lang/System") {
                                        if (insn.name == "exit" || insn.name == "loadLibrary" || insn.name == "load" || insn.name == "setSecurityManager" || insn.name == "getProperty" || insn.name == "getProperties" || insn.name == "getenv") {
                                            throw SecurityException("Security Sandbox: Potentially unsafe code detected in class ${classNode.name}. Illegal System call: ${insn.name}")
                                        }
                                    }

                                    // Block TimeZone and Region (Locale) access
                                    if (!isTrusted) {
                                        if (owner == "java/util/TimeZone" && insn.name == "getDefault") {
                                            throw SecurityException("Security Sandbox: Access to TimeZone.getDefault() is blocked. Plugins do not need timezone data.")
                                        }
                                        if (owner == "java/time/ZoneId" && insn.name == "systemDefault") {
                                            throw SecurityException("Security Sandbox: Access to ZoneId.systemDefault() is blocked. Plugins do not need timezone data.")
                                        }
                                        if (owner == "java/util/Locale" && insn.name == "getDefault") {
                                            throw SecurityException("Security Sandbox: Access to Locale.getDefault() is blocked. Plugins do not need region data.")
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
