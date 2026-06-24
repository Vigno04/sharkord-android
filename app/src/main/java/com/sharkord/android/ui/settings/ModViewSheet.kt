package com.sharkord.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.sharkord.android.data.model.ModViewData
import com.sharkord.android.data.model.Role
import com.sharkord.android.ui.components.rememberAsyncImagePainter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModViewSheet(
    uiState: ServerSettingsUiState,
    viewModel: ServerSettingsViewModel,
    onDismissRequest: () -> Unit
) {
    val data = uiState.modViewData
    val isLoading = uiState.isModViewLoading
    val screen = uiState.modViewScreen

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF313338),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .zIndex(10f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (screen == ModViewScreen.MAIN) {
                    Text("Moderate User", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                } else {
                    IconButton(onClick = { viewModel.setModViewScreen(ModViewScreen.MAIN) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                }
            }

            if (isLoading || data == null) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF5865F2))
                }
            } else {
                when (screen) {
                    ModViewScreen.MAIN -> ModViewMainContent(data, uiState, viewModel, onDismissRequest)
                    ModViewScreen.MESSAGES -> ModViewMessages(data)
                    ModViewScreen.LINKS -> ModViewLinks(data)
                    ModViewScreen.FILES -> ModViewFiles(data)
                }
            }
        }
    }
}

@Composable
private fun ModViewMainContent(
    data: ModViewData,
    uiState: ServerSettingsUiState,
    viewModel: ServerSettingsViewModel,
    onDismissRequest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        ModViewHeader(data, uiState, viewModel, onDismissRequest)
        
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 16.dp))
            ModViewServerActivity(data, viewModel)
            HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 16.dp))
            ModViewStorage(data)
            HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 16.dp))
            ModViewDetails(data)
        }
    }
}

