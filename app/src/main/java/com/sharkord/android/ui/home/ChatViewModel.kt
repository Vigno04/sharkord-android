package com.sharkord.android.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharkord.android.data.model.Message
import com.sharkord.android.data.model.User
import com.sharkord.android.data.model.Role
import com.sharkord.android.data.network.ServerEvent
import com.sharkord.android.data.network.ServerEventHandler
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.data.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import com.sharkord.android.data.network.ConnectionState

/**
 * UI state for the chat panel of a single channel.
 */
data class ChatUiState(
    /** Message list, newest at the end (chronological order). */
    val messages: List<Message> = emptyList(),
    /** True while the initial page of messages is loading. */
    val isLoadingHistory: Boolean = true,
    /** True while older messages (pagination) are loading. */
    val isLoadingOlder: Boolean = false,
    /** True while a send is in-flight. */
    val isSending: Boolean = false,
    /** Non-null when an error occurred that should be surfaced to the user. */
    val errorMessage: String? = null,
    /** Cursor for the next older page. Null when there are no more older messages. */
    val nextCursor: Long? = null,
    /** True once we've loaded all the way to the top (no more older messages). */
    val hasReachedTop: Boolean = false,
    /** Active reply target message. */
    val replyTarget: Message? = null,
    /** Active editing message. Null when not in editing mode. */
    val editingMessage: Message? = null,
    /** Set of active typing user IDs in this channel. */
    val typingUsers: Set<Int> = emptySet(),
    /** Pinned messages for this channel. */
    val pinnedMessages: List<Message> = emptyList(),
    /** True while pinned messages are loading. */
    val isLoadingPinned: Boolean = false,
    /** Controls pinned messages overlay visibility. */
    val showPinnedMessages: Boolean = false,
    /** Pending attached files. */
    val attachedFiles: List<com.sharkord.android.data.model.FileInfo> = emptyList(),
    /** True while an attachment upload is in-flight. */
    val isUploadingAttachment: Boolean = false,
    /** Expose the current user's ID reactively from the server connection state. */
    val ownUserId: Int = -1
) {
    /** Helper check to see if the user is authorized to edit/delete a message. */
    fun canManageMessage(message: Message, roles: List<Role>, users: List<User>): Boolean {
        if (ownUserId == -1) return false
        if (message.userId == ownUserId) return true
        val currentUser = users.find { it.id == ownUserId } ?: return false
        val userRoleIds = currentUser.roleIds ?: emptyList()
        val userRoles = roles.filter { it.id in userRoleIds }
        return userRoles.any { it.permissions.contains("MANAGE_MESSAGES") }
    }

    /** Helper check to see if the user is authorized to pin messages. */
    fun canPinMessage(roles: List<Role>, users: List<User>): Boolean {
        if (ownUserId == -1) return false
        val currentUser = users.find { it.id == ownUserId } ?: return false
        val userRoleIds = currentUser.roleIds ?: emptyList()
        val userRoles = roles.filter { it.id in userRoleIds }
        return userRoles.any { it.permissions.contains("PIN_MESSAGES") }
    }
}

/**
 * ViewModel for a single text channel's chat panel.
 *
 * One instance is created per channel (keyed by channelId in the Compose `viewModel()` call),
 * so switching channels doesn't discard the already-loaded message history.
 */
