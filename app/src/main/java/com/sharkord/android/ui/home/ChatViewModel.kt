package com.sharkord.android.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharkord.android.data.model.Message
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
    val replyTarget: Message? = null
)

/**
 * ViewModel for a single text channel's chat panel.
 *
 * One instance is created per channel (keyed by channelId in the Compose `viewModel()` call),
 * so switching channels doesn't discard the already-loaded message history.
 *
 * Responsibilities:
 * - Load the initial message page from [ChatRepository.getMessages].
 * - Paginate older messages on demand.
 * - Handle send via [ChatRepository.sendMessage].
 * - Apply real-time [ServerEvent] mutations (new/update/delete) from [SharkordClient.webSocket].
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

    // ─── Lifecycle ────────────────────────────────────────────

    /**
     * Initialises this ViewModel for [channelId].
     *
     * Safe to call multiple times — if the channelId has not changed, does nothing.
     * Called from `ChatPanel` in `LaunchedEffect(channelId)`.
     */
    fun init(channelId: Int) {
        if (this.channelId == channelId) return
        this.channelId = channelId

        _uiState.value = ChatUiState() // reset state for the new channel
        loadInitialMessages()
        startObservingEvents()
    }

    // ─── Message Loading ──────────────────────────────────────

    private fun loadInitialMessages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingHistory = true, errorMessage = null) }
            repository.getMessages(channelId).fold(
                onSuccess = { page ->
                    // Messages come newest-first from the server; reverse to chronological
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
     * Loads the next older page of messages (triggered when the user scrolls to the top).
     * No-ops if already loading, or if we've reached the beginning of the channel history.
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
                            // Prepend older messages, avoiding duplicates by id
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

    // ─── Send ─────────────────────────────────────────────────

    /**
     * Sends a new message. No-ops on blank/empty content.
     */
    fun sendMessage(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        val replyToId = _uiState.value.replyTarget?.id

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, errorMessage = null) }
            repository.sendMessage(channelId, trimmed, replyToMessageId = replyToId).fold(
                onSuccess = {
                    // The server will push the new message back via messages.onNew subscription,
                    // so we don't need to manually append it here to avoid duplication.
                    _uiState.update { it.copy(isSending = false, replyTarget = null) }
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

    /** Clears a previously shown error message. */
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
                _uiState.update { state ->
                    // Avoid duplicates (optimistic insert could have already added it)
                    if (state.messages.any { it.id == event.message.id }) return@update state
                    state.copy(messages = state.messages + event.message)
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

            // All other events are not relevant to a single channel's chat view
            else -> Unit
        }
    }

    override fun onCleared() {
        super.onCleared()
        eventJob?.cancel()
    }
}
