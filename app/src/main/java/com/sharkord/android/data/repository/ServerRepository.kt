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

// repository layer that coordinates HTTP and WebSocket operations
// and exposes reactive state for the UI layer
// this is the single source of truth for server connectivity state,
// replacing the scattered state management that was previously in
// homeScreen, LoginViewModel, and SharkordClient
class ServerRepository {

    companion object {
        private const val TAG = "ServerRepository"
    }

    private val http get() = SharkordClient.http
    private val webSocket get() = SharkordClient.webSocket
    private val session get() = SharkordClient.session

    // reactive State

    // current WebSocket connection state
    val connectionState: StateFlow<ConnectionState>
        get() = webSocket.connectionState

    // emits JoinServerData when successfully connected
    val serverData: SharedFlow<JoinServerData>
        get() = webSocket.serverData

    // emits incoming real-time events from tRPC subscriptions
    val incomingEvents: SharedFlow<IncomingEvent>
        get() = webSocket.incomingEvents

    // HTTP Operations

    // fetches server info (name, description, logo, version) from GET /info
    // does NOT modify session state
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

    // authenticates with the server via POST /login
    // on success, stores the token and server URL in session (if auto-login is enabled)
    // @return The JWT token on success
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

    // webSocket Operations

    // connects to the server via WebSocket and performs the full tRPC handshake + joinServer flow
    // uses the currently stored token and server URL
    // @return true if connection was initiated, false if missing token/url
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

    // disconnects the WebSocket and clears session state
    // used for explicit logout
    fun logout() {
        SharkordClient.clearState()
        session.clearSession()
        Log.d(TAG, "Logged out, session cleared")
    }

    // disconnects the WebSocket without clearing session (e.g., app going to background)
    fun disconnectWebSocket() {
        webSocket.disconnect()
    }

    // session Queries

    // attempts to restore a saved session. Returns true if credentials exist
    // and the session was restored to SharkordClient's in-memory state
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

    // saves only the server URL to preferences (when user enters URL before login)
    fun saveServerUrl(serverUrl: String) {
        val cleanUrl = serverUrl.trimEnd('/')
        session.saveServerUrl(cleanUrl)
        SharkordClient.currentServerUrl = cleanUrl
    }

    // returns the saved server URL from preferences (for pre-filling the URL field)
    fun getSavedServerUrl(): String? = session.serverUrl

    fun isAutoLoginEnabled(): Boolean = session.autoLogin

    // opens a direct message channel with the given user
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

    suspend fun createChannel(name: String, type: com.sharkord.android.data.model.ChannelType, categoryId: Int?): Result<Unit> {
        return try {
            val input = JsonObject().apply {
                addProperty("name", name)
                addProperty("type", type.value)
                if (categoryId != null) {
                    addProperty("categoryId", categoryId)
                }
            }
            webSocket.sendMutationAwait("channels.add", input)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create channel", e)
            Result.failure(e)
        }
    }

    suspend fun createCategory(name: String): Result<Unit> {
        return try {
            val input = JsonObject().apply {
                addProperty("name", name)
            }
            webSocket.sendMutationAwait("categories.add", input)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create category", e)
            Result.failure(e)
        }
    }

    suspend fun updateChannel(channelId: Int, name: String, topic: String?, isPrivate: Boolean): Result<Unit> {
        return try {
            val input = JsonObject().apply {
                addProperty("channelId", channelId)
                addProperty("name", name)
                if (topic != null) {
                    addProperty("topic", topic)
                } else {
                    add("topic", com.google.gson.JsonNull.INSTANCE)
                }
                addProperty("private", isPrivate)
            }
            webSocket.sendMutationAwait("channels.update", input)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update channel", e)
            Result.failure(e)
        }
    }

    suspend fun deleteChannel(channelId: Int): Result<Unit> {
        return try {
            val input = JsonObject().apply {
                addProperty("channelId", channelId)
            }
            webSocket.sendMutationAwait("channels.delete", input)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete channel", e)
            Result.failure(e)
        }
    }

