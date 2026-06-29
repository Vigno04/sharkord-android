package com.sharkord.android.ui.emojipicker.presentation
import com.sharkord.android.R

import com.sharkord.android.ui.emojipicker.domain.model.EmojiCategory
import com.sharkord.android.ui.emojipicker.domain.model.EmojiListItem

internal data class EmojiPickerState(
    val emojiCategories: List<EmojiCategory>? = null,
    val emojiListItems: List<EmojiListItem>? = null,

    val categoryTitleIndexes: Map<String, Int> = emptyMap(),

    val searchText: String = ""
)