@Composable
private fun ModViewHeader(
    data: ModViewData,
    uiState: ServerSettingsUiState,
    viewModel: ServerSettingsViewModel,
    onDismissRequest: () -> Unit
) {
    val user = data.user
    val serverRoles = uiState.serverData?.roles ?: emptyList()
    val userRoles = user.roles ?: user.roleIds?.mapNotNull { id -> serverRoles.find { it.id == id } } ?: emptyList()

    val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val joinDate = user.createdAt?.let { dateFormatter.format(Date(it)) } ?: "Unknown"

    var showKickDialog by remember { mutableStateOf(false) }
    var showBanDialog by remember { mutableStateOf(false) }
    var showAssignRoleDialog by remember { mutableStateOf(false) }
    var showDeleteUserDialog by remember { mutableStateOf(false) }
    var wipeData by remember { mutableStateOf(false) }
    var reasonInput by remember { mutableStateOf("") }

    if (showKickDialog) {
        AlertDialog(
            onDismissRequest = { showKickDialog = false },
            title = { Text("Kick ${user.name}") },
            text = {
                OutlinedTextField(
                    value = reasonInput,
                    onValueChange = { reasonInput = it },
                    label = { Text("Reason (optional)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.kickUser(user.id, reasonInput.takeIf { it.isNotBlank() })
                    showKickDialog = false
                    reasonInput = ""
                }) { Text("Kick") }
            },
            dismissButton = {
                TextButton(onClick = { showKickDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showBanDialog) {
        AlertDialog(
            onDismissRequest = { showBanDialog = false },
            title = { Text("Ban ${user.name}") },
            text = {
                OutlinedTextField(
                    value = reasonInput,
                    onValueChange = { reasonInput = it },
                    label = { Text("Reason (optional)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.banUser(user.id, reasonInput.takeIf { it.isNotBlank() })
                    showBanDialog = false
                    reasonInput = ""
                }) { Text("Ban") }
            },
            dismissButton = {
                TextButton(onClick = { showBanDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAssignRoleDialog) {
        val userRoleIds = userRoles.map { it.id }
        val availableRoles = serverRoles.filter { it.id !in userRoleIds }

        AlertDialog(
            onDismissRequest = { showAssignRoleDialog = false },
            title = { Text("Assign Role") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(availableRoles) { role ->
                        Text(
                            text = role.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.addUserRole(user.id, role.id)
                                    showAssignRoleDialog = false
                                }
                                .padding(16.dp)
                        )
                    }
                    if (availableRoles.isEmpty()) {
                        item { Text("No more roles available to assign.", modifier = Modifier.padding(16.dp)) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAssignRoleDialog = false }) { Text("Close") }
            }
        )
    }

    if (showDeleteUserDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteUserDialog = false },
            title = { Text("Delete ${user.name}") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = wipeData, onCheckedChange = { wipeData = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Wipe all data (destructive)", color = Color.White)
                    }
                    if (wipeData) {
                        Text("This will permanently delete all messages and files from this user.", color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    } else {
                        Text("The user will be deleted, but messages will remain as __delete_user_.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteUser(user.id, wipeData)
                    showDeleteUserDialog = false
                    onDismissRequest()
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteUserDialog = false }) { Text("Cancel") }
            }
        )
    }

    // banner
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(
                try {
                    Color(android.graphics.Color.parseColor(user.bannerColor ?: "#2B2B2B"))
                } catch (e: Exception) {
                    Color(0xFF2B2B2B)
                }
            )
    ) {
        val serverUrl = com.sharkord.android.data.network.SharkordClient.currentServerUrl
        val bannerUrl = user.banner?.name?.let { "$serverUrl/public/${android.net.Uri.encode(it)}" }
        val bannerPainter = rememberAsyncImagePainter(bannerUrl)
        if (bannerPainter != null) {
            androidx.compose.foundation.Image(
                painter = bannerPainter,
                contentDescription = "User Banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(56.dp))

            Text(user.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (!user.identity.isNullOrEmpty()) {
                Text(user.identity, color = Color.Gray, fontSize = 14.sp)
            }
            Text("Joined $joinDate", color = Color.Gray, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Button(
                    onClick = { showKickDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2D31)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.PersonRemove, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.LightGray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Kick", color = Color.LightGray, fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        if (user.banned) viewModel.unbanUser(user.id) else showBanDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2D31)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Gavel, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.LightGray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (user.banned) "Unban" else "Ban", color = Color.LightGray, fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        showDeleteUserDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2D31)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFED4245))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", color = Color(0xFFED4245), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("ROLES", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                userRoles.forEach { role ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF2B2D31))
                            .clickable { viewModel.removeUserRole(user.id, role.id) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(role.name, color = Color.White, fontSize = 12.sp)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2B2D31))
                        .clickable { showAssignRoleDialog = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Assign Role", color = Color.LightGray, fontSize = 12.sp)
                    }
                }
            }
        }

        // overlapping Avatar
        Box(
            modifier = Modifier
                .offset(y = (-40).dp)
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFF313338))
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.DarkGray)
            ) {
                val serverUrl = com.sharkord.android.data.network.SharkordClient.currentServerUrl
                val avatarUrl = user.avatar?.name?.let { "$serverUrl/public/${android.net.Uri.encode(it)}" }
                val avatarPainter = rememberAsyncImagePainter(avatarUrl)
                if (avatarPainter != null) {
                    androidx.compose.foundation.Image(
                        painter = avatarPainter,
                        contentDescription = "User Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = user.name.firstOrNull()?.toString()?.uppercase() ?: "?",
                        color = Color.White,
                        fontSize = 32.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModViewServerActivity(data: ModViewData, viewModel: ServerSettingsViewModel) {
    Text("Server Activity", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(12.dp))
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ActivityStatBox(
            title = "Messages", 
            value = data.messages.size.toString(), 
            modifier = Modifier.weight(1f).clickable { viewModel.setModViewScreen(ModViewScreen.MESSAGES) }
        )
        Spacer(modifier = Modifier.width(8.dp))
        ActivityStatBox(
            title = "Files", 
            value = data.storage.fileCount.toString(), 
            modifier = Modifier.weight(1f).clickable { viewModel.setModViewScreen(ModViewScreen.FILES) }
        )
        Spacer(modifier = Modifier.width(8.dp))
        val linksCount = data.messages.count { it.content.contains("http") }
        ActivityStatBox(
            title = "Links", 
            value = linksCount.toString(), 
            modifier = Modifier.weight(1f).clickable { viewModel.setModViewScreen(ModViewScreen.LINKS) }
        )
    }
}

@Composable
private fun ActivityStatBox(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2B2D31))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(title, color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
private fun ModViewStorage(data: ModViewData) {
    Text("Storage", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(12.dp))

    val used = data.storage.usedStorage
    val quota = data.storage.quota
    val progress = if (quota > 0) (used.toFloat() / quota.toFloat()).coerceIn(0f, 1f) else 0f
    
    val usedText = formatSize(used)
    val quotaText = if (quota > 0) formatSize(quota) else "Unlimited"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2B2D31))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Used Space", color = Color.Gray, fontSize = 14.sp)
            Text("$usedText / $quotaText", color = Color.White, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = if (progress > 0.9f) Color.Red else Color(0xFF5865F2),
            trackColor = Color.DarkGray,
        )
    }
}

@Composable
private fun ModViewDetails(data: ModViewData) {
    Text("Details", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(12.dp))

    val user = data.user
    val lastLogin = data.logins.firstOrNull()
    val dateFormatter = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2B2D31))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DetailRow(
            icon = Icons.Default.Person,
            label = "User ID",
            value = user.id.toString()
        )

        var showIdentity by remember { mutableStateOf(false) }
        DetailRowWithToggle(
            icon = Icons.Default.Person,
            label = "Identity",
            value = user.identity ?: "***",
            visible = showIdentity,
            onVisibilityChange = { showIdentity = it }
        )

        var showIp by remember { mutableStateOf(false) }
        DetailRowWithToggle(
            icon = Icons.Default.Wifi,
            label = "IP Address",
            value = lastLogin?.ip ?: "Unknown",
            visible = showIp,
            onVisibilityChange = { showIp = it }
        )

        var showLocation by remember { mutableStateOf(false) }
        val locationString = lastLogin?.let { 
            if (it.country != null || it.city != null) "${it.country ?: "N/A"} - ${it.city ?: "N/A"}" else "Unknown" 
        } ?: "Unknown"
        DetailRowWithToggle(
            icon = Icons.Default.Public,
            label = "Location",
            value = locationString,
            visible = showLocation,
            onVisibilityChange = { showLocation = it }
        )

        DetailRow(
            icon = Icons.Default.DateRange,
            label = "Joined Server",
            value = user.createdAt?.let { dateFormatter.format(Date(it)) } ?: "Unknown"
        )

        DetailRow(
            icon = Icons.Default.Schedule,
            label = "Last Active",
            value = lastLogin?.createdAt?.let { dateFormatter.format(Date(it)) } ?: "Unknown"
        )

        if (user.banned) {
            DetailRow(
                icon = Icons.Default.Gavel,
                label = "Banned",
                value = "Yes"
            )
        }
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = Color.Gray, fontSize = 14.sp)
        }
        Text(value, color = Color.White, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun DetailRowWithToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    visible: Boolean,
    onVisibilityChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = Color.Gray, fontSize = 14.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (visible) value else "***", color = Color.White, fontSize = 14.sp, maxLines = 1)
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { onVisibilityChange(!visible) }, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ModViewMessages(data: ModViewData) {
    if (data.messages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text("No messages found.", color = Color.Gray)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(data.messages) { message ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2B2D31))
                        .padding(12.dp)
                ) {
                    Text(message.content, color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun ModViewLinks(data: ModViewData) {
    val messagesWithLinks = data.messages.filter { it.content.contains("http") }
    if (messagesWithLinks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text("No links found.", color = Color.Gray)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(messagesWithLinks) { message ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2B2D31))
                        .padding(12.dp)
                ) {
                    Text(message.content, color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun ModViewFiles(data: ModViewData) {
    if (data.files.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text("No files found.", color = Color.Gray)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(data.files) { file ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2B2D31))
                        .padding(12.dp)
                ) {
                    Text(file.originalName ?: file.name ?: "Unknown File", color = Color.White, fontSize = 14.sp)
                    Text(file.mimeType ?: "Unknown Type", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}
