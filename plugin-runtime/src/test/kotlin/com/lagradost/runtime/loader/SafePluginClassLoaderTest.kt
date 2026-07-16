package com.lagradost.runtime.loader

import org.junit.jupiter.api.Test
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SafePluginClassLoaderTest {

    @Test
    fun testAllowedClassesLoadedNormally() {
        val classLoader = SafePluginClassLoader(this::class.java.classLoader)
        
        // java.lang.String is perfectly safe
        val strClass = classLoader.loadClass("java.lang.String")
        assertEquals("java.lang.String", strClass.name)
    }

    @Test
    fun testDangerousClassesBlocked() {
        val classLoader = SafePluginClassLoader(this::class.java.classLoader)
        
        // These should throw SecurityException
        val blockedClasses = listOf(
            "java.lang.Thread",
            "java.lang.ProcessBuilder",
            "java.lang.Runtime",
            "java.lang.ClassLoader",
            "java.lang.invoke.MethodHandles\$Lookup",
            "java.lang.reflect.Method",
            "java.lang.reflect.Field",
            "java.net.Socket",
            "java.net.ServerSocket",
            "java.io.File",
            "java.io.FileInputStream",
            "sun.misc.Unsafe"
        )
        
        for (className in blockedClasses) {
            assertFailsWith<SecurityException>("Expected $className to be blocked") {
                classLoader.loadClass(className)
            }
        }
    }
}
