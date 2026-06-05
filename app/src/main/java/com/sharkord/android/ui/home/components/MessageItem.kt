package com.sharkord.android.ui.home.components

import android.media.MediaPlayer
 import android.net.Uri
import android.util.Log
import android.widget.VideoView
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.sharkord.android.data.model.Message
import com.sharkord.android.data.model.Role
import com.sharkord.android.data.model.User
import com.sharkord.android.data.network.ConnectionState
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.ui.components.AsyncImageState
import com.sharkord.android.ui.components.rememberAsyncImagePainter
import com.sharkord.android.ui.components.rememberAsyncImageState
import com.sharkord.android.ui.components.rememberVideoThumbnailState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString

private val imgRegex = Regex("""<img\b[^>]*>""")
private val classRegex = Regex("""class=["']([^"']+)["']""")
private val altRegex = Regex("""alt=["']([^"']+)["']""")
private val srcRegex = Regex("""src=["']([^"']+)["']""")
private val tokenRegex = Regex("""\[\[EMOJI\|(.*?)\|(.*?)\]\]""")

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
    fullscreenMediaId: String? = null,
    onLongClick: (Message) -> Unit = {},
    onReplyClick: (Int) -> Unit = {},
    onReactionClick: (Int, String) -> Unit = { _, _ -> },
    onMediaClick: (com.sharkord.android.data.model.FileInfo) -> Unit = {},
    onMediaLongClick: (com.sharkord.android.data.model.FileInfo) -> Unit = {}
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

    // Pre-process HTML to preserve custom emojis as tokens before stripping tags
    val preProcessedHtml = remember(message.content) {
        val raw = message.content ?: ""
        imgRegex.replace(raw) { matchResult ->
            val imgTag = matchResult.value
            val clazz = classRegex.find(imgTag)?.groupValues?.get(1) ?: ""
            val alt = altRegex.find(imgTag)?.groupValues?.get(1) ?: ""
            val src = srcRegex.find(imgTag)?.groupValues?.get(1) ?: ""

            if (src.isNotEmpty()) {
                "[[EMOJI|$alt|$src]]"
            } else {
                "" // Drop other images
            }
        }
    }

    val textWithTokens = remember(preProcessedHtml) {
        HtmlCompat.fromHtml(preProcessedHtml, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
    }

    val inlineContentMap = remember(textWithTokens) { mutableMapOf<String, InlineTextContent>() }

    val annotatedContent = remember(textWithTokens) {
        buildAnnotatedString {
            var lastIndex = 0
            tokenRegex.findAll(textWithTokens).forEach { matchResult ->
                append(textWithTokens.substring(lastIndex, matchResult.range.first))

                val alt = matchResult.groupValues[1]
                val src = matchResult.groupValues[2]
                val id = "emoji_${matchResult.range.first}"
                
                val safeAlt = alt.ifEmpty { "emoji" }
                appendInlineContent(id, safeAlt)

                val isEmojiOnly = tokenRegex.replace(textWithTokens, "").trim().isEmpty()
                val size = if (isEmojiOnly) 48.sp else 24.sp

                inlineContentMap[id] = InlineTextContent(
                    Placeholder(width = size, height = size, placeholderVerticalAlign = PlaceholderVerticalAlign.Center)
                ) {
                    val fullUrl = if (src.startsWith("http")) src else "${SharkordClient.currentServerUrl}$src"
                    val painter = rememberAsyncImagePainter(fullUrl)
                    if (painter != null) {
                        Image(painter = painter, contentDescription = alt, modifier = Modifier.fillMaxSize())
                    } else {
                        Text(text = alt, fontSize = size * 0.5f)
                    }
                }

                lastIndex = matchResult.range.last + 1
            }
            append(textWithTokens.substring(lastIndex))
        }
    }

    val isEmojiOnly = remember(textWithTokens) {
        val withoutTokens = tokenRegex.replace(textWithTokens, "").trim()
        if (withoutTokens.isEmpty() && textWithTokens.isNotEmpty()) {
            true // Only custom emojis
        } else if (withoutTokens.isNotEmpty()) {
            EmojiMapper.isEmojiOnly(withoutTokens) // Check if remaining text is only unicode emojis
        } else {
            false
        }
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

                // Resolve target author for the reply preview
                val replyAuthor = users.find { it.id == message.replyTo.userId }

                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3A3A3A)),
                    contentAlignment = Alignment.Center
                ) {
                    val replyAvatarUrl = replyAuthor?.avatar?.name?.let { name ->
                        "${SharkordClient.currentServerUrl}/public/$name"
                    }
                    val replyAvatarState = rememberAsyncImageState(replyAvatarUrl)
                    when (replyAvatarState) {
                        is AsyncImageState.Success -> Image(
                            painter = replyAvatarState.painter,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        is AsyncImageState.Loading -> CircularProgressIndicator(
                            color = ChatColors.AccentColor,
                            modifier = Modifier.size(8.dp),
                            strokeWidth = 1.dp
                        )
                        else -> Text(
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
                    val replacedHtml = imgRegex.replace(raw) { matchResult ->
                        val alt = altRegex.find(matchResult.value)?.groupValues?.get(1) ?: ""
                        if (alt.isNotEmpty()) alt else ""
                    }
                    HtmlCompat.fromHtml(replacedHtml, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
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
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3A3A3A)),
                    contentAlignment = Alignment.Center
                ) {
                    val avatarUrl = author?.avatar?.name?.let { name ->
                        "${SharkordClient.currentServerUrl}/public/$name"
                    }
                    val avatarState = rememberAsyncImageState(avatarUrl)
                    when (avatarState) {
                        is AsyncImageState.Success -> Image(
                            painter = avatarState.painter,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        is AsyncImageState.Loading -> CircularProgressIndicator(
                            color = ChatColors.AccentColor,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        else -> {
                            // Empty (no avatar) or Failure — show initials
                            val initials = getInitials(author?.name ?: "?")
                            val bgColor = getUsernameColor(author?.name ?: "?")
                            Box(
                                modifier = Modifier.fillMaxSize().background(bgColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initials,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
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

                if (annotatedContent.isNotEmpty()) {
                    val fontSize = if (isEmojiOnly) 36.sp else 15.sp
                    val lineHeight = if (isEmojiOnly) 44.sp else 21.sp
                    Text(
                        text = annotatedContent,
                        color = textSecondary,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        inlineContent = inlineContentMap
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
                        val isVideo = mimeType.startsWith("video/") || extension in listOf("mp4", "mkv", "mov", "webm", "avi")
                        val isAudio = mimeType.startsWith("audio/") || extension in listOf("m4a", "mp3", "wav", "aac", "ogg")

                        when {
                            isImage && file.name != null -> {
                                val imageUrl = "${SharkordClient.currentServerUrl}/public/${file.name}"
                                val imageState = rememberAsyncImageState(imageUrl)
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .width(240.dp)
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF2B2B2B))
                                        .combinedClickable(
                                            onClick = { onMediaClick(file) },
                                            onLongClick = { onMediaLongClick(file) }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when (imageState) {
                                        is AsyncImageState.Success -> Image(
                                            painter = imageState.painter,
                                            contentDescription = file.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        is AsyncImageState.Loading -> CircularProgressIndicator(
                                            color = ChatColors.AccentColor,
                                            modifier = Modifier.size(28.dp),
                                            strokeWidth = 2.dp
                                        )
                                        else -> Text(
                                            text = "🖼️",
                                            fontSize = 24.sp
                                        )
                                    }
                                }
                            }
                            isVideo && file.name != null -> {
                                val videoUrl = "${SharkordClient.currentServerUrl}/public/${file.name}"
                                val thumbnailState = rememberVideoThumbnailState(videoUrl)
                                var isPlayingInline by remember(message.id) { mutableStateOf(false) }
                                val isOverlayActive = fullscreenMediaId == file.id

                                LaunchedEffect(isOverlayActive) {
                                    if (isOverlayActive) {
                                        isPlayingInline = true
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .width(240.dp)
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black)
                                        .then(
                                            if (!isPlayingInline) {
                                                Modifier.combinedClickable(
                                                    onClick = { onMediaClick(file) },
                                                    onLongClick = { onMediaLongClick(file) }
                                                )
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isPlayingInline) {
                                        CustomVideoPlayer(
                                            videoUrl = videoUrl,
                                            autoPlay = true,
                                            isOverlayActive = fullscreenMediaId == file.id,
                                            onFullscreenClick = { onMediaClick(file) },
                                            onReturnToThumbnail = { isPlayingInline = false },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        when (thumbnailState) {
                                            is AsyncImageState.Success -> {
                                                Image(
                                                    painter = thumbnailState.painter,
                                                    contentDescription = file.displayName,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                            is AsyncImageState.Loading -> {
                                                CircularProgressIndicator(
                                                    color = ChatColors.AccentColor,
                                                    modifier = Modifier.size(28.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                            else -> {
                                                // Fallback background or nothing
                                            }
                                        }

                                        // Play icon overlay ALWAYS visible when not playing
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.5f))
                                                .clickable { isPlayingInline = true },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play",
                                                tint = Color.White,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            isAudio && file.name != null -> {
                                val audioUrl = "${SharkordClient.currentServerUrl}/public/${file.name}"
                                AudioPlayer(
                                    audioUrl = audioUrl,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            else -> {
                                val fileIcon = getFileIcon(file.displayName, file.mimeType)
                                Row(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .background(
                                            Color(0xFF2B2B2B),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clip(RoundedCornerShape(6.dp))
                                        .combinedClickable(
                                            onClick = { onMediaClick(file) },
                                            onLongClick = { onMediaLongClick(file) }
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = fileIcon,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = file.displayName,
                                        color = Color(0xFF5B9BD5),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }

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
                                    if (firstReaction.file != null && firstReaction.file.name != null) {
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
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
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

    val context = androidx.compose.ui.platform.LocalContext.current
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
        val player = mediaPlayer
        if (player != null && isPrepared && isPlaying) {
            player.pause()
            isPlaying = false
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
                            audioManager.isSpeakerphoneOn = false
                        } else {
                            audioManager.mode = android.media.AudioManager.MODE_NORMAL
                            audioManager.isSpeakerphoneOn = true
                            onPausePlayback.value()
                        }
                    }
                }
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        }
    }

    DisposableEffect(isPlaying) {
        if (isPlaying) {
            proximitySensor?.let {
                sensorManager.registerListener(sensorEventListener, it, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
            }
            try {
                wakeLock?.takeIf { !it.isHeld }?.acquire()
            } catch (e: Exception) {
                Log.e("AudioPlayer", "WakeLock acquire failed", e)
            }
        } else {
            sensorManager.unregisterListener(sensorEventListener)
            try {
                wakeLock?.takeIf { it.isHeld }?.release()
            } catch (e: Exception) {
                Log.e("AudioPlayer", "WakeLock release failed", e)
            }
            audioManager.mode = android.media.AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = true
            isNear = false
        }
        onDispose {
            sensorManager.unregisterListener(sensorEventListener)
            try {
                wakeLock?.takeIf { it.isHeld }?.release()
            } catch (e: Exception) {
                Log.e("AudioPlayer", "WakeLock release failed", e)
            }
            audioManager.mode = android.media.AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = true
            isNear = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth(0.9f)
            .background(Color(0xFF242424), RoundedCornerShape(12.dp))
            .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = playPauseAction, 
                enabled = isPrepared,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color(0xFF5B9BD5),
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
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

// Helpers

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

private fun getFileIcon(fileName: String, mimeType: String?): String {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    val mime = mimeType?.lowercase() ?: ""
    return when {
        mime.startsWith("image/") || extension in listOf("png", "jpg", "jpeg", "webp", "gif", "svg") -> "🖼️"
        mime.startsWith("audio/") || extension in listOf("mp3", "wav", "m4a", "aac", "ogg", "flac") -> "🎵"
        mime.startsWith("video/") || extension in listOf("mp4", "mkv", "avi", "mov", "webm") -> "🎥"
        mime.startsWith("text/") || extension in listOf("txt", "log", "md", "csv") -> "📄"
        extension == "pdf" -> "📕"
        extension in listOf("zip", "rar", "tar", "gz", "7z") -> "📦"
        extension in listOf("doc", "docx", "odt") -> "📘"
        extension in listOf("xls", "xlsx", "ods") -> "📗"
        extension in listOf("ppt", "pptx", "odp") -> "📙"
        extension in listOf("js", "ts", "kt", "java", "py", "cpp", "c", "html", "css", "json", "xml", "gradle") -> "💻"
        else -> "📎"
    }
}

/**
 * Returns initials from a display name, mirroring the web app's getInitialsFromName():
 * single-word names -> first letter; multi-word -> first letter of first two words.
 */
private fun getInitials(name: String): String {
    val words = name.trim().split(" ").filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

/** Stable deterministic background color for a user's avatar based on their name. */
private fun getUsernameColor(name: String): Color {
    val palette = listOf(
        Color(0xFF5865F2), // Discord blurple
        Color(0xFF57F287), // green
        Color(0xFFFEE75C), // yellow
        Color(0xFFEB459E), // fuchsia
        Color(0xFFED4245), // red
        Color(0xFF3BA55C), // dark green
        Color(0xFF1ABC9C), // teal
        Color(0xFF9B59B6), // purple
        Color(0xFFE67E22), // orange
        Color(0xFF2980B9), // blue
    )
    val index = Math.abs(name.hashCode()) % palette.size
    return palette[index]
}
