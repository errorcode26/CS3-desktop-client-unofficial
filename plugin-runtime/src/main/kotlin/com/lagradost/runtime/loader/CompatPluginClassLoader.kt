package com.lagradost.runtime.loader

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.net.URL
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * A URLClassLoader that applies backwards-compatibility patches to plugin bytecode
 * at class-load time, so it works regardless of whether the jar is cached or freshly converted.
 *
 * The key problem: between kotlinx-coroutines 1.8.x and 1.10.x, Kotlin changed the
 * internal mangled names of many top-level suspend functions (e.g. `delay_VtjQ1oo` →
 * `delay`, `withTimeoutOrNull_KLykuaI` → different name). Plugins compiled against
 * the old version call method names that no longer exist in the host runtime.
 *
 * The fix: a generic runtime resolver. For any call into `kotlinx.coroutines.*` where
 * the method name looks mangled (baseName_SUFFIX) and the method doesn't exist in
 * the host runtime, we find the correct current name via reflection and rewrite the
 * bytecode on the fly. This handles ALL past and future mangling changes with zero
 * hardcoding of specific method names.
 */
class CompatPluginClassLoader(urls: Array<URL>, parent: ClassLoader) : URLClassLoader(urls, parent) {

    override fun findClass(name: String): Class<*> {
        val path = name.replace('.', '/') + ".class"
        val url = findResource(path) ?: throw ClassNotFoundException(name)
        val bytes = url.openStream().use { it.readBytes() }
        val patched = applyCompatPatches(bytes)
        return defineClass(name, patched, 0, patched.size)
    }

    private fun applyCompatPatches(bytes: ByteArray): ByteArray {
        return try {
            val reader = ClassReader(bytes)
            val writer = ClassWriter(0)
            reader.accept(CompatPatchVisitor(writer, parent), 0)
            writer.toByteArray()
        } catch (e: Exception) {
            bytes
        }
    }

    private class CompatPatchVisitor(cv: ClassVisitor, private val runtimeLoader: ClassLoader) :
        ClassVisitor(Opcodes.ASM9, cv) {
        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?,
        ): MethodVisitor {
            val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
            return CompatMethodVisitor(mv, runtimeLoader)
        }
    }

    private class CompatMethodVisitor(mv: MethodVisitor, private val runtimeLoader: ClassLoader) :
        MethodVisitor(Opcodes.ASM9, mv) {

        override fun visitMethodInsn(
            opcode: Int,
            owner: String,
            name: String,
            descriptor: String,
            isInterface: Boolean,
        ) {
            // Only intercept calls into kotlinx.coroutines.* that look like they have
            // a mangled suffix (e.g. delay_VtjQ1oo, withTimeoutOrNull_KLykuaI).
            // If the method doesn't exist in the current runtime, we find the right
            // current name via reflection and rewrite the call automatically.
            if (owner.startsWith("kotlinx/coroutines/") && looksMangled(name)) {
                val resolvedName = CoroutinesMethodResolver.resolve(owner, name, runtimeLoader)
                if (resolvedName != name) {
                    super.visitMethodInsn(opcode, owner, resolvedName, descriptor, isInterface)
                    return
                }
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
        }

        /** Returns true if the name looks like a Kotlin-mangled function (baseName_XXXXXXX). */
        private fun looksMangled(name: String): Boolean {
            val idx = name.lastIndexOf('_')
            if (idx < 1) return false
            val suffix = name.substring(idx + 1)
            // Kotlin mangled suffixes are 7+ alphanumeric characters
            return suffix.length >= 6 && suffix.all { it.isLetterOrDigit() }
        }
    }

    /**
     * Resolves old mangled coroutines method names to their current equivalents in
     * the host runtime. Results are cached so reflection only fires once per name.
     */
    private object CoroutinesMethodResolver {
        private val cache = ConcurrentHashMap<String, String>()

        fun resolve(owner: String, mangledName: String, loader: ClassLoader): String {
            val key = "$owner.$mangledName"
            return cache.getOrPut(key) { findCurrentName(owner, mangledName, loader) }
        }

        private fun findCurrentName(owner: String, mangledName: String, loader: ClassLoader): String {
            return try {
                val className = owner.replace('/', '.')
                val clazz = Class.forName(className, false, loader)
                val methods = clazz.declaredMethods

                // Method still exists with exact name → nothing to rewrite
                if (methods.any { it.name == mangledName }) return mangledName

                // Strip the mangled suffix to get the base function name
                val baseName = mangledName.substringBeforeLast('_')

                // Find any method in the runtime class that matches the base name
                // (either exact match or with a different mangling suffix)
                val candidate = methods.firstOrNull { m ->
                    m.name == baseName || m.name.startsWith("${baseName}_")
                }

                if (candidate != null) {
                    println("[CoroutinesCompat] Remapped $owner.$mangledName → ${candidate.name}")
                    candidate.name
                } else {
                    mangledName // Let it fail naturally if we can't find anything
                }
            } catch (e: Exception) {
                mangledName
            }
        }
    }
}
