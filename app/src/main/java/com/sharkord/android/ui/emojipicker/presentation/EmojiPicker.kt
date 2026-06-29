package com.sharkord.android.ui.emojipicker.presentation
import com.sharkord.android.R

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.sharkord.android.ui.emojipicker.common.EmojiConstants
import com.sharkord.android.ui.emojipicker.common.TestTags
import com.sharkord.android.ui.emojipicker.di.RepositoryModule
import com.sharkord.android.ui.emojipicker.domain.model.Emoji
import com.sharkord.android.ui.emojipicker.domain.model.EmojiCategory
import com.sharkord.android.ui.emojipicker.domain.model.EmojiCategoryTitle
import com.sharkord.android.ui.emojipicker.domain.model.EmojiItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPicker(
    open: Boolean,
    colors: EmojiPickerColors = EmojiPickerDefaults.emojiPickerColors(),
    onClose: () -> Unit,
    onEmojiSelected: (emoji: Emoji) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val gridState = rememberLazyGridState()
    // remember a CoroutineScope to be able to launch
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val viewModel: EmojiPickerViewModel = RepositoryModule.provideEmojiPickerViewModel(
        context = context
    )

    LaunchedEffect(open) {
        if (!open) {
            // if picker is being closed,

            // just clear the search text
            viewModel.onSearchTextChanged("")

            // scroll at beginning
            gridState.scrollToItem(0)
        }
    }

    if (open) {
        ModalBottomSheet(
            onDismissRequest = onClose,
            sheetState = sheetState,
            modifier = Modifier
                .nestedScroll(rememberNestedScrollInteropConnection())
                .systemBarsPadding()
                .testTag(TestTags.EMOJI_PICKER),
            containerColor = colors.backgroundColor
        ) {
            EmojiPickerContent(
                state = viewModel.state.collectAsState().value,
                gridState = gridState,
                colors = colors,
                onCategoryTabClick = { categoryTitleIndex ->
                    coroutineScope.launch {
                        // animate scroll to the 10th item
                        gridState.animateScrollToItem(
                            index = categoryTitleIndex
                        )
                    }
                },
                onSearchTextChange = viewModel::onSearchTextChanged,
                onEmojiSelected = onEmojiSelected,
                onAddToRecent = viewModel::onAddToRecent
            )
        }
    }
}

