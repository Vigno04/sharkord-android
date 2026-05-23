package com.sharkord.android.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharkord.android.R
import com.sharkord.android.data.model.JoinServerData
import com.sharkord.android.data.network.SharkordClient

@Composable
fun HomeScreen(
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    
    // Original dark theme background (oklch(0.145 0 0) = #1C1C1C)
    val bgColor = Color(0xFF1C1C1C)
    // Original dark theme card (oklch(0.205 0 0) = #2B2B2B)
    val cardColor = Color(0xFF2B2B2B)
    val primaryText = Color(0xFFE8E8E8) // oklch(0.922 0 0)
    val foregroundText = Color(0xFFFAFAFA) // oklch(0.985 0 0)
    val accentColor = Color(0xFFE8E8E8)

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var serverData by remember { mutableStateOf<JoinServerData?>(null) }

    // Function to load data via WebSocket
    fun loadData(showFullscreenLoading: Boolean = true) {
        if (showFullscreenLoading) {
            isLoading = true
        }
        errorMessage = null
        SharkordClient.fetchServerData(
            context = context,
            onSuccess = { data ->
                isLoading = false
                errorMessage = null
                serverData = data
            },
            onError = { err ->
                isLoading = false
                errorMessage = err
            }
        )
    }

    // Start connection when screen is displayed
    LaunchedEffect(Unit) {
        loadData(showFullscreenLoading = true)
    }

    // Auto-reconnect in background if disconnected while active
    LaunchedEffect(errorMessage) {
        if (errorMessage != null && serverData != null) {
            // Wait 3 seconds, then try to reconnect silently in the background
            kotlinx.coroutines.delay(3000)
            loadData(showFullscreenLoading = false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = primaryText, modifier = Modifier.size(50.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(id = R.string.connect_loggingInAutomatically),
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            errorMessage != null && serverData == null -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(id = R.string.disconnected_connectionLost),
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage ?: stringResource(id = R.string.disconnected_lostConnectionMessage),
                            color = primaryText,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { loadData(showFullscreenLoading = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryText)
                        ) {
                            Text(stringResource(id = R.string.settings_marketplaceRetry), color = bgColor)
                        }
                    }
                }
            }
            serverData != null -> {
                val data = serverData!!
                
                // Find authenticated user in returned list to confirm correct name
                val currentUser = data.users.find { it.id == data.ownUserId }
                val userName = currentUser?.name ?: stringResource(id = R.string.common_unknownUser)

                var selectedTab by remember { mutableStateOf(0) }
                val tabs = listOf(
                    "💬 " + stringResource(id = R.string.dialogs_textChannelTitle),
                    stringResource(id = R.string.settings_usersTab),
                    stringResource(id = R.string.settings_profileTab)
                )

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (errorMessage != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFEF4444),
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Connection lost. Reconnecting...",
                                color = Color(0xFFEF4444),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    // Sleek TopBar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF22C55E)) // Green active dot
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = data.serverName,
                                    color = foregroundText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                             Text(
                                text = stringResource(id = R.string.settings_marketplaceVerified),
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Mini user badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(cardColor)
                                .border(1.dp, Color.White.copy(alpha=0.1f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = userName,
                                color = foregroundText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Premium Custom Navigation Tabs
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = primaryText,
                        indicator = { tabPositions ->
                            TabRowDefaults.Indicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = primaryText,
                                height = 3.dp
                            )
                        },
                        divider = {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        text = title,
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                },
                                selectedContentColor = foregroundText,
                                unselectedContentColor = Color.Gray
                            )
                        }
                    }

                    // Tab Contents
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when (selectedTab) {
                            0 -> {
                                // TAB 1: CHANNELS (Text & Voice grouped beautifully)
                                val textChannels = data.channels.filter { !it.isVoice }
                                val voiceChannels = data.channels.filter { it.isVoice }

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (textChannels.isNotEmpty()) {
                                        item {
                                             Text(
                                                text = "💬 " + stringResource(id = R.string.dialogs_textChannelTitle).uppercase(),
                                                color = Color.Gray,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp,
                                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                                            )
                                        }
                                        items(textChannels) { channel ->
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = cardColor),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "#",
                                                        color = Color.Gray,
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Text(
                                                        text = channel.name,
                                                        color = primaryText,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (voiceChannels.isNotEmpty()) {
                                        item {
                                            Spacer(modifier = Modifier.height(12.dp))
                                             Text(
                                                text = "🔊 " + stringResource(id = R.string.dialogs_voiceChannelTitle).uppercase(),
                                                color = Color.Gray,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp,
                                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                                            )
                                        }
                                        items(voiceChannels) { channel ->
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = cardColor),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(text = "🔊", fontSize = 16.sp)
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Text(
                                                        text = channel.name,
                                                        color = primaryText,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Spacer(modifier = Modifier.weight(1f))
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(Color.White.copy(alpha = 0.1f))
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = stringResource(id = R.string.dialogs_joinBtn),
                                                            color = primaryText,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            1 -> {
                                // TAB 2: USERS (Online Members Sidebar)
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    item {
                                         Text(
                                             text = stringResource(id = R.string.sidebar_membersHeader, data.users.size),
                                             color = Color.Gray,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp,
                                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                                        )
                                    }
                                    items(data.users) { user ->
                                        val isMe = user.id == data.ownUserId
                                        val firstLetter = user.name.take(1).uppercase()
                                        
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = cardColor),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Beautiful Avatar with online dot indicator
                                                Box(modifier = Modifier.size(36.dp)) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clip(CircleShape)
                                                            .background(Color.DarkGray),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isMe) {
                                                            Image(
                                                                painter = painterResource(id = R.drawable.logo),
                                                                contentDescription = "Avatar",
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                        } else {
                                                            Text(
                                                                text = firstLetter,
                                                                color = Color.White,
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                    // Green Online Dot
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF22C55E))
                                                            .align(Alignment.BottomEnd)
                                                            .border(1.5.dp, bgColor, CircleShape)
                                                    )
                                                }
                                                
                                                Spacer(modifier = Modifier.width(12.dp))
                                                
                                                Text(
                                                    text = user.name,
                                                    color = foregroundText,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )

                                                if (isMe) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(Color.White.copy(alpha = 0.15f))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                         Text(
                                                             text = stringResource(id = R.string.settings_profileTab).uppercase(),
                                                             color = primaryText,
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            2 -> {
                                // TAB 3: PROFILE & SERVER (Profile / Settings Screen)
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // User card
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = cardColor),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(60.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.DarkGray),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Image(
                                                    painter = painterResource(id = R.drawable.logo),
                                                    contentDescription = "User Logo",
                                                    modifier = Modifier.size(40.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column {
                                                Text(
                                                    text = userName,
                                                    color = foregroundText,
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                 Text(
                                                     text = stringResource(id = R.string.settings_userIdLabel) + ": #" + data.ownUserId,
                                                     color = Color.Gray,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }

                                    // Server Details card
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = cardColor),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha=0.1f), RoundedCornerShape(16.dp))
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                 text = stringResource(id = R.string.settings_serverInfoTitle).uppercase(),
                                                 color = Color.Gray,
                                                 fontSize = 10.sp,
                                                 fontWeight = FontWeight.Bold,
                                                 letterSpacing = 1.5.sp
                                             )
                                             Spacer(modifier = Modifier.height(12.dp))
                                             
                                             Text(
                                                 text = stringResource(id = R.string.settings_nameLabel) + ": " + data.serverName,
                                                 color = foregroundText,
                                                 fontSize = 15.sp,
                                                 fontWeight = FontWeight.SemiBold
                                             )
                                             Text(
                                                 text = stringResource(id = R.string.sidebar_server) + " URL: " + (SharkordClient.currentServerUrl ?: ""),
                                                 color = Color.Gray,
                                                fontSize = 13.sp
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF22C55E))
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                 Text(
                                                     text = "WebSocket: " + stringResource(id = R.string.settings_inviteActive),
                                                     color = Color(0xFF22C55E),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    // Disconnect Button
                                    Button(
                                        onClick = {
                                            SharkordClient.activeWebSocket?.close(1000, "Logout")
                                            SharkordClient.currentToken = null
                                            onLogout()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFEF4444).copy(alpha = 0.15f),
                                            contentColor = Color(0xFFEF4444)
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                    ) {
                                        Text(stringResource(id = R.string.sidebar_disconnect), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}
