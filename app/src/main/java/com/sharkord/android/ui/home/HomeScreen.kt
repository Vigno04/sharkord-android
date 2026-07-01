package com.sharkord.android.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalClipboard
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sharkord.android.R
import com.sharkord.android.data.model.Channel
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.ui.components.rememberAsyncImagePainter
import com.sharkord.android.ui.components.rememberExtendedImageState
import com.sharkord.android.ui.home.components.*
import com.sharkord.android.ui.theme.SharkordTheme
import kotlinx.coroutines.launch

/** Tolerance in px used to detect when the voice panel is fully covering the chat pane. */
private const val VOICE_FULLSCREEN_THRESHOLD_PX = 10f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToServerSettings: () -> Unit,
    onNavigateToChannelSettings: (channelId: Int) -> Unit,
    viewModel: HomeViewModel = viewModel(),
    voiceViewModel: com.sharkord.android.ui.voice.VoiceViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    
    // collect uiState
    val uiState by viewModel.uiState.collectAsState()
    val voiceUiState by voiceViewModel.uiState.collectAsState()

    // initial connection
    LaunchedEffect(Unit) {
        viewModel.connect()
    }

    // theme colors
    val colors = SharkordTheme.colors
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
                        color = SharkordTheme.colors.primaryText.copy(alpha = 0.8f),
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
                
                val isTablet = screenWidthDp >= 600.dp
                val leftPaneWidthDp = 320.dp
                val halfScreenWidthDp = screenWidthDp / 2
                val splitOffset = if (isTablet) -screenWidthPx + with(density) { leftPaneWidthDp.toPx() } else -screenWidthPx
                
                // track dynamic swipe-to-dismiss offset of the Channels List in pixels
                val serverSwipeOffset = remember { Animatable(if (uiState.activePanel == HomePanel.SERVER_LIST) 0f else -screenWidthPx) }
                val dmsSwipeOffset = remember { Animatable(if (uiState.activePanel == HomePanel.SERVER_LIST || uiState.activePanel == HomePanel.DMS_LIST) 0f else -screenWidthPx) }
                val voiceSwipeOffset = remember { Animatable(if (uiState.isViewingVoiceChat) -screenWidthPx else 0f) }
                val coroutineScope = rememberCoroutineScope()
                val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

                // programmatic panel transitions
                LaunchedEffect(uiState.activePanel) {
                    when (uiState.activePanel) {
                        HomePanel.SERVER_LIST -> {
                            keyboardController?.hide()
                            dmsSwipeOffset.snapTo(-screenWidthPx)
                        }
                        HomePanel.DMS_LIST -> {
                            keyboardController?.hide()
                            serverSwipeOffset.snapTo(-screenWidthPx)
                        }
                        HomePanel.SERVER_CHAT -> {
                            dmsSwipeOffset.snapTo(-screenWidthPx)
                        }
                        HomePanel.DM_CHAT -> {
                            serverSwipeOffset.snapTo(-screenWidthPx)
                        }
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
                    when (uiState.activePanel) {
                        HomePanel.SERVER_LIST -> {
                            serverSwipeOffset.snapTo(0f)
                            if (uiState.isDmsListSelected) dmsSwipeOffset.snapTo(0f) else dmsSwipeOffset.snapTo(-screenWidthPx)
                        }
                        HomePanel.DMS_LIST -> {
                            serverSwipeOffset.snapTo(-screenWidthPx)
                            dmsSwipeOffset.snapTo(0f)
                        }
                        HomePanel.SERVER_CHAT, HomePanel.DM_CHAT -> {
                            val target = if (isTablet && !uiState.isChatFullScreen) splitOffset else -screenWidthPx
                            if (uiState.isDmsListSelected) {
                                dmsSwipeOffset.snapTo(target)
                                serverSwipeOffset.snapTo(-screenWidthPx)
                            } else {
                                serverSwipeOffset.snapTo(target)
                                dmsSwipeOffset.snapTo(-screenWidthPx)
                            }
                        }
                    }
                    
                    if (uiState.isViewingVoiceChat) {
                        voiceSwipeOffset.snapTo(-screenWidthPx)
                    } else {
                        voiceSwipeOffset.snapTo(0f)
                    }
                }

                // close voice chat when pressing back button
                BackHandler(enabled = uiState.isViewingVoiceChat) {
                    viewModel.setViewingVoiceChat(false)
                }

                // go back to server list when pressing back button in chat
                BackHandler(enabled = uiState.activePanel == HomePanel.SERVER_CHAT && !uiState.isViewingVoiceChat) {
                    viewModel.setPanel(HomePanel.SERVER_LIST)
                }

                BackHandler(enabled = uiState.activePanel == HomePanel.DMS_LIST) {
                    viewModel.exitDmsListToServer()
                }

                BackHandler(enabled = uiState.activePanel == HomePanel.DM_CHAT && !uiState.isViewingVoiceChat) {
                    viewModel.setPanel(HomePanel.DMS_LIST)
                }

                LaunchedEffect(screenWidthPx, isTablet, uiState.activePanel, uiState.isChatFullScreen) {
                    val targetOffset = if (uiState.activePanel in listOf(HomePanel.SERVER_LIST, HomePanel.DMS_LIST)) {
                        0f
                    } else if (isTablet && !uiState.isChatFullScreen) {
                        splitOffset
                    } else {
                        -screenWidthPx
                    }
                    val activeOffset = if (uiState.isDmsListSelected) dmsSwipeOffset else serverSwipeOffset
                    if (activeOffset.value != targetOffset) {
                        activeOffset.animateTo(targetOffset)
                    }
                }

                val handleDragEnd: (androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, Float, Boolean) -> Unit = { offset, totalDrag, isDm ->
                    val activeId = if (isDm) uiState.selectedDmChannelId else uiState.selectedServerChannelId
                    val isVoice = activeId?.let { id -> data.channels.find { it.id == id }?.isVoice == true } == true

                    val anchors = if (isTablet) listOf(0f, splitOffset, -screenWidthPx) else listOf(0f, -screenWidthPx)
                    val currentAnchorIndex = when {
                        uiState.activePanel in listOf(HomePanel.SERVER_LIST, HomePanel.DMS_LIST) -> 0
                        isTablet && !uiState.isChatFullScreen -> 1
                        else -> anchors.lastIndex
                    }
                    
                    var targetIndex = if (isTablet) {
                        if (offset.value > splitOffset) {
                            // Segment A: Dragging between 0 and splitOffset
                            val segmentAnchors = listOf(0f, splitOffset)
                            val closestSegmentOffset = segmentAnchors.minByOrNull { kotlin.math.abs(it - offset.value) } ?: 0f
                            var localTarget = if (closestSegmentOffset == 0f) 0 else 1
                            
                            if (localTarget == currentAnchorIndex) {
                                if (totalDrag < -20) localTarget = 1
                                else if (totalDrag > 20) localTarget = 0
                            }
                            localTarget
                        } else {
                            // Segment B: Dragging between splitOffset and -screenWidthPx
                            val segmentAnchors = listOf(splitOffset, -screenWidthPx)
                            val closestSegmentOffset = segmentAnchors.minByOrNull { kotlin.math.abs(it - offset.value) } ?: splitOffset
                            var localTarget = if (closestSegmentOffset == splitOffset) 1 else 2
                            
                            if (localTarget == currentAnchorIndex) {
                                if (totalDrag < -20) localTarget = 2
                                else if (totalDrag > 20) localTarget = 1
                            }
                            localTarget
                        }
                    } else {
                        // Phone logic
                        val closestOffset = anchors.minByOrNull { kotlin.math.abs(it - offset.value) } ?: anchors[currentAnchorIndex]
                        var localTarget = anchors.indexOf(closestOffset)
                        
                        if (localTarget == currentAnchorIndex) {
                            if (totalDrag < -20) {
                                localTarget = (currentAnchorIndex + 1).coerceAtMost(anchors.lastIndex)
                            } else if (totalDrag > 20) {
                                localTarget = (currentAnchorIndex - 1).coerceAtLeast(0)
                            }
                        }
                        localTarget
                    }
                    
                    if (isTablet && isVoice && targetIndex == 1) {
                        targetIndex = if (totalDrag < 0) 2 else 0
                    }
                    
                    // Update state immediately. LaunchedEffect will handle the animation.
                    if (targetIndex == 0) {
                        viewModel.setPanel(if (isDm) HomePanel.DMS_LIST else HomePanel.SERVER_LIST)
                    } else if (isTablet && targetIndex == 1) {
                        viewModel.setPanel(if (isDm) HomePanel.DM_CHAT else HomePanel.SERVER_CHAT)
                        viewModel.setChatFullScreen(false)
                    } else {
                        viewModel.setPanel(if (isDm) HomePanel.DM_CHAT else HomePanel.SERVER_CHAT)
                        if (isTablet) viewModel.setChatFullScreen(true)
                    }

                    coroutineScope.launch {
                        offset.animateTo(anchors[targetIndex])
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // LAYER 1: CHAT PANEL (BOTTOM)
                    val activeChannelId = if (uiState.isDmsListSelected) uiState.selectedDmChannelId else uiState.selectedServerChannelId
                    val isDmSelected = uiState.isDmsListSelected
                    if (activeChannelId != null) {
                        val activeChannel = data.channels.find { it.id == activeChannelId }
                        val isDm = activeChannel?.isDm == true
                        val otherUser = activeChannel?.takeIf { isDm }?.run {
                            val parts = name.removePrefix("DM - ").split(":")
                            val otherUserId = parts.firstOrNull { it != data.ownUserId.toString() }?.toIntOrNull()
                            data.users.find { it.id == otherUserId }
                        }
                        
                        val displayName = if (isDm) {
                            otherUser?.name ?: activeChannel?.name ?: ""
                        } else {
                            activeChannel?.name ?: ""
                        }

                        if (activeChannel?.isVoice == true) {
                            val channelUsers = data.voiceMap?.get(activeChannelId.toString())?.users ?: emptyMap()
                            val currentState = channelUsers[data.ownUserId.toString()]
                            val isMuted = currentState?.micMuted ?: true
                            val isDeafened = currentState?.soundMuted ?: true

                            val permissionLauncher = rememberLauncherForActivityResult(
                                ActivityResultContracts.RequestMultiplePermissions()
                            ) { results ->
                                if (results[Manifest.permission.RECORD_AUDIO] == true) {
                                    voiceViewModel.joinVoiceChannel(activeChannelId, context, displayName)
                                }
                            }
                            
                            val cameraPermissionLauncher = rememberLauncherForActivityResult(
                                ActivityResultContracts.RequestMultiplePermissions()
                            ) { results ->
                                if (results[Manifest.permission.CAMERA] == true) {
                                    voiceViewModel.toggleCamera(context)
                                }
                            }

                            val voiceSplitOffset = -screenWidthPx / 3f
                            val vActiveOffset = voiceSwipeOffset.value
                            
                            val voiceChatWidthDp = if (isTablet) {
                                with(density) { kotlin.math.max(screenWidthPx / 3f, -vActiveOffset).toDp() }
                            } else screenWidthDp

                            val voiceChatOffsetDp = if (isTablet) {
                                with(density) { kotlin.math.min(screenWidthPx * 2/3f, vActiveOffset + screenWidthPx).toDp() }
                            } else 0.dp

                            Box(modifier = Modifier.fillMaxSize()) {
                                // chat Panel (underneath)
                                ChatPanel(
                                    channelId = activeChannelId,
                                    targetMessageId = uiState.selectedMessageId,
                                    jumpTrigger = uiState.jumpTrigger,
                                    channelName = displayName,
                                    isDm = isDm,
                                    dmUser = otherUser,
                                    users = data.users,
                                    roles = data.roles ?: emptyList(),
                                    customEmojis = data.emojis ?: emptyList(),
                                    isActive = (uiState.activePanel == HomePanel.SERVER_CHAT || uiState.activePanel == HomePanel.DM_CHAT) && uiState.isViewingVoiceChat,
                                    isTablet = isTablet,
                                    isChatFullScreen = voiceSwipeOffset.value <= -screenWidthPx + VOICE_FULLSCREEN_THRESHOLD_PX,
                                    onToggleFullScreen = {
                                        coroutineScope.launch {
                                            if (voiceSwipeOffset.value <= -screenWidthPx + VOICE_FULLSCREEN_THRESHOLD_PX) {
                                                voiceSwipeOffset.animateTo(voiceSplitOffset)
                                            } else {
                                                voiceSwipeOffset.animateTo(-screenWidthPx)
                                            }
                                        }
                                    },
                                    onBackClick = {
                                        viewModel.setViewingVoiceChat(false)
                                        coroutineScope.launch {
                                            voiceSwipeOffset.animateTo(0f)
                                        }
                                    },
                                    onUserClick = { userId -> viewModel.showProfileSheet(userId) },
                                    modifier = Modifier
                                        .then(if (isTablet) Modifier.offset(x = voiceChatOffsetDp).width(voiceChatWidthDp).fillMaxHeight() else Modifier.fillMaxSize())
                                        .pointerInput(uiState.activePanel) {
                                            if (uiState.activePanel == HomePanel.SERVER_CHAT || uiState.activePanel == HomePanel.DM_CHAT) {
                                                var totalDrag = 0f
                                                detectHorizontalDragGestures(
                                                    onDragEnd = {
                                                        coroutineScope.launch {
                                                            if (isTablet) {
                                                                var targetIndex = 0
                                                                if (voiceSwipeOffset.value > voiceSplitOffset) {
                                                                    targetIndex = if (totalDrag < -20) 1 else if (totalDrag > 20) 0 else if (voiceSwipeOffset.value < voiceSplitOffset / 2) 1 else 0
                                                                } else {
                                                                    targetIndex = if (totalDrag < -20) 2 else if (totalDrag > 20) 1 else if (voiceSwipeOffset.value < (voiceSplitOffset - screenWidthPx) / 2) 2 else 1
                                                                }

                                                                when (targetIndex) {
                                                                    0 -> voiceSwipeOffset.animateTo(0f)
                                                                    1 -> voiceSwipeOffset.animateTo(voiceSplitOffset)
                                                                    2 -> voiceSwipeOffset.animateTo(-screenWidthPx)
                                                                }
                                                            } else {
                                                                val target = if (totalDrag < -20) -screenWidthPx else if (totalDrag > 20) 0f else if (voiceSwipeOffset.value < -screenWidthPx / 2) -screenWidthPx else 0f
                                                                voiceSwipeOffset.animateTo(target)
                                                                if (target == 0f) viewModel.setViewingVoiceChat(false)
                                                            }
                                                        }
                                                        totalDrag = 0f
                                                    },
                                                    onDragCancel = {
                                                        totalDrag = 0f
                                                        coroutineScope.launch {
                                                            if (isTablet) {
                                                                if (voiceSwipeOffset.value > voiceSplitOffset) {
                                                                    voiceSwipeOffset.animateTo(0f)
                                                                } else {
                                                                    voiceSwipeOffset.animateTo(-screenWidthPx)
                                                                }
                                                            } else {
                                                                voiceSwipeOffset.animateTo(-screenWidthPx)
                                                            }
                                                        }
                                                    },
                                                    onHorizontalDrag = { change, dragAmount ->
                                                        change.consume()
                                                        totalDrag += dragAmount
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
                                val voiceWidthDp = if (isTablet) {
                                    with(density) { kotlin.math.max(screenWidthPx * 2/3f, vActiveOffset + screenWidthPx).toDp() }
                                } else screenWidthDp

                                val voiceOffsetDp = if (isTablet) {
                                    with(density) { kotlin.math.min(0f, vActiveOffset - voiceSplitOffset).toDp() }
                                } else with(density) { vActiveOffset.toDp() }

                                VoicePanel(
                                    channelName = displayName,
                                    voiceUsers = if (data.voiceMap != null) {
                                        channelUsers.mapNotNull { (userIdStr, state) ->
                                            val user = data.users.find { it.id.toString() == userIdStr }
                                            if (user != null) {
                                                val isSpeaking = if (user.id == data.ownUserId) {
                                                    voiceUiState.activeSpeakers.contains("local")
                                                } else {
                                                    voiceUiState.activeSpeakers.contains(user.id.toString())
                                                }
                                                VoiceUserDisplay(user, state, isSpeaking)
                                            } else null
                                        }
                                    } else emptyList(),
                                    isConnected = voiceUiState.activeVoiceChannelId == activeChannelId,
                                    isConnectingToVoice = voiceUiState.isConnectingToVoice,
                                    isMuted = isMuted,
                                    isDeafened = isDeafened,
                                    cameraEnabled = voiceUiState.cameraEnabled,
                                    isScreenSharing = voiceUiState.isScreenSharing,
                                    localVideoTrack = voiceUiState.localVideoTrack,
                                    remoteVideoTracks = voiceUiState.remoteVideoTracks,
                                    eglBaseContext = voiceUiState.eglBaseContext,
                                    ownUserId = data.ownUserId,
                                    onDisconnectClick = { voiceViewModel.leaveVoiceChannel(context) },
                                    onConnectClick = { 
                                        val neededPermissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                            neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        val toRequest = neededPermissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
                                        
                                        if (toRequest.isEmpty()) {
                                            voiceViewModel.joinVoiceChannel(activeChannelId, context, displayName)
                                        } else {
                                            permissionLauncher.launch(toRequest.toTypedArray())
                                        }
                                    },
                                    onToggleMicClick = { _ -> 
                                        voiceViewModel.toggleMic(activeChannelId, isMuted, isDeafened) 
                                    },
                                    onToggleDeafenClick = { _ ->
                                        voiceViewModel.toggleDeafen(activeChannelId, isMuted, isDeafened)
                                    },
                                    onToggleCameraClick = {
                                        val neededPermissions = mutableListOf(Manifest.permission.CAMERA)
                                        val toRequest = neededPermissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
                                        
                                        if (toRequest.isEmpty()) {
                                            voiceViewModel.toggleCamera(context)
                                        } else {
                                            cameraPermissionLauncher.launch(toRequest.toTypedArray())
                                        }
                                    },
                                    onToggleScreenShareClick = { enabled, intent ->
                                        voiceViewModel.toggleScreenShare(context, enabled, intent)
                                    },
                                    onSwitchCameraClick = {
                                        voiceViewModel.switchCamera(context)
                                    },
                                    onOpenChatClick = {
                                        if (voiceSwipeOffset.value < 0f) {
                                            coroutineScope.launch {
                                                voiceSwipeOffset.animateTo(0f)
                                                viewModel.setViewingVoiceChat(false)
                                            }
                                        } else {
                                            viewModel.setViewingVoiceChat(true)
                                            coroutineScope.launch {
                                                voiceSwipeOffset.animateTo(if (isTablet) voiceSplitOffset else -screenWidthPx)
                                            }
                                        }
                                    },
                                    onBackClick = {
                                        coroutineScope.launch {
                                            serverSwipeOffset.animateTo(0f)
                                            dmsSwipeOffset.animateTo(0f)
                                            voiceSwipeOffset.snapTo(0f)
                                            viewModel.setPanel(if (isDmSelected) HomePanel.DMS_LIST else HomePanel.SERVER_LIST)
                                            if (isTablet) viewModel.setChatFullScreen(false)
                                        }
                                    },
                                    modifier = Modifier
                                        .then(
                                            if (isTablet) Modifier.width(voiceWidthDp).fillMaxHeight().offset(x = voiceOffsetDp)
                                            else Modifier.fillMaxSize().offset(x = voiceOffsetDp)
                                        )
                                        .drawBehind {
                                            val shadowWidth = 12.dp.toPx()
                                            val shadowAlpha = if (colors.isLight) 0.15f else 0.5f
                                            val shadowColor = Color.Black.copy(alpha = shadowAlpha)
                                            drawRect(
                                                brush = Brush.horizontalGradient(
                                                    colors = listOf(shadowColor, Color.Transparent),
                                                    startX = size.width,
                                                    endX = size.width + shadowWidth
                                                ),
                                                topLeft = Offset(size.width, 0f),
                                                size = androidx.compose.ui.geometry.Size(shadowWidth, size.height)
                                            )
                                            val strokeWidth = 1.dp.toPx()
                                            val x = size.width - strokeWidth / 2
                                            drawLine(
                                                color = Color.Black.copy(alpha = if (colors.isLight) 0.1f else 0.3f),
                                                start = Offset(x, 0f),
                                                end = Offset(x, size.height),
                                                strokeWidth = strokeWidth
                                            )
                                        }
                                        .pointerInput(uiState.activePanel) {
                                            if (uiState.activePanel == HomePanel.SERVER_CHAT || uiState.activePanel == HomePanel.DM_CHAT) {
                                                var totalDrag = 0f
                                                detectHorizontalDragGestures(
                                                    onDragEnd = {
                                                        coroutineScope.launch {
                                                            if (isTablet) {
                                                                var targetIndex = 0
                                                                if (voiceSwipeOffset.value > voiceSplitOffset) {
                                                                    targetIndex = if (totalDrag < -20) 1 else if (totalDrag > 20) 0 else if (voiceSwipeOffset.value < voiceSplitOffset / 2) 1 else 0
                                                                } else {
                                                                    targetIndex = if (totalDrag < -20) 2 else if (totalDrag > 20) 1 else if (voiceSwipeOffset.value < (voiceSplitOffset - screenWidthPx) / 2) 2 else 1
                                                                }

                                                                when (targetIndex) {
                                                                    0 -> voiceSwipeOffset.animateTo(0f)
                                                                    1 -> {
                                                                        voiceSwipeOffset.animateTo(voiceSplitOffset)
                                                                        viewModel.setViewingVoiceChat(true)
                                                                    }
                                                                    2 -> {
                                                                        voiceSwipeOffset.animateTo(-screenWidthPx)
                                                                        viewModel.setViewingVoiceChat(true)
                                                                    }
                                                                }
                                                            } else {
                                                                val offsetToCheck = if (isDmSelected) dmsSwipeOffset else serverSwipeOffset
                                                                if (offsetToCheck.value > -screenWidthPx) {
                                                                    val target = if (totalDrag < -20) -screenWidthPx else if (totalDrag > 20) 0f else if (offsetToCheck.value > -screenWidthPx / 2) 0f else -screenWidthPx
                                                                    offsetToCheck.animateTo(target)
                                                                    if (target == 0f) viewModel.setPanel(if (isDmSelected) HomePanel.DMS_LIST else HomePanel.SERVER_LIST)
                                                                } else {
                                                                    val target = if (totalDrag < -20) -screenWidthPx else if (totalDrag > 20) 0f else if (voiceSwipeOffset.value < -screenWidthPx / 2) -screenWidthPx else 0f
                                                                    voiceSwipeOffset.animateTo(target)
                                                                    if (target == -screenWidthPx) viewModel.setViewingVoiceChat(true)
                                                                }
                                                            }
                                                        }
                                                        totalDrag = 0f
                                                    },
                                                    onDragCancel = {
                                                        totalDrag = 0f
                                                        coroutineScope.launch {
                                                            if (isTablet) {
                                                                if (voiceSwipeOffset.value > voiceSplitOffset) {
                                                                    voiceSwipeOffset.animateTo(0f)
                                                                } else {
                                                                    voiceSwipeOffset.animateTo(-screenWidthPx)
                                                                }
                                                            } else {
                                                                serverSwipeOffset.animateTo(-screenWidthPx)
                                                                dmsSwipeOffset.animateTo(-screenWidthPx)
                                                                voiceSwipeOffset.animateTo(0f)
                                                            }
                                                        }
                                                    },
                                                    onHorizontalDrag = { change, dragAmount ->
                                                        change.consume()
                                                        totalDrag += dragAmount
                                                        coroutineScope.launch {
                                                            if (isTablet) {
                                                                if (dragAmount > 0 || voiceSwipeOffset.value > -screenWidthPx) {
                                                                    val newOffset = (voiceSwipeOffset.value + dragAmount).coerceIn(-screenWidthPx, 0f)
                                                                    voiceSwipeOffset.snapTo(newOffset)
                                                                    if (newOffset < 0f) viewModel.setViewingVoiceChat(true)
                                                                }
                                                            } else {
                                                                val activeOffset = if (isDmSelected) dmsSwipeOffset else serverSwipeOffset
                                                                if (activeOffset.value > -screenWidthPx) {
                                                                    val newOffset = (activeOffset.value + dragAmount).coerceIn(-screenWidthPx, 0f)
                                                                    activeOffset.snapTo(newOffset)
                                                                } else if (voiceSwipeOffset.value < 0f) {
                                                                    val newOffset = (voiceSwipeOffset.value + dragAmount).coerceIn(-screenWidthPx, 0f)
                                                                    voiceSwipeOffset.snapTo(newOffset)
                                                                } else if (dragAmount < 0) {
                                                                    val newOffset = (voiceSwipeOffset.value + dragAmount).coerceIn(-screenWidthPx, 0f)
                                                                    voiceSwipeOffset.snapTo(newOffset)
                                                                } else if (dragAmount > 0) {
                                                                    val newOffset = (activeOffset.value + dragAmount).coerceIn(-screenWidthPx, 0f)
                                                                    activeOffset.snapTo(newOffset)
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
                                channelId = activeChannelId,
                                targetMessageId = uiState.selectedMessageId,
                                jumpTrigger = uiState.jumpTrigger,
                                channelName = displayName,
                                isDm = isDm,
                                dmUser = otherUser,
                                users = data.users,
                                roles = data.roles ?: emptyList(),
                                customEmojis = data.emojis ?: emptyList(),
                                isActive = uiState.activePanel == HomePanel.SERVER_CHAT || uiState.activePanel == HomePanel.DM_CHAT,
                                isTablet = isTablet,
                                isChatFullScreen = uiState.isChatFullScreen,
                                onToggleFullScreen = { viewModel.setChatFullScreen(!uiState.isChatFullScreen) },
                                onBackClick = {
                                    coroutineScope.launch {
                                        val offsetToAnimate = if (isDmSelected) dmsSwipeOffset else serverSwipeOffset
                                        offsetToAnimate.animateTo(0f)
                                        viewModel.setPanel(if (isDmSelected) HomePanel.DMS_LIST else HomePanel.SERVER_LIST)
                                    }
                                },
                                onUserClick = { userId -> viewModel.showProfileSheet(userId) },
                                modifier = Modifier
                                    .then(
                                        if (isTablet) {
                                            val activeSwipeOffset = if (uiState.isDmsListSelected) dmsSwipeOffset.value else serverSwipeOffset.value
                                            val chatWidthDp = with(density) { kotlin.math.max(screenWidthPx - leftPaneWidthDp.toPx(), -activeSwipeOffset).toDp() }
                                            val chatOffsetDp = with(density) { kotlin.math.min(leftPaneWidthDp.toPx(), activeSwipeOffset + screenWidthPx).toDp() }
                                            Modifier.offset(x = chatOffsetDp).width(chatWidthDp).fillMaxHeight()
                                        } else Modifier.fillMaxSize()
                                    )
                                    .pointerInput(uiState.activePanel, isTablet) {
                                        if (uiState.activePanel == HomePanel.SERVER_CHAT || uiState.activePanel == HomePanel.DM_CHAT) {
                                            var totalDrag = 0f
                                            detectHorizontalDragGestures(
                                                onDragEnd = {
                                                    val offset = if (isDmSelected) dmsSwipeOffset else serverSwipeOffset
                                                    handleDragEnd(offset, totalDrag, isDmSelected)
                                                    totalDrag = 0f
                                                },
                                                onDragCancel = {
                                                    totalDrag = 0f
                                                },
                                                onHorizontalDrag = { change, dragAmount ->
                                                    change.consume()
                                                    totalDrag += dragAmount
                                                    coroutineScope.launch {
                                                        val activeOffset = if (isDmSelected) dmsSwipeOffset else serverSwipeOffset
                                                        val newOffset = (activeOffset.value + dragAmount).coerceIn(-screenWidthPx, 0f)
                                                        activeOffset.snapTo(newOffset)
                                                    }
                                                }
                                            )
                                        }
                                    }
                            )
                        }
                    }

                    // LAYER 2: DMS LIST PANEL (MIDDLE)
                    val isCurrentServerChannelVoice = uiState.selectedServerChannelId?.let { id -> data.channels.find { it.id == id }?.isVoice == true } == true
                    val isCurrentDmChannelVoice = uiState.selectedDmChannelId?.let { id -> data.channels.find { it.id == id }?.isVoice == true } == true

                    val dmsWidthTabletDp = if (isTablet) {
                        with(density) { kotlin.math.max(320f, dmsSwipeOffset.value + screenWidthPx).toDp() }
                    } else screenWidthDp

                    val dmsOffsetDp = if (isTablet) {
                        with(density) { kotlin.math.min(0f, dmsSwipeOffset.value - splitOffset).toDp() }
                    } else with(density) { dmsSwipeOffset.value.toDp() }
                    
                    Box(modifier = Modifier
                        .then(if (isTablet) Modifier.width(dmsWidthTabletDp).fillMaxHeight() else Modifier.fillMaxSize())
                        .offset(x = dmsOffsetDp)
                    ) {
                        DmsListPanel(
                            data = data,
                            uiState = uiState,
                            viewModel = viewModel,
                            foregroundText = foregroundText,
                            primaryText = primaryText,
                            modifier = Modifier
                                .fillMaxSize()
                                .drawBehind {
                                    val shadowWidth = 12.dp.toPx()
                                    val shadowAlpha = if (colors.isLight) 0.15f else 0.5f
                                    val shadowColor = Color.Black.copy(alpha = shadowAlpha)
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(shadowColor, Color.Transparent),
                                            startX = size.width,
                                            endX = size.width + shadowWidth
                                        ),
                                        topLeft = Offset(size.width, 0f),
                                        size = androidx.compose.ui.geometry.Size(shadowWidth, size.height)
                                    )
                                    val strokeWidth = 1.dp.toPx()
                                    val x = size.width - strokeWidth / 2
                                    drawLine(
                                        color = Color.Black.copy(alpha = if (colors.isLight) 0.1f else 0.3f),
                                        start = Offset(x, 0f),
                                        end = Offset(x, size.height),
                                        strokeWidth = strokeWidth
                                    )
                                }
                                .pointerInput(uiState.activePanel, isTablet, isCurrentDmChannelVoice) {
                            if (uiState.activePanel == HomePanel.DMS_LIST || (isTablet && uiState.activePanel == HomePanel.DM_CHAT && !isCurrentDmChannelVoice)) {
                                var totalDrag = 0f
                                // Track which offset we actually moved so we can snap it on release
                                var movedServerOffset = false
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                if (movedServerOffset) {
                                                    // User dragged right into the server panel - snap it
                                                    handleDragEnd(serverSwipeOffset, totalDrag, false)
                                                } else {
                                                    handleDragEnd(dmsSwipeOffset, totalDrag, true)
                                                }
                                                movedServerOffset = false
                                                totalDrag = 0f
                                            },
                                            onDragCancel = {
                                                totalDrag = 0f
                                                movedServerOffset = false
                                                coroutineScope.launch {
                                                    // Snap both offsets back to their nearest anchor
                                                    val anchors = if (isTablet) listOf(0f, splitOffset, -screenWidthPx) else listOf(0f, -screenWidthPx)
                                                    val dmsTarget = anchors.minByOrNull { kotlin.math.abs(it - dmsSwipeOffset.value) } ?: dmsSwipeOffset.value
                                                    val srvTarget = anchors.minByOrNull { kotlin.math.abs(it - serverSwipeOffset.value) } ?: serverSwipeOffset.value
                                                    launch { dmsSwipeOffset.animateTo(dmsTarget) }
                                                    launch { serverSwipeOffset.animateTo(srvTarget) }
                                                }
                                            },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                totalDrag += dragAmount
                                                coroutineScope.launch {
                                                    if (dragAmount > 0) {
                                                        movedServerOffset = true
                                                        val newOffset = (serverSwipeOffset.value + dragAmount).coerceIn(-screenWidthPx, 0f)
                                                        serverSwipeOffset.snapTo(newOffset)
                                                    } else if (dragAmount < 0 && uiState.selectedDmChannelId != null) {
                                                        val newOffset = (dmsSwipeOffset.value + dragAmount).coerceIn(-screenWidthPx, 0f)
                                                        dmsSwipeOffset.snapTo(newOffset)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                        )
                    }

                    // LAYER 3: SERVER LIST PANEL (TOP)
                    val channelsWidthTabletDp = if (isTablet) {
                        with(density) { kotlin.math.max(320f, serverSwipeOffset.value + screenWidthPx).toDp() }
                    } else screenWidthDp

                    val channelsOffsetDp = if (isTablet) {
                        with(density) { kotlin.math.min(0f, serverSwipeOffset.value - splitOffset).toDp() }
                    } else with(density) { serverSwipeOffset.value.toDp() }

                    Column(
                        modifier = Modifier
                            .then(if (isTablet) Modifier.width(channelsWidthTabletDp).fillMaxHeight() else Modifier.fillMaxSize())
                            .offset(x = channelsOffsetDp)
                            .background(bgColor)
                            .drawBehind {
                                val shadowWidth = 12.dp.toPx()
                                val shadowAlpha = if (colors.isLight) 0.15f else 0.5f
                                val shadowColor = Color.Black.copy(alpha = shadowAlpha)
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(shadowColor, Color.Transparent),
                                        startX = size.width,
                                        endX = size.width + shadowWidth
                                    ),
                                    topLeft = Offset(size.width, 0f),
                                    size = androidx.compose.ui.geometry.Size(shadowWidth, size.height)
                                )
                                val strokeWidth = 1.dp.toPx()
                                val x = size.width - strokeWidth / 2
                                drawLine(
                                    color = Color.Black.copy(alpha = if (colors.isLight) 0.1f else 0.3f),
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = strokeWidth
                                )
                            }
                            .pointerInput(uiState.activePanel, isTablet, isCurrentServerChannelVoice) {
                                if (uiState.activePanel == HomePanel.SERVER_LIST || (isTablet && uiState.activePanel == HomePanel.SERVER_CHAT && !isCurrentServerChannelVoice)) {
                                    var totalDrag = 0f
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            handleDragEnd(serverSwipeOffset, totalDrag, false)
                                            totalDrag = 0f
                                        },
                                        onDragCancel = {
                                            totalDrag = 0f
                                            coroutineScope.launch {
                                                val anchors = if (isTablet) listOf(0f, splitOffset, -screenWidthPx) else listOf(0f, -screenWidthPx)
                                                val target = anchors.minByOrNull { kotlin.math.abs(it - serverSwipeOffset.value) } ?: serverSwipeOffset.value
                                                serverSwipeOffset.animateTo(target)
                                            }
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            totalDrag += dragAmount
                                            coroutineScope.launch {
                                                val newOffset = (serverSwipeOffset.value + dragAmount).coerceIn(-screenWidthPx, 0f)
                                                serverSwipeOffset.snapTo(newOffset)
                                            }
                                        }
                                    )
                                }
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
                                            coroutineScope.launch {
                                                clipboard.setClipEntry(
                                                    androidx.compose.ui.platform.ClipEntry(
                                                        android.content.ClipData.newPlainText("error", errorMsg)
                                                    )
                                                )
                                            }
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
                                        colors = listOf(SharkordTheme.colors.cardColor, SharkordTheme.colors.cardColor) // Sleek premium dark gradient
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
                                    totalUnreadDMs = totalUnreadDMs,
                                    isDmsListSelected = uiState.isDmsListSelected
                                )
                            }

                            // uncategorized Channels
                            if (uncategorizedText.isNotEmpty()) {
                                items(uncategorizedText) { channel ->
                                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    ChannelItem(
                                            channel = channel,
                                            isSelected = uiState.selectedServerChannelId == channel.id && !uiState.isDmsListSelected,
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
                                            isSelected = uiState.selectedServerChannelId == channel.id && !uiState.isDmsListSelected,
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
                                                            voiceUiState.activeSpeakers.contains("local")
                                                        } else {
                                                            voiceUiState.activeSpeakers.contains(user.id.toString())
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
                                            selectedChannelId = if (uiState.isDmsListSelected) null else uiState.selectedServerChannelId,
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
                                            activeSpeakers = voiceUiState.activeSpeakers
                                        )
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
                if (uiState.profileSheetUserId != null) {
                    val profileUser = data.users.find { it.id == uiState.profileSheetUserId } ?: if (uiState.profileSheetUserId == data.ownUserId) currentUser else null
                    ProfileBottomSheet(
                        currentUser = profileUser,
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



