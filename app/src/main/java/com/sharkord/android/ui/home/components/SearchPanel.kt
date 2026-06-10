package com.sharkord.android.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.sharkord.android.data.model.UnifiedSearchResult
import com.sharkord.android.data.model.UnifiedMessageResult
import com.sharkord.android.data.model.UnifiedFileResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.filled.ArrowBack
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import com.sharkord.android.R
import com.sharkord.android.data.model.User
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.ui.components.AsyncImageState
import com.sharkord.android.ui.components.rememberAsyncImageState

@Composable
fun SearchPanel(
    searchQuery: String,
    isSearching: Boolean,
    searchResults: List<UnifiedSearchResult>?,
    users: List<User>,
    onQueryChange: (String) -> Unit,
    onSearchTrigger: () -> Unit,
    onDismissRequest: () -> Unit,
    onResultClick: (channelId: Int, messageId: Int) -> Unit,
    bgColor: Color,
    cardColor: Color,
    primaryText: Color,
    foregroundText: Color
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Debounce logic
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            delay(300)
            onSearchTrigger()
        }
    }

    LaunchedEffect(Unit) {
        // Small delay to ensure the bottom sheet is fully composed and 
        // animated into view before requesting focus and showing the keyboard.
        delay(100)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    BackHandler(onBack = onDismissRequest)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .systemBarsPadding()
    ) {
        // Header / Search Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismissRequest) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.settings_goBack), tint = foregroundText)
            }
            Spacer(modifier = Modifier.width(4.dp))
            OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text(stringResource(R.string.topbar_searchContent), color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF5865F2),
                        unfocusedBorderColor = cardColor,
                        focusedContainerColor = cardColor,
                        unfocusedContainerColor = cardColor,
                        focusedTextColor = foregroundText,
                        unfocusedTextColor = foregroundText,
                        cursorColor = foregroundText
                    ),
                    shape = RoundedCornerShape(24.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close), tint = Color.Gray)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        onSearchTrigger()
                        keyboardController?.hide()
                    })
                )
            }

            // Results
            Box(modifier = Modifier.weight(1f)) {
                if (isSearching && searchResults == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFF5865F2)
                    )
                } else if (searchResults?.isEmpty() == true) {
                    Text(
                        text = stringResource(R.string.dialogs_noResults),
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (!searchResults.isNullOrEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(searchResults, key = { it.key }) { result ->
                            SearchResultItem(
                                result = result,
                                users = users,
                                cardColor = cardColor,
                                primaryText = primaryText,
                                foregroundText = foregroundText,
                                onClick = {
                                    val (cId, mId) = when (result) {
                                        is UnifiedMessageResult -> result.item.channelId to result.item.id
                                        is UnifiedFileResult -> result.item.channelId to result.item.messageId
                                    }
                                    onResultClick(cId, mId)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

@Composable
fun SearchResultItem(
    result: UnifiedSearchResult,
    users: List<User>,
    cardColor: Color,
    primaryText: Color,
    foregroundText: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cardColor)
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            when (result) {
                is UnifiedMessageResult -> MessageResultContent(
                    result = result,
                    users = users,
                    primaryText = primaryText,
                    foregroundText = foregroundText
                )
                is UnifiedFileResult -> FileResultContent(
                    result = result,
                    primaryText = primaryText,
                    foregroundText = foregroundText
                )
            }
        }
    }
}

@Composable
private fun MessageResultContent(
    result: UnifiedMessageResult,
    users: List<User>,
    primaryText: Color,
    foregroundText: Color
) {
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()) }
    val author = users.find { it.id == result.item.userId }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (result.item.channelIsDm) result.item.channelName else "#${result.item.channelName}",
            color = foregroundText,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Text(
            text = dateFormatter.format(Date(result.createdAt)),
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
    
    Spacer(modifier = Modifier.height(6.dp))
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Avatar
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(0xFF3A3A3A)),
            contentAlignment = Alignment.Center
        ) {
            val avatarUrl = author?.avatar?.name?.let { name ->
                "${SharkordClient.currentServerUrl}/public/$name"
            }
            val avatarState = rememberAsyncImageState(avatarUrl)
            when (avatarState) {
                is AsyncImageState.Success -> Image(
                    painter = avatarState.painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                else -> Text(
                    text = (author?.name ?: "?").take(1).uppercase(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.width(6.dp))
        
        // Author name
        Text(
            text = author?.name ?: stringResource(R.string.settings_unknownValue),
            color = foregroundText,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
    
    Spacer(modifier = Modifier.height(4.dp))
    
    Text(
        text = result.item.plainContent.ifEmpty { stringResource(R.string.common_replyAttachmentFallback) },
        color = primaryText,
        fontSize = 14.sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun FileResultContent(
    result: UnifiedFileResult,
    primaryText: Color,
    foregroundText: Color
) {
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (result.item.channelIsDm) result.item.channelName else "#${result.item.channelName}",
            color = foregroundText,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = dateFormatter.format(Date(result.createdAt)),
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = result.item.file.originalName ?: result.item.file.name,
        color = Color(0xFF5865F2), // Accent color
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    if (!result.item.messageContent.isNullOrEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = result.item.messageContent,
            color = primaryText,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
