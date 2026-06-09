package com.sharkord.android.ui.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
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
import com.sharkord.android.data.network.SharkordClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerProfileBottomSheet(
    serverName: String,
    serverDescription: String?,
    hasManageServer: Boolean,
    onDismissRequest: () -> Unit,
    onShowMembers: () -> Unit,
    onServerOptionsClick: () -> Unit,
    onAddCategoryClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    val colors = com.sharkord.android.ui.theme.LocalSharkordColors.current
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
                    .background(cardColor)
            )

            // Server Info Section
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Spacer for the overlapping logo
                    Spacer(modifier = Modifier.height(56.dp))

                    Text(
                        text = serverName,
                        color = foregroundText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color.White.copy(alpha = 0.1f))

                    if (!serverDescription.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.settings_descriptionLabel),
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = serverDescription,
                            color = primaryText,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { onShowMembers() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = cardColor,
                            contentColor = primaryText
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.profile_show_members))
                        }
                    }

                    if (hasManageServer) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onServerOptionsClick() },
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
                                Text(stringResource(R.string.sidebar_serverSettings))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onAddCategoryClick() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = cardColor,
                                contentColor = primaryText
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create Category")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { onDisconnectClick() },
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

                // Overlapping Logo
                Box(
                    modifier = Modifier
                        .offset(y = (-40).dp)
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(bgColor)
                        .border(4.dp, bgColor, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color.DarkGray)) {
                        val logoPainter = com.sharkord.android.ui.components.rememberAsyncImagePainter(SharkordClient.currentServerLogoUrl)
                        if (logoPainter != null) {
                            Image(
                                painter = logoPainter,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val fallbackPainter = painterResource(id = R.drawable.logo)
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
