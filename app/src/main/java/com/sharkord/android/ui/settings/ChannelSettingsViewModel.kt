package com.sharkord.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharkord.android.data.model.Channel
import com.sharkord.android.data.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChannelSettingsUiState(
    val channel: Channel? = null,
    val serverData: com.sharkord.android.data.model.JoinServerData? = null,
    val rolePermissions: List<com.sharkord.android.data.model.ChannelRolePermission> = emptyList(),
    val userPermissions: List<com.sharkord.android.data.model.ChannelUserPermission> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val isPermissionsLoading: Boolean = false
)

class ChannelSettingsViewModel(private val channelId: Int) : ViewModel() {
    private val repository = ServerRepository()
    
    private val _uiState = MutableStateFlow(ChannelSettingsUiState(isLoading = true))
    val uiState: StateFlow<ChannelSettingsUiState> = _uiState.asStateFlow()

    init {
        loadChannel()
        loadPermissions()
    }

    private fun loadChannel() {
        viewModelScope.launch {
            repository.serverData.collect { data ->
                val channel = data.channels.find { it.id == channelId }
                if (channel != null) {
                    _uiState.update { it.copy(channel = channel, serverData = data, isLoading = false) }
                } else {
                    _uiState.update { it.copy(errorMessage = "Channel not found", isLoading = false) }
                }
            }
        }
    }

    private fun loadPermissions() {
        viewModelScope.launch {
            if (_uiState.value.rolePermissions.isEmpty() && _uiState.value.userPermissions.isEmpty()) {
                _uiState.update { it.copy(isPermissionsLoading = true) }
            }
            val result = repository.getChannelPermissions(channelId)
            if (result.isSuccess) {
                val data = result.getOrNull()
                _uiState.update { 
                    it.copy(
                        rolePermissions = data?.rolePermissions ?: emptyList(),
                        userPermissions = data?.userPermissions ?: emptyList(),
                        isPermissionsLoading = false
                    ) 
                }
            } else {
                _uiState.update { it.copy(isPermissionsLoading = false, errorMessage = "Failed to load permissions") }
            }
        }
    }

    fun updateChannel(name: String, topic: String?, isPrivate: Boolean, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val result = repository.updateChannel(channelId, name, topic, isPrivate)
            _uiState.update { it.copy(isSaving = false) }
            
            if (result.isSuccess) {
                onSuccess()
            } else {
                _uiState.update { it.copy(errorMessage = result.exceptionOrNull()?.message ?: "Failed to update channel") }
            }
        }
    }

    fun updatePermissions(roleId: Int?, userId: Int?, isCreate: Boolean, permissions: List<String>) {
        viewModelScope.launch {
            // Optimistic Update
            val oldRolePerms = _uiState.value.rolePermissions
            val oldUserPerms = _uiState.value.userPermissions

            val newRolePerms = oldRolePerms.toMutableList()
            val newUserPerms = oldUserPerms.toMutableList()

            com.sharkord.android.data.model.ChannelPermission.values().forEach { permissionEnum ->
                val permStr = permissionEnum.value
                val isAllowed = permissions.contains(permStr)
                if (roleId != null) {
                    newRolePerms.removeAll { it.roleId == roleId && it.permission == permStr }
                    newRolePerms.add(com.sharkord.android.data.model.ChannelRolePermission(channelId, roleId, permStr, isAllowed))
                } else if (userId != null) {
                    newUserPerms.removeAll { it.userId == userId && it.permission == permStr }
                    newUserPerms.add(com.sharkord.android.data.model.ChannelUserPermission(channelId, userId, permStr, isAllowed))
                }
            }
            _uiState.update { it.copy(rolePermissions = newRolePerms, userPermissions = newUserPerms) }

            val result = repository.updateChannelPermissions(channelId, roleId, userId, isCreate, permissions)
            if (result.isSuccess) {
                loadPermissions() // Reload to get fresh state
            } else {
                // Revert
                _uiState.update { it.copy(rolePermissions = oldRolePerms, userPermissions = oldUserPerms, errorMessage = result.exceptionOrNull()?.message ?: "Failed to update permissions") }
            }
        }
    }

    fun deletePermissions(roleId: Int?, userId: Int?) {
        viewModelScope.launch {
            // Optimistic Update
            val oldRolePerms = _uiState.value.rolePermissions
            val oldUserPerms = _uiState.value.userPermissions

            _uiState.update { 
                it.copy(
                    rolePermissions = it.rolePermissions.filter { perm -> perm.roleId != roleId },
                    userPermissions = it.userPermissions.filter { perm -> perm.userId != userId }
                ) 
            }

            val result = repository.deleteChannelPermissions(channelId, roleId, userId)
            if (result.isSuccess) {
                loadPermissions() // Reload to get fresh state
            } else {
                // Revert
                _uiState.update { it.copy(rolePermissions = oldRolePerms, userPermissions = oldUserPerms, errorMessage = result.exceptionOrNull()?.message ?: "Failed to delete permissions") }
            }
        }
    }

    class Factory(private val channelId: Int) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChannelSettingsViewModel::class.java)) {
                return ChannelSettingsViewModel(channelId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
