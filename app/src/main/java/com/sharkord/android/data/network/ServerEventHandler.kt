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

/**
 * Typed representation of every real-time server event pushed via tRPC subscriptions.
 *
 * Mirrors the web client's per-domain subscription modules
 * (channels/subscriptions.ts, users/subscriptions.ts, etc.) but expressed as a
 * sealed class hierarchy so the UI layer can exhaustively pattern-match.
 *
 * Payload shapes match the server's pubsub.ts `Events` type map.
 */
sealed class ServerEvent {

    // Channels

    /** A new channel was created and is visible to the current user. */
    data class ChannelCreated(val channel: Channel) : ServerEvent()

    /** A channel was permanently deleted. `channelId` is the deleted channel's id. */
    data class ChannelDeleted(val channelId: Int) : ServerEvent()

    /** A channel's metadata (name, description, position, etc.) was updated. */
    data class ChannelUpdated(val channel: Channel) : ServerEvent()

    /** A channel's read state count was set explicitly. */
    data class ChannelReadStateUpdate(val channelId: Int, val count: Int) : ServerEvent()

    /** A channel's read state count received an incremental delta. */
    data class ChannelReadStateDelta(val channelId: Int, val delta: Int) : ServerEvent()

    // Categories

    /** A new category was created. */
    data class CategoryCreated(val category: Category) : ServerEvent()

    /** A category was permanently deleted. `categoryId` is the deleted category's id. */
    data class CategoryDeleted(val categoryId: Int) : ServerEvent()

    /** A category's metadata was updated. */
    data class CategoryUpdated(val category: Category) : ServerEvent()

    // Users

    /** A user connected to the server (status became online). */
    data class UserJoined(val user: User) : ServerEvent()

    /** A user disconnected (status became offline). `userId` identifies who left. */
    data class UserLeft(val userId: Int) : ServerEvent()

    /** A new user account was created on the server. */
    data class UserCreated(val user: User) : ServerEvent()

    /** A user's profile (name, avatar, status, roles, etc.) was updated. */
    data class UserUpdated(val user: User) : ServerEvent()

    /**
     * A user account was deleted.
     *
     * @param isWipe  When true, the user's messages are deleted too (wipe).
     *                When false, messages are reassigned to the [deletedUserId] placeholder.
     * @param userId  The id of the account that was deleted.
     * @param deletedUserId  The id of the server's "Deleted User" placeholder account.
     */
    data class UserDeleted(
        val isWipe: Boolean,
        val userId: Int,
        val deletedUserId: Int
    ) : ServerEvent()

    // Roles

    /** A new role was created. */
    data class RoleCreated(val role: Role) : ServerEvent()

    /** A role was permanently deleted. `roleId` is the deleted role's id. */
    data class RoleDeleted(val roleId: Int) : ServerEvent()

    /** A role's properties (name, color, permissions, etc.) were updated. */
    data class RoleUpdated(val role: Role) : ServerEvent()

    // Emojis

    /** A new custom emoji was added. */
    data class EmojiCreated(val emoji: Emoji) : ServerEvent()

    /** A custom emoji was permanently deleted. `emojiId` is the deleted emoji's id. */
    data class EmojiDeleted(val emojiId: Int) : ServerEvent()

    /** A custom emoji's metadata was updated. */
    data class EmojiUpdated(val emoji: Emoji) : ServerEvent()

    // Messages

    /** A new message was sent in a channel the current user can access. */
    data class MessageReceived(val message: Message) : ServerEvent()

    /** A message was edited. */
    data class MessageUpdated(val message: Message) : ServerEvent()

    /** A message was deleted. */
    data class MessageDeleted(val messageId: Int, val channelId: Int) : ServerEvent()

    /** A user started typing in a channel. */
    data class UserTyping(val channelId: Int, val userId: Int, val parentMessageId: Int? = null) : ServerEvent()

    // Server Settings

    /** Public server settings (name, description, feature flags, etc.) were updated. */
    data class ServerSettingsUpdated(val settings: PublicSettings) : ServerEvent()
}

/**
 * Parses raw [IncomingEvent] payloads from [WebSocketManager.incomingEvents] into
 * typed [ServerEvent] instances.
 *
 * Scalar-valued events (delete events that carry only an id) arrive wrapped in
 * `{"value": <id>}` by [TrpcProtocol.parseResponse], so we read them via
 * `data.get("value").asInt`.
 */
object ServerEventHandler {

    private const val TAG = "ServerEventHandler"

    private val gson = Gson()

    /**
     * Converts an [IncomingEvent] into the matching [ServerEvent], or returns null
     * if the path is unrecognized or the payload fails to parse.
     *
     * Callers should log or ignore null returns — they indicate either an event
     * that this client doesn't handle yet, or a malformed server payload.
     */
    fun parse(event: IncomingEvent): ServerEvent? {
        return try {
            when (event.path) {

                // Channels
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

                // Categories
                "categories.onCreate" ->
                    ServerEvent.CategoryCreated(fromJson(event.data, Category::class.java))

                "categories.onDelete" ->
                    ServerEvent.CategoryDeleted(event.data.get("value").asInt)

                "categories.onUpdate" ->
                    ServerEvent.CategoryUpdated(fromJson(event.data, Category::class.java))

                // Users
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

                // Roles
                "roles.onCreate" ->
                    ServerEvent.RoleCreated(fromJson(event.data, Role::class.java))

                "roles.onDelete" ->
                    ServerEvent.RoleDeleted(event.data.get("value").asInt)

                "roles.onUpdate" ->
                    ServerEvent.RoleUpdated(fromJson(event.data, Role::class.java))

                // Emojis
                "emojis.onCreate" ->
                    ServerEvent.EmojiCreated(fromJson(event.data, Emoji::class.java))

                "emojis.onDelete" ->
                    ServerEvent.EmojiDeleted(event.data.get("value").asInt)

                "emojis.onUpdate" ->
                    ServerEvent.EmojiUpdated(fromJson(event.data, Emoji::class.java))

                // Messages
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

                // Server Settings
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

    // Helpers

    private fun <T> fromJson(data: JsonObject, clazz: Class<T>): T =
        gson.fromJson(data, clazz)
}
