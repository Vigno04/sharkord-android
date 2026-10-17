package com.sharkord.android.ui.home.components

import com.sharkord.android.ui.theme.SharkordTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
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
    isEnlarged: Boolean = false,
    modifier: Modifier = Modifier,
    onEnlargeClick: ((String) -> Unit)? = null,
    onFullscreenClick: ((VideoTrack) -> Unit)? = null
) {
    val voiceUser = displayItem.voiceUser
    val isScreenShare = displayItem is VoiceDisplayItem.ScreenShare
    
    var isZoomedOut by remember { mutableStateOf(isScreenShare) }
    
    val defaultBorderWidth = 1.dp
    val defaultBorderColor = colors.foregroundText.copy(alpha = 0.1f)
    val borderWidth by animateDpAsState(targetValue = if (displayItem.isSpeaking) 3.dp else defaultBorderWidth)
    val borderColor = if (displayItem.isSpeaking) Color.Green else defaultBorderColor
    
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

    Box(
        modifier = modifier
            .height(itemHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardColor, RoundedCornerShape(16.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
            .clickable(enabled = !isScreenShare) {
                isZoomedOut = !isZoomedOut
            },
        contentAlignment = Alignment.Center
    ) {
        val avatarUrl = com.sharkord.android.data.network.SharkordClient.getFileUrl(voiceUser.user.avatar)
        val avatarPainter = rememberAsyncImagePainter(avatarUrl, fallbackResourceId = null)

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
        
        if (isConnected) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onEnlargeClick != null) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable { onEnlargeClick(displayItem.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isEnlarged) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                            contentDescription = if (isEnlarged) "Shrink" else "Enlarge",
                            tint = SharkordTheme.colors.foregroundText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                if (videoTrack != null && onFullscreenClick != null) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable { onFullscreenClick(videoTrack) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = SharkordTheme.colors.foregroundText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        val allReactions = voiceUser.reactions.ifEmpty {
            voiceUser.reactionEmoji?.let { listOf(VoiceReaction(1L, it)) } ?: emptyList()
        }

        if (allReactions.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                allReactions.forEach { reaction ->
                    key(reaction.id) {
                        VoiceReactionItem(reaction = reaction)
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceReactionItem(
    reaction: VoiceReaction,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density

    // Random styling seeded by reaction id, matching server reference
    val (driftDp, maxRotation, baseScale) = remember(reaction.id) {
        val rand = kotlin.random.Random(reaction.id)
        val signX = if (rand.nextBoolean()) 1f else -1f
        val drift = (12f + rand.nextFloat() * (46f - 12f)) * signX
        val signRot = if (rand.nextBoolean()) 1f else -1f
        val rot = (5f + rand.nextFloat() * (14f - 5f)) * signRot
        val scale = 0.85f + rand.nextFloat() * 0.3f
        Triple(drift, rot, scale)
    }

    val animProgress = remember(reaction.id) { Animatable(0f) }
    LaunchedEffect(reaction.id) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 2600,
                easing = LinearEasing
            )
        )
    }

    val progress = animProgress.value

    // Opacity: 0% -> 0, 12% -> 1, 65% -> 1, 100% -> 0
    val alpha = when {
        progress < 0.12f -> (progress / 0.12f).coerceIn(0f, 1f)
        progress <= 0.65f -> 1f
        else -> ((1f - progress) / (1f - 0.65f)).coerceIn(0f, 1f)
    }

    // Rise animation easing: cubic-bezier(0.19, 0.72, 0.34, 1)
    val riseEasing = remember { CubicBezierEasing(0.19f, 0.72f, 0.34f, 1f) }
    val riseT = riseEasing.transform(progress)

    // Translate Y: starts at +15dp, rises to -140dp
    val translateY = 15f + riseT * (-140f - 15f)
    val translateX = driftDp * progress

    // Pop animation: cubic-bezier(0.34, 1.56, 0.64, 1)
    val popEasing = remember { CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f) }
    val scale = when {
        progress < 0.14f -> {
            val t = popEasing.transform((progress / 0.14f).coerceIn(0f, 1f))
            0.2f + t * (baseScale - 0.2f)
        }
        progress <= 0.78f -> baseScale
        else -> {
            val t = ((progress - 0.78f) / (1f - 0.78f)).coerceIn(0f, 1f)
            baseScale - t * (baseScale * 0.3f)
        }
    }

    // Rotation wobble
    val rotation = when {
        progress < 0.14f -> {
            (progress / 0.14f) * maxRotation
        }
        progress < 0.48f -> {
            val t = (progress - 0.14f) / (0.48f - 0.14f)
            maxRotation + t * (-maxRotation - maxRotation)
        }
        progress < 0.78f -> {
            val t = (progress - 0.48f) / (0.78f - 0.48f)
            -maxRotation + t * (maxRotation - (-maxRotation))
        }
        else -> {
            val t = (progress - 0.78f) / (1f - 0.78f)
            maxRotation - t * maxRotation
        }
    }

    val customEmoji = remember(reaction.emoji) {
        val trimmed = reaction.emoji.trim(':')
        SharkordClient.webSocket.serverData.replayCache.firstOrNull()?.emojis?.find { it.name == trimmed }
    }

    val emojiText = remember(reaction.emoji) {
        when (reaction.emoji.trim(':')) {
            "sparkling_heart" -> "💖"
            "+1", "thumbsup" -> "👍"
            "-1", "thumbsdown" -> "👎"
            "tada" -> "🎉"
            "clap" -> "👏"
            "smile" -> "😄"
            "open_mouth" -> "😮"
            "cry" -> "😢"
            "thinking" -> "🤔"
            "fire" -> "🔥"
            "heart" -> "❤️"
            else -> reaction.emoji
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                this.translationX = translateX * density
                this.translationY = translateY * density
                this.scaleX = scale
                this.scaleY = scale
                this.rotationZ = rotation
                this.alpha = alpha
            },
        contentAlignment = Alignment.Center
    ) {
        if (customEmoji?.file != null) {
            val emojiUrl = SharkordClient.getFileUrl(customEmoji.file)
            val painter = rememberAsyncImagePainter(emojiUrl)
            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = customEmoji.name,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Text(
                    text = emojiText,
                    fontSize = 44.sp,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.5f),
                            offset = Offset(2f, 4f),
                            blurRadius = 8f
                        )
                    )
                )
            }
        } else {
            Text(
                text = emojiText,
                fontSize = 44.sp,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.5f),
                        offset = Offset(2f, 4f),
                        blurRadius = 8f
                    )
                )
            )
        }
    }
}
