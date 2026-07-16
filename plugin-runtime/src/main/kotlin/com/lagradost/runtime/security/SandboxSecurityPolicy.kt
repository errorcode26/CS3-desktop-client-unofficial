package com.lagradost.runtime.security

/**
 * Centralized security policy for CloudStream Desktop plugins.
 * Enforces a strict Default Deny (Whitelist-Only) security model.
 */
object SandboxSecurityPolicy {

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
        "javax.net.ssl."
    )

    private val CLOUDSTREAM_ECOSYSTEM = setOf(
        "com.lagradost.",
        "org.jsoup.",
        "com.fasterxml.jackson.",
        "com.google.gson.",
        "org.mozilla.javascript.",
        "com.evilsnow.rhino.",
        "android.",
        "androidx.",
        "okhttp3.",
        "okio."
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
        "java.net.ProtocolException"
    )

    private val SAFE_INVOKE = setOf(
        "java.lang.invoke.LambdaMetafactory",
        "java.lang.invoke.MethodType",
        "java.lang.invoke.CallSite",
        "java.lang.invoke.ConstantCallSite",
        "java.lang.invoke.MutableCallSite",
        "java.lang.invoke.VolatileCallSite",
        "java.lang.invoke.StringConcatFactory",
        "java.lang.invoke.TypeDescriptor",
        "java.lang.invoke.TypeDescriptor\$OfField",
        "java.lang.invoke.TypeDescriptor\$OfMethod"
    )

    private val SAFE_IO = setOf(
        "java.io.InputStream",
        "java.io.OutputStream",
        "java.io.ByteArrayInputStream",
        "java.io.ByteArrayOutputStream",
        "java.io.StringReader",
        "java.io.StringWriter",
        "java.io.InputStreamReader",
        "java.io.OutputStreamWriter",
        "java.io.BufferedReader",
        "java.io.BufferedWriter",
        "java.io.IOException",
        "java.io.EOFException",
        "java.io.FileNotFoundException",
        "java.io.InterruptedIOException",
        "java.io.UnsupportedEncodingException",
        "java.io.FilterInputStream",
        "java.io.FilterOutputStream",
        "java.io.BufferedInputStream",
        "java.io.BufferedOutputStream",
        "java.io.DataInputStream",
        "java.io.DataOutputStream",
        "java.io.Reader",
        "java.io.Writer",
        "java.io.Serializable",
        "java.io.Closeable",
        "java.io.PrintStream",
        "java.io.PrintWriter",
        "java.io.ObjectStreamException"
    )

    private val EXPLICIT_DENY = setOf(
        "java.lang.ProcessBuilder",
        "java.lang.Thread",
        "java.lang.ClassLoader",
        "java.net.URLClassLoader",
        "java.security.SecureClassLoader",
        "java.lang.SecurityManager",
        "java.lang.Compiler",
        "java.lang.Runtime",
        "java.net.InetAddress",
        "java.net.NetworkInterface"
    )

    private val RAW_SOCKET_CLASSES = setOf(
        "java.net.Socket",
        "java.net.ServerSocket",
        "java.net.DatagramSocket",
        "java.net.HttpURLConnection",
        "java.net.URLConnection",
        "javax.net.ssl.HttpsURLConnection"
    )

    /**
     * Determines whether a class is permitted to be loaded by a sandboxed plugin.
     * Enforces explicit denies first, then checks against whitelisted sets.
     */
    fun isClassAllowed(className: String, hasSocketPermission: Boolean = false): Boolean {
        // 1. Explicit deny override
        if (EXPLICIT_DENY.contains(className)) return false
        if (className.startsWith("javax.script.") || className.startsWith("javax.naming.") || 
            className.startsWith("sun.") || className.startsWith("jdk.") ||
            className.startsWith("com.sun.") || className.startsWith("com.oracle.") ||
            className.startsWith("org.apache.") || className.startsWith("org.w3c.") ||
            className.startsWith("org.xml.") || className.startsWith("org.ietf.") ||
            className.startsWith("org.omg.")) {
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
        if (className.startsWith("java.nio.")) {
            return className.startsWith("java.nio.charset.") || className.contains("Buffer")
        }

        // 4. Check whitelisted prefixes
        for (prefix in LANGUAGE_CORE) {
            if (className.startsWith(prefix)) return true
        }
        for (prefix in CLOUDSTREAM_ECOSYSTEM) {
            if (className.startsWith(prefix)) return true
        }
        if (NETWORK_SAFE_SUBSET.contains(className)) return true

        // 5. Default Deny for system/JVM namespaces!
        if (className.startsWith("java.") || className.startsWith("javax.")) {
            return false
        }

        // 6. Allow plugin custom packages (e.g. com.mega.*, com.hdhub4u.*, com.megix.*, eu.kanade.*)
        return true
    }

    /**
     * Determines whether an ASM internal class owner (using slashes instead of dots)
     * is permitted in plugin bytecode.
     */
    fun isAsmOwnerAllowed(internalOwner: String): Boolean {
        val className = internalOwner.replace('/', '.')
        return isClassAllowed(className, hasSocketPermission = true)
    }
}
