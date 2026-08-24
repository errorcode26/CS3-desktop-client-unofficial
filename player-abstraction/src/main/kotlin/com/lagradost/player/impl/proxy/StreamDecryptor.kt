package com.lagradost.player.impl.proxy

import java.nio.ByteBuffer
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Native CENC decryptor and MP4 Atom surgeon for Cloudstream.
 * Removes all DRM metadata from init segments and decrypts media fragments (moof + mdat) in-memory.
 */
object StreamDecryptor {

    fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    /**
     * Cleans an initialization segment by removing the `sinf` box and swapping `encv`/`enca` back to their original `frma`.
     */
    fun cleanInitSegment(initSegment: ByteArray): ByteArray {
        val parser = MP4Parser(initSegment)
        val atoms = parser.listAtoms()
        val result = ByteBuffer.allocate(initSegment.size)

        for (atom in atoms) {
            if (atom.typeString == "moov") {
                result.put(processMoov(atom).pack())
            } else if (atom.typeString != "pssh") {
                result.put(atom.pack())
            }
        }
        return Arrays.copyOf(result.array(), result.position())
    }

    /**
     * Decrypts a media segment.
     */
    suspend fun streamingDecryptMediaSegment(
        source: okio.BufferedSource,
        keyIdHex: String,
        keyHex: String,
        onBytesDecrypted: suspend (ByteArray) -> Unit,
    ) {
        val keyIdBytes = hexStringToByteArray(keyIdHex)
        val keyBytes = hexStringToByteArray(keyHex)
        val decrypter = DecrypterSession(keyIdBytes, keyBytes)
        decrypter.streamingDecrypt(source, onBytesDecrypted)
    }

    fun decryptMediaSegment(mediaSegment: ByteArray, keyIdHex: String, keyHex: String): ByteArray {
        val keyIdBytes = hexStringToByteArray(keyIdHex)
        val keyBytes = hexStringToByteArray(keyHex)
        val decrypter = DecrypterSession(keyIdBytes, keyBytes)
        return decrypter.decrypt(mediaSegment)
    }

    private fun processMoov(moov: MP4Atom): MP4Atom {
        val parser = MP4Parser(moov.data)
        val newMoov = ByteBuffer.allocate(moov.data.size)
        while (true) {
            val atom = parser.readAtom() ?: break
            if (atom.typeString == "trak") {
                newMoov.put(processTrak(atom).pack())
            } else if (atom.typeString != "pssh") {
                newMoov.put(atom.pack())
            }
        }
        return MP4Atom("moov", Arrays.copyOf(newMoov.array(), newMoov.position()))
    }

    private fun processTrak(trak: MP4Atom): MP4Atom {
        val parser = MP4Parser(trak.data)
        val newTrak = ByteBuffer.allocate(trak.data.size)
        while (true) {
            val atom = parser.readAtom() ?: break
            if (atom.typeString == "mdia") {
                newTrak.put(processMdia(atom).pack())
            } else {
                newTrak.put(atom.pack())
            }
        }
        return MP4Atom("trak", Arrays.copyOf(newTrak.array(), newTrak.position()))
    }

    private fun processMdia(mdia: MP4Atom): MP4Atom {
        val parser = MP4Parser(mdia.data)
        val newMdia = ByteBuffer.allocate(mdia.data.size)
        while (true) {
            val atom = parser.readAtom() ?: break
            if (atom.typeString == "minf") {
                newMdia.put(processMinf(atom).pack())
            } else {
                newMdia.put(atom.pack())
            }
        }
        return MP4Atom("mdia", Arrays.copyOf(newMdia.array(), newMdia.position()))
    }

    private fun processMinf(minf: MP4Atom): MP4Atom {
        val parser = MP4Parser(minf.data)
        val newMinf = ByteBuffer.allocate(minf.data.size)
        while (true) {
            val atom = parser.readAtom() ?: break
            if (atom.typeString == "stbl") {
                newMinf.put(processStbl(atom).pack())
            } else {
                newMinf.put(atom.pack())
            }
        }
        return MP4Atom("minf", Arrays.copyOf(newMinf.array(), newMinf.position()))
    }

    private fun processStbl(stbl: MP4Atom): MP4Atom {
        val parser = MP4Parser(stbl.data)
        val newStbl = ByteBuffer.allocate(stbl.data.size)
        while (true) {
            val atom = parser.readAtom() ?: break
            if (atom.typeString == "stsd") {
                newStbl.put(processStsd(atom).pack())
            } else {
                newStbl.put(atom.pack())
            }
        }
        return MP4Atom("stbl", Arrays.copyOf(newStbl.array(), newStbl.position()))
    }

