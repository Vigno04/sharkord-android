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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
    onNavigateToSettings: () -> Unit,
    onNavigateToServerSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
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
                ConnectionErrorScreen(
                    errorMessage = uiState.errorMessage,
                    onBackClick = {
                        viewModel.logout(context)
                        onLogout()
                    },
                    onRetryClick = {
                        viewModel.reconnect(showFullscreenLoading = true)
                    }
                )
            }

            uiState.serverData != null -> {
                val data = uiState.serverData!!

                val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
                val density = LocalDensity.current
                val screenWidthPx = remember(screenWidthDp) { with(density) { screenWidthDp.toPx() } }
                
                // Track dynamic swipe-to-dismiss offset of the Channels List in pixels
                val swipeOffset = remember { Animatable(if (uiState.activePanel == HomePanel.SERVER) 0f else -screenWidthPx) }
                val coroutineScope = rememberCoroutineScope()

                // Programmatic panel transitions
                LaunchedEffect(uiState.activePanel) {
                    if (uiState.activePanel == HomePanel.SERVER) {
                        swipeOffset.animateTo(0f)
                    } else {
                        swipeOffset.animateTo(-screenWidthPx)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. UNDERLAY: Chat Panel (rendered on bottom if a channel is selected)
                    if (uiState.selectedChannelId != null) {
                        val activeChannel = data.channels.find { it.id == uiState.selectedChannelId }
                        val isDm = activeChannel?.isDm == true
                        val otherUser = if (isDm && activeChannel != null) {
                            val parts = activeChannel.name.removePrefix("DM - ").split(":")
                            val otherUserId = parts.firstOrNull { it != data.ownUserId.toString() }?.toIntOrNull()
                            data.users.find { it.id == otherUserId }
                        } else null
                        
                        val displayName = if (isDm) {
                            otherUser?.name ?: activeChannel?.name ?: ""
                        } else {
                            activeChannel?.name ?: ""
                        }
                        
                        ChatPanel(
                            channelId = uiState.selectedChannelId!!,
                            targetMessageId = uiState.selectedMessageId,
                            jumpTrigger = uiState.jumpTrigger,
                            channelName = displayName,
                            isDm = isDm,
                            dmUser = otherUser,
                            users = data.users,
                            roles = data.roles ?: emptyList(),
                            customEmojis = data.emojis ?: emptyList(),
                            onBackClick = {
                                coroutineScope.launch {
                                    swipeOffset.animateTo(0f)
                                    viewModel.setPanel(HomePanel.SERVER)
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(uiState.activePanel) {
                                    if (uiState.activePanel == HomePanel.CHAT) {
                                        detectHorizontalDragGestures { change, dragAmount ->
                                            if (dragAmount > 50) {
                                                viewModel.setPanel(HomePanel.SERVER)
                                            }
                                        }
                                    }
                                }
                        )
                    }

                    // 2. OVERLAY: Server Channels list (rendered on top, offset by swipeOffset)
                    val channelsOffset = with(density) { swipeOffset.value.toDp() }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(x = channelsOffset)
                            .background(bgColor)
                            .pointerInput(screenWidthPx) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        coroutineScope.launch {
                                            if (swipeOffset.value < -screenWidthPx / 3) {
                                                // Complete swipe back to chat panel
                                                swipeOffset.animateTo(-screenWidthPx)
                                                viewModel.setPanel(HomePanel.CHAT)
                                            } else {
                                                // Return to current server panel position
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
                                            val newOffset = (swipeOffset.value + dragAmount).coerceIn(-screenWidthPx, 0f)
                                            swipeOffset.snapTo(newOffset)
                                        }
                                    }
                                )
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
                        val dmChannels = data.channels.filter { channel ->
                            channel.isDm && channel.name.removePrefix("DM - ").split(":").contains(data.ownUserId.toString())
                        }
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
                                    .clickable {
                                        val errorMsg = uiState.errorMessage
                                        if (errorMsg != null) {
                                            clipboardManager.setText(AnnotatedString(errorMsg))
                                            android.widget.Toast.makeText(context, context.getString(R.string.common_errorDetailsCopied), android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
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
                                    foregroundText = foregroundText,
                                    onSearchClick = { viewModel.showSearchSheet() },
                                    onDirectMessagesClick = { viewModel.showMembersSheet() },
                                    onServerClick = { viewModel.showServerSheet() },
                                    isServerSheetOpen = uiState.showServerSheet
                                )
                            }

                            // C. CHANNELS LIST
                            
                            // 1. Render Direct Messages
                            if (dmChannels.isNotEmpty()) {
                                item(key = "dm-header") {
                                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "▼",
                                                    color = Color.Gray,
                                                    fontSize = 10.sp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "DIRECT MESSAGES",
                                                    color = Color.Gray,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 1.sp
                                                )
                                            }
                                        }
                                    }
                                }
                                items(dmChannels, key = { "dm-${it.id}" }) { channel ->
                                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                        val parts = channel.name.removePrefix("DM - ").split(":")
                                        val otherUserId = parts.firstOrNull { it != data.ownUserId.toString() }?.toIntOrNull()
                                        val otherUser = data.users.find { it.id == otherUserId }

                                        DmChannelItem(
                                            channelName = channel.name,
                                            user = otherUser,
                                            isSelected = uiState.selectedChannelId == channel.id,
                                            onSelect = { viewModel.selectChannel(channel.id) },
                                            foregroundText = foregroundText,
                                            primaryText = primaryText
                                        )
                                    }
                                }
                            }

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
                        },
                        onNavigateToSettings = {
                            viewModel.dismissProfileSheet()
                            onNavigateToSettings()
                        },
                        roles = data.roles ?: emptyList()
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
                        onDismissRequest = { viewModel.dismissMembersSheet() },
                        onMessageClick = { userId ->
                            viewModel.dismissMembersSheet()
                            viewModel.openDirectMessage(userId)
                        }
                    )
                }

                // ================= 7. SERVER PROFILE BOTTOM SHEET =================
                var showUnderConstruction by remember { mutableStateOf(false) }

                if (uiState.showServerSheet) {
                    val userRoles = currentUser?.roleIds?.mapNotNull { roleId -> data.roles?.find { it.id == roleId } } ?: emptyList()
                    val hasManageServer = userRoles.any { role ->
                        val p = role.permissions.map { it.uppercase() }
                        p.contains("MANAGE_SETTINGS") || p.contains("MANAGE_ROLES") || p.contains("MANAGE_EMOJIS") || 
                        p.contains("MANAGE_INVITES") || p.contains("MANAGE_USERS") || p.contains("MANAGE_PLUGINS") || 
                        p.contains("MANAGE_STORAGE") || p.contains("MANAGE_UPDATES")
                    } || data.ownUserId == 1

                    ServerProfileBottomSheet(
                        serverName = data.serverName,
                        serverDescription = data.publicSettings?.description,
                        hasManageServer = hasManageServer,
                        bgColor = bgColor,
                        cardColor = cardColor,
                        primaryText = primaryText,
                        foregroundText = foregroundText,
                        onDismissRequest = { viewModel.dismissServerSheet() },
                        onShowMembers = {
                            viewModel.dismissServerSheet()
                            viewModel.showMembersSheet()
                        },
                        onServerOptionsClick = {
                            viewModel.dismissServerSheet()
                            onNavigateToServerSettings()
                        },
                        onDisconnectClick = {
                            viewModel.logout(context)
                            viewModel.dismissServerSheet()
                            onLogout()
                        }
                    )
                }

                }
                
                // ================= 8. SEARCH PANEL =================
                androidx.compose.animation.AnimatedVisibility(
                    visible = uiState.showSearchSheet,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it },
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it }
                ) {
                    SearchPanel(
                        searchQuery = uiState.searchQuery,
                        isSearching = uiState.isSearching,
                        searchResults = uiState.searchResults,
                        users = data.users,
                        onQueryChange = { viewModel.setSearchQuery(it) },
                        onSearchTrigger = { viewModel.performSearch() },
                        onDismissRequest = { viewModel.dismissSearchSheet() },
                        onResultClick = { channelId, messageId ->
                            viewModel.dismissSearchSheet()
                            viewModel.selectChannel(channelId, messageId)
                        },
                        bgColor = bgColor,
                        cardColor = cardColor,
                        primaryText = primaryText,
                        foregroundText = foregroundText
                    )
                }
            } // closes uiState.serverData != null
        } // closes when
    } // closes Box(78)
} // closes HomeScreen
}

