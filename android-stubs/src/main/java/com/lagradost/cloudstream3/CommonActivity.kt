package com.lagradost.cloudstream3

import android.app.Activity
import android.content.DesktopContextProvider

object CommonActivity {
    var activity: Activity? = DesktopContextProvider.context as? Activity

    fun showToast(message: String?, duration: Int? = null) {
        println("Toast: $message")
    }

    fun showToast(act: Activity?, message: String?, duration: Int? = null) {
        println("Toast: $message")
    }
}