    private fun processStsd(stsd: MP4Atom): MP4Atom {
        val data = ByteBuffer.wrap(stsd.data)
        val entryCount = data.getInt(4)
        val newData = ByteBuffer.allocate(stsd.data.size)
        newData.put(stsd.data, 0, 8) // copy version, flags, entry count

        val parser = MP4Parser(stsd.data)
        parser.skip(8)
        for (i in 0 until entryCount) {
            val entry = parser.readAtom() ?: break
            newData.put(processSampleEntry(entry).pack())
        }
        return MP4Atom("stsd", Arrays.copyOf(newData.array(), newData.position()))
    }

    private fun processSampleEntry(entry: MP4Atom): MP4Atom {
        val type = entry.typeString
        val fixedSize = when (type) {
            "mp4a", "enca" -> 28
            "mp4v", "encv", "avc1", "hev1", "hvc1" -> 78
            else -> 16
        }

        if (entry.data.size < fixedSize) return entry
        val newData = ByteBuffer.allocate(entry.data.size)
        newData.put(entry.data, 0, fixedSize)

        val parser = MP4Parser(Arrays.copyOfRange(entry.data, fixedSize, entry.data.size))
        var codecFormat: String? = null

        while (true) {
            val atom = parser.readAtom() ?: break
            if (atom.typeString == "sinf" || atom.typeString == "schi" || atom.typeString == "tenc" || atom.typeString == "schm") {
                if (atom.typeString == "sinf") {
                    codecFormat = extractCodecFormat(atom)
                }
                // We drop these DRM boxes completely
            } else {
                newData.put(atom.pack())
            }
        }

        val newType = codecFormat ?: type
        return MP4Atom(newType, Arrays.copyOf(newData.array(), newData.position()))
    }

    private fun extractCodecFormat(sinf: MP4Atom): String? {
        val parser = MP4Parser(sinf.data)
        while (true) {
            val atom = parser.readAtom() ?: break
            if (atom.typeString == "frma") {
                return String(atom.data, Charsets.UTF_8).trim()
            }
        }
        return null
    }

    private class DecrypterSession(private val keyId: ByteArray, private val key: ByteArray) {
        private var moofOverhead = 0
        private var totalOverhead = 0
        private var sampleInfoList = emptyList<SampleInfo>()
        private var trunSampleSizes = IntArray(0)
        private var defaultIvSize = 8

        fun decrypt(data: ByteArray): ByteArray {
            val parser = MP4Parser(data)
            val result = ByteBuffer.allocate(data.size)

            val atoms = parser.listAtoms()

            // First pass: Calculate total encryption overhead for sidx, and moof overhead for trun offset
            moofOverhead = 0
            totalOverhead = 0
            var moovAtom: MP4Atom? = null
            var processedMoov: MP4Atom? = null
            for (atom in atoms) {
                if (atom.typeString == "pssh") totalOverhead += atom.size
                if (atom.typeString == "moov") {
                    moovAtom = atom
                    processedMoov = StreamDecryptor.processMoov(atom)
                    totalOverhead += (atom.size - processedMoov.size)
                }
                if (atom.typeString == "moof") {
                    for (moofAtom in MP4Parser(atom.data).listAtoms()) {
                        if (moofAtom.typeString == "pssh") {
                            moofOverhead += moofAtom.size
                            totalOverhead += moofAtom.size
                        }
                        if (moofAtom.typeString == "traf") {
                            for (trafAtom in MP4Parser(moofAtom.data).listAtoms()) {
                                if (trafAtom.typeString in listOf("senc", "saiz", "saio", "sbgp", "sgpd")) {
                                    moofOverhead += trafAtom.size
                                    totalOverhead += trafAtom.size
                                }
                            }
                        }
                    }
                }
            }

            var i = 0
            while (i < atoms.size) {
                val atom = atoms[i]
                when (atom.typeString) {
                    "sidx" -> result.put(processSidx(atom).pack())
                    "moov" -> {
                        if (processedMoov != null) {
                            result.put(processedMoov.pack())
                        } else {
                            result.put(StreamDecryptor.processMoov(atom).pack())
                        }
                    }
                    "moof" -> {
                        result.put(processMoof(atom).pack())
                        if (i + 1 < atoms.size && atoms[i + 1].typeString == "mdat") {
                            i++
                            result.put(decryptMdat(atoms[i]).pack())
                        }
                    }
                    "mdat" -> result.put(decryptMdat(atom).pack())
                    "pssh" -> {} // Drop top-level PSSH
                    else -> result.put(atom.pack())
                }
                i++
            }
            return Arrays.copyOf(result.array(), result.position())
        }

