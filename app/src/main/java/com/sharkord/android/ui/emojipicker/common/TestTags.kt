package com.sharkord.android.ui.emojipicker.common
import com.sharkord.android.R

import com.sharkord.android.ui.emojipicker.domain.model.Emoji
import com.sharkord.android.ui.emojipicker.domain.model.EmojiCategory

internal object TestTags {
    const val EMOJI_PICKER = "EMOJI_PICKER"
    const val EMPTY_EMOJI_PICKER = "EMPTY_EMOJI_PICKER"
    const val SEARCH_BAR = "SEARCH_BAR"

    fun tabButton(emojiCategory: EmojiCategory): String {
        return "${emojiCategory.key}_button"
    }

    fun get(emoji: Emoji): String {
        return emoji.name
    }

    fun get(emojiCategory: EmojiCategory): String {
        return emojiCategory.key
    }
}
