package com.sharkord.android.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharkord.android.data.model.JoinServerData
import com.sharkord.android.data.network.ConnectionState
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.data.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the home screen.
 * Replaces the ~10 inline `remember { mutableStateOf }` calls that were in HomeScreen.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val serverData: JoinServerData? = null,
    val selectedChannelId: Int? = null,
    val collapsedCategories: Set<Int> = emptySet(),
    val errorMessage: String? = null,
    val reconnectAttempts: Int = 0,
    val showProfileSheet: Boolean = false,
    val showMembersSheet: Boolean = false
)

/**
 * ViewModel for the home/server screen.
 *
 * Extracts all business logic and state management from HomeScreen,
 * making it testable and keeping the Composable focused on rendering.
 */
class HomeViewModel : ViewModel() {

    private val repository = ServerRepository()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** Connection state directly from the WebSocket manager. */
    val connectionState: StateFlow<ConnectionState>
        get() = repository.connectionState

    // ─── Lifecycle ────────────────────────────────────────────

    /**
     * Called on first composition. Initiates the WebSocket connection.
     */
    fun connect() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        // Observe connection state changes
        viewModelScope.launch {
            repository.connectionState.collect { state ->
                handleConnectionStateChange(state)
            }
        }

        // Initiate the WebSocket connection
        if (!repository.connectWebSocket()) {
            _uiState.update {
                it.copy(isLoading = false, errorMessage = "Missing server URL or token")
            }
        }
    }

    /**
     * Reconnects the WebSocket (e.g., after manual retry or background reconnect).
     */
    fun reconnect(showFullscreenLoading: Boolean = true) {
        _uiState.update {
            it.copy(
                isLoading = showFullscreenLoading,
                errorMessage = null
            )
        }
        repository.connectWebSocket()
    }

    // ─── Connection State Handling ────────────────────────────

    private fun handleConnectionStateChange(state: ConnectionState) {
        when (state) {
            is ConnectionState.Connected -> {
                val data = state.serverData
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                        reconnectAttempts = 0,
                        serverData = data,
                        selectedChannelId = it.selectedChannelId
                            ?: data.channels.firstOrNull()?.id
                    )
                }
            }

            is ConnectionState.Error -> {
                val attempts = _uiState.value.reconnectAttempts + 1
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        reconnectAttempts = attempts,
                        // Only show error to user after 3 failed attempts
                        // (if we already have data, show banner instead of fullscreen error)
                        errorMessage = if (attempts >= 3) state.message else it.errorMessage
                    )
                }
            }

            is ConnectionState.Reconnecting -> {
                _uiState.update {
                    it.copy(
                        errorMessage = if (it.serverData != null) "Connection lost. Reconnecting..." else it.errorMessage
                    )
                }
            }

            is ConnectionState.Connecting,
            is ConnectionState.Authenticating,
            is ConnectionState.HandshakePending,
            is ConnectionState.Disconnected -> {
                // No UI changes needed for intermediate states
            }

            is ConnectionState.JoinPending -> {
                // No UI changes needed
            }
        }
    }

    // ─── UI Actions ───────────────────────────────────────────

    fun selectChannel(channelId: Int) {
        _uiState.update { it.copy(selectedChannelId = channelId) }
    }

    fun toggleCategory(categoryId: Int) {
        _uiState.update { state ->
            val newCollapsed = state.collapsedCategories.toMutableSet()
            if (categoryId in newCollapsed) {
                newCollapsed.remove(categoryId)
            } else {
                newCollapsed.add(categoryId)
            }
            state.copy(collapsedCategories = newCollapsed)
        }
    }

    fun showProfileSheet() {
        _uiState.update { it.copy(showProfileSheet = true) }
    }

    fun dismissProfileSheet() {
        _uiState.update { it.copy(showProfileSheet = false) }
    }

    fun showMembersSheet() {
        _uiState.update { it.copy(showMembersSheet = true) }
    }

    fun dismissMembersSheet() {
        _uiState.update { it.copy(showMembersSheet = false) }
    }

    /**
     * Performs logout: disconnects WebSocket, clears session, and navigates back.
     */
    fun logout(context: Context) {
        repository.logout()
    }

    override fun onCleared() {
        super.onCleared()
        // Don't disconnect here — the WebSocket should stay alive
        // even during config changes. Only disconnect on explicit logout.
    }
}
