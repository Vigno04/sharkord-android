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
    SERVER_LIST, SERVER_CHAT, DMS_LIST, DM_CHAT
}

// UI state for the home screen
// replaces the ~10 inline `remember { mutableStateOf }` calls that were in HomeScreen
data class HomeUiState(
    val isLoading: Boolean = true,
    val serverData: JoinServerData? = null,
    val selectedServerChannelId: Int? = null,
    val selectedDmChannelId: Int? = null,
    val isDmsListSelected: Boolean = false,
    val selectedMessageId: Int? = null,
    val jumpTrigger: Long = 0L,
    val collapsedCategories: Set<Int> = emptySet(),
    val errorMessage: String? = null,
    val reconnectAttempts: Int = 0,
    val profileSheetUserId: Int? = null,
    val showMembersSheet: Boolean = false,
    val showServerSheet: Boolean = false,
    val showSearchSheet: Boolean = false,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<UnifiedSearchResult>? = null,
    val activePanel: HomePanel = HomePanel.SERVER_LIST,
    val showAddChannelDialog: Boolean = false,
    val addChannelCategoryId: Int? = null,
    val showAddCategoryDialog: Boolean = false,
    val showDeleteChannelDialogForId: Int? = null,
    val membersSheetFilterDms: Boolean = false,
    val readStates: Map<Int, Int> = emptyMap(),
    val isViewingVoiceChat: Boolean = false
)

