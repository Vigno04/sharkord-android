package com.sharkord.android.ui.emojipicker.domain.model
import com.sharkord.android.R

internal data class EmojiItem(
    override val id: String,
    val emoji: Emoji
) : EmojiListItem(Type.EMOJI)
