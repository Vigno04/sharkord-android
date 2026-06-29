package com.sharkord.android.ui.emojipicker.common
import com.sharkord.android.R

import com.sharkord.android.ui.emojipicker.domain.model.EmojiCategory

internal object EmojiConstants {
    val categoryOrder = listOf(
        EmojiCategory.RECENT,
        EmojiCategory.SMILEYS_AND_PEOPLE,
        EmojiCategory.ANIMALS_AND_NATURE,
        EmojiCategory.FOOD_AND_DRINK,
        EmojiCategory.ACTIVITY,
        EmojiCategory.TRAVEL_AND_PLACES,
        EmojiCategory.OBJECTS,
        EmojiCategory.SYMBOLS,
        EmojiCategory.FLAGS
    )

    const val EMOJI_PER_ROW = 6

    const val RECENT_EMOJI_LIMIT = 18
}
