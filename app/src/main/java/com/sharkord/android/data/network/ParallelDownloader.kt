package com.sharkord.android.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.min

object ParallelDownloader {

    suspend fun downloadFile(
        client: OkHttpClient,
        url: String,
        destFile: File,
        minChunkSize: Long = 1024 * 1024, // 1MB minimum chunk size
        maxConcurrency: Int = 4
    ): Boolean = withContext(Dispatchers.IO) {
        val safeUrl = url.replace(" ", "%20")
        
        // try to get file size and check range support using a 0-0 byte GET request
        // head requests are not supported by server
        val checkRequest = Request.Builder()
            .url(safeUrl)
            .header("Range", "bytes=0-0")
            .build()

        val response = try {
            client.newCall(checkRequest).execute()
        } catch (e: Exception) {
            return@withContext downloadSequential(client, url, destFile)
        }

        if (!response.isSuccessful && response.code != 206) {
            response.close()
            return@withContext downloadSequential(client, url, destFile)
        }

        if (response.code == 200) {
            return@withContext try {
                response.body?.let { body ->
                    destFile.outputStream().use { out ->
                        body.byteStream().copyTo(out)
                    }
                    true
                } ?: false
            } catch (e: Exception) {
                false
            } finally {
                response.close()
            }
        }

        // response is 206 Partial Content
        var contentLength = -1L
        val contentRange = response.header("Content-Range")
        if (contentRange != null && contentRange.contains("/")) {
            contentLength = contentRange.substringAfter("/").toLongOrNull() ?: -1L
        }
        
        // close the check response
        response.close()

        if (contentLength <= 0 || contentLength <= minChunkSize) {
            // fallback to sequential download
            return@withContext downloadSequential(client, url, destFile)
        }

        // parallel download using HTTP Range
        val chunkSize = maxOf(minChunkSize, contentLength / maxConcurrency)
        val chunks = mutableListOf<Pair<Long, Long>>()
        var start = 0L
        while (start < contentLength) {
            val end = min(start + chunkSize - 1, contentLength - 1)
            chunks.add(start to end)
            start = end + 1
        }

        try {
            RandomAccessFile(destFile, "rw").use { raf ->
                raf.setLength(contentLength)
            }
        } catch (e: Exception) {
            return@withContext false
        }

        val deferreds = chunks.map { (chunkStart, chunkEnd) ->
            async {
                downloadChunk(client, url, destFile, chunkStart, chunkEnd)
            }
        }

        val results = deferreds.awaitAll()
        return@withContext results.all { it }
    }

    private fun downloadChunk(
        client: OkHttpClient,
        url: String,
        destFile: File,
        start: Long,
        end: Long
    ): Boolean {
        val safeUrl = url.replace(" ", "%20")
        val request = Request.Builder()
            .url(safeUrl)
            .header("Range", "bytes=$start-$end")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 206) {
                    val body = response.body
                    if (body != null) {
                        RandomAccessFile(destFile, "rw").use { raf ->
                            raf.seek(start)
                            val inputStream = body.byteStream()
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (inputStream.read(buffer).also { read = it } != -1) {
                                raf.write(buffer, 0, read)
                            }
                        }
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun downloadSequential(client: OkHttpClient, url: String, destFile: File): Boolean = withContext(Dispatchers.IO) {
        val safeUrl = url.replace(" ", "%20")
        val request = Request.Builder().url(safeUrl).build()
        return@withContext try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.let { body ->
                        destFile.outputStream().use { out ->
                            body.byteStream().copyTo(out)
                        }
                        true
                    } ?: false
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun downloadPartial(
        client: OkHttpClient,
        url: String,
        destFile: File,
        bytesToDownload: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val safeUrl = url.replace(" ", "%20")
        val request = Request.Builder()
            .url(safeUrl)
            .header("Range", "bytes=0-${bytesToDownload - 1}")
            .build()

        return@withContext try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 206) {
                    response.body?.let { body ->
                        destFile.outputStream().use { out ->
                            body.byteStream().copyTo(out)
                        }
                        true
                    } ?: false
                } else if (response.code == 416) {
                    // if the file is smaller than the requested range, some servers return 416
                    response.close()
                    downloadSequential(client, url, destFile)
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}
