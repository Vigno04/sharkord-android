package com.sharkord.android.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharkord.android.data.model.JoinServerData
import com.sharkord.android.ui.home.HomeUiState
import com.sharkord.android.ui.home.HomeViewModel
import com.sharkord.android.ui.theme.SharkordTheme

@Composable
fun DmsListPanel(
    data: JoinServerData,
    uiState: HomeUiState,
    viewModel: HomeViewModel,
    foregroundText: Color,
    primaryText: Color,
    modifier: Modifier = Modifier
) {
    val dmChannels = data.channels.filter { channel ->
        if (channel.isDm && channel.name.removePrefix("DM - ").split(":").contains(data.ownUserId.toString())) {
            val parts = channel.name.removePrefix("DM - ").split(":")
            val otherUserId = parts.firstOrNull { it != data.ownUserId.toString() }?.toIntOrNull()
            data.users.any { it.id == otherUserId }
        } else {
            false
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(SharkordTheme.colors.bgColor)
    ) {
        item(key = "dm-header") {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { viewModel.exitDmsListToServer() }
                            .padding(4.dp)
                    ) {
                        Text(
                            text = "◀",
                            color = SharkordTheme.colors.primaryText.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DIRECT MESSAGES",
                            color = SharkordTheme.colors.primaryText.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    IconButton(
                        onClick = { viewModel.showMembersSheet(true) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "New DM",
                            tint = SharkordTheme.colors.primaryText.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        if (dmChannels.isNotEmpty()) {
            items(dmChannels, key = { "dm-${it.id}" }) { channel ->
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    val parts = channel.name.removePrefix("DM - ").split(":")
                    val otherUserId = parts.firstOrNull { it != data.ownUserId.toString() }?.toIntOrNull()
                    val otherUser = data.users.find { it.id == otherUserId }

                    DmChannelItem(
                        channelName = channel.name,
                        user = otherUser,
                        isSelected = uiState.selectedDmChannelId == channel.id,
                        onSelect = { viewModel.selectChannel(channel.id) },
                        foregroundText = foregroundText,
                        primaryText = primaryText,
                        unreadCount = uiState.readStates[channel.id] ?: 0
                    )
                }
            }
        } else {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No Direct Messages yet.", color = SharkordTheme.colors.primaryText.copy(alpha = 0.6f), fontSize = 14.sp)
                }
            }
        }
    }
}
