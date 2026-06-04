package com.sharkord.android.data.model

import com.google.gson.annotations.SerializedName

// HTTP Login

/** POST /login request body */
data class LoginRequest(
    val identity: String,
    val password: String
)

/**
 * POST /login response.
 * Server returns { success: true, token } on success,
 * or { errors: { field: message } } / { error: message } on failure.
 */
data class LoginResponse(
    val success: Boolean = false,
    val token: String? = null,
    val error: String? = null,
    val errors: Map<String, String>? = null
)

// GET /info

data class ServerLogo(
    val id: Int,
    val name: String,
    @SerializedName("originalName", alternate = ["original_name"]) val originalName: String? = null,
    @SerializedName("mimeType", alternate = ["mime_type"]) val mimeType: String? = null
)

/**
 * GET /info response, matching TServerInfo in shared/types.ts.
 */
data class ServerInfoResponse(
    val serverId: String,
    val version: String,
    val name: String,
    val description: String? = null,
    val logo: ServerLogo? = null,
    val allowNewUsers: Boolean = true
)

// File (shared)

/**
 * File metadata, matching TFile in shared/tables.ts.
 * Used for avatars, banners, message attachments, emojis, and server logo.
 *
 * Note: the upload response (TTempFile) does NOT include a `name` field,
 * only `originalName`. Therefore `name` is nullable here. Use
 * `displayName` for UI to get a safe non-null display string.
 */
data class FileInfo(
    val id: String,
    val name: String? = null,
    @SerializedName("originalName", alternate = ["original_name"]) val originalName: String? = null,
    @SerializedName("mimeType", alternate = ["mime_type"]) val mimeType: String? = null,
    val size: Int? = null,
    @SerializedName("_accessToken") val accessToken: String? = null,
    @SerializedName("_accessTokenExpiresAt") val accessTokenExpiresAt: Long? = null,
    val localUri: String? = null
) {
    /** Safe display name: prefer originalName, fall back to name, then id. */
    val displayName: String get() = originalName ?: name ?: id

    val isImage: Boolean
        get() {
            val ext = originalName?.substringAfterLast('.', "")?.lowercase() ?: ""
            val mime = mimeType?.lowercase() ?: ""
            return mime.startsWith("image/") || ext in listOf("png", "jpg", "jpeg", "webp", "gif")
        }

    val isVideo: Boolean
        get() {
            val ext = originalName?.substringAfterLast('.', "")?.lowercase() ?: ""
            val mime = mimeType?.lowercase() ?: ""
            return mime.startsWith("video/") || ext in listOf("mp4", "mkv", "mov", "webm", "avi")
        }
}

// Categories

data class Category(
    val id: Int,
    val name: String,
    val position: Int
)

// Channels

/**
 * Channel model matching the server's TChannel schema.
 * The server returns more fields than before; we capture the ones we need.
 */
data class Channel(
    val id: Int,
    val name: String,
    val isDm: Boolean = false,
    val type: String = "TEXT",
    val categoryId: Int? = null,
    val position: Int? = null,
    val description: String? = null,
    val private: Boolean = false,
    val createdAt: Long? = null
) {
    val isVoice: Boolean
        get() = type == ChannelType.VOICE.value

    val isText: Boolean
        get() = type == ChannelType.TEXT.value
}

// Roles

/**
 * Role model matching TJoinedRole in shared/tables.ts.
 */
data class Role(
    val id: Int,
    val name: String,
    val color: String = "#99AAB5",
    val position: Int = 0,
    val permissions: List<String> = emptyList()
)

// Users

/**
 * Public user model matching TJoinedPublicUser in shared/tables.ts.
 * The server returns these in the joinServer response.
 */
data class User(
    val id: Int,
    val name: String,
    val bio: String? = null,
    val bannerColor: String? = null,
    val banned: Boolean = false,
    val avatar: FileInfo? = null,
    val avatarId: Int? = null,
    val banner: FileInfo? = null,
    val bannerId: Int? = null,
    val status: String? = null,
    val roleIds: List<Int>? = null,
    val createdAt: Long? = null
) {
    val userStatus: UserStatus
        get() = UserStatus.fromValue(status ?: "offline")
}

