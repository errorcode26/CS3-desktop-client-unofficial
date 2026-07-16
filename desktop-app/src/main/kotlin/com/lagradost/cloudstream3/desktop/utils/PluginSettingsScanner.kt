package com.lagradost.cloudstream3.desktop.utils

import com.lagradost.common.storage.PluginSettingsSchemaRegistry
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import java.io.File
import java.util.zip.ZipFile

object PluginSettingsScanner {
    fun scanJarForSettings(pluginName: String, jarFile: File) {
        try {
            val keys = mutableSetOf<String>()
            java.util.zip.ZipFile(jarFile).use { zip ->
                for (entry in zip.entries()) {
                    if (entry.name.endsWith(".class")) {
                        zip.getInputStream(entry).use { inputStream ->
                            val reader = ClassReader(inputStream)
                            val node = ClassNode()
                            reader.accept(node, 0)
                            scanClassNode(node, keys)
                        }
                    }
                }
            }

            // For every extracted string key, guess the type (String) and register it
            for (key in keys) {
                // Ignore keys that don't look like settings keys (spaces, massive strings)
                if (key.length in 2..50 && !key.contains("\n") && !key.contains(" ")) {
                    var guessedType = "String"
                    var guessedDefault: Any = ""

                    val lowerKey = key.lowercase()
                    if (lowerKey.startsWith("provider") || lowerKey.endsWith("enable") ||
                        lowerKey.endsWith("_on") || lowerKey.startsWith("seen_") ||
                        lowerKey.contains("toggle") || lowerKey.contains("use_")
                    ) {
                        guessedType = "Boolean"
                        // Usually providers and toggles default to true
                        guessedDefault = true
                    } else if (lowerKey.contains("concurrency") || lowerKey.contains("order")) {
                        guessedType = "Int"
                        guessedDefault = 1
                    }

                    PluginSettingsSchemaRegistry.register(
                        pluginPrefName = "${pluginName}_",
                        key = key,
                        type = guessedType,
                        defaultValue = guessedDefault,
                        isGlobal = true,
                    )
                }
            }
        } catch (e: Exception) {
            com.lagradost.common.logging.AppLogger.e("Error scanning jar for settings schema", e)
        }
    }

    private fun scanClassNode(classNode: ClassNode, keys: MutableSet<String>) {
        for (method in classNode.methods) {
            scanMethodNode(method, keys)
        }
    }

    private fun scanMethodNode(methodNode: MethodNode, keys: MutableSet<String>) {
        val instructions = methodNode.instructions.toArray()
        for (i in instructions.indices) {
            val insn = instructions[i]
            if (insn is MethodInsnNode) {
                // Look for DataStore.getKey, DataStore.setKey, or SharedPreferences.getString/getBoolean
                if (insn.owner == "com/lagradost/cloudstream3/utils/DataStore" &&
                    (insn.name == "getKey" || insn.name == "setKey")
                ) {
                    extractPrecedingStringLdc(instructions, i, keys)
                } else if (insn.owner == "android/content/SharedPreferences" &&
                    (insn.name == "getString" || insn.name == "getBoolean" || insn.name == "getInt" || insn.name == "getStringSet")
                ) {
                    extractPrecedingStringLdc(instructions, i, keys)
                }
            }
        }
    }

    private fun extractPrecedingStringLdc(instructions: Array<AbstractInsnNode>, currentIndex: Int, keys: MutableSet<String>) {
        // Walk backwards up to 10 instructions to find the string literals pushed to the stack
        val maxBack = maxOf(0, currentIndex - 10)
        for (j in currentIndex downTo maxBack) {
            val prevInsn = instructions[j]
            if (prevInsn is LdcInsnNode && prevInsn.cst is String) {
                val str = prevInsn.cst as String
                keys.add(str)
                // If it's SharedPreferences, the first string argument is the key.
                // We might find multiple strings (e.g., the default value).
                // We add them all; the regex filter will drop obvious non-keys.
            }
        }
    }
}
