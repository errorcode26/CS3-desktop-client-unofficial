package com.lagradost.cloudstream3

import android.app.Activity

object CommonActivity {
    var activity: Activity? = null

    fun showToast(message: String?, duration: Int? = null) {
        println("Toast: $message")
    }

    fun showToast(act: Activity?, message: String?, duration: Int? = null) {
        println("Toast: $message")
    }
}
