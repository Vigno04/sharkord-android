package com.sharkord.android.data.network

import android.content.Context
import com.sharkord.android.data.session.SessionManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Lightweight facade that provides access to the Sharkord networking layer.
 *
 * Previously this was a monolithic 250+ line object containing all HTTP calls,
 * WebSocket management, tRPC protocol handling, and mutable global state.
 * Now it's a thin coordinator that owns the shared OkHttpClient and provides
 * access to the specialized components.
 *
 * Usage:
 * ```
 * SharkordClient.initialize(context)
 * val info = SharkordClient.http.fetchServerInfo(url)
 * SharkordClient.webSocket.connect(url, token)
 * ```
 */
object SharkordClient {

    /** Shared OkHttpClient instance with appropriate timeouts and keepalive. */
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /** HTTP REST API client for /login, /info, etc. */
    val http: SharkordHttpClient = SharkordHttpClient(okHttpClient)

    /** WebSocket manager for tRPC connection lifecycle. */
    val webSocket: WebSocketManager = WebSocketManager(okHttpClient)

    /** Session/preferences manager. Initialized lazily via [initialize]. */
    lateinit var session: SessionManager
        private set

    /**
     * Current server URL (kept in memory for convenience during a session).
     * This is the canonical source during an active session.
     */
    var currentServerUrl: String? = null

    /**
     * Current auth token (kept in memory for the active session).
     */
    var currentToken: String? = null

    /**
     * Current server logo URL (derived from /info response + server URL).
     */
    var currentServerLogoUrl: String? = null

    /**
     * Initializes the client with an Android Context (required for SessionManager).
     * Call this once from Application.onCreate() or the first Activity.
     */
    fun initialize(context: Context) {
        session = SessionManager(context.applicationContext)
    }

    /**
     * Clears all in-memory state. Call on logout.
     */
    fun clearState() {
        currentServerUrl = null
        currentToken = null
        currentServerLogoUrl = null
        webSocket.disconnect()
    }
}
