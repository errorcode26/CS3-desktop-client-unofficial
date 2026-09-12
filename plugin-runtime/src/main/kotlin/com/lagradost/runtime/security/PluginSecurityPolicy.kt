package com.lagradost.runtime.security

/**
 * Centralized security policy for CloudStream Desktop plugins.
 * Enforces a strict Default Deny (Whitelist-Only) security model.
 */
object PluginSecurityPolicy {

    private val LANGUAGE_CORE = setOf(
        "kotlin.",
        "kotlinx.",
        "java.util.",
        "java.text.",
        "java.time.",
        "java.math.",
        "java.security.",
        "javax.crypto.",
        "javax.net.ssl.",
    )

    val SAFE_JAVA_LANG_CLASSES = setOf(
        // Core Primitives & Wrappers
        "java.lang.Object",
        "java.lang.String",
        "java.lang.CharSequence",
        "java.lang.Number",
        "java.lang.Integer",
        "java.lang.Long",
        "java.lang.Double",
        "java.lang.Float",
        "java.lang.Short",
        "java.lang.Byte",
        "java.lang.Boolean",
        "java.lang.Character",
        "java.lang.Void",

        // Language & Computation Constructs
        "java.lang.Enum",
        "java.lang.Comparable",
        "java.lang.Iterable",
        "java.lang.Cloneable",
        "java.lang.Appendable",
        "java.lang.StringBuilder",
        "java.lang.StringBuffer",
        "java.lang.Math",
        "java.lang.StrictMath",
        "java.lang.Class",
        "java.lang.System",
        "java.lang.Runtime",
        "java.lang.AutoCloseable",
        "java.lang.Runnable",
        "java.lang.ThreadLocal",

        // Standard Annotations
        "java.lang.Deprecated",
        "java.lang.Override",
        "java.lang.SuppressWarnings",

        // Standard Exceptions and Errors
        "java.lang.Throwable",
        "java.lang.Exception",
        "java.lang.RuntimeException",
        "java.lang.IllegalArgumentException",
        "java.lang.IllegalStateException",
        "java.lang.NullPointerException",
        "java.lang.IndexOutOfBoundsException",
        "java.lang.ArrayIndexOutOfBoundsException",
        "java.lang.StringIndexOutOfBoundsException",
        "java.lang.UnsupportedOperationException",
        "java.lang.IllegalAccessException",
        "java.lang.NoSuchMethodException",
        "java.lang.NoSuchFieldException",
        "java.lang.ClassNotFoundException",
        "java.lang.NoClassDefFoundError",
        "java.lang.Error",
        "java.lang.AssertionError",
        "java.lang.NumberFormatException",
        "java.lang.ArithmeticException",
        "java.lang.ClassCastException",
        "java.lang.SecurityException",
        "java.lang.TypeNotPresentException",
        "java.lang.ArrayStoreException",
        "java.lang.NegativeArraySizeException",
        "java.lang.IllegalMonitorStateException",
    )

    private val SAFE_REFLECT = setOf(
        "java.lang.reflect.Type",
        "java.lang.reflect.ParameterizedType",
        "java.lang.reflect.GenericArrayType",
        "java.lang.reflect.WildcardType",
        "java.lang.reflect.TypeVariable",
        "java.lang.reflect.InvocationTargetException",
        "java.lang.reflect.Array",
        "java.lang.reflect.Member",
        "java.lang.reflect.Modifier",
        "java.lang.reflect.Method",
        "java.lang.reflect.Field",
        "java.lang.reflect.Constructor",
        "java.lang.reflect.AccessibleObject",
    )

    private val DANGEROUS_UTIL_CLASSES = setOf(
        "java.util.concurrent.Executors",
        "java.util.concurrent.ForkJoinPool",
        "java.util.concurrent.ThreadPoolExecutor",
        "java.util.concurrent.ScheduledThreadPoolExecutor",
        "java.util.ServiceLoader",
        "java.util.jar.JarFile",
    )

    private val CLOUDSTREAM_ECOSYSTEM = setOf(
        "com.lagradost.cloudstream3.",
        "com.lagradost.nicehttp.",
        "com.lagradost.api.",
        "com.lagradost.safefile.",
        "org.jsoup.",
        "com.fleeksoft.ksoup.",
        "com.fasterxml.jackson.",
        "com.google.gson.",
        "org.json.",
        "org.mozilla.",
        "com.evilsnow.rhino.",
        "org.schabi.newpipe.extractor.",
        "dev.whyoleg.cryptography.",
        "io.ktor.",
        "android.",
        "androidx.",
        "com.android.",
        "com.google.android.",
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
        "java.io.File",
        "java.io.FileInputStream",
        "java.io.FileOutputStream",
        "java.io.FileReader",
        "java.io.FileWriter",
    )

