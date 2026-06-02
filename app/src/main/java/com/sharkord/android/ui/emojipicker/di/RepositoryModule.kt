package com.sharkord.android.ui.emojipicker.di
import com.sharkord.android.R

import android.content.Context
import com.sharkord.android.ui.emojipicker.data.EmojiCategoryRepositoryImpl
import com.sharkord.android.ui.emojipicker.data.EmojiRepositoryImpl
import com.sharkord.android.ui.emojipicker.domain.repository.EmojiCategoryRepository
import com.sharkord.android.ui.emojipicker.domain.repository.EmojiRepository
import com.sharkord.android.ui.emojipicker.domain.use_case.AddToRecentEmojisUseCase
import com.sharkord.android.ui.emojipicker.domain.use_case.GetEmojiCategoriesUseCase
import com.sharkord.android.ui.emojipicker.domain.use_case.GetEmojiCategoryByKeyUseCase
import com.sharkord.android.ui.emojipicker.domain.use_case.GetEmojisUseCase
import com.sharkord.android.ui.emojipicker.presentation.EmojiPickerViewModel

internal object RepositoryModule {
    private fun provideEmojiRepository(
        context: Context,
    ): EmojiRepository = EmojiRepositoryImpl(
        context = context,
    )

    private fun provideEmojiCategoryRepository(): EmojiCategoryRepository = EmojiCategoryRepositoryImpl()

    fun provideEmojiPickerViewModel(
        context: Context
    ): EmojiPickerViewModel {
        val emojiRepository = provideEmojiRepository(context)
        val emojiCategoryRepository = provideEmojiCategoryRepository()

        return EmojiPickerViewModel(
            getEmojisUseCase = GetEmojisUseCase(
                repository = emojiRepository
            ),
            getEmojiCategoriesUseCase = GetEmojiCategoriesUseCase(
                repository = emojiCategoryRepository
            ),
            getEmojiCategoryByKeyUseCase = GetEmojiCategoryByKeyUseCase(
                repository = emojiCategoryRepository
            ),
            addToRecentEmojisUseCase = AddToRecentEmojisUseCase(
                repository = emojiRepository
            ),
        )
    }

}