// viewModel for the home/server screen
// extracts all business logic and state management from HomeScreen,
// making it testable and keeping the Composable focused on rendering
// after the initial joinServer connection succeeds, real-time tRPC subscription
// events arrive via [ServerRepository.incomingEvents]. They are parsed by
// [ServerEventHandler] and applied as incremental mutations to [serverData],
// mirroring the web client's per-domain subscription handlers
class HomeViewModel : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val repository = ServerRepository()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // debounce job for the reconnection banner
    // brief reconnects (< 3s) are invisible to the user — only persistent
    // disconnects show the "Connection lost. Reconnecting..." message
    private var reconnectBannerJob: Job? = null
    
    private var typingJob: Job? = null
    private var preDeafenMicMuted: Boolean = false
    private var hasPlayedDisconnectSound = false

    // connection state directly from the WebSocket manager
    val connectionState: StateFlow<ConnectionState>
        get() = repository.connectionState

    init {
        // voice-related initialization moved to VoiceViewModel
        
        // sync active channel to SharkordClient for notifications
        viewModelScope.launch {
            uiState.collect { state ->
                val activeChannel = if (state.activePanel == com.sharkord.android.ui.home.HomePanel.SERVER_CHAT) {
                    state.selectedServerChannelId
                } else if (state.activePanel == com.sharkord.android.ui.home.HomePanel.DM_CHAT) {
                    state.selectedDmChannelId
                } else {
                    null
                }
                SharkordClient.activeChannelId.value = activeChannel
            }
        }

        // observe connection state changes
        viewModelScope.launch {
            repository.connectionState.collect { state ->
                handleConnectionStateChange(state)
            }
        }

        // observe real-time subscription events pushed by the server after joinServer
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

        // observe global jump events
        viewModelScope.launch {
            com.sharkord.android.ui.navigation.MessageNavigationManager.jumpEvents.collect { event ->
                selectChannel(channelId = event.channelId, messageId = event.messageId, navigateToChat = true)
            }
        }

        // observe explicit mark as read events (e.g. from notifications)
        viewModelScope.launch {
            SharkordClient.channelReadEvents.collect { channelId ->
                _uiState.update { state ->
                    val newMap = state.readStates.toMutableMap()
                    newMap[channelId] = 0
                    state.copy(readStates = newMap)
                }
            }
        }
    }

    // lifecycle

    // called on first composition. Initiates the WebSocket connection
    fun connect() {
        if (repository.connectionState.value.isConnected || repository.connectionState.value.isInProgress) {
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        // fetch server details in background to ensure freshest server logo, name and description
        viewModelScope.launch {
            SharkordClient.currentServerUrl?.let { url ->
                repository.fetchServerInfo(url)
            }
        }

        // initiate the WebSocket connection
        if (!repository.connectWebSocket()) {
            _uiState.update {
                it.copy(isLoading = false, errorMessage = "Missing server URL or token")
            }
        }
    }

    // reconnects the WebSocket (e.g., after manual retry or background reconnect)
    fun reconnect(showFullscreenLoading: Boolean = true) {
        _uiState.update {
            it.copy(
                isLoading = showFullscreenLoading,
                errorMessage = null
            )
        }
        // fetch server details in background on reconnect as well
        viewModelScope.launch {
            SharkordClient.currentServerUrl?.let { url ->
                repository.fetchServerInfo(url)
            }
        }
        repository.connectWebSocket()
    }

    // connection State Handling

    private fun handleConnectionStateChange(state: ConnectionState) {
        when (state) {
            is ConnectionState.Connected -> {
                if (hasPlayedDisconnectSound) {
                    com.sharkord.android.audio.SoundEngine.playSound(com.sharkord.android.audio.SoundType.SERVER_RECONNECTED)
                }
                hasPlayedDisconnectSound = false
                // cancel any pending reconnect banner — the connection recovered
                reconnectBannerJob?.cancel()
                reconnectBannerJob = null

                val data = state.serverData
                
                // parse string-keyed map into Int-keyed map for readStates
                val initialReadStates = data.readStates?.mapNotNull { (key, value) ->
                    val id = key.toIntOrNull()
                    if (id != null) id to value else null
                }?.toMap() ?: emptyMap()

                val restoredVoiceChannelId = SharkordClient.voiceEngine.currentChannelId

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                        reconnectAttempts = 0,
                        serverData = data,
                        readStates = initialReadStates,
                        selectedServerChannelId = it.selectedServerChannelId
                            ?: restoredVoiceChannelId
                            ?: data.channels.firstOrNull { ch -> !ch.isVoice && !ch.isDm }?.id,
                        selectedDmChannelId = it.selectedDmChannelId,
                        isDmsListSelected = it.isDmsListSelected
                    )
                }
            }

            is ConnectionState.Error -> {
                if (!hasPlayedDisconnectSound && _uiState.value.serverData != null) {
                    com.sharkord.android.audio.SoundEngine.playSound(com.sharkord.android.audio.SoundType.SERVER_DISCONNECTED)
                    hasPlayedDisconnectSound = true
                }
                
                val attempts = _uiState.value.reconnectAttempts + 1
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        reconnectAttempts = attempts,
                        // only show error to user after 3 failed attempts
                        // (if data exists, show banner instead of fullscreen error)
                        errorMessage = if (attempts >= 3) state.message else it.errorMessage
                    )
                }
            }

            is ConnectionState.Reconnecting -> {
                // debounce the banner: only show it if the connection stays down
                // for more than 3 seconds. Brief reconnects are invisible to the user
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
            is ConnectionState.HandshakePending -> {
                // no UI changes needed for intermediate states
            }
            
            is ConnectionState.Disconnected -> {
                // we don't play the disconnect sound here anymore, since this state
                // is used for expected disconnects (closing app, going to background)
            }

            is ConnectionState.JoinPending -> {
                // no UI changes needed
            }
        }
    }

    // real-Time Event Application

    // applies a typed [ServerEvent] as an incremental mutation to [HomeUiState.serverData]
    // mirrors the web client's per-domain action handlers (channels/actions.ts,
    // users/actions.ts, etc.) but expressed as direct state transformations
    private fun applyServerEvent(event: ServerEvent) {
        _uiState.update { state ->
            val data = state.serverData ?: return@update state

            when (event) {

                // channels

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
                        // if the active channel was deleted, fall back to the first available one
                        selectedServerChannelId = if (state.selectedServerChannelId == event.channelId) {
                            newChannels.firstOrNull { !it.isVoice && !it.isDm }?.id
                        } else {
                            state.selectedServerChannelId
                        },
                        selectedDmChannelId = if (state.selectedDmChannelId == event.channelId) {
                            null
                        } else {
                            state.selectedDmChannelId
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

                // categories

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
                        // remove the category from the collapsed set if it was collapsed
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

                // users

                is ServerEvent.UserJoined -> {
                    Log.d(TAG, "[EVENT] users.onJoin: ${event.user.name}")
                    // add or replace (in case the user was already in the list with offline status)
                    state.copy(
                        serverData = data.copy(
                            users = data.users
                                .filter { it.id != event.user.id } + event.user
                        )
                    )
                }

                is ServerEvent.UserLeft -> {
                    Log.d(TAG, "[EVENT] users.onLeave: id=${event.userId}")
                    // mark the user as offline rather than removing them from the list,
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
                    // remove the deleted user; messages are handled by the server side
                    // the web client either wipes or reassigns — for the sidebar user list,
                    // removal is the correct behaviour in both cases
                    state.copy(
                        serverData = data.copy(
                            users = data.users.filter { it.id != event.userId }
                        )
                    )
                }

                // roles

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

                // emojis

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

                // messages
                // message state is managed by ChatViewModel, which observes incomingEvents
                // directly and applies per-channel mutations. HomeViewModel only sees these
                // events here as a no-op to keep the exhaustive when() complete

                is ServerEvent.MessageReceived -> {
                    if (event.message.userId != data.ownUserId) {
                        val prefs = SharkordClient.applicationContext.getSharedPreferences("SharkordSettings", android.content.Context.MODE_PRIVATE)
                        val notifyAll = prefs.getBoolean("notif_all_messages", false)
                        val notifyMentions = prefs.getBoolean("notif_mentions_only", false)
                        val notifyDms = prefs.getBoolean("notif_dms", false)
                        val notifyReplies = prefs.getBoolean("notif_replies", false)

                        val channel = data.channels.find { it.id == event.message.channelId }
                        val isDm = channel?.isDm == true
                        val ownUser = data.users.find { it.id == data.ownUserId }

                        val isReplyToMe = event.message.replyTo?.userId == data.ownUserId
                        val isMention = ownUser != null && event.message.content.contains("@${ownUser.name}")

                        val shouldNotify = when {
                            isDm -> notifyDms || notifyAll
                            notifyAll -> true
                            notifyReplies && isReplyToMe -> true
                            notifyMentions && isMention -> true
                            else -> false
                        }

                        if (shouldNotify) {
                            com.sharkord.android.audio.SoundEngine.playSound(com.sharkord.android.audio.SoundType.MESSAGE_RECEIVED)
                            
                            // fire a local notification if we aren't looking at this channel
                            if (SharkordClient.activeChannelId.value != event.message.channelId) {
                                val channelName = channel?.name
                                val senderName = data.users.find { it.id == event.message.userId }?.name ?: "Someone"
                                val replyToUserId = event.message.replyTo?.userId
                                val replyToName = if (replyToUserId != null) {
                                    if (replyToUserId == data.ownUserId) "you" else data.users.find { it.id == replyToUserId }?.name
                                } else null
                                
                                com.sharkord.android.utils.NotificationHelper.showNewMessageNotification(
                                    context = SharkordClient.applicationContext,
                                    channelId = event.message.channelId,
                                    senderName = senderName,
                                    messageContent = event.message.content,
                                    channelName = channelName,
                                    replyToName = replyToName
                                )
                            }
                        }
                    }
                    state
                }
                is ServerEvent.MessageUpdated -> state
                is ServerEvent.MessageDeleted -> state
                is ServerEvent.UserTyping -> state

                // voice
                is ServerEvent.UserJoinedVoice -> {
                    Log.d(TAG, "[EVENT] voice.onJoin: channelId=${event.channelId}, userId=${event.userId}")
                    if (SharkordClient.voiceEngine.currentChannelId == event.channelId && data.ownUserId != event.userId) {
                        com.sharkord.android.audio.SoundEngine.playSound(com.sharkord.android.audio.SoundType.REMOTE_USER_JOINED_VOICE_CHANNEL)
                    }
                    val newVoiceMap = data.voiceMap?.toMutableMap() ?: mutableMapOf()
                    val channelUsers = newVoiceMap[event.channelId.toString()]?.users?.toMutableMap() ?: mutableMapOf()
                    channelUsers[event.userId.toString()] = event.state
                    newVoiceMap[event.channelId.toString()] = com.sharkord.android.data.model.ServerChannelVoiceState(users = channelUsers)
                    state.copy(serverData = data.copy(voiceMap = newVoiceMap))
                }

                is ServerEvent.UserLeftVoice -> {
                    Log.d(TAG, "[EVENT] voice.onLeave: channelId=${event.channelId}, userId=${event.userId}")
                    if (SharkordClient.voiceEngine.currentChannelId == event.channelId && data.ownUserId != event.userId) {
                        com.sharkord.android.audio.SoundEngine.playSound(com.sharkord.android.audio.SoundType.REMOTE_USER_LEFT_VOICE_CHANNEL)
                    }

                    val newVoiceMap = data.voiceMap?.toMutableMap() ?: return@update state
                    val channelUsers = newVoiceMap[event.channelId.toString()]?.users?.toMutableMap() ?: return@update state
                    channelUsers.remove(event.userId.toString())
                    if (channelUsers.isEmpty()) {
                        newVoiceMap.remove(event.channelId.toString())
                    } else {
                        newVoiceMap[event.channelId.toString()] = com.sharkord.android.data.model.ServerChannelVoiceState(users = channelUsers)
                    }
                    state.copy(serverData = data.copy(voiceMap = newVoiceMap))
                }

                is ServerEvent.UserVoiceStateUpdated -> {
                    Log.d(TAG, "[EVENT] voice.onUpdateState: channelId=${event.channelId}, userId=${event.userId}")
                    val newVoiceMap = data.voiceMap?.toMutableMap() ?: mutableMapOf()
                    val channelUsers = newVoiceMap[event.channelId.toString()]?.users?.toMutableMap() ?: mutableMapOf()
                    channelUsers[event.userId.toString()] = event.state
                    newVoiceMap[event.channelId.toString()] = com.sharkord.android.data.model.ServerChannelVoiceState(users = channelUsers)
                    state.copy(serverData = data.copy(voiceMap = newVoiceMap))
                }

                is ServerEvent.VoiceNewProducer -> {
                    Log.d(TAG, "[EVENT] voice.onNewProducer: channelId=${event.channelId}, remoteId=${event.remoteId}")
                    state
                }

                is ServerEvent.VoiceProducerClosed -> {
                    Log.d(TAG, "[EVENT] voice.onProducerClosed: channelId=${event.channelId}, remoteId=${event.remoteId}")
                    state
                }

                // read States
                is ServerEvent.ChannelReadStateUpdate -> {
                    Log.d(TAG, "[EVENT] channels.onReadStateUpdate: channelId=${event.channelId}, count=${event.count}")
                    // if the channel is actively viewed, ignore updates and instantly mark as read
                    if ((state.selectedServerChannelId == event.channelId && state.activePanel == HomePanel.SERVER_CHAT) || 
                        (state.selectedDmChannelId == event.channelId && state.activePanel == HomePanel.DM_CHAT)) {
                        viewModelScope.launch { repository.markChannelAsRead(event.channelId) }
                        return@update state
                    }
                    val newMap = state.readStates.toMutableMap()
                    newMap[event.channelId] = event.count
                    state.copy(readStates = newMap)
                }

                is ServerEvent.ChannelReadStateDelta -> {
                    Log.d(TAG, "[EVENT] channels.onReadStateDelta: channelId=${event.channelId}, delta=${event.delta}")
                    if ((state.selectedServerChannelId == event.channelId && state.activePanel == HomePanel.SERVER_CHAT) || 
                        (state.selectedDmChannelId == event.channelId && state.activePanel == HomePanel.DM_CHAT)) {
                        viewModelScope.launch { repository.markChannelAsRead(event.channelId) }
                        return@update state
                    }
                    val currentCount = state.readStates[event.channelId] ?: 0
                    val newCount = kotlin.math.max(0, currentCount + event.delta)
                    val newMap = state.readStates.toMutableMap()
                    newMap[event.channelId] = newCount
                    state.copy(readStates = newMap)
                }

                // server Settings

                is ServerEvent.ServerSettingsUpdated -> {
                    Log.d(TAG, "[EVENT] others.onServerSettingsUpdate: name=${event.settings.name}")
                    state.copy(
                        serverData = data.copy(
                            // update the display name shown in the server header
                            serverName = event.settings.name ?: data.serverName,
                            publicSettings = event.settings
                        )
                    )
                }
            }
        }
    }

    // UI actions

    fun selectChannel(channelId: Int, messageId: Int? = null, navigateToChat: Boolean = true) {
        val channel = _uiState.value.serverData?.channels?.find { it.id == channelId }
        val isDm = channel?.isDm == true

        _uiState.update { state ->
            if (isDm) {
                state.copy(
                    selectedDmChannelId = channelId,
                    selectedMessageId = messageId,
                    activePanel = if (navigateToChat) HomePanel.DM_CHAT else state.activePanel,
                    jumpTrigger = if (messageId != null) System.currentTimeMillis() else state.jumpTrigger,
                    readStates = if (navigateToChat) {
                        state.readStates.toMutableMap().apply { put(channelId, 0) }
                    } else state.readStates
                )
            } else {
                state.copy(
                    selectedServerChannelId = channelId,
                    selectedMessageId = messageId,
                    isDmsListSelected = false,
                    activePanel = if (navigateToChat) HomePanel.SERVER_CHAT else state.activePanel,
                    jumpTrigger = if (messageId != null) System.currentTimeMillis() else state.jumpTrigger,
                    readStates = if (navigateToChat) {
                        state.readStates.toMutableMap().apply { put(channelId, 0) }
                    } else state.readStates
                )
            }
        }
        
        // notify the server that we have read the channel
        viewModelScope.launch {
            repository.markChannelAsRead(channelId)
        }
    }

    fun setPanel(panel: HomePanel) {
        _uiState.update { state ->
            var newReadStates = state.readStates
            if ((panel == HomePanel.SERVER_CHAT && state.selectedServerChannelId != null) || 
                (panel == HomePanel.DM_CHAT && state.selectedDmChannelId != null)) {
                newReadStates = state.readStates.toMutableMap().apply {
                    state.selectedServerChannelId?.let { put(it, 0) }
                    state.selectedDmChannelId?.let { put(it, 0) }
                }
            }
            state.copy(
                activePanel = panel,
                readStates = newReadStates
            )
        }

        if (panel == HomePanel.SERVER_CHAT) {
            _uiState.value.selectedServerChannelId?.let { channelId ->
                viewModelScope.launch {
                    repository.markChannelAsRead(channelId)
                }
            }
        } else if (panel == HomePanel.DM_CHAT) {
            _uiState.value.selectedDmChannelId?.let { channelId ->
                viewModelScope.launch {
                    repository.markChannelAsRead(channelId)
                }
            }
        }
    }

    fun setViewingVoiceChat(viewing: Boolean) {
        _uiState.update { it.copy(isViewingVoiceChat = viewing) }
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

    fun showProfileSheet(userId: Int? = null) {
        val idToUse = userId ?: _uiState.value.serverData?.ownUserId
        _uiState.update { it.copy(profileSheetUserId = idToUse) }
    }

    fun dismissProfileSheet() {
        _uiState.update { it.copy(profileSheetUserId = null) }
    }

    fun showMembersSheet(filterDms: Boolean = false) {
        _uiState.update { it.copy(showMembersSheet = true, membersSheetFilterDms = filterDms) }
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

    fun openDmsList() {
        _uiState.update { it.copy(activePanel = HomePanel.DMS_LIST, isDmsListSelected = true) }
    }

    fun exitDmsListToServer() {
        _uiState.update { it.copy(activePanel = HomePanel.SERVER_LIST) }
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

    fun showAddChannelDialog(categoryId: Int?) {
        _uiState.update { it.copy(showAddChannelDialog = true, addChannelCategoryId = categoryId) }
    }

    fun dismissAddChannelDialog() {
        _uiState.update { it.copy(showAddChannelDialog = false, addChannelCategoryId = null) }
    }

    fun createChannel(name: String, type: com.sharkord.android.data.model.ChannelType, categoryId: Int?) {
        viewModelScope.launch {
            val result = repository.createChannel(name, type, categoryId)
            if (result.isSuccess) {
                dismissAddChannelDialog()
            }
            result.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Failed to create channel") }
            }
        }
    }

    fun showAddCategoryDialog() {
        _uiState.update { it.copy(showAddCategoryDialog = true) }
    }

    fun dismissAddCategoryDialog() {
        _uiState.update { it.copy(showAddCategoryDialog = false) }
    }

    fun createCategory(name: String) {
        viewModelScope.launch {
            val result = repository.createCategory(name)
            if (result.isSuccess) {
                dismissAddCategoryDialog()
            }
            result.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Failed to create category") }
            }
        }
    }

    fun showDeleteChannelDialog(channelId: Int) {
        _uiState.update { it.copy(showDeleteChannelDialogForId = channelId) }
    }

    fun dismissDeleteChannelDialog() {
        _uiState.update { it.copy(showDeleteChannelDialogForId = null) }
    }

    fun deleteChannel(channelId: Int) {
        viewModelScope.launch {
            val result = repository.deleteChannel(channelId)
            if (result.isSuccess) {
                dismissDeleteChannelDialog()
            } else {
                _uiState.update { it.copy(errorMessage = result.exceptionOrNull()?.message ?: "Failed to delete channel") }
            }
        }
    }

    fun reorderChannels(categoryId: Int, channelIds: List<Int>) {
        // optimistic update to prevent visual glitches while websocket events stream in
        _uiState.update { state ->
            val data = state.serverData ?: return@update state
            val currentChannels = data.channels.toMutableList()
            
            var currentPosition = 1
            for (id in channelIds) {
                val index = currentChannels.indexOfFirst { it.id == id }
                if (index != -1) {
                    val channel = currentChannels[index]
                    currentChannels[index] = channel.copy(position = currentPosition++)
                }
            }
            state.copy(serverData = data.copy(channels = currentChannels))
        }

        viewModelScope.launch {
            val result = repository.reorderChannels(categoryId, channelIds)
            result.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "Failed to reorder channels") }
            }
        }
    }

    // opens a direct message channel with the given user and navigates to it
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

    // performs logout: disconnects WebSocket, clears session, and navigates back
    fun logout(context: Context) {
        repository.logout()
    }

    override fun onCleared() {
        super.onCleared()
        reconnectBannerJob?.cancel()
        // don't disconnect here — the WebSocket should stay alive
        // even during config changes. Only disconnect on explicit logout
    }
}
