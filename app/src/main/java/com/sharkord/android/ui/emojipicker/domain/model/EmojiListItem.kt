package com.sharkord.android.ui.emojipicker.domain.model
import com.sharkord.android.R

internal sealed class EmojiListItem(
    open val type: Type
) {
    open val id: String = ""

    enum class Type(
        val value: String
    ) {
        TITLE("title"),
        EMOJI("emoji")
    }
}