    private val SAFE_NIO_CLASSES = setOf(
        "java.nio.ByteBuffer",
        "java.nio.ByteOrder",
        "java.nio.CharBuffer",
        "java.nio.ShortBuffer",
        "java.nio.IntBuffer",
        "java.nio.LongBuffer",
        "java.nio.FloatBuffer",
        "java.nio.DoubleBuffer",
        "java.nio.Buffer",
        "java.nio.BufferUnderflowException",
        "java.nio.BufferOverflowException",
        "java.nio.ReadOnlyBufferException",
        "java.nio.InvalidMarkException",
    )

    private val EXPLICIT_DENY = setOf(
        "java.lang.Process",
        "java.lang.ProcessBuilder",
        "java.lang.ProcessBuilder\$Redirect",
        "java.lang.Compiler",
        "java.lang.SecurityManager",
        "java.lang.Thread",
        "java.lang.ThreadGroup",
        "java.lang.InheritableThreadLocal",
        "java.lang.ClassLoader",
        "java.lang.instrument.Instrumentation",
        "java.lang.management.ManagementFactory",
        "java.nio.file.Files",
        "java.nio.file.Path",
        "java.nio.file.Paths",
        "java.nio.file.FileSystem",
        "java.nio.file.FileSystems",
        "java.awt.Desktop",
        "java.awt.Robot",
        "java.net.NetworkInterface",
        "java.io.RandomAccessFile",
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
        // 1. Explicit deny override - NEVER bypassed, even for trusted plugins
        if (EXPLICIT_DENY.contains(className)) {
            return false
        }

        // Allow our internal safety stubs (RuntimeStub, ReflectionStub, ProcessBuilderStub, etc.)
        if (className.startsWith("com.lagradost.runtime.loader.stubs.")) {
            return true
        }

        // Block internal desktop application infrastructure, SQLite storage, and JNI - NEVER bypassed
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

        // 2. If user explicitly trusted the plugin, permit remaining non-critical class loading
        if (isTrusted) {
            return true
        }

        // 3. Raw sockets check
        if (RAW_SOCKET_CLASSES.contains(className)) {
            return hasSocketPermission
        }

        // 3. Special handling for java.lang, java.lang.invoke, java.lang.reflect, java.lang.annotation, java.io, java.nio, and java.util
        if (className.startsWith("java.lang.invoke.")) {
            return SAFE_INVOKE.contains(className)
        }

        if (className.startsWith("java.lang.reflect.")) {
            return SAFE_REFLECT.contains(className)
        }

        if (className.startsWith("java.lang.annotation.")) {
            return true
        }

        if (className.startsWith("java.lang.ref.")) {
            return true
        }

        if (className.startsWith("java.lang.")) {
            return SAFE_JAVA_LANG_CLASSES.contains(className)
        }

        if (className.startsWith("java.util.")) {
            if (DANGEROUS_UTIL_CLASSES.contains(className)) {
                return false
            }
        }

        if (className.startsWith("java.io.")) {
            return SAFE_IO.contains(className)
        }

        if (className.startsWith("java.nio.charset.")) {
            return true
        }

        if (className.startsWith("java.nio.")) {
            return SAFE_NIO_CLASSES.contains(className)
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

        // 6. True Default Deny: Anything not explicitly whitelisted is rejected
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

    /**
     * Identifies whether a class belongs to the Java Platform, JDK internals, or host desktop application.
     */
    fun isSystemOrHostPackage(className: String): Boolean {
        return className.startsWith("java.") ||
            className.startsWith("javax.") ||
            className.startsWith("sun.") ||
            className.startsWith("com.sun.") ||
            className.startsWith("jdk.") ||
            className.startsWith("com.oracle.") ||
            className.startsWith("org.ietf.") ||
            className.startsWith("org.omg.") ||
            className.startsWith("org.w3c.") ||
            className.startsWith("org.xml.") ||
            className.startsWith("org.apache.") ||
            className.startsWith("com.lagradost.common.") ||
            className.startsWith("com.lagradost.cloudstream3.desktop.") ||
            className.startsWith("com.lagradost.runtime.") ||
            className.startsWith("app.cash.sqldelight.") ||
            className.startsWith("org.bytedeco.") ||
            className.startsWith("com.sun.jna.")
    }
}
