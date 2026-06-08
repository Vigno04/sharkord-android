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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharkord.android.data.model.Channel

/**
 * Isolated Component for a single channel item in the list.
 * Supports a long-press context menu for editing and deleting.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelItem(
    channel: Channel,
    isSelected: Boolean,
    onSelect: () -> Unit,
    canManage: Boolean = false,
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    foregroundText: Color,
    primaryText: Color,
    cardColor: Color = Color(0xFF2B2B2B)
) {
    val icon = if (channel.isVoice) Icons.Default.VolumeUp else Icons.Default.Tag
    val bg = if (isSelected) Color.White.copy(alpha = 0.08f) else Color.Transparent
    val tint = if (isSelected) foregroundText else Color.Gray
    val textWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium

    var menuExpanded by remember { mutableStateOf(false) }

    val hasContextMenu = canManage || channel.isVoice

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(bg)
                .combinedClickable(
                    onClick = { onSelect() },
                    onLongClick = {
                        if (hasContextMenu) {
                            menuExpanded = true
                        }
                    }
                )
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
                fontWeight = textWeight
            )
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
                        onDeleteClick()
                    }
                )
            }
        }
    }
}
