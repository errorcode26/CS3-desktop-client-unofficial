package com.lagradost.runtime.loader.stubs

import com.lagradost.common.logging.AppLogger
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Bytecode-injected stub that intercepts reflection calls made by plugins.
 * This allows plugins to use reflection for harmless tasks (like JSON parsing)
 * while blocking sandbox escapes (e.g., reflecting into System, Runtime, ClassLoader).
 */
object ReflectionStub {

    private fun isDangerous(className: String, methodName: String? = null): Boolean {
        // Core sandbox escapes
        if (className == "java.lang.System" && methodName != null) {
            return methodName == "exit" || methodName == "loadLibrary" || methodName == "load" || methodName == "setSecurityManager"
        }
        if (className == "java.lang.Runtime" && methodName != null) {
            return methodName == "exec" || methodName == "loadLibrary" || methodName == "load" || methodName == "exit" || methodName == "halt"
        }
        if (className == "java.lang.ProcessBuilder" || className == "java.lang.Thread" || className == "java.lang.ClassLoader") {
            return true
        }
        
        // Prevent meta-reflection (using reflection to bypass this stub by reflecting into java.lang.reflect)
        if (className.startsWith("java.lang.reflect.")) return true
        if (className.startsWith("java.lang.invoke.")) return true
        
        // Prevent bypassing the stub by reflecting directly into the stub itself
        if (className.startsWith("com.lagradost.runtime.loader.stubs.")) return true
        
        // Prevent reflecting into the classloader itself
        if (className == "com.lagradost.runtime.loader.SafePluginClassLoader") return true

        return false
    }

    @JvmStatic
    fun invoke(method: Method, obj: Any?, args: Array<Any?>?): Any? {
        if (isDangerous(method.declaringClass.name, method.name)) {
            AppLogger.i("Security Sandbox: Blocked reflection invoke on ${method.declaringClass.name}.${method.name}")
            throw SecurityException("Security Sandbox: Reflection invoke on ${method.declaringClass.name}.${method.name} is blocked.")
        }
        return method.invoke(obj, *(args ?: emptyArray()))
    }

    @JvmStatic
    fun get(field: Field, obj: Any?): Any? {
        if (isDangerous(field.declaringClass.name)) {
            AppLogger.i("Security Sandbox: Blocked reflection get on ${field.declaringClass.name}.${field.name}")
            throw SecurityException("Security Sandbox: Reflection get on ${field.declaringClass.name}.${field.name} is blocked.")
        }
        return field.get(obj)
    }

    @JvmStatic
    fun set(field: Field, obj: Any?, value: Any?) {
        if (isDangerous(field.declaringClass.name)) {
            AppLogger.i("Security Sandbox: Blocked reflection set on ${field.declaringClass.name}.${field.name}")
            throw SecurityException("Security Sandbox: Reflection set on ${field.declaringClass.name}.${field.name} is blocked.")
        }
        field.set(obj, value)
    }

    @JvmStatic
    fun newInstance(constructor: Constructor<*>, args: Array<Any?>?): Any {
        if (isDangerous(constructor.declaringClass.name)) {
            AppLogger.i("Security Sandbox: Blocked reflection newInstance on ${constructor.declaringClass.name}")
            throw SecurityException("Security Sandbox: Reflection newInstance on ${constructor.declaringClass.name} is blocked.")
        }
        return constructor.newInstance(*(args ?: emptyArray()))
    }
    
    @JvmStatic
    fun setAccessible(accessibleObject: java.lang.reflect.AccessibleObject, flag: Boolean) {
        val declaringClass = when (accessibleObject) {
            is Method -> accessibleObject.declaringClass
            is Field -> accessibleObject.declaringClass
            is Constructor<*> -> accessibleObject.declaringClass
            else -> null
        }
        if (declaringClass != null && isDangerous(declaringClass.name)) {
            AppLogger.i("Security Sandbox: Blocked setAccessible on ${declaringClass.name}")
            throw SecurityException("Security Sandbox: Reflection setAccessible on ${declaringClass.name} is blocked.")
        }
        accessibleObject.isAccessible = flag
    }
}
