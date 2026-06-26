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

// viewModel for the login flow (server URL entry + credentials)
// uses [ServerRepository] for all network operations instead of directly
// calling SharkordClient. Uses coroutines instead of callback chains
class LoginViewModel : ViewModel() {

    private val repository = ServerRepository()

    var serverUrl by mutableStateOf("")
    var identity by mutableStateOf("")
    var password by mutableStateOf("")
    var autoLogin by mutableStateOf(false)
    
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var loginSuccess by mutableStateOf(false)

    var showBiometricSavePrompt by mutableStateOf(false)
    var showBiometricLaunchPrompt by mutableStateOf(false)
    var pendingSuccessAction: (() -> Unit)? = null

    var showInsecureConnectionPrompt by mutableStateOf(false)
    var pendingInsecureUrl by mutableStateOf<String?>(null)

    var currentStep by mutableStateOf(LoginStep.URL)
    var serverLogoUrl by mutableStateOf<String?>(null)
    var serverName by mutableStateOf("Sharkord")
    var serverDescription by mutableStateOf<String?>(null)

    // tracks whether we are currently performing an automatic login transition
    var isAutoLoggingIn by mutableStateOf(false)
        private set
        
    var hideSplashScreen by mutableStateOf(false)

    // called once on first composition to restore saved state and
    // optionally trigger auto-login
    fun initialize(context: Context, onAutoLoginSuccess: () -> Unit) {
        // ensure SharkordClient is initialized
        SharkordClient.initialize(context)

        // restore auto-login preference
        autoLogin = repository.isAutoLoginEnabled()

        // try to auto-login if a saved session exists
        if (repository.restoreSession()) {
            isAutoLoggingIn = true
            serverUrl = SharkordClient.currentServerUrl ?: ""
            fetchServerDetails(serverUrl)

            if (SharkordClient.session.alwaysRequireBiometrics && SharkordClient.session.hasBiometricCredentials() && isBiometricSupported(context)) {
                pendingSuccessAction = onAutoLoginSuccess
                showBiometricLaunchPrompt = true
            } else {
                onAutoLoginSuccess()
            }
            return
        }

        // try to pre-fill the server URL from saved preferences
        val savedUrl = repository.getSavedServerUrl()
        if (!savedUrl.isNullOrBlank()) {
            serverUrl = savedUrl
            currentStep = LoginStep.CREDENTIALS
            fetchServerDetails(savedUrl)
        } else {
            currentStep = LoginStep.URL
        }
    }

    // fetches server info (name, description, logo) for display
    // silently ignores errors — this is a best-effort cosmetic operation
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

    // step 1: User taps "Next" after entering a server URL
    // validates the URL by fetching server info
    fun onNextClick(context: Context, isRetry: Boolean = false) {
        if (serverUrl.isBlank()) {
            errorMessage = context.getString(R.string.connect_invalidUrlError)
            return
        }

        val originalUrl = serverUrl.trim()
        val hasScheme = originalUrl.startsWith("http://", ignoreCase = true) || originalUrl.startsWith("https://", ignoreCase = true)
        val attemptUrl = if (hasScheme || isRetry) originalUrl else "https://$originalUrl"

        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            repository.fetchServerInfo(attemptUrl).fold(
                onSuccess = { info ->
                    isLoading = false
                    val cleanUrl = attemptUrl.trimEnd('/')
                    serverLogoUrl = info.logo?.name?.let { name ->
                        val encodedName = android.net.Uri.encode(name)
                        "$cleanUrl/public/$encodedName"
                    }
                    serverName = info.name
                    serverDescription = info.description

                    // save the validated URL
                    repository.saveServerUrl(cleanUrl)
                    serverUrl = cleanUrl

                    currentStep = LoginStep.CREDENTIALS
                },
                onFailure = { error ->
                    isLoading = false
                    if (!hasScheme && !isRetry) {
                        pendingInsecureUrl = "http://$originalUrl"
                        showInsecureConnectionPrompt = true
                    } else {
                        errorMessage = context.getString(
                            R.string.connect_connectError,
                            error.message ?: context.getString(R.string.settings_errorBadge)
                        )
                    }
                }
            )
        }
    }

    fun onInsecureConnectionConfirm(context: Context) {
        showInsecureConnectionPrompt = false
        pendingInsecureUrl?.let {
            serverUrl = it
            onNextClick(context, isRetry = true)
        }
        pendingInsecureUrl = null
    }

    fun onInsecureConnectionCancel() {
        showInsecureConnectionPrompt = false
        pendingInsecureUrl = null
    }

    // step 1b: User taps "Back" to change the server URL
    fun changeServer() {
        errorMessage = null
        currentStep = LoginStep.URL
        serverName = "Sharkord"
        serverDescription = null
        serverLogoUrl = null
    }

    // step 2: User taps "Connect" with identity + password
    fun onLoginClick(context: Context, onSuccess: () -> Unit, isBiometric: Boolean = false) {
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
                    
                    if (!isBiometric && !SharkordClient.session.hasBiometricCredentials() && isBiometricSupported(context)) {
                        pendingSuccessAction = onSuccess
                        showBiometricSavePrompt = true
                    } else {
                        onSuccess()
                    }
                },
                onFailure = { error ->
                    isLoading = false
                    if (isBiometric) {
                        errorMessage = "Credenziali scadute o errate. Effettua il login manualmente."
                        SharkordClient.session.clearBiometricCredentials()
                        password = ""
                    } else {
                        errorMessage = error.message ?: context.getString(R.string.settings_errorBadge)
                    }
                }
            )
        }
    }

    fun onBiometricSaveAnswer(save: Boolean) {
        if (save) {
            SharkordClient.session.saveBiometricCredentials(identity, password)
        }
        showBiometricSavePrompt = false
        pendingSuccessAction?.invoke()
        pendingSuccessAction = null
    }

    fun onBiometricLaunchSuccess() {
        showBiometricLaunchPrompt = false
        pendingSuccessAction?.invoke()
        pendingSuccessAction = null
    }

    fun onBiometricLaunchCancel() {
        showBiometricLaunchPrompt = false
        pendingSuccessAction = null
        SharkordClient.session.clearSession()
        isAutoLoggingIn = false
        hideSplashScreen = true
        currentStep = LoginStep.CREDENTIALS
    }

    fun isBiometricSupported(context: Context): Boolean {
        val biometricManager = androidx.biometric.BiometricManager.from(context)
        return biometricManager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    }
}
