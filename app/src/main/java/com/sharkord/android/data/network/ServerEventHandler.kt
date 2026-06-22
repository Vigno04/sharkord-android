package com.sharkord.android.data.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sharkord.android.data.model.Category
import com.sharkord.android.data.model.Channel
import com.sharkord.android.data.model.Emoji
import com.sharkord.android.data.model.Message
import com.sharkord.android.data.model.PublicSettings
import com.sharkord.android.data.model.Role
import com.sharkord.android.data.model.User

// typed representation of every real-time server event pushed via tRPC subscriptions
// mirrors the web client's per-domain subscription modules
// (channels/subscriptions.ts, users/subscriptions.ts, etc.) but expressed as a
// sealed class hierarchy so the UI layer can exhaustively pattern-match
// payload shapes match the server's pubsub.ts `Events` type map
sealed class ServerEvent {

    // channels

    // a new channel was created and is visible to the current user
    data class ChannelCreated(val channel: Channel) : ServerEvent()

    // a channel was permanently deleted. `channelId` is the deleted channel's id
    data class ChannelDeleted(val channelId: Int) : ServerEvent()

    // a channel's metadata (name, description, position, etc.) was updated
    data class ChannelUpdated(val channel: Channel) : ServerEvent()

    // a channel's read state count was set explicitly
    data class ChannelReadStateUpdate(val channelId: Int, val count: Int) : ServerEvent()

    // a channel's read state count received an incremental delta
    data class ChannelReadStateDelta(val channelId: Int, val delta: Int) : ServerEvent()

    // categories

    // a new category was created
    data class CategoryCreated(val category: Category) : ServerEvent()

    // a category was permanently deleted. `categoryId` is the deleted category's id
    data class CategoryDeleted(val categoryId: Int) : ServerEvent()

    // a category's metadata was updated
    data class CategoryUpdated(val category: Category) : ServerEvent()

    // users

    // a user connected to the server (status became online)
    data class UserJoined(val user: User) : ServerEvent()

    // a user disconnected (status became offline). `userId` identifies who left
    data class UserLeft(val userId: Int) : ServerEvent()

    // a new user account was created on the server
    data class UserCreated(val user: User) : ServerEvent()

    // a user's profile (name, avatar, status, roles, etc.) was updated
    data class UserUpdated(val user: User) : ServerEvent()

    // a user account was deleted
    // @param isWipe  When true, the user's messages are deleted too (wipe)
    // when false, messages are reassigned to the [deletedUserId] placeholder
    // @param userId  The id of the account that was deleted
    // @param deletedUserId  The id of the server's "Deleted User" placeholder account
    data class UserDeleted(
        val isWipe: Boolean,
        val userId: Int,
        val deletedUserId: Int
    ) : ServerEvent()

    // roles

    // a new role was created
    data class RoleCreated(val role: Role) : ServerEvent()

    // a role was permanently deleted. `roleId` is the deleted role's id
    data class RoleDeleted(val roleId: Int) : ServerEvent()

    // a role's properties (name, color, permissions, etc.) were updated
    data class RoleUpdated(val role: Role) : ServerEvent()

    // emojis

    // a new custom emoji was added
    data class EmojiCreated(val emoji: Emoji) : ServerEvent()

    // a custom emoji was permanently deleted. `emojiId` is the deleted emoji's id
    data class EmojiDeleted(val emojiId: Int) : ServerEvent()

    // a custom emoji's metadata was updated
    data class EmojiUpdated(val emoji: Emoji) : ServerEvent()

    // messages

    // a new message was sent in a channel the current user can access
    data class MessageReceived(val message: Message) : ServerEvent()

    // a message was edited
    data class MessageUpdated(val message: Message) : ServerEvent()

    // a message was deleted
    data class MessageDeleted(val messageId: Int, val channelId: Int) : ServerEvent()

    // a user started typing in a channel
    data class UserTyping(val channelId: Int, val userId: Int, val parentMessageId: Int? = null) : ServerEvent()

    // voice

    // a user joined a voice channel
    data class UserJoinedVoice(val channelId: Int, val userId: Int, val state: com.sharkord.android.data.model.VoiceUserState) : ServerEvent()

    // a user left a voice channel
    data class UserLeftVoice(val channelId: Int, val userId: Int) : ServerEvent()

    // a user's voice state (mute/deafen) was updated
    data class UserVoiceStateUpdated(val channelId: Int, val userId: Int, val state: com.sharkord.android.data.model.VoiceUserState) : ServerEvent()

    // a new producer stream was added in the voice channel
    data class VoiceNewProducer(val channelId: Int, val remoteId: Int, val kind: com.sharkord.android.data.model.StreamKind) : ServerEvent()

    // a producer stream was closed in the voice channel
    data class VoiceProducerClosed(val channelId: Int, val remoteId: Int, val kind: com.sharkord.android.data.model.StreamKind) : ServerEvent()

    // server Settings

    // public server settings (name, description, feature flags, etc.) were updated
    data class ServerSettingsUpdated(val settings: PublicSettings) : ServerEvent()
}

// parses raw [IncomingEvent] payloads from [WebSocketManager.incomingEvents] into
// typed [ServerEvent] instances
// scalar-valued events (delete events that carry only an id) arrive wrapped in
// `{"value": <id>}` by [TrpcProtocol.parseResponse], so we read them via
// `data.get("value").asInt`
object ServerEventHandler {

    private const val TAG = "ServerEventHandler"

    private val gson = Gson()

