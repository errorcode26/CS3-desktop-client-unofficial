package com.lagradost.cloudstream3.desktop.network

import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Intercepts Android-style Dialog and DialogFragment show() calls made by plugins that try to
 * spawn a Cloudflare bypass WebView dialog.
 *
 * On desktop this interceptor detects known CF dialog patterns and routes them to the
 * single manual desktop verification window.
 */
object DesktopCfDialogInterceptor {

    private const val TAG = "DesktopCfDialogInterceptor"

    // Known class name fragments that indicate a plugin's CF bypass dialog
    private val CF_DIALOG_HINTS = listOf(
        "cloudflare",
        "cfbypass",
        "webviewdialog",
        "cfwebview",
        "cfchallenge",
        "bottomsheetdialog",
        "bottomsheet",
    )

    /**
     * Called from [androidx.fragment.app.DialogFragment.show] and [android.app.Dialog.show] stubs.
     */
    fun onShowCalled(dialog: Any, tag: String?) {
        val className = dialog.javaClass.name.lowercase()
        val timeSinceChallenge = System.currentTimeMillis() - CloudflareKiller.lastChallengeTimestamp
        val isRecentlyChallenged = timeSinceChallenge in 0..15000L

        val isCfDialog = CF_DIALOG_HINTS.any { className.contains(it) } || isRecentlyChallenged

        if (isCfDialog) {
            AppLogger.i("$TAG: Intercepted CF dialog show() from '${dialog.javaClass.simpleName}' (tag=$tag). Triggering manual verification window.")
            val targetUrl = extractUrlFromDialog(dialog) ?: CloudflareKiller.lastChallengedUrl

            if (targetUrl != null) {
                val host = try { java.net.URI(targetUrl).host.orEmpty() } catch (_: Exception) { "" }.ifEmpty {
                    CloudflareKiller.lastChallengedHost.orEmpty()
                }

                CoroutineScope(Dispatchers.IO).launch {
                    val solved = SystemBrowserCdpBypass.launchManualClearance(targetUrl, host)
                    if (solved) {
                        AppLogger.i("$TAG: Dismissing plugin dialog '${dialog.javaClass.simpleName}' after successful clearance.")
                        runCatching {
                            val dismissMethod = dialog.javaClass.getMethod("dismiss")
                            dismissMethod.invoke(dialog)
                        }
                    }
                }
            } else {
                AppLogger.w("$TAG: Could not determine target URL for dialog $className")
            }
        } else {
            AppLogger.w("$TAG: Discarded unrecognised Dialog.show() call from '${dialog.javaClass.simpleName}' (tag=$tag). No desktop equivalent.")
        }
    }

    private fun extractUrlFromDialog(dialog: Any): String? {
        var clazz: Class<*>? = dialog.javaClass
        while (clazz != null && clazz != Any::class.java) {
            for (field in clazz.declaredFields) {
                try {
                    field.isAccessible = true
                    val value = field.get(dialog)
                    if (value is String && (value.startsWith("http://") || value.startsWith("https://"))) {
                        return value
                    }
                } catch (_: Exception) {}
            }
            clazz = clazz.superclass
        }
        return null
    }
}
