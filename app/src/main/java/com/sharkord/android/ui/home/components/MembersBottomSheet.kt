package com.sharkord.android.ui.home.components

import com.sharkord.android.ui.theme.SharkordTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.Sms
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharkord.android.R
import com.sharkord.android.data.model.User
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.ui.components.rememberAsyncImagePainter

// bottom sheet displaying the directory/list of members in the server
// this slides up to show a scrollable list of everyone hanging out on the server!
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersBottomSheet(
    users: List<User>,
    ownUserId: Int,
    onDismissRequest: () -> Unit,
    onMessageClick: (Int) -> Unit
) {
    val colors = SharkordTheme.colors
    val bgColor = colors.bgColor
    val cardColor = colors.cardColor
    val primaryText = colors.primaryText
    val foregroundText = colors.foregroundText

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = bgColor,
        contentColor = primaryText,
        modifier = Modifier // Removed fillMaxHeight to prevent detaching from bottom
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.common_members),
                    color = foregroundText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${users.size} members",
                    color = primaryText.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = foregroundText.copy(alpha = 0.1f))

            if (users.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Wow, so empty. So many friends",
                        color = primaryText.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(users) { user ->
                    val bannerColor = androidx.compose.runtime.remember(user.bannerColor) {
                        try {
                            Color(android.graphics.Color.parseColor(user.bannerColor ?: "#00000000"))
                        } catch (e: Exception) {
                            Color.Transparent
                        }
                    }
                    
                    val itemBgColor = if (bannerColor != Color.Transparent) bannerColor.copy(alpha = 0.15f) else cardColor

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(itemBgColor)
                            .clickable {
                                if (user.id != ownUserId) {
                                    onMessageClick(user.id)
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // left accent bar for banner color
                        if (bannerColor != Color.Transparent) {
                            Box(
                                modifier = Modifier
                                    .height(32.dp)
                                    .width(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(bannerColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    
                        val avatarUrl = user.avatar?.name?.let { "${SharkordClient.currentServerUrl}/public/$it" }
                        val avatarPainter = rememberAsyncImagePainter(avatarUrl, fallbackResourceId = null)
                        
                        Box(
                            modifier = Modifier
                                .size(40.dp)
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
                                    text = user.name.take(1).uppercase(),
                                    color = SharkordTheme.colors.foregroundText,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column {
                            Text(
                                text = user.name,
                                color = foregroundText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (user.id == ownUserId) {
                                Text(
                                    text = stringResource(id = R.string.common_you),
                                    color = SharkordTheme.colors.primaryText.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        if (user.id != ownUserId) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SharkordTheme.colors.accentColor.copy(alpha = 0.15f))
                                    .clickable { onMessageClick(user.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Sms,
                                    contentDescription = "Message",
                                    tint = SharkordTheme.colors.accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}
