package com.sharkord.android.ui.login

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.sharkord.android.R
import com.sharkord.android.data.network.SharkordClient

enum class LoginStep {
    URL,
    CREDENTIALS
}

class LoginViewModel : ViewModel() {
    var serverUrl by mutableStateOf("")
    var identity by mutableStateOf("")
    var password by mutableStateOf("")
    
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var loginSuccess by mutableStateOf(false)

    var currentStep by mutableStateOf(LoginStep.URL)
    var serverLogoUrl by mutableStateOf<String?>(null)
    var serverName by mutableStateOf("Sharkord")
    var serverDescription by mutableStateOf<String?>(null)

    fun initialize(context: Context) {
        if (serverUrl.isBlank()) {
            val sharedPrefs = context.getSharedPreferences("sharkord_prefs", Context.MODE_PRIVATE)
            val savedUrl = sharedPrefs.getString("server_url", "") ?: ""
            if (savedUrl.isNotBlank()) {
                serverUrl = savedUrl
                currentStep = LoginStep.CREDENTIALS
                // Fetch server info (e.g. name, description, logo) asynchronously
                fetchServerDetails(context, savedUrl)
            } else {
                currentStep = LoginStep.URL
            }
        }
    }

    private fun fetchServerDetails(context: Context, url: String) {
        SharkordClient.fetchServerInfo(
            context = context,
            serverUrl = url,
            onSuccess = { info ->
                val cleanUrl = url.trimEnd('/')
                serverLogoUrl = info.logo?.name?.let { "$cleanUrl/public/$it" }
                serverName = info.name
                serverDescription = info.description
            },
            onError = {
                // Ignore error if it fails on startup, since we already saved it
            }
        )
    }

    fun onNextClick(context: Context) {
        if (serverUrl.isBlank()) {
            errorMessage = context.getString(R.string.connect_invalidUrlError)
            return
        }

        isLoading = true
        errorMessage = null

        SharkordClient.fetchServerInfo(
            context = context,
            serverUrl = serverUrl,
            onSuccess = { info ->
                isLoading = false
                val cleanUrl = serverUrl.trimEnd('/')
                serverLogoUrl = info.logo?.name?.let { "$cleanUrl/public/$it" }
                serverName = info.name
                serverDescription = info.description
                
                // Save URL in SharedPreferences
                val sharedPrefs = context.getSharedPreferences("sharkord_prefs", Context.MODE_PRIVATE)
                sharedPrefs.edit().putString("server_url", cleanUrl).apply()
                
                currentStep = LoginStep.CREDENTIALS
            },
            onError = { error ->
                isLoading = false
                errorMessage = context.getString(R.string.connect_connectError, error)
            }
        )
    }

    fun changeServer() {
        errorMessage = null
        currentStep = LoginStep.URL
        serverName = "Sharkord"
        serverDescription = null
        serverLogoUrl = null
    }

    fun onLoginClick(context: Context, onSuccess: () -> Unit) {
        if (serverUrl.isBlank() || identity.isBlank() || password.isBlank()) {
            errorMessage = context.getString(R.string.settings_errorBadge)
            return
        }

        isLoading = true
        errorMessage = null

        SharkordClient.login(
            context = context,
            serverUrl = serverUrl,
            identity = identity,
            password = password,
            onSuccess = { token ->
                isLoading = false
                loginSuccess = true
                onSuccess()
            },
            onError = { error ->
                isLoading = false
                errorMessage = error
            }
        )
    }
}
