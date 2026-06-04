package com.sharkord.android.data.repository

import android.util.Log
import com.sharkord.android.data.model.JoinServerData
import com.sharkord.android.data.model.Invite
import com.sharkord.android.data.model.ServerInfoResponse
import com.sharkord.android.data.network.ConnectionState
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.data.network.IncomingEvent
import com.google.gson.JsonObject
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

    // Reactive State

    /** Current WebSocket connection state. */
    val connectionState: StateFlow<ConnectionState>
        get() = webSocket.connectionState

    /** Emits JoinServerData when successfully connected. */
    val serverData: SharedFlow<JoinServerData>
        get() = webSocket.serverData

    /** Emits incoming real-time events from tRPC subscriptions. */
    val incomingEvents: SharedFlow<IncomingEvent>
        get() = webSocket.incomingEvents

    // HTTP Operations

    /**
     * Fetches server info (name, description, logo, version) from GET /info.
     * Does NOT modify session state.
     */
    suspend fun fetchServerInfo(serverUrl: String): Result<ServerInfoResponse> {
        val cleanUrl = serverUrl.trimEnd('/')
        val result = http.fetchServerInfo(cleanUrl)

        result.onSuccess { info ->
            val logoUrl = info.logo?.name?.let { name ->
                val encodedName = android.net.Uri.encode(name)
                "$cleanUrl/public/$encodedName"
            }
            Log.d("ServerRepository", "fetchServerInfo success: name=${info.name}, logoUrl=$logoUrl")
            SharkordClient.currentServerUrl = cleanUrl
            SharkordClient.currentServerLogoUrl = logoUrl
            session.saveServerLogoUrl(logoUrl)
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
            session.saveSession(cleanUrl, token, SharkordClient.currentServerLogoUrl, autoLogin)
            Log.d(TAG, "Login successful, session saved (autoLogin=$autoLogin)")
        }

        return result
    }

    // WebSocket Operations

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

    // Session Queries

    /**
     * Attempts to restore a saved session. Returns true if credentials exist
     * and the session was restored to SharkordClient's in-memory state.
     */
    fun restoreSession(): Boolean {
        if (!session.hasValidSession()) return false

        val url = session.serverUrl ?: return false
        val token = session.token ?: return false
        val logoUrl = session.serverLogoUrl

        SharkordClient.currentServerUrl = url
        SharkordClient.currentToken = token
        SharkordClient.currentServerLogoUrl = logoUrl

        Log.d(TAG, "Session restored from preferences (url=$url, logoUrl=$logoUrl)")
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

    fun isAutoLoginEnabled(): Boolean = session.autoLogin

    /**
     * Opens a direct message channel with the given user.
     */
    suspend fun openDirectMessage(userId: Int): Result<Int> {
        return try {
            val input = JsonObject().apply {
                addProperty("userId", userId)
            }
            val response = webSocket.sendMutationAwait("dms.open", input)
            val channelId = response.get("channelId").asInt
            Result.success(channelId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open DM", e)
            Result.failure(e)
        }
    }

    // Server Administration (Settings & Roles)

    suspend fun getAdminSettings(): Result<com.sharkord.android.data.model.AdminSettings> {
        return try {
            val response = webSocket.sendQueryAwait("others.getSettings", com.google.gson.JsonObject())
            val gson = com.google.gson.Gson()
            val parsed = gson.fromJson(response, com.sharkord.android.data.model.AdminSettings::class.java)
            Result.success(parsed)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get admin settings", e)
            Result.failure(e)
        }
    }

    suspend fun updateServerSettings(settings: com.sharkord.android.data.model.AdminSettings): Result<Unit> {
        return try {
            val input = JsonObject().apply {
                addProperty("name", settings.name)
                if (settings.description != null) {
                    addProperty("description", settings.description)
                } else {
                    add("description", com.google.gson.JsonNull.INSTANCE)
                }
                if (settings.password != null) {
                    addProperty("password", settings.password)
                } else {
                    add("password", com.google.gson.JsonNull.INSTANCE)
                }
                addProperty("onlyAskForPasswordOnFirstJoin", settings.onlyAskForPasswordOnFirstJoin)
                addProperty("allowNewUsers", settings.allowNewUsers)
                addProperty("directMessagesEnabled", settings.directMessagesEnabled)
                addProperty("enablePlugins", settings.enablePlugins)
                addProperty("webRtcSimulcastEnabled", settings.webRtcSimulcastEnabled)
                addProperty("enableSearch", settings.enableSearch)
                addProperty("showWelcomeDialog", settings.showWelcomeDialog)
            }
            webSocket.sendMutationAwait("others.updateSettings", input)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update server settings", e)
            Result.failure(e)
        }
    }

    suspend fun createRole(): Result<Unit> {
        return try {
            webSocket.sendMutationAwait("roles.add", com.google.gson.JsonObject())
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create role", e)
            Result.failure(e)
        }
    }

    suspend fun updateRole(id: Int, name: String, color: String, permissions: List<String>): Result<Unit> {
        return try {
            val input = com.google.gson.JsonObject().apply {
                addProperty("roleId", id)
                addProperty("name", name)
                addProperty("color", color)
                val permsArray = com.google.gson.JsonArray()
                permissions.forEach { permsArray.add(it) }
                add("permissions", permsArray)
                addProperty("storageQuotaOverrideEnabled", false)
                addProperty("storageSpaceQuota", 0)
            }
            webSocket.sendMutationAwait("roles.update", input)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update role", e)
            Result.failure(e)
        }
    }

    suspend fun deleteRole(id: Int): Result<Unit> {
        return try {
            val input = com.google.gson.JsonObject().apply {
                addProperty("roleId", id)
            }
            webSocket.sendMutationAwait("roles.delete", input)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete role", e)
            Result.failure(e)
        }
    }

    // Invites Operations

    suspend fun getInvites(): Result<List<Invite>> {
        return try {
            val result = webSocket.sendQueryAwait("invites.getAll")
            val type = object : com.google.gson.reflect.TypeToken<List<Invite>>() {}.type
            val invites = com.google.gson.Gson().fromJson<List<Invite>>(result.get("value"), type)
            Result.success(invites)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get invites", e)
            Result.failure(e)
        }
    }

    suspend fun createInvite(maxUses: Int?, expiresAt: Long?, roleId: Int?, code: String?): Result<Invite> {
        return try {
            val input = com.google.gson.JsonObject().apply {
                if (maxUses != null) addProperty("maxUses", maxUses)
                if (expiresAt != null) addProperty("expiresAt", expiresAt)
                if (roleId != null) addProperty("roleId", roleId)
                if (!code.isNullOrBlank()) addProperty("code", code)
            }
            val result = webSocket.sendMutationAwait("invites.add", input)
            val invite = com.google.gson.Gson().fromJson(result, Invite::class.java)
            Result.success(invite)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create invite", e)
            Result.failure(e)
        }
    }

    suspend fun deleteInvite(inviteId: Int): Result<Unit> {
        return try {
            val input = com.google.gson.JsonObject().apply {
                addProperty("inviteId", inviteId)
            }
            webSocket.sendMutationAwait("invites.delete", input)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete invite", e)
            Result.failure(e)
        }
    }

    // Emojis Operations

    suspend fun uploadEmoji(name: String, fileBytes: ByteArray, originalName: String): Result<Unit> {
        return try {
            val token = SharkordClient.currentToken ?: throw Exception("Not logged in")
            val url = SharkordClient.currentServerUrl ?: throw Exception("No server URL")
            val fileUploadResult = http.uploadFile(url, token, originalName, fileBytes)
            
            fileUploadResult.onSuccess { fileInfo ->
                val inputData = com.google.gson.JsonObject().apply {
                    addProperty("fileId", fileInfo.id)
                    addProperty("name", name)
                }
                val jsonArray = com.google.gson.JsonArray().apply { add(inputData) }
                
                webSocket.sendMutationAwait("emojis.add", jsonArray)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload emoji", e)
            Result.failure(e)
        }
    }

    suspend fun deleteEmoji(emojiId: Int): Result<Unit> {
        return try {
            val input = com.google.gson.JsonObject().apply {
                addProperty("emojiId", emojiId)
            }
            webSocket.sendMutationAwait("emojis.delete", input)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete emoji", e)
            Result.failure(e)
        }
    }
}
