package com.lagradost.runtime.security

import com.lagradost.runtime.loader.stubs.ReflectionStub
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReflectionWhitelistTest {

    @Test
    fun `allowed core types pass reflection check`() {
        assertTrue(ReflectionStub.isReflectionAllowed(String::class.java))
        assertTrue(ReflectionStub.isReflectionAllowed(java.util.ArrayList::class.java))
        assertTrue(ReflectionStub.isReflectionAllowed(java.util.HashMap::class.java))
        assertTrue(ReflectionStub.isReflectionAllowed(Int::class.java))
        assertTrue(ReflectionStub.isReflectionAllowed(java.lang.Integer::class.java))
        assertTrue(ReflectionStub.isReflectionAllowed(Array<String>::class.java))
    }

    @Test
    fun `dangerous system and process classes are blocked`() {
        assertFalse(ReflectionStub.isReflectionAllowed(java.lang.System::class.java))
        assertFalse(ReflectionStub.isReflectionAllowed(java.lang.Runtime::class.java))
        assertFalse(ReflectionStub.isReflectionAllowed(java.lang.ProcessBuilder::class.java))
        assertFalse(ReflectionStub.isReflectionAllowed(java.lang.Process::class.java))
        assertFalse(ReflectionStub.isReflectionAllowed(java.lang.ClassLoader::class.java))
        assertFalse(ReflectionStub.isReflectionAllowed(java.lang.Thread::class.java))
    }

    @Test
    fun `filesystem classes are blocked from reflection`() {
        assertFalse(ReflectionStub.isReflectionAllowed(File::class.java))
        assertFalse(ReflectionStub.isReflectionAllowed(java.io.FileInputStream::class.java))
        assertFalse(ReflectionStub.isReflectionAllowed(java.nio.file.Path::class.java))
        assertFalse(ReflectionStub.isReflectionAllowed(java.nio.file.Paths::class.java))
    }

    @Test
    fun `desktop and common host classes are blocked from reflection`() {
        assertFalse(ReflectionStub.isReflectionAllowed(com.lagradost.common.logging.AppLogger::class.java))
    }

    @Test
    fun `invoking blocked method throws SecurityException`() {
        val exitMethod = java.lang.System::class.java.getMethod("exit", Int::class.javaPrimitiveType)
        val ex = assertFailsWith<SecurityException> {
            ReflectionStub.invoke(exitMethod, null, arrayOf(0))
        }
        assertTrue(ex.message!!.contains("Default Deny policy"))
    }

    @Test
    fun `invoking allowed method succeeds`() {
        val substringMethod = String::class.java.getMethod("substring", Int::class.javaPrimitiveType)
        val result = ReflectionStub.invoke(substringMethod, "Hello World", arrayOf(6))
        assertEquals("World", result)
    }
}
