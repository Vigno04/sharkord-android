package com.sharkord.android.ui.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharkord.android.data.model.User
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.ui.components.rememberAsyncImagePainter

@Composable
fun DmChannelItem(
    channelName: String,
    user: User?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    foregroundText: Color,
    primaryText: Color
) {
    val bg = if (isSelected) Color.White.copy(alpha = 0.08f) else Color.Transparent
    val textWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable { onSelect() }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val avatarUrl = user?.avatar?.name?.let { "${SharkordClient.currentServerUrl}/public/$it" }
        val avatarPainter = rememberAsyncImagePainter(avatarUrl, fallbackResourceId = null)
        
        val displayInitial = user?.name?.take(1)?.uppercase() ?: channelName.take(1).uppercase()
        val displayName = user?.name ?: channelName
        
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.DarkGray),
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
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = displayName,
            color = if (isSelected) foregroundText else primaryText,
            fontSize = 15.sp,
            fontWeight = textWeight
        )
    }
}
