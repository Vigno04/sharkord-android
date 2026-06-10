package com.sharkord.android.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharkord.android.data.model.JoinServerData
import com.sharkord.android.data.model.PluginInfo
import com.sharkord.android.data.model.Role
import com.sharkord.android.data.model.UpdateInfo
import com.sharkord.android.data.repository.ServerRepository
import com.sharkord.android.data.model.Invite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ModViewScreen {
    MAIN, MESSAGES, LINKS, FILES
}

data class ServerSettingsUiState(
    val serverData: JoinServerData? = null,
    val adminSettings: com.sharkord.android.data.model.AdminSettings? = null,
    val diskMetrics: com.sharkord.android.data.model.DiskMetrics? = null,
    val activeInvites: List<Invite> = emptyList(),
    val plugins: List<PluginInfo> = emptyList(),
    val updateInfo: UpdateInfo? = null,
    val isModViewOpen: Boolean = false,
    val isModViewLoading: Boolean = false,
    val modViewData: com.sharkord.android.data.model.ModViewData? = null,
    val modViewScreen: ModViewScreen = ModViewScreen.MAIN,
    val marketplaceEntries: List<com.sharkord.android.data.model.MarketplaceEntry> = emptyList(),
    val isMarketplaceLoading: Boolean = false,
    val marketplaceError: String? = null,
    val pluginLogs: List<com.sharkord.android.data.model.PluginLogEntry>? = null,
    val pluginCommands: List<com.sharkord.android.data.model.PluginCommandInfo>? = null,
    val pluginSettings: com.sharkord.android.data.model.PluginSettingsResponse? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

class ServerSettingsViewModel(
    private val repository: ServerRepository = ServerRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerSettingsUiState())
    val uiState: StateFlow<ServerSettingsUiState> = _uiState.asStateFlow()

    init {
        // Collect real-time server data (emits on initial connect)
        viewModelScope.launch {
            repository.serverData.collect { data ->
                _uiState.value = _uiState.value.copy(
                    serverData = data
                )
            }
        }

        // Listen for real-time events to update the local copy
        viewModelScope.launch {
            repository.incomingEvents.collect { event ->
                val parsed = com.sharkord.android.data.network.ServerEventHandler.parse(event)
                if (parsed != null) {
                    applyServerEvent(parsed)
                }
            }
        }

        // Fetch admin settings immediately
        fetchAdminSettings()

        // Fetch disk metrics
        fetchDiskMetrics()

        // Fetch invites immediately
        fetchInvites()
    }

    private fun applyServerEvent(event: com.sharkord.android.data.network.ServerEvent) {
        _uiState.update { state ->
            val data = state.serverData ?: return@update state
            when (event) {
                is com.sharkord.android.data.network.ServerEvent.RoleCreated -> {
                    state.copy(
                        serverData = data.copy(roles = (data.roles ?: emptyList()) + event.role)
                    )
                }
                is com.sharkord.android.data.network.ServerEvent.RoleUpdated -> {
                    state.copy(
                        serverData = data.copy(
                            roles = data.roles?.map { if (it.id == event.role.id) event.role else it }
                        )
                    )
                }
                is com.sharkord.android.data.network.ServerEvent.RoleDeleted -> {
                    state.copy(
                        serverData = data.copy(
                            roles = data.roles?.filter { it.id != event.roleId }
                        )
                    )
                }
                is com.sharkord.android.data.network.ServerEvent.EmojiCreated -> {
                    state.copy(
                        serverData = data.copy(emojis = (data.emojis ?: emptyList()) + event.emoji)
                    )
                }
                is com.sharkord.android.data.network.ServerEvent.EmojiDeleted -> {
                    state.copy(
                        serverData = data.copy(emojis = data.emojis?.filter { it.id != event.emojiId })
                    )
                }
                is com.sharkord.android.data.network.ServerEvent.EmojiUpdated -> {
                    state.copy(
                        serverData = data.copy(emojis = data.emojis?.map { if (it.id == event.emoji.id) event.emoji else it })
                    )
                }
                else -> state
            }
        }
    }

    private fun fetchAdminSettings() {
        viewModelScope.launch {
            val result = repository.getAdminSettings()
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(adminSettings = result.getOrNull())
            } else {
                Log.e("ServerSettingsVM", "Failed to fetch admin settings", result.exceptionOrNull())
            }
        }
    }

    private fun fetchDiskMetrics() {
        viewModelScope.launch {
            val result = repository.getDiskMetrics()
            if (result.isSuccess) {
                _uiState.update { it.copy(diskMetrics = result.getOrNull()) }
            } else {
                Log.e("ServerSettingsVM", "Failed to fetch disk metrics", result.exceptionOrNull())
            }
        }
    }

    fun updateAdminSettings(newSettings: com.sharkord.android.data.model.AdminSettings) {
        _uiState.value = _uiState.value.copy(adminSettings = newSettings)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }

    fun saveGeneralSettings() {
        val settings = _uiState.value.adminSettings ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = repository.updateServerSettings(settings)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = "Server settings updated successfully!"
                )
            } else {
                val e = result.exceptionOrNull()
                Log.e("ServerSettingsVM", "Failed to save settings", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to update settings: ${e?.message}"
                )
            }
        }
    }

    fun uploadServerLogo(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()

                if (bytes == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to read file")
                    return@launch
                }

                var originalName = "logo.png"
                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val displayNameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex != -1) {
                            originalName = it.getString(displayNameIndex)
                        }
                    }
                }

                val result = repository.uploadServerLogo(bytes, originalName)
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Logo uploaded successfully!"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to upload logo: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Error: ${e.message}")
            }
        }
    }

    fun createRole() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = repository.createRole()
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isLoading = false, successMessage = "Role created")
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to create role")
            }
        }
    }

    fun updateRole(id: Int, name: String, color: String, permissions: List<String>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = repository.updateRole(id, name, color, permissions)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isLoading = false, successMessage = "Role updated")
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to update role")
            }
        }
    }

    fun deleteRole(id: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = repository.deleteRole(id)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isLoading = false, successMessage = "Role deleted")
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to delete role")
            }
        }
    }

    // Invites

    fun fetchInvites() {
        viewModelScope.launch {
            val result = repository.getInvites()
            result.onSuccess { invites ->
                _uiState.update { it.copy(activeInvites = invites) }
            }
        }
    }

    fun createInvite(maxUses: Int? = null, expiresAt: Long? = null, roleId: Int? = null, code: String? = null) {
        viewModelScope.launch {
            val result = repository.createInvite(maxUses, expiresAt, roleId, code)
            result.onSuccess { invite ->
                _uiState.update { it.copy(activeInvites = it.activeInvites + invite) }
            }
        }
    }

    fun deleteInvite(inviteId: Int) {
        viewModelScope.launch {
            val result = repository.deleteInvite(inviteId)
            result.onSuccess {
                _uiState.update { state -> 
                    state.copy(activeInvites = state.activeInvites.filter { it.id != inviteId })
                }
            }
        }
    }

    // Emojis

    fun uploadEmoji(name: String, fileBytes: ByteArray, originalName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.uploadEmoji(name, fileBytes, originalName)
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, successMessage = "Emoji uploaded successfully") }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Failed to upload emoji: ${result.exceptionOrNull()?.message}") }
            }
        }
    }

    fun deleteEmoji(emojiId: Int) {
        viewModelScope.launch {
            repository.deleteEmoji(emojiId)
        }
    }

    // Plugins

    fun fetchPlugins() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.getPlugins()
            result.onSuccess { plugins ->
                _uiState.update { it.copy(plugins = plugins, isLoading = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = "Failed to fetch plugins") }
            }
        }
    }

    fun fetchMarketplacePlugins() {
        viewModelScope.launch {
            _uiState.update { it.copy(isMarketplaceLoading = true, marketplaceError = null) }
            val result = repository.getMarketplacePlugins()
            result.onSuccess { entries ->
                _uiState.update { it.copy(marketplaceEntries = entries, isMarketplaceLoading = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isMarketplaceLoading = false, marketplaceError = "Failed to fetch marketplace: ${e.message}") }
            }
        }
    }

    fun togglePlugin(pluginId: String, enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.togglePlugin(pluginId, enabled)
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, successMessage = if (enabled) "Plugin enabled" else "Plugin disabled") }
                fetchPlugins()
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Failed to toggle plugin") }
            }
        }
    }

    fun removePlugin(pluginId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.removePlugin(pluginId)
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, successMessage = "Plugin removed") }
                fetchPlugins()
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Failed to remove plugin") }
            }
        }
    }

    fun installPlugin(pluginId: String, version: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.installPlugin(pluginId, version)
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, successMessage = "Plugin installed") }
                fetchPlugins()
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Failed to install plugin") }
            }
        }
    }

    fun updatePlugin(pluginId: String, version: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.updatePlugin(pluginId, version)
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, successMessage = "Plugin updated") }
                fetchPlugins()
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Failed to update plugin") }
            }
        }
    }

    fun fetchPluginLogs(pluginId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, pluginLogs = null) }
            val result = repository.getPluginLogs(pluginId)
            result.onSuccess { logs ->
                _uiState.update { it.copy(pluginLogs = logs, isLoading = false) }
            }.onFailure {
                _uiState.update { it.copy(isLoading = false, error = "Failed to fetch logs") }
            }
        }
    }
    
    fun fetchPluginCommands(pluginId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, pluginCommands = null) }
            val result = repository.getPluginCommands(pluginId)
            result.onSuccess { commands ->
                _uiState.update { it.copy(pluginCommands = commands, isLoading = false) }
            }.onFailure {
                _uiState.update { it.copy(isLoading = false, error = "Failed to fetch commands") }
            }
        }
    }
    
    fun fetchPluginSettings(pluginId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, pluginSettings = null) }
            val result = repository.getPluginSettings(pluginId)
            result.onSuccess { settings ->
                _uiState.update { it.copy(pluginSettings = settings, isLoading = false) }
            }.onFailure {
                _uiState.update { it.copy(isLoading = false, error = "Failed to fetch settings") }
            }
        }
    }
    
    fun executePluginCommand(pluginId: String, commandName: String, args: com.google.gson.JsonObject) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.executePluginCommand(pluginId, commandName, args)
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, successMessage = "Command executed: ${result.getOrNull()}") }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Failed to execute command") }
            }
        }
    }
    
    fun updatePluginSetting(pluginId: String, key: String, value: Any) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.updatePluginSetting(pluginId, key, value)
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, successMessage = "Setting updated") }
                fetchPluginSettings(pluginId) // Reload
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Failed to update setting") }
            }
        }
    }
    
    fun clearPluginModals() {
        _uiState.update { it.copy(pluginLogs = null, pluginCommands = null, pluginSettings = null) }
    }

    // Updates

    fun fetchUpdateInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.getServerUpdate()
            result.onSuccess { info ->
                _uiState.update { it.copy(updateInfo = info, isLoading = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = "Failed to fetch update info") }
            }
        }
    }

    fun updateServer() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.updateServer()
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, successMessage = "Server update initiated") }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Failed to update server") }
            }
        }
    }

    // Users

    fun deleteUser(userId: Int, wipe: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = repository.deleteUser(userId, wipe)
            if (result.isSuccess) {
                // Remove user from the local serverData list
                _uiState.update { state ->
                    val updatedUsers = state.serverData?.users?.filter { it.id != userId } ?: emptyList()
                    state.copy(
                        isLoading = false, 
                        successMessage = "User deleted",
                        serverData = state.serverData?.copy(users = updatedUsers)
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Failed to delete user") }
            }
        }
    }

    // Mod View

    fun openModView(userId: Int) {
        _uiState.update { it.copy(isModViewOpen = true, isModViewLoading = true, modViewData = null) }
        viewModelScope.launch {
            val result = repository.getUserInfo(userId)
            result.onSuccess { data ->
                _uiState.update { it.copy(modViewData = data, isModViewLoading = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isModViewLoading = false, error = "Failed to load user info") }
            }
        }
    }

    fun closeModView() {
        _uiState.update { it.copy(isModViewOpen = false, modViewData = null, modViewScreen = ModViewScreen.MAIN) }
    }

    fun setModViewScreen(screen: ModViewScreen) {
        _uiState.update { it.copy(modViewScreen = screen) }
    }

    fun kickUser(userId: Int, reason: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isModViewLoading = true) }
            val result = repository.kickUser(userId, reason)
            if (result.isSuccess) {
                _uiState.update { it.copy(successMessage = "User kicked") }
                openModView(userId) // Reload
            } else {
                _uiState.update { it.copy(isModViewLoading = false, error = "Failed to kick user") }
            }
        }
    }

    fun banUser(userId: Int, reason: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isModViewLoading = true) }
            val result = repository.banUser(userId, reason)
            if (result.isSuccess) {
                _uiState.update { it.copy(successMessage = "User banned") }
                openModView(userId) // Reload
            } else {
                _uiState.update { it.copy(isModViewLoading = false, error = "Failed to ban user") }
            }
        }
    }

    fun unbanUser(userId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isModViewLoading = true) }
            val result = repository.unbanUser(userId)
            if (result.isSuccess) {
                _uiState.update { it.copy(successMessage = "User unbanned") }
                openModView(userId) // Reload
            } else {
                _uiState.update { it.copy(isModViewLoading = false, error = "Failed to unban user") }
            }
        }
    }

    fun addUserRole(userId: Int, roleId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isModViewLoading = true) }
            val result = repository.addUserRole(userId, roleId)
            if (result.isSuccess) {
                _uiState.update { it.copy(successMessage = "Role added") }
                openModView(userId) // Reload
            } else {
                _uiState.update { it.copy(isModViewLoading = false, error = "Failed to add role") }
            }
        }
    }

    fun removeUserRole(userId: Int, roleId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isModViewLoading = true) }
            val result = repository.removeUserRole(userId, roleId)
            if (result.isSuccess) {
                _uiState.update { it.copy(successMessage = "Role removed") }
                openModView(userId) // Reload
            } else {
                _uiState.update { it.copy(isModViewLoading = false, error = "Failed to remove role") }
            }
        }
    }
}
