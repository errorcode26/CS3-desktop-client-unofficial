package android.webkit

import java.util.concurrent.ConcurrentHashMap

@android.annotation.Implemented
class CookieManager {
    companion object {
        @JvmStatic
        private val INSTANCE = CookieManager()

        @JvmStatic
        fun getInstance(): CookieManager {
            return INSTANCE
        }

        @JvmStatic
        var setCookieHandler: ((String, String) -> Unit)? = null

        @JvmStatic
        var getCookieHandler: ((String) -> String?)? = null

        @JvmStatic
        var removeAllCookiesHandler: ((ValueCallback<Boolean>?) -> Unit)? = null
    }

    private val localJar = ConcurrentHashMap<String, String>()

    fun setAcceptCookie(accept: Boolean) {}

    fun acceptCookie(): Boolean = true

    fun setAcceptThirdPartyCookies(webView: android.webkit.WebView, accept: Boolean) {}

    fun acceptThirdPartyCookies(webView: android.webkit.WebView): Boolean = true

    fun setCookie(url: String, value: String) {
        localJar[url] = value
        setCookieHandler?.invoke(url, value)
    }

    fun setCookie(url: String, value: String, callback: ValueCallback<Boolean>?) {
        setCookie(url, value)
        callback?.onReceiveValue(true)
    }

    fun getCookie(url: String): String? {
        return getCookieHandler?.invoke(url) ?: localJar[url]
    }

    fun removeAllCookies(callback: ValueCallback<Boolean>?) {
        localJar.clear()
        removeAllCookiesHandler?.invoke(callback) ?: callback?.onReceiveValue(true)
    }

    fun removeSessionCookies(callback: ValueCallback<Boolean>?) {
        callback?.onReceiveValue(true)
    }

    fun removeAllCookie() {
        localJar.clear()
    }

    fun removeSessionCookie() {}

    fun hasCookies(): Boolean = localJar.isNotEmpty()

    fun flush() {}
}
