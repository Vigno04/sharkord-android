package com.sharkord.android.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val releaseUrl: String
)

object UpdateManager {
    private const val TAG = "UpdateManager"
    private const val REPO_LATEST_RELEASE_URL = "https://api.github.com/repos/Vigno04/sharkord-android/releases/latest"

    suspend fun checkForUpdates(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(REPO_LATEST_RELEASE_URL)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val json = JSONObject(responseBody)
                    val tagName = json.getString("tag_name")
                    val htmlUrl = json.getString("html_url")
                    
                    val currentVersion = getCurrentVersion(context)
                    
                    // Simple comparison, assumes format like "v1.0.0" or "1.0.0"
                    val latestVersionClean = tagName.removePrefix("v")
                    val currentVersionClean = currentVersion.removePrefix("v")
                    
                    val hasUpdate = isNewerVersion(currentVersionClean, latestVersionClean)
                    
                    return@withContext UpdateInfo(
                        hasUpdate = hasUpdate,
                        latestVersion = tagName,
                        releaseUrl = htmlUrl
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates", e)
        }
        return@withContext null
    }

    private fun getCurrentVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "0.0.0"
        } catch (e: Exception) {
            "0.0.0"
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until length) {
            val currentPart = currentParts.getOrElse(i) { 0 }
            val latestPart = latestParts.getOrElse(i) { 0 }
            if (currentPart < latestPart) return true
            if (currentPart > latestPart) return false
        }
        return false
    }
}
