package com.sharkord.android.ui.settings

import com.sharkord.android.ui.theme.SharkordTheme
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharkord.android.data.model.Emoji
import com.sharkord.android.data.network.SharkordClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEmojisTab(
    emojis: List<Emoji>,
    viewModel: ServerSettingsViewModel,
    cardColor: Color,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color
) {
    val context = LocalContext.current
    var showNameDialog by remember { mutableStateOf(false) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var newEmojiName by remember { mutableStateOf("") }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            showNameDialog = true
        }
    }

    if (showNameDialog && selectedUri != null) {
        AlertDialog(
            onDismissRequest = { 
                showNameDialog = false
                selectedUri = null 
            },
            containerColor = cardColor,
            title = { Text("Upload Emoji", color = foregroundText, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter a name for the new emoji (no spaces):", color = primaryText)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newEmojiName,
                        onValueChange = { newEmojiName = it.replace(" ", "_") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = foregroundText,
                            unfocusedTextColor = primaryText,
                            focusedIndicatorColor = accentColor
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = selectedUri
                        if (uri != null && newEmojiName.isNotBlank()) {
                            val inputStream = context.contentResolver.openInputStream(uri)
                            val bytes = inputStream?.readBytes()
                            inputStream?.close()
                            if (bytes != null) {
                                viewModel.uploadEmoji(newEmojiName, bytes, "emoji.png")
                            }
                            showNameDialog = false
                            selectedUri = null
                            newEmojiName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("Upload")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showNameDialog = false
                    selectedUri = null 
                }) {
                    Text("Cancel", color = primaryText)
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SERVER EMOJIS", color = foregroundText, fontWeight = FontWeight.Bold)
            Button(
                onClick = { galleryLauncher.launch("image/*") },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Upload Emoji")
            }
        }

        if (emojis.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No custom emojis yet.", color = primaryText)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(emojis) { emoji ->
                    EmojiCard(
                        emoji = emoji,
                        onDelete = { viewModel.deleteEmoji(emoji.id) },
                        cardColor = cardColor,
                        foregroundText = foregroundText,
                        primaryText = primaryText
                    )
                }
            }
        }
    }
}

@Composable
fun EmojiCard(
    emoji: Emoji,
    onDelete: () -> Unit,
    cardColor: Color,
    foregroundText: Color,
    primaryText: Color
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(cardColor)
            .padding(8.dp)
    ) {
        val serverUrl = SharkordClient.currentServerUrl
        val fileUrl = if (serverUrl != null && emoji.file?.name != null) {
            "$serverUrl/public/${android.net.Uri.encode(emoji.file.name)}"
        } else null
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            val painter = com.sharkord.android.ui.components.rememberAsyncImagePainter(fileUrl)
            if (painter != null) {
                androidx.compose.foundation.Image(
                    painter = painter,
                    contentDescription = emoji.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Box(modifier = Modifier.size(48.dp).background(SharkordTheme.colors.cardColor))
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(":${emoji.name}:", color = foregroundText, fontSize = 12.sp, maxLines = 1)
        }
        
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Delete",
            tint = Color(0xFFED4245),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDelete)
                .padding(4.dp)
        )
    }
}
