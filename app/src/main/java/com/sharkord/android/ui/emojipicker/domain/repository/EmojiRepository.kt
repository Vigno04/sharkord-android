package com.sharkord.android.ui.emojipicker.domain.repository
import com.sharkord.android.R

import com.sharkord.android.ui.emojipicker.domain.model.Emoji

internal interface EmojiRepository {
    fun list(searchText: String): List<Emoji>
    fun addToRecentEmojis(emoji: Emoji)
}
