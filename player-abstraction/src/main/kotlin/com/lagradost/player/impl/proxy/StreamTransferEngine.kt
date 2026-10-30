package com.lagradost.player.impl.proxy

import com.lagradost.common.logging.AppLogger
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondBytesWriter
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal object StreamTransferEngine {

    fun findTsSyncOffset(buffer: ByteArray, length: Int): Int {
        val syncByte = 0x47.toByte()
        val packetSize = 188
        for (i in 0 until length - packetSize * 2) {
            if (buffer[i] == syncByte &&
                buffer[i + packetSize] == syncByte &&
                buffer[i + packetSize * 2] == syncByte
            ) {
                return i
            }
        }
        return 0
    }

    suspend fun transferStream(
        call: ApplicationCall,
        response: Response,
        parsedContentType: ContentType,
        finalCode: Int,
        contentLengthParam: Long?,
        skipBytes: Long,
        detectedTsOffset: Int,
        url: String,
        requestBuilder: Request.Builder,
        mergedHeaders: Map<String, String>,
        client: OkHttpClient,
        tracksListener: ProxyTracksListener?,
    ) {
        var streamStarted = false
        try {
            call.respondBytesWriter(
                contentType = parsedContentType,
                status = HttpStatusCode.fromValue(finalCode),
                contentLength = contentLengthParam,
            ) {
                streamStarted = true
                var currentResponse: Response? = response
                var streamSource = currentResponse?.body?.source() ?: throw Exception("No body")

                val totalSkip = skipBytes + detectedTsOffset
                if (totalSkip > 0) {
                    withContext(Dispatchers.IO) {
                        streamSource.skip(totalSkip)
                    }
                }

                val ktorChannel = this
                var totalBytesRead = skipBytes
                val buffer = ByteArray(65536)
                tracksListener?.onLoadingComplete()

                try {
                    while (true) {
                        try {
                            var bytesRead: Int
                            withContext(Dispatchers.IO) {
                                while (true) {
                                    val readBytes = try {
                                        streamSource.read(buffer)
                                    } catch (e: java.net.SocketException) {
                                        throw Exception("CDN_ERROR", e)
                                    } catch (e: Exception) {
                                        throw Exception("CDN_ERROR", e)
                                    }
                                    if (readBytes == -1) {
                                        val expectedCl = currentResponse?.body?.contentLength() ?: -1L
                                        val expectedBytes = if (expectedCl != -1L) (expectedCl - totalSkip) else -1L
                                        if (expectedBytes != -1L && (totalBytesRead - skipBytes) < expectedBytes) {
                                            AppLogger.w("Proxy:LocalStream", "Premature EOF from CDN for $url at ${totalBytesRead - skipBytes}/$expectedBytes bytes")
                                            throw Exception("CDN_ERROR_PREMATURE_EOF")
                                        }
                                        break
                                    }

                                    try {
                                        ktorChannel.writeFully(buffer, 0, readBytes)
                                        ktorChannel.flush()
                                        totalBytesRead += readBytes
                                    } catch (e: Exception) {
                                        AppLogger.w("Proxy:LocalStream", "Write to MPV socket failed for $url at $totalBytesRead bytes: ${e.javaClass.simpleName}: ${e.message}")
                                        throw Exception("CLIENT_DISCONNECT", e)
                                    }
                                }
                            }
                            try {
                                ktorChannel.flush()
                            } catch (_: Exception) {}
                            break // EOF reached naturally
                        } catch (e: Exception) {
                            val cl = currentResponse?.body?.contentLength() ?: -1L
                            AppLogger.w("Proxy:LocalStream", "Stream transfer interrupted: $url at $totalBytesRead/$cl bytes. Exception: ${e.message}, Cause: ${e.cause?.javaClass?.simpleName}: ${e.cause?.message}, ChannelClosed=${ktorChannel.isClosedForWrite}")
                            if (e.message == "CLIENT_DISCONNECT" || ktorChannel.isClosedForWrite) {
                                break
                            }

                            val expectedTotal = if (cl != -1L) (cl - totalSkip) else -1L
                            if (expectedTotal != -1L && (totalBytesRead - skipBytes) >= expectedTotal) {
                                break
                            }

                            AppLogger.w("Proxy:LocalStream", "CDN connection dropped mid-stream at $totalBytesRead/$cl bytes. Resuming transparently...")
                            withContext(Dispatchers.IO) {
                                currentResponse?.body?.close()
                            }

                            // Transparently reconnect and resume from totalBytesRead
                            val resumeBuilder = requestBuilder.build().newBuilder()
                            val origRangeHeader = mergedHeaders["Range"]
                            if (origRangeHeader != null && origRangeHeader.startsWith("bytes=", ignoreCase = true)) {
                                val startPart = origRangeHeader.substringAfter("=").substringBefore("-").toLongOrNull() ?: 0L
                                val endPart = origRangeHeader.substringAfter("-")
                                val newStart = startPart + (totalBytesRead - skipBytes) + detectedTsOffset
                                resumeBuilder.safeHeader("Range", "bytes=$newStart-$endPart")
                            } else {
                                val newStart = totalBytesRead + detectedTsOffset
                                resumeBuilder.safeHeader("Range", "bytes=$newStart-")
                            }

                            var retrySuccess = false
                            for (attempt in 1..3) {
                                try {
                                    currentResponse = client.newCall(resumeBuilder.build()).await()
                                    if (currentResponse!!.isSuccessful) {
                                        streamSource = currentResponse!!.body?.source() ?: throw Exception("No body")
                                        if (currentResponse!!.code == 200 && totalBytesRead > 0) {
                                            withContext(Dispatchers.IO) {
                                                streamSource.skip(totalBytesRead)
                                            }
                                        }
                                        retrySuccess = true
                                        break
                                    }
                                } catch (retryEx: Exception) {
                                    kotlinx.coroutines.delay(500L * attempt)
                                }
                                if (attempt < 3) {
                                    currentResponse?.body?.close()
                                }
                            }

                            if (!retrySuccess && (currentResponse?.code in listOf(400, 403, 405, 416))) {
                                withContext(Dispatchers.IO) {
                                    currentResponse?.body?.close()
                                }
                                val noRangeBuilder = requestBuilder.build().newBuilder()
                                noRangeBuilder.removeHeader("Range")
                                for (attempt in 1..2) {
                                    try {
                                        val fallbackResp = client.newCall(noRangeBuilder.build()).await()
                                        if (fallbackResp.isSuccessful) {
                                            currentResponse = fallbackResp
                                            streamSource = fallbackResp.body?.source() ?: throw Exception("No body")
                                            if (totalBytesRead > 0) {
                                                withContext(Dispatchers.IO) {
                                                    streamSource.skip(totalBytesRead)
                                                }
                                            }
                                            retrySuccess = true
                                            break
                                        } else {
                                            withContext(Dispatchers.IO) {
                                                fallbackResp.body?.close()
                                            }
                                        }
                                    } catch (_: Exception) {
                                        kotlinx.coroutines.delay(300L * attempt)
                                    }
                                }
                            }

                            if (!retrySuccess) {
                                AppLogger.e("Proxy:LocalStream", "Failed to transparently resume CDN stream.")
                                throw e
                            }
                        }
                    }
                } finally {
                    withContext(Dispatchers.IO) {
                        try {
                            currentResponse?.body?.close()
                        } catch (ignored: Exception) {
                            AppLogger.w("Proxy:LocalStream", "Failed to close response body: ${ignored.message}", ignored)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (!streamStarted) {
                withContext(Dispatchers.IO) {
                    try {
                        response.body?.close()
                    } catch (ignored: Exception) {
                        AppLogger.w("Proxy:LocalStream", "Failed to close response body on early error: ${ignored.message}", ignored)
                    }
                }
            }
            throw e
        }
    }
}
