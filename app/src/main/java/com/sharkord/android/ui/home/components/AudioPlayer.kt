package com.sharkord.android.ui.home.components

import com.sharkord.android.ui.theme.SharkordTheme
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

object AudioMetadataCache {
    val durationCache = java.util.concurrent.ConcurrentHashMap<String, Long>()
}

// custom voice note / audio playback card interface
@Composable
fun AudioPlayer(audioUrl: String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var exoPlayer by remember { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var isPrepared by remember { mutableStateOf(false) }

    DisposableEffect(audioUrl) {
        onDispose {
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    LaunchedEffect(audioUrl) {
        if (duration == 0L) {
            val cached = AudioMetadataCache.durationCache[audioUrl]
            if (cached != null) {
                duration = cached
            } else {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    var retriever: android.media.MediaMetadataRetriever? = null
                    try {
                        retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(audioUrl, HashMap<String, String>())
                        val timeString = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val parsed = timeString?.toLongOrNull()
                        if (parsed != null && parsed > 0) {
                            duration = parsed
                            AudioMetadataCache.durationCache[audioUrl] = parsed
                        }
                    } catch (e: Exception) {
                        Log.d("AudioPlayer", "Could not retrieve metadata duration for $audioUrl")
                    } finally {
                        try {
                            retriever?.release()
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    val playPauseAction = {
        if (exoPlayer == null) {
            val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
                override fun buildVideoRenderers(
                    context: android.content.Context,
                    extensionRendererMode: Int,
                    mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
                    enableDecoderFallback: Boolean,
                    eventHandler: android.os.Handler,
                    eventListener: androidx.media3.exoplayer.video.VideoRendererEventListener,
                    allowedVideoJoiningTimeMs: Long,
                    out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>
                ) {
                    // Do not build video renderers for audio-only player to avoid codec query errors
                }
            }
            val player = androidx.media3.exoplayer.ExoPlayer.Builder(context, renderersFactory).build().apply {
                val attributes = androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_VOICE_COMMUNICATION)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build()
                setAudioAttributes(attributes, false)
                setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.parse(audioUrl)))
                
                addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == androidx.media3.common.Player.STATE_READY) {
                            val d = this@apply.duration.coerceAtLeast(0L)
                            if (d > 0) duration = d
                            isPrepared = true
                        } else if (state == androidx.media3.common.Player.STATE_ENDED) {
                            isPlaying = false
                            currentPosition = 0L
                            seekTo(0)
                            playWhenReady = false
                        }
                    }
                    override fun onIsPlayingChanged(isPlayingState: Boolean) {
                        isPlaying = isPlayingState
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e("AudioPlayer", "ExoPlayer error", error)
                        isPlaying = false
                    }
                })
                prepare()
                play()
            }
            exoPlayer = player
        } else {
            val player = exoPlayer
            if (player != null && (isPrepared || player.playbackState == androidx.media3.common.Player.STATE_READY)) {
                if (isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
            }
        }
    }

    // playback progress loop
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying && exoPlayer != null) {
                currentPosition = exoPlayer?.currentPosition ?: 0L
                delay(200)
            }
        }
    }

    val formatTime = { ms: Long ->
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        String.format("%d:%02d", minutes, seconds)
    }

    val sensorManager = remember { context.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager }
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    val powerManager = remember { context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager }
    val proximitySensor = remember { sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_PROXIMITY) }

    val wakeLock = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            powerManager.newWakeLock(android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "Sharkord:AudioPlayerProximity")
        } else {
            null
        }
    }

    var isNear by remember { mutableStateOf(false) }

    val onPausePlayback = rememberUpdatedState {
        val player = exoPlayer
        if (player != null && isPrepared && isPlaying) {
            player.pause()
        }
    }

    val sensorEventListener = remember {
        object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                if (event?.sensor?.type == android.hardware.Sensor.TYPE_PROXIMITY) {
                    val distance = event.values[0]
                    val maxRange = proximitySensor?.maximumRange ?: 5f
                    val near = distance < maxRange && distance < 5f
                    if (isNear != near) {
                        isNear = near
                        if (near) {
                            audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                val earpiece = audioManager.availableCommunicationDevices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                                if (earpiece != null) {
                                    audioManager.setCommunicationDevice(earpiece)
                                } else {
                                    audioManager.clearCommunicationDevice()
                                }
                            }
                            @Suppress("DEPRECATION")
                            audioManager.isSpeakerphoneOn = false
                        } else {
                            audioManager.mode = android.media.AudioManager.MODE_NORMAL
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                audioManager.clearCommunicationDevice()
                            }
                            @Suppress("DEPRECATION")
                            audioManager.isSpeakerphoneOn = true
                            onPausePlayback.value()
                        }
                    }
                }
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        }
    }

    if (isPlaying) {
        DisposableEffect(Unit) {
            proximitySensor?.let {
                sensorManager.registerListener(sensorEventListener, it, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
            }
            try {
                wakeLock?.takeIf { !it.isHeld }?.acquire()
            } catch (e: Exception) {
                Log.e("AudioPlayer", "WakeLock acquire failed", e)
            }

            onDispose {
                sensorManager.unregisterListener(sensorEventListener)
                try {
                    wakeLock?.takeIf { it.isHeld }?.release()
                } catch (e: Exception) {
                    Log.e("AudioPlayer", "WakeLock release failed", e)
                }
                if (isNear) {
                    audioManager.mode = android.media.AudioManager.MODE_NORMAL
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        audioManager.clearCommunicationDevice()
                    }
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = true
                    isNear = false
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth(0.9f)
            .background(SharkordTheme.colors.cardColor, RoundedCornerShape(12.dp))
            .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = playPauseAction, 
                enabled = exoPlayer == null || isPrepared,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = SharkordTheme.colors.accentColor,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Slider(
                value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                onValueChange = { ratio ->
                    val player = exoPlayer
                    if (player != null && isPrepared) {
                        val newPos = (ratio * duration).toLong()
                        player.seekTo(newPos)
                        currentPosition = newPos
                    }
                },
                colors = SliderDefaults.colors(
                    thumbColor = SharkordTheme.colors.accentColor,
                    activeTrackColor = SharkordTheme.colors.accentColor,
                    inactiveTrackColor = SharkordTheme.colors.primaryText.copy(alpha = 0.2f)
                ),
                modifier = Modifier.weight(1f).height(18.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, top = 4.dp), // 36dp (button) + 12dp (spacer) = 48dp
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(currentPosition),
                color = SharkordTheme.colors.primaryText.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
            Text(
                text = formatTime(duration),
                color = SharkordTheme.colors.primaryText.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }
    }
}
