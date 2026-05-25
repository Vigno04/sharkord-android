package com.sharkord.android.ui.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
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
 * Bottom sheet displaying options for user profiles, server configurations, showing member list, and logging out (to redo)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileBottomSheet(
    currentUser: User?,
    userName: String,
    ownUserId: Int,
    serverName: String,
    serverId: String?,
    memberCount: Int,
    bgColor: Color,
    cardColor: Color,
    primaryText: Color,
    foregroundText: Color,
    onDismissRequest: () -> Unit,
    onShowMembers: () -> Unit,
    onLogoutClick: () -> Unit
) {
    // This is the core bottom sheet popup
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = cardColor,
        contentColor = primaryText
    ) {
        // Vertical layout container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main title text
            Text(
                text = stringResource(id = R.string.profile_user_settings_title),
                color = foregroundText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            // This displays our profile card with avatar picture and user name
            Card(
                colors = CardDefaults.cardColors(containerColor = bgColor),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.05f),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val avatarUrl = currentUser?.avatar?.name?.let { "${SharkordClient.currentServerUrl}/public/$it" }
                    val avatarPainter = rememberAsyncImagePainter(avatarUrl)
                    
                    // Circular Avatar holder
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color.DarkGray),
                        contentAlignment = Alignment.Center
                    ) {
                        avatarPainter?.let {
                            Image(
                                painter = it,
                                contentDescription = "User Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Text details: user's nickname and their unique user ID number
                    Column {
                        Text(
                            text = userName,
                            color = foregroundText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(id = R.string.settings_userIdLabel) + ": #" + ownUserId,
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            // This card shows details about the current server we are connected to
            Card(
                colors = CardDefaults.cardColors(containerColor = bgColor),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.05f),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Small capitalized category title
                    Text(
                        text = stringResource(id = R.string.settings_serverInfoTitle).uppercase(),
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Server's name
                    Text(
                        text = stringResource(id = R.string.settings_nameLabel) + ": " + serverName,
                        color = foregroundText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    // Server's unique ID
                    Text(
                        text = "ID: " + (serverId ?: stringResource(id = R.string.settings_unknownValue)),
                        color = Color.Gray,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    // Divider line
                    Divider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Show members
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onShowMembers() } // When clicked, close this sheet and open the members sheet!
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = primaryText,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                             Text(
                                 text = stringResource(id = R.string.profile_show_members),
                                 color = primaryText,
                                 fontSize = 14.sp
                             )
                        }
                        // Displays the total member count
                        Text(
                            text = "$memberCount",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Pushes everything up, but allows spacer to be smaller if screen is small
            Spacer(modifier = Modifier.weight(1f, fill = false))

            // A red outline button to log out / disconnect from the active server
            Button(
                onClick = { onLogoutClick() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEF4444).copy(alpha = 0.15f), // Transparent red background
                    contentColor = Color(0xFFEF4444) // Bright red text color
                ),
                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)), // Red border
                shape = RoundedCornerShape(12.dp)
            ) {
                 Text(
                     stringResource(id = R.string.sidebar_disconnect),
                     modifier = Modifier.padding(vertical = 4.dp)
                 )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

