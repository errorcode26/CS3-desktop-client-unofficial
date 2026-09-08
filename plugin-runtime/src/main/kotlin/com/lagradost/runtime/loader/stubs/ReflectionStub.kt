package com.lagradost.runtime.loader.stubs

import com.lagradost.common.logging.AppLogger
import com.lagradost.runtime.loader.CompatPluginClassLoader
import com.lagradost.runtime.loader.SafePluginClassLoader
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Bytecode-injected stub that intercepts reflection calls made by plugins.
 * Enforces a strict Default Deny (Whitelist-Only) security policy to prevent sandbox escapes.
 */
object ReflectionStub {

    private val EXPLICIT_DENY_PREFIXES = listOf(
        // System and Process
        "java.lang.System",
        "java.lang.Runtime",
        "java.lang.Process",
        "java.lang.ProcessBuilder",
        "java.lang.Thread",
        "java.lang.ThreadGroup",
        "java.lang.ClassLoader",
        "java.lang.SecurityManager",
        "java.lang.Compiler",
        "java.lang.instrument.",
        "java.lang.management.",

        // Meta-Reflection & Invocation
        "java.lang.reflect.",
        "java.lang.invoke.",
        "com.lagradost.runtime.loader.stubs.",
        "com.lagradost.runtime.loader.SafePluginClassLoader",

        // Filesystem and Direct I/O
        "java.io.File",
        "java.io.FileInputStream",
        "java.io.FileOutputStream",
        "java.io.RandomAccessFile",
        "java.nio.file.",
        "java.awt.Desktop",
        "java.awt.Robot",

        // Raw Networking
        "java.net.Socket",
        "java.net.ServerSocket",
        "java.net.DatagramSocket",
        "java.net.NetworkInterface",

        // Host Desktop Application Internals & Storage
        "com.lagradost.cloudstream3.desktop.",
        "com.lagradost.common.",
        "com.lagradost.runtime.",
        "app.cash.sqldelight.",
        "com.sun.jna.",
        "org.bytedeco.",

        // JDK Internals
        "sun.",
        "com.sun.",
        "jdk.",
        "com.oracle.",
        "javax.script.",
        "javax.naming.",
    )

    private val WHITELIST_PREFIXES = listOf(
        // Standard Collections & Utilities
        "java.util.",
        "java.text.",
        "java.time.",
        "java.math.",

        // Safe Crypto & Network Handlers
        "java.security.",
        "javax.crypto.",
        "javax.net.ssl.",

        // Safe Network Classes (URI, URL parsing)
        "java.net.URI",
        "java.net.URL",
        "java.net.URLDecoder",
        "java.net.URLEncoder",
        "java.net.HttpCookie",
        "java.net.IDN",

        // Kotlin Runtime & Serialization
        "kotlin.",
        "kotlinx.serialization.",
        "kotlinx.coroutines.",

        // Safe Parsers (HTML / JSON / XML)
        "org.jsoup.",
        "com.fleeksoft.ksoup.",
        "com.fasterxml.jackson.",
        "com.google.gson.",
        "org.json.",
        "org.mozilla.",
        "com.evilsnow.rhino.",
        "org.schabi.newpipe.extractor.",

        // Network Client Library
        "okhttp3.",
        "okio.",
        "io.ktor.",

        // CloudStream Ecosystem Public APIs
        "com.lagradost.cloudstream3.",
        "com.lagradost.nicehttp.",
        "com.lagradost.api.",

        // Android Compatibility Stubs
        "android.",
        "androidx.",
        "com.android.",
        "com.google.android.",
    )

    private val SAFE_JAVA_LANG_CLASSES = setOf(
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
        "java.lang.Enum",
        "java.lang.Throwable",
        "java.lang.Exception",
        "java.lang.RuntimeException",
        "java.lang.IllegalArgumentException",
        "java.lang.IllegalStateException",
        "java.lang.NullPointerException",
        "java.lang.IndexOutOfBoundsException",
        "java.lang.StringBuilder",
        "java.lang.StringBuffer",
        "java.lang.Comparable",
        "java.lang.Iterable",
        "java.lang.Cloneable",
        "java.lang.Math",
    )

