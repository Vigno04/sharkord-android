package com.sharkord.android.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.data.repository.ServerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.sharkord.android.ACTION_MARK_AS_READ") {
            val channelId = intent.getIntExtra("channel_id", -1)
            if (channelId == -1) return

            // immediately clear the notification for responsive UI
            NotificationHelper.clearNotification(context, channelId)

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    try {
                        SharkordClient.applicationContext
                    } catch (e: Exception) {
                        SharkordClient.initialize(context.applicationContext)
                    }

                    val repository = ServerRepository()
                    if (SharkordClient.webSocket.connectionState.value !is com.sharkord.android.data.network.ConnectionState.Connected) {
                        if (repository.restoreSession()) {
                            repository.connectWebSocket()
                        }
                    }

                    repository.markChannelAsRead(channelId)
                } catch (e: Exception) {
                    Log.e("NotificationAction", "Failed to mark channel $channelId as read", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
