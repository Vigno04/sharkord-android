package com.sharkord.android.utils

import android.content.Context
import android.util.Log
import com.sharkord.android.data.network.SharkordClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

object DiskCacheManager {
    private const val TAG = "DiskCacheManager"

    // Base score for priorities:
    // Text/Structure = 300
    // Previews (.img_cache) = 200
    // Images = 100
    // Documents/Others = 50
    // Videos/Audio = 0
    private fun getFileBaseScore(file: File): Float {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".json") || name.endsWith(".txt") -> 300f
            name.endsWith(".img_cache") -> 200f
            name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") -> 100f
            name.endsWith(".mp4") || name.endsWith(".webm") || name.endsWith(".mkv") -> 0f
            name.endsWith(".m4a") || name.endsWith(".mp3") || name.endsWith(".ogg") || name.endsWith(".wav") -> 0f
            else -> 50f
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
                    
                    val now = System.currentTimeMillis()
                    
                    // calculate eviction score for each file. Lowest score is deleted first.
                    // age penalty: lose 10 points per day. So an image (100) older than 10 days is worth less than a new video (0).
                    val fileScores = allFiles.associateWith { file ->
                        val ageMs = now - file.lastModified()
                        val ageDays = ageMs / (1000f * 60f * 60f * 24f)
                        val baseScore = getFileBaseScore(file)
                        baseScore - (ageDays * 10f)
                    }

                    // sort by score (lowest first)
                    val sortedFiles = allFiles.sortedBy { fileScores[it] ?: 0f }

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
