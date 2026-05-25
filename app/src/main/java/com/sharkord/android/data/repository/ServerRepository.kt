package com.sharkord.android.data.repository

import android.util.Log
import com.sharkord.android.data.model.JoinServerData
import com.sharkord.android.data.model.ServerInfoResponse
import com.sharkord.android.data.network.ConnectionState
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.data.network.IncomingEvent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository layer that coordinates HTTP and WebSocket operations
 * and exposes reactive state for the UI layer.
 *
 * This is the single source of truth for server connectivity state,
 * replacing the scattered state management that was previously in
 * HomeScreen, LoginViewModel, and SharkordClient.
 */
class ServerRepository {

    companion object {
        private const val TAG = "ServerRepository"
    }

    private val http get() = SharkordClient.http
    private val webSocket get() = SharkordClient.webSocket
    private val session get() = SharkordClient.session

    // ─── Reactive State ───────────────────────────────────────

    /** Current WebSocket connection state. */
    val connectionState: StateFlow<ConnectionState>
        get() = webSocket.connectionState

    /** Emits JoinServerData when successfully connected. */
    val serverData: SharedFlow<JoinServerData>
        get() = webSocket.serverData

    /** Emits incoming real-time events from tRPC subscriptions. */
    val incomingEvents: SharedFlow<IncomingEvent>
        get() = webSocket.incomingEvents

    // ─── HTTP Operations ──────────────────────────────────────

    /**
     * Fetches server info (name, description, logo, version) from GET /info.
     * Does NOT modify session state.
     */
    suspend fun fetchServerInfo(serverUrl: String): Result<ServerInfoResponse> {
        val cleanUrl = serverUrl.trimEnd('/')
        val result = http.fetchServerInfo(cleanUrl)

        result.onSuccess { info ->
            SharkordClient.currentServerUrl = cleanUrl
            SharkordClient.currentServerLogoUrl = info.logo?.name?.let { "$cleanUrl/public/$it" }
        }

        return result
    }

    /**
     * Authenticates with the server via POST /login.
     * On success, stores the token and server URL in session (if auto-login is enabled).
     *
     * @return The JWT token on success.
     */
    suspend fun login(
        serverUrl: String,
        identity: String,
        password: String,
        autoLogin: Boolean
    ): Result<String> {
        val cleanUrl = serverUrl.trimEnd('/')
        val result = http.login(cleanUrl, identity, password)

        result.onSuccess { token ->
            SharkordClient.currentToken = token
            SharkordClient.currentServerUrl = cleanUrl
            session.saveSession(cleanUrl, token, autoLogin)
            Log.d(TAG, "Login successful, session saved (autoLogin=$autoLogin)")
        }

        return result
    }

    // ─── WebSocket Operations ─────────────────────────────────

    /**
     * Connects to the server via WebSocket and performs the full tRPC handshake + joinServer flow.
     * Uses the currently stored token and server URL.
     *
     * @return true if connection was initiated, false if missing token/url.
     */
    fun connectWebSocket(): Boolean {
        val url = SharkordClient.currentServerUrl
        val token = SharkordClient.currentToken

        if (url.isNullOrBlank() || token.isNullOrBlank()) {
            Log.w(TAG, "Cannot connect: missing serverUrl or token")
            return false
        }

        webSocket.connect(url, token)
        return true
    }

    /**
     * Disconnects the WebSocket and clears session state.
     * Used for explicit logout.
     */
    fun logout() {
        SharkordClient.clearState()
        session.clearSession()
        Log.d(TAG, "Logged out, session cleared")
    }

    /**
     * Disconnects the WebSocket without clearing session (e.g., app going to background).
     */
    fun disconnectWebSocket() {
        webSocket.disconnect()
    }

    // ─── Session Queries ──────────────────────────────────────

    /**
     * Attempts to restore a saved session. Returns true if credentials exist
     * and the session was restored to SharkordClient's in-memory state.
     */
    fun restoreSession(): Boolean {
        if (!session.hasValidSession()) return false

        val url = session.serverUrl ?: return false
        val token = session.token ?: return false

        SharkordClient.currentServerUrl = url
        SharkordClient.currentToken = token

        Log.d(TAG, "Session restored from preferences (url=$url)")
        return true
    }

    /**
     * Saves only the server URL to preferences (when user enters URL before login).
     */
    fun saveServerUrl(serverUrl: String) {
        val cleanUrl = serverUrl.trimEnd('/')
        session.saveServerUrl(cleanUrl)
        SharkordClient.currentServerUrl = cleanUrl
    }

    /**
     * Returns the saved server URL from preferences (for pre-filling the URL field).
     */
    fun getSavedServerUrl(): String? = session.serverUrl

    /**
     * Returns whether auto-login is enabled in preferences.
     */
    fun isAutoLoginEnabled(): Boolean = session.autoLogin
}
