package com.sharkord.android.ui.home.components.chat

import com.sharkord.android.ui.theme.SharkordTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import com.sharkord.android.data.model.User
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.ui.components.rememberAsyncImagePainter

@Composable
fun ChatTopBar(
    channelName: String,
    isDm: Boolean,
    dmUser: User? = null,
    showPinnedMessages: Boolean,
    onBackClick: () -> Unit,
    onTogglePinnedMessages: () -> Unit,
    modifier: Modifier = Modifier
) {
    val headerColor = SharkordTheme.colors.bgColor
    val textPrimary = SharkordTheme.colors.primaryText
    val textSecondary = SharkordTheme.colors.primaryText.copy(alpha = 0.5f)
    val accentColor = SharkordTheme.colors.accentColor

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerColor)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onBackClick)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Channels",
                    tint = textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            if (isDm && dmUser != null) {
                val avatarUrl = dmUser.avatar?.name?.let { "${SharkordClient.currentServerUrl}/public/$it" }
                val avatarPainter = rememberAsyncImagePainter(avatarUrl, fallbackResourceId = null)
                val displayInitial = dmUser.name.take(1).uppercase()
                
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(SharkordTheme.colors.cardColor),
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
                            text = displayInitial,
                            color = SharkordTheme.colors.foregroundText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = channelName,
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            } else if (isDm) {
                Text(
                    text = "@ $channelName",
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    text = "# $channelName",
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onTogglePinnedMessages)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "Pinned Messages",
                    tint = if (showPinnedMessages) accentColor else textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        HorizontalDivider(color = SharkordTheme.colors.foregroundText.copy(alpha = 0.05f))
    }
}
