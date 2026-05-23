package com.sharkord.android.data.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sharkord.android.R
import com.sharkord.android.data.model.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object SharkordClient {
    private const val TAG = "SharkordClient"
    internal val client = OkHttpClient.Builder()
        .pingInterval(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    var currentToken: String? = null
    var currentServerUrl: String? = null
    var activeWebSocket: WebSocket? = null

    // Performs HTTP login
    fun login(
        context: Context,
        serverUrl: String,
        identity: String,
        password: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanUrl = serverUrl.trimEnd('/')
        val requestUrl = "$cleanUrl/login"
        val requestBody = gson.toJson(LoginRequest(identity, password))

        val request = Request.Builder()
            .url(requestUrl)
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val errorMsg = e.message ?: context.getString(R.string.disconnected_lostConnectionMessage)
                Log.e(TAG, "Login failed: $errorMsg")
                runOnMain { onError(errorMsg) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    runOnMain { onError(context.getString(R.string.connect_connectError, context.getString(R.string.settings_errorBadge))) }
                    return
                }

                try {
                    val loginResponse = gson.fromJson(body, LoginResponse::class.java)
                    if (loginResponse.success && loginResponse.token != null) {
                        currentToken = loginResponse.token
                        currentServerUrl = cleanUrl
                        Log.d(TAG, "Login successful, token obtained")
                        runOnMain { onSuccess(loginResponse.token) }
                    } else {
                        val errMsg = loginResponse.error ?: context.getString(R.string.connect_connectError, context.getString(R.string.settings_errorBadge))
                        runOnMain { onError(errMsg) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing login response", e)
                    runOnMain { onError(context.getString(R.string.connect_connectError, context.getString(R.string.settings_errorBadge))) }
                }
            }
        })
    }

    // Fetches server metadata and logo
    fun fetchServerInfo(
        context: Context,
        serverUrl: String,
        onSuccess: (ServerInfoResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanUrl = serverUrl.trimEnd('/')
        val requestUrl = "$cleanUrl/info"

        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val errorMsg = e.message ?: context.getString(R.string.disconnected_lostConnectionMessage)
                Log.e(TAG, "Failed to fetch server info: $errorMsg")
                runOnMain { onError(errorMsg) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    runOnMain { onError(context.getString(R.string.connect_connectError, context.getString(R.string.settings_errorBadge))) }
                    return
                }

                try {
                    val serverInfo = gson.fromJson(body, ServerInfoResponse::class.java)
                    Log.d(TAG, "Server info fetched successfully")
                    runOnMain { onSuccess(serverInfo) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing server info response", e)
                    runOnMain { onError(context.getString(R.string.connect_connectError, context.getString(R.string.settings_errorBadge))) }
                }
            }
        })
    }

    // Connects via WebSocket and retrieves user and channel information
    fun fetchServerData(
        context: Context,
        onSuccess: (JoinServerData) -> Unit,
        onError: (String) -> Unit
    ) {
        val serverUrl = currentServerUrl
        if (serverUrl == null) {
            onError(context.getString(R.string.disconnected_lostConnectionMessage))
            return
        }
        val token = currentToken
        if (token == null) {
            onError(context.getString(R.string.disconnected_lostConnectionMessage))
            return
        }

        // Converts the URL to WebSocket by adding ?connectionParams=1 for tRPC
        val wsUrl = if (serverUrl.startsWith("https://")) {
            serverUrl.replace("https://", "wss://") + "?connectionParams=1"
        } else if (serverUrl.startsWith("http://")) {
            serverUrl.replace("http://", "ws://") + "?connectionParams=1"
        } else {
            "ws://$serverUrl?connectionParams=1"
        }

        Log.d(TAG, "Connecting to WebSocket: $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            private var handshakeSent = false
            private var joinSent = false
            private var handshakeHash: String? = null

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened. Sending authentication...")
                
                // 1. Sends the payload with the token. With ?connectionParams=1 tRPC will read "data" and use the token.
                // In addition, this also satisfies the custom ws.once('message') listener
                val authPayload = JsonObject().apply {
                    add("data", JsonObject().apply {
                        addProperty("token", token)
                    })
                }
                webSocket.send(gson.toJson(authPayload))

                // Wait a moment and perform the tRPC Handshake
                mainHandler.postDelayed({
                    Log.d(TAG, "Sending tRPC handshake query...")
                    val handshakeQuery = JsonObject().apply {
                        addProperty("id", 1)
                        addProperty("method", "query")
                        add("params", JsonObject().apply {
                            addProperty("path", "others.handshake")
                            add("input", JsonObject())
                        })
                    }
                    webSocket.send(gson.toJson(handshakeQuery))
                    handshakeSent = true
                }, 300)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WS Received: $text")
                try {
                    val root = JsonParser.parseString(text).asJsonObject
                    if (root.has("id")) {
                        val id = root.get("id").asInt
                        
                        // Handle Handshake Response (id = 1)
                        if (id == 1 && !joinSent) {
                            val result = root.getAsJsonObject("result")
                            val data = result.getAsJsonObject("data")
                            handshakeHash = data.get("handshakeHash").asString
                            
                            Log.d(TAG, "Handshake successful. Hash: $handshakeHash. Sending joinServer...")

                            // 2. Sends tRPC query joinServer with the obtained handshakeHash
                            val joinQuery = JsonObject().apply {
                                addProperty("id", 2)
                                addProperty("method", "query")
                                add("params", JsonObject().apply {
                                    addProperty("path", "others.joinServer")
                                    add("input", JsonObject().apply {
                                        addProperty("handshakeHash", handshakeHash)
                                    })
                                })
                            }
                            webSocket.send(gson.toJson(joinQuery))
                            joinSent = true
                        }
                        // Handle JoinServer Response (id = 2)
                        else if (id == 2) {
                            val result = root.getAsJsonObject("result")
                            val dataJson = result.getAsJsonObject("data")
                            
                            val joinData = gson.fromJson(dataJson, JoinServerData::class.java)
                            Log.d(TAG, "JoinServer success. Server Name: ${joinData.serverName}")
                            
                            runOnMain { onSuccess(joinData) }
                            // Keep the connection active to stay online
                            Log.d(TAG, "WebSocket kept open for real-time operations")
                        }
                    } else if (root.has("error")) {
                        val errorJson = root.getAsJsonObject("error")
                        val message = errorJson.get("message")?.asString ?: context.getString(R.string.disconnected_lostConnectionMessage)
                        runOnMain { onError(message) }
                        webSocket.close(1000, "Error occurred")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in onMessage logic", e)
                    runOnMain { onError(context.getString(R.string.disconnected_lostConnectionMessage)) }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val errorMsg = t.message ?: context.getString(R.string.disconnected_lostConnectionMessage)
                Log.e(TAG, "WebSocket failure", t)
                runOnMain { onError(errorMsg) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason ($code)")
            }
        })
    }

    private fun runOnMain(action: () -> Unit) {
        mainHandler.post(action)
    }
}
