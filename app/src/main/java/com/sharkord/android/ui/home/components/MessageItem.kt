package com.sharkord.android.ui.home.components

import android.media.MediaPlayer
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import com.sharkord.android.data.model.Message
import com.sharkord.android.data.model.Role
import com.sharkord.android.data.model.User
import com.sharkord.android.data.network.ConnectionState
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.ui.components.rememberAsyncImagePainter
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import kotlinx.coroutines.delay

/**
 * Renders a single chat message in the message list.
 *
 * Groups consecutive messages from the same author (compact mode) to mirror
 * Discord's visual style — only the first message in a run shows the avatar + name.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MessageItem(
    message: Message,
    previousMessage: Message?,
    users: List<User>,
    roles: List<Role>,
    ownUserId: Int,
    modifier: Modifier = Modifier,
    onLongClick: (Message) -> Unit = {},
    onReplyClick: (Int) -> Unit = {},
    onReactionClick: (Int, String) -> Unit = { _, _ -> }
) {
    val bgColor = ChatColors.BgColor
    val textPrimary = ChatColors.TextPrimary
    val textSecondary = ChatColors.TextSecondary
    val textMuted = ChatColors.TextMuted

    val author = users.find { it.id == message.userId }

    // Show the avatar + author header only when this is the first message in a consecutive run
    val showHeader = previousMessage == null
        || previousMessage.userId != message.userId
        || (message.createdAt - previousMessage.createdAt) > TimeUnit.MINUTES.toMillis(5)
        || message.replyTo != null

    // Resolve author's top-role color
    val nameColor = remember(author, roles) {
        val authorRoleIds = author?.roleIds ?: emptyList()
        val topRole = roles
            .filter { it.id in authorRoleIds }
            .maxByOrNull { it.position }
        val hex = topRole?.color?.takeIf { it.isNotBlank() && it != "#99AAB5" }
        if (hex != null) {
            runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(textPrimary)
        } else {
            textPrimary
        }
    }

    // Strip HTML tags from content for plain-text rendering
    val plainContent = remember(message.content) {
        HtmlCompat.fromHtml(message.content, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
    }

    val timestamp by produceState(initialValue = formatTimestamp(message.createdAt), message.createdAt) {
        while (true) {
            delay(30_000)
            value = formatTimestamp(message.createdAt)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onLongClick = { onLongClick(message) },
                onClick = {}
            )
    ) {
        // If it is a reply, render the curved connection line and original message preview
        if (message.replyTo != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onReplyClick(message.replyTo.id) }
                    .padding(start = 12.dp, top = 6.dp, bottom = 2.dp)
            ) {
                // Curved connecting thread
                Canvas(
                    modifier = Modifier
                        .width(36.dp)
                        .height(20.dp)
                ) {
                    val startX = 16.dp.toPx()
                    val path = Path().apply {
                        moveTo(size.width, size.height / 2f)
                        quadraticTo(startX, size.height / 2f, startX, size.height)
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFF4F545C),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Resolve target author
                val replyAuthor = users.find { it.id == message.replyTo.userId }
                val replyAvatarUrl = replyAuthor?.avatar?.name?.let { name ->
                    "${SharkordClient.currentServerUrl}/public/$name"
                }
                val replyAvatarPainter = rememberAsyncImagePainter(replyAvatarUrl)

                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3A3A3A)),
                    contentAlignment = Alignment.Center
                ) {
                    if (replyAvatarPainter != null) {
                        Image(
                            painter = replyAvatarPainter,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = (replyAuthor?.name ?: "?").take(1).uppercase(),
                            color = textPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = replyAuthor?.name ?: "Unknown",
                    color = textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(8.dp))

                val replyPlainContent = remember(message.replyTo.content) {
                    val raw = message.replyTo.content ?: ""
                    HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                }

                Text(
                    text = replyPlainContent,
                    color = textMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = if (showHeader && message.replyTo == null) 12.dp else 2.dp,
                    bottom = 0.dp
                ),
            verticalAlignment = Alignment.Top
        ) {
            // Left column: avatar or spacer
            if (showHeader) {
                val avatarUrl = author?.avatar?.name?.let { name ->
                    "${SharkordClient.currentServerUrl}/public/$name"
                }
                val avatarPainter = rememberAsyncImagePainter(avatarUrl)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3A3A3A)),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarPainter != null) {
                        Image(
                            painter = avatarPainter,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Initial letter fallback
                        Text(
                            text = (author?.name ?: "?").take(1).uppercase(),
                            color = textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // Compact mode — reserve the same width as the avatar column so text aligns
                Spacer(modifier = Modifier.width(40.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right column: header (name + time) and content
            Column(modifier = Modifier.weight(1f)) {
                if (showHeader) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = author?.name ?: "Unknown",
                            color = nameColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = timestamp,
                            color = textMuted,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }

                if (plainContent.isNotEmpty()) {
                    val isEmojiOnly = remember(plainContent) { EmojiMapper.isEmojiOnly(plainContent) }
                    val fontSize = if (isEmojiOnly) 36.sp else 15.sp
                    val lineHeight = if (isEmojiOnly) 44.sp else 21.sp
                    Text(
                        text = plainContent,
                        color = textSecondary,
                        fontSize = fontSize,
                        lineHeight = lineHeight
                    )
                }

                // Edited indicator
                if (message.editedAt != null) {
                    Text(
                        text = "(edited)",
                        color = textMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Media & File attachments: display photos/audios inline or generic chips
                if (message.files.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    message.files.forEach { file ->
                        val extension = file.originalName?.substringAfterLast('.', "")?.lowercase() ?: ""
                        val mimeType = file.mimeType?.lowercase() ?: ""
                        val isImage = mimeType.startsWith("image/") || extension in listOf("png", "jpg", "jpeg", "webp", "gif")
                        val isAudio = mimeType.startsWith("audio/") || extension in listOf("m4a", "mp3", "wav", "aac", "ogg")

                        when {
                            isImage -> {
                                val imageUrl = "${SharkordClient.currentServerUrl}/public/${file.name}"
                                val painter = rememberAsyncImagePainter(imageUrl)
                                if (painter != null) {
                                    Image(
                                        painter = painter,
                                        contentDescription = file.originalName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .fillMaxWidth(0.85f)
                                            .heightIn(max = 200.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }
                            }
                            isAudio -> {
                                val audioUrl = "${SharkordClient.currentServerUrl}/public/${file.name}"
                                AudioPlayer(
                                    audioUrl = audioUrl,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            else -> {
                                // Generic filechip
                                Text(
                                    text = "📎 ${file.originalName ?: file.name}",
                                    color = Color(0xFF5B9BD5),
                                    fontSize = 13.sp,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .background(
                                            Color(0xFF2B2B2B),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Reactions Wrap Grid
                if (message.reactions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))

                    val grouped = remember(message.reactions) {
                        message.reactions.groupBy { it.file?.name ?: it.emoji ?: "" }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        grouped.forEach { (key, groupList) ->
                            key(key) {
                                val firstReaction = groupList.first()
                                val count = groupList.size
                                val hasReacted = groupList.any { it.userId == ownUserId }
                                val emojiCode = firstReaction.emoji ?: ""

                                val chipBg = if (hasReacted) ChatColors.AccentColor.copy(alpha = 0.15f) else ChatColors.CardColor
                                val chipBorder = if (hasReacted) ChatColors.AccentColor.copy(alpha = 0.8f) else Color.Transparent

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(chipBg)
                                        .border(1.dp, chipBorder, RoundedCornerShape(8.dp))
                                        .clickable { onReactionClick(message.id, emojiCode) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (firstReaction.file != null) {
                                        val customEmojiUrl = "${SharkordClient.currentServerUrl}/public/${firstReaction.file.name}"
                                        val emojiPainter = rememberAsyncImagePainter(customEmojiUrl)
                                        if (emojiPainter != null) {
                                            Image(
                                                painter = emojiPainter,
                                                contentDescription = emojiCode,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else {
                                            Text(
                                                text = EmojiMapper.map(emojiCode),
                                                color = Color.White,
                                                fontSize = 12.sp
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = EmojiMapper.map(emojiCode),
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = count.toString(),
                                        color = if (hasReacted) ChatColors.AccentColor else textSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom voice note / audio playback card interface.
 */
