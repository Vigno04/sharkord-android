package com.sharkord.android.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sharkord.android.MainActivity
import com.sharkord.android.R

object NotificationHelper {

    private const val CHANNEL_ID = "sharkord_messages_channel"
    private const val CHANNEL_NAME = "Messages"
    private const val CHANNEL_DESC = "Notifications for new messages"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNewMessageNotification(
        context: Context,
        channelId: Int,
        senderName: String,
        messageContent: String,
        channelName: String? = null,
        replyToName: String? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // we can add extras here to route to the specific channel if the Navigation system supports it
            putExtra("target_channel_id", channelId)
        }
        
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            channelId, // use channelId as requestCode to have separate pending intents per channel
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (channelName != null) {
            if (replyToName != null) {
                "$senderName replied to $replyToName in #$channelName"
            } else {
                "$senderName in #$channelName"
            }
        } else {
            if (replyToName != null) {
                "$senderName replied to $replyToName"
            } else {
                senderName
            }
        }

        val parsedMessageContent = androidx.core.text.HtmlCompat.fromHtml(messageContent, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT)

        val markAsReadIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "com.sharkord.android.ACTION_MARK_AS_READ"
            putExtra("channel_id", channelId)
        }
        val markAsReadPendingIntent: PendingIntent = PendingIntent.getBroadcast(
            context,
            channelId,
            markAsReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_custom) // custom logo icon
            .setContentTitle(title)
            .setContentText(parsedMessageContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(parsedMessageContent))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(0, "Mark as read", markAsReadPendingIntent)

        with(NotificationManagerCompat.from(context)) {
            // using channelId as notification ID to group/replace notifications per channel
            notify(channelId, builder.build())
        }
    }

    fun clearNotification(context: Context, channelId: Int) {
        with(NotificationManagerCompat.from(context)) {
            cancel(channelId)
        }
    }
}
