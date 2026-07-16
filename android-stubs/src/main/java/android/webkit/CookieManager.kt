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
    }

    fun setAcceptCookie(accept: Boolean) {}

    fun setCookie(url: String, value: String) {
        setCookieHandler?.invoke(url, value)
    }

    fun getCookie(url: String): String? {
        return getCookieHandler?.invoke(url)
    }

    fun removeAllCookies(callback: Any?) {}

    fun flush() {}
}