    suspend fun reorderChannels(categoryId: Int, channelIds: List<Int>): Result<Unit> {
        return try {
            val input = JsonObject().apply {
                addProperty("categoryId", categoryId)
                val idsArray = com.google.gson.JsonArray()
                channelIds.forEach { idsArray.add(it) }
                add("channelIds", idsArray)
            }
            webSocket.sendMutationAwait("channels.reorder", input)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reorder channels", e)
            Result.failure(e)
        }
    }

    suspend fun getChannelPermissions(channelId: Int): Result<com.sharkord.android.data.model.ChannelPermissionsResponse> {
        return try {
            val input = JsonObject().apply {
                addProperty("channelId", channelId)
            }
            val response = webSocket.sendMutationAwait("channels.getPermissions", input)
            val gson = com.google.gson.Gson()
            val parsed = gson.fromJson(response, com.sharkord.android.data.model.ChannelPermissionsResponse::class.java)
            Result.success(parsed)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get channel permissions", e)
            Result.failure(e)
        }
    }

    suspend fun updateChannelPermissions(
        channelId: Int,
        roleId: Int?,
        userId: Int?,
        isCreate: Boolean,
        permissions: List<String>
    ): Result<Unit> {
        return try {
            val input = JsonObject().apply {
                addProperty("channelId", channelId)
                roleId?.let { addProperty("roleId", it) }
                userId?.let { addProperty("userId", it) }
                addProperty("isCreate", isCreate)
                val permsArray = com.google.gson.JsonArray()
                permissions.forEach { permsArray.add(it) }
                add("permissions", permsArray)
            }
            webSocket.sendMutationAwait("channels.updatePermissions", input)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update channel permissions", e)
            Result.failure(e)
        }
    }

    suspend fun deleteChannelPermissions(
        channelId: Int,
        roleId: Int?,
        userId: Int?
    ): Result<Unit> {
        return try {
            val input = JsonObject().apply {
                addProperty("channelId", channelId)
                roleId?.let { addProperty("roleId", it) }
                userId?.let { addProperty("userId", it) }
            }
            webSocket.sendMutationAwait("channels.deletePermissions", input)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete channel permissions", e)
            Result.failure(e)
        }
    }

    suspend fun markChannelAsRead(channelId: Int): Result<Unit> {
        return try {
            com.sharkord.android.utils.NotificationHelper.clearNotification(com.sharkord.android.data.network.SharkordClient.applicationContext, channelId)
            val input = com.google.gson.JsonObject().apply {
                addProperty("channelId", channelId)
            }
            webSocket.sendMutationAwait("channels.markAsRead", input)
            com.sharkord.android.data.network.SharkordClient.channelReadEvents.tryEmit(channelId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark channel as read", e)
            Result.failure(e)
        }
    }

    // server Administration (Settings & Roles)

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

    suspend fun getDiskMetrics(): Result<com.sharkord.android.data.model.DiskMetrics> {
        return try {
            val response = webSocket.sendQueryAwait("others.getStorageSettings", com.google.gson.JsonObject())
            val diskMetricsJson = response.get("diskMetrics")
            val gson = com.google.gson.Gson()
            val diskMetrics = gson.fromJson(diskMetricsJson, com.sharkord.android.data.model.DiskMetrics::class.java)
            Result.success(diskMetrics)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get disk metrics", e)
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
                
                // storage Fields
                addProperty("storageUploadEnabled", settings.storageUploadEnabled)
                if (settings.storageQuota != null) addProperty("storageQuota", settings.storageQuota)
                if (settings.storageUploadMaxFileSize != null) addProperty("storageUploadMaxFileSize", settings.storageUploadMaxFileSize)
                addProperty("storageMaxFilesPerMessage", settings.storageMaxFilesPerMessage)
                addProperty("storageFileSharingInDirectMessages", settings.storageFileSharingInDirectMessages)
                if (settings.storageSpaceQuotaByUser != null) addProperty("storageSpaceQuotaByUser", settings.storageSpaceQuotaByUser)
                if (settings.storageOverflowAction != null) addProperty("storageOverflowAction", settings.storageOverflowAction)
                addProperty("storageSignedUrlsEnabled", settings.storageSignedUrlsEnabled)
                addProperty("storageSignedUrlsTtlSeconds", settings.storageSignedUrlsTtlSeconds)
                addProperty("storageImageOptimizationEnabled", settings.storageImageOptimizationEnabled)
                addProperty("storageImageOptimizationQuality", settings.storageImageOptimizationQuality)
                if (settings.storageMaxAvatarSize != null) addProperty("storageMaxAvatarSize", settings.storageMaxAvatarSize)
                if (settings.storageMaxBannerSize != null) addProperty("storageMaxBannerSize", settings.storageMaxBannerSize)
            }
            webSocket.sendMutationAwait("others.updateSettings", input)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update server settings", e)
            Result.failure(e)
        }
    }

