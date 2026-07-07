package com.sharkord.android.data.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import com.google.gson.JsonObject
import com.sharkord.android.MainActivity
import com.sharkord.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sharkord.android.ui.pip.FloatingPipScreen

private class MyLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

class VoiceService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_TOGGLE_MIC = "ACTION_TOGGLE_MIC"
        const val ACTION_TOGGLE_DEAFEN = "ACTION_TOGGLE_DEAFEN"
        const val ACTION_START_SCREEN_SHARE = "ACTION_START_SCREEN_SHARE"
        const val ACTION_STOP_SCREEN_SHARE = "ACTION_STOP_SCREEN_SHARE"
        const val ACTION_SHOW_OVERLAY = "ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "ACTION_HIDE_OVERLAY"
        const val ACTION_SET_APP_VISIBLE = "ACTION_SET_APP_VISIBLE"
        private const val CHANNEL_ID = "VoiceServiceChannel"
        private const val NOTIFICATION_ID = 1001
    }

    private var currentChannelName: String = ""
    private var isScreenSharing: Boolean = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Floating PiP
    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var overlayLifecycleOwner: MyLifecycleOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isAppVisible = true
    private var videoTrackCollectionJob: kotlinx.coroutines.Job? = null
    private var stoppedCleanly = false

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
            ACTION_START_SCREEN_SHARE -> {
                isScreenSharing = true
                startForegroundService()
                val mediaProjectionIntent = intent.getParcelableExtra<Intent>("EXTRA_MEDIA_PROJECTION_INTENT")
                SharkordClient.voiceEngine.setScreenShareEnabled(this, mediaProjectionIntent, true)
            }
            ACTION_STOP_SCREEN_SHARE -> {
                SharkordClient.voiceEngine.setScreenShareEnabled(this, null, false)
                isScreenSharing = false
                startForegroundService()
            }
            ACTION_SHOW_OVERLAY -> {
                if (SharkordClient.isVoiceEngineInitialized && SharkordClient.voiceEngine.isConnected.value) {
                    showOverlay()
                }
            }
            ACTION_HIDE_OVERLAY -> {
                hideOverlay()
            }
            ACTION_SET_APP_VISIBLE -> {
                isAppVisible = intent.getBooleanExtra("EXTRA_VISIBLE", true)
                if (isAppVisible) {
                    hideOverlay()
                } else {
                    checkAndShowOverlayIfNeeded()
                }
            }
        }
        return START_NOT_STICKY
    }

    // starts the foreground service and shows the ongoing notification
    private fun startForegroundService() {
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (isScreenSharing) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            
            startForeground(
                NOTIFICATION_ID, 
                buildNotification(), 
                type
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    // builds the notification for the foreground service
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

        val isMicMuted = if (SharkordClient.isVoiceEngineInitialized) SharkordClient.voiceEngine.isMicMuted else true
        val isSoundMuted = if (SharkordClient.isVoiceEngineInitialized) SharkordClient.voiceEngine.isSoundMuted else true

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

    // toggles the microphone mute state
    private fun toggleMic() {
        if (!SharkordClient.isVoiceEngineInitialized) return
        val newMuted = !SharkordClient.voiceEngine.isMicMuted
        val currentDeafened = SharkordClient.voiceEngine.isSoundMuted
        
        // if unmuting while deafened, also undeafen (Discord behavior)
        val newDeafened = if (!newMuted && currentDeafened) false else currentDeafened

        updateVoiceState(newMuted, newDeafened)
    }

    // toggles the deafen state
    private fun toggleDeafen() {
        if (!SharkordClient.isVoiceEngineInitialized) return
        val newDeafened = !SharkordClient.voiceEngine.isSoundMuted
        val newMuted = if (newDeafened) true else SharkordClient.voiceEngine.isMicMuted

        updateVoiceState(newMuted, newDeafened)
    }

    // updates the voice engine state and syncs with the server
    private fun updateVoiceState(micMuted: Boolean, soundMuted: Boolean) {
        SharkordClient.voiceEngine.setMicEnabled(!micMuted)
        SharkordClient.voiceEngine.setSoundEnabled(!soundMuted)
        
        // update notification
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification())

        // notify server
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

    // stops the foreground service and leaves the active voice channel
    private fun stopForegroundService() {
        stoppedCleanly = true
        scope.launch {
            try {
                if (SharkordClient.voiceEngine.isConnected.value) {
                    SharkordClient.voiceEngine.leaveChannel()
                    SharkordClient.webSocket.sendMutationAwait("voice.leave", com.google.gson.JsonObject())
                    com.sharkord.android.audio.SoundEngine.playSound(com.sharkord.android.audio.SoundType.OWN_USER_LEFT_VOICE_CHANNEL)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // If app is in background, pause WS so WS drops don't trigger disconnect sounds.
            // MainActivity.onStart will call resumeConnection when app returns to foreground.
            if (!isAppVisible) {
                SharkordClient.webSocket.pauseConnection()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            hideOverlay()
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (SharkordClient.isVoiceEngineInitialized) {
            startVideoTrackCollection()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        videoTrackCollectionJob?.cancel()
        // only leave channel if we weren't already stopped cleanly via stopForegroundService()
        if (!stoppedCleanly) {
            scope.launch {
                try {
                    if (SharkordClient.voiceEngine.isConnected.value) {
                        SharkordClient.voiceEngine.leaveChannel()
                        SharkordClient.webSocket.sendMutationAwait("voice.leave", com.google.gson.JsonObject())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        hideOverlay()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopForegroundService()
    }

    // creates the notification channel for modern android versions
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

    private fun showOverlay() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            scope.launch(Dispatchers.Main) { showOverlay() }
            return
        }

        if (composeView != null) return // Already showing

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        // Rounded square layout
        val density = resources.displayMetrics.density
        val size = (160 * density).toInt()
 
        layoutParams = WindowManager.LayoutParams(
            size,
            size,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        composeView = ComposeView(this).apply {
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                    val radius = 16f * resources.displayMetrics.density
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
            setContent {
                val voiceEngine = SharkordClient.voiceEngine
                FloatingPipScreen(
                    voiceEngine = voiceEngine,
                    onDrag = { dx, dy ->
                        layoutParams?.let { lp ->
                            val wmLp = lp as WindowManager.LayoutParams
                            wmLp.x += dx.toInt()
                            wmLp.y += dy.toInt()
                            composeView?.let { view ->
                                windowManager?.updateViewLayout(view, wmLp)
                            }
                        }
                    },
                    onResize = { zoomMultiplier ->
                        layoutParams?.let { lp ->
                            val wmLp = lp as WindowManager.LayoutParams
                            val densityMultiplier = resources.displayMetrics.density
                            val minSize = (160 * densityMultiplier).toInt() // Increased minimum size
                            val maxSize = (350 * densityMultiplier).toInt()
                            val newWidth = (wmLp.width * zoomMultiplier).toInt().coerceIn(minSize, maxSize)
                            wmLp.width = newWidth
                            wmLp.height = newWidth
                            composeView?.let { view ->
                                windowManager?.updateViewLayout(view, wmLp)
                            }
                        }
                    },
                    onClose = { hideOverlay() },
                    onTap = {
                        val intent = Intent(this@VoiceService, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(intent)
                        hideOverlay()
                    }
                )
            }
        }

        overlayLifecycleOwner = MyLifecycleOwner()
        composeView?.setViewTreeLifecycleOwner(overlayLifecycleOwner)
        composeView?.setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)

        try {
            windowManager?.addView(composeView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
            hideOverlay()
        }
    }

    private fun hideOverlay() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            scope.launch(Dispatchers.Main) { hideOverlay() }
            return
        }

        if (composeView != null) {
            try {
                windowManager?.removeView(composeView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayLifecycleOwner?.destroy()
            overlayLifecycleOwner = null
            composeView = null
        }
    }

    private fun startVideoTrackCollection() {
        if (videoTrackCollectionJob != null) return
        videoTrackCollectionJob = scope.launch {
            // Combine watching all three video track sources so we react to any change
            kotlinx.coroutines.flow.combine(
                SharkordClient.voiceEngine.videoEngine.remoteVideoTracks,
                SharkordClient.voiceEngine.videoEngine.localVideoTrackFlow,
                SharkordClient.voiceEngine.videoEngine.localScreenTrackFlow
            ) { remote, local, screen ->
                remote.isNotEmpty() || local != null || screen != null
            }.collect { hasVideo ->
                updateOverlayVisibility(hasVideo)
            }
        }
    }

    private fun updateOverlayVisibility(hasVideo: Boolean) {
        if (isAppVisible) return

        if (hasVideo) {
            if (!SharkordClient.session.enableFloatingPip) return
            if (!android.provider.Settings.canDrawOverlays(this)) return
            scope.launch(Dispatchers.Main) { showOverlay() }
        } else {
            // All cameras/screens turned off — dismiss the floating overlay
            hideOverlay()
        }
    }

    private fun checkAndShowOverlayIfNeeded() {
        if (isAppVisible) return
        if (!SharkordClient.session.enableFloatingPip) return
        if (!android.provider.Settings.canDrawOverlays(this)) return

        val hasVideo = SharkordClient.voiceEngine.videoEngine.remoteVideoTracks.value.isNotEmpty() ||
                SharkordClient.voiceEngine.videoEngine.localVideoTrackFlow.value != null ||
                SharkordClient.voiceEngine.videoEngine.localScreenTrackFlow.value != null

        if (hasVideo) {
            scope.launch(Dispatchers.Main) { showOverlay() }
        }
    }
}
