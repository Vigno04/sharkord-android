package com.sharkord.android.ui.home.components

import com.sharkord.android.ui.theme.SharkordTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharkord.android.R

// top Server Header, including search bar and Direct Messages trigger
@Composable
fun ServerHeader(
    serverName: String,
    memberCount: Int,
    cardColor: Color,
    foregroundText: Color,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    onDirectMessagesClick: () -> Unit = {},
    onServerClick: () -> Unit = {},
    isServerSheetOpen: Boolean = false,
    totalUnreadDMs: Int = 0,
    isDmsListSelected: Boolean = false
) {
    // stack the header, search bar, and direct message bar vertically
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // this Row displays the server's name and how many people are in it
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onServerClick() }
                    .padding(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // show the server name with a little down arrow icon
                    Text(
                        text = serverName,
                        color = foregroundText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis // If name is too long, cut it with "..."
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    val chevronRotation by animateFloatAsState(targetValue = if (isServerSheetOpen) 180f else 0f)
                    
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Server Options",
                        tint = foregroundText,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(chevronRotation)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // display the number of members in a gray subtext
                Text(
                    text = stringResource(id = R.string.common_member_count, memberCount),
                    color = SharkordTheme.colors.primaryText.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // this Box is styled to look like a search bar (to implement)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(cardColor)
                .clickable { onSearchClick() } // Triggers search when clicked (to implement)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // magnifying glass icon
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(id = R.string.dialogs_searchTitle),
                    tint = SharkordTheme.colors.primaryText.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // search placeholder text
                Text(
                    text = stringResource(id = R.string.dialogs_searchTitle),
                    color = SharkordTheme.colors.primaryText.copy(alpha = 0.6f),
                    fontSize = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // separation line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.05f))
        )

        Spacer(modifier = Modifier.height(8.dp))

        // direct Messages trigger bar (to implement)
        val dmBgColor = if (isDmsListSelected) Color.White.copy(alpha = 0.1f) else Color.Transparent
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(dmBgColor)
                .clickable { onDirectMessagesClick() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Sms,
                contentDescription = null,
                tint = SharkordTheme.colors.primaryText.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(id = R.string.sidebar_directMessages),
                color = SharkordTheme.colors.primaryText.copy(alpha = 0.8f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            
            if (totalUnreadDMs > 0) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(SharkordTheme.colors.primaryText) // Snow/porcelain color
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = totalUnreadDMs.toString(),
                        color = SharkordTheme.colors.cardColor, // Dark text for contrast
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // separation line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.05f))
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

