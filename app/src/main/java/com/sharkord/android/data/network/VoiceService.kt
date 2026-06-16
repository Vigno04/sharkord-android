package com.sharkord.android.data.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.gson.JsonObject
import com.sharkord.android.MainActivity
import com.sharkord.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VoiceService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_TOGGLE_MIC = "ACTION_TOGGLE_MIC"
        const val ACTION_TOGGLE_DEAFEN = "ACTION_TOGGLE_DEAFEN"
        private const val CHANNEL_ID = "VoiceServiceChannel"
        private const val NOTIFICATION_ID = 1001
    }

    private var currentChannelName: String = ""
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val name = intent.getStringExtra("EXTRA_CHANNEL_NAME")
                if (name != null) {
                    currentChannelName = name
                }
                startForegroundService()
            }
            ACTION_STOP -> stopForegroundService()
            ACTION_TOGGLE_MIC -> toggleMic()
            ACTION_TOGGLE_DEAFEN -> toggleDeafen()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, VoiceService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleMicIntent = Intent(this, VoiceService::class.java).apply {
            action = ACTION_TOGGLE_MIC
        }
        val toggleMicPendingIntent = PendingIntent.getService(
            this,
            2,
            toggleMicIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleDeafenIntent = Intent(this, VoiceService::class.java).apply {
            action = ACTION_TOGGLE_DEAFEN
        }
        val toggleDeafenPendingIntent = PendingIntent.getService(
            this,
            3,
            toggleDeafenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isMicMuted = SharkordClient.voiceEngine.isMicMuted
        val isSoundMuted = SharkordClient.voiceEngine.isSoundMuted

        val micActionText = if (isMicMuted) "Riattiva microfono" else "Silenzia"
        val deafenActionText = if (isSoundMuted) "Riattiva audio" else "Silenzia audio"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Connesso alla chat vocale")
            .setContentText("Tocca per tornare alla chiamata\n$currentChannelName")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Tocca per tornare alla chiamata\n$currentChannelName"))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .addAction(0, "Disconnetti", stopPendingIntent)
            .addAction(0, micActionText, toggleMicPendingIntent)
            .addAction(0, deafenActionText, toggleDeafenPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun toggleMic() {
        val newMuted = !SharkordClient.voiceEngine.isMicMuted
        val currentDeafened = SharkordClient.voiceEngine.isSoundMuted
        
        // If unmuting while deafened, also undeafen (Discord behavior)
        val newDeafened = if (!newMuted && currentDeafened) false else currentDeafened

        updateVoiceState(newMuted, newDeafened)
    }

    private fun toggleDeafen() {
        val newDeafened = !SharkordClient.voiceEngine.isSoundMuted
        val newMuted = if (newDeafened) true else SharkordClient.voiceEngine.isMicMuted

        updateVoiceState(newMuted, newDeafened)
    }

    private fun updateVoiceState(micMuted: Boolean, soundMuted: Boolean) {
        SharkordClient.voiceEngine.setMicEnabled(!micMuted)
        SharkordClient.voiceEngine.setSoundEnabled(!soundMuted)
        
        // Update notification
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification())

        // Notify server
        scope.launch {
            try {
                val input = JsonObject().apply {
                    addProperty("micMuted", micMuted)
                    addProperty("soundMuted", soundMuted)
                }
                SharkordClient.webSocket.sendMutationAwait("voice.updateState", input)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopForegroundService() {
        try {
            SharkordClient.voiceEngine.leaveChannel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            SharkordClient.voiceEngine.leaveChannel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopForegroundService()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Voice Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}