        suspend fun streamingDecrypt(source: okio.BufferedSource, onBytesDecrypted: suspend (ByteArray) -> Unit) {
            val preMdatAtoms = mutableListOf<MP4Atom>()
            var mdatAtomHeader: ByteArray? = null
            var mdatPayloadSize = 0L

            while (!source.exhausted()) {
                val headerBuffer = source.readByteArray(8)
                val sizeBuffer = ByteBuffer.wrap(headerBuffer)
                var size = sizeBuffer.int.toLong() and 0xFFFFFFFFL
                val typeBytes = ByteArray(4)
                sizeBuffer.get(typeBytes)
                val type = String(typeBytes, Charsets.ISO_8859_1)

                var headerSize = 8
                var fullHeader = headerBuffer

                if (size == 1L) {
                    val extSizeBuffer = source.readByteArray(8)
                    size = ByteBuffer.wrap(extSizeBuffer).long
                    headerSize = 16
                    fullHeader = headerBuffer + extSizeBuffer
                }

                if (type == "mdat") {
                    mdatAtomHeader = fullHeader
                    mdatPayloadSize = size - headerSize
                    break
                } else {
                    val payloadSize = size - headerSize
                    val payload = source.readByteArray(payloadSize)
                    preMdatAtoms.add(MP4Atom(type, payload))
                }
            }

            moofOverhead = 0
            totalOverhead = 0
            var moovAtom: MP4Atom? = null
            var processedMoov: MP4Atom? = null
            for (atom in preMdatAtoms) {
                if (atom.typeString == "pssh") totalOverhead += atom.size
                if (atom.typeString == "moov") {
                    moovAtom = atom
                    processedMoov = StreamDecryptor.processMoov(atom)
                    totalOverhead += (atom.size - processedMoov.size)
                }
                if (atom.typeString == "moof") {
                    for (moofAtom in MP4Parser(atom.data).listAtoms()) {
                        if (moofAtom.typeString == "pssh") {
                            moofOverhead += moofAtom.size
                            totalOverhead += moofAtom.size
                        }
                        if (moofAtom.typeString == "traf") {
                            for (trafAtom in MP4Parser(moofAtom.data).listAtoms()) {
                                if (trafAtom.typeString in listOf("senc", "saiz", "saio", "sbgp", "sgpd")) {
                                    moofOverhead += trafAtom.size
                                    totalOverhead += trafAtom.size
                                }
                            }
                        }
                    }
                }
            }

            val preMdatOutput = ByteBuffer.allocate(1024 * 1024)
            for (atom in preMdatAtoms) {
                when (atom.typeString) {
                    "sidx" -> preMdatOutput.put(processSidx(atom).pack())
                    "moov" -> {
                        if (processedMoov != null) {
                            preMdatOutput.put(processedMoov.pack())
                        } else {
                            preMdatOutput.put(StreamDecryptor.processMoov(atom).pack())
                        }
                    }
                    "moof" -> preMdatOutput.put(processMoof(atom).pack())
                    "pssh" -> {} // Drop top-level PSSH
                    else -> preMdatOutput.put(atom.pack())
                }
            }
            onBytesDecrypted(Arrays.copyOf(preMdatOutput.array(), preMdatOutput.position()))

            if (mdatAtomHeader != null) {
                onBytesDecrypted(mdatAtomHeader)

                if (sampleInfoList.isEmpty()) {
                    while (mdatPayloadSize > 0 && !source.exhausted()) {
                        val toRead = minOf(65536L, mdatPayloadSize)
                        val chunk = source.readByteArray(toRead)
                        onBytesDecrypted(chunk)
                        mdatPayloadSize -= chunk.size
                    }
                } else {
                    var i = 0
                    for (info in sampleInfoList) {
                        val sampleSize = if (i < trunSampleSizes.size) trunSampleSizes[i] else mdatPayloadSize.toInt()
                        if (sampleSize <= 0) break

                        val sampleBytes = source.readByteArray(sampleSize.toLong())
                        val decryptedSample = decryptSample(sampleBytes, info)
                        onBytesDecrypted(decryptedSample)
                        mdatPayloadSize -= sampleSize
                        i++
                    }
                    while (mdatPayloadSize > 0 && !source.exhausted()) {
                        val toRead = minOf(65536L, mdatPayloadSize)
                        val chunk = source.readByteArray(toRead)
                        onBytesDecrypted(chunk)
                        mdatPayloadSize -= chunk.size
                    }
                }
            }

            while (!source.exhausted()) {
                val chunk = source.readByteArray(minOf(65536L, source.buffer.size.coerceAtLeast(1024).toLong()))
                onBytesDecrypted(chunk)
            }
        }

