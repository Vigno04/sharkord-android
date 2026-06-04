package com.sharkord.android.ui.home.components.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.sharkord.android.R
import com.sharkord.android.data.model.Emoji
import com.sharkord.android.ui.emojipicker.presentation.EmojiPickerContent
import com.sharkord.android.ui.emojipicker.presentation.EmojiPickerDefaults
import com.sharkord.android.ui.home.components.CustomEmojiPickerContent
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatBottomPanel(
    isEmojiPickerOpen: Boolean,
    onCloseEmojiPicker: () -> Unit,
    customEmojis: List<Emoji>,
    inputText: TextFieldValue,
    onInputTextChanged: (TextFieldValue) -> Unit,
    onType: (String) -> Unit,
    isAtBottom: Boolean,
    messagesCount: Int,
    listState: LazyListState,
    bgColor: Color,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    val isImeVisible = WindowInsets.isImeVisible
    val imeAboveNavPx = (imeBottomPx - navBottomPx).coerceAtLeast(0)

    val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    var storedKbAboveNavPx by remember { mutableIntStateOf(prefs.getInt("keyboard_height_above_nav", 0)) }

    // Persist keyboard height when it settles
    LaunchedEffect(imeAboveNavPx) {
        val minKbPx = with(density) { 150.dp.toPx() }
        if (imeAboveNavPx > minKbPx) {
            kotlinx.coroutines.delay(250)
            if (storedKbAboveNavPx != imeAboveNavPx) {
                storedKbAboveNavPx = imeAboveNavPx
                prefs.edit().putInt("keyboard_height_above_nav", imeAboveNavPx).apply()
            }
        }
    }

    // Emoji → Keyboard: close emoji after the keyboard has appeared
    LaunchedEffect(isImeVisible) {
        if (isImeVisible && isEmojiPickerOpen) {
            kotlinx.coroutines.delay(300)
            onCloseEmojiPicker()
        }
    }

    // Bottom panel height = max(current IME above nav, emoji target)
    val fallbackAboveNavPx = with(density) { 300.dp.toPx().toInt() }
    val emojiTargetPx = if (isEmojiPickerOpen) {
        if (storedKbAboveNavPx > 0) storedKbAboveNavPx else fallbackAboveNavPx
    } else 0
    
    val animatedEmojiPx by androidx.compose.animation.core.animateIntAsState(
        targetValue = emojiTargetPx,
        animationSpec = if (imeAboveNavPx > 0) {
            androidx.compose.animation.core.snap()
        } else {
            androidx.compose.animation.core.spring()
        },
        label = "emojiPanelAnim"
    )
    
    val bottomPanelPx = maxOf(imeAboveNavPx, animatedEmojiPx)
    val bottomPanelDp = with(density) { bottomPanelPx.toDp() }

    var wasAtBottom by remember { mutableStateOf(true) }

    LaunchedEffect(isImeVisible, isEmojiPickerOpen) {
        wasAtBottom = isAtBottom
    }

    LaunchedEffect(bottomPanelDp) {
        if (wasAtBottom && messagesCount > 0) {
            listState.scrollToItem(messagesCount - 1)
        }
    }

    var selectedEmojiTab by remember { mutableStateOf("standard") }

    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val emojiViewModel: com.sharkord.android.ui.emojipicker.presentation.EmojiPickerViewModel = remember {
        com.sharkord.android.ui.emojipicker.di.RepositoryModule.provideEmojiPickerViewModel(context)
    }

    LaunchedEffect(isEmojiPickerOpen) {
        if (isEmojiPickerOpen) {
            selectedEmojiTab = "standard"
        }
        emojiViewModel.onSearchTextChanged("")
        gridState.scrollToItem(0)
    }

    if (bottomPanelDp > 0.dp) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(bottomPanelDp)
                .background(bgColor)
        ) {
            if (isEmojiPickerOpen) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TabRow(
                        selectedTabIndex = if (selectedEmojiTab == "standard") 0 else 1,
                        containerColor = cardColor,
                        contentColor = textPrimary
                    ) {
                        Tab(
                            selected = selectedEmojiTab == "standard",
                            onClick = { selectedEmojiTab = "standard" },
                            text = { Text(text = stringResource(id = R.string.common_emojiTab), fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                        )
                        Tab(
                            selected = selectedEmojiTab == "custom",
                            onClick = { selectedEmojiTab = "custom" },
                            text = { Text(text = stringResource(id = R.string.common_customTab), fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (selectedEmojiTab == "standard") {
                            EmojiPickerContent(
                                state = emojiViewModel.state.collectAsState().value,
                                gridState = gridState,
                                colors = EmojiPickerDefaults.emojiPickerColors(
                                    backgroundColor = cardColor,
                                    textColor = textPrimary,
                                    searchBarBackgroundColor = bgColor,
                                    searchBarIconTint = textSecondary,
                                    searchBarTextColor = textPrimary,
                                    activeCategoryTint = accentColor,
                                    inactiveCategoryTint = textSecondary
                                ),
                                onCategoryTabClick = { index ->
                                    coroutineScope.launch { gridState.animateScrollToItem(index) }
                                },
                                onSearchTextChange = emojiViewModel::onSearchTextChanged,
                                onEmojiSelected = { emoji ->
                                    val currentText = inputText.text
                                    val selection = inputText.selection
                                    val insertPos = if (selection.start >= 0) selection.start else currentText.length
                                    val textBefore = currentText.substring(0, insertPos)
                                    val textAfter = currentText.substring(if (selection.end >= 0) selection.end else currentText.length)
                                    val newText = textBefore + emoji.emoji + textAfter
                                    val newSelection = androidx.compose.ui.text.TextRange(insertPos + emoji.emoji.length)
                                    val newValue = TextFieldValue(newText, newSelection)
                                    onInputTextChanged(newValue)
                                    onType(newText)
                                },
                                onAddToRecent = emojiViewModel::onAddToRecent
                            )
                        } else {
                            CustomEmojiPickerContent(
                                customEmojis = customEmojis,
                                colors = EmojiPickerDefaults.emojiPickerColors(
                                    backgroundColor = cardColor,
                                    textColor = textPrimary,
                                    searchBarBackgroundColor = bgColor,
                                    searchBarIconTint = textSecondary,
                                    searchBarTextColor = textPrimary,
                                    activeCategoryTint = accentColor,
                                    inactiveCategoryTint = textSecondary
                                ),
                                onEmojiSelected = { emoji ->
                                    val currentText = inputText.text
                                    val selection = inputText.selection
                                    val insertPos = if (selection.start >= 0) selection.start else currentText.length
                                    val textBefore = currentText.substring(0, insertPos)
                                    val textAfter = currentText.substring(if (selection.end >= 0) selection.end else currentText.length)
                                    val code = ":${emoji.name}:"
                                    val newText = textBefore + code + textAfter
                                    val newSelection = androidx.compose.ui.text.TextRange(insertPos + code.length)
                                    val newValue = TextFieldValue(newText, newSelection)
                                    onInputTextChanged(newValue)
                                    onType(newText)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
