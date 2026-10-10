package com.lagradost.cloudstream3.desktop.network.webview

import com.lagradost.common.logging.AppLogger
import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.ptr.DoubleByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Low-level COM vtable dispatch and dynamic callback generation for Microsoft Edge WebView2 via JNA.
 * Handles interface pointer calls by vtable index, out parameter reading, and Java-side COM callback dispatch.
 */
internal object WindowsCom {
    const val S_OK = 0
    val E_NOINTERFACE = 0x80004002L.toInt()

    private val functions = ConcurrentHashMap<Long, Function>()

    private fun function(obj: Pointer, index: Int): Function {
        val address = Pointer.nativeValue(obj.getPointer(0).getPointer(index.toLong() * Native.POINTER_SIZE))
        return functions.getOrPut(address) { Function.getFunction(Pointer(address)) }
    }

    /** Calls method [index] of the COM interface [obj] (0-2 are IUnknown), returning the HRESULT */
    fun call(obj: Pointer, index: Int, vararg args: Any?): Int =
        function(obj, index).invokeInt(arrayOf<Any?>(obj, *args))

    fun ok(hr: Int): Boolean = hr >= 0

    fun release(p: Pointer?) {
        if (p != null) runCatching { call(p, 2) }
    }

    fun addRef(p: Pointer?): Pointer? {
        if (p != null) call(p, 1)
        return p
    }

    fun iid(s: String): Guid.GUID = Guid.GUID.fromString("{$s}")

    /** QueryInterface, returns null when not supported */
    fun query(p: Pointer, iid: Guid.GUID): Pointer? {
        val ref = PointerByReference()
        return if (ok(call(p, 0, iid, ref))) ref.value else null
    }

    /** A method with one interface out parameter after [args]; caller must release the result */
    fun getPtr(p: Pointer, index: Int, vararg args: Any?): Pointer? {
        val ref = PointerByReference()
        return if (ok(call(p, index, *args, ref))) ref.value else null
    }

    /** A method with one LPWSTR out parameter after [args] (CoTaskMemFree released here) */
    fun getString(p: Pointer, index: Int, vararg args: Any?): String? {
        val ref = PointerByReference()
        if (!ok(call(p, index, *args, ref))) return null
        val s = ref.value ?: return null
        return try {
            s.getWideString(0)
        } finally {
            Ole32.INSTANCE.CoTaskMemFree(s)
        }
    }

    fun getInt(p: Pointer, index: Int): Int? {
        val ref = IntByReference()
        return if (ok(call(p, index, ref))) ref.value else null
    }

    fun getBool(p: Pointer, index: Int): Boolean = getInt(p, index)?.let { it != 0 } ?: false

    fun getDouble(p: Pointer, index: Int): Double? {
        val ref = DoubleByReference()
        return if (ok(call(p, index, ref))) ref.value else null
    }

    fun getLong(p: Pointer, index: Int): Long? {
        val ref = LongByReference()
        return if (ok(call(p, index, ref))) ref.value else null
    }

    fun putBool(p: Pointer, index: Int, value: Boolean): Int = call(p, index, if (value) 1 else 0)

    fun w(s: String?): WString? = s?.let { WString(it) }

    /** Registers [handler] on [p] at method [index] and returns registration token, or null */
    fun addHandler(p: Pointer, index: Int, handler: ComObject): Long? {
        val token = LongByReference()
        return if (ok(call(p, index, handler.pointer, token))) token.value else null
    }

    // ------------------------------------------------------------------ IStream Helpers

    private interface Shlwapi : Library {
        fun SHCreateMemStream(pInit: ByteArray?, cbInit: Int): Pointer?
    }

    private val shlwapi by lazy { Native.load("shlwapi", Shlwapi::class.java) }

    /** Returns a new IStream over a copy of [bytes] (caller must release it) */
    fun memStream(bytes: ByteArray): Pointer? = shlwapi.SHCreateMemStream(bytes, bytes.size)

    /** Reads an IStream to the end up to [limit] bytes */
    fun readStream(stream: Pointer, limit: Int = 16 shl 20): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = Memory(65536)
        val read = IntByReference()
        while (out.size() < limit) {
            read.value = 0
            val hr = call(stream, 3, buffer, 65536, read)
            if (hr < 0 || read.value <= 0) break
            out.write(buffer.getByteArray(0, read.value))
            if (hr == 1) break // S_FALSE: EOF
        }
        return out.toByteArray()
    }
}