        private fun processMoof(moof: MP4Atom): MP4Atom {
            val parser = MP4Parser(moof.data)
            val newMoof = ByteBuffer.allocate(moof.data.size)

            val atoms = parser.listAtoms()

            for (atom in atoms) {
                if (atom.typeString == "traf") {
                    newMoof.put(processTraf(atom).pack())
                } else if (atom.typeString != "pssh") {
                    newMoof.put(atom.pack())
                }
            }
            return MP4Atom("moof", Arrays.copyOf(newMoof.array(), newMoof.position()))
        }

        private fun processTraf(traf: MP4Atom): MP4Atom {
            val parser = MP4Parser(traf.data)
            val newTraf = ByteBuffer.allocate(traf.data.size)
            var sampleCount = 0

            for (atom in parser.listAtoms()) {
                when (atom.typeString) {
                    "tfhd" -> newTraf.put(processTfhd(atom).pack())
                    "trun" -> {
                        sampleCount = processTrun(atom)
                        newTraf.put(modifyTrun(atom).pack())
                    }
                    "senc" -> sampleInfoList = parseSenc(atom, sampleCount)
                    "saiz", "saio", "sbgp", "sgpd" -> {} // Drop DRM boxes
                    else -> newTraf.put(atom.pack())
                }
            }
            return MP4Atom("traf", Arrays.copyOf(newTraf.array(), newTraf.position()))
        }

        private fun processTfhd(tfhd: MP4Atom): MP4Atom {
            val data = ByteBuffer.wrap(tfhd.data.clone())
            val flags = data.getInt(0) and 0xFFFFFF
            if ((flags and 0x000001) != 0) {
                val current = data.getLong(8)
                data.putLong(8, current - (totalOverhead - moofOverhead))
            }
            return MP4Atom("tfhd", data.array())
        }

        private fun decryptMdat(mdat: MP4Atom): MP4Atom {
            if (sampleInfoList.isEmpty()) return mdat

            val decryptedSamples = ByteBuffer.allocate(mdat.data.size)
            val mdatData = ByteBuffer.wrap(mdat.data)

            for ((i, info) in sampleInfoList.withIndex()) {
                if (!mdatData.hasRemaining()) break
                val sampleSize = if (i < trunSampleSizes.size) trunSampleSizes[i] else mdatData.remaining()
                if (sampleSize > mdatData.remaining()) break

                val sampleBytes = ByteArray(sampleSize)
                mdatData.get(sampleBytes)
                decryptedSamples.put(decryptSample(sampleBytes, info))
            }

            if (mdatData.hasRemaining()) {
                val remaining = ByteArray(mdatData.remaining())
                mdatData.get(remaining)
                decryptedSamples.put(remaining)
            }
            return MP4Atom("mdat", Arrays.copyOf(decryptedSamples.array(), decryptedSamples.position()))
        }

        private fun parseSenc(senc: MP4Atom, sampleCount: Int): List<SampleInfo> {
            val data = ByteBuffer.wrap(senc.data)
            val versionFlags = data.int
            val flags = versionFlags and 0xFFFFFF
            val count = data.int

            val list = mutableListOf<SampleInfo>()
            for (i in 0 until count) {
                if (!data.hasRemaining()) break
                val iv = ByteArray(defaultIvSize)
                data.get(iv)
                val subSamples = mutableListOf<Pair<Int, Int>>() // clear, encrypted
                if ((flags and 0x000002) != 0 && data.remaining() >= 2) {
                    val subSampleCount = data.short.toInt() and 0xFFFF
                    for (j in 0 until subSampleCount) {
                        if (data.remaining() >= 6) {
                            subSamples.add(Pair(data.short.toInt() and 0xFFFF, data.int))
                        }
                    }
                }
                list.add(SampleInfo(iv, subSamples))
            }
            return list
        }

        private val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        private val secretKey = SecretKeySpec(key, "AES")

        private fun decryptSample(sample: ByteArray, info: SampleInfo): ByteArray {
            val iv = ByteArray(16)
            System.arraycopy(info.iv, 0, iv, 0, minOf(info.iv.size, 16))
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))

            if (info.subSamples.isEmpty()) return cipher.doFinal(sample)

            val totalEncrypted = info.subSamples.sumOf { it.second }
            if (totalEncrypted == 0) return sample

