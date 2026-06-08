package com.sharkord.android.ui.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharkord.android.data.model.JoinServerData
import com.sharkord.android.data.network.ConnectionState
import com.sharkord.android.data.network.ServerEvent
import com.sharkord.android.data.network.ServerEventHandler
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.data.repository.ServerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sharkord.android.data.model.UnifiedSearchResult
import com.sharkord.android.data.model.UnifiedMessageResult
import com.sharkord.android.data.model.UnifiedFileResult
import com.sharkord.android.data.model.SearchResults

enum class HomePanel {
    SERVER, CHAT
}

/**
 * UI state for the home screen.
 * Replaces the ~10 inline `remember { mutableStateOf }` calls that were in HomeScreen.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val serverData: JoinServerData? = null,
    val selectedChannelId: Int? = null,
    val selectedMessageId: Int? = null,
    val jumpTrigger: Long = 0L,
    val collapsedCategories: Set<Int> = emptySet(),
    val errorMessage: String? = null,
    val reconnectAttempts: Int = 0,
    val showProfileSheet: Boolean = false,
    val showMembersSheet: Boolean = false,
    val showServerSheet: Boolean = false,
    val showSearchSheet: Boolean = false,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<UnifiedSearchResult>? = null,
    val activePanel: HomePanel = HomePanel.SERVER
)

/**
 * ViewModel for the home/server screen.
 *
 * Extracts all business logic and state management from HomeScreen,
 * making it testable and keeping the Composable focused on rendering.
 *
 * After the initial joinServer connection succeeds, real-time tRPC subscription
 * events arrive via [ServerRepository.incomingEvents]. They are parsed by
 * [ServerEventHandler] and applied as incremental mutations to [serverData],
 * mirroring the web client's per-domain subscription handlers.
 */
