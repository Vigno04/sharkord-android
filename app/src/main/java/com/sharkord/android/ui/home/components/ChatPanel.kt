package com.sharkord.android.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.text.HtmlCompat
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import com.sharkord.android.R
import com.sharkord.android.data.model.Emoji
import com.sharkord.android.data.model.Message
import com.sharkord.android.data.model.Role
import com.sharkord.android.data.model.User
import com.sharkord.android.ui.home.ChatViewModel
import com.sharkord.android.ui.home.components.chat.ChatInputBar
import com.sharkord.android.ui.home.components.chat.ChatTopBar
import com.sharkord.android.ui.home.components.chat.MessageContextMenu
import com.sharkord.android.ui.home.components.chat.PinnedMessagesSheet
import androidx.compose.foundation.Image
import com.sharkord.android.ui.components.rememberAsyncImagePainter
import com.sharkord.android.data.network.SharkordClient
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChatPanel(
    channelId: Int,
    channelName: String,
    isDm: Boolean = false,
    dmUser: User? = null,
    users: List<User>,
    roles: List<Role>,
    customEmojis: List<Emoji>,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel(key = "chat_$channelId")
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val bgColor = ChatColors.BgColor
    val cardColor = ChatColors.CardColor
    val textPrimary = ChatColors.TextPrimary
    val textSecondary = ChatColors.TextSecondary
    val textMuted = ChatColors.TextMuted
    val accentColor = ChatColors.AccentColor

    val uiState by viewModel.uiState.collectAsState()
    val listState = remember(channelId) { androidx.compose.foundation.lazy.LazyListState() }
    val coroutineScope = rememberCoroutineScope()
    var inputText by remember(channelId) { mutableStateOf(TextFieldValue("")) }

    val ownUserId = uiState.ownUserId

    var showMenuMessage by remember(channelId) { mutableStateOf<Message?>(null) }
    var isEmojiPickerOpen by remember(channelId) { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current


    // Initialize ViewModel
    LaunchedEffect(channelId) {
        viewModel.init(channelId)
    }

    // Edit mode: populate input field
    LaunchedEffect(uiState.editingMessage) {
        val editing = uiState.editingMessage
        if (editing != null) {
            val text = HtmlCompat.fromHtml(editing.content, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
            inputText = TextFieldValue(text = text, selection = TextRange(text.length))
        } else {
            inputText = TextFieldValue("")
        }
    }

    // Auto-scroll to bottom
    val isAtBottom by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index
                ?: return@derivedStateOf true
            lastVisible >= layout.totalItemsCount - 2
        }
    }

    var hasInitialLoaded by remember(channelId) { mutableStateOf(false) }
    var unreadNewCount by remember(channelId) { mutableIntStateOf(0) }

    val lastMessage = uiState.messages.lastOrNull()
    val lastMessageId = lastMessage?.id
    LaunchedEffect(lastMessageId) {
        if (lastMessageId == null) return@LaunchedEffect
        if (!hasInitialLoaded) {
            if (uiState.messages.isNotEmpty()) {
                listState.scrollToItem(uiState.messages.size - 1)
            }
            hasInitialLoaded = true
            unreadNewCount = 0
            return@LaunchedEffect
        }
        val isOwnMessage = lastMessage?.userId == ownUserId
        if (isAtBottom || isOwnMessage) {
            listState.animateScrollToItem(uiState.messages.size - 1)
            unreadNewCount = 0
        } else {
            unreadNewCount++
        }
    }

    LaunchedEffect(isAtBottom) {
        if (isAtBottom) unreadNewCount = 0
    }

    // Paginate old messages
    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisibleIndex) {
        if (firstVisibleIndex == 0 && !uiState.isLoadingHistory && !uiState.isLoadingOlder && !uiState.hasReachedTop) {
            viewModel.loadOlderMessages()
        }
    }

    // Helper to close the keyboard
    val dismissInputPanel = {
        if (isEmojiPickerOpen) {
            isEmojiPickerOpen = false
        }
        keyboardController?.hide()
    }

    // System back button interception
    BackHandler {
        when {
            uiState.viewingMediaFile != null -> viewModel.setViewingMediaFile(null)
            showMenuMessage != null          -> showMenuMessage = null
            isEmojiPickerOpen                -> isEmojiPickerOpen = false
            uiState.showPinnedMessages       -> viewModel.setPinnedMessagesVisible(false)
            else                             -> onBackClick()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Top Bar
            ChatTopBar(
                channelName = channelName,
                isDm = isDm,
                dmUser = dmUser,
                showPinnedMessages = uiState.showPinnedMessages,
                onBackClick = onBackClick,
                onTogglePinnedMessages = { viewModel.setPinnedMessagesVisible(!uiState.showPinnedMessages) }
            )

            // Error Banner
            AnimatedVisibility(
                visible = uiState.errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable {
                            val errorMsg = uiState.errorMessage
                            if (errorMsg != null) {
                                clipboardManager.setText(AnnotatedString(errorMsg))
                                android.widget.Toast.makeText(context, context.getString(R.string.common_errorDetailsCopied), android.widget.Toast.LENGTH_SHORT).show()
                            }
                            viewModel.clearError()
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = Color(0xFFEF4444),
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = "✕", color = Color(0xFFEF4444), fontSize = 13.sp)
                }
            }

            // Message List Area
            // Tapping anywhere in this area dismisses the keyboard,
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    uiState.isLoadingHistory -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = accentColor,
                                modifier = Modifier.size(40.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(id = R.string.settings_modViewLoading),
                                color = textMuted,
                                fontSize = 14.sp
                            )
                        }
                    }

                    uiState.messages.isEmpty() && !uiState.isLoadingHistory -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(cardColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "#",
                                    color = textPrimary,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(id = R.string.chat_welcomeToChannel, channelName),
                                color = textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(id = R.string.chat_channelBeginning, channelName),
                                color = textSecondary,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { dismissInputPanel() }
                                    )
                                },
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            if (uiState.isLoadingOlder) {
                                item(key = "loading_older") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = accentColor,
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            } else if (uiState.hasReachedTop && uiState.messages.isNotEmpty()) {
                                item(key = "top_reached") {
                                    Text(
                                        text = stringResource(id = R.string.chat_channelBeginningShort, channelName),
                                        color = textMuted,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    )
                                }
                            }

                            itemsIndexed(
                                items = uiState.messages,
                                key = { _, msg -> msg.id }
                            ) { index, message ->
                                val previousMessage = if (index > 0) uiState.messages[index - 1] else null
                                MessageItem(
                                    message = message,
                                    previousMessage = previousMessage,
                                    users = users,
                                    roles = roles,
                                    ownUserId = ownUserId,
                                    fullscreenMediaId = uiState.viewingMediaFile?.id,
                                    onLongClick = { target -> showMenuMessage = target },
                                    onReplyClick = { parentId ->
                                        val targetIndex = uiState.messages.indexOfFirst { it.id == parentId }
                                        if (targetIndex != -1) {
                                            val headerOffset = if (uiState.isLoadingOlder || (uiState.hasReachedTop && uiState.messages.isNotEmpty())) 1 else 0
                                            coroutineScope.launch {
                                                listState.animateScrollToItem(targetIndex + headerOffset)
                                            }
                                        } else {
                                            android.widget.Toast.makeText(context, "Referenced message is not loaded yet", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onReactionClick = { messageId, emoji ->
                                        viewModel.toggleReaction(messageId, emoji)
                                    },
                                    onMediaClick = { file ->
                                        if (file.isImage || file.isVideo) {
                                            viewModel.setViewingMediaFile(file)
                                        } else {
                                            viewModel.downloadAndOpenFile(context, file)
                                        }
                                    },
                                    onMediaLongClick = { file -> viewModel.downloadFile(context, file) }
                                )
                            }

                            item(key = "bottom_spacer") {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }

                // New Messages Pill
                androidx.compose.animation.AnimatedVisibility(
                    visible = unreadNewCount > 0,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    enter = fadeIn() + androidx.compose.animation.slideInVertically { fullHeight -> fullHeight },
                    exit  = fadeOut() + androidx.compose.animation.slideOutVertically { fullHeight -> fullHeight }
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(accentColor)
                            .clickable {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(uiState.messages.size - 1)
                                }
                                unreadNewCount = 0
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (unreadNewCount == 1) {
                                stringResource(id = R.string.chat_newMessageSingular)
                            } else {
                                stringResource(id = R.string.chat_newMessagesPlural, unreadNewCount)
                            },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Input Bar
            ChatInputBar(
                channelName = channelName,
                users = users,
                uiState = uiState,
                inputText = inputText,
                onInputTextChanged = { textVal ->
                    inputText = textVal
                    viewModel.onType(textVal.text)
                },
                onSend = { text ->
                    val editing = uiState.editingMessage
                    if (editing != null) {
                        viewModel.submitEdit(editing.id, text)
                    } else {
                        viewModel.sendMessage(text)
                    }
                    inputText = TextFieldValue("")
                },
                onCancelReply = { viewModel.setReplyTarget(null) },
                onCancelEdit = { viewModel.setEditingMessage(null) },
                onFileUpload = { name, bytes, uri -> viewModel.uploadAndAttachFile(name, bytes, uri) },
                onRemoveAttachment = { id -> viewModel.removeAttachedFile(id) },
                onSendAudioRecording = { name, bytes -> viewModel.sendAudioVoiceNote(name, bytes) },
                isEmojiPickerOpen = isEmojiPickerOpen,
                onToggleEmojiPicker = {
                    if (!isEmojiPickerOpen) {
                        // Keyboard → Emoji: open emoji picker, hide keyboard
                        isEmojiPickerOpen = true
                        keyboardController?.hide()
                    }
                    // Emoji → Keyboard: do nothing here.
                    // ChatInputBar will request focus and show the keyboard.
                    // The LaunchedEffect below closes emoji once the keyboard appears.
                }
            )

            // Bottom Panel (keyboard / emoji space)
            com.sharkord.android.ui.home.components.chat.ChatBottomPanel(
                isEmojiPickerOpen = isEmojiPickerOpen,
                onCloseEmojiPicker = { isEmojiPickerOpen = false },
                customEmojis = customEmojis,
                inputText = inputText,
                onInputTextChanged = { textVal ->
                    inputText = textVal
                },
                onType = { text -> viewModel.onType(text) },
                isAtBottom = isAtBottom,
                messagesCount = uiState.messages.size,
                listState = listState,
                bgColor = bgColor,
                cardColor = cardColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                accentColor = accentColor
            )
        }

        // Pinned Messages Overlay
        if (uiState.showPinnedMessages) {
            PinnedMessagesSheet(
                isLoading = uiState.isLoadingPinned,
                pinnedMessages = uiState.pinnedMessages,
                users = users,
                onClose = { viewModel.setPinnedMessagesVisible(false) },
                onUnpin = { viewModel.togglePin(it) }
            )
        }

        // Context Menu Overlay
        if (showMenuMessage != null) {
            val msg = showMenuMessage!!
            MessageContextMenu(
                message = msg,
                customEmojis = customEmojis,
                ownUserId = uiState.ownUserId,
                users = users,
                roles = roles,
                canPin = uiState.canPinMessage(roles, users),
                canManage = uiState.canManageMessage(msg, roles, users),
                onClose = { showMenuMessage = null },
                onReactionClick = { emoji -> viewModel.toggleReaction(msg.id, emoji) },
                onReply = { viewModel.setReplyTarget(msg) },
                onTogglePin = { viewModel.togglePin(msg.id) },
                onEdit = { viewModel.setEditingMessage(msg) },
                onDelete = { viewModel.submitDelete(msg.id) },
                onDownloadFile = { file -> viewModel.downloadFile(context, file) }
            )
        }

        // Media Lightbox Overlay
        val viewingFile = uiState.viewingMediaFile
        if (viewingFile != null) {
            com.sharkord.android.ui.home.components.chat.MediaLightboxViewer(
                file = viewingFile,
                onClose = { viewModel.setViewingMediaFile(null) },
                onDownload = { viewModel.downloadFile(context, viewingFile) }
            )
        }
    }
}

@Composable
fun CustomEmojiPickerContent(
    customEmojis: List<com.sharkord.android.data.model.Emoji>,
    colors: com.sharkord.android.ui.emojipicker.presentation.EmojiPickerColors,
    onEmojiSelected: (com.sharkord.android.data.model.Emoji) -> Unit
) {
    if (customEmojis.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = ":(",
                color = colors.textColor.copy(alpha = 0.6f),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(id = R.string.common_noCustomEmojis),
                color = colors.textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(id = R.string.common_serverAdminsCanUpload),
                color = colors.textColor.copy(alpha = 0.5f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(id = R.string.common_serverEmojis, customEmojis.size),
                color = colors.textColor.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(8),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(customEmojis.size) { index ->
                    val emoji = customEmojis[index]
                    val customUrl = "${com.sharkord.android.data.network.SharkordClient.currentServerUrl}/public/${emoji.file?.name}"
                    val painter = rememberAsyncImagePainter(customUrl)
                    
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onEmojiSelected(emoji) }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (painter != null) {
                            Image(
                                painter = painter,
                                contentDescription = emoji.name,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

