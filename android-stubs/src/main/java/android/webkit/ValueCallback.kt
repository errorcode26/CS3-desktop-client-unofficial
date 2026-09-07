package android.webkit

@android.annotation.Implemented
fun interface ValueCallback<T> {
    fun onReceiveValue(value: T)
}
