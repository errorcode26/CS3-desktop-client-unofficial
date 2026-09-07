package android.webkit

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

    fun setAcceptCookie(accept: Boolean) {}

    fun setAcceptThirdPartyCookies(webView: android.webkit.WebView, accept: Boolean) {}

    fun setCookie(url: String, value: String) {
        setCookieHandler?.invoke(url, value)
    }

    fun getCookie(url: String): String? {
        return getCookieHandler?.invoke(url)
    }

    fun removeAllCookies(callback: ValueCallback<Boolean>?) {
        removeAllCookiesHandler?.invoke(callback) ?: callback?.onReceiveValue(true)
    }

    fun removeSessionCookies(callback: ValueCallback<Boolean>?) {
        callback?.onReceiveValue(true)
    }

    fun removeAllCookie() {}
    fun removeSessionCookie() {}
    fun hasCookies(): Boolean = false

    fun flush() {}
}
