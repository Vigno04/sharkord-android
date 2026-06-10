package com.sharkord.android.ui.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharkord.android.R
import com.sharkord.android.data.model.User
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.ui.components.rememberAsyncImagePainter

/**
 * Floating profile pill at the bottom of the home screen.
 */
@Composable
fun BottomProfileBar(
    currentUser: User?,
    userName: String,
    foregroundText: Color,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Layer the floating circular avatar on top of the background bar
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onProfileClick() } // Open user settings profile sheet when clicked
            .padding(horizontal = 16.dp, vertical = 8.dp) // Outer spacing
    ) {
        // The dark background bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(start = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF232428))
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.05f),
                    RoundedCornerShape(16.dp)
                )
        ) {
            // Text details inside the bar (name & "Online" status)
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = 60.dp, end = 16.dp), // Padding ensures name text doesn't hide behind avatar
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // User's name
                    Text(
                        text = userName,
                        color = foregroundText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Down-arrow indicating they can click it for menu
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
                // Online label (to implement)
                Text(
                    text = stringResource(id = R.string.common_online),
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }

        // The Avatar (Floating on the left side of the bar)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(72.dp)
        ) {
            val avatarUrl = currentUser?.avatar?.name?.let { "${SharkordClient.currentServerUrl}/public/$it" }
            val avatarPainter = rememberAsyncImagePainter(avatarUrl)
            
            // Draw the profile avatar image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                avatarPainter?.let {
                    Image(
                        painter = it,
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            // Green Online Dot: small green circle in the bottom right corner of the avatar (to implement)
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF23A559))
                    .align(Alignment.BottomEnd)
                    .border(2.5.dp, Color(0xFF232428), CircleShape)
            )
        }
    }
}

