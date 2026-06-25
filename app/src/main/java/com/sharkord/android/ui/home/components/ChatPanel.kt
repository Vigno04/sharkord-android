package com.sharkord.android.ui.home.components

import com.sharkord.android.ui.theme.SharkordTheme
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
    targetMessageId: Int? = null,
    jumpTrigger: Long = 0L,
    channelName: String,
    isDm: Boolean = false,
    dmUser: User? = null,
    users: List<User>,
    roles: List<Role>,
    customEmojis: List<Emoji>,
    isActive: Boolean = true,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel(key = "chat_$channelId")
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val bgColor = SharkordTheme.colors.bgColor
    val cardColor = SharkordTheme.colors.cardColor
    val textPrimary = SharkordTheme.colors.primaryText
    val textSecondary = SharkordTheme.colors.primaryText.copy(alpha = 0.5f)
    val textMuted = SharkordTheme.colors.primaryText.copy(alpha = 0.5f)
    val accentColor = SharkordTheme.colors.accentColor

    val uiState by viewModel.uiState.collectAsState()
    val listState = remember(channelId) { androidx.compose.foundation.lazy.LazyListState() }
    val coroutineScope = rememberCoroutineScope()
    var inputText by remember(channelId) { mutableStateOf(TextFieldValue("")) }

    val ownUserId = uiState.ownUserId

    var showMenuMessage by remember(channelId) { mutableStateOf<Message?>(null) }
    var isEmojiPickerOpen by remember(channelId) { mutableStateOf(false) }
    var playingHighlightId by remember(channelId) { mutableStateOf<Int?>(null) }
    var isJumping by remember(channelId, jumpTrigger) { mutableStateOf(targetMessageId != null) }

    LaunchedEffect(playingHighlightId) {
        if (playingHighlightId != null) {
            isJumping = true
            kotlinx.coroutines.delay(2000)
            playingHighlightId = null
            isJumping = false
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    // initialize ViewModel
    LaunchedEffect(channelId, targetMessageId, jumpTrigger) {
        if (targetMessageId != null) {
            val index = uiState.messages.indexOfFirst { it.id == targetMessageId }
            if (index != -1) {
                // the message is already loaded. Scroll to it directly
                val headerOffset = if (uiState.isLoadingOlder || (uiState.hasReachedTop && uiState.messages.isNotEmpty())) 1 else 0
                val viewportHeight = listState.layoutInfo.viewportSize.height
                // offset by roughly a third of the viewport height to position the target message
                // towards the center rather than stuck at the top/bottom edge
                val offset = if (viewportHeight > 0) -(viewportHeight / 3) else -500
                listState.scrollToItem(index + headerOffset, offset)
                playingHighlightId = targetMessageId
                return@LaunchedEffect
            }
        }
        viewModel.init(channelId, targetMessageId)
    }

    // jump to message
    val jumpTarget = uiState.jumpTargetMessageId
    LaunchedEffect(jumpTarget, uiState.messages) {
        if (jumpTarget != null && uiState.messages.isNotEmpty()) {
            val index = uiState.messages.indexOfFirst { it.id == jumpTarget }
            if (index != -1) {
                val headerOffset = if (uiState.isLoadingOlder || (uiState.hasReachedTop && uiState.messages.isNotEmpty())) 1 else 0
                val viewportHeight = listState.layoutInfo.viewportSize.height
                // offset by roughly a third of the viewport height to position the target message
                // towards the center rather than stuck at the top/bottom edge
                val offset = if (viewportHeight > 0) -(viewportHeight / 3) else -500
                listState.scrollToItem(index + headerOffset, offset)
                playingHighlightId = jumpTarget
                kotlinx.coroutines.delay(50) // Prevent race condition with lastMessageId effect
                viewModel.clearJumpTarget()
            }
        }
    }

    // edit mode: populate input field
    LaunchedEffect(uiState.editingMessage) {
        val editing = uiState.editingMessage
        if (editing != null) {
            val text = HtmlCompat.fromHtml(editing.content, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
            inputText = TextFieldValue(text = text, selection = TextRange(text.length))
        } else {
            inputText = TextFieldValue("")
        }
    }

    // auto-scroll to bottom
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
        
        // do not auto-scroll to bottom if there is a jump target
        if (uiState.jumpTargetMessageId != null) return@LaunchedEffect

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

    // paginate old messages & retain scroll position
    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    
    var previousMessagesSize by remember(channelId) { mutableIntStateOf(0) }
    var previousFirstVisibleId by remember(channelId) { mutableStateOf<Int?>(null) }
    var previousFirstVisibleOffset by remember(channelId) { mutableIntStateOf(0) }
    var previousFirstVisibleIndex by remember(channelId) { mutableIntStateOf(0) }

    LaunchedEffect(firstVisibleIndex, listState.firstVisibleItemScrollOffset) {
        val headerOffset = if (uiState.isLoadingOlder || (uiState.hasReachedTop && uiState.messages.isNotEmpty())) 1 else 0
        if (uiState.messages.isNotEmpty()) {
            if (firstVisibleIndex >= headerOffset && firstVisibleIndex - headerOffset < uiState.messages.size) {
                previousFirstVisibleId = uiState.messages[firstVisibleIndex - headerOffset].id
                previousFirstVisibleOffset = listState.firstVisibleItemScrollOffset
                previousFirstVisibleIndex = firstVisibleIndex - headerOffset
            } else if (firstVisibleIndex < headerOffset) {
                previousFirstVisibleId = uiState.messages[0].id
                previousFirstVisibleOffset = listState.firstVisibleItemScrollOffset
                previousFirstVisibleIndex = 0
            }
        }
    }

    LaunchedEffect(uiState.messages.size) {
        val newSize = uiState.messages.size
        val oldSize = previousMessagesSize
        previousMessagesSize = newSize

        if (oldSize > 0 && newSize > oldSize) {
            val prevFirstId = previousFirstVisibleId
            if (prevFirstId != null) {
                val newIndex = uiState.messages.indexOfFirst { it.id == prevFirstId }
                // Only adjust scroll if items were prepended (pushing the old first item down)
                if (newIndex > previousFirstVisibleIndex) {
                    val headerOffset = if (uiState.isLoadingOlder || (uiState.hasReachedTop && uiState.messages.isNotEmpty())) 1 else 0
                    listState.scrollToItem(newIndex + headerOffset, previousFirstVisibleOffset)
                    previousFirstVisibleIndex = newIndex
                }
            }
        }
    }

    LaunchedEffect(firstVisibleIndex) {
        if (firstVisibleIndex <= 5 && !uiState.isLoadingHistory && !uiState.isLoadingOlder && !uiState.hasReachedTop) {
            viewModel.loadOlderMessages()
        }
    }

    // helper to close the keyboard
    val dismissInputPanel = {
        if (isEmojiPickerOpen) {
            isEmojiPickerOpen = false
        }
        keyboardController?.hide()
    }

    // system back button interception
    BackHandler(enabled = isActive) {
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

            // top Bar
            ChatTopBar(
                channelName = channelName,
                isDm = isDm,
                dmUser = dmUser,
                showPinnedMessages = uiState.showPinnedMessages,
                onBackClick = onBackClick,
                onTogglePinnedMessages = { viewModel.setPinnedMessagesVisible(!uiState.showPinnedMessages) }
            )

            // error Banner
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

            // message List Area
            // tapping anywhere in this area dismisses the keyboard,
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
                                    isHighlighted = message.id == playingHighlightId,
                                    fullscreenMediaId = uiState.viewingMediaFile?.id,
                                    onLongClick = { target -> showMenuMessage = target },
                                    onReplyClick = { parentId ->
                                        val targetIndex = uiState.messages.indexOfFirst { it.id == parentId }
                                        if (targetIndex != -1) {
                                            val headerOffset = if (uiState.isLoadingOlder || (uiState.hasReachedTop && uiState.messages.isNotEmpty())) 1 else 0
                                            val viewportHeight = listState.layoutInfo.viewportSize.height
                                            // offset by roughly a third of the viewport height to position the target message
                                            // towards the center rather than stuck at the top/bottom edge
                                            val offset = if (viewportHeight > 0) -(viewportHeight / 3) else -500
                                            coroutineScope.launch {
                                                listState.animateScrollToItem(targetIndex + headerOffset, offset)
                                                playingHighlightId = parentId
                                            }
                                        } else {
                                            android.widget.Toast.makeText(context, context.getString(R.string.chat_referencedMessageNotLoaded), android.widget.Toast.LENGTH_SHORT).show()
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

                // new Messages Pill
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
                            color = SharkordTheme.colors.foregroundText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = SharkordTheme.colors.foregroundText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // input Bar
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
                    
                    var processedText = text
                    customEmojis.forEach { emoji ->
                        val code = ":${emoji.name}:"
                        if (processedText.contains(code)) {
                            val url = "/public/${emoji.file?.name}"
                            val htmlEmoji = """<span class="emoji-image" data-type="emoji" data-name="${emoji.name}"><img src="$url" alt="$code" class="emoji-image"></span>"""
                            processedText = processedText.replace(code, htmlEmoji)
                        }
                    }

                    if (editing != null) {
                        viewModel.submitEdit(editing.id, processedText)
                    } else {
                        viewModel.sendMessage(processedText)
                    }
                    inputText = TextFieldValue("")
                },
                onCancelReply = { viewModel.setReplyTarget(null) },
                onCancelEdit = { viewModel.setEditingMessage(null) },
                onFileUpload = { name, uri -> viewModel.uploadAndAttachFile(context, name, uri) },
                onRemoveAttachment = { id -> viewModel.removeAttachedFile(id) },
                onSendAudioRecording = { name, bytes -> viewModel.sendAudioVoiceNote(name, bytes) },
                isEmojiPickerOpen = isEmojiPickerOpen,
                onToggleEmojiPicker = {
                    if (!isEmojiPickerOpen) {
                        // keyboard → Emoji: open emoji picker, hide keyboard
                        isEmojiPickerOpen = true
                        keyboardController?.hide()
                    }
                    // emoji → Keyboard: do nothing here
                    // chatInputBar will request focus and show the keyboard
                    // the LaunchedEffect below closes emoji once the keyboard appears
                }
            )

            // bottom Panel (keyboard / emoji space)
            com.sharkord.android.ui.home.components.chat.ChatBottomPanel(
                isEmojiPickerOpen = isEmojiPickerOpen,
                onCloseEmojiPicker = { isEmojiPickerOpen = false },
                customEmojis = customEmojis,
                inputText = inputText,
                onInputTextChanged = { textVal ->
                    inputText = textVal
                },
                onType = { text -> viewModel.onType(text) },
                isAtBottom = isAtBottom && !isJumping,
                messagesCount = uiState.messages.size,
                listState = listState,
                bgColor = bgColor,
                cardColor = cardColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                accentColor = accentColor
            )
        }

        // pinned Messages Overlay
        if (uiState.showPinnedMessages) {
            PinnedMessagesSheet(
                isLoading = uiState.isLoadingPinned,
                pinnedMessages = uiState.pinnedMessages,
                users = users,
                onClose = { viewModel.setPinnedMessagesVisible(false) },
                onUnpin = { viewModel.togglePin(it) }
            )
        }

        // context Menu Overlay
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

        // media Lightbox Overlay
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