    fun isReflectionAllowed(targetClass: Class<*>, memberName: String? = null): Boolean {
        // Strip array wrappers down to component type
        var rootClass = targetClass
        while (rootClass.isArray) {
            rootClass = rootClass.componentType ?: break
        }

        // Primitives are always safe
        if (rootClass.isPrimitive) return true

        val className = rootClass.name

        // 1. Explicit Deny: Block dangerous sandbox escape vectors immediately
        if (EXPLICIT_DENY_PREFIXES.any { className.startsWith(it) }) {
            return false
        }

        // 2. Allow any class loaded by the plugin's own ClassLoader (plugin internal code)
        val loader = rootClass.classLoader
        if (loader is CompatPluginClassLoader || loader is SafePluginClassLoader) {
            return true
        }

        // 3. Safe java.lang classes (primitives, strings, exceptions, stringbuilder)
        if (SAFE_JAVA_LANG_CLASSES.contains(className)) {
            return true
        }

        // 4. Whitelisted prefixes (Collections, JSON libraries, CloudStream models, OkHttp)
        if (WHITELIST_PREFIXES.any { className.startsWith(it) }) {
            return true
        }

        // 5. Default Deny: Everything not explicitly whitelisted is rejected
        return false
    }

    @JvmStatic
    fun invoke(method: Method, obj: Any?, args: Array<Any?>?): Any? {
        val declaringClass = method.declaringClass
        if (!isReflectionAllowed(declaringClass, method.name)) {
            AppLogger.w("Plugin Security: Blocked reflection invoke on ${declaringClass.name}.${method.name}")
            throw SecurityException("Plugin Security: Reflection invoke on ${declaringClass.name}.${method.name} is blocked by Default Deny policy.")
        }
        return method.invoke(obj, *(args ?: emptyArray()))
    }

    @JvmStatic
    fun get(field: Field, obj: Any?): Any? {
        val declaringClass = field.declaringClass
        if (!isReflectionAllowed(declaringClass, field.name)) {
            AppLogger.w("Plugin Security: Blocked reflection get on ${declaringClass.name}.${field.name}")
            throw SecurityException("Plugin Security: Reflection get on ${declaringClass.name}.${field.name} is blocked by Default Deny policy.")
        }
        return field.get(obj)
    }

    @JvmStatic
    fun set(field: Field, obj: Any?, value: Any?) {
        val declaringClass = field.declaringClass
        if (!isReflectionAllowed(declaringClass, field.name)) {
            AppLogger.w("Plugin Security: Blocked reflection set on ${declaringClass.name}.${field.name}")
            throw SecurityException("Plugin Security: Reflection set on ${declaringClass.name}.${field.name} is blocked by Default Deny policy.")
        }
        field.set(obj, value)
    }

    @JvmStatic
    fun newInstance(constructor: Constructor<*>, args: Array<Any?>?): Any {
        val declaringClass = constructor.declaringClass
        if (!isReflectionAllowed(declaringClass, "<init>")) {
            AppLogger.w("Plugin Security: Blocked reflection newInstance on ${declaringClass.name}")
            throw SecurityException("Plugin Security: Reflection newInstance on ${declaringClass.name} is blocked by Default Deny policy.")
        }
        return constructor.newInstance(*(args ?: emptyArray()))
    }

    @JvmStatic
    fun setAccessible(accessibleObject: AccessibleObject, flag: Boolean) {
        val declaringClass = when (accessibleObject) {
            is Method -> accessibleObject.declaringClass
            is Field -> accessibleObject.declaringClass
            is Constructor<*> -> accessibleObject.declaringClass
            else -> null
        }
        if (declaringClass != null && !isReflectionAllowed(declaringClass)) {
            AppLogger.w("Plugin Security: Blocked setAccessible on ${declaringClass.name}")
            throw SecurityException("Plugin Security: Reflection setAccessible on ${declaringClass.name} is blocked by Default Deny policy.")
        }
        accessibleObject.isAccessible = flag
    }
}
