package com.sharkord.android.ui.navigation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

// event representing a request to navigate to a specific message or channel
data class JumpEvent(val channelId: Int, val messageId: Int?)

// global navigation manager for jumping to messages from anywhere in the application
// (e.g., from pinned messages, search results, push notifications)
object MessageNavigationManager {

    private val _jumpEvents = Channel<JumpEvent>(Channel.BUFFERED)
    
    // flow that emits [JumpEvent]s. To be collected by a top-level ViewModel or
    // navigation host (e.g., HomeViewModel) to handle the actual routing and state changes
    val jumpEvents = _jumpEvents.receiveAsFlow()

    // dispatch an event to jump to a specific message in a channel
    // @param channelId The ID of the channel containing the message
    // @param messageId The ID of the target message to jump to
    fun jumpToMessage(channelId: Int, messageId: Int) {
        _jumpEvents.trySend(JumpEvent(channelId, messageId))
    }

    // dispatch an event to jump to a specific channel without a specific message
    fun jumpToChannel(channelId: Int) {
        _jumpEvents.trySend(JumpEvent(channelId, null))
    }
}
