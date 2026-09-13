package com.lagradost.player.impl.proxy

import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamDecryptorTest {

    private fun makeAtom(type: String, data: ByteArray): ByteArray {
        val size = data.size + 8
        val buffer = ByteBuffer.allocate(size)
        buffer.putInt(size)
        buffer.put(type.toByteArray(Charsets.ISO_8859_1))
        buffer.put(data)
        return buffer.array()
    }

    @Test
    fun testHexStringToByteArray() {
        val hex = "0123456789abcdef"
        val bytes = StreamDecryptor.hexStringToByteArray(hex)
        assertEquals(8, bytes.size)
        assertEquals(0x01.toByte(), bytes[0])
        assertEquals(0xef.toByte(), bytes[7])
    }

    @Test
    fun testExtractIvSizeFromInitWith16ByteIv() {
        // Construct tenc atom with 16-byte default IV size
        val tencData = ByteArray(24)
        tencData[0] = 0 // version 0
        tencData[6] = 1 // default_isProtected = 1
        tencData[7] = 16 // default_Per_Sample_IV_Size = 16

        val tencAtom = makeAtom("tenc", tencData)
        val schiAtom = makeAtom("schi", tencAtom)
        val sinfAtom = makeAtom("sinf", schiAtom)
        val moovAtom = makeAtom("moov", sinfAtom)

        val detectedIv = StreamDecryptor.extractIvSizeFromInit(moovAtom)
        assertEquals(16, detectedIv, "Should detect 16-byte IV size from tenc box")
    }

    @Test
    fun testExtractIvSizeFromInitWith8ByteIv() {
        // Construct tenc atom with 8-byte default IV size
        val tencData = ByteArray(24)
        tencData[0] = 0
        tencData[6] = 1
        tencData[7] = 8 // default_Per_Sample_IV_Size = 8

        val tencAtom = makeAtom("tenc", tencData)
        val schiAtom = makeAtom("schi", tencAtom)
        val sinfAtom = makeAtom("sinf", schiAtom)
        val moovAtom = makeAtom("moov", sinfAtom)

        val detectedIv = StreamDecryptor.extractIvSizeFromInit(moovAtom)
        assertEquals(8, detectedIv, "Should detect 8-byte IV size from tenc box")
    }

    @Test
    fun testExtractIvSizeDefaultsTo8WhenNoTenc() {
        val emptyMoov = makeAtom("moov", ByteArray(0))
        val detectedIv = StreamDecryptor.extractIvSizeFromInit(emptyMoov)
        assertEquals(8, detectedIv, "Should default to 8-byte IV when no tenc box is present")
    }

    @Test
    fun testCleanInitSegmentRemovesPssh() {
        val psshData = ByteArray(16)
        val psshAtom = makeAtom("pssh", psshData)
        val moovAtom = makeAtom("moov", ByteArray(0))

        val combined = psshAtom + moovAtom
        val cleaned = StreamDecryptor.cleanInitSegment(combined)

        // pssh should be completely stripped
        val cleanedString = String(cleaned, Charsets.ISO_8859_1)
        assertTrue(cleanedString.contains("moov"), "Cleaned init must preserve moov")
        assertTrue(!cleanedString.contains("pssh"), "Cleaned init must strip top-level pssh")
    }
}
