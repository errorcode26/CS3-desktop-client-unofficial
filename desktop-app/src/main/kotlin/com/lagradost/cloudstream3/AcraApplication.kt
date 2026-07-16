package com.lagradost.cloudstream3

import android.annotation.Implemented

@Implemented
class AcraApplication {
    companion object {
        inline fun <reified T> getKey(key: String): T? {
            return CloudStreamApp.getKey<T>(key, null)
        }

        @JvmStatic
        fun setKey(key: String, value: Any?) {
            CloudStreamApp.setKey(key, value)
        }
    }
}