    // converts an [IncomingEvent] into the matching [ServerEvent], or returns null
    // if the path is unrecognized or the payload fails to parse
    // callers should log or ignore null returns — they indicate either an event
    // that this client doesn't handle yet, or a malformed server payload
    fun parse(event: IncomingEvent): ServerEvent? {
        return try {
            when (event.path) {

                // channels
                "channels.onCreate" ->
                    ServerEvent.ChannelCreated(fromJson(event.data, Channel::class.java))

                "channels.onDelete" ->
                    ServerEvent.ChannelDeleted(event.data.get("value").asInt)

                "channels.onUpdate" ->
                    ServerEvent.ChannelUpdated(fromJson(event.data, Channel::class.java))

                "channels.onReadStateUpdate" ->
                    ServerEvent.ChannelReadStateUpdate(
                        channelId = event.data.get("channelId").asInt,
                        count = event.data.get("count").asInt
                    )

                "channels.onReadStateDelta" ->
                    ServerEvent.ChannelReadStateDelta(
                        channelId = event.data.get("channelId").asInt,
                        delta = event.data.get("delta").asInt
                    )

                // categories
                "categories.onCreate" ->
                    ServerEvent.CategoryCreated(fromJson(event.data, Category::class.java))

                "categories.onDelete" ->
                    ServerEvent.CategoryDeleted(event.data.get("value").asInt)

                "categories.onUpdate" ->
                    ServerEvent.CategoryUpdated(fromJson(event.data, Category::class.java))

                // users
                "users.onJoin" ->
                    ServerEvent.UserJoined(fromJson(event.data, User::class.java))

                "users.onLeave" ->
                    ServerEvent.UserLeft(event.data.get("value").asInt)

                "users.onCreate" ->
                    ServerEvent.UserCreated(fromJson(event.data, User::class.java))

                "users.onUpdate" ->
                    ServerEvent.UserUpdated(fromJson(event.data, User::class.java))

                "users.onDelete" ->
                    ServerEvent.UserDeleted(
                        isWipe = event.data.get("isWipe").asBoolean,
                        userId = event.data.get("userId").asInt,
                        deletedUserId = event.data.get("deletedUserId").asInt
                    )

                // roles
                "roles.onCreate" ->
                    ServerEvent.RoleCreated(fromJson(event.data, Role::class.java))

                "roles.onDelete" ->
                    ServerEvent.RoleDeleted(event.data.get("value").asInt)

                "roles.onUpdate" ->
                    ServerEvent.RoleUpdated(fromJson(event.data, Role::class.java))

                // emojis
                "emojis.onCreate" ->
                    ServerEvent.EmojiCreated(fromJson(event.data, Emoji::class.java))

                "emojis.onDelete" ->
                    ServerEvent.EmojiDeleted(event.data.get("value").asInt)

                "emojis.onUpdate" ->
                    ServerEvent.EmojiUpdated(fromJson(event.data, Emoji::class.java))

                // messages
                "messages.onNew" ->
                    ServerEvent.MessageReceived(fromJson(event.data, Message::class.java))

                "messages.onUpdate" ->
                    ServerEvent.MessageUpdated(fromJson(event.data, Message::class.java))

                "messages.onDelete" ->
                    ServerEvent.MessageDeleted(
                        messageId = event.data.get("messageId").asInt,
                        channelId = event.data.get("channelId").asInt
                    )

                "messages.onTyping" -> {
                    val pId = event.data.get("parentMessageId")
                    ServerEvent.UserTyping(
                        channelId = event.data.get("channelId").asInt,
                        userId = event.data.get("userId").asInt,
                        parentMessageId = if (pId == null || pId.isJsonNull) null else pId.asInt
                    )
                }

                // voice
                "voice.onJoin" ->
                    ServerEvent.UserJoinedVoice(
                        channelId = event.data.get("channelId").asInt,
                        userId = event.data.get("userId").asInt,
                        state = fromJson(event.data.get("state").asJsonObject, com.sharkord.android.data.model.VoiceUserState::class.java)
                    )

                "voice.onLeave" ->
                    ServerEvent.UserLeftVoice(
                        channelId = event.data.get("channelId").asInt,
                        userId = event.data.get("userId").asInt
                    )

                "voice.onUpdateState" ->
                    ServerEvent.UserVoiceStateUpdated(
                        channelId = event.data.get("channelId").asInt,
                        userId = event.data.get("userId").asInt,
                        state = fromJson(event.data.get("state").asJsonObject, com.sharkord.android.data.model.VoiceUserState::class.java)
                    )

                "voice.onNewProducer" ->
                    ServerEvent.VoiceNewProducer(
                        channelId = event.data.get("channelId").asInt,
                        remoteId = event.data.get("remoteId").asInt,
                        kind = com.sharkord.android.data.model.StreamKind.fromValue(event.data.get("kind").asString)
                    )

                "voice.onProducerClosed" ->
                    ServerEvent.VoiceProducerClosed(
                        channelId = event.data.get("channelId").asInt,
                        remoteId = event.data.get("remoteId").asInt,
                        kind = com.sharkord.android.data.model.StreamKind.fromValue(event.data.get("kind").asString)
                    )

                // server Settings
                "others.onServerSettingsUpdate" ->
                    ServerEvent.ServerSettingsUpdated(fromJson(event.data, PublicSettings::class.java))

                else -> {
                    Log.d(TAG, "Unhandled subscription path: ${event.path}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse event [${event.path}]: ${e.message}")
            null
        }
    }

    // helpers

    private fun <T> fromJson(data: JsonObject, clazz: Class<T>): T =
        gson.fromJson(data, clazz)
}
