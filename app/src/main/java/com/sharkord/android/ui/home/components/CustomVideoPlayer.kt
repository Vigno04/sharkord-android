package com.sharkord.android.ui.home.components

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@UnstableApi
object SharedExoPlayerManager {
    private val players = mutableMapOf<String, ExoPlayer>()
    private val refCounts = mutableMapOf<String, Int>()

    fun getPlayer(context: android.content.Context, url: String): ExoPlayer {
        refCounts[url] = (refCounts[url] ?: 0) + 1
        return players.getOrPut(url) {
            ExoPlayer.Builder(context.applicationContext).build().apply {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build()
                setAudioAttributes(audioAttributes, true)
                setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                prepare()
            }
        }
    }

    fun releasePlayer(url: String) {
        val count = (refCounts[url] ?: 0) - 1
        if (count <= 0) {
            players[url]?.release()
            players.remove(url)
            refCounts.remove(url)
        } else {
            refCounts[url] = count
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun CustomVideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    isOverlayActive: Boolean = false,
    onFullscreenClick: (() -> Unit)? = null,
    onReturnToThumbnail: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(autoPlay) }
    var isPrepared by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }

    val exoPlayer = remember(videoUrl) {
        SharedExoPlayerManager.getPlayer(context, videoUrl).apply {
            if (autoPlay && playbackState == Player.STATE_IDLE) {
                playWhenReady = true
            } else if (autoPlay) {
                playWhenReady = true
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    isPrepared = true
                    duration = exoPlayer.duration
                } else if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                    showControls = true
                    exoPlayer.seekTo(0)
                    exoPlayer.playWhenReady = false
                }
            }
            override fun onIsPlayingChanged(isPlayingState: Boolean) {
                isPlaying = isPlayingState
            }
        }
        exoPlayer.addListener(listener)
        // initialize state
        isPlaying = exoPlayer.isPlaying
        isPrepared = exoPlayer.playbackState == Player.STATE_READY
        duration = exoPlayer.duration.coerceAtLeast(0)
        
        onDispose {
            exoPlayer.removeListener(listener)
            SharedExoPlayerManager.releasePlayer(videoUrl)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isPlaying, showControls) {
        if (isPlaying && showControls) {
            delay(3000)
            showControls = false
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            delay(200)
        }
    }

    LaunchedEffect(isPlaying, currentPosition) {
        if (!isPlaying) {
            delay(60_000) // 1 minute timeout
            onReturnToThumbnail?.invoke()
        }
    }

    val playPauseToggle: () -> Unit = {
        if (isPlaying) {
            exoPlayer.pause()
            showControls = true
        } else {
            exoPlayer.play()
        }
    }

    val formatTime = { ms: Long ->
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        String.format("%d:%02d", minutes, seconds)
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable {
                showControls = !showControls
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = if (isOverlayActive) null else exoPlayer
                }
            },
            update = { view ->
                val targetPlayer = if (isOverlayActive) null else exoPlayer
                if (view.player != targetPlayer) {
                    view.player = targetPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // overlay Controls
        AnimatedVisibility(
            visible = showControls || !isPlaying,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable {
                            playPauseToggle()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                if (isPrepared) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onValueChange = { ratio ->
                                val newPos = (ratio * duration).toLong()
                                currentPosition = newPos
                                exoPlayer.seekTo(newPos)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = ChatColors.AccentColor,
                                activeTrackColor = ChatColors.AccentColor,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatTime(duration),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        if (onFullscreenClick != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = onFullscreenClick,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (isOverlayActive) Icons.Default.Close else Icons.Default.Fullscreen,
                                    contentDescription = if (isOverlayActive) "Exit Fullscreen" else "Fullscreen",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
