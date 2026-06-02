package com.sharkord.android.ui.emojipicker.presentation
import com.sharkord.android.R

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
class EmojiPickerColors(
    val backgroundColor: Color,
    val searchBarBackgroundColor: Color,
    val searchBarIconTint: Color,
    val searchBarTextColor: Color,
    val textColor: Color,
    val activeCategoryTint: Color,
    val inactiveCategoryTint: Color
)

object EmojiPickerDefaults {
    @Composable
    fun emojiPickerColors(): EmojiPickerColors {
        return EmojiPickerColors(
            backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
            searchBarBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
            searchBarIconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            searchBarTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
            activeCategoryTint = MaterialTheme.colorScheme.primary,
            inactiveCategoryTint = MaterialTheme.colorScheme.onSurface,
        )
    }

    @Composable
    fun emojiPickerColors(
        backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
        searchBarBackgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
        searchBarIconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        searchBarTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        activeCategoryTint: Color = MaterialTheme.colorScheme.primary,
        inactiveCategoryTint: Color = MaterialTheme.colorScheme.onSurface,
    ): EmojiPickerColors {
        return EmojiPickerColors(
            backgroundColor = backgroundColor,
            searchBarBackgroundColor = searchBarBackgroundColor,
            searchBarIconTint = searchBarIconTint,
            searchBarTextColor = searchBarTextColor,
            textColor = textColor,
            activeCategoryTint = activeCategoryTint,
            inactiveCategoryTint = inactiveCategoryTint
        )
    }
}
