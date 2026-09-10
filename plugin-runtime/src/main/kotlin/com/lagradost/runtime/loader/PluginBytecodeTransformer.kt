package com.lagradost.runtime.loader

import com.lagradost.common.logging.AppLogger
import org.objectweb.asm.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object PluginBytecodeTransformer {

    fun transform(jarFile: File, classLoader: ClassLoader? = null) {
        val tempFile = File(jarFile.absolutePath + ".tmp")
        val effectiveLoader = classLoader ?: PluginBytecodeTransformer::class.java.classLoader

        ZipInputStream(FileInputStream(jarFile)).use { zis ->
            ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val newEntry = ZipEntry(entry.name)
                    zos.putNextEntry(newEntry)

                    val bytes = zis.readBytes()
                    if (entry.name.endsWith(".class")) {
                        try {
                            val reader = ClassReader(bytes)
                            val writer = SafeComputeClassWriter(
                                reader = null,
                                flags = ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS,
                                classLoader = effectiveLoader,
                            )

                            val visitor = object : ClassVisitor(Opcodes.ASM9, writer) {
                                private var currentSuperName: String? = null

                                override fun visit(
                                    version: Int,
                                    access: Int,
                                    name: String?,
                                    signature: String?,
                                    superName: String?,
                                    interfaces: Array<out String>?,
                                ) {
                                    currentSuperName = superName
                                    super.visit(version, access, name, signature, superName, interfaces)
                                }

                                override fun visitMethod(
                                    access: Int,
                                    name: String,
                                    descriptor: String?,
                                    signature: String?,
                                    exceptions: Array<out String>?,
                                ): MethodVisitor {
                                    val mv = super.visitMethod(access, fixMethodName(name), descriptor, signature, exceptions)
                                    return object : MethodVisitor(Opcodes.ASM9, mv) {
                                        private fun isUIClass(owner: String): Boolean {
                                            return owner.startsWith("android/widget/") ||
                                                owner.startsWith("android/view/") ||
                                                owner.startsWith("android/graphics/") ||
                                                owner.startsWith("android/app/") ||
                                                owner.startsWith("android/text/") ||
                                                owner.startsWith("androidx/")
                                        }

                                        private fun pushDefault(type: Type) {
                                            when (type.sort) {
                                                Type.VOID -> {}
                                                Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> super.visitInsn(Opcodes.ICONST_0)
                                                Type.FLOAT -> super.visitInsn(Opcodes.FCONST_0)
                                                Type.LONG -> super.visitInsn(Opcodes.LCONST_0)
                                                Type.DOUBLE -> super.visitInsn(Opcodes.DCONST_0)
                                                Type.ARRAY, Type.OBJECT -> super.visitInsn(Opcodes.ACONST_NULL)
                                            }
                                        }

                                        private fun popType(type: Type) {
                                            if (type.size == 2) {
                                                super.visitInsn(Opcodes.POP2)
                                            } else {
                                                super.visitInsn(Opcodes.POP)
                                            }
                                        }

                                        override fun visitMethodInsn(
                                            opcode: Int,
                                            owner: String,
                                            methodName: String,
                                            descriptor: String,
                                            isInterface: Boolean,
                                        ) {
                                            if (methodName != "<init>" && isUIClass(owner) && methodName != "getSharedPreferences") {
                                                val argTypes = Type.getArgumentTypes(descriptor)
                                                val retType = Type.getReturnType(descriptor)

                                                for (i in argTypes.indices.reversed()) {
                                                    popType(argTypes[i])
                                                }

                                                if (opcode != Opcodes.INVOKESTATIC) {
                                                    super.visitInsn(Opcodes.POP)
                                                }

                                                pushDefault(retType)
                                                return
                                            }

                                            var newOpcode = opcode
                                            var newOwner = owner
                                            var newDesc = descriptor

                                            if (owner == "java/lang/Runtime" && (methodName == "exec" || methodName == "loadLibrary" || methodName == "load" || methodName == "exit" || methodName == "halt")) {
                                                newOpcode = Opcodes.INVOKESTATIC
                                                newOwner = "com/lagradost/runtime/loader/stubs/RuntimeStub"
                                                newDesc = descriptor.replace("(", "(Ljava/lang/Runtime;")
                                            } else if (owner == "java/lang/Runtime" && methodName == "availableProcessors" && descriptor == "()I") {
                                                newOpcode = Opcodes.INVOKESTATIC
                                                newOwner = "com/lagradost/runtime/loader/stubs/RuntimeStub"
                                                newDesc = "(Ljava/lang/Runtime;)I"
                                            } else if (owner == "java/lang/Runtime" && (methodName == "maxMemory" || methodName == "totalMemory" || methodName == "freeMemory") && descriptor == "()J") {
                                                newOpcode = Opcodes.INVOKESTATIC
                                                newOwner = "com/lagradost/runtime/loader/stubs/RuntimeStub"
                                                newDesc = "(Ljava/lang/Runtime;)J"
                                            } else if (owner == "java/lang/System" && (methodName == "exit" || methodName == "loadLibrary" || methodName == "load" || methodName == "setSecurityManager" || methodName == "getProperty" || methodName == "getProperties" || methodName == "getenv")) {
                                                newOwner = "com/lagradost/runtime/loader/stubs/SystemStub"
                                            } else if (owner == "java/lang/reflect/Method" && methodName == "invoke" && descriptor == "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;") {
                                                newOpcode = Opcodes.INVOKESTATIC
                                                newOwner = "com/lagradost/runtime/loader/stubs/ReflectionStub"
                                                newDesc = "(Ljava/lang/reflect/Method;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;"
                                            } else if (owner == "java/lang/reflect/Field" && methodName == "get" && descriptor == "(Ljava/lang/Object;)Ljava/lang/Object;") {
                                                newOpcode = Opcodes.INVOKESTATIC
                                                newOwner = "com/lagradost/runtime/loader/stubs/ReflectionStub"
                                                newDesc = "(Ljava/lang/reflect/Field;Ljava/lang/Object;)Ljava/lang/Object;"
                                            } else if (owner == "java/lang/reflect/Field" && methodName == "set" && descriptor == "(Ljava/lang/Object;Ljava/lang/Object;)V") {
                                                newOpcode = Opcodes.INVOKESTATIC
                                                newOwner = "com/lagradost/runtime/loader/stubs/ReflectionStub"
                                                newDesc = "(Ljava/lang/reflect/Field;Ljava/lang/Object;Ljava/lang/Object;)V"
                                            } else if (owner == "java/lang/reflect/Constructor" && methodName == "newInstance" && descriptor == "([Ljava/lang/Object;)Ljava/lang/Object;") {
                                                newOpcode = Opcodes.INVOKESTATIC
                                                newOwner = "com/lagradost/runtime/loader/stubs/ReflectionStub"
                                                newDesc = "(Ljava/lang/reflect/Constructor;[Ljava/lang/Object;)Ljava/lang/Object;"
                                            } else if ((owner == "java/lang/reflect/AccessibleObject" || owner == "java/lang/reflect/Method" || owner == "java/lang/reflect/Field" || owner == "java/lang/reflect/Constructor") && methodName == "setAccessible" && descriptor == "(Z)V") {
                                                newOpcode = Opcodes.INVOKESTATIC
                                                newOwner = "com/lagradost/runtime/loader/stubs/ReflectionStub"
                                                newDesc = "(Ljava/lang/reflect/AccessibleObject;Z)V"
                                            } else if (owner == "java/net/URL" && methodName == "openConnection") {
                                                newOpcode = Opcodes.INVOKESTATIC
                                                newOwner = "com/lagradost/runtime/loader/stubs/URLStub"
                                                newDesc = descriptor.replace("(", "(Ljava/net/URL;")
                                            } else if (owner == "java/net/URL" && methodName == "openStream" && descriptor == "()Ljava/io/InputStream;") {
                                                newOpcode = Opcodes.INVOKESTATIC
                                                newOwner = "com/lagradost/runtime/loader/stubs/URLStub"
                                                newDesc = "(Ljava/net/URL;)Ljava/io/InputStream;"
                                            } else if (owner == "java/net/URL" && methodName == "getContent") {
                                                newOpcode = Opcodes.INVOKESTATIC
                                                newOwner = "com/lagradost/runtime/loader/stubs/URLStub"
                                                newDesc = descriptor.replace("(", "(Ljava/net/URL;")
                                            } else if (owner == "com/lagradost/nicehttp/Requests" && methodName == "<init>" && currentSuperName != "com/lagradost/nicehttp/Requests") {
                                                super.visitMethodInsn(opcode, owner, fixMethodName(methodName), descriptor, isInterface)
                                                super.visitInsn(Opcodes.DUP)
                                                super.visitMethodInsn(
                                                    Opcodes.INVOKESTATIC,
                                                    "com/lagradost/runtime/loader/stubs/RequestsStub",
                                                    "attachGlobalBaseClient",
                                                    "(Lcom/lagradost/nicehttp/Requests;)V",
                                                    false,
                                                )
                                                return
                                            }

                                            // Intercept File constructors to enforce folder jail via PluginFileSecurityStub
                                            if (opcode == Opcodes.INVOKESPECIAL && owner == "java/io/File" && methodName == "<init>") {
                                                when (descriptor) {
                                                    "(Ljava/lang/String;)V" -> {
                                                        super.visitMethodInsn(
                                                            Opcodes.INVOKESTATIC,
                                                            "com/lagradost/runtime/loader/stubs/PluginFileSecurityStub",
                                                            "checkPath",
                                                            "(Ljava/lang/String;)Ljava/lang/String;",
                                                            false,
                                                        )
                                                        super.visitMethodInsn(opcode, owner, fixMethodName(methodName), descriptor, isInterface)
                                                        return
                                                    }
                                                    "(Ljava/lang/String;Ljava/lang/String;)V" -> {
                                                        super.visitMethodInsn(
                                                            Opcodes.INVOKESTATIC,
                                                            "com/lagradost/runtime/loader/stubs/PluginFileSecurityStub",
                                                            "checkParentChild",
                                                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                                                            false,
                                                        )
                                                        super.visitMethodInsn(opcode, owner, fixMethodName(methodName), "(Ljava/lang/String;)V", isInterface)
                                                        return
                                                    }
                                                    "(Ljava/io/File;Ljava/lang/String;)V" -> {
                                                        super.visitMethodInsn(
                                                            Opcodes.INVOKESTATIC,
                                                            "com/lagradost/runtime/loader/stubs/PluginFileSecurityStub",
                                                            "checkFileChildPath",
                                                            "(Ljava/io/File;Ljava/lang/String;)Ljava/lang/String;",
                                                            false,
                                                        )
                                                        super.visitMethodInsn(opcode, owner, fixMethodName(methodName), "(Ljava/lang/String;)V", isInterface)
                                                        return
                                                    }
                                                    "(Ljava/net/URI;)V" -> {
                                                        super.visitMethodInsn(
                                                            Opcodes.INVOKESTATIC,
                                                            "com/lagradost/runtime/loader/stubs/PluginFileSecurityStub",
                                                            "checkUri",
                                                            "(Ljava/net/URI;)Ljava/net/URI;",
                                                            false,
                                                        )
                                                        super.visitMethodInsn(opcode, owner, fixMethodName(methodName), descriptor, isInterface)
                                                        return
                                                    }
                                                }
                                            }

                                            // Intercept Stream, RandomAccessFile, and Reader/Writer constructors to validate file paths
                                            if (opcode == Opcodes.INVOKESPECIAL &&
                                                (owner == "java/io/FileInputStream" || owner == "java/io/FileOutputStream" ||
                                                 owner == "java/io/FileReader" || owner == "java/io/FileWriter" ||
                                                 owner == "java/io/RandomAccessFile") &&
                                                methodName == "<init>"
                                            ) {
                                                when (descriptor) {
                                                    "(Ljava/lang/String;)V" -> {
                                                        super.visitMethodInsn(
                                                            Opcodes.INVOKESTATIC,
                                                            "com/lagradost/runtime/loader/stubs/PluginFileSecurityStub",
                                                            "checkPath",
                                                            "(Ljava/lang/String;)Ljava/lang/String;",
                                                            false,
                                                        )
                                                        super.visitMethodInsn(opcode, owner, fixMethodName(methodName), descriptor, isInterface)
                                                        return
                                                    }
                                                    "(Ljava/io/File;)V" -> {
                                                        super.visitMethodInsn(
                                                            Opcodes.INVOKESTATIC,
                                                            "com/lagradost/runtime/loader/stubs/PluginFileSecurityStub",
                                                            "checkFile",
                                                            "(Ljava/io/File;)Ljava/io/File;",
                                                            false,
                                                        )
                                                        super.visitMethodInsn(opcode, owner, fixMethodName(methodName), descriptor, isInterface)
                                                        return
                                                    }
                                                    "(Ljava/lang/String;Ljava/lang/String;)V" -> {
                                                        super.visitInsn(Opcodes.SWAP)
                                                        super.visitMethodInsn(
                                                            Opcodes.INVOKESTATIC,
                                                            "com/lagradost/runtime/loader/stubs/PluginFileSecurityStub",
                                                            "checkPath",
                                                            "(Ljava/lang/String;)Ljava/lang/String;",
                                                            false,
                                                        )
                                                        super.visitInsn(Opcodes.SWAP)
                                                        super.visitMethodInsn(opcode, owner, fixMethodName(methodName), descriptor, isInterface)
                                                        return
                                                    }
                                                    "(Ljava/io/File;Ljava/lang/String;)V" -> {
                                                        super.visitInsn(Opcodes.SWAP)
                                                        super.visitMethodInsn(
                                                            Opcodes.INVOKESTATIC,
                                                            "com/lagradost/runtime/loader/stubs/PluginFileSecurityStub",
                                                            "checkFile",
                                                            "(Ljava/io/File;)Ljava/io/File;",
                                                            false,
                                                        )
                                                        super.visitInsn(Opcodes.SWAP)
                                                        super.visitMethodInsn(opcode, owner, fixMethodName(methodName), descriptor, isInterface)
                                                        return
                                                    }
                                                    "(Ljava/lang/String;Z)V" -> {
                                                        super.visitInsn(Opcodes.SWAP)
                                                        super.visitMethodInsn(
                                                            Opcodes.INVOKESTATIC,
                                                            "com/lagradost/runtime/loader/stubs/PluginFileSecurityStub",
                                                            "checkPath",
                                                            "(Ljava/lang/String;)Ljava/lang/String;",
                                                            false,
                                                        )
                                                        super.visitInsn(Opcodes.SWAP)
                                                        super.visitMethodInsn(opcode, owner, fixMethodName(methodName), descriptor, isInterface)
                                                        return
                                                    }
                                                    "(Ljava/io/File;Z)V" -> {
                                                        super.visitInsn(Opcodes.SWAP)
                                                        super.visitMethodInsn(
                                                            Opcodes.INVOKESTATIC,
                                                            "com/lagradost/runtime/loader/stubs/PluginFileSecurityStub",
                                                            "checkFile",
                                                            "(Ljava/io/File;)Ljava/io/File;",
                                                            false,
                                                        )
                                                        super.visitInsn(Opcodes.SWAP)
                                                        super.visitMethodInsn(opcode, owner, fixMethodName(methodName), descriptor, isInterface)
                                                        return
                                                    }
                                                }
                                            }

                                            // Intercept Paths.get
                                            if (owner == "java/nio/file/Paths" && methodName == "get") {
                                                super.visitMethodInsn(
                                                    Opcodes.INVOKESTATIC,
                                                    "com/lagradost/runtime/loader/stubs/PluginFileSecurityStub",
                                                    "getPath",
                                                    descriptor,
                                                    false,
                                                )
                                                return
                                            }

                                            super.visitMethodInsn(newOpcode, newOwner, fixMethodName(methodName), newDesc, isInterface)
                                        }

                                        override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
                                            if (isUIClass(owner)) {
                                                val type = Type.getType(descriptor)
                                                when (opcode) {
                                                    Opcodes.GETSTATIC -> {
                                                        pushDefault(type)
                                                    }
                                                    Opcodes.PUTSTATIC -> {
                                                        popType(type)
                                                    }
                                                    Opcodes.GETFIELD -> {
                                                        super.visitInsn(Opcodes.POP)
                                                        pushDefault(type)
                                                    }
                                                    Opcodes.PUTFIELD -> {
                                                        popType(type)
                                                        super.visitInsn(Opcodes.POP)
                                                    }
                                                }
                                                return
                                            }
                                            super.visitFieldInsn(opcode, owner, name, descriptor)
                                        }
                                    }
                                }
                            }

                            reader.accept(visitor, ClassReader.SKIP_FRAMES)
                            zos.write(writer.toByteArray())
                        } catch (t: Throwable) {
                            tempFile.delete()
                            AppLogger.e("Plugin Security: Failed to verify and transform bytecode for ${entry.name}: ${t.message}", t)
                            throw SecurityException("Plugin Security: Failed to parse and verify bytecode for ${entry.name}. Unverified or obfuscated bytecode cannot be loaded.", t)
                        }
                    } else {
                        zos.write(bytes)
                    }

                    zos.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        jarFile.delete()
        tempFile.renameTo(jarFile)
    }

    private fun fixMethodName(name: String): String {
        return when (name) {
            "constructor_impl" -> "constructor-impl"
            "box_impl" -> "box-impl"
            "unbox_impl" -> "unbox-impl"
            "isSuccess_impl" -> "isSuccess-impl"
            "isFailure_impl" -> "isFailure-impl"
            "getOrNull_impl" -> "getOrNull-impl"
            "exceptionOrNull_impl" -> "exceptionOrNull-impl"
            else -> name
        }
    }
}
