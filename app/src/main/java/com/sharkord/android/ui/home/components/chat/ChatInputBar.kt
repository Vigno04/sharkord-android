package com.sharkord.android.ui.home.components.chat

import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import com.sharkord.android.R
import com.sharkord.android.data.model.User
import com.sharkord.android.ui.components.AsyncImageState
import com.sharkord.android.ui.components.rememberAsyncImageState
import com.sharkord.android.ui.home.ChatUiState
import com.sharkord.android.ui.home.components.ChatColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChatInputBar(
    channelName: String,
    users: List<User>,
    uiState: ChatUiState,
    inputText: TextFieldValue,
    onInputTextChanged: (TextFieldValue) -> Unit,
    onSend: (String) -> Unit,
    onCancelReply: () -> Unit,
    onCancelEdit: () -> Unit,
    onFileUpload: (String, ByteArray, String?) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSendAudioRecording: (String, ByteArray) -> Unit,
    isEmojiPickerOpen: Boolean = false,
    onToggleEmojiPicker: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cardColor = ChatColors.CardColor
    val textPrimary = ChatColors.TextPrimary
    val textSecondary = ChatColors.TextSecondary
    val textMuted = ChatColors.TextMuted
    val accentColor = ChatColors.AccentColor
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    // Voice Recorder State
    var isRecording by remember { mutableStateOf(false) }
    var isHoldToRecordMode by remember { mutableStateOf(false) }
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
            Log.e("ChatInputBar", "Failed to start audio recording", e)
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
                if (recordingTimer < 1) {
                    if (file.exists()) file.delete()
                    android.widget.Toast.makeText(context, context.getString(R.string.chat_voiceMessageTooShort), android.widget.Toast.LENGTH_SHORT).show()
                } else if (file.exists() && file.length() > 0) {
                    val fileBytes = file.readBytes()
                    onSendAudioRecording(file.name, fileBytes)
                }
            }
        } catch (e: Exception) {
            Log.e("ChatInputBar", "Failed to stop recording", e)
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

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            android.widget.Toast.makeText(context, context.getString(R.string.chat_microphonePermissionRequired), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // File Picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val contentResolver = context.contentResolver
            // Get original filename
            var originalName = "file.bin"
            if (uri.scheme == "content") {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            originalName = it.getString(index)
                        }
                    }
                }
            }
            if (originalName == "file.bin") {
                originalName = uri.path?.substringAfterLast('/') ?: "file.bin"
            }

            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val fileBytes = inputStream.readBytes()
                    onFileUpload(originalName, fileBytes, uri.toString())
                }
            } catch (e: Exception) {
                Log.e("ChatInputBar", "Failed to read file", e)
            }
        }
    }

    val focusRequester = remember { FocusRequester() }

    val hasText = inputText.text.isNotBlank()

    val baseModifier = modifier.fillMaxWidth()
    val finalModifier = if (isEmojiPickerOpen) {
        baseModifier
    } else {
        baseModifier.navigationBarsPadding()
    }

    Column(
        modifier = finalModifier
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)
    ) {
        // Typing Indicator
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

        // Attachments
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
                        when {
                            file.isImage && file.localUri != null -> {
                                val imgState = rememberAsyncImageState(file.localUri)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF2B2B2B)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when (imgState) {
                                        is AsyncImageState.Success -> Image(
                                            painter = imgState.painter,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        is AsyncImageState.Loading -> CircularProgressIndicator(
                                            color = accentColor,
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 1.5.dp
                                        )
                                        else -> Text("🖼️", fontSize = 16.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            file.isVideo && file.localUri != null -> {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AndroidView(
                                        factory = { ctx ->
                                            android.widget.VideoView(ctx).apply {
                                                setVideoURI(Uri.parse(file.localUri))
                                                setOnPreparedListener { mp -> mp.seekTo(1) }
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            else -> {
                                Text(text = "📎", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                        }

                        Text(
                            text = file.displayName,
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
                                .clickable { onRemoveAttachment(file.id) }
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

        // Reply Preview
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
                            text = stringResource(id = R.string.common_replyingTo, replyAuthor?.name ?: "Unknown"),
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
                        onClick = onCancelReply,
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

        // Edit Preview
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
                        onClick = onCancelEdit,
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

        // Input Field Row
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
                if (!isHoldToRecordMode) {
                    IconButton(onClick = { cancelRecording() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = textSecondary
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(start = if (isHoldToRecordMode) 12.dp else 8.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = String.format("%d:%02d", recordingTimer / 60, recordingTimer % 60),
                    color = textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = if (isHoldToRecordMode) Modifier else Modifier.weight(1f)
                )
                if (isHoldToRecordMode) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(id = R.string.chat_slideToCancel),
                        color = textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // Left: Emoji Smiley button
                IconButton(onClick = {
                    onToggleEmojiPicker()
                    if (isEmojiPickerOpen) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                }) {
                    Icon(
                        imageVector = if (isEmojiPickerOpen) Icons.Default.Keyboard else Icons.Default.SentimentSatisfiedAlt,
                        contentDescription = "Emoji",
                        tint = if (isEmojiPickerOpen) accentColor else textSecondary
                    )
                }

                BasicTextField(
                    value = inputText,
                    onValueChange = onInputTextChanged,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .padding(vertical = 8.dp),
                    textStyle = TextStyle(
                        color = textPrimary,
                        fontSize = 15.sp
                    ),
                    cursorBrush = SolidColor(accentColor),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default
                    ),
                    keyboardActions = KeyboardActions(),
                    maxLines = 6,
                    decorationBox = { innerTextField ->
                        Box {
                            if (inputText.text.isEmpty()) {
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

                // Right: Attach File button
                IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Attach File",
                        tint = textSecondary
                    )
                }
            }

            // Rightmost: Send or Mic
            val showSendButton = hasText || uiState.attachedFiles.isNotEmpty() || uiState.editingMessage != null || (isRecording && !isHoldToRecordMode)
            if (showSendButton) {
                IconButton(
                    onClick = { 
                        if (isRecording && !isHoldToRecordMode) {
                            stopAndSendRecording()
                        } else {
                            onSend(inputText.text)
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
                            contentDescription = "Send",
                            tint = accentColor
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var isLongPress = false
                                var dragCancelled = false
                                var currentDragX = 0f

                                val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                                val longPressJob = coroutineScope.launch {
                                    delay(longPressTimeout)
                                    isLongPress = true
                                    isHoldToRecordMode = true
                                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                        startRecording() 
                                    } else {
                                        audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    }
                                }

                                val pointerId = down.id
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val anyUp = event.changes.any { it.changedToUp() }

                                    if (anyUp) {
                                        longPressJob.cancel()
                                        if (isLongPress) {
                                            if (!dragCancelled) {
                                                stopAndSendRecording()
                                            }
                                        } else {
                                            // Single Tap to Toggle record mode!
                                            isHoldToRecordMode = false
                                            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                startRecording() 
                                            } else {
                                                audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                        break
                                    }

                                    if (isLongPress) {
                                        val change = event.changes.firstOrNull { it.id == pointerId }
                                        if (change != null) {
                                            val positionChange = change.positionChange()
                                            currentDragX += positionChange.x
                                            recordCancelDistance = currentDragX
                                            if (currentDragX < -200f && !dragCancelled) {
                                                dragCancelled = true
                                                cancelRecording()
                                            }
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        }
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Record Voice Message",
                        tint = if (isRecording) Color.Red else textSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun TypingDotsWave() {
    val dots = listOf(
        remember { androidx.compose.animation.core.Animatable(0f) },
        remember { androidx.compose.animation.core.Animatable(0f) },
        remember { androidx.compose.animation.core.Animatable(0f) }
    )

    dots.forEachIndexed { index, animatable ->
        LaunchedEffect(animatable) {
            kotlinx.coroutines.delay(index * 150L)
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                )
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        dots.forEach { animatable ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer { translationY = -animatable.value * 8.dp.toPx() }
                    .background(Color.Gray, CircleShape)
            )
        }
    }
}