@Composable
internal fun EmojiPickerContent(
    state: EmojiPickerState,
    gridState: LazyGridState,
    colors: EmojiPickerColors = EmojiPickerDefaults.emojiPickerColors(),
    onCategoryTabClick: (categoryTitleIndex: Int) -> Unit,
    onSearchTextChange: (searchText: String) -> Unit,
    onEmojiSelected: (emoji: Emoji) -> Unit,
    onAddToRecent: (emoji: Emoji) -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(10.dp)
            .fillMaxSize()
    ) {

        // calculate selected category only when firstVisibleItemIndex or categoryTitleIndexes actually change
        val selectedCategoryKey by remember {
            derivedStateOf {
                state.categoryTitleIndexes.keys
                .filter { categoryKey ->
                    // filter out categories that are not in the result set
                    state.categoryTitleIndexes.containsKey(categoryKey)
                }
                .last { categoryKey ->
                    // calculate all distances from current visible item and category titles
                    // < 0 if item is not visible (still to see)
                    // >= 0 if item has already been viewed
                    gridState.firstVisibleItemIndex - state.categoryTitleIndexes.getOrDefault(
                        categoryKey,
                        0
                    ) >= 0
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            EmojiConstants.categoryOrder.forEach { category ->
                val categoryTitleIndex = state.categoryTitleIndexes.getOrDefault(category.key, -1)
                val isEnabled = categoryTitleIndex != -1
                val isSelected = isEnabled && selectedCategoryKey == category.key

                CategoryTabButton(
                    category = category,
                    selected = isSelected,
                    enabled = isEnabled,
                    colors = colors,
                    onClick = {
                        onCategoryTabClick(categoryTitleIndex)
                    },
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(
                vertical = 5.dp
            )
        )

        if (state.emojiListItems!!.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(TestTags.EMPTY_EMOJI_PICKER),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.no_emoji_found),
                    style = TextStyle(
                        fontSize = 16.sp
                    ),
                    color = colors.textColor
                )
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(EmojiConstants.EMOJI_PER_ROW),
                modifier = Modifier
                    .fillMaxSize()
            ) {
                items(state.emojiListItems, span = {
                    if (it is EmojiItem) {
                        GridItemSpan(1)
                    } else {
                        GridItemSpan(6)
                    }
                }, key = { it.id }, contentType = { item ->
                    when (item) {
                        is EmojiItem -> "EmojiButton"
                        is EmojiCategoryTitle -> "CategoryTitle"
                    }
                }) {
                    if (it is EmojiItem) {
                        EmojiButton(
                            emoji = it.emoji,
                            onEmojiSelected = onEmojiSelected,
                            onAddToRecent = onAddToRecent
                        )
                    } else if (it is EmojiCategoryTitle) {
                        CategoryTitle(
                            category = it.category,
                            colors = colors
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiSearchBar(
    value: String,
    onValueChange: (value: String) -> Unit,
    colors: EmojiPickerColors,
    modifier: Modifier = Modifier,
) {
    val height = 40.dp
    val cornerShape = RoundedCornerShape(8.dp)

    Row(
        modifier = Modifier
            .height(height)
            .fillMaxWidth()
            .background(
                color = colors.searchBarBackgroundColor,
                shape = cornerShape
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            modifier = modifier
                .weight(5f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .testTag(TestTags.SEARCH_BAR),
            value = value,
            onValueChange = onValueChange,
            decorationBox = { innerTextField ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth(),
                    content = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = colors.searchBarIconTint
                        )
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart,
                            content = {
                                if (value.isEmpty()) {
                                    Text(
                                        textAlign = TextAlign.Start,
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        text = stringResource(R.string.search),
                                        color = colors.searchBarTextColor
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search
            ),
            singleLine = true
        )
    }
}

@Composable
private fun RowScope.CategoryTabButton(
    category: EmojiCategory,
    selected: Boolean,
    enabled: Boolean,
    colors: EmojiPickerColors,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .testTag(TestTags.tabButton(category)),
        enabled = enabled
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(category.icon),
            contentDescription = stringResource(category.title),
            tint = if (selected) colors.activeCategoryTint else colors.inactiveCategoryTint
        )
    }
}

@Composable
private fun CategoryTitle(
    category: EmojiCategory,
    colors: EmojiPickerColors
) {
    Text(
        text = stringResource(category.title),
        style = TextStyle(
            fontSize = 16.sp
        ),
        color = colors.textColor,
        modifier = Modifier
            .testTag(TestTags.get(category))
    )
}

@Composable
private fun EmojiButton(
    emoji: Emoji,
    onEmojiSelected: (emoji: Emoji) -> Unit,
    onAddToRecent: (emoji: Emoji) -> Unit,
) {
    TextButton(
        onClick = {
            // add to recent
            onAddToRecent(emoji)

            // select emoji
            onEmojiSelected(emoji)
        },
        modifier = Modifier
            .testTag(TestTags.get(emoji))
    ) {
        Text(
            text = emoji.emoji,
            style = TextStyle(
                fontSize = 26.sp,
                textAlign = TextAlign.Center
            )
        )
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Preview(name = "Light Mode", showBackground = true)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Full Preview", showSystemUi = true)
@Composable
private fun EmojiBottomSheetContentPreview() {
    val randomEmoji = Emoji(
        id = "grinning_face",
        emoji = "😀",
        name = "grinning face",
        slug = "grinning_face",
        category = EmojiCategory.SMILEYS_AND_PEOPLE.key
    )

    PlaygroundTheme {
        Scaffold {
            EmojiPickerContent(
                state = EmojiPickerState(
                    emojiListItems = listOf(
                        EmojiCategoryTitle(
                            category = EmojiCategory.OBJECTS,
                            id = EmojiCategory.OBJECTS.key
                        ),
                        EmojiItem(
                            id = randomEmoji.id,
                            emoji = randomEmoji
                        )
                    ),
                    categoryTitleIndexes = mapOf(
                        EmojiCategory.RECENT.key to 0
                    )
                ),
                gridState = LazyGridState(),
                onCategoryTabClick = { },
                onSearchTextChange = { },
                onEmojiSelected = { },
                onAddToRecent = { }
            )
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Preview
@Composable
private fun EmojiBottomSheetContentEmptyPreview() {
    Scaffold {
        EmojiPickerContent(
            state = EmojiPickerState(
                emojiListItems = listOf(),
                categoryTitleIndexes = mapOf()
            ),
            gridState = LazyGridState(),
            onCategoryTabClick = { },
            onSearchTextChange = { },
            onEmojiSelected = { },
            onAddToRecent = { }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Preview
@Composable
private fun EmojiBottomSheetContentCustomColors() {
    val randomEmoji = Emoji(
        id = "grinning_face",
        emoji = "😀",
        name = "grinning face",
        slug = "grinning_face",
        category = EmojiCategory.SMILEYS_AND_PEOPLE.key
    )

    PlaygroundTheme {
        Scaffold {
            EmojiPickerContent(
                state = EmojiPickerState(
                    emojiListItems = listOf(
                        EmojiCategoryTitle(
                            category = EmojiCategory.OBJECTS,
                            id = EmojiCategory.OBJECTS.key
                        ),
                        EmojiItem(
                            id = randomEmoji.id,
                            emoji = randomEmoji
                        )
                    ),
                    categoryTitleIndexes = mapOf(
                        EmojiCategory.RECENT.key to 0
                    )
                ),
                gridState = LazyGridState(),
                colors = EmojiPickerDefaults.emojiPickerColors(
                    backgroundColor = Color.Black,
                    searchBarBackgroundColor = Color.Red,
                    searchBarIconTint = Color.Green,
                    searchBarTextColor = Color.Blue,
                    textColor = Color.Yellow,
                    activeCategoryTint = Color.Magenta,
                    inactiveCategoryTint = Color.Cyan
                ),
                onCategoryTabClick = { },
                onSearchTextChange = { },
                onEmojiSelected = { },
                onAddToRecent = { }
            )
        }
    }
}
