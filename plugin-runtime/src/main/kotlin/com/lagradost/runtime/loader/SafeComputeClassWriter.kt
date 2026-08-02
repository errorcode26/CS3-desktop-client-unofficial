package com.lagradost.runtime.loader

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter

/**
 * A resilient ClassWriter that prevents TypeNotPresentException / ClassNotFoundException
 * during ASM's COMPUTE_FRAMES recalculation for plugins and Android stub classes.
 */
open class SafeComputeClassWriter(
    reader: ClassReader? = null,
    flags: Int = COMPUTE_FRAMES or COMPUTE_MAXS,
    private val classLoader: ClassLoader = SafeComputeClassWriter::class.java.classLoader,
) : ClassWriter(reader, flags) {

    public override fun getCommonSuperClass(type1: String, type2: String): String {
        if (type1 == type2) return type1
        if (type1 == "java/lang/Object" || type2 == "java/lang/Object") return "java/lang/Object"

        val class1 = tryLoadClass(type1)
        val class2 = tryLoadClass(type2)

        if (class1 == null || class2 == null) {
            // If either class cannot be resolved via ClassLoader, fall back safely to java/lang/Object
            return "java/lang/Object"
        }

        return try {
            if (class1.isAssignableFrom(class2)) {
                return type1
            }
            if (class2.isAssignableFrom(class1)) {
                return type2
            }
            if (class1.isInterface || class2.isInterface) {
                return "java/lang/Object"
            }

            var curr: Class<*>? = class1
            while (curr != null) {
                curr = curr.superclass
                if (curr == null) break
                if (curr.isAssignableFrom(class2)) {
                    return curr.name.replace('.', '/')
                }
            }
            "java/lang/Object"
        } catch (_: Throwable) {
            "java/lang/Object"
        }
    }

    private fun tryLoadClass(internalName: String): Class<*>? {
        val fqcn = internalName.replace('/', '.')
        return try {
            Class.forName(fqcn, false, classLoader)
        } catch (_: Throwable) {
            try {
                Class.forName(fqcn, false, Thread.currentThread().contextClassLoader)
            } catch (_: Throwable) {
                null
            }
        }
    }
}