class ChatViewModel : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    private val repository = ChatRepository()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var channelId: Int = -1
    private var eventJob: Job? = null
    private var typingExpiryJob: Job? = null
    private var lastTypingSentTime = 0L

    private val activeTypingUsers = mutableMapOf<Int, Long>()

    init {
        viewModelScope.launch {
            SharkordClient.webSocket.connectionState.collect { connState ->
                val ownId = if (connState is ConnectionState.Connected) connState.serverData.ownUserId else -1
                _uiState.update { it.copy(ownUserId = ownId) }
            }
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────

    /**
     * Initialises this ViewModel for [channelId].
     */
    fun init(channelId: Int) {
        if (this.channelId == channelId) return
        this.channelId = channelId

        activeTypingUsers.clear()
        val currentOwnId = _uiState.value.ownUserId
        _uiState.value = ChatUiState(ownUserId = currentOwnId) // reset state for the new channel but preserve ownUserId
        loadInitialMessages()
        startObservingEvents()
        startTypingExpiryTimer()
    }

    // ─── Message Loading ──────────────────────────────────────

    private fun loadInitialMessages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingHistory = true, errorMessage = null) }
            repository.getMessages(channelId).fold(
                onSuccess = { page ->
                    val sorted = page.messages.sortedBy { it.createdAt }
                    _uiState.update {
                        it.copy(
                            messages = sorted,
                            isLoadingHistory = false,
                            nextCursor = page.nextCursor,
                            hasReachedTop = page.nextCursor == null
                        )
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to load messages: ${error.message}")
                    _uiState.update {
                        it.copy(
                            isLoadingHistory = false,
                            errorMessage = error.message ?: "Failed to load messages"
                        )
                    }
                }
            )
        }
    }

    /**
     * Loads the next older page of messages.
     */
    fun loadOlderMessages() {
        val state = _uiState.value
        if (state.isLoadingOlder || state.hasReachedTop || state.nextCursor == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingOlder = true) }
            repository.getMessages(channelId, cursor = state.nextCursor).fold(
                onSuccess = { page ->
                    val older = page.messages.sortedBy { it.createdAt }
                    _uiState.update { current ->
                        current.copy(
                            messages = (older + current.messages).distinctBy { it.id },
                            isLoadingOlder = false,
                            nextCursor = page.nextCursor,
                            hasReachedTop = page.nextCursor == null
                        )
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to load older messages: ${error.message}")
                    _uiState.update {
                        it.copy(
                            isLoadingOlder = false,
                            errorMessage = error.message ?: "Failed to load older messages"
                        )
                    }
                }
            )
        }
    }

    // ─── Send, Edit, Delete, Reactions ─────────────────────────

    /**
     * Sends a new message, optionally with attached files.
     */
    fun sendMessage(content: String) {
        val trimmed = content.trim()
        val attached = _uiState.value.attachedFiles
        if (trimmed.isEmpty() && attached.isEmpty()) return
        val replyToId = _uiState.value.replyTarget?.id
        val fileNames = attached.map { it.name }

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, errorMessage = null) }
            repository.sendMessage(channelId, trimmed, replyToMessageId = replyToId, files = fileNames).fold(
                onSuccess = {
                    _uiState.update { it.copy(
                        isSending = false,
                        replyTarget = null,
                        attachedFiles = emptyList()
                    ) }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to send message: ${error.message}")
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            errorMessage = error.message ?: "Failed to send message"
                        )
                    }
                }
            )
        }
    }

    /** Sets or clears the active reply target message. */
    fun setReplyTarget(message: Message?) {
        _uiState.update { it.copy(replyTarget = message) }
    }

    /** Sets or clears the active message being edited. */
    fun setEditingMessage(message: Message?) {
        _uiState.update { it.copy(editingMessage = message) }
    }

    /** Submits message edit to server. */
    fun submitEdit(messageId: Int, newContent: String) {
        val trimmed = newContent.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, errorMessage = null) }
            repository.editMessage(messageId, trimmed).fold(
                onSuccess = {
                    _uiState.update { it.copy(isSending = false, editingMessage = null) }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to edit message: ${error.message}")
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            errorMessage = error.message ?: "Failed to edit message"
                        )
                    }
                }
            )
        }
    }

    /** Deletes message on server. */
    fun submitDelete(messageId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            repository.deleteMessage(messageId).onFailure { error ->
                Log.e(TAG, "Failed to delete message: ${error.message}")
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "Failed to delete message")
                }
            }
        }
    }

    /** Toggles emoji reaction on message. */
    fun toggleReaction(messageId: Int, emoji: String) {
        viewModelScope.launch {
            repository.toggleReaction(messageId, emoji).onFailure { error ->
                Log.e(TAG, "Failed to toggle reaction: ${error.message}")
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "Failed to update reaction")
                }
            }
        }
    }

    /** Toggles pinned status of message. */
    fun togglePin(messageId: Int) {
        viewModelScope.launch {
            repository.togglePin(messageId).onFailure { error ->
                Log.e(TAG, "Failed to toggle pin: ${error.message}")
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "Failed to update pin state")
                }
            }
        }
    }

    // ─── Pinned Messages Panel ─────────────────────────────────

    /** Loads pinned messages for this channel. */
    fun loadPinnedMessages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPinned = true, errorMessage = null) }
            repository.getPinnedMessages(channelId).fold(
                onSuccess = { list ->
                    _uiState.update { it.copy(pinnedMessages = list, isLoadingPinned = false) }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to load pinned messages: ${error.message}")
                    _uiState.update {
                        it.copy(
                            isLoadingPinned = false,
                            errorMessage = error.message ?: "Failed to load pinned messages"
                        )
                    }
                }
            )
        }
    }

    /** Controls visibility of pinned messages bottom sheet. */
    fun setPinnedMessagesVisible(visible: Boolean) {
        _uiState.update { it.copy(showPinnedMessages = visible) }
        if (visible) {
            loadPinnedMessages()
        }
    }

    // ─── File Upload & Voice Messaging ─────────────────────────

    /** Uploads a file and attaches it to the draft. */
    fun uploadAndAttachFile(originalName: String, fileBytes: ByteArray) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingAttachment = true, errorMessage = null) }
            repository.uploadFile(originalName, fileBytes).fold(
                onSuccess = { fileInfo ->
                    _uiState.update { it.copy(
                        attachedFiles = it.attachedFiles + fileInfo,
                        isUploadingAttachment = false
                    )}
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to upload file: ${error.message}")
                    _uiState.update { it.copy(
                        isUploadingAttachment = false,
                        errorMessage = error.message ?: "Failed to upload file"
                    )}
                }
            )
        }
    }

    /** Removes a previously uploaded file from the attachment list. */
    fun removeAttachedFile(fileId: Int) {
        _uiState.update { state ->
            state.copy(
                attachedFiles = state.attachedFiles.filter { file -> file.id != fileId }
            )
        }
    }

    /** Uploads a custom voice note raw bytes and posts it to the channel immediately. */
    fun sendAudioVoiceNote(fileName: String, fileBytes: ByteArray) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, errorMessage = null) }
            repository.uploadFile(fileName, fileBytes).fold(
                onSuccess = { fileInfo ->
                    repository.sendMessage(channelId, "", files = listOf(fileInfo.name)).fold(
                        onSuccess = {
                            _uiState.update { it.copy(isSending = false) }
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Failed to post voice note: ${error.message}")
                            _uiState.update { it.copy(
                                isSending = false,
                                errorMessage = error.message ?: "Failed to send voice note"
                            )}
                        }
                    )
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to upload voice note: ${error.message}")
                    _uiState.update { it.copy(
                        isSending = false,
                        errorMessage = error.message ?: "Failed to upload voice note"
                    )}
                }
            )
        }
    }

    // ─── Typing Indicators ─────────────────────────────────────

    /** Outbound: call when user is typing. */
    fun onType(text: String) {
        if (text.isBlank()) return
        val now = System.currentTimeMillis()
        if (now - lastTypingSentTime > 3000) {
            lastTypingSentTime = now
            viewModelScope.launch {
                repository.signalTyping(channelId)
            }
        }
    }

    private fun startTypingExpiryTimer() {
        typingExpiryJob?.cancel()
        typingExpiryJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                val expired = activeTypingUsers.filter { now - it.value > 5000 }.keys
                if (expired.isNotEmpty()) {
                    expired.forEach { activeTypingUsers.remove(it) }
                    _uiState.update { it.copy(typingUsers = activeTypingUsers.keys.toSet()) }
                }
            }
        }
    }

    /** Clears error message. */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ─── Real-Time Event Handling ─────────────────────────────

    private fun startObservingEvents() {
        eventJob?.cancel()
        eventJob = viewModelScope.launch {
            SharkordClient.webSocket.incomingEvents.collect { event ->
                val parsed = ServerEventHandler.parse(event) ?: return@collect
                applyServerEvent(parsed)
            }
        }
    }

    private fun applyServerEvent(event: ServerEvent) {
        when (event) {
            is ServerEvent.MessageReceived -> {
                if (event.message.channelId != channelId) return
                Log.d(TAG, "[EVENT] messages.onNew: id=${event.message.id}")
                activeTypingUsers.remove(event.message.userId)
                _uiState.update { state ->
                    if (state.messages.any { it.id == event.message.id }) return@update state
                    state.copy(
                        messages = state.messages + event.message,
                        typingUsers = activeTypingUsers.keys.toSet()
                    )
                }
            }

            is ServerEvent.MessageUpdated -> {
                if (event.message.channelId != channelId) return
                Log.d(TAG, "[EVENT] messages.onUpdate: id=${event.message.id}")
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == event.message.id) event.message else msg
                        }
                    )
                }
            }

            is ServerEvent.MessageDeleted -> {
                if (event.channelId != channelId) return
                Log.d(TAG, "[EVENT] messages.onDelete: id=${event.messageId}")
                _uiState.update { state ->
                    state.copy(messages = state.messages.filter { it.id != event.messageId })
                }
            }

            is ServerEvent.UserTyping -> {
                if (event.channelId != channelId) return
                val conn = SharkordClient.webSocket.connectionState.value
                val ownUserId = if (conn is ConnectionState.Connected) conn.serverData.ownUserId else -1
                if (event.userId == ownUserId) return
                Log.d(TAG, "[EVENT] messages.onTyping: userId=${event.userId}")
                activeTypingUsers[event.userId] = System.currentTimeMillis()
                _uiState.update { it.copy(typingUsers = activeTypingUsers.keys.toSet()) }
            }

            else -> Unit
        }
    }

    override fun onCleared() {
        super.onCleared()
        eventJob?.cancel()
        typingExpiryJob?.cancel()
    }
}