    // plugins

    suspend fun getPlugins(): Result<List<com.sharkord.android.data.model.PluginInfo>> {
        return try {
            val response = webSocket.sendQueryAwait("plugins.get")
            val type = object : com.google.gson.reflect.TypeToken<List<com.sharkord.android.data.model.PluginInfo>>() {}.type
            // response returns { plugins: [...] }
            val pluginsArray = response.get("plugins")
            val plugins = com.google.gson.Gson().fromJson<List<com.sharkord.android.data.model.PluginInfo>>(pluginsArray, type)
            Result.success(plugins)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get plugins", e)
            Result.failure(e)
        }
    }

    suspend fun togglePlugin(pluginId: String, enabled: Boolean): Result<Unit> {
        return try {
            val input = com.google.gson.JsonObject().apply {
                addProperty("pluginId", pluginId)
                addProperty("enabled", enabled)
            }
            webSocket.sendMutationAwait("plugins.toggle", input)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle plugin", e)
            Result.failure(e)
        }
    }

    suspend fun removePlugin(pluginId: String): Result<Unit> {
        return try {
            val input = com.google.gson.JsonObject().apply {
                addProperty("pluginId", pluginId)
            }
            webSocket.sendMutationAwait("plugins.remove", input)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove plugin", e)
            Result.failure(e)
        }
    }

    suspend fun installPlugin(pluginId: String, version: String): Result<Unit> {
        return try {
            val input = com.google.gson.JsonObject().apply {
                addProperty("pluginId", pluginId)
                addProperty("version", version)
            }
            webSocket.sendMutationAwait("plugins.install", input)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install plugin", e)
            Result.failure(e)
        }
    }

    suspend fun updatePlugin(pluginId: String, version: String): Result<Unit> {
        return try {
            val input = com.google.gson.JsonObject().apply {
                addProperty("pluginId", pluginId)
                addProperty("version", version)
            }
            webSocket.sendMutationAwait("plugins.update", input)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update plugin", e)
            Result.failure(e)
        }
    }

    suspend fun getPluginLogs(pluginId: String): Result<List<com.sharkord.android.data.model.PluginLogEntry>> {
        return try {
            val input = com.google.gson.JsonObject().apply {
                addProperty("pluginId", pluginId)
            }
            val response = webSocket.sendQueryAwait("plugins.getLogs", input)
            
            // webSocketManager wraps non-JsonObject responses (like arrays) in {"value": ...}
            val logsElement = if (response.has("value")) response.get("value") else response
            
            val type = object : com.google.gson.reflect.TypeToken<List<com.sharkord.android.data.model.PluginLogEntry>>() {}.type
            val logs = com.google.gson.Gson().fromJson<List<com.sharkord.android.data.model.PluginLogEntry>>(logsElement, type)
            Result.success(logs)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get plugin logs", e)
            Result.failure(e)
        }
    }