/** RECT passed by value for put_Bounds */
@Structure.FieldOrder("left", "top", "right", "bottom")
internal class RectByValue(
    @JvmField var left: Int = 0,
    @JvmField var top: Int = 0,
    @JvmField var right: Int = 0,
    @JvmField var bottom: Int = 0,
) : Structure(), Structure.ByValue

/**
 * A COM object implemented in Java: IUnknown + one Invoke method.
 * Matches WebView2's handler interfaces (events: Invoke(sender, args); completions: Invoke(hr, result)).
 */
internal class ComObject private constructor(private val iid: Guid.GUID) {
    private interface QueryFn : Callback {
        fun invoke(self: Pointer?, riid: Pointer?, ppv: Pointer?): Int
    }

    private interface RefFn : Callback {
        fun invoke(self: Pointer?): Int
    }

    private interface EventFn : Callback {
        fun invoke(self: Pointer?, sender: Pointer?, args: Pointer?): Int
    }

    private interface DoneFn : Callback {
        fun invoke(self: Pointer?, hr: Int, result: Pointer?): Int
    }

    companion object {
        private const val TAG = "WindowsCom"
        private val live = ConcurrentHashMap.newKeySet<ComObject>()
        private val IUNKNOWN = WindowsCom.iid("00000000-0000-0000-C000-000000000046")

        /** Event handler: [block] receives (sender, args) valid during invocation */
        fun event(iid: Guid.GUID, block: (sender: Pointer?, args: Pointer?) -> Unit): ComObject =
            ComObject(iid).apply {
                invokeFn = object : EventFn {
                    override fun invoke(self: Pointer?, sender: Pointer?, args: Pointer?): Int {
                        try {
                            block(sender, args)
                        } catch (t: Throwable) {
                            AppLogger.e("$TAG: Event handler error: ${t.message}")
                        }
                        return WindowsCom.S_OK
                    }
                }
                build()
            }

        /** Completion handler: [block] receives (HRESULT, result) valid during invocation */
        fun completed(iid: Guid.GUID, block: (hr: Int, result: Pointer?) -> Unit): ComObject =
            ComObject(iid).apply {
                invokeFn = object : DoneFn {
                    override fun invoke(self: Pointer?, hr: Int, result: Pointer?): Int {
                        try {
                            block(hr, result)
                        } catch (t: Throwable) {
                            AppLogger.e("$TAG: Completion handler error: ${t.message}")
                        }
                        return WindowsCom.S_OK
                    }
                }
                build()
            }
    }

    private val refs = AtomicInteger(1)
    private lateinit var invokeFn: Callback
    private val vtable = Memory(4L * Native.POINTER_SIZE)
    private val obj = Memory(Native.POINTER_SIZE.toLong())
    val pointer: Pointer get() = obj

    private val queryFn = object : QueryFn {
        override fun invoke(self: Pointer?, riid: Pointer?, ppv: Pointer?): Int {
            if (ppv == null) return WindowsCom.E_NOINTERFACE
            val asked = riid?.let { Guid.GUID(it) }
            if (asked != null && (asked == iid || asked == IUNKNOWN)) {
                refs.incrementAndGet()
                ppv.setPointer(0, obj)
                return WindowsCom.S_OK
            }
            ppv.setPointer(0, Pointer.NULL)
            return WindowsCom.E_NOINTERFACE
        }
    }

    private val addRefFn = object : RefFn {
        override fun invoke(self: Pointer?): Int = refs.incrementAndGet()
    }

    private val releaseFn = object : RefFn {
        override fun invoke(self: Pointer?): Int {
            val n = refs.decrementAndGet()
            if (n == 0) live.remove(this@ComObject)
            return n
        }
    }

    private fun build() {
        vtable.setPointer(0, CallbackReference.getFunctionPointer(queryFn))
        vtable.setPointer(Native.POINTER_SIZE.toLong(), CallbackReference.getFunctionPointer(addRefFn))
        vtable.setPointer(2L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(releaseFn))
        vtable.setPointer(3L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(invokeFn))
        obj.setPointer(0, vtable)
        live.add(this)
    }

    /** Releases the creator's initial reference after passing the pointer to COM */
    fun releaseOwn() {
        releaseFn.invoke(null)
    }
}
