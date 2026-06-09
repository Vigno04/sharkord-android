package com.sharkord.android.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharkord.android.data.model.Channel
import com.sharkord.android.data.model.Category
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.ui.zIndex

@Composable
fun CategorySection(
    category: Category,
    channels: List<Channel>,
    isCollapsed: Boolean,
    hasManageChannels: Boolean,
    selectedChannelId: Int?,
    onToggleCategory: () -> Unit,
    onAddChannelClick: () -> Unit,
    onChannelSelect: (Int) -> Unit,
    onChannelLongPress: (Int) -> Unit,
    onChannelEdit: (Int) -> Unit,
    onChannelDelete: (Int) -> Unit,
    onReorderChannels: (List<Int>) -> Unit,
    foregroundText: Color,
    primaryText: Color
) {
    // Local state for dragging
    var localChannels by remember(channels) { mutableStateOf(channels) }
    var draggedId by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var dragStartIndex by remember { mutableStateOf(-1) }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        // HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable { onToggleCategory() }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isCollapsed) "▶" else "▼",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = category.name.uppercase(),
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            if (hasManageChannels) {
                IconButton(
                    onClick = onAddChannelClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Channel",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // CHANNELS
        if (!isCollapsed) {
            val density = LocalDensity.current.density
            val itemHeightPx = 44 * density

            localChannels.forEachIndexed { index, channel ->
                key(channel.id) {
                    val isDragging = draggedId == channel.id
                    val offsetModifier = if (isDragging) {
                        Modifier.offset { IntOffset(0, dragOffset.roundToInt()) }
                    } else {
                        Modifier
                    }

                    Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 1f else 0f)
                        .then(offsetModifier)
                ) {
                    ChannelItem(
                        channel = channel,
                        isSelected = selectedChannelId == channel.id,
                        onSelect = {
                            if (!channel.isVoice) onChannelSelect(channel.id)
                        },
                        onLongPress = {
                            if (!channel.isVoice) onChannelLongPress(channel.id)
                        },
                        canManage = hasManageChannels,
                        onEditClick = { onChannelEdit(channel.id) },
                        onDeleteClick = { onChannelDelete(channel.id) },
                        onDragStart = {
                            draggedId = channel.id
                            dragStartIndex = localChannels.indexOfFirst { it.id == channel.id }
                            dragOffset = 0f
                        },
                        onDrag = { delta ->
                            val maxUp = -dragStartIndex * itemHeightPx
                            val maxDown = (localChannels.size - 1 - dragStartIndex) * itemHeightPx
                            dragOffset = (dragOffset + delta).coerceIn(maxUp, maxDown)

                            // Calculate if we moved past the threshold of another item
                            val newIndex = (dragStartIndex + (dragOffset / itemHeightPx).roundToInt())
                                .coerceIn(0, localChannels.size - 1)
                            
                            val currentIndex = localChannels.indexOfFirst { it.id == channel.id }
                            
                            if (currentIndex != -1 && newIndex != currentIndex) {
                                // Swap items visually
                                val mutableList = localChannels.toMutableList()
                                val item = mutableList.removeAt(currentIndex)
                                mutableList.add(newIndex, item)
                                localChannels = mutableList
                                dragStartIndex = newIndex
                                dragOffset -= (newIndex - currentIndex) * itemHeightPx
                            }
                        },
                        onDragEnd = {
                            draggedId = null
                            dragOffset = 0f
                            // Trigger callback to backend
                            if (localChannels != channels) {
                                onReorderChannels(localChannels.map { it.id })
                            }
                        },
                        isDragging = isDragging,
                        foregroundText = foregroundText,
                        primaryText = primaryText
                    )
                }
            }
            }
        }
    }
}
