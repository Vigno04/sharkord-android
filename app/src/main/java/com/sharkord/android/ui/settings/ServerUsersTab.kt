package com.sharkord.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import com.sharkord.android.R
import com.sharkord.android.data.model.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ServerUsersTab(
    users: List<User>,
    viewModel: ServerSettingsViewModel,
    cardColor: Color,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredUsers = users.filter { it.name.contains(searchQuery, ignoreCase = true) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.settings_serverUsersTitle),
            color = foregroundText,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.settings_searchUsers), color = primaryText) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = primaryText) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = foregroundText,
                unfocusedTextColor = primaryText,
                focusedIndicatorColor = accentColor
            )
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredUsers) { user ->
                UserItemRow(
                    user = user,
                    onModerate = { viewModel.openModView(user.id) },
                    onDelete = { wipeData -> viewModel.deleteUser(user.id, wipeData) },
                    cardColor = cardColor,
                    foregroundText = foregroundText,
                    primaryText = primaryText,
                    accentColor = accentColor
                )
            }
        }
    }
}

@Composable
fun UserItemRow(
    user: User,
    onModerate: () -> Unit,
    onDelete: (Boolean) -> Unit,
    cardColor: Color,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color
) {
    val dateFormatter = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
    val joinDate = user.createdAt?.let { dateFormatter.format(Date(it)) } ?: "Unknown"
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var wipeData by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.settings_deleteUserTitle, user.name)) },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = wipeData, onCheckedChange = { wipeData = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_wipeAllDataLabel), color = foregroundText)
                    }
                    if (wipeData) {
                        Text(stringResource(R.string.settings_wipeAllDataDesc1), color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    } else {
                        Text(stringResource(R.string.settings_wipeAllDataDesc2), color = primaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete(wipeData)
                }) {
                    Text(stringResource(R.string.common_delete), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel), color = primaryText)
                }
            },
            containerColor = cardColor,
            titleContentColor = foregroundText,
            textContentColor = primaryText
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cardColor)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.DarkGray)
        ) {
            val serverUrl = com.sharkord.android.data.network.SharkordClient.currentServerUrl
            val avatarUrl = user.avatar?.name?.let { name ->
                "$serverUrl/public/${android.net.Uri.encode(name)}"
            }
            val avatarPainter = com.sharkord.android.ui.components.rememberAsyncImagePainter(avatarUrl)
            if (avatarPainter != null) {
                androidx.compose.foundation.Image(
                    painter = avatarPainter,
                    contentDescription = "User Avatar",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = user.name.firstOrNull()?.toString()?.uppercase() ?: "?",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(user.name, color = foregroundText, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.settings_joinedDate, joinDate), color = primaryText, fontSize = 12.sp)
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = user.status ?: "offline",
            color = if (user.status == "online") Color(0xFF4CAF50) else primaryText,
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 16.dp)
        )

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = primaryText)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(cardColor)
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_moderateUserMenu), color = foregroundText) },
                    leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, tint = foregroundText) },
                    onClick = {
                        menuExpanded = false
                        onModerate()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_deleteUserMenu), color = Color(0xFFED4245)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFED4245)) },
                    onClick = {
                        menuExpanded = false
                        showDeleteDialog = true
                    }
                )
            }
        }
    }
}
