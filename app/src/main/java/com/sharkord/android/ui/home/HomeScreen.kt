package com.sharkord.android.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sharkord.android.R
import com.sharkord.android.data.model.Channel
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.ui.components.rememberAsyncImagePainter
import com.sharkord.android.ui.components.rememberExtendedImageState
import com.sharkord.android.ui.home.components.*
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    
    // We collect the uiState here from our ViewModel as a State object,
    // so anytime something in the database/connection updates, our screen recomposes/redraws automatically!
    val uiState by viewModel.uiState.collectAsState()

    // When the screen opens for the first time, we tell the ViewModel to connect to our server!
    LaunchedEffect(Unit) {
        viewModel.connect()
    }

    // Set up some nice dark theme colors matching Discord's aesthetic:
    val bgColor = Color(0xFF1C1C1C) // Deep dark gray background
    val cardColor = Color(0xFF2B2B2B) // Slightly lighter card color for contrast
    val primaryText = Color(0xFFE8E8E8) // Warm light gray for normal text
    val foregroundText = Color(0xFFFAFAFA) // Pure bright white for headers
    val accentColor = Color(0xFFE8E8E8)

    // A Box covers the whole screen. We put the bgColor on it.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.TopCenter
    ) {

        when {
            uiState.isLoading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
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

            uiState.errorMessage != null && uiState.serverData == null -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
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
                                text = uiState.errorMessage
                                    ?: stringResource(id = R.string.disconnected_lostConnectionMessage),
                                color = primaryText,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { viewModel.reconnect(showFullscreenLoading = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = primaryText)
                            ) {
                                Text(
                                    stringResource(id = R.string.settings_marketplaceRetry),
                                    color = bgColor
                                )
                            }
                        }
                    }
                }
            }

            uiState.serverData != null -> {
                val data = uiState.serverData!!

                val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
                val density = LocalDensity.current
                val screenWidthPx = remember(screenWidthDp) { with(density) { screenWidthDp.toPx() } }
                
                // Track dynamic swipe-to-dismiss offset of the ChatPanel in pixels
                val swipeOffset = remember { Animatable(if (uiState.activePanel == HomePanel.CHAT) 0f else screenWidthPx) }
                val coroutineScope = rememberCoroutineScope()

                // Programmatic panel transitions
                LaunchedEffect(uiState.activePanel) {
                    if (uiState.activePanel == HomePanel.CHAT) {
                        swipeOffset.animateTo(0f)
                    } else {
                        swipeOffset.animateTo(screenWidthPx)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. UNDERLAY: Server Channels list (always rendered, so dragging chat right reveals it)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(uiState.activePanel, uiState.selectedChannelId) {
                                if (uiState.activePanel == HomePanel.SERVER && uiState.selectedChannelId != null) {
                                    detectHorizontalDragGestures { change, dragAmount ->
                                        if (dragAmount < -50) {
                                            viewModel.setPanel(HomePanel.CHAT)
                                        }
                                    }
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Find authenticated user in returned list to confirm correct name
                        val currentUser = data.users.find { it.id == data.ownUserId }
                        val userName = currentUser?.name ?: stringResource(id = R.string.common_unknownUser)

                        // Channels Grouping
                        val uncategorizedText =
                            data.channels.filter { it.categoryId == null && !it.isVoice && !it.isDm }
                        val uncategorizedVoice =
                            data.channels.filter { it.categoryId == null && it.isVoice && !it.isDm }
                        val categoriesList = data.categories?.sortedBy { it.position } ?: emptyList()

                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (uiState.errorMessage != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
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
                                    text = uiState.errorMessage!!,
                                    color = Color(0xFFEF4444),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // ================= 1. SINGLE SCROLLABLE LAZYCOLUMN =================
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // A. SERVER BANNER
                            item {
                                val logoState =
                                    rememberExtendedImageState(SharkordClient.currentServerLogoUrl)
                                
                                val bannerBrush = if (logoState.painter != null) {
                                    Brush.horizontalGradient(
                                        colors = listOf(logoState.leftColor, logoState.rightColor)
                                    )
                                } else {
                                    Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF2C3E50), Color(0xFF1A252F)) // Sleek premium dark gradient
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .background(bannerBrush),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (logoState.painter != null) {
                                        // Foreground custom logo
                                        Image(
                                            painter = logoState.painter,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        // Fallback premium logo placeholder
                                        val fallbackPainter = painterResource(id = R.drawable.logo)
                                        Image(
                                            painter = fallbackPainter,
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                        )
                                    }
                                }
                            }

                            // B. SERVER HEADER, SEARCH, DM NAVIGATION
                            item {
                                ServerHeader(
                                    serverName = data.serverName,
                                    memberCount = data.users.size,
                                    cardColor = cardColor,
                                    foregroundText = foregroundText
                                )
                            }

                            // C. CHANNELS LIST
                            // A. Render Uncategorized Channels (if any)
                            if (uncategorizedText.isNotEmpty()) {
                                items(uncategorizedText) { channel ->
                                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    ChannelItem(
                                            channel = channel,
                                            isSelected = uiState.selectedChannelId == channel.id,
                                            onSelect = {
                                                if (!channel.isVoice) viewModel.selectChannel(channel.id)
                                            },
                                            foregroundText = foregroundText,
                                            primaryText = primaryText
                                        )
                                    }
                                }
                            }
                            if (uncategorizedVoice.isNotEmpty()) {
                                items(uncategorizedVoice) { channel ->
                                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    ChannelItem(
                                            channel = channel,
                                            isSelected = uiState.selectedChannelId == channel.id,
                                            onSelect = { /* Voice channel — no text chat */ },
                                            foregroundText = foregroundText,
                                            primaryText = primaryText
                                        )
                                    }
                                }
                            }

                            // B. Render Grouped Categories & Channels
                            categoriesList.forEach { category ->
                                val catChannels =
                                    data.channels.filter { it.categoryId == category.id && !it.isDm }
                                if (catChannels.isNotEmpty()) {
                                    val isCollapsed =
                                        uiState.collapsedCategories.contains(category.id)

                                    item(key = "cat-${category.id}") {
                                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable { viewModel.toggleCategory(category.id) }
                                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = if (isCollapsed) "▶" else "▼",
                                                        color = Color.Gray,
                                                        fontSize = 10.sp
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = category.name.uppercase(),
                                                        color = Color.Gray,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 1.sp
                                                    )
                                                }
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = stringResource(id = R.string.common_add),
                                                    tint = Color.Gray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (!isCollapsed) {
                                        items(catChannels, key = { "chan-${it.id}" }) { channel ->
                                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                                ChannelItem(
                                                    channel = channel,
                                                    isSelected = uiState.selectedChannelId == channel.id,
                                                    onSelect = {
                                                        if (!channel.isVoice) viewModel.selectChannel(channel.id)
                                                    },
                                                    foregroundText = foregroundText,
                                                    primaryText = primaryText
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // D. BOTTOM SPACER (so text can scroll above the floating profile bar)
                            item {
                                Spacer(modifier = Modifier.height(96.dp))
                            }
                        }
                    }

                    // ================= 4. DISCORD BOTTOM PROFILE BAR =================
                    BottomProfileBar(
                        currentUser = currentUser,
                        userName = userName,
                        foregroundText = foregroundText,
                        onProfileClick = { viewModel.showProfileSheet() },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // ================= 5. PROFILE & SETTINGS BOTTOM SHEET =================
                if (uiState.showProfileSheet) {
                    ProfileBottomSheet(
                        currentUser = currentUser,
                        userName = userName,
                        ownUserId = data.ownUserId,
                        serverName = data.serverName,
                        serverId = data.serverId,
                        memberCount = data.users.size,
                        bgColor = bgColor,
                        cardColor = cardColor,
                        primaryText = primaryText,
                        foregroundText = foregroundText,
                        onDismissRequest = { viewModel.dismissProfileSheet() },
                        onShowMembers = {
                            viewModel.dismissProfileSheet()
                            viewModel.showMembersSheet()
                        },
                        onLogoutClick = {
                            viewModel.logout(context)
                            viewModel.dismissProfileSheet()
                            onLogout()
                        }
                    )
                }

                // ================= 6. MEMBERS DIRECTORY BOTTOM SHEET =================
                if (uiState.showMembersSheet) {
                    MembersBottomSheet(
                        users = data.users,
                        ownUserId = data.ownUserId,
                        cardColor = cardColor,
                        primaryText = primaryText,
                        foregroundText = foregroundText,
                        onDismissRequest = { viewModel.dismissMembersSheet() }
                    )
                }
                }

                // 2. OVERLAY: Chat Panel (rendered on top if a channel is selected, offset by swipeOffset)
                if (uiState.selectedChannelId != null) {
                    val activeChannel = data.channels.find { it.id == uiState.selectedChannelId }
                    val chatOffset = with(density) { swipeOffset.value.toDp() }

                    ChatPanel(
                        channelId = uiState.selectedChannelId!!,
                        channelName = activeChannel?.name ?: "",
                        users = data.users,
                        roles = data.roles ?: emptyList(),
                        customEmojis = data.emojis ?: emptyList(),
                        onBackClick = {
                            coroutineScope.launch {
                                swipeOffset.animateTo(screenWidthPx)
                                viewModel.setPanel(HomePanel.SERVER)
                            }
                        },
                        modifier = Modifier
                            .offset(x = chatOffset)
                            .pointerInput(screenWidthPx) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        coroutineScope.launch {
                                            if (swipeOffset.value > screenWidthPx / 3) {
                                                // Complete swipe back to server channels panel
                                                swipeOffset.animateTo(screenWidthPx)
                                                viewModel.setPanel(HomePanel.SERVER)
                                            } else {
                                                // Return to current chat panel position
                                                swipeOffset.animateTo(0f)
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        coroutineScope.launch {
                                            swipeOffset.animateTo(0f)
                                        }
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        coroutineScope.launch {
                                            val newOffset = (swipeOffset.value + dragAmount).coerceIn(0f, screenWidthPx)
                                            swipeOffset.snapTo(newOffset)
                                        }
                                    }
                                )
                            }
                    )
                }
                }
            }
        }
    }
}

