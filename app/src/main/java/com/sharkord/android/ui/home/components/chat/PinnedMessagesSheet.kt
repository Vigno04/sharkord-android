package com.sharkord.android.ui.home.components.chat

import com.sharkord.android.ui.theme.SharkordTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import com.sharkord.android.R
import com.sharkord.android.data.model.Message
import com.sharkord.android.data.model.User
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.ui.components.AsyncImageState
import com.sharkord.android.ui.components.rememberAsyncImageState

@Composable
fun PinnedMessagesSheet(
    isLoading: Boolean,
    pinnedMessages: List<Message>,
    users: List<User>,
    onClose: () -> Unit,
    onUnpin: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = SharkordTheme.colors.bgColor
    val cardColor = SharkordTheme.colors.cardColor
    val textPrimary = SharkordTheme.colors.primaryText
    val textSecondary = SharkordTheme.colors.primaryText.copy(alpha = 0.5f)
    val textMuted = SharkordTheme.colors.primaryText.copy(alpha = 0.5f)
    val accentColor = SharkordTheme.colors.accentColor

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onClose)
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .clickable(enabled = false, onClick = {}),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            colors = CardDefaults.cardColors(containerColor = bgColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.common_pinnedMessagesTitle),
                        color = textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = textSecondary)
                    }
                }
                HorizontalDivider(color = SharkordTheme.colors.foregroundText.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 8.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentColor)
                    }
                } else if (pinnedMessages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(id = R.string.common_noPinnedMessages), color = textMuted, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(pinnedMessages) { _, msg ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(cardColor, RoundedCornerShape(8.dp))
                                    .clickable {
                                        com.sharkord.android.ui.navigation.MessageNavigationManager.jumpToMessage(msg.channelId, msg.id)
                                        onClose()
                                    }
                                    .padding(12.dp)
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val author = users.find { it.id == msg.userId }
                                
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(SharkordTheme.colors.cardColor),
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
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp
                                        )
                                        else -> {
                                            val initials = getInitials(author?.name ?: "?")
                                            val bgColor = getUsernameColor(author?.name ?: "?")
                                            Box(
                                                modifier = Modifier.fillMaxSize().background(bgColor),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = initials,
                                                    color = SharkordTheme.colors.foregroundText,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    val authorName = author?.name ?: "Unknown"
                                    Text(text = authorName, color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val pinPlain = remember(msg.content) {
                                        HtmlCompat.fromHtml(msg.content, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                                    }
                                    Text(text = pinPlain, color = textPrimary, fontSize = 14.sp)
                                }
                                IconButton(onClick = { onUnpin(msg.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Unpin",
                                        tint = Color.Red.copy(alpha = 0.7f)
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

private fun getInitials(name: String): String {
    val words = name.trim().split(" ").filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

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

