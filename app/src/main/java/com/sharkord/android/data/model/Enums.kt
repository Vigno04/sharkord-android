package com.sharkord.android.data.model

// user online status, matching the server's UserStatus enum
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

// channel type, matching the server's ChannelType enum
enum class ChannelType(val value: String) {
    TEXT("TEXT"),
    VOICE("VOICE");

    companion object {
        fun fromValue(value: String): ChannelType {
            return entries.find { it.value == value } ?: TEXT
        }
    }
}

// server-side event names used for tRPC subscriptions
// matches the server's ServerEvents enum in packages/shared/src/events.ts
object ServerEvent {
    // messages
    const val NEW_MESSAGE = "newMessage"
    const val MESSAGE_UPDATE = "messageUpdate"
    const val MESSAGE_DELETE = "messageDelete"
    const val MESSAGE_TYPING = "messageTyping"
    const val THREAD_REPLY_COUNT_UPDATE = "threadReplyCountUpdate"

    // users
    const val USER_JOIN = "userJoin"
    const val USER_LEAVE = "userLeave"
    const val USER_CREATE = "userCreate"
    const val USER_UPDATE = "userUpdate"
    const val USER_DELETE = "userDelete"

    // channels
    const val CHANNEL_CREATE = "channelCreate"
    const val CHANNEL_UPDATE = "channelUpdate"
    const val CHANNEL_DELETE = "channelDelete"
    const val CHANNEL_PERMISSIONS_UPDATE = "channelPermissionsUpdate"
    const val CHANNEL_READ_STATES_UPDATE = "channelReadStatesUpdate"
    const val CHANNEL_READ_STATES_DELTA = "channelReadStatesDelta"

    // voice
    const val USER_JOIN_VOICE = "userJoinVoice"
    const val USER_LEAVE_VOICE = "userLeaveVoice"
    const val USER_VOICE_STATE_UPDATE = "userVoiceStateUpdate"
    const val USER_VOICE_MOVED = "userVoiceMoved"
    const val USER_VOICE_REACTION = "userVoiceReaction"

    // emojis
    const val EMOJI_CREATE = "emojiCreate"
    const val EMOJI_UPDATE = "emojiUpdate"
    const val EMOJI_DELETE = "emojiDelete"

    // roles
    const val ROLE_CREATE = "roleCreate"
    const val ROLE_UPDATE = "roleUpdate"
    const val ROLE_DELETE = "roleDelete"

    // server
    const val SERVER_SETTINGS_UPDATE = "serverSettingsUpdate"

    // categories
    const val CATEGORY_CREATE = "categoryCreate"
    const val CATEGORY_UPDATE = "categoryUpdate"
    const val CATEGORY_DELETE = "categoryDelete"

    // dMs
    const val DM_CONVERSATION_OPEN = "dmConversationOpen"
}

// permission names matching packages/shared/src/statics/permissions.ts
object Permission {
    const val SEND_MESSAGES = "SEND_MESSAGES"
    const val REACT_TO_MESSAGES = "REACT_TO_MESSAGES"
    const val PIN_MESSAGES = "PIN_MESSAGES"
    const val UPLOAD_FILES = "UPLOAD_FILES"
    const val JOIN_VOICE_CHANNELS = "JOIN_VOICE_CHANNELS"
    const val SHARE_SCREEN = "SHARE_SCREEN"
    const val ENABLE_WEBCAM = "ENABLE_WEBCAM"
    const val SEND_VOICE_REACTION = "SEND_VOICE_REACTION"

    const val MANAGE_CHANNELS = "MANAGE_CHANNELS"
    const val MANAGE_CHANNEL_PERMISSIONS = "MANAGE_CHANNEL_PERMISSIONS"
    const val MANAGE_CATEGORIES = "MANAGE_CATEGORIES"
    const val MANAGE_ROLES = "MANAGE_ROLES"
    const val MANAGE_EMOJIS = "MANAGE_EMOJIS"
    const val MANAGE_SETTINGS = "MANAGE_SETTINGS"
    const val MANAGE_USERS = "MANAGE_USERS"
    const val MOVE_MEMBERS = "MOVE_MEMBERS"
    const val MANAGE_MESSAGES = "MANAGE_MESSAGES"
    const val MANAGE_STORAGE = "MANAGE_STORAGE"
    const val MANAGE_INVITES = "MANAGE_INVITES"
    const val MANAGE_UPDATES = "MANAGE_UPDATES"
    const val MANAGE_PLUGINS = "MANAGE_PLUGINS"
    const val MANAGE_PLUGIN_PERMISSIONS = "MANAGE_PLUGIN_PERMISSIONS"
    const val USE_PLUGINS = "USE_PLUGINS"
    const val VIEW_USER_SENSITIVE_DATA = "VIEW_USER_SENSITIVE_DATA"
}
