package com.sharkord.android.ui.home.components

import com.sharkord.android.ui.theme.SharkordTheme
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.ui.components.rememberAsyncImagePainter
import com.sharkord.android.ui.theme.SharkordColors
import org.webrtc.EglBase
import org.webrtc.VideoTrack

sealed interface VoiceDisplayItem {
    val voiceUser: VoiceUserDisplay
    val id: String
    val isSpeaking: Boolean

    data class User(
        override val voiceUser: VoiceUserDisplay
    ) : VoiceDisplayItem {
        override val id = voiceUser.user.id.toString()
        override val isSpeaking = voiceUser.isSpeaking
    }

    data class ScreenShare(
        override val voiceUser: VoiceUserDisplay,
        val track: VideoTrack?
    ) : VoiceDisplayItem {
        override val id = "${voiceUser.user.id}:screen"
        override val isSpeaking = false
    }
}

@Composable
fun VoiceGridItem(
    displayItem: VoiceDisplayItem,
    itemHeight: androidx.compose.ui.unit.Dp,
    ownUserId: Int?,
    localVideoTrack: VideoTrack?,
    remoteVideoTracks: Map<String, VideoTrack>,
    eglBaseContext: EglBase.Context,
    colors: SharkordColors,
    isConnected: Boolean,
    modifier: Modifier = Modifier,
    onFullscreenClick: ((VideoTrack) -> Unit)? = null
) {
    val voiceUser = displayItem.voiceUser
    val isScreenShare = displayItem is VoiceDisplayItem.ScreenShare
    
    var isZoomedOut by remember { mutableStateOf(isScreenShare) }
    
    val defaultBorderWidth = 1.dp
    val defaultBorderColor = colors.foregroundText.copy(alpha = 0.1f)
    val borderWidth by animateDpAsState(targetValue = if (displayItem.isSpeaking) 3.dp else defaultBorderWidth)
    val borderColor = if (displayItem.isSpeaking) Color.Green else defaultBorderColor
    
    Box(
        modifier = modifier
            .height(itemHeight)
            .background(colors.cardColor, RoundedCornerShape(16.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
            .clickable(enabled = !isScreenShare) { isZoomedOut = !isZoomedOut },
        contentAlignment = Alignment.Center
    ) {
        val avatarUrl = voiceUser.user.avatar?.name?.let { "${SharkordClient.currentServerUrl}/public/$it" }
        val avatarPainter = rememberAsyncImagePainter(avatarUrl, fallbackResourceId = null)

        val hasVideo = when (displayItem) {
            is VoiceDisplayItem.User -> voiceUser.state.webcamEnabled
            is VoiceDisplayItem.ScreenShare -> true
        }
        val videoTrack = when (displayItem) {
            is VoiceDisplayItem.User -> {
                if (ownUserId != null && voiceUser.user.id == ownUserId) {
                    localVideoTrack
                } else {
                    remoteVideoTracks["${voiceUser.user.id}:video"]
                }
            }
            is VoiceDisplayItem.ScreenShare -> displayItem.track
        }

        if (hasVideo && videoTrack == null) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (avatarPainter != null) {
                    Image(
                        painter = avatarPainter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().blur(16.dp)
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(SharkordTheme.colors.cardColor))
                }
                
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
                
                Text(
                    text = if (isScreenShare) { if (isConnected) "Loading screen share..." else "Enter channel to\nsee screen share" } else { "Enter channel to\nsee user's camera" },
                    color = SharkordTheme.colors.foregroundText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 8.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(SharkordTheme.colors.cardColor),
                contentAlignment = Alignment.Center
            ) {
                if (avatarPainter != null) {
                    Image(
                        painter = avatarPainter,
                        contentDescription = "User Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = voiceUser.user.name.take(1).uppercase(),
                        color = SharkordTheme.colors.foregroundText,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (videoTrack != null) {
            WebRtcVideoRenderer(
                videoTrack = videoTrack,
                eglBaseContext = eglBaseContext,
                isZoomedOut = isZoomedOut || isScreenShare,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // user Name Tag
        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            val canFitBoth = maxWidth > 100.dp
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isScreenShare) "${voiceUser.user.name}'s Screen" else voiceUser.user.name,
                    color = SharkordTheme.colors.foregroundText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                
                val isDeafened = voiceUser.state.soundMuted
                val isMuted = voiceUser.state.micMuted
                
                if (isDeafened) {
                    Icon(
                        Icons.Default.HeadsetOff,
                        contentDescription = "Deafened",
                        tint = Color.Red,
                        modifier = Modifier.size(14.dp).padding(start = 4.dp)
                    )
                } else if (isMuted) {
                    Icon(
                        Icons.Default.MicOff,
                        contentDescription = "Muted",
                        tint = Color.Red,
                        modifier = Modifier.size(14.dp).padding(start = 4.dp)
                    )
                }
            }
        }
        
        if (isScreenShare && videoTrack != null && onFullscreenClick != null) {
            IconButton(
                onClick = { onFullscreenClick(videoTrack) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                Icon(
                    Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = SharkordTheme.colors.foregroundText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
