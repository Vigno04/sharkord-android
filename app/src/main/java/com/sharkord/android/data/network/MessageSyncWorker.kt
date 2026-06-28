package com.sharkord.android.data.network

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sharkord.android.data.session.SessionManager
import com.sharkord.android.utils.NotificationHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.google.gson.JsonObject
import com.sharkord.android.data.model.MessagesPage

class MessageSyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "MessageSyncWorker"
        private const val PREF_NAME = "MessageSyncWorkerPrefs"
        private const val KEY_LAST_COUNTS = "last_unread_counts"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Running background message sync...")

        val sessionManager = SessionManager(appContext)
        val token = sessionManager.token
        val serverUrl = sessionManager.serverUrl

        if (token == null || serverUrl == null) {
            Log.d(TAG, "No session, aborting sync")
            return Result.failure()
        }

        // Use a short-lived OkHttpClient for background sync to not conflict with foreground client
        val bgClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        
        val webSocketManager = WebSocketManager(bgClient)
        
        try {
            webSocketManager.connect(serverUrl, token)
            
            // Wait up to 15 seconds for connection
            val connectedState = withTimeoutOrNull(15000) {
                webSocketManager.connectionState.first { it.isConnected || it is ConnectionState.Error }
            }

            if (connectedState == null || connectedState is ConnectionState.Error) {
                Log.e(TAG, "Failed to connect for sync: ${connectedState?.javaClass?.simpleName}")
                return Result.retry()
            }

            // Connection successful! We have JoinServerData.
            val joinData = (connectedState as ConnectionState.Connected).serverData
            val readStates = joinData.readStates ?: emptyMap<String, Int>()
            
            val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val gson = Gson()
            val lastCountsJson = prefs.getString(KEY_LAST_COUNTS, "{}") ?: "{}"
            val type = object : TypeToken<Map<String, Int>>() {}.type
            val lastCounts: Map<String, Int> = gson.fromJson(lastCountsJson, type) ?: emptyMap<String, Int>()

            val userPrefs = appContext.getSharedPreferences("SharkordSettings", Context.MODE_PRIVATE)
            val notifyAll = userPrefs.getBoolean("notif_all_messages", false)
            val notifyMentions = userPrefs.getBoolean("notif_mentions_only", false)
            val notifyDms = userPrefs.getBoolean("notif_dms", false)
            val notifyReplies = userPrefs.getBoolean("notif_replies", false)

            var hasNewMessages = false

            // Compare current read states with last known read states
            for ((channelIdStr, count) in readStates) {
                val lastCount = lastCounts[channelIdStr] ?: 0
                if (count > lastCount) {
                    val channelId = channelIdStr.toIntOrNull() ?: continue
                    val channel = joinData.channels.find { it.id == channelId }
                    val channelName = channel?.name ?: "Unknown"
                    val isDm = channel?.isDm == true
                    
                    val delta = count - lastCount
                    
                    try {
                        val input = JsonObject().apply {
                            addProperty("channelId", channelId)
                            addProperty("limit", delta.coerceAtMost(10)) // Fetch up to 10 latest messages to be safe
                        }
                        val response = webSocketManager.sendQueryAwait("messages.get", input)
                        val page = gson.fromJson(response, MessagesPage::class.java)
                        
                        // we iterate through the new messages from newest to oldest
                        val newMessages = page.messages.sortedByDescending { it.createdAt }.take(delta)
                        
                        for (msg in newMessages) {
                            if (msg.userId == joinData.ownUserId) continue
                            
                            val ownUser = joinData.users.find { it.id == joinData.ownUserId }
                            val isReplyToMe = msg.replyTo?.userId == joinData.ownUserId
                            val isMention = ownUser != null && msg.content.contains("@${ownUser.name}")
                            
                            val shouldNotify = when {
                                isDm -> notifyDms || notifyAll
                                notifyAll -> true
                                notifyReplies && isReplyToMe -> true
                                notifyMentions && isMention -> true
                                else -> false
                            }
                            
                            if (shouldNotify) {
                                val senderName = joinData.users.find { it.id == msg.userId }?.name ?: "Someone"
                                val replyToUserId = msg.replyTo?.userId
                                val replyToName = if (replyToUserId != null) {
                                    if (replyToUserId == joinData.ownUserId) "you" else joinData.users.find { it.id == replyToUserId }?.name
                                } else null
                                
                                NotificationHelper.showNewMessageNotification(
                                    context = appContext,
                                    channelId = channelId,
                                    senderName = senderName,
                                    messageContent = msg.content,
                                    channelName = channelName,
                                    replyToName = replyToName
                                )
                                hasNewMessages = true
                                break // Only show the latest matching message for this channel
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch messages for sync", e)
                    }
                }
            }

            // Save new states
            prefs.edit().putString(KEY_LAST_COUNTS, gson.toJson(readStates)).apply()
            
            Log.d(TAG, "Sync complete. Found new messages: $hasNewMessages")
            return Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during sync", e)
            return Result.retry()
        } finally {
            // CRITICAL: Disconnect immediately to save battery and network
            webSocketManager.disconnect()
        }
    }
}
