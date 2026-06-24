package com.sharkord.android.ui.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// event representing a request to navigate to a specific message in a channel
data class JumpEvent(val channelId: Int, val messageId: Int)

// global navigation manager for jumping to messages from anywhere in the application
// (e.g., from pinned messages, search results, push notifications)
object MessageNavigationManager {

    private val _jumpEvents = MutableSharedFlow<JumpEvent>(extraBufferCapacity = 1)
    
    // sharedFlow that emits [JumpEvent]s. To be collected by a top-level ViewModel or
    // navigation host (e.g., HomeViewModel) to handle the actual routing and state changes
    val jumpEvents = _jumpEvents.asSharedFlow()

    // dispatch an event to jump to a specific message in a channel
    // @param channelId The ID of the channel containing the message
    // @param messageId The ID of the target message to jump to
    fun jumpToMessage(channelId: Int, messageId: Int) {
        _jumpEvents.tryEmit(JumpEvent(channelId, messageId))
    }
}
