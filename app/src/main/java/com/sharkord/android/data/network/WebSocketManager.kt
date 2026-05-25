package com.sharkord.android.data.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sharkord.android.data.model.HandshakeResponse
import com.sharkord.android.data.model.JoinServerData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Manages the tRPC WebSocket connection to the Sharkord server.
 *
 * Implements a proper state machine for connection lifecycle:
 * Disconnected → Connecting → Authenticating → HandshakePending → JoinPending → Connected
 *
 * Features:
 * - Response-based sequencing (no arbitrary delays)
 * - Auto-incrementing tRPC message IDs
 * - Exponential backoff reconnection
 * - Connection state exposed as StateFlow for reactive UI updates
 * - tRPC subscription support infrastructure
 */
class WebSocketManager(
    private val okHttpClient: OkHttpClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private const val TAG = "WebSocketManager"

        // Reconnection backoff parameters
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val MAX_RETRY_DELAY_MS = 30_000L
        private const val BACKOFF_MULTIPLIER = 2.0
    }

    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private var token: String? = null
    private var serverUrl: String? = null
    private var reconnectJob: Job? = null
    private var shouldReconnect = false

    // Track pending tRPC request IDs and their purpose
    private var handshakeId: Int? = null
    private var joinServerId: Int? = null

    // Subscription tracking: tRPC id -> subscription path
    private val activeSubscriptions = mutableMapOf<Int, String>()

    // ─── Public State ─────────────────────────────────────────

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    /** Observe the current connection lifecycle state. */
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _serverData = MutableSharedFlow<JoinServerData>(replay = 1)
    /** Emits the JoinServerData when successfully connected. */
    val serverData: SharedFlow<JoinServerData> = _serverData.asSharedFlow()

    private val _incomingEvents = MutableSharedFlow<IncomingEvent>(extraBufferCapacity = 64)
    /** Emits real-time events from tRPC subscriptions. */
    val incomingEvents: SharedFlow<IncomingEvent> = _incomingEvents.asSharedFlow()

    // ─── Public API ───────────────────────────────────────────

    /**
     * Initiates a WebSocket connection to the server.
     * If already connected, disconnects first.
     */
    fun connect(serverUrl: String, token: String) {
        this.token = token
        this.serverUrl = serverUrl
        shouldReconnect = true
        reconnectJob?.cancel()

        doConnect()
    }

    /**
     * Explicitly disconnects and stops reconnection attempts.
     */
    fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        reconnectJob = null
        activeSubscriptions.clear()

        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Sends a tRPC query and returns the assigned message ID.
     * The response will arrive via the WebSocket message handler.
     */
    fun sendQuery(path: String, input: JsonObject = JsonObject()): Int {
        val id = TrpcProtocol.getNextId()
        val message = TrpcProtocol.buildQuery(id, path, input)
        webSocket?.send(message)
        Log.d(TAG, "Sent query [$id]: $path")
        return id
    }

    /**
     * Sends a tRPC mutation and returns the assigned message ID.
     */
    fun sendMutation(path: String, input: JsonObject): Int {
        val id = TrpcProtocol.getNextId()
        val message = TrpcProtocol.buildMutation(id, path, input)
        webSocket?.send(message)
        Log.d(TAG, "Sent mutation [$id]: $path")
        return id
    }

    /**
     * Subscribes to a tRPC subscription and returns the assigned message ID.
     */
    fun subscribe(path: String, input: JsonObject? = null): Int {
        val id = TrpcProtocol.getNextId()
        val message = TrpcProtocol.buildSubscription(id, path, input)
        activeSubscriptions[id] = path
        webSocket?.send(message)
        Log.d(TAG, "Subscribed [$id]: $path")
        return id
    }

    // ─── Internal Connection Logic ────────────────────────────

    private fun doConnect() {
        _connectionState.value = ConnectionState.Connecting
        TrpcProtocol.resetIdCounter()
        handshakeId = null
        joinServerId = null

        val url = serverUrl ?: return
        val wsUrl = buildWsUrl(url)

        Log.d(TAG, "Connecting to WebSocket: $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, createWebSocketListener())
    }

    private fun buildWsUrl(serverUrl: String): String {
        val cleanUrl = serverUrl.trimEnd('/')
        val wsUrl = when {
            cleanUrl.startsWith("https://") -> cleanUrl.replace("https://", "wss://")
            cleanUrl.startsWith("http://") -> cleanUrl.replace("http://", "ws://")
            else -> "ws://$cleanUrl"
        }
        return "$wsUrl?connectionParams=1"
    }

    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened. Sending authentication...")
                _connectionState.value = ConnectionState.Authenticating

                // Step 1: Send the auth token payload
                val authPayload = TrpcProtocol.buildAuthPayload(token ?: "")
                webSocket.send(authPayload)

                // Step 2: Immediately send the handshake query (no delay needed!)
                // The server's tRPC connectionParams reads from the FIRST message,
                // and the handshake query arrives as a separate tRPC message
                // that is processed after auth context is established.
                _connectionState.value = ConnectionState.HandshakePending

                handshakeId = TrpcProtocol.getNextId()
                val handshakeQuery = TrpcProtocol.buildQuery(
                    handshakeId!!,
                    "others.handshake"
                )
                webSocket.send(handshakeQuery)
                Log.d(TAG, "Sent handshake query [${handshakeId}]")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WS Received: $text")

                when (val response = TrpcProtocol.parseResponse(text)) {
                    is TrpcResponse.Success -> handleSuccess(response, webSocket)
                    is TrpcResponse.Error -> handleError(response)
                    is TrpcResponse.SubscriptionData -> handleSubscriptionData(response)
                    is TrpcResponse.SubscriptionComplete -> handleSubscriptionComplete(response)
                    is TrpcResponse.Unknown -> {
                        Log.d(TAG, "Ignoring unrecognized message")
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                _connectionState.value = ConnectionState.Error(
                    t.message ?: "Connection failed",
                    t
                )
                scheduleReconnect()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $reason ($code)")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason ($code)")
                if (shouldReconnect && code != 1000) {
                    _connectionState.value = ConnectionState.Error("Connection closed: $reason")
                    scheduleReconnect()
                }
            }
        }
    }

    // ─── Message Handlers ─────────────────────────────────────

    private fun handleSuccess(response: TrpcResponse.Success, webSocket: WebSocket) {
        when (response.id) {
            handshakeId -> onHandshakeResponse(response.data, webSocket)
            joinServerId -> onJoinServerResponse(response.data)
            else -> {
                // Check if it's a subscription event
                val subPath = activeSubscriptions[response.id]
                if (subPath != null) {
                    scope.launch {
                        _incomingEvents.emit(IncomingEvent(subPath, response.data))
                    }
                } else {
                    Log.d(TAG, "Received response for unknown id ${response.id}")
                }
            }
        }
    }

    private fun onHandshakeResponse(data: JsonObject, webSocket: WebSocket) {
        try {
            val handshake = gson.fromJson(data, HandshakeResponse::class.java)
            Log.d(TAG, "Handshake OK. Hash: ${handshake.handshakeHash}, hasPassword: ${handshake.hasPassword}")

            _connectionState.value = ConnectionState.JoinPending(handshake.handshakeHash)

            // Step 3: Send joinServer with the handshake hash
            joinServerId = TrpcProtocol.getNextId()
            val input = JsonObject().apply {
                addProperty("handshakeHash", handshake.handshakeHash)
            }
            val joinQuery = TrpcProtocol.buildQuery(joinServerId!!, "others.joinServer", input)
            webSocket.send(joinQuery)
            Log.d(TAG, "Sent joinServer query [${joinServerId}]")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process handshake response", e)
            _connectionState.value = ConnectionState.Error("Handshake failed: ${e.message}", e)
            scheduleReconnect()
        }
    }

    private fun onJoinServerResponse(data: JsonObject) {
        try {
            val joinData = gson.fromJson(data, JoinServerData::class.java)
            Log.d(TAG, "JoinServer OK. Server: ${joinData.serverName}, Channels: ${joinData.channels.size}")

            _connectionState.value = ConnectionState.Connected(joinData)

            scope.launch {
                _serverData.emit(joinData)
            }

            // Reset reconnection state on successful connection
            reconnectJob?.cancel()

            Log.d(TAG, "WebSocket kept open for real-time operations")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process joinServer response", e)
            _connectionState.value = ConnectionState.Error("Join failed: ${e.message}", e)
            scheduleReconnect()
        }
    }

    private fun handleError(response: TrpcResponse.Error) {
        val errorMsg = response.error.message
        Log.e(TAG, "tRPC error [${response.id}]: $errorMsg (code: ${response.error.code})")

        // If this error is for the handshake or join, it's fatal for this connection
        if (response.id == handshakeId || response.id == joinServerId) {
            _connectionState.value = ConnectionState.Error(errorMsg)
            webSocket?.close(1000, "Auth error")
            // Don't auto-reconnect on auth errors (e.g., invalid token)
            if (response.error.code == "UNAUTHORIZED" || response.error.code == "FORBIDDEN") {
                shouldReconnect = false
            } else {
                scheduleReconnect()
            }
        }
    }

    private fun handleSubscriptionData(response: TrpcResponse.SubscriptionData) {
        val subPath = activeSubscriptions[response.id]
        if (subPath != null) {
            scope.launch {
                _incomingEvents.emit(IncomingEvent(subPath, response.data))
            }
        }
    }

    private fun handleSubscriptionComplete(response: TrpcResponse.SubscriptionComplete) {
        val subPath = activeSubscriptions.remove(response.id)
        Log.d(TAG, "Subscription completed [${ response.id}]: $subPath")
    }

    // ─── Reconnection ─────────────────────────────────────────

    private fun scheduleReconnect() {
        if (!shouldReconnect) return

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var attempt = 1
            var delay = INITIAL_RETRY_DELAY_MS

            while (isActive && shouldReconnect) {
                _connectionState.value = ConnectionState.Reconnecting(attempt, delay)
                Log.d(TAG, "Reconnecting in ${delay}ms (attempt $attempt)")

                delay(delay)

                if (!shouldReconnect) break

                Log.d(TAG, "Reconnect attempt $attempt...")
                doConnect()

                // Wait for connection to either succeed or fail
                // If it succeeds, the reconnect loop will be cancelled by onJoinServerResponse
                // If it fails, onFailure/onClosed will set shouldReconnect and we loop again
                delay(2000) // Give the connection attempt time to complete

                if (_connectionState.value.isConnected) {
                    break
                }

                attempt++
                delay = (delay * BACKOFF_MULTIPLIER).toLong().coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }
    }
}

/**
 * Represents an incoming real-time event from a tRPC subscription.
 */
data class IncomingEvent(
    /** The tRPC subscription path (e.g., "messages.onNew") */
    val path: String,
    /** The event data payload */
    val data: JsonObject
)
