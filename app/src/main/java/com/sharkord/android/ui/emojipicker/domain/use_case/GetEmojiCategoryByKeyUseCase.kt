package com.sharkord.android.ui.emojipicker.domain.use_case
import com.sharkord.android.R

import com.sharkord.android.ui.emojipicker.domain.model.EmojiCategory
import com.sharkord.android.ui.emojipicker.domain.repository.EmojiCategoryRepository

internal class GetEmojiCategoryByKeyUseCase(
    private val repository: EmojiCategoryRepository
) {
    operator fun invoke(key: String): EmojiCategory? {
        return repository.get(key)
    }
}
