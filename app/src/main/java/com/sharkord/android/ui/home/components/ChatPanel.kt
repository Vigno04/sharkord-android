package com.sharkord.android.ui.home.components

import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sharkord.android.data.model.Role
import com.sharkord.android.data.model.User
import com.sharkord.android.data.model.Emoji
import com.sharkord.android.data.model.Message
import com.sharkord.android.ui.home.ChatViewModel
import kotlinx.coroutines.launch
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Reply
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.text.HtmlCompat
import com.sharkord.android.data.network.ConnectionState
import com.sharkord.android.data.network.SharkordClient
import androidx.compose.ui.res.stringResource
import com.sharkord.android.R
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.sharkord.android.ui.components.rememberAsyncImagePainter
import kotlinx.coroutines.delay

/**
 * Full-screen chat panel for a single text channel.
 *
 * Displays message history, handles real-time updates, custom reactions,
 * WhatsApp-style audio recording, pinned messages overlay, and file attachments.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatPanel(
    channelId: Int,
    channelName: String,
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
    val headerColor = ChatColors.HeaderColor
    val cardColor = ChatColors.CardColor
    val textPrimary = ChatColors.TextPrimary
    val textSecondary = ChatColors.TextSecondary
    val textMuted = ChatColors.TextMuted
    val accentColor = ChatColors.AccentColor

    val uiState by viewModel.uiState.collectAsState()
    val listState = remember(channelId) { androidx.compose.foundation.lazy.LazyListState() }
    val coroutineScope = rememberCoroutineScope()
    var inputText by remember(channelId) { mutableStateOf("") }

    val ownUserId = uiState.ownUserId

    // State for message long-press menu overlay
    var showMenuMessage by remember(channelId) { mutableStateOf<Message?>(null) }

    // Initialise the ViewModel for this channel
    LaunchedEffect(channelId) {
        viewModel.init(channelId)
    }

    // Pre-fill inputText if we are in editing mode
    LaunchedEffect(uiState.editingMessage) {
        val editing = uiState.editingMessage
        if (editing != null) {
            inputText = HtmlCompat.fromHtml(editing.content, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
        } else {
            inputText = ""
        }
    }

    // True when the last visible item is within 2 slots of the end of the list
    val isAtBottom by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index
                ?: return@derivedStateOf true
            lastVisible >= layout.totalItemsCount - 2
        }
    }

    // Flag to track if the initial load of messages has completed and list was scrolled/positioned at the bottom.
    var hasInitialLoaded by remember(channelId) { mutableStateOf(false) }

    // Badge counter: how many new messages arrived while the user was scrolled up
    var unreadNewCount by remember(channelId) { mutableIntStateOf(0) }

    // Watch the ID of the very last message so we react only when a *new* message
    // is appended — not when older messages are prepended by pagination.
    val lastMessageId = uiState.messages.lastOrNull()?.id
    LaunchedEffect(lastMessageId) {
        if (lastMessageId == null) return@LaunchedEffect
        if (!hasInitialLoaded) {
            // First load: snap instantly to the bottom, don't animate, and don't count as unread messages.
            if (uiState.messages.isNotEmpty()) {
                listState.scrollToItem(uiState.messages.size - 1)
            }
            hasInitialLoaded = true
            unreadNewCount = 0
            return@LaunchedEffect
        }
        if (isAtBottom) {
            listState.animateScrollToItem(uiState.messages.size - 1)
            unreadNewCount = 0
        } else {
            unreadNewCount++
        }
    }

    // As soon as the user scrolls back to the bottom, clear the badge
    LaunchedEffect(isAtBottom) {
        if (isAtBottom) unreadNewCount = 0
    }

    // Trigger pagination when scrolling to the top
    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisibleIndex) {
        if (firstVisibleIndex == 0 && !uiState.isLoadingHistory && !uiState.isLoadingOlder && !uiState.hasReachedTop) {
            viewModel.loadOlderMessages()
        }
    }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val contentResolver = context.contentResolver
            val originalName = getFileNameFromUri(context, uri) ?: "file.bin"
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val fileBytes = inputStream.readBytes()
                    viewModel.uploadAndAttachFile(originalName, fileBytes)
                }
            } catch (e: Exception) {
                Log.e("ChatPanel", "Failed to read file", e)
            }
        }
    }

    // WhatsApp-Style Voice Recorder State
    var isRecording by remember { mutableStateOf(false) }
    var recordingTimer by remember { mutableIntStateOf(0) }
    var recordCancelDistance by remember { mutableFloatStateOf(0f) }
    var hasSwipeCancelled by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    var audioFilepath by remember { mutableStateOf<String?>(null) }

    val startRecording = {
        try {
            val file = java.io.File(context.cacheDir, "voice_note_${System.currentTimeMillis()}.m4a")
            audioFilepath = file.absolutePath
            recordCancelDistance = 0f
            hasSwipeCancelled = false

            val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.media.MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                android.media.MediaRecorder()
            }.apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            isRecording = true

            // Trigger slight haptic rumble
            try {
                val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }
            } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.e("ChatPanel", "Failed to start audio recording", e)
        }
    }

    val stopAndSendRecording = {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            if (!hasSwipeCancelled && audioFilepath != null) {
                val file = java.io.File(audioFilepath!!)
                if (file.exists() && file.length() > 0) {
                    val fileBytes = file.readBytes()
                    viewModel.sendAudioVoiceNote(file.name, fileBytes)
                }
            }
        } catch (e: Exception) {
            Log.e("ChatPanel", "Failed to stop recording", e)
            mediaRecorder = null
            isRecording = false
        }
    }

    val cancelRecording = {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {}
        mediaRecorder = null
        isRecording = false
        hasSwipeCancelled = true
        recordCancelDistance = 0f
        audioFilepath?.let { java.io.File(it).delete() }
    }

    // Increment recording timer stopwatch (capped at 2 minutes auto-send)
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingTimer = 0
            while (isRecording) {
                delay(1000)
                recordingTimer++
                if (recordingTimer >= 120) {
                    stopAndSendRecording()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── 1. TOP HEADER BAR ──────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerColor)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onBackClick)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Channels",
                        tint = textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = "# $channelName",
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { viewModel.setPinnedMessagesVisible(true) }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned Messages",
                        tint = if (uiState.showPinnedMessages) accentColor else textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

            // ── 2. ERROR BANNER ────────────────────────────────────
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
                    Text(
                        text = "✕",
                        color = Color(0xFFEF4444),
                        fontSize = 13.sp
                    )
                }
            }

            // ── 3. MESSAGE LIST ────────────────────────────────────
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
                            modifier = Modifier.fillMaxSize(),
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
                                    onLongClick = { target ->
                                        showMenuMessage = target
                                    },
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
                                    }
                                )
                            }

                            item(key = "bottom_spacer") {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }

                // ── Floating "new messages" pill ───────────────────
                androidx.compose.animation.AnimatedVisibility(
                    visible = unreadNewCount > 0,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    enter = fadeIn() + slideInVertically { fullHeight -> fullHeight },
                    exit  = fadeOut() + slideOutVertically { fullHeight -> fullHeight }
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

            // ── 4. TEXT INPUT BAR ──────────────────────────────────
            val hasText = inputText.isNotBlank()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)
            ) {
                // Typing Indicator bar
                val typingUserIds = uiState.typingUsers
                if (typingUserIds.isNotEmpty()) {
                    val typingNames = typingUserIds.map { id -> users.find { it.id == id }?.name ?: stringResource(id = R.string.chat_someoneTyping) }
                    val typingText = when {
                        typingNames.size == 1 -> stringResource(id = R.string.chat_userIsTyping, typingNames[0])
                        typingNames.size == 2 -> stringResource(id = R.string.chat_usersAreTyping, typingNames[0], typingNames[1])
                        else -> stringResource(id = R.string.chat_severalPeopleAreTyping)
                    }
                    Row(
                        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = typingText,
                            color = textSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        TypingDotsWave()
                    }
                }

                // File Attachment Previews Row
                if (uiState.attachedFiles.isNotEmpty() || uiState.isUploadingAttachment) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        uiState.attachedFiles.forEach { file ->
                            Row(
                                modifier = Modifier
                                    .background(cardColor, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📎 ${file.originalName ?: file.name}",
                                    color = textPrimary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 120.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = textSecondary,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { viewModel.removeAttachedFile(file.id) }
                                )
                            }
                        }
                        if (uiState.isUploadingAttachment) {
                            Row(
                                modifier = Modifier
                                    .background(cardColor, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = accentColor,
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(id = R.string.chat_uploading),
                                    color = textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Reply Preview Bar
                AnimatedVisibility(
                    visible = uiState.replyTarget != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val target = uiState.replyTarget
                    if (target != null) {
                        val replyAuthor = users.find { it.id == target.userId }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = Color(0xFF242424),
                                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(id = R.string.common_replyingTo, replyAuthor?.name ?: stringResource(id = R.string.common_unknownUser)),
                                    color = textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val snippet = remember(target.content) {
                                    HtmlCompat.fromHtml(target.content ?: "", HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                                }
                                Text(
                                    text = snippet,
                                    color = textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { viewModel.setReplyTarget(null) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel Reply",
                                    tint = textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Edit Preview Bar
                AnimatedVisibility(
                    visible = uiState.editingMessage != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val target = uiState.editingMessage
                    if (target != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = Color(0xFF242424),
                                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(id = R.string.common_editMessage),
                                    color = accentColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val snippet = remember(target.content) {
                                    HtmlCompat.fromHtml(target.content ?: "", HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                                }
                                Text(
                                    text = snippet,
                                    color = textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { viewModel.setEditingMessage(null) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel Edit",
                                    tint = textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Input field row (transitions to Voice Recording state)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = cardColor,
                            shape = if (uiState.replyTarget != null || uiState.editingMessage != null) {
                                RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                            } else {
                                RoundedCornerShape(24.dp)
                            }
                        )
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRecording) {
                        // PULSING RED DOT
                        Box(
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.Red)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = String.format("%d:%02d", recordingTimer / 60, recordingTimer % 60),
                            color = textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = stringResource(id = R.string.chat_slideToCancel),
                            color = textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { cancelRecording() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel",
                                tint = textSecondary
                            )
                        }
                    } else {
                        // Attachment Add Button
                        IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Attach File",
                                tint = textSecondary
                            )
                        }

                        // Text input field
                        BasicTextField(
                            value = inputText,
                            onValueChange = {
                                inputText = it
                                viewModel.onType(it)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp),
                            textStyle = TextStyle(
                                color = textPrimary,
                                fontSize = 15.sp
                            ),
                            cursorBrush = SolidColor(accentColor),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Send
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    val editing = uiState.editingMessage
                                    if (editing != null) {
                                        viewModel.submitEdit(editing.id, inputText)
                                    } else {
                                        viewModel.sendMessage(inputText)
                                    }
                                    inputText = ""
                                }
                            ),
                            maxLines = 6,
                            decorationBox = { innerTextField ->
                                Box {
                                    if (inputText.isEmpty()) {
                                        Text(
                                            text = stringResource(id = R.string.chat_messageChannelPlaceholder, channelName),
                                            color = textMuted,
                                            fontSize = 15.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        // Emoji button (no-op placeholder)
                        IconButton(onClick = { /* Future: emoticon picker */ }) {
                            Icon(
                                imageVector = Icons.Default.SentimentSatisfiedAlt,
                                contentDescription = "Emoji",
                                tint = textSecondary
                            )
                        }

                        // Send / Mic WhatsApp-style recorder gesture toggle
                        if (hasText || uiState.attachedFiles.isNotEmpty() || uiState.editingMessage != null) {
                            IconButton(
                                onClick = {
                                    val editing = uiState.editingMessage
                                    if (editing != null) {
                                        viewModel.submitEdit(editing.id, inputText)
                                    } else {
                                        viewModel.sendMessage(inputText)
                                    }
                                    inputText = ""
                                },
                                enabled = !uiState.isSending
                            ) {
                                if (uiState.isSending) {
                                    CircularProgressIndicator(
                                        color = accentColor,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = accentColor
                                    )
                                }
                            }
                        } else {
                            // Touch handles for WhatsApp-like voice record
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .pointerInput(Unit) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { startRecording() },
                                            onDragEnd = { stopAndSendRecording() },
                                            onDragCancel = { cancelRecording() },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                recordCancelDistance += dragAmount.x
                                                if (recordCancelDistance < -200f) {
                                                    if (!hasSwipeCancelled) {
                                                        cancelRecording()
                                                    }
                                                }
                                            }
                                        )
                                    }
                                    .padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Record Voice Message",
                                    tint = textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 5. PINNED MESSAGES SHEET OVERLAY ───────────────────
        if (uiState.showPinnedMessages) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { viewModel.setPinnedMessagesVisible(false) }
            ) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.6f)
                        .clickable(enabled = false, onClick = {}),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = bgColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.common_pinnedMessagesTitle),
                                color = textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { viewModel.setPinnedMessagesVisible(false) }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = textSecondary)
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 8.dp))

                        if (uiState.isLoadingPinned) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = accentColor)
                            }
                        } else if (uiState.pinnedMessages.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = stringResource(id = R.string.common_noPinnedMessages), color = textMuted, fontSize = 14.sp)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(uiState.pinnedMessages) { _, msg ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(cardColor, RoundedCornerShape(8.dp))
                                            .padding(12.dp)
                                            .padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            val authorName = users.find { it.id == msg.userId }?.name ?: "Unknown"
                                            Text(text = authorName, color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            val pinPlain = remember(msg.content) {
                                                HtmlCompat.fromHtml(msg.content, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                                            }
                                            Text(text = pinPlain, color = textPrimary, fontSize = 14.sp)
                                        }
                                        IconButton(onClick = { viewModel.togglePin(msg.id) }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Unpin",
                                                tint = Color.Red.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 6. CONTEXT MENU BOTTOM SHEET OVERLAY ───────────────
        if (showMenuMessage != null) {
            val msg = showMenuMessage!!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showMenuMessage = null }
            ) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clickable(enabled = false, onClick = {}),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = headerColor)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .navigationBarsPadding()
                    ) {
                        // Quick Reactions Selection Row
                        Text(
                            text = stringResource(id = R.string.common_addReaction),
                            color = textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(
                                "thumbsup" to "👍",
                                "heart" to "❤️",
                                "joy" to "😂",
                                "fire" to "🔥",
                                "open_mouth" to "😮"
                            ).forEach { (shortcode, char) ->
                                Text(
                                    text = char,
                                    fontSize = 28.sp,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            viewModel.toggleReaction(msg.id, shortcode)
                                            showMenuMessage = null
                                        }
                                        .padding(8.dp)
                                )
                            }
                        }

                        // Custom Emojis Grid (if available)
                        if (customEmojis.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(id = R.string.common_customTab),
                                color = textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                customEmojis.forEach { emoji ->
                                    val customUrl = "${SharkordClient.currentServerUrl}/public/${emoji.file?.name}"
                                    val customPainter = rememberAsyncImagePainter(customUrl)
                                    if (customPainter != null) {
                                        Image(
                                            painter = customPainter,
                                            contentDescription = emoji.name,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .clickable {
                                                    viewModel.toggleReaction(msg.id, emoji.name)
                                                    showMenuMessage = null
                                                }
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))

                        // Standard operations: Reply
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setReplyTarget(msg)
                                    showMenuMessage = null
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Reply, contentDescription = "Reply", tint = textPrimary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = stringResource(id = R.string.common_replyToMessage), color = textPrimary, fontSize = 15.sp)
                        }

                        // Toggle Pin
                        val canPin = remember(uiState.ownUserId, roles, users) {
                            uiState.canPinMessage(roles, users)
                        }
                        if (canPin) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.togglePin(msg.id)
                                        showMenuMessage = null
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PushPin, contentDescription = "Pin", tint = textPrimary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(id = if (msg.pinned) R.string.common_unpinMessage else R.string.common_pinMessage),
                                    color = textPrimary,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        // Edit (if authorized)
                        val canManage = remember(msg, uiState.ownUserId, roles, users) {
                            uiState.canManageMessage(msg, roles, users)
                        }
                        if (canManage && msg.editable) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.setEditingMessage(msg)
                                        showMenuMessage = null
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = textPrimary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = stringResource(id = R.string.common_editMessage), color = textPrimary, fontSize = 15.sp)
                            }
                        }

                        // Delete (if authorized)
                        if (canManage) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.submitDelete(msg.id)
                                        showMenuMessage = null
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.8f))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = stringResource(id = R.string.common_deleteMessageTitle), color = Color.Red.copy(alpha = 0.8f), fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────

fun getFileNameFromUri(context: android.content.Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
    }
    if (name == null) {
        name = uri.path?.substringAfterLast('/')
    }
    return name
}

// Helpers were moved into ChatUiState in ChatViewModel.kt

@Composable
fun TypingDotsWave(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typingDots")

    @Composable
    fun Dot(delayMs: Int) {
        val yOffset by transition.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1000
                    0f at delayMs with FastOutSlowInEasing
                    -4f at delayMs + 150 with FastOutSlowInEasing
                    0f at delayMs + 300
                    0f at 1000
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "yOffset"
        )
        Box(
            modifier = Modifier
                .size(4.dp)
                .offset(y = yOffset.dp)
                .clip(CircleShape)
                .background(Color(0xFF9E9E9E))
        )
    }

    Row(
        modifier = modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dot(delayMs = 0)
        Dot(delayMs = 150)
        Dot(delayMs = 300)
    }
}
