package com.sharkord.android.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.ui.components.rememberAsyncImagePainter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path

/**
 * Renders a single chat message in the message list.
 *
 * Groups consecutive messages from the same author (compact mode) to mirror
 * Discord's visual style — only the first message in a run shows the avatar + name.
 *
 * @param message      The message to render.
 * @param previousMessage  The message directly above this one (null if it's the first). Used to
 *                         decide whether to show the author header or render in compact mode.
 * @param users        The full user list, used to resolve the author's name and avatar.
 * @param roles        The full role list, used to color the author's name by their top role.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    message: Message,
    previousMessage: Message?,
    users: List<User>,
    roles: List<Role>,
    modifier: Modifier = Modifier,
    onLongClick: (Message) -> Unit = {},
    onReplyClick: (Int) -> Unit = {}
) {
    val bgColor = Color(0xFF1C1C1C)
    val textPrimary = Color(0xFFE8E8E8)
    val textSecondary = Color(0xFF9E9E9E)
    val textMuted = Color(0xFF6E6E6E)

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

    val timestamp = remember(message.createdAt) {
        formatTimestamp(message.createdAt)
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
                    Text(
                        text = plainContent,
                        color = textSecondary,
                        fontSize = 15.sp,
                        lineHeight = 21.sp
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

                // File attachments: display names as small chips
                if (message.files.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    message.files.forEach { file ->
                        Text(
                            text = "📎 ${file.originalName ?: file.name}",
                            color = Color(0xFF5B9BD5),
                            fontSize = 13.sp,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .background(
                                    Color(0xFF2B2B2B),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
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
