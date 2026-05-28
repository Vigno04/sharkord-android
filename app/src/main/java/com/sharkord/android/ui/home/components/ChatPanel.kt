package com.sharkord.android.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.sharkord.android.ui.home.ChatViewModel
import kotlinx.coroutines.launch
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.text.HtmlCompat

/**
 * Full-screen chat panel for a single text channel.
 *
 * Displays message history, handles real-time updates, and provides a working
 * text input for sending new messages.
 *
 * @param channelId    The id of the currently-selected channel.
 * @param channelName  Display name used in the header.
 * @param users        Full user list from [HomeUiState] — used to resolve author info in [MessageItem].
 * @param roles        Full role list — used to color author names by their top role.
 * @param onBackClick  Called when the user taps the back arrow or completes a swipe-back gesture.
 * @param modifier     Applied to the root [Column].
 */
@Composable
fun ChatPanel(
    channelId: Int,
    channelName: String,
    users: List<User>,
    roles: List<Role>,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel(key = "chat_$channelId")
) {
    // Discord-themed colors (matching HomeScreen palette)
    val bgColor = Color(0xFF1C1C1C)
    val headerColor = Color(0xFF242424)
    val cardColor = Color(0xFF2B2B2B)
    val textPrimary = Color(0xFFE8E8E8)
    val textSecondary = Color(0xFF9E9E9E)
    val textMuted = Color(0xFF6E6E6E)
    val accentColor = Color(0xFF5B9BD5)

    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }

    // Initialise the ViewModel for this channel
    LaunchedEffect(channelId) {
        viewModel.init(channelId)
    }

    // Scroll to the bottom whenever the message list grows (new message received/sent)
    val messageCount = uiState.messages.size
    LaunchedEffect(messageCount) {
        if (messageCount > 0) {
            listState.animateScrollToItem(messageCount - 1)
        }
    }

    // Trigger pagination when the user scrolls to the top
    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisibleIndex) {
        if (firstVisibleIndex == 0 && !uiState.isLoadingHistory && !uiState.isLoadingOlder && !uiState.hasReachedTop) {
            viewModel.loadOlderMessages()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
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
                    .clickable { /* Pinned messages — future feature */ }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "Pinned Messages",
                    tint = textSecondary,
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
                    .clickable { viewModel.clearError() },
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
                    // Full-screen loading while the first page loads
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
                            text = "Loading messages…",
                            color = textMuted,
                            fontSize = 14.sp
                        )
                    }
                }

                uiState.messages.isEmpty() && !uiState.isLoadingHistory -> {
                    // Empty state — channel has no messages yet
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
                            text = "Welcome to #$channelName!",
                            color = textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This is the beginning of the #$channelName channel.",
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
                        // Pagination indicator at the top
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
                                    text = "This is the beginning of #$channelName",
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
                                onLongClick = { target ->
                                    viewModel.setReplyTarget(target)
                                },
                                onReplyClick = { parentId ->
                                    val targetIndex = uiState.messages.indexOfFirst { it.id == parentId }
                                    if (targetIndex != -1) {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(targetIndex)
                                        }
                                    }
                                }
                            )
                        }

                        // Bottom spacer so the last message isn't hidden behind the input bar
                        item(key = "bottom_spacer") {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
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
                                text = "Replying to ${replyAuthor?.name ?: "Unknown"}",
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

            // Input field row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = cardColor,
                        shape = if (uiState.replyTarget != null) {
                            RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                        } else {
                            RoundedCornerShape(24.dp)
                        }
                    )
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            // Add attachment button (no-op for now)
            IconButton(onClick = { /* Future: file upload */ }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Attach File",
                    tint = textSecondary
                )
            }

            // Text input field
            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
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
                        if (hasText && !uiState.isSending) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    }
                ),
                maxLines = 6,
                decorationBox = { innerTextField ->
                    Box {
                        if (inputText.isEmpty()) {
                            Text(
                                text = "Message #$channelName",
                                color = textMuted,
                                fontSize = 15.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Emoji button (no-op for now)
            IconButton(onClick = { /* Future: emoji picker */ }) {
                Icon(
                    imageVector = Icons.Default.SentimentSatisfiedAlt,
                    contentDescription = "Emoji",
                    tint = textSecondary
                )
            }

            // Send / Mic toggle
            AnimatedVisibility(visible = hasText) {
                IconButton(
                    onClick = {
                        if (!uiState.isSending) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
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
                            contentDescription = "Send Message",
                            tint = accentColor
                        )
                    }
                }
            }
            AnimatedVisibility(visible = !hasText) {
                IconButton(onClick = { /* Future: voice message */ }) {
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
