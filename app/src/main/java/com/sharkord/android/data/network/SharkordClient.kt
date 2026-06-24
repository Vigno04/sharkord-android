package com.sharkord.android.data.network

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sharkord.android.data.session.SessionManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// lightweight facade that provides access to the Sharkord networking layer
// previously this was a monolithic 250+ line object containing all HTTP calls,
// webSocket management, tRPC protocol handling, and mutable global state
// now it's a thin coordinator that owns the shared OkHttpClient and provides
// access to the specialized components
// usage:
// ```
// sharkordClient.initialize(context)
// val info = SharkordClient.http.fetchServerInfo(url)
// sharkordClient.webSocket.connect(url, token)
// ```
object SharkordClient {

    // shared OkHttpClient instance with appropriate timeouts and keepalive
    // note: readTimeout and writeTimeout are set to 0 (disabled) to prevent the WebSocket
    // reader from timing out during prolonged idle periods
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    // HTTP REST API client for /login, /info, etc
    val http: SharkordHttpClient = SharkordHttpClient(okHttpClient)

    // webSocket manager for tRPC connection lifecycle
    val webSocket: WebSocketManager = WebSocketManager(okHttpClient)

    // voice engine managing mediasoup WebRTC pipeline
    lateinit var voiceEngine: VoiceEngine
        private set

    val isVoiceEngineInitialized: Boolean
        get() = this::voiceEngine.isInitialized

    // session/preferences manager. Initialized lazily via [initialize]
    lateinit var session: SessionManager
        private set

    // global application context, initialized lazily
    lateinit var applicationContext: Context
        private set

    // current server URL (kept in memory for convenience during a session)
    // this is the canonical source during an active session
    var currentServerUrl: String? = null

    // current auth token (kept in memory for the active session)
    var currentToken: String? = null

    // current server logo URL (derived from /info response + server URL)
    // exposed as a Compose reactive state so components automatically recompose when updated
    var currentServerLogoUrl: String? by mutableStateOf(null)

    // initializes the client with an Android Context (required for SessionManager)
    // call this once from Application.onCreate() or the first Activity
    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        session = SessionManager(applicationContext)
        voiceEngine = VoiceEngine(applicationContext, webSocket)
    }

    // clears all in-memory state. Call on logout
    fun clearState() {
        currentServerUrl = null
        currentToken = null
        currentServerLogoUrl = null
        webSocket.disconnect()
    }
}