    suspend fun getPluginCommands(pluginId: String): Result<List<com.sharkord.android.data.model.PluginCommandInfo>> {
        return try {
            val input = com.google.gson.JsonObject().apply {
                addProperty("pluginId", pluginId)
            }
            val response = webSocket.sendQueryAwait("plugins.getCommands", input)
            val type = object : com.google.gson.reflect.TypeToken<Map<String, List<com.sharkord.android.data.model.PluginCommandInfo>>>() {}.type
            val commandsMap = com.google.gson.Gson().fromJson<Map<String, List<com.sharkord.android.data.model.PluginCommandInfo>>>(response, type)
            val commands = commandsMap[pluginId] ?: emptyList()
            Result.success(commands)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get plugin commands", e)
            Result.failure(e)
        }
    }

    suspend fun executePluginCommand(pluginId: String, commandName: String, args: com.google.gson.JsonObject): Result<com.google.gson.JsonObject> {
        return try {
            val input = com.google.gson.JsonObject().apply {
                addProperty("pluginId", pluginId)
                addProperty("commandName", commandName)
                add("args", args)
            }
            val response = webSocket.sendMutationAwait("plugins.executeCommand", input)
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute plugin command", e)
            Result.failure(e)
        }
    }

    suspend fun getPluginSettings(pluginId: String): Result<com.sharkord.android.data.model.PluginSettingsResponse> {
        return try {
            val input = com.google.gson.JsonObject().apply {
                addProperty("pluginId", pluginId)
            }
            val response = webSocket.sendQueryAwait("plugins.getSettings", input)
            val parsed = com.google.gson.Gson().fromJson(response, com.sharkord.android.data.model.PluginSettingsResponse::class.java)
            Result.success(parsed)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get plugin settings", e)
            Result.failure(e)
        }
    }

    suspend fun updatePluginSetting(pluginId: String, key: String, value: Any): Result<Unit> {
        return try {
            val input = com.google.gson.JsonObject().apply {
                addProperty("pluginId", pluginId)
                addProperty("key", key)
                when (value) {
                    is String -> addProperty("value", value)
                    is Number -> addProperty("value", value)
                    is Boolean -> addProperty("value", value)
                    else -> addProperty("value", value.toString())
                }
            }
            webSocket.sendMutationAwait("plugins.updateSetting", input)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update plugin setting", e)
            Result.failure(e)
        }
    }

