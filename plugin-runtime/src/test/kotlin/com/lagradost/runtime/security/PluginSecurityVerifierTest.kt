package com.lagradost.runtime.security

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Tests for PluginSecurityVerifier — the bytecode scanner that gates plugin loading.
 * These are the most critical tests in the project: if this scanner breaks, a malicious
 * plugin could call Runtime.exec(), read files, or exfiltrate data.
 *
 * Strategy: use ASM to generate minimal synthetic .class files inside temp JARs
 * rather than shipping real test fixtures on disk.
 */
class PluginSecurityVerifierTest {

    @TempDir
    lateinit var tempDir: File

    // --- Helpers ---

    /** Builds a minimal .class file (as bytes) that calls the given owner/method. */
    private fun buildClassWithCall(
        className: String,
        ownerClass: String,
        methodName: String,
        descriptor: String,
        isInterface: Boolean = false
    ): ByteArray {
        val cw = ClassWriter(0)
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "run", "()V", null, null)
        mv.visitCode()
        val opcode = if (isInterface) Opcodes.INVOKEINTERFACE else Opcodes.INVOKEVIRTUAL
        mv.visitMethodInsn(opcode, ownerClass, methodName, descriptor, isInterface)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(1, 0)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    /** Wraps one .class file into a temporary JAR and returns the File. */
    private fun jarWith(className: String, classBytes: ByteArray): File {
        val jar = File(tempDir, "$className.jar")
        ZipOutputStream(jar.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("$className.class"))
            zip.write(classBytes)
            zip.closeEntry()
        }
        return jar
    }

    // --- Tests ---

    @Test
    fun `clean class with no dangerous calls passes verification`() {
        // A totally empty class — no method calls at all — should pass cleanly
        val cw = ClassWriter(0)
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, "SafePlugin", null, "java/lang/Object", null)
        cw.visitEnd()
        val jar = jarWith("SafePlugin", cw.toByteArray())

        // Should not throw
        PluginSecurityVerifier.verifyJar(jar, "safe-plugin")
    }

    @Test
    fun `Runtime#exec is blocked`() {
        val classBytes = buildClassWithCall(
            "EvilPlugin", "java/lang/Runtime", "exec",
            "(Ljava/lang/String;)Ljava/lang/Process;"
        )
        val jar = jarWith("EvilPlugin", classBytes)
        assertFailsWith<SecurityException> {
            PluginSecurityVerifier.verifyJar(jar, "evil-plugin")
        }
    }

    @Test
    fun `System#exit is blocked`() {
        val classBytes = buildClassWithCall(
            "ExitPlugin", "java/lang/System", "exit", "(I)V",
            isInterface = false
        )
        val jar = jarWith("ExitPlugin", classBytes)
        assertFailsWith<SecurityException> {
            PluginSecurityVerifier.verifyJar(jar, "exit-plugin")
        }
    }

    @Test
    fun `System#getenv is blocked`() {
        val classBytes = buildClassWithCall(
            "EnvPlugin", "java/lang/System", "getenv",
            "(Ljava/lang/String;)Ljava/lang/String;"
        )
        val jar = jarWith("EnvPlugin", classBytes)
        assertFailsWith<SecurityException> {
            PluginSecurityVerifier.verifyJar(jar, "env-plugin")
        }
    }

    @Test
    fun `URL#openStream is blocked`() {
        val classBytes = buildClassWithCall(
            "UrlPlugin", "java/net/URL", "openStream",
            "()Ljava/io/InputStream;"
        )
        val jar = jarWith("UrlPlugin", classBytes)
        assertFailsWith<SecurityException> {
            PluginSecurityVerifier.verifyJar(jar, "url-plugin")
        }
    }

    @Test
    fun `TimeZone#getDefault is blocked for untrusted plugins`() {
        val classBytes = buildClassWithCall(
            "TzPlugin", "java/util/TimeZone", "getDefault",
            "()Ljava/util/TimeZone;"
        )
        val jar = jarWith("TzPlugin", classBytes)
        assertFailsWith<SecurityException>("TimeZone.getDefault should be blocked for untrusted plugins") {
            PluginSecurityVerifier.verifyJar(jar, "tz-plugin", isTrusted = false)
        }
    }

    @Test
    fun `TimeZone#getDefault is allowed for trusted plugins`() {
        val classBytes = buildClassWithCall(
            "TrustedPlugin", "java/util/TimeZone", "getDefault",
            "()Ljava/util/TimeZone;"
        )
        val jar = jarWith("TrustedPlugin", classBytes)
        // Should NOT throw for trusted plugins
        PluginSecurityVerifier.verifyJar(jar, "trusted-plugin", isTrusted = true)
    }
}
