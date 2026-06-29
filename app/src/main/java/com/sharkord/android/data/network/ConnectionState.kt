package com.sharkord.android.data.network

import com.sharkord.android.data.model.JoinServerData

// represents all possible states of the WebSocket connection lifecycle
// state transitions:
// ```
// disconnected → Connecting → Authenticating → HandshakePending
// → JoinPending → Connected
// any state → Error → Reconnecting → Connecting (loop)
// any state → Disconnected (explicit disconnect)
// ```
sealed class ConnectionState {
    // no active connection. Initial state or after explicit disconnect
    data object Disconnected : ConnectionState()

    // webSocket connection is being established
    data object Connecting : ConnectionState()

    // webSocket opened; sending auth token payload
    data object Authenticating : ConnectionState()

    // auth sent; waiting for handshake response from others.handshake
    data object HandshakePending : ConnectionState()

    // handshake received; sending joinServer query
    data class JoinPending(val handshakeHash: String) : ConnectionState()

    // fully connected and authenticated. Server data is available
    data class Connected(val serverData: JoinServerData) : ConnectionState()

    // connection failed or was lost
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : ConnectionState()

    // attempting to reconnect after a failure
    data class Reconnecting(
        val attempt: Int,
        val nextRetryMs: Long
    ) : ConnectionState()

    // whether the connection is in a usable (fully connected) state
    val isConnected: Boolean
        get() = this is Connected

    // whether the connection is actively trying to connect or reconnect
    val isInProgress: Boolean
        get() = this is Connecting || this is Authenticating ||
                this is HandshakePending || this is JoinPending ||
                this is Reconnecting
}