            // 1. Gather all encrypted slices into a contiguous buffer
            val allEncrypted = ByteArray(totalEncrypted)
            var encPos = 0
            var sampleOffset = 0
            for ((clear, encrypted) in info.subSamples) {
                sampleOffset += clear
                if (encrypted > 0 && sampleOffset + encrypted <= sample.size) {
                    System.arraycopy(sample, sampleOffset, allEncrypted, encPos, encrypted)
                    encPos += encrypted
                    sampleOffset += encrypted
                }
            }

            // 2. Decrypt all encrypted bytes in a single continuous keystream pass
            val decryptedBytes = cipher.doFinal(allEncrypted)

            // 3. Reconstruct the sample by splicing decrypted slices into a clone of original sample
            val result = sample.clone()
            sampleOffset = 0
            encPos = 0
            for ((clear, encrypted) in info.subSamples) {
                sampleOffset += clear
                if (encrypted > 0 && sampleOffset + encrypted <= result.size && encPos + encrypted <= decryptedBytes.size) {
                    System.arraycopy(decryptedBytes, encPos, result, sampleOffset, encrypted)
                    encPos += encrypted
                    sampleOffset += encrypted
                }
            }
            return result
        }

        private fun processTrun(trun: MP4Atom): Int {
            val data = ByteBuffer.wrap(trun.data)
            val flags = data.int and 0xFFFFFF
            var offset = 8
            if ((flags and 0x000001) != 0) offset += 4
            if ((flags and 0x000004) != 0) offset += 4
            val sampleCount = data.getInt(4)
            trunSampleSizes = IntArray(sampleCount)

            val sizePresent = (flags and 0x000200) != 0
            var currentPos = offset
            for (i in 0 until sampleCount) {
                if ((flags and 0x000100) != 0) currentPos += 4
                if (sizePresent) {
                    trunSampleSizes[i] = data.getInt(currentPos)
                    currentPos += 4
                } else {
                    trunSampleSizes[i] = 0
                }
                if ((flags and 0x000400) != 0) currentPos += 4
                if ((flags and 0x000800) != 0) currentPos += 4
            }
            return sampleCount
        }

        private fun modifyTrun(trun: MP4Atom): MP4Atom {
            val data = ByteBuffer.wrap(trun.data.clone())
            if ((data.getInt(0) and 0x000001) != 0) {
                data.putInt(8, data.getInt(8) - moofOverhead)
            }
            return MP4Atom("trun", data.array())
        }

        private fun processSidx(sidx: MP4Atom): MP4Atom {
            val data = ByteBuffer.wrap(sidx.data.clone())
            val version = data.get(0).toInt()
            val offset = if (version == 0) 24 else 32
            val refCount = data.getShort(offset - 2).toInt() and 0xFFFF
            var refOffset = offset
            for (i in 0 until refCount) {
                val currentSize = data.getInt(refOffset)
                val newSize = ((currentSize ushr 31) shl 31) or ((currentSize and 0x7FFFFFFF) - totalOverhead)
                data.putInt(refOffset, newSize)
                refOffset += 12
            }
            return MP4Atom("sidx", data.array())
        }
    }

    private data class SampleInfo(val iv: ByteArray, val subSamples: List<Pair<Int, Int>>)

    private class MP4Parser(private val data: ByteArray) {
        var position = 0
        fun readAtom(): MP4Atom? {
            if (position + 8 > data.size) return null
            val buffer = ByteBuffer.wrap(data, position, 8)
            var size = buffer.int.toLong() and 0xFFFFFFFFL
            val typeBytes = ByteArray(4)
            buffer.get(typeBytes)
            val type = String(typeBytes, Charsets.ISO_8859_1)

            var headerSize = 8
            if (size == 1L) {
                if (position + 16 > data.size) return null
                size = ByteBuffer.wrap(data, position + 8, 8).long
                headerSize = 16
            }
            if (size < headerSize || position + size > data.size) return null

            val atomData = Arrays.copyOfRange(data, position + headerSize, (position + size).toInt())
            position += size.toInt()
            return MP4Atom(type, atomData)
        }
        fun listAtoms(): List<MP4Atom> {
            val originalPos = position
            position = 0
            val list = mutableListOf<MP4Atom>()
            while (true) {
                list.add(readAtom() ?: break)
            }
            position = originalPos
            return list
        }
        fun skip(bytes: Int) {
            position += bytes
        }
    }

    private class MP4Atom(val typeString: String, val data: ByteArray) {
        val size: Int get() = data.size + 8
        fun pack(): ByteArray {
            val buffer = ByteBuffer.allocate(size)
            buffer.putInt(size)
            buffer.put(typeString.toByteArray(Charsets.ISO_8859_1))
            buffer.put(data)
            return buffer.array()
        }
    }
}