    suspend fun getMarketplacePlugins(): Result<List<com.sharkord.android.data.model.MarketplaceEntry>> {
        return try {
            val url = "https://raw.githubusercontent.com/Sharkord/plugins/refs/heads/main/plugins.json?raw=true"
            val responseBody = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                java.net.URL(url).readText()
            }
            val type = object : com.google.gson.reflect.TypeToken<List<com.sharkord.android.data.model.MarketplaceEntry>>() {}.type
            val entries = com.google.gson.Gson().fromJson<List<com.sharkord.android.data.model.MarketplaceEntry>>(responseBody, type)
            Result.success(entries)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get marketplace plugins", e)
            Result.failure(e)
        }
    }

    // updates

    suspend fun getServerUpdate(): Result<com.sharkord.android.data.model.UpdateInfo> {
        return try {
            val response = webSocket.sendQueryAwait("others.getUpdate")
            val gson = com.google.gson.Gson()
            val parsed = gson.fromJson(response, com.sharkord.android.data.model.UpdateInfo::class.java)
            Result.success(parsed)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get server update", e)
            Result.failure(e)
        }
    }

    suspend fun updateServer(): Result<Unit> {
        return try {
            webSocket.sendMutationAwait("others.updateServer", com.google.gson.JsonObject())
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update server", e)
            Result.failure(e)
        }
    }

    // users

    suspend fun deleteUser(userId: Int, wipe: Boolean = false): Result<Unit> {
        return try {
            val input = com.google.gson.JsonObject().apply {
                addProperty("userId", userId)
                addProperty("wipe", wipe)
            }
            webSocket.sendMutationAwait("users.delete", input)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete user", e)
            Result.failure(e)
        }
    }

    suspend fun kickUser(userId: Int, reason: String?): Result<Unit> {
        return try {
            val input = com.google.gson.JsonObject().apply {
                addProperty("userId", userId)
                reason?.let { addProperty("reason", it) }
            }
            webSocket.sendMutationAwait("users.kick", input)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to kick user", e)
            Result.failure(e)
        }
    }

    suspend fun banUser(userId: Int, reason: String?): Result<Unit> {
        return try {
            val input = com.google.gson.JsonObject().apply {
                addProperty("userId", userId)
                reason?.let { addProperty("reason", it) }
            }
            webSocket.sendMutationAwait("users.ban", input)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ban user", e)
            Result.failure(e)
        }
    }

    suspend fun unbanUser(userId: Int): Result<Unit> {
        return try {
            val input = com.google.gson.JsonObject().apply {
                addProperty("userId", userId)
            }
            webSocket.sendMutationAwait("users.unban", input)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unban user", e)
            Result.failure(e)
        }
    }

    suspend fun addUserRole(userId: Int, roleId: Int): Result<Unit> {
        return try {
            val input = com.google.gson.JsonObject().apply {
                addProperty("userId", userId)
                addProperty("roleId", roleId)
            }
            webSocket.sendMutationAwait("users.addRole", input)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add role", e)
            Result.failure(e)
        }
    }

    suspend fun removeUserRole(userId: Int, roleId: Int): Result<Unit> {
        return try {
            val input = com.google.gson.JsonObject().apply {
                addProperty("userId", userId)
                addProperty("roleId", roleId)
            }
            webSocket.sendMutationAwait("users.removeRole", input)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove role", e)
            Result.failure(e)
        }
    }

    suspend fun getUserInfo(userId: Int): Result<com.sharkord.android.data.model.ModViewData> {
        return try {
            val input = com.google.gson.JsonObject().apply {
                addProperty("userId", userId)
            }
            val response = webSocket.sendQueryAwait("users.getInfo", input)
            val gson = com.google.gson.Gson()
            val parsed = gson.fromJson(response, com.sharkord.android.data.model.ModViewData::class.java)
            Result.success(parsed)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get user info", e)
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

    // invites Operations

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

    // emojis Operations

    suspend fun uploadEmoji(name: String, fileBytes: ByteArray, originalName: String): Result<Unit> {
        return try {
            val token = SharkordClient.currentToken ?: throw Exception("Not logged in")
            val url = SharkordClient.currentServerUrl ?: throw Exception("No server URL")
            val fileUploadResult = http.uploadFile(url, token, originalName, fileBytes)
            
            if (fileUploadResult.isFailure) {
                return Result.failure(fileUploadResult.exceptionOrNull() ?: Exception("Upload failed"))
            }

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

    suspend fun uploadServerLogo(fileBytes: ByteArray, originalName: String): Result<Unit> {
        return try {
            val token = SharkordClient.currentToken ?: throw Exception("Not logged in")
            val url = SharkordClient.currentServerUrl ?: throw Exception("No server URL")
            val fileUploadResult = http.uploadFile(url, token, originalName, fileBytes)
            
            if (fileUploadResult.isFailure) {
                return Result.failure(fileUploadResult.exceptionOrNull() ?: Exception("Upload failed"))
            }

            fileUploadResult.onSuccess { fileInfo ->
                val inputData = com.google.gson.JsonObject().apply {
                    addProperty("logoId", fileInfo.id)
                }
                webSocket.sendMutationAwait("others.updateSettings", inputData)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload server logo", e)
            Result.failure(e)
        }
    }
}
