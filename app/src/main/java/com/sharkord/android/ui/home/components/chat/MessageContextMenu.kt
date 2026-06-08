package com.sharkord.android.ui.home.components.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharkord.android.R
import com.sharkord.android.data.model.Emoji
import com.sharkord.android.data.model.Message
import com.sharkord.android.data.model.Role
import com.sharkord.android.data.model.User
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.ui.components.rememberAsyncImagePainter
import com.sharkord.android.ui.home.components.ChatColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageContextMenu(
    message: Message,
    customEmojis: List<Emoji>,
    ownUserId: Int,
    users: List<User>,
    roles: List<Role>,
    canPin: Boolean,
    canManage: Boolean,
    onClose: () -> Unit,
    onReactionClick: (String) -> Unit,
    onReply: () -> Unit,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDownloadFile: (com.sharkord.android.data.model.FileInfo) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val headerColor = ChatColors.HeaderColor
    val textPrimary = ChatColors.TextPrimary
    val textSecondary = ChatColors.TextSecondary

    var showDeleteConfirm by remember { androidx.compose.runtime.mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(id = R.string.common_deleteMessageTitle)) },
            text = { Text(stringResource(id = R.string.common_deleteMessageConfirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                    onClose()
                }) {
                    Text(stringResource(id = R.string.common_deleteLabel), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(id = R.string.common_cancel), color = textPrimary)
                }
            },
            containerColor = headerColor,
            titleContentColor = textPrimary,
            textContentColor = textSecondary
        )
    }

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
                .clickable(enabled = false, onClick = {}),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            colors = CardDefaults.cardColors(containerColor = headerColor)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                // Quick Reactions Selection Row
                Text(
                    text = stringResource(id = R.string.common_addReaction),
                    color = textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(
                        "thumbsup" to "👍",
                        "heart" to "❤️",
                        "joy" to "😂",
                        "fire" to "🔥",
                        "open_mouth" to "😮"
                    ).forEach { (shortcode, char) ->
                        Text(
                            text = char,
                            fontSize = 28.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    onReactionClick(shortcode)
                                    onClose()
                                }
                                .padding(8.dp)
                        )
                    }
                }

                // Custom Emojis Grid
                if (customEmojis.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(id = R.string.common_customTab),
                        color = textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        customEmojis.forEach { emoji ->
                            val customUrl = "${SharkordClient.currentServerUrl}/public/${emoji.file?.name}"
                            val customPainter = rememberAsyncImagePainter(customUrl)
                            if (customPainter != null) {
                                Image(
                                    painter = customPainter,
                                    contentDescription = emoji.name,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            onReactionClick(emoji.name)
                                            onClose()
                                        }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))

                // Reply
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onReply()
                            onClose()
                        }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Reply, contentDescription = "Reply", tint = textPrimary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = stringResource(id = R.string.common_replyToMessage), color = textPrimary, fontSize = 15.sp)
                }

                // Pin
                if (canPin) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onTogglePin()
                                onClose()
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PushPin, contentDescription = "Pin", tint = textPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(id = if (message.pinned) R.string.common_unpinMessage else R.string.common_pinMessage),
                            color = textPrimary,
                            fontSize = 15.sp
                        )
                    }
                }

                // Edit
                if (canManage && message.editable) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onEdit()
                                onClose()
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = textPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = stringResource(id = R.string.common_editMessage), color = textPrimary, fontSize = 15.sp)
                    }
                }

                // Delete
                if (canManage) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showDeleteConfirm = true
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.8f))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = stringResource(id = R.string.common_deleteMessageTitle), color = Color.Red.copy(alpha = 0.8f), fontSize = 15.sp)
                    }
                }

                // Download Files
                if (message.files.isNotEmpty()) {
                    message.files.forEach { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onDownloadFile(file)
                                    onClose()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Download", tint = textPrimary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = stringResource(id = R.string.chat_downloadFile, file.displayName), color = textPrimary, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}
