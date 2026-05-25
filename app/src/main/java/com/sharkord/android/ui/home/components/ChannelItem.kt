package com.sharkord.android.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.VolumeUp
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
import com.sharkord.android.data.model.Channel

/**
 * Isolated Component for a single channel item in the list.
 * This is just a neat little item that shows up in our channel list!
 */
@Composable
fun ChannelItem(
    channel: Channel,
    isSelected: Boolean,
    onSelect: () -> Unit,
    foregroundText: Color,
    primaryText: Color
) {
    // This checks if the channel is for voice chat. If yes, it uses a speaker icon.
    // Otherwise, it uses a hashtag (#) icon for standard text channels!
    val icon = if (channel.isVoice) Icons.Default.VolumeUp else Icons.Default.Tag

    // If the user clicked this channel, we give it a subtle white background highlight so they know it's selected.
    // If not, we just keep the background completely invisible/transparent.
    val bg = if (isSelected) Color.White.copy(alpha = 0.08f) else Color.Transparent

    // Let's color the icon! Bright text if selected, otherwise simple gray so it doesn't distract.
    val tint = if (isSelected) foregroundText else Color.Gray

    // We also make the text bold if selected so it stands out, otherwise just medium weight.
    val textWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium

    // This Row is a horizontal box that holds the icon and text together in a clickable bar.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)) // Round the corners so the highlight looks smooth
            .background(bg) // Draw that highlight color we figured out above
            .clickable { onSelect() } // When the user clicks this bar, trigger the select action!
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically // Center items vertically so they align nicely
    ) {
        // Draw the speaker or hashtag icon here
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        
        // Put a bit of space between the icon and the text
        Spacer(modifier = Modifier.width(12.dp))
        
        // Draw the name of the channel!
        Text(
            text = channel.name,
            color = if (isSelected) foregroundText else primaryText,
            fontSize = 15.sp,
            fontWeight = textWeight
        )
    }
}

