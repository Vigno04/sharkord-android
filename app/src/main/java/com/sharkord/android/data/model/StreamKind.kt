package com.sharkord.android.data.model

enum class StreamKind(val value: String) {
    AUDIO("audio"),
    VIDEO("video"),
    SCREEN("screen"),
    SCREEN_AUDIO("screen_audio"),
    EXTERNAL_VIDEO("external_video"),
    EXTERNAL_AUDIO("external_audio");

    companion object {
        fun fromValue(value: String): StreamKind {
            return entries.find { it.value == value } ?: AUDIO
        }
    }
}
