package com.sharkord.android.ui.home.components

import com.sharkord.android.ui.theme.SharkordTheme
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
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

private val imgRegex = Regex("""<img\b[^>]*>""")
private val classRegex = Regex("""class=["']([^"']+)["']""")
private val altRegex = Regex("""alt=["']([^"']+)["']""")
private val srcRegex = Regex("""src=["']([^"']+)["']""")
private val tokenRegex = Regex("""\[\[EMOJI\|(.*?)\|(.*?)\]\]""")

// renders a single chat message in the message list
// groups consecutive messages from the same author (compact mode) to mirror
// discord's visual style — only the first message in a run shows the avatar + name
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MessageItem(
    message: Message,
    previousMessage: Message?,
    users: List<User>,
    roles: List<Role>,
    ownUserId: Int,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier,
    fullscreenMediaId: String? = null,
    onLongClick: (Message) -> Unit = {},
    onReplyClick: (Int) -> Unit = {},
    onReactionClick: (Int, String) -> Unit = { _, _ -> },
    onMediaClick: (com.sharkord.android.data.model.FileInfo) -> Unit = {},
    onMediaLongClick: (com.sharkord.android.data.model.FileInfo) -> Unit = {},
    onUserClick: (Int) -> Unit = {}
) {
    val bgColor = SharkordTheme.colors.bgColor
    val textPrimary = SharkordTheme.colors.primaryText
    val textSecondary = SharkordTheme.colors.primaryText.copy(alpha = 0.5f)
    val textMuted = SharkordTheme.colors.primaryText.copy(alpha = 0.5f)

    val author = users.find { it.id == message.userId }

    // show the avatar + author header only when this is the first message in a consecutive run
    val showHeader = previousMessage == null
        || previousMessage.userId != message.userId
        || (message.createdAt - previousMessage.createdAt) > TimeUnit.MINUTES.toMillis(5)
        || message.replyTo != null

    val isLight = SharkordTheme.colors.isLight
    // resolve author's top-role color
    val nameColor = remember(author, roles, isLight) {
        val authorRoleIds = author?.roleIds ?: emptyList()
        val topRole = roles
            .filter { it.id in authorRoleIds }
            .maxByOrNull { it.position }
        val hex = topRole?.color?.takeIf { it.isNotBlank() && it != "#99AAB5" }
        if (hex != null) {
            val rawColor = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(textPrimary)
            // In light mode, if the role color is very bright (e.g. white or yellow), fallback to the default text color for contrast
            if (isLight && rawColor.luminance() > 0.8f) {
                textPrimary
            } else {
                rawColor
            }
        } else {
            textPrimary
        }
    }

    // pre-process HTML to preserve custom emojis and mentions as tokens before stripping tags
    val preProcessedHtml = remember(message.content) {
        var raw = message.content ?: ""
        
        val mentionRegex1 = Regex("""<span[^>]*data-type=["']mention["'][^>]*data-user-id=["'](\d+)["'][^>]*>(.*?)</span>""")
        raw = mentionRegex1.replace(raw) { matchResult ->
            "[[MENTION|${matchResult.groupValues[1]}|${matchResult.groupValues[2]}]]"
        }
        val mentionRegex2 = Regex("""<span[^>]*data-user-id=["'](\d+)["'][^>]*data-type=["']mention["'][^>]*>(.*?)</span>""")
        raw = mentionRegex2.replace(raw) { matchResult ->
            "[[MENTION|${matchResult.groupValues[1]}|${matchResult.groupValues[2]}]]"
        }
        
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

    val accentColor = SharkordTheme.colors.accentColor
    val accentBgColor = SharkordTheme.colors.accentColor.copy(alpha = 0.15f)

    val annotatedContent = remember(textWithTokens, accentColor, accentBgColor) {
        buildAnnotatedString {
            val combinedRegex = Regex("""\[\[(EMOJI|MENTION)\|(.*?)\|(.*?)\]\]""")
            var lastIndex = 0
            
            combinedRegex.findAll(textWithTokens).forEach { matchResult ->
                append(textWithTokens.substring(lastIndex, matchResult.range.first))

                val type = matchResult.groupValues[1]
                if (type == "EMOJI") {
                    val alt = matchResult.groupValues[2]
                    val src = matchResult.groupValues[3]
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
                } else if (type == "MENTION") {
                    val userId = matchResult.groupValues[2]
                    val usernameText = matchResult.groupValues[3]
                    
                    pushStringAnnotation(tag = "mention", annotation = userId)
                    pushStyle(androidx.compose.ui.text.SpanStyle(
                        color = accentColor,
                        background = accentBgColor,
                        fontWeight = FontWeight.Bold
                    ))
                    append(usernameText)
                    pop()
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

    val highlightColor = SharkordTheme.colors.foregroundText.copy(alpha = 0.08f)
    val transparentHighlight = Color(0x00FFFFFF)
    val animatableColor = remember { androidx.compose.animation.Animatable(transparentHighlight) }
    
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            animatableColor.animateTo(highlightColor, androidx.compose.animation.core.tween(300))
            animatableColor.animateTo(transparentHighlight, androidx.compose.animation.core.tween(300))
        } else {
            animatableColor.snapTo(transparentHighlight)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(animatableColor.value)
            .combinedClickable(
                onLongClick = { onLongClick(message) },
                onClick = {}
            )
    ) {
        // if it is a reply, render the curved connection line and original message preview
        if (message.replyTo != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onReplyClick(message.replyTo.id) }
                    .padding(start = 12.dp, top = 6.dp, bottom = 2.dp)
            ) {
                // curved connecting thread
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

                // resolve target author for the reply preview
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
                            color = SharkordTheme.colors.accentColor,
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
            // left column: avatar or spacer
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
                            color = SharkordTheme.colors.accentColor,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        else -> {
                            // empty (no avatar) or Failure — show initials
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
                // compact mode — reserve the same width as the avatar column so text aligns
                Spacer(modifier = Modifier.width(40.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            // right column: header (name + time) and content
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
                    var textLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                    
                    Text(
                        text = annotatedContent,
                        color = textPrimary,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        inlineContent = inlineContentMap,
                        onTextLayout = { textLayoutResult = it },
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = { pos: androidx.compose.ui.geometry.Offset -> onLongClick(message) },
                                onTap = { pos: androidx.compose.ui.geometry.Offset ->
                                    textLayoutResult?.let { layoutResult ->
                                        val offset = layoutResult.getOffsetForPosition(pos)
                                        annotatedContent.getStringAnnotations(tag = "mention", start = offset, end = offset)
                                            .firstOrNull()?.let { annotation ->
                                                annotation.item.toIntOrNull()?.let { userId ->
                                                    onUserClick(userId)
                                                }
                                            }
                                    }
                                }
                            )
                        }
                    )
                }

                // edited indicator
                if (message.editedAt != null) {
                    Text(
                        text = "(edited)",
                        color = textMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // media & File attachments: display photos/audios inline or generic chips
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
                                        .background(SharkordTheme.colors.cardColor)
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
                                            color = SharkordTheme.colors.accentColor,
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
                                                    color = SharkordTheme.colors.accentColor,
                                                    modifier = Modifier.size(28.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                            else -> {
                                                // fallback background or nothing
                                            }
                                        }

                                        // play icon overlay ALWAYS visible when not playing
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
                                            SharkordTheme.colors.cardColor,
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

                                val chipBg = if (hasReacted) SharkordTheme.colors.accentColor.copy(alpha = 0.15f) else SharkordTheme.colors.cardColor
                                val chipBorder = if (hasReacted) SharkordTheme.colors.accentColor.copy(alpha = 0.8f) else Color.Transparent

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
                                        color = if (hasReacted) SharkordTheme.colors.accentColor else textSecondary,
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



// helpers

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

// returns initials from a display name, mirroring the web app's getInitialsFromName():
// single-word names -> first letter; multi-word -> first letter of first two words
private fun getInitials(name: String): String {
    val words = name.trim().split(" ").filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

// stable deterministic background color for a user's avatar based on their name
@androidx.compose.runtime.Composable
private fun getUsernameColor(name: String): Color {
    val isLight = com.sharkord.android.ui.theme.SharkordTheme.colors.isLight
    val palette = if (isLight) {
        listOf(
            Color(0xFF3B48D9), // darker blurple
            Color(0xFF2E8B57), // sea green
            Color(0xFFD4AC0D), // dark yellow
            Color(0xFFC2185B), // dark fuchsia
            Color(0xFFC0392B), // dark red
            Color(0xFF1E8449), // darker green
            Color(0xFF117A65), // dark teal
            Color(0xFF7D3C98), // dark purple
            Color(0xFFBA4A00), // dark orange
            Color(0xFF2471A3), // dark blue
        )
    } else {
        listOf(
            com.sharkord.android.ui.theme.SharkordTheme.colors.accentColor, // Discord blurple
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
    }
    val index = Math.abs(name.hashCode()) % palette.size
    return palette[index]
}
