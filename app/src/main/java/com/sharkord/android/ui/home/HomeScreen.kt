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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
    onNavigateToChannelSettings: (channelId: Int) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    // Collect uiState
    val uiState by viewModel.uiState.collectAsState()

    // Initial connection
    LaunchedEffect(Unit) {
        viewModel.connect()
    }

    // Theme colors
    val colors = com.sharkord.android.ui.theme.LocalSharkordColors.current
    val bgColor = colors.bgColor
    val cardColor = colors.cardColor
    val primaryText = colors.primaryText
    val foregroundText = colors.foregroundText
    val accentColor = colors.accentColor

    // Main container
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
                    // Chat Panel
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
                        
                        if (activeChannel?.isVoice == true) {
                            val channelUsers = data.voiceMap?.get(uiState.selectedChannelId!!.toString())?.users ?: emptyMap()
                            val currentState = channelUsers[data.ownUserId.toString()]
                            val isMuted = currentState?.micMuted ?: true
                            val isDeafened = currentState?.soundMuted ?: true

                            val permissionLauncher = rememberLauncherForActivityResult(
                                ActivityResultContracts.RequestPermission()
                            ) { isGranted: Boolean ->
                                if (isGranted) {
                                    viewModel.joinVoiceChannel(uiState.selectedChannelId!!)
                                }
                            }

                            VoicePanel(
                                channelName = displayName,
                                voiceUsers = if (data.voiceMap != null) {
                                    channelUsers.mapNotNull { (userIdStr, state) ->
                                        val user = data.users.find { it.id.toString() == userIdStr }
                                        if (user != null) {
                                            val isSpeaking = if (user.id == data.ownUserId) {
                                                uiState.activeSpeakers.contains("local")
                                            } else {
                                                uiState.activeSpeakers.contains(user.id.toString())
                                            }
                                            VoiceUserDisplay(user, state, isSpeaking)
                                        } else null
                                    }
                                } else emptyList(),
                                isConnected = uiState.activeVoiceChannelId == uiState.selectedChannelId,
                                isMuted = isMuted,
                                isDeafened = isDeafened,
                                onDisconnectClick = { viewModel.leaveVoiceChannel() },
                                onConnectClick = { 
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        viewModel.joinVoiceChannel(uiState.selectedChannelId!!)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                onToggleMicClick = { _ -> 
                                    viewModel.toggleMic(uiState.selectedChannelId!!, isMuted, isDeafened) 
                                },
                                onToggleDeafenClick = { _ ->
                                    viewModel.toggleDeafen(uiState.selectedChannelId!!, isMuted, isDeafened)
                                },
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
                        } else {
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
                    }

                    // Server Channels list
                    val channelsOffset = with(density) { swipeOffset.value.toDp() }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(x = channelsOffset)
                            .background(bgColor)
                            .drawBehind {
                                val strokeWidth = 1.dp.toPx()
                                val x = size.width - strokeWidth / 2
                                drawLine(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = strokeWidth
                                )
                            }
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

                        val userRoles = currentUser?.roleIds?.mapNotNull { roleId -> data.roles?.find { it.id == roleId } } ?: emptyList()
                        val hasManageChannels = userRoles.any { role ->
                            val p = role.permissions.map { it.uppercase() }
                            p.contains("MANAGE_CHANNELS") || p.contains("MANAGE_SETTINGS")
                        } || data.ownUserId == 1

                        // Channels Grouping
                        val uncategorizedText =
                            data.channels.filter { it.categoryId == null && !it.isVoice && !it.isDm }
                        val uncategorizedVoice =
                            data.channels.filter { it.categoryId == null && it.isVoice && !it.isDm }
                        val dmChannels = data.channels.filter { channel ->
                            if (channel.isDm && channel.name.removePrefix("DM - ").split(":").contains(data.ownUserId.toString())) {
                                val parts = channel.name.removePrefix("DM - ").split(":")
                                val otherUserId = parts.firstOrNull { it != data.ownUserId.toString() }?.toIntOrNull()
                                data.users.any { it.id == otherUserId }
                            } else {
                                false
                            }
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

                        // Main Scrollable Content
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (uiState.isDmsListOpen) {
                                // DM LIST VIEW
                                item(key = "dm-header") {
                                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable { viewModel.closeDmsList() }
                                                    .padding(4.dp)
                                            ) {
                                                Text(
                                                    text = "◀",
                                                    color = Color.Gray,
                                                    fontSize = 14.sp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "DIRECT MESSAGES",
                                                    color = Color.Gray,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 1.sp
                                                )
                                            }
                                            androidx.compose.material3.IconButton(
                                                onClick = { viewModel.showMembersSheet(true) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = "New DM",
                                                    tint = Color.Gray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                if (dmChannels.isNotEmpty()) {
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
                                                primaryText = primaryText,
                                                unreadCount = uiState.readStates[channel.id] ?: 0
                                            )
                                        }
                                    }
                                } else {
                                    item {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("No Direct Messages yet.", color = Color.Gray, fontSize = 14.sp)
                                        }
                                    }
                                }
                            } else {
                            // Server Banner
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
                                        // Fallback logo
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

                            // Server Header
                            item {
                                val totalUnreadDMs = dmChannels.sumOf { uiState.readStates[it.id] ?: 0 }

                                ServerHeader(
                                    serverName = data.serverName,
                                    memberCount = data.users.size,
                                    cardColor = cardColor,
                                    foregroundText = foregroundText,
                                    onSearchClick = { viewModel.showSearchSheet() },
                                    onDirectMessagesClick = { viewModel.openDmsList() },
                                    onServerClick = { viewModel.showServerSheet() },
                                    isServerSheetOpen = uiState.showServerSheet,
                                    totalUnreadDMs = totalUnreadDMs
                                )
                            }

                            // Uncategorized Channels
                            if (uncategorizedText.isNotEmpty()) {
                                items(uncategorizedText) { channel ->
                                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    ChannelItem(
                                            channel = channel,
                                            isSelected = uiState.selectedChannelId == channel.id,
                                            onSelect = {
                                                if (!channel.isVoice) viewModel.selectChannel(channel.id)
                                            },
                                            canManage = hasManageChannels,
                                            onEditClick = { onNavigateToChannelSettings(channel.id) },
                                            onDeleteClick = { viewModel.showDeleteChannelDialog(channel.id) },
                                            foregroundText = foregroundText,
                                            primaryText = primaryText,
                                            unreadCount = uiState.readStates[channel.id] ?: 0
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
                                            onSelect = {
                                                viewModel.selectChannel(channel.id)
                                            },
                                            canManage = hasManageChannels,
                                            onEditClick = { onNavigateToChannelSettings(channel.id) },
                                            onDeleteClick = { viewModel.showDeleteChannelDialog(channel.id) },
                                            foregroundText = foregroundText,
                                            primaryText = primaryText,
                                            unreadCount = uiState.readStates[channel.id] ?: 0,
                                            voiceUsers = if (data.voiceMap != null) {
                                                val channelUsers = data.voiceMap[channel.id.toString()]?.users ?: emptyMap()
                                                channelUsers.mapNotNull { (userIdStr, state) ->
                                                    val user = data.users.find { it.id.toString() == userIdStr }
                                                    if (user != null) {
                                                        val isSpeaking = if (user.id == data.ownUserId) {
                                                            uiState.activeSpeakers.contains("local")
                                                        } else {
                                                            uiState.activeSpeakers.contains(user.id.toString())
                                                        }
                                                        VoiceUserDisplay(user, state, isSpeaking)
                                                    } else null
                                                }
                                            } else emptyList()
                                        )
                                    }
                                }
                            }

                            // Grouped Categories
                            categoriesList.forEach { category ->
                                val catChannels =
                                    data.channels.filter { it.categoryId == category.id && !it.isDm }
                                        .sortedWith(compareBy({ it.position ?: 0 }, { it.id }))
                                if (catChannels.isNotEmpty() || hasManageChannels) {
                                    val isCollapsed =
                                        uiState.collapsedCategories.contains(category.id)

                                    item(key = "cat-${category.id}") {
                                        CategorySection(
                                            category = category,
                                            channels = catChannels,
                                            isCollapsed = isCollapsed,
                                            hasManageChannels = hasManageChannels,
                                            selectedChannelId = uiState.selectedChannelId,
                                            onToggleCategory = { viewModel.toggleCategory(category.id) },
                                            onAddChannelClick = { viewModel.showAddChannelDialog(category.id) },
                                            onChannelSelect = { channelId ->
                                                viewModel.selectChannel(channelId)
                                            },
                                            onChannelLongPress = { channelId -> viewModel.selectChannel(channelId, navigateToChat = false) },
                                            onChannelEdit = { channelId -> onNavigateToChannelSettings(channelId) },
                                            onChannelDelete = { channelId -> viewModel.showDeleteChannelDialog(channelId) },
                                            onReorderChannels = { newChannelIds -> viewModel.reorderChannels(category.id, newChannelIds) },
                                            foregroundText = foregroundText,
                                            primaryText = primaryText,
                                            readStates = uiState.readStates,
                                            voiceMap = data.voiceMap,
                                            users = data.users,
                                            ownUserId = data.ownUserId,
                                            activeSpeakers = uiState.activeSpeakers
                                        )
                                    }
                                }
                            }
                            }

                            // Bottom spacer
                            item {
                                Spacer(modifier = Modifier.height(96.dp))
                            }
                        }
                    }

                    // Bottom Profile Bar
                    BottomProfileBar(
                        currentUser = currentUser,
                        userName = userName,
                        foregroundText = foregroundText,
                        onProfileClick = { viewModel.showProfileSheet() },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // Profile Bottom Sheet
                if (uiState.showProfileSheet) {
                    ProfileBottomSheet(
                        currentUser = currentUser,
                        userName = userName,
                        ownUserId = data.ownUserId,
                        serverName = data.serverName,
                        serverId = data.serverId,
                        memberCount = data.users.size,
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

                // Members Bottom Sheet
                if (uiState.showMembersSheet) {
                    val usersToShow = if (uiState.membersSheetFilterDms) {
                        val existingDmUserIds = data.channels.filter { it.isDm }.mapNotNull { ch ->
                            val parts = ch.name.removePrefix("DM - ").split(":")
                            parts.firstOrNull { it != data.ownUserId.toString() }?.toIntOrNull()
                        }.toSet()
                        data.users.filter { user ->
                            user.id != data.ownUserId && user.id !in existingDmUserIds && !user.isDeleted
                        }
                    } else {
                        data.users
                    }

                    MembersBottomSheet(
                        users = usersToShow,
                        ownUserId = data.ownUserId,
                        onDismissRequest = { viewModel.dismissMembersSheet() },
                        onMessageClick = { userId ->
                            viewModel.dismissMembersSheet()
                            viewModel.openDirectMessage(userId)
                        }
                    )
                }

                // Server Profile Bottom Sheet
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
                        onDismissRequest = { viewModel.dismissServerSheet() },
                        onShowMembers = {
                            viewModel.dismissServerSheet()
                            viewModel.showMembersSheet()
                        },
                        onServerOptionsClick = {
                            viewModel.dismissServerSheet()
                            onNavigateToServerSettings()
                        },
                        onAddCategoryClick = {
                            viewModel.dismissServerSheet()
                            viewModel.showAddCategoryDialog()
                        },
                        onDisconnectClick = {
                            viewModel.logout(context)
                            viewModel.dismissServerSheet()
                            onLogout()
                        }
                    )
                }

                }
                
                // Add Channel Dialog
                if (uiState.showAddChannelDialog) {
                    AddChannelDialog(
                        onDismissRequest = { viewModel.dismissAddChannelDialog() },
                        onConfirm = { name, type -> viewModel.createChannel(name, type, uiState.addChannelCategoryId) },
                        bgColor = bgColor,
                        cardColor = cardColor,
                        primaryText = primaryText,
                        foregroundText = foregroundText
                    )
                }

                // Add Category Dialog
                if (uiState.showAddCategoryDialog) {
                    AddCategoryDialog(
                        onDismissRequest = { viewModel.dismissAddCategoryDialog() },
                        onConfirm = { name -> viewModel.createCategory(name) },
                        bgColor = bgColor,
                        cardColor = cardColor,
                        primaryText = primaryText,
                        foregroundText = foregroundText
                    )
                }

                // Delete Channel Dialog
                if (uiState.showDeleteChannelDialogForId != null) {
                    val channelId = uiState.showDeleteChannelDialogForId!!
                    val channelToDelete = data.channels.find { it.id == channelId }
                    if (channelToDelete != null) {
                        AlertDialog(
                            onDismissRequest = { viewModel.dismissDeleteChannelDialog() },
                            title = { Text(stringResource(id = R.string.settings_deleteChannelTitle)) },
                            text = { Text(stringResource(id = R.string.settings_deleteChannelMsg)) },
                            confirmButton = {
                                TextButton(onClick = { viewModel.deleteChannel(channelId) }) {
                                    Text(stringResource(id = R.string.settings_deleteLabel), color = Color.Red)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { viewModel.dismissDeleteChannelDialog() }) {
                                    Text(stringResource(id = R.string.common_cancel), color = primaryText)
                                }
                            },
                            containerColor = cardColor,
                            titleContentColor = foregroundText,
                            textContentColor = primaryText
                        )
                    }
                }

                // Search Panel
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

