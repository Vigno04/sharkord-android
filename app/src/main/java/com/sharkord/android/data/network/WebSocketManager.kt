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
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

// manages the tRPC WebSocket connection to the Sharkord server
// implements a proper state machine for connection lifecycle:
// disconnected → Connecting → Authenticating → HandshakePending → JoinPending → Connected
// after reaching [ConnectionState.Connected], all real-time tRPC subscriptions are
// automatically registered, mirroring what the web client does in `initSubscriptions()`
// incoming subscription events are emitted on [incomingEvents] and routed by path to
// [ServerEventHandler] for typed parsing
// features:
// - Response-based sequencing (no arbitrary delays)
// - Auto-incrementing tRPC message IDs
// - Exponential backoff reconnection
// - Auto-registration of all server-side real-time subscriptions
// - Connection state exposed as StateFlow for reactive UI updates
class WebSocketManager(
    private val okHttpClient: OkHttpClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private const val TAG = "WebSocketManager"

        // reconnection backoff parameters
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
    private var isPaused = false

    // track pending tRPC request IDs and their purpose
    private var handshakeId: Int? = null
    private var joinServerId: Int? = null

    // subscription tracking: tRPC id -> subscription path
    // internal visibility for documentation purposes (see TrpcProtocol comment on Success routing)
    internal val activeSubscriptions = mutableMapOf<Int, String>()

    // pending one-shot calls (query/mutation): tRPC id -> deferred result
    // callers suspend via sendQueryAwait / sendMutationAwait until the response arrives
    private val pendingCalls = mutableMapOf<Int, CompletableDeferred<JsonObject>>()

    // public State

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    // observe the current connection lifecycle state
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _serverData = MutableSharedFlow<JoinServerData>(replay = 1)
    // emits the JoinServerData when successfully connected
    val serverData: SharedFlow<JoinServerData> = _serverData.asSharedFlow()

    private val _incomingEvents = MutableSharedFlow<IncomingEvent>(extraBufferCapacity = 64)
    // emits real-time events from tRPC subscriptions
    val incomingEvents: SharedFlow<IncomingEvent> = _incomingEvents.asSharedFlow()

    // public API

    // initiates a WebSocket connection to the server
    // if already connected, disconnects first
    fun connect(serverUrl: String, token: String) {
        this.token = token
        this.serverUrl = serverUrl
        shouldReconnect = true
        reconnectJob?.cancel()

        doConnect()
    }

    // explicitly disconnects and stops reconnection attempts
    fun disconnect() {
        shouldReconnect = false
        isPaused = false
        reconnectJob?.cancel()
        reconnectJob = null
        activeSubscriptions.clear()
        cancelPendingCalls("WebSocket disconnected")

        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    fun pauseConnection() {
        if (!shouldReconnect) return
        isPaused = true
        Log.d(TAG, "Pausing connection (app in background)")
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "App paused")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    fun resumeConnection() {
        if (!isPaused || !shouldReconnect) return
        isPaused = false
        Log.d(TAG, "Resuming connection (app in foreground)")
        forceReconnect()
    }

    // forces an immediate reconnection attempt, bypassing any active backoff delay.
    // useful for lifecycle events (like the app returning to foreground).
    fun forceReconnect() {
        if (!shouldReconnect || serverUrl == null || token == null) return
        
        val currentState = _connectionState.value
        if (currentState.isConnected || currentState is ConnectionState.Connecting || 
            currentState is ConnectionState.Authenticating || currentState is ConnectionState.HandshakePending || 
            currentState is ConnectionState.JoinPending) {
            return // already connected or actively connecting
        }
        
        Log.d(TAG, "Forcing immediate reconnect...")
        reconnectJob?.cancel()
        reconnectJob = null
        doConnect()
    }

    // sends a tRPC query and returns the assigned message ID
    // the response will arrive via the WebSocket message handler
    fun sendQuery(path: String, input: com.google.gson.JsonElement = JsonObject()): Int {
        val id = TrpcProtocol.getNextId()
        val message = TrpcProtocol.buildQuery(id, path, input)
        webSocket?.send(message)
        Log.d(TAG, "Sent query [$id]: $path")
        return id
    }

    // sends a tRPC mutation and returns the assigned message ID
    fun sendMutation(path: String, input: com.google.gson.JsonElement): Int {
        val id = TrpcProtocol.getNextId()
        val message = TrpcProtocol.buildMutation(id, path, input)
        webSocket?.send(message)
        Log.d(TAG, "Sent mutation [$id]: $path")
        return id
    }

    // sends a tRPC query and suspends until the server responds
    // throws [WebSocketNotConnectedException] if the socket is null,
    // or a [TrpcCallException] if the server returns an error
    suspend fun sendQueryAwait(path: String, input: com.google.gson.JsonElement = JsonObject()): JsonObject {
        if (_connectionState.value !is ConnectionState.Connected) {
            try {
                withTimeout(15000) {
                    _connectionState.first { it is ConnectionState.Connected }
                }
            } catch (e: TimeoutCancellationException) {
                throw WebSocketNotConnectedException("WebSocket is not connected (timeout)")
            }
        }
        val id = TrpcProtocol.getNextId()
        val deferred = CompletableDeferred<JsonObject>()
        pendingCalls[id] = deferred
        val message = TrpcProtocol.buildQuery(id, path, input)
        val sent = webSocket?.send(message) ?: false
        if (!sent) {
            pendingCalls.remove(id)
            throw WebSocketNotConnectedException("WebSocket is not connected")
        }
        Log.d(TAG, "Sent query (awaited) [$id]: $path")
        return deferred.await()
    }

    // sends a tRPC mutation and suspends until the server responds
    // throws [WebSocketNotConnectedException] if the socket is null,
    // or a [TrpcCallException] if the server returns an error
    suspend fun sendMutationAwait(path: String, input: com.google.gson.JsonElement): JsonObject {
        if (_connectionState.value !is ConnectionState.Connected) {
            try {
                withTimeout(15000) {
                    _connectionState.first { it is ConnectionState.Connected }
                }
            } catch (e: TimeoutCancellationException) {
                throw WebSocketNotConnectedException("WebSocket is not connected (timeout)")
            }
        }
        val id = TrpcProtocol.getNextId()
        val deferred = CompletableDeferred<JsonObject>()
        pendingCalls[id] = deferred
        val message = TrpcProtocol.buildMutation(id, path, input)
        val sent = webSocket?.send(message) ?: false
        if (!sent) {
            pendingCalls.remove(id)
            throw WebSocketNotConnectedException("WebSocket is not connected")
        }
        Log.d(TAG, "Sent mutation (awaited) [$id]: $path")
        return deferred.await()
    }

    // subscribes to a tRPC subscription and returns the assigned message ID
    fun subscribe(path: String, input: com.google.gson.JsonElement? = null): Int {
        val id = TrpcProtocol.getNextId()
        val message = TrpcProtocol.buildSubscription(id, path, input)
        activeSubscriptions[id] = path
        webSocket?.send(message)
        Log.d(TAG, "Subscribed [$id]: $path")
        return id
    }

    // unsubscribes from a previously established subscription
    fun unsubscribe(id: Int) {
        if (activeSubscriptions.containsKey(id)) {
            val message = TrpcProtocol.buildUnsubscribe(id)
            webSocket?.send(message)
            activeSubscriptions.remove(id)
            Log.d(TAG, "Unsubscribed [$id]")
        }
    }

    // internal Connection Logic

    private fun doConnect() {
        if (isPaused) return
        _connectionState.value = ConnectionState.Connecting
        TrpcProtocol.resetIdCounter()
        handshakeId = null
        joinServerId = null

        // clear stale subscription IDs from any previous connection — IDs are reset above
        // via resetIdCounter(), so old entries would never match incoming messages anyway
        // and would accumulate as a memory leak across reconnects
        activeSubscriptions.clear()
        cancelPendingCalls("WebSocket reconnecting")

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

                // step 1: Send the auth token payload
                val authPayload = TrpcProtocol.buildAuthPayload(token ?: "")
                webSocket.send(authPayload)

                // step 2: Immediately send the handshake query
                // the server's tRPC connectionParams reads from the FIRST message,
                // and the handshake query arrives as a separate tRPC message
                // that is processed after auth context is established
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
                    is TrpcResponse.SubscriptionStarted -> {
                        Log.d(TAG, "Subscription started [${response.id}]: ${activeSubscriptions[response.id]}")
                    }
                    is TrpcResponse.SubscriptionComplete -> handleSubscriptionComplete(response)
                    is TrpcResponse.Ping -> {
                        Log.d(TAG, "Received tRPC ping, replying with pong")
                        if (response.id != null) {
                            webSocket.send("{\"id\":${response.id},\"method\":\"pong\"}")
                        } else {
                            webSocket.send("PONG")
                        }
                    }
                    is TrpcResponse.Pong -> {
                        Log.d(TAG, "Received tRPC pong")
                    }
                    is TrpcResponse.Unknown -> {
                        Log.d(TAG, "Ignoring unrecognized message")
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (isPaused) {
                    Log.d(TAG, "Ignoring WebSocket failure because app is paused: ${t.message}")
                    return
                }
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
                if (isPaused) {
                    Log.d(TAG, "Ignoring WebSocket closed because app is paused: $reason ($code)")
                    return
                }
                Log.d(TAG, "WebSocket closed: $reason ($code)")
                if (shouldReconnect && code != 1000) {
                    _connectionState.value = ConnectionState.Error("Connection closed: $reason")
                    scheduleReconnect()
                }
            }
        }
    }

    // message Handlers

    private fun handleSuccess(response: TrpcResponse.Success, webSocket: WebSocket) {
        when (response.id) {
            handshakeId -> onHandshakeResponse(response.data, webSocket)
            joinServerId -> onJoinServerResponse(response.data)
            else -> {
                // check if this is a pending one-shot call (query/mutation)
                val pending = pendingCalls.remove(response.id)
                if (pending != null) {
                    pending.complete(response.data)
                    return
                }

                // otherwise it's a subscription push event
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

            // step 3: Send joinServer with the handshake hash
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

            // step 4: Register all real-time subscriptions after connecting
            // this mirrors the web client's initSubscriptions() call after joinServer succeeds
            setupRealtimeSubscriptions()

            // reset reconnection state on successful connection
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

        // if this error is for the handshake or join, it's fatal for this connection
        if (response.id == handshakeId || response.id == joinServerId) {
            _connectionState.value = ConnectionState.Error(errorMsg)
            webSocket?.close(1000, "Auth error")
            // don't auto-reconnect on auth errors (e.g., invalid token)
            if (response.error.code == "UNAUTHORIZED" || response.error.code == "FORBIDDEN") {
                shouldReconnect = false
            } else {
                scheduleReconnect()
            }
            return
        }

        // reject any pending one-shot call that was waiting for this id
        val pending = response.id?.let { pendingCalls.remove(it) }
        if (pending != null) {
            pending.completeExceptionally(TrpcCallException(errorMsg, response.error.code))
        }
    }

    private fun handleSubscriptionComplete(response: TrpcResponse.SubscriptionComplete) {
        val subPath = activeSubscriptions.remove(response.id)
        Log.d(TAG, "Subscription completed [${response.id}]: $subPath")
    }

    // real-Time Subscriptions

    // registers all real-time tRPC subscriptions after a successful joinServer
    // mirrors the web client's `initSubscriptions()` which calls:
    // subscribeToChannels / subscribeToCategories / subscribeToUsers /
    // subscribeToRoles / subscribeToEmojis / subscribeToMessages / subscribeToServer
    // incoming events are emitted on [incomingEvents] and should be parsed by
    // [ServerEventHandler] to produce typed [ServerEvent] instances for the UI layer
    private fun setupRealtimeSubscriptions() {
        Log.d(TAG, "Registering real-time subscriptions...")

        // channels
        subscribe("channels.onCreate")
        subscribe("channels.onDelete")
        subscribe("channels.onUpdate")
        subscribe("channels.onReadStateUpdate")
        subscribe("channels.onReadStateDelta")

        // categories
        subscribe("categories.onCreate")
        subscribe("categories.onDelete")
        subscribe("categories.onUpdate")

        // users
        subscribe("users.onJoin")
        subscribe("users.onLeave")
        subscribe("users.onUpdate")
        subscribe("users.onCreate")
        subscribe("users.onDelete")

        // roles
        subscribe("roles.onCreate")
        subscribe("roles.onDelete")
        subscribe("roles.onUpdate")

        // emojis
        subscribe("emojis.onCreate")
        subscribe("emojis.onDelete")
        subscribe("emojis.onUpdate")

        // messages
        subscribe("messages.onNew")
        subscribe("messages.onUpdate")
        subscribe("messages.onDelete")
        subscribe("messages.onTyping")

        // voice
        subscribe("voice.onJoin")
        subscribe("voice.onLeave")
        subscribe("voice.onUpdateState")

        // server Settings
        subscribe("others.onServerSettingsUpdate")

        Log.d(TAG, "Registered ${activeSubscriptions.size} real-time subscriptions")
    }

    // reconnection

    private fun scheduleReconnect() {
        if (!shouldReconnect || isPaused) return
        if (reconnectJob?.isActive == true) return

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

                // wait for connection to either succeed or fail by observing the state flow
                // onJoinServerResponse() sets Connected and cancels this job;
                // onFailure/onClosed sets Error and loops to the next attempt
                val result = _connectionState.first {
                    it.isConnected || it is ConnectionState.Error
                }

                if (result.isConnected) break

                attempt++
                delay = (delay * BACKOFF_MULTIPLIER).toLong().coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }
    }

    // helpers

    private fun cancelPendingCalls(reason: String) {
        val exception = CancellationException(reason)
        pendingCalls.values.forEach { it.completeExceptionally(exception) }
        pendingCalls.clear()
    }
}

// represents an incoming real-time event from a tRPC subscription
data class IncomingEvent(
    // the tRPC subscription path (e.g., "channels.onCreate", "messages.onNew")
    val path: String,
    // the raw event data payload, to be parsed by [ServerEventHandler]
    val data: JsonObject
)

// thrown by [WebSocketManager.sendQueryAwait] / [WebSocketManager.sendMutationAwait]
// when the server responds with a tRPC error frame
class TrpcCallException(message: String, val code: String? = null) : Exception(message)

// thrown by [WebSocketManager.sendQueryAwait] / [WebSocketManager.sendMutationAwait]
// when the WebSocket is not currently open
class WebSocketNotConnectedException(message: String) : Exception(message)
