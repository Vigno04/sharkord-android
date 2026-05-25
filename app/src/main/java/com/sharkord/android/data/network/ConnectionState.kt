package com.sharkord.android.data.network

import com.sharkord.android.data.model.JoinServerData

/**
 * Represents all possible states of the WebSocket connection lifecycle.
 *
 * State transitions:
 * ```
 * Disconnected → Connecting → Authenticating → HandshakePending
 *     → JoinPending → Connected
 *
 * Any state → Error → Reconnecting → Connecting (loop)
 * Any state → Disconnected (explicit disconnect)
 * ```
 */
sealed class ConnectionState {
    /** No active connection. Initial state or after explicit disconnect. */
    data object Disconnected : ConnectionState()

    /** WebSocket connection is being established. */
    data object Connecting : ConnectionState()

    /** WebSocket opened; sending auth token payload. */
    data object Authenticating : ConnectionState()

    /** Auth sent; waiting for handshake response from others.handshake. */
    data object HandshakePending : ConnectionState()

    /** Handshake received; sending joinServer query. */
    data class JoinPending(val handshakeHash: String) : ConnectionState()

    /** Fully connected and authenticated. Server data is available. */
    data class Connected(val serverData: JoinServerData) : ConnectionState()

    /** Connection failed or was lost. */
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : ConnectionState()

    /** Attempting to reconnect after a failure. */
    data class Reconnecting(
        val attempt: Int,
        val nextRetryMs: Long
    ) : ConnectionState()

    /** Whether the connection is in a usable (fully connected) state. */
    val isConnected: Boolean
        get() = this is Connected

    /** Whether the connection is actively trying to connect or reconnect. */
    val isInProgress: Boolean
        get() = this is Connecting || this is Authenticating ||
                this is HandshakePending || this is JoinPending ||
                this is Reconnecting
}
