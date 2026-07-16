package com.lagradost.runtime.loader.stubs

import java.io.InputStream
import java.net.URL
import java.net.URLConnection

object URLStub {
    @JvmStatic
    fun openConnection(url: URL): URLConnection {
        if (url.protocol == "file") {
            throw SecurityException("Security Sandbox: file:// URLs are blocked!")
        }
        return url.openConnection()
    }

    @JvmStatic
    fun openStream(url: URL): InputStream {
        if (url.protocol == "file") {
            throw SecurityException("Security Sandbox: file:// URLs are blocked!")
        }
        return url.openStream()
    }

    @JvmStatic
    fun getContent(url: URL): Any {
        if (url.protocol == "file") {
            throw SecurityException("Security Sandbox: file:// URLs are blocked!")
        }
        return url.content
    }
}