// Emojis

/**
 * Custom emoji, matching TJoinedEmoji in shared/tables.ts.
 */
data class Emoji(
    val id: Int,
    val name: String,
    val file: FileInfo? = null
)

// Messages

/**
 * Message file attachment.
 */
data class MessageFile(
    val id: String,
    val name: String,
    @SerializedName("originalName", alternate = ["original_name"]) val originalName: String? = null,
    @SerializedName("mimeType", alternate = ["mime_type"]) val mimeType: String? = null,
    val size: Int? = null,
    val localUri: String? = null
)

/**
 * Message reaction, matching TJoinedMessageReaction.
 */
data class MessageReaction(
    val id: Int,
    val messageId: Int,
    val userId: Int,
    val emojiId: Int? = null,
    val emoji: String? = null,
    val file: FileInfo? = null
)

/**
 * Reply preview for inline replies.
 */
data class MessageReplyPreview(
    val id: Int,
    val content: String?,
    val userId: Int,
    val pluginId: String? = null
)

/**
 * Full message model matching TJoinedMessage in shared/tables.ts.
 */
data class Message(
    val id: Int,
    val content: String,
    val channelId: Int,
    val userId: Int,
    val createdAt: Long,
    val editedAt: Long? = null,
    val editable: Boolean = true,
    val parentMessageId: Int? = null,
    val replyToMessageId: Int? = null,
    val pinned: Boolean = false,
    val pluginId: String? = null,
    val files: List<FileInfo> = emptyList(),
    val reactions: List<MessageReaction> = emptyList(),
    val replyCount: Int = 0,
    val replyTo: MessageReplyPreview? = null
)

/**
 * Paginated messages response from messages.get tRPC query.
 */
data class MessagesPage(
    val messages: List<Message>,
    val nextCursor: Long? = null
)

// Public Settings

/**
 * Public server settings, matching TPublicServerSettings in shared/types.ts.
 */
data class PublicSettings(
    val name: String? = null,
    val description: String? = null,
    val serverId: String? = null,
    val storageUploadEnabled: Boolean = true,
    val directMessagesEnabled: Boolean = true,
    val storageQuota: Long? = null,
    val storageUploadMaxFileSize: Long? = null,
    val storageMaxFilesPerMessage: Int = 10,
    val enableSearch: Boolean = true,
    val showWelcomeDialog: Boolean = false
)

// Voice

/**
 * Represents users currently in voice channels.
 * voiceMap is channelId -> list of userIds.
 */
typealias VoiceMap = Map<String, List<Int>>

// Read States

/**
 * Unread count per channel.
 * readStates is channelId -> unreadCount.
 */
typealias ReadStateMap = Map<String, Int>

// Channel Permissions

/**
 * Per-channel permission info for the current user.
 */
data class ChannelPermissionInfo(
    val channelId: Int,
    val permissions: Map<String, Boolean>
)

/**
 * channelPermissions is channelId -> ChannelPermissionInfo.
 */
typealias ChannelPermissionsMap = Map<String, ChannelPermissionInfo>

// tRPC joinServer response

/**
 * Full joinServer response matching the server's others.joinServer return type.
 * See: apps/server/src/routers/others/join.ts
 */
data class JoinServerData(
    val ownUserId: Int,
    val serverName: String,
    val serverId: String? = null,
    val channels: List<Channel> = emptyList(),
    val users: List<User> = emptyList(),
    val categories: List<Category>? = null,
    val roles: List<Role>? = null,
    val emojis: List<Emoji>? = null,
    val publicSettings: PublicSettings? = null,
    val voiceMap: Map<String, Any>? = null,
    val readStates: Map<String, Any>? = null,
    val channelPermissions: Map<String, Any>? = null,
    val showWelcomeDialog: Boolean = false
)

// Handshake response

/**
 * Response from others.handshake tRPC query.
 */
data class HandshakeResponse(
    val handshakeHash: String,
    val hasPassword: Boolean = false
)
