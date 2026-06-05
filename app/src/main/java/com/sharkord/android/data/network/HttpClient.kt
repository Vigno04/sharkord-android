package com.sharkord.android.data.network

import android.util.Log
import com.google.gson.Gson
import com.sharkord.android.data.model.LoginRequest
import com.sharkord.android.data.model.LoginResponse
import com.sharkord.android.data.model.ServerInfoResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Handles all HTTP REST API calls to the Sharkord server.
 *
 * Uses Kotlin coroutines instead of callbacks — callers get clean `Result<T>` returns.
 * No Android Context dependency; error mapping happens at the ViewModel layer.
 */
class SharkordHttpClient(private val client: OkHttpClient) {

    private val gson = Gson()

    companion object {
        private const val TAG = "SharkordHttpClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    // Public API

    /**
     * POST /login — authenticate with identity and password.
     *
     * @return [Result.success] with the JWT token string on success,
     *         [Result.failure] with a descriptive [SharkordApiException] on failure.
     */
    suspend fun login(serverUrl: String, identity: String, password: String): Result<String> {
        val cleanUrl = serverUrl.trimEnd('/')
        val requestUrl = "$cleanUrl/login"
        val requestBody = gson.toJson(LoginRequest(identity, password))

        val request = Request.Builder()
            .url(requestUrl)
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            val response = client.awaitCall(request)
            val body = response.body?.string()

            if (!response.isSuccessful || body == null) {
                // Try to parse validation errors from the server
                if (body != null) {
                    try {
                        val loginResponse = gson.fromJson(body, LoginResponse::class.java)
                        val errorMsg = loginResponse.error
                            ?: loginResponse.errors?.values?.firstOrNull()
                            ?: "Login failed (HTTP ${response.code})"
                        return Result.failure(SharkordApiException(errorMsg, response.code))
                    } catch (_: Exception) { /* fall through */ }
                }
                return Result.failure(SharkordApiException("Login failed (HTTP ${response.code})", response.code))
            }

            val loginResponse = gson.fromJson(body, LoginResponse::class.java)
            if (loginResponse.success && loginResponse.token != null) {
                Log.d(TAG, "Login successful, token obtained")
                Result.success(loginResponse.token)
            } else {
                val errorMsg = loginResponse.error
                    ?: loginResponse.errors?.values?.firstOrNull()
                    ?: "Login failed"
                Result.failure(SharkordApiException(errorMsg))
            }
        } catch (e: IOException) {
            Log.e(TAG, "Login network error: ${e.message}")
            Result.failure(SharkordApiException("Network error: ${e.message}", cause = e))
        } catch (e: Exception) {
            Log.e(TAG, "Login error", e)
            Result.failure(SharkordApiException("Unexpected error: ${e.message}", cause = e))
        }
    }

    /**
     * GET /info — fetch server metadata (name, description, logo, version).
     */
    suspend fun fetchServerInfo(serverUrl: String): Result<ServerInfoResponse> {
        val cleanUrl = serverUrl.trimEnd('/')
        val requestUrl = "$cleanUrl/info"

        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .build()

        return try {
            val response = client.awaitCall(request)
            val body = response.body?.string()

            if (!response.isSuccessful || body == null) {
                return Result.failure(
                    SharkordApiException("Failed to fetch server info (HTTP ${response.code})", response.code)
                )
            }

            val serverInfo = gson.fromJson(body, ServerInfoResponse::class.java)
            Log.d(TAG, "Server info fetched: name=${serverInfo.name}, logo=${serverInfo.logo}")
            Result.success(serverInfo)
        } catch (e: IOException) {
            Log.e(TAG, "Server info network error: ${e.message}")
            Result.failure(SharkordApiException("Network error: ${e.message}", cause = e))
        } catch (e: Exception) {
            Log.e(TAG, "Server info error", e)
            Result.failure(SharkordApiException("Unexpected error: ${e.message}", cause = e))
        }
    }

    /**
     * POST /upload — upload a raw file attachment.
     */
    suspend fun uploadFile(
        serverUrl: String,
        token: String,
        originalName: String,
        fileBytes: ByteArray
    ): Result<com.sharkord.android.data.model.FileInfo> {
        val cleanUrl = serverUrl.trimEnd('/')
        val requestUrl = "$cleanUrl/upload"

        val mediaType = "application/octet-stream".toMediaType()
        val requestBody = fileBytes.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(requestUrl)
            .post(requestBody)
            .addHeader("x-token", token)
            .addHeader("x-file-name", originalName)
            .build()

        return try {
            val response = client.awaitCall(request)
            val body = response.body?.string()

            if (!response.isSuccessful || body == null) {
                return Result.failure(
                    SharkordApiException("File upload failed (HTTP ${response.code})", response.code)
                )
            }

            val fileInfo = gson.fromJson(body, com.sharkord.android.data.model.FileInfo::class.java)
            Log.d(TAG, "File uploaded successfully: id=${fileInfo.id}, name=${fileInfo.name}")
            Result.success(fileInfo)
        } catch (e: IOException) {
            Log.e(TAG, "File upload network error: ${e.message}")
            Result.failure(SharkordApiException("Network error: ${e.message}", cause = e))
        } catch (e: Exception) {
            Log.e(TAG, "File upload error", e)
            Result.failure(SharkordApiException("Unexpected error: ${e.message}", cause = e))
        }
    }

    // Internal Utilities

    /**
     * Bridge from OkHttp's callback-based API to Kotlin coroutines.
     */
    private suspend fun OkHttpClient.awaitCall(request: Request): Response {
        return suspendCancellableCoroutine { continuation ->
            val call = this.newCall(request)

            continuation.invokeOnCancellation {
                call.cancel()
            }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) {
                        continuation.resume(response)
                    }
                }
            })
        }
    }
}

/**
 * Exception type for Sharkord API errors, carrying an optional HTTP status code.
 */
class SharkordApiException(
    message: String,
    val httpCode: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)