@Composable
fun AudioPlayer(audioUrl: String, modifier: Modifier = Modifier) {
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableIntStateOf(0) }
    var currentPosition by remember { mutableIntStateOf(0) }
    var isPrepared by remember { mutableStateOf(false) }

    DisposableEffect(audioUrl) {
        val player = MediaPlayer().apply {
            try {
                setDataSource(audioUrl)
                setOnPreparedListener {
                    duration = it.duration
                    isPrepared = true
                }
                setOnCompletionListener {
                    isPlaying = false
                    currentPosition = 0
                }
                prepareAsync()
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Error preparing player", e)
            }
        }
        mediaPlayer = player

        onDispose {
            player.release()
            mediaPlayer = null
        }
    }

    // Playback progress loop
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying && mediaPlayer != null) {
                currentPosition = mediaPlayer?.currentPosition ?: 0
                delay(200)
            }
        }
    }

    val playPauseAction = {
        val player = mediaPlayer
        if (player != null && isPrepared) {
            if (isPlaying) {
                player.pause()
                isPlaying = false
            } else {
                player.start()
                isPlaying = true
            }
        }
    }

    val formatTime = { ms: Int ->
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        String.format("%d:%02d", minutes, seconds)
    }

    Row(
        modifier = modifier
            .fillMaxWidth(0.9f)
            .background(Color(0xFF242424), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = playPauseAction, enabled = isPrepared) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color(0xFF5B9BD5),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Slider(
                value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                onValueChange = { ratio ->
                    val player = mediaPlayer
                    if (player != null && isPrepared) {
                        val newPos = (ratio * duration).toInt()
                        player.seekTo(newPos)
                        currentPosition = newPos
                    }
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF5B9BD5),
                    activeTrackColor = Color(0xFF5B9BD5),
                    inactiveTrackColor = Color(0xFF4A4A4A)
                ),
                modifier = Modifier.fillMaxWidth().height(18.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(currentPosition),
                    color = Color(0xFF9E9E9E),
                    fontSize = 11.sp
                )
                Text(
                    text = formatTime(duration),
                    color = Color(0xFF9E9E9E),
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────

private fun formatTimestamp(epochMs: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - epochMs
    return when {
        diffMs < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diffMs < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diffMs)}m ago"
        diffMs < TimeUnit.HOURS.toMillis(12) -> {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMs))
        }
        diffMs < TimeUnit.DAYS.toMillis(7) -> {
            SimpleDateFormat("EEE h:mm a", Locale.getDefault()).format(Date(epochMs))
        }
        else -> SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(epochMs))
    }
}
