package com.sharkord.android.ui.emojipicker.domain.repository
import com.sharkord.android.R

import com.sharkord.android.ui.emojipicker.domain.model.EmojiCategory

internal interface EmojiCategoryRepository {
    fun list(): List<EmojiCategory>
    fun get(key: String): EmojiCategory?
}
