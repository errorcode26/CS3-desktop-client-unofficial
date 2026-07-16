import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.zip.ZipFile

object StubRadar {
    fun scanForMissingStubs(jarFile: File): String {
        println("Scanning ${jarFile.name} for Android dependencies using ASM...")

        val androidRefs = mutableSetOf<String>()

        ZipFile(jarFile).use { zip ->
            for (entry in zip.entries()) {
                if (entry.name.endsWith(".class")) {
                    zip.getInputStream(entry).use { input ->
                        val reader = ClassReader(input)
                        reader.accept(
                            object : ClassVisitor(Opcodes.ASM9) {
                                override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
                                    if (superName != null && isAndroidClass(superName)) androidRefs.add(superName)
                                    interfaces?.forEach { if (isAndroidClass(it)) androidRefs.add(it) }
                                    super.visit(version, access, name, signature, superName, interfaces)
                                }

                                override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                                    // Extract from descriptor
                                    extractTypesFromDescriptor(descriptor).forEach { if (isAndroidClass(it)) androidRefs.add(it) }

                                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                                    return object : MethodVisitor(Opcodes.ASM9, mv) {
                                        override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                                            if (isAndroidClass(owner)) androidRefs.add(owner)
                                            extractTypesFromDescriptor(descriptor).forEach { if (isAndroidClass(it)) androidRefs.add(it) }
                                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                                        }

                                        override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
                                            if (isAndroidClass(owner)) androidRefs.add(owner)
                                            extractTypesFromDescriptor(descriptor).forEach { if (isAndroidClass(it)) androidRefs.add(it) }
                                            super.visitFieldInsn(opcode, owner, name, descriptor)
                                        }

                                        override fun visitTypeInsn(opcode: Int, type: String) {
                                            if (isAndroidClass(type)) androidRefs.add(type)
                                            super.visitTypeInsn(opcode, type)
                                        }
                                    }
                                }
                            },
                            0,
                        )
                    }
                }
            }
        }

        println("Found ${androidRefs.size} unique Android/AndroidX references.")

        // Dynamically resolve paths by finding the workspace root
        var rootDir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (rootDir != null && !File(rootDir, "desktop-app").exists()) {
            rootDir = rootDir.parentFile
        }

        if (rootDir == null) {
            println("Error: Could not dynamically locate the 'desktop-app' module.")
            return "Error locating desktop-app module."
        }

        val stubsDir = File(rootDir, "android-stubs/src/main/java")
        val stubsKotlinDir = File(rootDir, "android-stubs/src/main/kotlin")
        val coreDir = File(rootDir, "common/src/main/kotlin") // common has some platform logic

        val missingStubs = mutableListOf<String>()
        val stubbedApis = mutableListOf<String>()
        val coreApis = mutableListOf<String>()

        androidRefs.forEach { ref ->
            // ref is like "android/content/Context"
            if (File(coreDir, "$ref.java").exists() || File(coreDir, "$ref.kt").exists()) {
                coreApis.add(ref)
            } else if (File(stubsDir, "$ref.java").exists() || File(stubsDir, "$ref.kt").exists() ||
                File(stubsKotlinDir, "$ref.kt").exists()
            ) {
                stubbedApis.add(ref)
            } else {
                missingStubs.add(ref)
            }
        }

        val sb = StringBuilder()
        sb.append("\n--- \uD83D\uDCCA PLUGIN COMPATIBILITY REPORT \uD83D\uDCCA ---\n")

        sb.append("\n✅ IMPLEMENTED CORE APIs (${coreApis.size}) - Safe data logic:\n")
        coreApis.sorted().forEach { sb.append("   - $it\n") }

        sb.append("\n⚠️ STUBBED UI APIs (${stubbedApis.size}) - Faked out to prevent crashes:\n")
        stubbedApis.sorted().forEach { sb.append("   - $it\n") }

        if (missingStubs.isEmpty()) {
            sb.append("\n🎉 NO MISSING STUBS! This plugin should run flawlessly.\n")
        } else {
            sb.append("\n❌ MISSING APIs DETECTED (${missingStubs.size}):\n")
            missingStubs.sorted().forEach { sb.append("   - $it\n") }
            sb.append("\n🚨 Please implement these in desktop-app/src/main/android_core or stub them in android_stubs.\n")
        }

        return sb.toString()
    }

    private fun isAndroidClass(name: String): Boolean {
        return name.startsWith("android/") || name.startsWith("androidx/")
    }

    private fun extractTypesFromDescriptor(descriptor: String): List<String> {
        val types = mutableListOf<String>()
        var i = 0
        while (i < descriptor.length) {
            if (descriptor[i] == 'L') {
                val end = descriptor.indexOf(';', i)
                if (end != -1) {
                    types.add(descriptor.substring(i + 1, end))
                    i = end
                }
            }
            i++
        }
        return types
    }
}
