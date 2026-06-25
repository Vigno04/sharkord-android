package com.sharkord.android.ui.emojipicker.data
import com.sharkord.android.R

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

import com.sharkord.android.ui.emojipicker.common.EmojiConstants
import com.sharkord.android.ui.emojipicker.domain.model.Emoji
import com.sharkord.android.ui.emojipicker.domain.model.EmojiCategory
import com.sharkord.android.ui.emojipicker.domain.repository.EmojiRepository

private const val RECENT_EMOJIS_KEY = "emoji_picker_recent_emojis"
private const val SHARED_PREFERENCES_KEY = "emoji_picker_shared_preferences"

internal class EmojiRepositoryImpl(
    private val context: Context
): EmojiRepository {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        SHARED_PREFERENCES_KEY,
        Context.MODE_PRIVATE
    )
    private val listType = object : TypeToken<List<Emoji>>() {}.type
    private val emojis: List<Emoji>

    init {
        val json = context.resources.openRawResource(R.raw.emojis)
            .bufferedReader().use { it.readText() }

        val emojiList: List<Emoji> =
            Gson().fromJson(json, object : TypeToken<MutableList<Emoji>>() {})

        this.emojis = emojiList
    }

   private fun getRecentEmojis(): List<Emoji> {
        val recentEmojis = sharedPreferences.getString(RECENT_EMOJIS_KEY, null)
        return if (recentEmojis.isNullOrEmpty()) {
            mutableListOf()
        } else {
            Gson().fromJson(recentEmojis, listType)
        }
   }

    override fun list(
        searchText: String
    ): List<Emoji> {
        val emojis = ArrayList(this.emojis)

        val recentEmojis = this.getRecentEmojis()
        val mappedRecentEmojis = recentEmojis.map { recentEmoji ->
            recentEmoji.copy(
                id = "recent_${recentEmoji.id}",
                category = EmojiCategory.RECENT.key
            )
        }
        emojis.addAll(0, mappedRecentEmojis)

        return emojis.filter {
            val categoryTitle = context.getString(EmojiCategory.findByKey(it.category)!!.title)

            // search in emoji or category name
            val whereToSearch = listOf(it.name, categoryTitle)

            whereToSearch.any { searchContext ->
                searchContext.lowercase().contains(searchText.lowercase())
            }
        }
    }

    override fun addToRecentEmojis(emoji: Emoji) {
        val recentEmojis = ArrayList(this.getRecentEmojis())

        if (recentEmojis.contains(emoji)) {
            // remove item if already in the list
            recentEmojis.remove(emoji)
        }
        recentEmojis.add(0, emoji)

        // keep only first EmojiConstants.RECENT_EMOJI_LIMIT items (since we add at 0)
        val limitedRecent = recentEmojis.take(EmojiConstants.RECENT_EMOJI_LIMIT)

        // update shared preferences
        sharedPreferences.edit()
            .putString(RECENT_EMOJIS_KEY, Gson().toJson(limitedRecent, listType))
            .apply()
    }
}
