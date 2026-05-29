package com.sharkord.android.ui.home.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import com.sharkord.android.R
import com.sharkord.android.data.model.Message
import com.sharkord.android.data.model.User
import com.sharkord.android.ui.home.components.ChatColors

@Composable
fun PinnedMessagesSheet(
    isLoading: Boolean,
    pinnedMessages: List<Message>,
    users: List<User>,
    onClose: () -> Unit,
    onUnpin: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = ChatColors.BgColor
    val cardColor = ChatColors.CardColor
    val textPrimary = ChatColors.TextPrimary
    val textSecondary = ChatColors.TextSecondary
    val textMuted = ChatColors.TextMuted
    val accentColor = ChatColors.AccentColor

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
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 8.dp))

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
                                    .padding(12.dp)
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val authorName = users.find { it.id == msg.userId }?.name ?: "Unknown"
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
