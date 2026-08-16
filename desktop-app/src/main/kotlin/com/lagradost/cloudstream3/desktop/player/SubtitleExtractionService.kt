package com.lagradost.cloudstream3.desktop.player

import com.lagradost.cloudstream3.desktop.subtitles.SubtitlePipeline
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.common.logging.AppLogger
import com.lagradost.runtime.executor.SafePluginInvoker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object SubtitleExtractionService {
    private const val TAG = "SubtitleExtractionService"

    suspend fun searchSubtitles(
        query: String,
        lang: String?,
        season: Int?,
        episode: Int?,
    ): List<Map<String, Any?>> = SubtitlePipeline.searchSubtitles(
        query = query,
        lang = lang,
        season = season,
        episode = episode,
    )

    suspend fun downloadAndExtractSubtitle(
        idPrefix: String,
        data: String,
        name: String,
        lang: String,
        source: String,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val provider = AccountManager.subtitleProviders.firstOrNull { it.idPrefix == idPrefix }
            val auth = AccountManager.cachedAccounts[idPrefix]?.firstOrNull()

            val sub = SubtitleEntity(
                idPrefix = idPrefix,
                name = name,
                data = data,
                lang = lang,
                source = source,
            )

            val fileUrl = if (provider != null) {
                SafePluginInvoker.invokeOrNull(
                    tag = "SubLoad:${provider.name}",
                    providerName = provider.name,
                    timeoutMs = SafePluginInvoker.TIMEOUT_LOAD_MS,
                ) {
                    provider.load(auth, sub)
                }
            } else {
                data.takeIf { it.startsWith("http", ignoreCase = true) || it.startsWith("file:", ignoreCase = true) }
            }

            if (fileUrl == null) {
                AppLogger.w(TAG, "Failed to resolve download URL for subtitle '$name' (idPrefix=$idPrefix)")
                return@withContext null
            }

            AppLogger.i(TAG, "Resolved subtitle download URL from provider '$idPrefix': $fileUrl")
            var finalFile: File? = null

            if (fileUrl.startsWith("http", ignoreCase = true)) {
                val resp = com.lagradost.cloudstream3.app.get(fileUrl, timeout = 15000L).okhttpResponse
                if (!resp.isSuccessful) {
                    AppLogger.w(TAG, "HTTP download failed with status ${resp.code} for $fileUrl")
                    return@withContext null
                }
                val rawBytes = resp.body.bytes()
                AppLogger.i(TAG, "Downloaded ${rawBytes.size} bytes from $fileUrl (HTTP ${resp.code})")

                if (rawBytes.isEmpty()) {
                    AppLogger.w(TAG, "Downloaded subtitle payload is empty")
                    return@withContext null
                }

                finalFile = processAndNormalizeSubtitleBytes(rawBytes, name)
            } else if (fileUrl.startsWith("file://", ignoreCase = true)) {
                val f = File(URI(fileUrl))
                if (f.exists()) {
                    finalFile = processAndNormalizeSubtitleBytes(f.readBytes(), name)
                }
            } else {
                val f = File(fileUrl)
                if (f.exists()) {
                    finalFile = processAndNormalizeSubtitleBytes(f.readBytes(), name)
                }
            }

            if (finalFile == null || !finalFile.exists() || finalFile.length() == 0L) {
                AppLogger.w(TAG, "Subtitle extraction produced no valid subtitle file")
                return@withContext null
            }

            val finalPath = finalFile.absolutePath.replace("\\", "/")
            AppLogger.i(TAG, "Validated and saved normalized subtitle to '$finalPath' (${finalFile.length()} bytes)")
            return@withContext finalPath
        } catch (e: Exception) {
            AppLogger.e(TAG, "downloadAndExtractSubtitle error: ${e.message}", e)
            return@withContext null
        }
    }

    private fun processAndNormalizeSubtitleBytes(rawBytes: ByteArray, fallbackName: String): File? {
        val extractedBytes = extractFromArchiveIfPresent(rawBytes) ?: rawBytes
        if (extractedBytes.isEmpty()) return null

        val (decodedText, formatExt) = decodeAndDetectFormat(extractedBytes)
        if (decodedText.isBlank()) return null

        val tmpFile = File.createTempFile("sub_norm_", formatExt)
        tmpFile.writeText(decodedText, Charsets.UTF_8)
        return tmpFile
    }

    private fun extractFromArchiveIfPresent(bytes: ByteArray): ByteArray? {
        // ZIP Archive: PK (0x50 0x4B)
        if (bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            AppLogger.i(TAG, "Detected ZIP archive payload, extracting subtitle entries...")
            try {
                ZipInputStream(ByteArrayInputStream(bytes)).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        val nameLower = entry.name.lowercase()
                        if (!entry.isDirectory && !nameLower.contains("__macosx") && !nameLower.startsWith(".")) {
                            if (nameLower.endsWith(".srt") || nameLower.endsWith(".vtt") || nameLower.endsWith(".ass") || nameLower.endsWith(".ssa") || nameLower.endsWith(".sub")) {
                                val entryBytes = zipIn.readBytes()
                                if (entryBytes.isNotEmpty()) {
                                    AppLogger.i(TAG, "Extracted valid subtitle '${entry.name}' (${entryBytes.size} bytes) from ZIP")
                                    return entryBytes
                                }
                            }
                        }
                        entry = zipIn.nextEntry
                    }
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to unpack ZIP archive: ${e.message}")
            }
        }
        // GZIP Stream: 0x1F 0x8B
        else if (bytes.size > 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
            AppLogger.i(TAG, "Detected GZIP compressed stream, decompressing...")
            try {
                GZIPInputStream(ByteArrayInputStream(bytes)).use { gzIn ->
                    val decompressed = gzIn.readBytes()
                    if (decompressed.isNotEmpty()) {
                        AppLogger.i(TAG, "Decompressed ${decompressed.size} bytes from GZIP stream")
                        return decompressed
                    }
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to decompress GZIP stream: ${e.message}")
            }
        }
        return null
    }

    private fun decodeAndDetectFormat(bytes: ByteArray): Pair<String, String> {
        val text = when {
            // UTF-8 with BOM: EF BB BF
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> {
                String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            }
            // UTF-16 LE with BOM: FF FE
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> {
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            }
            // UTF-16 BE with BOM: FE FF
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> {
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            }
            else -> {
                try {
                    val decoder = Charsets.UTF_8.newDecoder()
                    val byteBuffer = java.nio.ByteBuffer.wrap(bytes)
                    decoder.decode(byteBuffer).toString()
                } catch (e: Exception) {
                    // Fallback to Windows-1252 / ISO-8859-1 for legacy single-byte encodings
                    try {
                        String(bytes, Charset.forName("windows-1252"))
                    } catch (_: Exception) {
                        String(bytes, Charsets.ISO_8859_1)
                    }
                }
            }
        }

        val trimmedSample = text.take(500).trimStart()
        val ext = when {
            trimmedSample.startsWith("WEBVTT", ignoreCase = true) -> ".vtt"
            trimmedSample.contains("[Script Info]", ignoreCase = true) || trimmedSample.contains("[V4+ Styles]", ignoreCase = true) -> ".ass"
            trimmedSample.contains("<SAMI>", ignoreCase = true) -> ".smi"
            else -> ".srt"
        }

        return Pair(text, ext)
    }
}

