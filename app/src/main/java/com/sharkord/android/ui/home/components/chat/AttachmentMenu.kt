package com.sharkord.android.ui.home.components.chat

import com.sharkord.android.ui.theme.SharkordTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AttachmentMenu(
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onFilesClick: () -> Unit,
    onLocationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardColor = SharkordTheme.colors.cardColor
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(cardColor, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AttachmentMenuItem(
            icon = Icons.Default.CameraAlt,
            label = "Camera",
            onClick = onCameraClick,
            color = Color(0xFFE91E63)
        )
        AttachmentMenuItem(
            icon = Icons.Default.PhotoLibrary,
            label = "Gallery",
            onClick = onGalleryClick,
            color = Color(0xFF9C27B0)
        )
        AttachmentMenuItem(
            icon = Icons.Default.InsertDriveFile,
            label = "Files",
            onClick = onFilesClick,
            color = Color(0xFF2196F3)
        )
        AttachmentMenuItem(
            icon = Icons.Default.LocationOn,
            label = "Location",
            onClick = onLocationClick,
            color = Color(0xFF4CAF50)
        )
    }
}

@Composable
private fun AttachmentMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color
) {
    val textPrimary = SharkordTheme.colors.primaryText

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(color.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = textPrimary,
            fontSize = 13.sp
        )
    }
}
