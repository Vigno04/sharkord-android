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
import androidx.activity.compose.BackHandler
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
    
    // collect uiState
    val uiState by viewModel.uiState.collectAsState()

    // initial connection
    LaunchedEffect(Unit) {
        viewModel.connect()
    }

    // theme colors
    val colors = com.sharkord.android.ui.theme.LocalSharkordColors.current
    val bgColor = colors.bgColor
    val cardColor = colors.cardColor
    val primaryText = colors.primaryText
    val foregroundText = colors.foregroundText
    val accentColor = colors.accentColor

    // main container
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
                
                // track dynamic swipe-to-dismiss offset of the Channels List in pixels
                val swipeOffset = remember { Animatable(if (uiState.activePanel == HomePanel.SERVER) 0f else -screenWidthPx) }
                val voiceSwipeOffset = remember { Animatable(if (uiState.isViewingVoiceChat) -screenWidthPx else 0f) }
                val coroutineScope = rememberCoroutineScope()
                val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

                // programmatic panel transitions
                LaunchedEffect(uiState.activePanel) {
                    if (uiState.activePanel == HomePanel.SERVER) {
                        keyboardController?.hide()
                        swipeOffset.animateTo(0f)
                    } else {
                        swipeOffset.animateTo(-screenWidthPx)
                    }
                }

                LaunchedEffect(uiState.isViewingVoiceChat) {
                    if (uiState.isViewingVoiceChat) {
                        voiceSwipeOffset.animateTo(-screenWidthPx)
                    } else {
                        voiceSwipeOffset.animateTo(0f)
                    }
                }

                // handle screen orientation/width changes
                LaunchedEffect(screenWidthPx) {
                    if (uiState.activePanel == HomePanel.SERVER) {
                        swipeOffset.snapTo(0f)
                    } else {
                        swipeOffset.snapTo(-screenWidthPx)
                    }
                    
                    if (uiState.isViewingVoiceChat) {
                        voiceSwipeOffset.snapTo(-screenWidthPx)
                    } else {
                        voiceSwipeOffset.snapTo(0f)
                    }
                }

                // close DM list when pressing back button
                BackHandler(enabled = uiState.activePanel == HomePanel.SERVER && uiState.isDmsListOpen) {
                    viewModel.closeDmsList()
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // chat Panel
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
                                ActivityResultContracts.RequestMultiplePermissions()
                            ) { results ->
                                if (results[Manifest.permission.RECORD_AUDIO] == true) {
                                    viewModel.joinVoiceChannel(uiState.selectedChannelId!!, context, displayName)
                                }
                            }
                            
                            val cameraPermissionLauncher = rememberLauncherForActivityResult(
                                ActivityResultContracts.RequestMultiplePermissions()
                            ) { results ->
                                if (results[Manifest.permission.CAMERA] == true) {
                                    viewModel.toggleCamera(context)
                                }
                            }

                            Box(modifier = Modifier.fillMaxSize()) {
                                // chat Panel (underneath)
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
                                    isActive = uiState.activePanel == HomePanel.CHAT && uiState.isViewingVoiceChat,
                                    onBackClick = {
                                        viewModel.setViewingVoiceChat(false)
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(uiState.activePanel) {
                                            if (uiState.activePanel == HomePanel.CHAT) {
                                                detectHorizontalDragGestures(
                                                    onDragEnd = {
                                                        coroutineScope.launch {
                                                            if (voiceSwipeOffset.value > -screenWidthPx * 2 / 3) {
                                                                voiceSwipeOffset.animateTo(0f)
                                                                viewModel.setViewingVoiceChat(false)
                                                            } else {
                                                                voiceSwipeOffset.animateTo(-screenWidthPx)
                                                            }
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        coroutineScope.launch {
                                                            voiceSwipeOffset.animateTo(-screenWidthPx)
                                                        }
                                                    },
                                                    onHorizontalDrag = { change, dragAmount ->
                                                        change.consume()
                                                        coroutineScope.launch {
                                                            if (dragAmount > 0 || voiceSwipeOffset.value > -screenWidthPx) {
                                                                val newOffset = (voiceSwipeOffset.value + dragAmount).coerceIn(-screenWidthPx, 0f)
                                                                voiceSwipeOffset.snapTo(newOffset)
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                )

                                // voice Panel (sliding on top)
                                val vOffset = with(density) { voiceSwipeOffset.value.toDp() }
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
                                    isConnectingToVoice = uiState.isConnectingToVoice,
                                    isMuted = isMuted,
                                    isDeafened = isDeafened,
                                    cameraEnabled = uiState.cameraEnabled,
                                    isScreenSharing = uiState.isScreenSharing,
                                    localVideoTrack = uiState.localVideoTrack,
                                    remoteVideoTracks = uiState.remoteVideoTracks,
                                    eglBaseContext = uiState.eglBaseContext,
                                    ownUserId = data.ownUserId,
                                    onDisconnectClick = { viewModel.leaveVoiceChannel(context) },
                                    onConnectClick = { 
                                        val neededPermissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                            neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        val toRequest = neededPermissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
                                        
                                        if (toRequest.isEmpty()) {
                                            viewModel.joinVoiceChannel(uiState.selectedChannelId!!, context, displayName)
                                        } else {
                                            permissionLauncher.launch(toRequest.toTypedArray())
                                        }
                                    },
                                    onToggleMicClick = { _ -> 
                                        viewModel.toggleMic(uiState.selectedChannelId!!, isMuted, isDeafened) 
                                    },
                                    onToggleDeafenClick = { _ ->
                                        viewModel.toggleDeafen(uiState.selectedChannelId!!, isMuted, isDeafened)
                                    },
                                    onToggleCameraClick = {
                                        val neededPermissions = mutableListOf(Manifest.permission.CAMERA)
                                        val toRequest = neededPermissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
                                        
                                        if (toRequest.isEmpty()) {
                                            viewModel.toggleCamera(context)
                                        } else {
                                            cameraPermissionLauncher.launch(toRequest.toTypedArray())
                                        }
                                    },
                                    onToggleScreenShareClick = { enabled, intent ->
                                        viewModel.toggleScreenShare(context, intent, enabled)
                                    },
                                    onSwitchCameraClick = {
                                        viewModel.switchCamera(context)
                                    },
                                    onOpenChatClick = {
                                        viewModel.setViewingVoiceChat(true)
                                    },
                                    onBackClick = {
                                        coroutineScope.launch {
                                            swipeOffset.animateTo(0f)
                                            viewModel.setPanel(HomePanel.SERVER)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .offset(x = vOffset)
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
                                        .pointerInput(uiState.activePanel) {
                                            if (uiState.activePanel == HomePanel.CHAT) {
                                                detectHorizontalDragGestures(
                                                    onDragEnd = {
                                                        coroutineScope.launch {
                                                            if (swipeOffset.value > -screenWidthPx * 2 / 3 && swipeOffset.value != -screenWidthPx) {
                                                                swipeOffset.animateTo(0f)
                                                                viewModel.setPanel(HomePanel.SERVER)
                                                            } else if (swipeOffset.value > -screenWidthPx) {
                                                                swipeOffset.animateTo(-screenWidthPx)
                                                            } else if (voiceSwipeOffset.value < -screenWidthPx / 3 && voiceSwipeOffset.value != 0f) {
                                                                voiceSwipeOffset.animateTo(-screenWidthPx)
                                                                viewModel.setViewingVoiceChat(true)
                                                            } else if (voiceSwipeOffset.value < 0f) {
                                                                voiceSwipeOffset.animateTo(0f)
                                                            }
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        coroutineScope.launch {
                                                            swipeOffset.animateTo(-screenWidthPx)
                                                            voiceSwipeOffset.animateTo(0f)
                                                        }
                                                    },
                                                    onHorizontalDrag = { change, dragAmount ->
                                                        change.consume()
                                                        coroutineScope.launch {
                                                            if (swipeOffset.value > -screenWidthPx) {
                                                                val newOffset = (swipeOffset.value + dragAmount).coerceIn(-screenWidthPx, 0f)
                                                                swipeOffset.snapTo(newOffset)
                                                            } else if (voiceSwipeOffset.value < 0f) {
                                                                val newOffset = (voiceSwipeOffset.value + dragAmount).coerceIn(-screenWidthPx, 0f)
                                                                voiceSwipeOffset.snapTo(newOffset)
                                                            } else {
                                                                if (dragAmount > 0) {
                                                                    val newOffset = (swipeOffset.value + dragAmount).coerceIn(-screenWidthPx, 0f)
                                                                    swipeOffset.snapTo(newOffset)
                                                                } else {
                                                                    val newOffset = (voiceSwipeOffset.value + dragAmount).coerceIn(-screenWidthPx, 0f)
                                                                    voiceSwipeOffset.snapTo(newOffset)
                                                                }
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                )
                            }
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
                                isActive = uiState.activePanel == HomePanel.CHAT,
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
                                            detectHorizontalDragGestures(
                                                onDragEnd = {
                                                    coroutineScope.launch {
                                                        if (swipeOffset.value > -screenWidthPx * 2 / 3) {
                                                            swipeOffset.animateTo(0f)
                                                            viewModel.setPanel(HomePanel.SERVER)
                                                        } else {
                                                            swipeOffset.animateTo(-screenWidthPx)
                                                        }
                                                    }
                                                },
                                                onDragCancel = {
                                                    coroutineScope.launch {
                                                        swipeOffset.animateTo(-screenWidthPx)
                                                    }
                                                },
                                                onHorizontalDrag = { change, dragAmount ->
                                                    change.consume()
                                                    coroutineScope.launch {
                                                        if (dragAmount > 0 || swipeOffset.value > -screenWidthPx) {
                                                            val newOffset = (swipeOffset.value + dragAmount).coerceIn(-screenWidthPx, 0f)
                                                            swipeOffset.snapTo(newOffset)
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                            )
                        }
                    }

                    // server Channels list
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
                                                // complete swipe back to chat panel
                                                swipeOffset.animateTo(-screenWidthPx)
                                                viewModel.setPanel(HomePanel.CHAT)
                                            } else {
                                                // return to current server panel position
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
                        // find authenticated user in returned list to confirm correct name
                        val currentUser = data.users.find { it.id == data.ownUserId }
                        val userName = currentUser?.name ?: stringResource(id = R.string.common_unknownUser)

                        val userRoles = currentUser?.roleIds?.mapNotNull { roleId -> data.roles?.find { it.id == roleId } } ?: emptyList()
                        val hasManageChannels = userRoles.any { role ->
                            val p = role.permissions.map { it.uppercase() }
                            p.contains("MANAGE_CHANNELS") || p.contains("MANAGE_SETTINGS")
                        } || data.ownUserId == 1

                        // channels Grouping
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

                        // main Scrollable Content
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
                            // server Banner
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
                                        // foreground custom logo
                                        Image(
                                            painter = logoState.painter,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        // fallback logo
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

                            // server Header
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

                            // uncategorized Channels
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

                            // grouped Categories
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

                            // bottom spacer
                            item {
                                Spacer(modifier = Modifier.height(96.dp))
                            }
                        }
                    }

                    // bottom Profile Bar
                    BottomProfileBar(
                        currentUser = currentUser,
                        userName = userName,
                        foregroundText = foregroundText,
                        onProfileClick = { viewModel.showProfileSheet() },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // profile Bottom Sheet
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

                // members Bottom Sheet
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

                // server Profile Bottom Sheet
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
                
                // add Channel Dialog
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

                // add Category Dialog
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

                // delete Channel Dialog
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

                // search Panel
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

