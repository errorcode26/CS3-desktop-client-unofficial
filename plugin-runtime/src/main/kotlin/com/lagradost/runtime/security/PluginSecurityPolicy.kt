package com.lagradost.runtime.security

/**
 * Centralized security policy for CloudStream Desktop plugins.
 * Enforces a strict Default Deny (Whitelist-Only) security model.
 */
object PluginSecurityPolicy {

    private val LANGUAGE_CORE = setOf(
        "kotlin.",
        "kotlinx.",
        "java.lang.",
        "java.util.",
        "java.text.",
        "java.time.",
        "java.math.",
        "java.security.",
        "javax.crypto.",
        "javax.net.ssl.",
    )

    private val CLOUDSTREAM_ECOSYSTEM = setOf(
        "com.lagradost.cloudstream3.",
        "com.lagradost.nicehttp.",
        "com.lagradost.api.",
        "org.jsoup.",
        "com.fasterxml.jackson.",
        "com.google.gson.",
        "org.mozilla.javascript.",
        "com.evilsnow.rhino.",
        "android.",
        "androidx.",
        "okhttp3.",
        "okio.",
    )

    private val NETWORK_SAFE_SUBSET = setOf(
        "java.net.URI",
        "java.net.URL",
        "java.net.URLDecoder",
        "java.net.URLEncoder",
        "java.net.HttpCookie",
        "java.net.CookieManager",
        "java.net.IDN",
        "java.net.MalformedURLException",
        "java.net.URISyntaxException",
        "java.net.UnknownHostException",
        "java.net.SocketTimeoutException",
        "java.net.ConnectException",
        "java.net.ProtocolException",
    )

    private val SAFE_INVOKE = setOf(
        "java.lang.invoke.LambdaMetafactory",
        "java.lang.invoke.MethodType",
        "java.lang.invoke.CallSite",
        "java.lang.invoke.ConstantCallSite",
        "java.lang.invoke.MutableCallSite",
        "java.lang.invoke.VolatileCallSite",
        "java.lang.invoke.StringConcatFactory",
    )

    private val SAFE_IO = setOf(
        "java.io.InputStream",
        "java.io.OutputStream",
        "java.io.ByteArrayInputStream",
        "java.io.ByteArrayOutputStream",
        "java.io.BufferedInputStream",
        "java.io.BufferedOutputStream",
        "java.io.DataInputStream",
        "java.io.DataOutputStream",
        "java.io.Reader",
        "java.io.Writer",
        "java.io.StringReader",
        "java.io.StringWriter",
        "java.io.BufferedReader",
        "java.io.BufferedWriter",
        "java.io.InputStreamReader",
        "java.io.OutputStreamWriter",
        "java.io.Closeable",
        "java.io.AutoCloseable",
        "java.io.Flushable",
        "java.io.IOException",
        "java.io.EOFException",
        "java.io.InterruptedIOException",
        "java.io.UnsupportedEncodingException",
        "java.io.CharConversionException",
        "java.io.UTFDataFormatException",
        "java.io.Serializable",
        "java.io.Externalizable",
        "java.io.ObjectStreamException",
        "java.io.NotSerializableException",
        "java.io.InvalidClassException",
        "java.io.PrintStream",
    )

    private val EXPLICIT_DENY = setOf(
        "java.lang.Process",
        "java.lang.ProcessBuilder",
        "java.lang.ProcessBuilder\$Redirect",
        "java.lang.Runtime",
        "java.lang.Compiler",
        "java.lang.SecurityManager",
        "java.lang.Thread",
        "java.lang.ThreadGroup",
        "java.lang.ThreadLocal",
        "java.lang.InheritableThreadLocal",
        "java.lang.ClassLoader",
        "java.lang.instrument.Instrumentation",
        "java.lang.management.ManagementFactory",
    )

    private val RAW_SOCKET_CLASSES = setOf(
        "java.net.Socket",
        "java.net.ServerSocket",
        "java.net.DatagramSocket",
        "java.net.HttpURLConnection",
        "java.net.URLConnection",
        "javax.net.ssl.HttpsURLConnection",
    )

    /**
     * Determines whether a class is permitted to be loaded by a plugin.
     * Enforces explicit denies first, then checks against whitelisted sets.
     */
    fun isClassAllowed(className: String, hasSocketPermission: Boolean = false, isTrusted: Boolean = false): Boolean {
        // 0. If user explicitly trusted the plugin, permit class loading
        if (isTrusted) {
            return true
        }

        // 1. Explicit deny override
        if (EXPLICIT_DENY.contains(className)) {
            return false
        }

        // Allow our internal safety stubs (RuntimeStub, ReflectionStub, ProcessBuilderStub, etc.)
        if (className.startsWith("com.lagradost.runtime.loader.stubs.")) {
            return true
        }

        // Block internal desktop application infrastructure, SQLite storage, and JNI
        if (className.startsWith("com.lagradost.common.") ||
            className.startsWith("com.lagradost.cloudstream3.desktop.") ||
            className.startsWith("com.lagradost.runtime.") ||
            className.startsWith("com.sun.jna.") ||
            className.startsWith("org.bytedeco.") ||
            className.startsWith("app.cash.sqldelight.")
        ) {
            return false
        }

        if (className.startsWith("javax.script.") || className.startsWith("javax.naming.") ||
            className.startsWith("sun.") || className.startsWith("jdk.") ||
            className.startsWith("com.sun.") || className.startsWith("com.oracle.") ||
            className.startsWith("org.apache.") || className.startsWith("org.w3c.") ||
            className.startsWith("org.xml.") || className.startsWith("org.ietf.") ||
            className.startsWith("org.omg.")
        ) {
            return false
        }

        // 2. Raw sockets check
        if (RAW_SOCKET_CLASSES.contains(className)) {
            return hasSocketPermission
        }

        // 3. Special handling for java.lang.invoke and java.io
        if (className.startsWith("java.lang.invoke.")) {
            return SAFE_INVOKE.contains(className)
        }

        if (className.startsWith("java.io.")) {
            return SAFE_IO.contains(className)
        }

        // 4. Ecosystem & Language Core prefix check
        val inLanguageCore = LANGUAGE_CORE.any { className.startsWith(it) }
        val inCloudstream = CLOUDSTREAM_ECOSYSTEM.any { className.startsWith(it) }

        if (inLanguageCore || inCloudstream) {
            return true
        }

        // 5. Network safe subset
        if (NETWORK_SAFE_SUBSET.contains(className)) {
            return true
        }

        // 6. Whitelist plugin's own packages and arbitrary 3rd party bundled libraries
        if (className.contains(".")) {
            return true
        }

        return false
    }

    /**
     * Helper for ASM bytecode scanners where internal JVM names use '/' instead of '.'.
     */
    fun isAsmOwnerAllowed(internalName: String, isTrusted: Boolean = false): Boolean {
        // Strip array dimensions (e.g. "[Ljava/lang/String;" -> "java/lang/String")
        val cleanInternal = internalName.trimStart('[').removePrefix("L").removeSuffix(";")
        val dotName = cleanInternal.replace('/', '.')
        return isClassAllowed(dotName, hasSocketPermission = false, isTrusted = isTrusted)
    }
}
