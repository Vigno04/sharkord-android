package com.sharkord.android.utils

import android.content.Context
import android.util.Log
import com.sharkord.android.data.network.SharkordClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

object DiskCacheManager {
    private const val TAG = "DiskCacheManager"

    // Priority 0: Delete First (Videos, Audio)
    // Priority 1: Medium (Documents, other temps)
    // Priority 2: Keep Longest (Images, Thumbnails)
    private fun getFilePriority(file: File): Int {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".mp4") || name.endsWith(".webm") || name.endsWith(".mkv") -> 0
            name.endsWith(".m4a") || name.endsWith(".mp3") || name.endsWith(".ogg") || name.endsWith(".wav") -> 0
            name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".img_cache") -> 2
            else -> 1
        }
    }

    private fun getAllFilesRecursively(dir: File): List<File> {
        return dir.walkBottomUp().filter { it.isFile }.toList()
    }

    fun trim(context: Context) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val cacheDir = context.cacheDir
                if (cacheDir == null || !cacheDir.exists()) return@launch

                val allFiles = getAllFilesRecursively(cacheDir)
                var totalSize = allFiles.sumOf { it.length() }
                
                val maxMb = SharkordClient.session.maxDiskCacheMb
                val maxDiskCacheSize = maxMb * 1024 * 1024L

                if (totalSize > maxDiskCacheSize) {
                    Log.d(TAG, "Trimming disk cache: current size is ${totalSize / (1024 * 1024)} MB (max $maxMb MB)")
                    
                    // sort by priority first (0 first, then 1, then 2), 
                    // then by last modified (oldest first)
                    val sortedFiles = allFiles.sortedWith(
                        compareBy<File> { getFilePriority(it) }
                            .thenBy { it.lastModified() }
                    )

                    val now = System.currentTimeMillis()
                    val fiveMinutesMs = 5 * 60 * 1000L

                    for (file in sortedFiles) {
                        // don't delete files being actively written/recorded
                        if (now - file.lastModified() < fiveMinutesMs) continue

                        val length = file.length()
                        if (file.delete()) {
                            totalSize -= length
                        }
                        
                        // trim down to maxDiskCacheSize / 2 to avoid running this on every small file addition
                        if (totalSize <= maxDiskCacheSize / 2) {
                            break
                        }
                    }
                    Log.d(TAG, "Trimmed disk cache down to ${totalSize / (1024 * 1024)} MB")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error trimming disk cache", e)
            }
        }
    }
}
