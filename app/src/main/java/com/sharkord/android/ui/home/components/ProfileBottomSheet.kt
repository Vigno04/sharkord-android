package com.sharkord.android.ui.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
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
import com.sharkord.android.data.model.Role
import com.sharkord.android.data.model.User
import com.sharkord.android.data.network.SharkordClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    onLogoutClick: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    roles: List<Role> = emptyList()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = bgColor,
        contentColor = primaryText,
        dragHandle = null // Remove default drag handle to allow banner to touch the top
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Banner Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        try {
                            Color(android.graphics.Color.parseColor(currentUser?.bannerColor ?: "#2B2B2B"))
                        } catch (e: Exception) {
                            cardColor
                        }
                    )
            ) {
                val bannerUrl = currentUser?.banner?.name?.let { "${SharkordClient.currentServerUrl}/public/$it" }
                val bannerPainter = com.sharkord.android.ui.components.rememberAsyncImagePainter(bannerUrl)
                if (bannerPainter != null) {
                    Image(
                        painter = bannerPainter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Profile Info Section
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Spacer for the overlapping avatar
                    Spacer(modifier = Modifier.height(56.dp))

                    Text(
                        text = userName,
                        color = foregroundText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(id = R.string.settings_userIdLabel) + ": #" + ownUserId,
                        color = Color.Gray,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color.White.copy(alpha = 0.1f))

                    if (!currentUser?.bio.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.settings_bioLabel),
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentUser.bio!!,
                            color = primaryText,
                            fontSize = 14.sp
                        )
                    }

                    val userRoles = currentUser?.roleIds?.mapNotNull { roleId -> roles.find { it.id == roleId } } ?: emptyList()
                    if (userRoles.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.settings_usersRolesCol),
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            userRoles.forEach { role ->
                                val roleColor = try {
                                    Color(android.graphics.Color.parseColor(role.color))
                                } catch (e: Exception) {
                                    Color.Gray
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(cardColor)
                                        .border(1.dp, roleColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(roleColor))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = role.name, color = primaryText, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    if (currentUser?.createdAt != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        val date = Date(currentUser.createdAt)
                        val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        Text(
                            text = stringResource(R.string.common_memberSince, formatter.format(date)),
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { onNavigateToSettings() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF5865F2).copy(alpha = 0.15f),
                            contentColor = Color(0xFF5865F2)
                        ),
                        border = BorderStroke(1.dp, Color(0xFF5865F2).copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_userSettingsTitle))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = { onLogoutClick() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444).copy(alpha = 0.15f),
                            contentColor = Color(0xFFEF4444)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                         Text(
                             stringResource(id = R.string.sidebar_disconnect),
                             modifier = Modifier.padding(vertical = 4.dp)
                         )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Overlapping Avatar
                val avatarUrl = currentUser?.avatar?.name?.let { "${SharkordClient.currentServerUrl}/public/$it" }

                Box(
                    modifier = Modifier
                        .offset(y = (-40).dp)
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(bgColor)
                        .border(4.dp, bgColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(Color.DarkGray)) {
                        val avatarPainter = com.sharkord.android.ui.components.rememberAsyncImagePainter(avatarUrl)
                        if (avatarPainter != null) {
                            Image(
                                painter = avatarPainter,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val fallbackPainter = com.sharkord.android.ui.components.rememberAsyncImagePainter(null)
                            if (fallbackPainter != null) {
                                Image(
                                    painter = fallbackPainter,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
