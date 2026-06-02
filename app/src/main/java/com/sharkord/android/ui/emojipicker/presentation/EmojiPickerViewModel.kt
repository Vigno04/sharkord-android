package com.sharkord.android.ui.emojipicker.presentation
import com.sharkord.android.R

import androidx.lifecycle.ViewModel
import com.sharkord.android.ui.emojipicker.common.EmojiConstants
import com.sharkord.android.ui.emojipicker.domain.model.Emoji
import com.sharkord.android.ui.emojipicker.domain.model.EmojiCategoryTitle
import com.sharkord.android.ui.emojipicker.domain.model.EmojiItem
import com.sharkord.android.ui.emojipicker.domain.model.EmojiListItem
import com.sharkord.android.ui.emojipicker.domain.use_case.AddToRecentEmojisUseCase
import com.sharkord.android.ui.emojipicker.domain.use_case.GetEmojiCategoriesUseCase
import com.sharkord.android.ui.emojipicker.domain.use_case.GetEmojiCategoryByKeyUseCase
import com.sharkord.android.ui.emojipicker.domain.use_case.GetEmojisUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class EmojiPickerViewModel(
    private val getEmojisUseCase: GetEmojisUseCase,
    private val getEmojiCategoriesUseCase: GetEmojiCategoriesUseCase,
    private val getEmojiCategoryByKeyUseCase: GetEmojiCategoryByKeyUseCase,
    private val addToRecentEmojisUseCase: AddToRecentEmojisUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(EmojiPickerState())
    val state = _state.asStateFlow()

    init {
        loadEmojisAndCategories()
    }

    private fun loadEmojisAndCategories() {
        _state.update {
            it.copy(
                emojiCategories = getEmojiCategoriesUseCase()
            )
        }
        updateEmojis()
    }

    private fun updateEmojis() {
        val searchText = state.value.searchText
        val emojis = getEmojisUseCase(searchText)

        val emojiListItems: List<EmojiListItem> = emojis.groupBy {
            // group by category
            it.category
        }.mapKeys {
            // get EmojiCategory object from key
            getEmojiCategoryByKeyUseCase(it.key)!!
        }.toList().sortedBy { entry ->
            EmojiConstants.categoryOrder.find { it.key == entry.first.key }
        }.flatMap {
            val category = it.first
            val categoryEmojis = it.second

            val categoryTitleListItem: EmojiListItem = EmojiCategoryTitle(
                id = category.key,
                category = category
            )
            val emojiListItems: Array<EmojiListItem> = categoryEmojis.map { emoji ->
                EmojiItem(
                    id = emoji.id,
                    emoji = emoji
                )
            }.toTypedArray()

            listOf(categoryTitleListItem, *emojiListItems)
        }

        // populate indexes map
        val categoryTitleIndexes: MutableMap<String, Int> = mutableMapOf()
        EmojiConstants.categoryOrder.forEach { category ->
            // set index in map
            categoryTitleIndexes[category.key] = emojiListItems.indexOfFirst {
                it.id == category.key
            }
        }

        _state.update {
            it.copy(
                emojiListItems = emojiListItems,
                categoryTitleIndexes = categoryTitleIndexes
            )
        }
    }

    fun onSearchTextChanged(searchText: String) {
        _state.update {
            it.copy(
                searchText = searchText
            )
        }

        // recompute the emojis
        updateEmojis()
    }

    fun onAddToRecent(emoji: Emoji) {
        addToRecentEmojisUseCase(emoji)
    }
}
