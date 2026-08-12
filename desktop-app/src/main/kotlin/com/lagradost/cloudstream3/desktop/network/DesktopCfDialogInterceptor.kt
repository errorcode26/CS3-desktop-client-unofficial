package com.lagradost.cloudstream3.desktop.network

import com.lagradost.common.logging.AppLogger

/**
 * Intercepts Android-style DialogFragment.show() calls made by plugins that try to
 * spawn a Cloudflare bypass WebView dialog using Android's Fragment API.
 *
 * On desktop there is no FragmentManager — this interceptor detects known CF dialog
 * patterns and silently re-routes them to the desktop WebView2 CDP bypass.
 *
 * Without this, plugins like AnimePahe crash with NoSuchMethodError because the
 * GhostStub for BottomSheetDialogFragment extends Object and has no show() method.
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
    )

    /**
     * Called from [androidx.fragment.app.DialogFragment.show] stub.
     *
     * If the dialog is a known Cloudflare bypass dialog, we fire our desktop
     * WebView2 CDP bypass. Otherwise we log and silently discard the call.
     */
    fun onShowCalled(dialog: Any, tag: String?) {
        val className = dialog.javaClass.name.lowercase()
        val isCfDialog = CF_DIALOG_HINTS.any { className.contains(it) }

        if (isCfDialog) {
            AppLogger.i("$TAG: Intercepted CF dialog show() from '${dialog.javaClass.simpleName}' (tag=$tag). Routing to desktop WebView2 bypass.")
            // The actual CF bypass is already wired through CloudflareKiller via
            // SystemBrowserCdpBypass. This call arrives AFTER CloudflareKiller has
            // already triggered CDP bypass (which runs in the OkHttp interceptor chain).
            // All we need to do here is swallow the call so the plugin does not crash.
            // The CDP bypass running in the CloudflareKiller will resolve the cookies.
        } else {
            AppLogger.w("$TAG: Discarded unrecognised DialogFragment.show() call from '${dialog.javaClass.simpleName}' (tag=$tag). No desktop equivalent.")
        }
    }
}
