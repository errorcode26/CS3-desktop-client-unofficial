package com.lagradost.cloudstream3.desktop.discord

import com.lagradost.common.logging.AppLogger
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Low-level Discord IPC client using a single duplex named pipe handle.
 *
 * Design notes:
 * - A single RandomAccessFile opened as "rw" is the correct way to open a Windows
 *   named pipe for bidirectional I/O (maps to GENERIC_READ | GENERIC_WRITE).
 * - After the handshake, all operations are fire-and-forget writes. There is no
 *   background read loop — Discord's SET_ACTIVITY does not require reading responses
 *   for correct operation, and concurrent reads/writes on the same RAF cause issues.
 * - Thread safety is ensured via a single lock covering all pipe access.
 */
class DiscordIpcClient {
    companion object {
        private const val TAG = "DiscordIpcClient"
        private const val OP_HANDSHAKE = 0
        private const val OP_FRAME = 1
        private const val OP_CLOSE = 2
    }

    @Volatile private var raf: RandomAccessFile? = null

    @Volatile private var connected = false
    private val lock = Any()

    fun isConnected(): Boolean = connected && raf != null

    /**
     * Opens the Discord IPC named pipe, performs handshake, and waits for READY.
     * This is a synchronous, blocking call. Returns true if READY was received.
     */
    fun connect(clientId: String): Boolean = synchronized(lock) {
        if (isConnected()) return true
        closeInternal()

        for (i in 0..9) {
            val pipePath = "\\\\.\\pipe\\discord-ipc-$i"
            try {
                val pipe = RandomAccessFile(pipePath, "rw")

                // Send handshake
                val handshake = """{"v":1,"client_id":"$clientId"}"""
                if (!writeFrame(pipe, OP_HANDSHAKE, handshake)) {
                    closeRaf(pipe)
                    continue
                }

                // Read READY — synchronous, one-time
                val response = readFrameSync(pipe)
                if (response != null && response.contains("READY", ignoreCase = true)) {
                    raf = pipe
                    connected = true
                    AppLogger.i(TAG, "Connected to Discord IPC on $pipePath")
                    return true
                } else {
                    closeRaf(pipe)
                }
            } catch (_: Exception) {
                // Pipe doesn't exist or is busy — silently continue probing next pipe
            }
        }
        return false
    }

    /** Fire-and-forget send. Returns false if the write fails (caller should reconnect). */
    fun sendActivity(activityJson: String): Boolean = synchronized(lock) {
        val pipe = raf ?: return false
        val nonce = java.util.UUID.randomUUID().toString()
        val payload = buildString {
            append("""{"cmd":"SET_ACTIVITY","args":{"pid":""")
            append(ProcessHandle.current().pid())
            append(""","activity":""")
            append(activityJson)
            append("""},"nonce":"""")
            append(nonce)
            append(""""}""")
        }
        AppLogger.d(TAG, "Sending SET_ACTIVITY: $payload")
        val ok = writeFrame(pipe, OP_FRAME, payload)
        if (!ok) {
            AppLogger.w(TAG, "sendActivity write failed — marking disconnected")
            connected = false
        }
        return ok
    }

    /** Sends a null activity to clear Discord presence. */
    fun clearActivity(): Boolean = synchronized(lock) {
        val pipe = raf ?: return false
        val nonce = java.util.UUID.randomUUID().toString()
        val payload = """{"cmd":"SET_ACTIVITY","args":{"pid":${ProcessHandle.current().pid()},"activity":null},"nonce":"$nonce"}"""
        AppLogger.d(TAG, "Clearing activity")
        val ok = writeFrame(pipe, OP_FRAME, payload)
        if (!ok) connected = false
        return ok
    }

    fun close() = synchronized(lock) {
        closeInternal()
    }

    // ---- Internal helpers ----

    private fun closeInternal() {
        connected = false
        raf?.let { closeRaf(it) }
        raf = null
    }

    private fun closeRaf(pipe: RandomAccessFile) {
        try {
            pipe.close()
        } catch (_: Exception) {}
    }

    private fun writeFrame(pipe: RandomAccessFile, opcode: Int, json: String): Boolean {
        return try {
            val bytes = json.toByteArray(Charsets.UTF_8)
            val buf = ByteBuffer.allocate(8 + bytes.size).order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(opcode)
            buf.putInt(bytes.size)
            buf.put(bytes)
            pipe.write(buf.array())
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "writeFrame(op=$opcode) error: ${e.message}")
            false
        }
    }

    private fun readFrameSync(pipe: RandomAccessFile): String? {
        return try {
            val header = ByteArray(8)
            pipe.readFully(header)
            val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val op = buf.getInt()
            val len = buf.getInt()

            if (op == OP_CLOSE) {
                AppLogger.i(TAG, "Received OP_CLOSE during handshake")
                return null
            }
            if (len <= 0 || len > 65536) return null

            val payload = ByteArray(len)
            pipe.readFully(payload)
            String(payload, Charsets.UTF_8)
        } catch (e: Exception) {
            AppLogger.w(TAG, "readFrameSync error: ${e.message}")
            null
        }
    }
}
