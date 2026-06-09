package com.sharkord.android.ui.home.components

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

/**
 * Top Server Header, including search bar and Direct Messages trigger.
 */
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
    isServerSheetOpen: Boolean = false
) {
    // Stack the header, search bar, and direct message bar vertically
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // This Row displays the server's name and how many people are in it.
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
                    // Show the server name with a little down arrow icon
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
                // Display the number of members in a gray subtext
                Text(
                    text = stringResource(id = R.string.common_member_count, memberCount),
                    color = Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // This Box is styled to look like a search bar (to implement)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(cardColor)
                .clickable { onSearchClick() } // Triggers search when clicked (to implement)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Magnifying glass icon
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(id = R.string.dialogs_searchTitle),
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Search placeholder text
                Text(
                    text = stringResource(id = R.string.dialogs_searchTitle),
                    color = Color.Gray,
                    fontSize = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Separation line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.05f))
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Direct Messages trigger bar (to implement)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onDirectMessagesClick() }
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Sms,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(id = R.string.sidebar_directMessages),
                color = Color.LightGray,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
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

