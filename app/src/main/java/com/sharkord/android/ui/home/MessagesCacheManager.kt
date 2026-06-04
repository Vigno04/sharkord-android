package com.sharkord.android.ui.home

import android.util.LruCache
import com.sharkord.android.data.model.Message

data class ChannelCacheEntry(
    val messages: List<Message>,
    val nextCursor: Long?,
    val hasReachedTop: Boolean
)

/**
 * Singleton cache for channel messages to allow instant switching between channels
 * without losing scroll position or triggering full network reloads.
 */
object MessagesCacheManager {
    // Cache up to 50 channels in memory.
    // Text data is very small, so this is extremely safe memory-wise.
    private val cache = LruCache<Int, ChannelCacheEntry>(50)

    fun getChannelCache(channelId: Int): ChannelCacheEntry? {
        return cache.get(channelId)
    }

    fun updateChannelCache(channelId: Int, entry: ChannelCacheEntry) {
        cache.put(channelId, entry)
    }

    fun clearCache() {
        cache.evictAll()
    }
}
