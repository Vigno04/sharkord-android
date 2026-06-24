package com.sharkord.android.data.model

data class VoiceUserState(
    val micMuted: Boolean = false,
    val soundMuted: Boolean = false,
    val webcamEnabled: Boolean = false,
    val sharingScreen: Boolean = false
)

data class VoiceUser(
    val userId: Int,
    val state: VoiceUserState
)

// maps channelId (String/Int) to a Map of userId (String/Int) to VoiceUserState
typealias VoiceChannelMap = Map<String, Map<String, VoiceUserState>>
