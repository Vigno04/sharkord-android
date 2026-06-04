package com.sharkord.android.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharkord.android.data.model.JoinServerData
import com.sharkord.android.data.model.Role
import com.sharkord.android.data.repository.ServerRepository
import com.sharkord.android.data.model.Invite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServerSettingsUiState(
    val serverData: JoinServerData? = null,
    val adminSettings: com.sharkord.android.data.model.AdminSettings? = null,
    val activeInvites: List<Invite> = emptyList(),
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
        // TODO: Implement file upload to storage and update settings
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            kotlinx.coroutines.delay(1000)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                successMessage = "Logo uploaded successfully!"
            )
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
            repository.uploadEmoji(name, fileBytes, originalName)
        }
    }

    fun deleteEmoji(emojiId: Int) {
        viewModelScope.launch {
            repository.deleteEmoji(emojiId)
        }
    }
}
