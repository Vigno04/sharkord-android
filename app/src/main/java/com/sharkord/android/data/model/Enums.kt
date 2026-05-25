package com.sharkord.android.data.model

/**
 * User online status, matching the server's UserStatus enum.
 */
enum class UserStatus(val value: String) {
    ONLINE("online"),
    IDLE("idle"),
    OFFLINE("offline");

    companion object {
        fun fromValue(value: String): UserStatus {
            return entries.find { it.value == value } ?: OFFLINE
        }
    }
}

/**
 * Channel type, matching the server's ChannelType enum.
 */
enum class ChannelType(val value: String) {
    TEXT("TEXT"),
    VOICE("VOICE");

    companion object {
        fun fromValue(value: String): ChannelType {
            return entries.find { it.value == value } ?: TEXT
        }
    }
}

/**
 * Server-side event names used for tRPC subscriptions.
 * Matches the server's ServerEvents enum in packages/shared/src/events.ts.
 */
object ServerEvent {
    // Messages
    const val NEW_MESSAGE = "newMessage"
    const val MESSAGE_UPDATE = "messageUpdate"
    const val MESSAGE_DELETE = "messageDelete"
    const val MESSAGE_TYPING = "messageTyping"
    const val THREAD_REPLY_COUNT_UPDATE = "threadReplyCountUpdate"

    // Users
    const val USER_JOIN = "userJoin"
    const val USER_LEAVE = "userLeave"
    const val USER_CREATE = "userCreate"
    const val USER_UPDATE = "userUpdate"
    const val USER_DELETE = "userDelete"

    // Channels
    const val CHANNEL_CREATE = "channelCreate"
    const val CHANNEL_UPDATE = "channelUpdate"
    const val CHANNEL_DELETE = "channelDelete"
    const val CHANNEL_PERMISSIONS_UPDATE = "channelPermissionsUpdate"
    const val CHANNEL_READ_STATES_UPDATE = "channelReadStatesUpdate"
    const val CHANNEL_READ_STATES_DELTA = "channelReadStatesDelta"

    // Voice
    const val USER_JOIN_VOICE = "userJoinVoice"
    const val USER_LEAVE_VOICE = "userLeaveVoice"
    const val USER_VOICE_STATE_UPDATE = "userVoiceStateUpdate"

    // Emojis
    const val EMOJI_CREATE = "emojiCreate"
    const val EMOJI_UPDATE = "emojiUpdate"
    const val EMOJI_DELETE = "emojiDelete"

    // Roles
    const val ROLE_CREATE = "roleCreate"
    const val ROLE_UPDATE = "roleUpdate"
    const val ROLE_DELETE = "roleDelete"

    // Server
    const val SERVER_SETTINGS_UPDATE = "serverSettingsUpdate"

    // Categories
    const val CATEGORY_CREATE = "categoryCreate"
    const val CATEGORY_UPDATE = "categoryUpdate"
    const val CATEGORY_DELETE = "categoryDelete"

    // DMs
    const val DM_CONVERSATION_OPEN = "dmConversationOpen"
}
