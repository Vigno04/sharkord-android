package com.sharkord.android.ui.emojipicker.domain.model
import com.sharkord.android.R

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes


internal enum class EmojiCategory(
    val key: String,
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
) {
    RECENT(
        "recent",
        R.string.category_recent,
        R.drawable.ic_recent
    ),

    SMILEYS_AND_PEOPLE(
        key = "smileys_and_people",
        title = R.string.category_smileys_and_people,
        icon = R.drawable.ic_smileys
    ),
    ANIMALS_AND_NATURE(
        key = "animals_and_nature",
        title = R.string.category_animals_and_nature,
        icon = R.drawable.ic_nature
    ),
    FOOD_AND_DRINK(
        key = "food_and_drink",
        title = R.string.category_food_and_drink,
        icon = R.drawable.ic_food
    ),
    TRAVEL_AND_PLACES(
        key = "travel_and_places",
        title = R.string.category_travel_and_places,
        icon = R.drawable.ic_travel
    ),
    ACTIVITY(key = "activity", title = R.string.category_activity, icon = R.drawable.ic_activity),
    OBJECTS(key = "objects", title = R.string.category_objects, icon = R.drawable.ic_objects),
    SYMBOLS(key = "symbols", title = R.string.category_symbols, icon = R.drawable.ic_symbols),
    FLAGS(key = "flags", title = R.string.category_flags, icon = R.drawable.ic_flags);

    companion object {
        fun findByKey(key: String): EmojiCategory? {
            return EmojiCategory.entries.find {
                it.key == key
            }
        }
    }
}
