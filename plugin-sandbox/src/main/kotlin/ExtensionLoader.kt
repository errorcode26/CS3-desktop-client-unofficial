import com.googlecode.dex2jar.tools.Dex2jarCmd
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

object ExtensionLoader {
    fun loadExtension(cs3File: File): File? {
        println("Loading extension from ${cs3File.name}...")

        if (!cs3File.exists()) {
            println("File does not exist!")
            return null
        }

        val buildDir = File(cs3File.parentFile, "build")
        if (!buildDir.exists()) buildDir.mkdirs()

        var convertedJar: File? = null

        ZipFile(cs3File).use { zip ->
            val dexEntry = zip.getEntry("classes.dex")
            if (dexEntry != null) {
                println("Found classes.dex! Extracting...")
                val dexFile = File(buildDir, cs3File.nameWithoutExtension + ".dex")
                zip.getInputStream(dexEntry).use { input ->
                    Files.copy(input, dexFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }

                convertedJar = File(buildDir, cs3File.nameWithoutExtension + "-jvm.jar")
                println("Converting Dalvik bytecode to JVM bytecode...")
                try {
                    Dex2jarCmd().doMain("-f", dexFile.absolutePath, "-o", convertedJar!!.absolutePath)
                } catch (e: Exception) {
                    Dex2jarCmd.main("-f", dexFile.absolutePath, "-o", convertedJar!!.absolutePath)
                }

                dexFile.delete() // Cleanup dex
                println("Successfully converted to ${convertedJar!!.name}")
            } else {
                println("classes.dex not found in the plugin!")
            }
        }

        return convertedJar
    }
}
