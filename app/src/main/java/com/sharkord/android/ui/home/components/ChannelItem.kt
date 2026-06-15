package com.sharkord.android.ui.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.Image
import com.sharkord.android.data.model.Channel
import com.sharkord.android.data.model.User
import com.sharkord.android.data.model.VoiceUserState
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.ui.components.rememberAsyncImagePainter
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation

/**
 * Isolated Component for a single channel item in the list.
 * Supports a long-press context menu for editing and deleting,
 * as well as drag-to-reorder.
 */
@Composable
fun ChannelItem(
    channel: Channel,
    isSelected: Boolean,
    onSelect: () -> Unit,
    canManage: Boolean = false,
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    isDragging: Boolean = false,
    foregroundText: Color,
    primaryText: Color,
    cardColor: Color = Color(0xFF2B2B2B),
    unreadCount: Int = 0,
    voiceUsers: List<VoiceUserDisplay> = emptyList()
) {
    val icon = if (channel.isVoice) Icons.Default.VolumeUp else Icons.Default.Tag
    val bg = if (isSelected || isDragging) Color.White.copy(alpha = 0.08f) else Color.Transparent
    val tint = if (isSelected) foregroundText else Color.Gray
    val textWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium

    var menuExpanded by remember { mutableStateOf(false) }

    val hasContextMenu = canManage || channel.isVoice

    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Column(modifier = Modifier.zIndex(if (isDragging) 1f else 0f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isDragging) Modifier.shadow(8.dp, RoundedCornerShape(8.dp)) else Modifier)
                .clip(RoundedCornerShape(8.dp))
                .background(bg)
                .pointerInput(channel.id, canManage) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            try {
                                withTimeout(viewConfiguration.longPressTimeoutMillis) {
                                    val up = waitForUpOrCancellation()
                                    if (up != null) {
                                        up.consume()
                                        currentOnSelect()
                                    }
                                }
                            } catch (e: PointerEventTimeoutCancellationException) {
                                currentOnLongPress()
                                if (hasContextMenu) {
                                    menuExpanded = true
                                }
                                var dragStarted = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null || change.changedToUp()) {
                                        break
                                    }
                                    val panChange = change.positionChange().y
                                    if (!dragStarted) {
                                        if (canManage && kotlin.math.abs(change.position.y - down.position.y) > viewConfiguration.touchSlop) {
                                            dragStarted = true
                                            menuExpanded = false
                                            currentOnDragStart()
                                            currentOnDrag(panChange)
                                            change.consume()
                                        }
                                    } else {
                                        if (panChange != 0f) {
                                            currentOnDrag(panChange)
                                            change.consume()
                                        }
                                    }
                                }
                                if (dragStarted) {
                                    currentOnDragEnd()
                                }
                            }
                        }
                    }
                }
                .padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = channel.name,
                color = if (isSelected) foregroundText else primaryText,
                fontSize = 15.sp,
                fontWeight = textWeight,
                modifier = Modifier.weight(1f)
            )

            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color(0xFFE3E5E8)) // Snow/porcelain color
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = unreadCount.toString(),
                        color = Color(0xFF2B2B2B), // Dark text for contrast
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // VOICE USERS LIST
        if (channel.isVoice && voiceUsers.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, top = 2.dp, bottom = 6.dp)
            ) {
                voiceUsers.forEach { voiceUser ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val avatarUrl = voiceUser.user.avatar?.name?.let { "${SharkordClient.currentServerUrl}/public/$it" }
                        val avatarPainter = rememberAsyncImagePainter(avatarUrl, fallbackResourceId = null)

                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.Gray),
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
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = voiceUser.user.name,
                            color = primaryText,
                            fontSize = 14.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Icons for mute/deafen
                        if (voiceUser.state.micMuted) {
                            Icon(
                                Icons.Default.MicOff,
                                contentDescription = "Muted",
                                tint = Color.Red.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp).padding(end = 4.dp)
                            )
                        }
                        if (voiceUser.state.soundMuted) {
                            Icon(
                                Icons.Default.HeadsetOff,
                                contentDescription = "Deafened",
                                tint = Color.Red.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.background(cardColor)
        ) {
            if (channel.isVoice) {
                DropdownMenuItem(
                    text = { Text("Open Chat", color = foregroundText) },
                    leadingIcon = { Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = foregroundText) },
                    onClick = {
                        menuExpanded = false
                        onSelect() // Voice channel selection opens its chat
                    }
                )
            }
            if (canManage) {
                DropdownMenuItem(
                    text = { Text("Edit Channel", color = foregroundText) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = foregroundText) },
                    onClick = {
                        menuExpanded = false
                        onEditClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete Channel", color = Color(0xFFED4245)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFED4245)) },
                    onClick = {
                        menuExpanded = false
                    }
                )
            }
        }
    }
}

data class VoiceUserDisplay(
    val user: User,
    val state: VoiceUserState,
    val isSpeaking: Boolean = false
)
