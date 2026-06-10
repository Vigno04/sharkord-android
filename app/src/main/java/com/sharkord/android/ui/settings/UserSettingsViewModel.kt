package com.sharkord.android.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.sharkord.android.data.model.User
import com.sharkord.android.data.network.SharkordClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UserSettingsState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val isSavingProfile: Boolean = false,
    val isSavingPassword: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

class UserSettingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UserSettingsState())
    val uiState: StateFlow<UserSettingsState> = _uiState.asStateFlow()

    // Profile Fields
    var name = MutableStateFlow("")
    var bio = MutableStateFlow("")
    var bannerColor = MutableStateFlow("#FFFFFF")

    // Password Fields
    var currentPassword = MutableStateFlow("")
    var newPassword = MutableStateFlow("")
    var confirmNewPassword = MutableStateFlow("")

    // App Settings
    var maxDiskCacheMb = MutableStateFlow(250)

    init {
        maxDiskCacheMb.value = SharkordClient.session.maxDiskCacheMb
        loadUser()
    }

    private fun loadUser() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            SharkordClient.webSocket.serverData.collect { joinData ->
                val currentUser = joinData.users.find { it.id == joinData.ownUserId }
                if (currentUser != null && _uiState.value.user?.id != currentUser.id) {
                    name.value = currentUser.name
                    bio.value = currentUser.bio ?: ""
                    bannerColor.value = currentUser.bannerColor ?: "#FFFFFF"
                }
                _uiState.value = _uiState.value.copy(
                    user = currentUser,
                    isLoading = false
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }

    fun saveProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingProfile = true, error = null, successMessage = null)
            try {
                val input = JsonObject().apply {
                    addProperty("name", name.value)
                    addProperty("bio", bio.value)
                    addProperty("bannerColor", bannerColor.value)
                }
                SharkordClient.webSocket.sendMutationAwait("users.update", input)
                _uiState.value = _uiState.value.copy(successMessage = "Profile updated successfully")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to update profile")
            } finally {
                _uiState.value = _uiState.value.copy(isSavingProfile = false)
            }
        }
    }

    fun updatePassword() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingPassword = true, error = null, successMessage = null)
            try {
                val input = JsonObject().apply {
                    addProperty("currentPassword", currentPassword.value)
                    addProperty("newPassword", newPassword.value)
                    addProperty("confirmNewPassword", confirmNewPassword.value)
                }
                SharkordClient.webSocket.sendMutationAwait("users.updatePassword", input)
                _uiState.value = _uiState.value.copy(successMessage = "Password updated successfully")
                currentPassword.value = ""
                newPassword.value = ""
                confirmNewPassword.value = ""
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to update password")
            } finally {
                _uiState.value = _uiState.value.copy(isSavingPassword = false)
            }
        }
    }

    fun saveMaxDiskCacheMb(value: Int) {
        maxDiskCacheMb.value = value
        SharkordClient.session.maxDiskCacheMb = value
    }

    fun uploadAvatar(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingProfile = true, error = null, successMessage = null)
            try {
                val fileId = uploadImage(context, uri)
                if (fileId != null) {
                    val input = JsonObject().apply { addProperty("fileId", fileId) }
                    SharkordClient.webSocket.sendMutationAwait("users.changeAvatar", input)
                    _uiState.value = _uiState.value.copy(successMessage = "Avatar updated")
                } else {
                    _uiState.value = _uiState.value.copy(error = "Failed to upload image")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to upload avatar")
            } finally {
                _uiState.value = _uiState.value.copy(isSavingProfile = false)
            }
        }
    }

    fun uploadBanner(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingProfile = true, error = null, successMessage = null)
            try {
                val fileId = uploadImage(context, uri)
                if (fileId != null) {
                    val input = JsonObject().apply { addProperty("fileId", fileId) }
                    SharkordClient.webSocket.sendMutationAwait("users.changeBanner", input)
                    _uiState.value = _uiState.value.copy(successMessage = "Banner updated")
                } else {
                    _uiState.value = _uiState.value.copy(error = "Failed to upload image")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Failed to upload banner")
            } finally {
                _uiState.value = _uiState.value.copy(isSavingProfile = false)
            }
        }
    }

    private suspend fun uploadImage(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()

            if (bytes != null) {
                val serverUrl = SharkordClient.currentServerUrl ?: return null
                val token = SharkordClient.currentToken ?: return null
                
                // Get filename from uri or fallback
                val fileName = "upload.jpg" // Could extract real name
                
                val result = SharkordClient.http.uploadFile(serverUrl, token, fileName, bytes)
                if (result.isSuccess) {
                    result.getOrNull()?.id
                } else {
                    Log.e("UserSettings", "Upload failed: ${result.exceptionOrNull()?.message}")
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("UserSettings", "Upload error", e)
            null
        }
    }
}
