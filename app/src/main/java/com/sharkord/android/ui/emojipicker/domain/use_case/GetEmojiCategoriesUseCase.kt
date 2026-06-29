package com.sharkord.android.ui.emojipicker.domain.use_case
import com.sharkord.android.R

import com.sharkord.android.ui.emojipicker.domain.model.EmojiCategory
import com.sharkord.android.ui.emojipicker.domain.repository.EmojiCategoryRepository

internal class GetEmojiCategoriesUseCase(
    private val repository: EmojiCategoryRepository
) {
    operator fun invoke(): List<EmojiCategory> {
        return repository.list()
    }
}
