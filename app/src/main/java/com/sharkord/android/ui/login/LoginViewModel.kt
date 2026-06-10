package com.sharkord.android.ui.login

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharkord.android.R
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.data.repository.ServerRepository
import kotlinx.coroutines.launch

enum class LoginStep {
    URL,
    CREDENTIALS
}

/**
 * ViewModel for the login flow (server URL entry + credentials).
 *
 * Uses [ServerRepository] for all network operations instead of directly
 * calling SharkordClient. Uses coroutines instead of callback chains.
 */
class LoginViewModel : ViewModel() {

    private val repository = ServerRepository()

    var serverUrl by mutableStateOf("")
    var identity by mutableStateOf("")
    var password by mutableStateOf("")
    var autoLogin by mutableStateOf(false)
    
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var loginSuccess by mutableStateOf(false)

    var currentStep by mutableStateOf(LoginStep.URL)
    var serverLogoUrl by mutableStateOf<String?>(null)
    var serverName by mutableStateOf("Sharkord")
    var serverDescription by mutableStateOf<String?>(null)

    /** Tracks whether we are currently performing an automatic login transition. */
    var isAutoLoggingIn by mutableStateOf(false)
        private set

    /**
     * Called once on first composition to restore saved state and
     * optionally trigger auto-login.
     */
    fun initialize(context: Context, onAutoLoginSuccess: () -> Unit) {
        // Ensure SharkordClient is initialized
        SharkordClient.initialize(context)

        // Restore auto-login preference
        autoLogin = repository.isAutoLoginEnabled()

        // Try to auto-login if a saved session exists
        if (repository.restoreSession()) {
            isAutoLoggingIn = true
            serverUrl = SharkordClient.currentServerUrl ?: ""
            fetchServerDetails(serverUrl)
            onAutoLoginSuccess()
            return
        }

        // Try to pre-fill the server URL from saved preferences
        val savedUrl = repository.getSavedServerUrl()
        if (!savedUrl.isNullOrBlank()) {
            serverUrl = savedUrl
            currentStep = LoginStep.CREDENTIALS
            fetchServerDetails(savedUrl)
        } else {
            currentStep = LoginStep.URL
        }
    }

    /**
     * Fetches server info (name, description, logo) for display.
     * Silently ignores errors — this is a best-effort cosmetic operation.
     */
    private fun fetchServerDetails(url: String) {
        viewModelScope.launch {
            repository.fetchServerInfo(url).onSuccess { info ->
                val cleanUrl = url.trimEnd('/')
                serverLogoUrl = info.logo?.name?.let { name ->
                    val encodedName = android.net.Uri.encode(name)
                    "$cleanUrl/public/$encodedName"
                }
                Log.d("LoginViewModel", "fetchServerDetails success: logoUrl=$serverLogoUrl")
                serverName = info.name
                serverDescription = info.description
            }.onFailure { error ->
                Log.e("LoginViewModel", "fetchServerDetails failure: ${error.message}", error)
            }
        }
    }

    /**
     * Step 1: User taps "Next" after entering a server URL.
     * Validates the URL by fetching server info.
     */
    fun onNextClick(context: Context) {
        if (serverUrl.isBlank()) {
            errorMessage = context.getString(R.string.connect_invalidUrlError)
            return
        }

        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            repository.fetchServerInfo(serverUrl).fold(
                onSuccess = { info ->
                    isLoading = false
                    val cleanUrl = serverUrl.trimEnd('/')
                    serverLogoUrl = info.logo?.name?.let { name ->
                        val encodedName = android.net.Uri.encode(name)
                        "$cleanUrl/public/$encodedName"
                    }
                    serverName = info.name
                    serverDescription = info.description

                    // Save the validated URL
                    repository.saveServerUrl(cleanUrl)
                    serverUrl = cleanUrl

                    currentStep = LoginStep.CREDENTIALS
                },
                onFailure = { error ->
                    isLoading = false
                    errorMessage = context.getString(
                        R.string.connect_connectError,
                        error.message ?: context.getString(R.string.settings_errorBadge)
                    )
                }
            )
        }
    }

    /**
     * Step 1b: User taps "Back" to change the server URL.
     */
    fun changeServer() {
        errorMessage = null
        currentStep = LoginStep.URL
        serverName = "Sharkord"
        serverDescription = null
        serverLogoUrl = null
    }

    /**
     * Step 2: User taps "Connect" with identity + password.
     */
    fun onLoginClick(context: Context, onSuccess: () -> Unit) {
        if (serverUrl.isBlank() || identity.isBlank() || password.isBlank()) {
            errorMessage = context.getString(R.string.settings_errorBadge)
            return
        }

        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            repository.login(
                serverUrl = serverUrl,
                identity = identity,
                password = password,
                autoLogin = autoLogin
            ).fold(
                onSuccess = {
                    isLoading = false
                    loginSuccess = true
                    onSuccess()
                },
                onFailure = { error ->
                    isLoading = false
                    errorMessage = error.message ?: context.getString(R.string.settings_errorBadge)
                }
            )
        }
    }
}
