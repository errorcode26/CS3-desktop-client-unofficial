package android.content

/** Provide a desktop `Context` implementation that is also an `AppCompatActivity` so
 * plugins that cast `Context` to `AppCompatActivity` succeed. */
@android.annotation.Implemented
object DesktopContextProvider {
    // A simple AppCompatActivity-backed context for plugins
    private class DesktopAppActivity : androidx.appcompat.app.AppCompatActivity() {
        // No-op; serves only as a type that extends AppCompatActivity
    }

    val context: Context = DesktopAppActivity()
}
