package com.lagradost.runtime.loader

import org.objectweb.asm.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object PluginBytecodeTransformer {

    fun transform(jarFile: File) {
        val tempFile = File(jarFile.absolutePath + ".tmp")
        ZipInputStream(FileInputStream(jarFile)).use { zis ->
            ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val newEntry = ZipEntry(entry.name)
                    zos.putNextEntry(newEntry)

                    val bytes = zis.readBytes()
                    if (entry.name.endsWith(".class")) {
                        val reader = ClassReader(bytes)
                        val writer = ClassWriter(0)

                        val visitor = object : ClassVisitor(Opcodes.ASM9, writer) {

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
                                        } else if (owner == "java/lang/System" && (methodName == "exit" || methodName == "loadLibrary" || methodName == "load" || methodName == "setSecurityManager")) {
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

                        reader.accept(visitor, 0)
                        zos.write(writer.toByteArray())
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
