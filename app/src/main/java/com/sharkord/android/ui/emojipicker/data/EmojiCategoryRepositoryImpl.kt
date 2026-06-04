package com.sharkord.android.ui.emojipicker.data
import com.sharkord.android.R

import com.sharkord.android.ui.emojipicker.domain.model.EmojiCategory
import com.sharkord.android.ui.emojipicker.domain.repository.EmojiCategoryRepository

internal class EmojiCategoryRepositoryImpl: EmojiCategoryRepository {
    private val emojiCategories: List<EmojiCategory> = EmojiCategory.entries.toList()

    override fun list(): List<EmojiCategory> {
        return this.emojiCategories
    }

    override fun get(key: String): EmojiCategory? {
        return EmojiCategory.findByKey(key)
    }
}
