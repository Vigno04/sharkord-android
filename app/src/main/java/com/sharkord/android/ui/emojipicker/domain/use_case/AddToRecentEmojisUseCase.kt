package com.sharkord.android.ui.emojipicker.domain.use_case
import com.sharkord.android.R

import com.sharkord.android.ui.emojipicker.domain.model.Emoji
import com.sharkord.android.ui.emojipicker.domain.repository.EmojiRepository

internal class AddToRecentEmojisUseCase(
    private val repository: EmojiRepository
) {
    operator fun invoke(emoji: Emoji) {
        repository.addToRecentEmojis(emoji)
    }
}
