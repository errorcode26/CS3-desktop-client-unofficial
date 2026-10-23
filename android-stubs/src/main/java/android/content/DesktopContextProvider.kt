package android.content

import java.io.File

/** Provide a desktop `Context` implementation that is also an `AppCompatActivity` so
 * plugins that cast `Context` to `AppCompatActivity` succeed. */
@android.annotation.Implemented
object DesktopContextProvider {
    // A simple AppCompatActivity-backed context for plugins
    private class DesktopAppActivity(baseDir: File? = null) : androidx.appcompat.app.AppCompatActivity(baseDir) {
        // No-op; serves only as a type that extends AppCompatActivity
    }

    val context: Context = DesktopAppActivity()

    fun createPluginContext(pluginName: String, storageDir: File): Context {
        return DesktopAppActivity(storageDir)
    }
}