class HomeViewModel : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val repository = ServerRepository()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * Debounce job for the reconnection banner.
     * Brief reconnects (< 3s) are invisible to the user — only persistent
     * disconnects show the "Connection lost. Reconnecting..." message.
     */
    private var reconnectBannerJob: Job? = null

    /** Connection state directly from the WebSocket manager. */
    val connectionState: StateFlow<ConnectionState>
        get() = repository.connectionState

    // Lifecycle

    /**
     * Called on first composition. Initiates the WebSocket connection and starts
     * observing both connection state changes and real-time subscription events.
     */
    fun connect() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        // Observe connection state changes
        viewModelScope.launch {
            repository.connectionState.collect { state ->
                handleConnectionStateChange(state)
            }
        }

        // Observe real-time subscription events pushed by the server after joinServer
        viewModelScope.launch {
            repository.incomingEvents.collect { event ->
                val parsed = ServerEventHandler.parse(event)
                if (parsed != null) {
                    applyServerEvent(parsed)
                } else {
                    Log.d(TAG, "Ignoring unhandled event path: ${event.path}")
                }
            }
        }

        // Fetch server details in background to ensure we always have the freshest server logo, name and description
        viewModelScope.launch {
            SharkordClient.currentServerUrl?.let { url ->
                repository.fetchServerInfo(url)
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
        // Fetch server details in background on reconnect as well
        viewModelScope.launch {
            SharkordClient.currentServerUrl?.let { url ->
                repository.fetchServerInfo(url)
            }
        }
        repository.connectWebSocket()
    }

    // Connection State Handling

    private fun handleConnectionStateChange(state: ConnectionState) {
        when (state) {
            is ConnectionState.Connected -> {
                // Cancel any pending reconnect banner — the connection recovered.
                reconnectBannerJob?.cancel()
                reconnectBannerJob = null

                val data = state.serverData
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                        reconnectAttempts = 0,
                        serverData = data,
                        selectedChannelId = it.selectedChannelId
                            ?: data.channels.firstOrNull { ch -> !ch.isVoice && !ch.isDm }?.id
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
                // Debounce the banner: only show it if the connection stays down
                // for more than 3 seconds. Brief reconnects are invisible to the user.
                if (reconnectBannerJob == null) {
                    reconnectBannerJob = viewModelScope.launch {
                        delay(3000)
                        _uiState.update {
                            it.copy(
                                errorMessage = if (it.serverData != null) "Connection lost. Reconnecting..." else it.errorMessage
                            )
                        }
                    }
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

    // Real-Time Event Application

    /**
     * Applies a typed [ServerEvent] as an incremental mutation to [HomeUiState.serverData].
     *
     * Mirrors the web client's per-domain action handlers (channels/actions.ts,
     * users/actions.ts, etc.) but expressed as direct state transformations.
     */
    private fun applyServerEvent(event: ServerEvent) {
        _uiState.update { state ->
            val data = state.serverData ?: return@update state

            when (event) {

                // Channels

                is ServerEvent.ChannelCreated -> {
                    Log.d(TAG, "[EVENT] channels.onCreate: ${event.channel.name}")
                    state.copy(
                        serverData = data.copy(
                            channels = data.channels + event.channel
                        )
                    )
                }

                is ServerEvent.ChannelDeleted -> {
                    Log.d(TAG, "[EVENT] channels.onDelete: id=${event.channelId}")
                    val newChannels = data.channels.filter { it.id != event.channelId }
                    state.copy(
                        serverData = data.copy(channels = newChannels),
                        // If the active channel was deleted, fall back to the first available one
                        selectedChannelId = if (state.selectedChannelId == event.channelId) {
                            newChannels.firstOrNull { !it.isVoice && !it.isDm }?.id
                        } else {
                            state.selectedChannelId
                        }
                    )
                }

                is ServerEvent.ChannelUpdated -> {
                    Log.d(TAG, "[EVENT] channels.onUpdate: ${event.channel.name}")
                    state.copy(
                        serverData = data.copy(
                            channels = data.channels.map { ch ->
                                if (ch.id == event.channel.id) event.channel else ch
                            }
                        )
                    )
                }

                // Categories

                is ServerEvent.CategoryCreated -> {
                    Log.d(TAG, "[EVENT] categories.onCreate: ${event.category.name}")
                    state.copy(
                        serverData = data.copy(
                            categories = (data.categories ?: emptyList()) + event.category
                        )
                    )
                }

                is ServerEvent.CategoryDeleted -> {
                    Log.d(TAG, "[EVENT] categories.onDelete: id=${event.categoryId}")
                    state.copy(
                        serverData = data.copy(
                            categories = data.categories?.filter { it.id != event.categoryId }
                        ),
                        // Remove the category from the collapsed set if it was collapsed
                        collapsedCategories = state.collapsedCategories - event.categoryId
                    )
                }

                is ServerEvent.CategoryUpdated -> {
                    Log.d(TAG, "[EVENT] categories.onUpdate: ${event.category.name}")
                    state.copy(
                        serverData = data.copy(
                            categories = data.categories?.map { cat ->
                                if (cat.id == event.category.id) event.category else cat
                            }
                        )
                    )
                }

                // Users

                is ServerEvent.UserJoined -> {
                    Log.d(TAG, "[EVENT] users.onJoin: ${event.user.name}")
                    // Add or replace (in case the user was already in the list with offline status)
                    state.copy(
                        serverData = data.copy(
                            users = data.users
                                .filter { it.id != event.user.id } + event.user
                        )
                    )
                }

                is ServerEvent.UserLeft -> {
                    Log.d(TAG, "[EVENT] users.onLeave: id=${event.userId}")
                    // Mark the user as offline rather than removing them from the list,
                    // matching the web client's updateUser(userId, { status: UserStatus.OFFLINE })
                    state.copy(
                        serverData = data.copy(
                            users = data.users.map { u ->
                                if (u.id == event.userId) u.copy(status = "offline") else u
                            }
                        )
                    )
                }

                is ServerEvent.UserCreated -> {
                    Log.d(TAG, "[EVENT] users.onCreate: ${event.user.name}")
                    state.copy(
                        serverData = data.copy(
                            users = data.users + event.user
                        )
                    )
                }

                is ServerEvent.UserUpdated -> {
                    Log.d(TAG, "[EVENT] users.onUpdate: ${event.user.name}")
                    state.copy(
                        serverData = data.copy(
                            users = data.users.map { u ->
                                if (u.id == event.user.id) event.user else u
                            }
                        )
                    )
                }

                is ServerEvent.UserDeleted -> {
                    Log.d(TAG, "[EVENT] users.onDelete: id=${event.userId}, isWipe=${event.isWipe}")
                    // Remove the deleted user; messages are handled by the server side.
                    // The web client either wipes or reassigns — for the sidebar user list,
                    // removal is the correct behaviour in both cases.
                    state.copy(
                        serverData = data.copy(
                            users = data.users.filter { it.id != event.userId }
                        )
                    )
                }

                // Roles

                is ServerEvent.RoleCreated -> {
                    Log.d(TAG, "[EVENT] roles.onCreate: ${event.role.name}")
                    state.copy(
                        serverData = data.copy(
                            roles = (data.roles ?: emptyList()) + event.role
                        )
                    )
                }

                is ServerEvent.RoleDeleted -> {
                    Log.d(TAG, "[EVENT] roles.onDelete: id=${event.roleId}")
                    state.copy(
                        serverData = data.copy(
                            roles = data.roles?.filter { it.id != event.roleId }
                        )
                    )
                }

                is ServerEvent.RoleUpdated -> {
                    Log.d(TAG, "[EVENT] roles.onUpdate: ${event.role.name}")
                    state.copy(
                        serverData = data.copy(
                            roles = data.roles?.map { r ->
                                if (r.id == event.role.id) event.role else r
                            }
                        )
                    )
                }

                // Emojis

                is ServerEvent.EmojiCreated -> {
                    Log.d(TAG, "[EVENT] emojis.onCreate: ${event.emoji.name}")
                    state.copy(
                        serverData = data.copy(
                            emojis = (data.emojis ?: emptyList()) + event.emoji
                        )
                    )
                }

                is ServerEvent.EmojiDeleted -> {
                    Log.d(TAG, "[EVENT] emojis.onDelete: id=${event.emojiId}")
                    state.copy(
                        serverData = data.copy(
                            emojis = data.emojis?.filter { it.id != event.emojiId }
                        )
                    )
                }

                is ServerEvent.EmojiUpdated -> {
                    Log.d(TAG, "[EVENT] emojis.onUpdate: ${event.emoji.name}")
                    state.copy(
                        serverData = data.copy(
                            emojis = data.emojis?.map { e ->
                                if (e.id == event.emoji.id) event.emoji else e
                            }
                        )
                    )
                }

                // Messages
                // Message state is managed by ChatViewModel, which observes incomingEvents
                // directly and applies per-channel mutations. HomeViewModel only sees these
                // events here as a no-op to keep the exhaustive when() complete.

                is ServerEvent.MessageReceived -> state
                is ServerEvent.MessageUpdated -> state
                is ServerEvent.MessageDeleted -> state
                is ServerEvent.UserTyping -> state

                // Server Settings

                is ServerEvent.ServerSettingsUpdated -> {
                    Log.d(TAG, "[EVENT] others.onServerSettingsUpdate: name=${event.settings.name}")
                    state.copy(
                        serverData = data.copy(
                            // Update the display name shown in the server header
                            serverName = event.settings.name ?: data.serverName,
                            publicSettings = event.settings
                        )
                    )
                }
            }
        }
    }

    // UI Actions

    fun selectChannel(channelId: Int, messageId: Int? = null) {
        _uiState.update { 
            it.copy(
                selectedChannelId = channelId, 
                selectedMessageId = messageId, 
                activePanel = HomePanel.CHAT,
                jumpTrigger = if (messageId != null) System.currentTimeMillis() else it.jumpTrigger
            ) 
        }
    }

    fun setPanel(panel: HomePanel) {
        _uiState.update { it.copy(activePanel = panel) }
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

    fun showServerSheet() {
        _uiState.update { it.copy(showServerSheet = true) }
    }

    fun dismissServerSheet() {
        _uiState.update { it.copy(showServerSheet = false) }
    }

    fun showSearchSheet() {
        _uiState.update { it.copy(showSearchSheet = true) }
    }

    fun dismissSearchSheet() {
        _uiState.update { it.copy(showSearchSheet = false, searchQuery = "", searchResults = null) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun performSearch() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) {
            _uiState.update { it.copy(searchResults = null, isSearching = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            try {
                val input = JsonObject().apply { addProperty("query", query) }
                val response = SharkordClient.webSocket.sendQueryAwait("messages.search", input)
                val results = Gson().fromJson(response, SearchResults::class.java)

                val unifiedList = mutableListOf<UnifiedSearchResult>()
                results.messages.forEach { msg ->
                    unifiedList.add(UnifiedMessageResult(key = "msg_${msg.id}", createdAt = msg.createdAt, item = msg))
                }
                results.files.forEach { file ->
                    unifiedList.add(UnifiedFileResult(key = "file_${file.file.id}", createdAt = file.messageCreatedAt, item = file))
                }
                unifiedList.sortByDescending { it.createdAt }

                _uiState.update { it.copy(searchResults = unifiedList, isSearching = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
                _uiState.update { it.copy(isSearching = false, searchResults = emptyList()) }
            }
        }
    }

    /**
     * Opens a direct message channel with the given user and navigates to it.
     */
    fun openDirectMessage(userId: Int) {
        viewModelScope.launch {
            val result = repository.openDirectMessage(userId)
            result.onSuccess { channelId ->
                selectChannel(channelId)
            }
            result.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Failed to open DM") }
            }
        }
    }

    /**
     * Performs logout: disconnects WebSocket, clears session, and navigates back.
     */
    fun logout(context: Context) {
        repository.logout()
    }

    override fun onCleared() {
        super.onCleared()
        reconnectBannerJob?.cancel()
        // Don't disconnect here — the WebSocket should stay alive
        // even during config changes. Only disconnect on explicit logout.
    }
}
