package com.sharkord.android.ui.pip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.sharkord.android.data.network.VoiceEngine
import com.sharkord.android.ui.home.components.WebRtcVideoRenderer



@Composable
fun FloatingPipScreen(
    voiceEngine: VoiceEngine,
    onDrag: (Float, Float) -> Unit,
    onResize: (Float) -> Unit,
    onClose: () -> Unit,
    onTap: () -> Unit
) {
    val remoteVideoTracks by voiceEngine.videoEngine.remoteVideoTracks.collectAsState()
    val localVideoTrack by voiceEngine.videoEngine.localVideoTrackFlow.collectAsState()
    val localScreenTrack by voiceEngine.videoEngine.localScreenTrackFlow.collectAsState()
    val audioLevels by voiceEngine.audioLevels.collectAsState(initial = emptyMap())

    val serverData by com.sharkord.android.data.network.SharkordClient.webSocket.serverData.collectAsState(initial = null)
    val ownUserId = serverData?.ownUserId

    // Sticky speaker selection: stay on current speaker for 2s after they stop talking,
    // only switch when a new speaker is actively talking
    val stickyTrack = remember { androidx.compose.runtime.mutableStateOf<org.webrtc.VideoTrack?>(null) }
    androidx.compose.runtime.LaunchedEffect(remoteVideoTracks, audioLevels, localScreenTrack, localVideoTrack, ownUserId) {
        val activeSpeakers = audioLevels.filter { it.value > 0.02f }.keys.filter { it != "local" && it != ownUserId.toString() }
        val currentTrack = stickyTrack.value

        // Find loudest active speaker who has video
        val loudestSpeakerId = activeSpeakers
            .filter { speakerId -> remoteVideoTracks.keys.any { it.startsWith("$speakerId:") } }
            .maxByOrNull { audioLevels[it] ?: 0f }

        // For a given speaker, always prefer camera over screen share
        val speakerTrack = loudestSpeakerId?.let { speakerId ->
            remoteVideoTracks["$speakerId:video"]
                ?: remoteVideoTracks["$speakerId:external_video"]
                ?: remoteVideoTracks["$speakerId:screen"]
        }

        val genuineRemoteTracks = remoteVideoTracks.entries.filter { ownUserId == null || !it.key.startsWith("$ownUserId:") }

        val newTrack = when {
            speakerTrack != null && speakerTrack != currentTrack -> {
                // Someone new is actively talking — switch immediately
                speakerTrack
            }
            currentTrack != null && (genuineRemoteTracks.any { it.value == currentTrack } || currentTrack == localVideoTrack || currentTrack == localScreenTrack) && genuineRemoteTracks.isEmpty() -> {
                // Current local track still valid and NO genuine remote tracks available — keep it
                currentTrack
            }
            currentTrack != null && genuineRemoteTracks.any { it.value == currentTrack } -> {
                // Current genuine remote track still valid — keep it
                currentTrack
            }
            else -> {
                // Pick first available genuine remote camera, preferring video over screen
                genuineRemoteTracks
                    .sortedBy { if (it.key.endsWith(":video")) 0 else if (it.key.endsWith(":screen")) 2 else 1 }
                    .firstOrNull()?.value
                    ?: localScreenTrack ?: localVideoTrack
            }
        }
        stickyTrack.value = newTrack
    }
    
    val genuineRemoteTracks = remoteVideoTracks.entries.filter { ownUserId == null || !it.key.startsWith("$ownUserId:") }
    
    val trackToRender = stickyTrack.value
        ?: genuineRemoteTracks
            .sortedBy { if (it.key.endsWith(":video")) 0 else if (it.key.endsWith(":screen")) 2 else 1 }
            .firstOrNull()?.value
        ?: localScreenTrack
        ?: localVideoTrack

    // Determine who is speaking for the currently rendered track
    val isSpeaking by remember(audioLevels, trackToRender, remoteVideoTracks, localScreenTrack, localVideoTrack) {
        derivedStateOf {
            val activeSpeakers = audioLevels.filter { it.value > 0.02f }.keys
            val track = trackToRender
            when {
                track == null -> false
                track == localScreenTrack || track == localVideoTrack -> activeSpeakers.contains("local")
                else -> {
                    val remoteId = remoteVideoTracks.entries.find { it.value == track }?.key?.substringBefore(":")
                    remoteId != null && activeSpeakers.contains(remoteId)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    onDrag(pan.x, pan.y)
                    if (zoom != 1f) onResize(zoom)
                }
            }
    ) {
        // Video fills the entire window — no layout shifts.
        // The window itself (ComposeView in VoiceService) has clipToOutline = true
        // with a 16dp radius, which is the ONLY reliable way to round-clip a SurfaceView
        // (SurfaceView renders in a separate hardware surface that ignores Compose/View clipping).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val track = trackToRender
            if (track != null) {
                // key() forces the entire renderer to be destroyed + recreated when the track
                // changes, preventing the stuck-frame bug where the old surface doesn't update
                androidx.compose.runtime.key(track) {
                    WebRtcVideoRenderer(
                        videoTrack = track,
                        eglBaseContext = voiceEngine.eglBaseContext,
                        modifier = Modifier.fillMaxSize(),
                        setZOrderMediaOverlay = false,
                        showStats = false
                    )
                }
            }

            // Speaking border: drawn as an overlay Box so it never affects layout/size.
            // Uses animateColorAsState for a smooth fade in/out.
            val borderColor = if (isSpeaking) Color(0xFF4CAF50) else Color.Transparent
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, borderColor)
            )
        }

        // Action buttons — top right, drawn ABOVE SurfaceView via setZOrderMediaOverlay
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { onTap() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Open Full Screen",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close PiP",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
