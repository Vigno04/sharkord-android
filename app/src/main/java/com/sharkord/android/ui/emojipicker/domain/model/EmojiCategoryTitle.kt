package com.sharkord.android.ui.emojipicker.domain.model
import com.sharkord.android.R

internal data class EmojiCategoryTitle(
    override val id: String,
    val category: EmojiCategory
) : EmojiListItem(Type.TITLE)
