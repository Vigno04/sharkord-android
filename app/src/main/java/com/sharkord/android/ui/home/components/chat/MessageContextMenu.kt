package com.sharkord.android.ui.home.components.chat

import com.sharkord.android.ui.theme.SharkordTheme
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
    onAddReactionClick: () -> Unit,
    onReply: () -> Unit,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDownloadFile: (com.sharkord.android.data.model.FileInfo) -> Unit = {},
    onShareFile: (com.sharkord.android.data.model.FileInfo) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val headerColor = SharkordTheme.colors.bgColor
    val textPrimary = SharkordTheme.colors.primaryText
    val textSecondary = SharkordTheme.colors.primaryText.copy(alpha = 0.5f)

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
                // quick Reactions Selection Row
                Text(
                    text = stringResource(id = R.string.common_addReaction),
                    color = textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                val context = androidx.compose.ui.platform.LocalContext.current
                val sharedPrefs = remember { context.getSharedPreferences("emoji_reactions_prefs", android.content.Context.MODE_PRIVATE) }
                val baseReactions = listOf("👍", "❤️", "😂", "🔥", "😮")
                val recentReactions = remember {
                    sharedPrefs.getString("recent_reactions", null)?.let {
                        com.google.gson.Gson().fromJson<List<String>>(it, object : com.google.gson.reflect.TypeToken<List<String>>() {}.type)
                    } ?: baseReactions
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    recentReactions.forEach { shortcode ->
                        val char = com.sharkord.android.ui.home.components.EmojiMapper.map(shortcode)
                        Text(
                            text = char,
                            fontSize = 28.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    onReactionClick(shortcode)
                                    val updatedList = recentReactions.toMutableList()
                                    updatedList.remove(shortcode)
                                    updatedList.add(0, shortcode)
                                    val limited = updatedList.take(15)
                                    sharedPrefs.edit().putString("recent_reactions", com.google.gson.Gson().toJson(limited)).apply()
                                    onClose()
                                }
                                .padding(8.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(SharkordTheme.colors.foregroundText.copy(alpha = 0.1f))
                            .clickable {
                                onAddReactionClick()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Reaction",
                            tint = textPrimary
                        )
                    }
                }

                // custom Emojis Grid
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

                HorizontalDivider(color = SharkordTheme.colors.foregroundText.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))

                // Message Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // reply
                    IconButton(
                        onClick = {
                            onReply()
                            onClose()
                        }
                    ) {
                        Icon(Icons.Default.Reply, contentDescription = "Reply", tint = textPrimary)
                    }

                    // pin
                    if (canPin) {
                        IconButton(
                            onClick = {
                                onTogglePin()
                                onClose()
                            }
                        ) {
                            Icon(Icons.Default.PushPin, contentDescription = "Pin", tint = if (message.pinned) SharkordTheme.colors.accentColor else textPrimary)
                        }
                    }

                    // edit
                    if (canManage && message.editable) {
                        IconButton(
                            onClick = {
                                onEdit()
                                onClose()
                            }
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = textPrimary)
                        }
                    }

                    // delete
                    if (canManage) {
                        IconButton(
                            onClick = { showDeleteConfirm = true }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.8f))
                        }
                    }

                    // download and share
                    message.files.firstOrNull()?.let { file ->
                        IconButton(
                            onClick = {
                                onDownloadFile(file)
                                onClose()
                            }
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Download", tint = textPrimary)
                        }

                        IconButton(
                            onClick = {
                                onShareFile(file)
                                onClose()
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = textPrimary)
                        }
                    }
                }
            }
        }
    }
}
