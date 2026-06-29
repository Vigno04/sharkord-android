package com.sharkord.android.ui.settings

import com.sharkord.android.ui.theme.SharkordTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sharkord.android.R
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextOverflow
import com.sharkord.android.data.model.ModViewData
import com.sharkord.android.data.model.Role
import com.sharkord.android.data.model.User
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
        containerColor = SharkordTheme.colors.bgColor,
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
                    Text(stringResource(R.string.settings_moderateUserMenu), color = SharkordTheme.colors.foregroundText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                } else {
                    IconButton(onClick = { viewModel.setModViewScreen(ModViewScreen.MAIN) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = SharkordTheme.colors.foregroundText)
                    }
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = SharkordTheme.colors.primaryText.copy(alpha = 0.6f))
                }
            }

            if (isLoading || data == null) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SharkordTheme.colors.accentColor)
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
            HorizontalDivider(color = SharkordTheme.colors.dividerColor, modifier = Modifier.padding(vertical = 16.dp))
            ModViewServerActivity(data, viewModel)
            HorizontalDivider(color = SharkordTheme.colors.dividerColor, modifier = Modifier.padding(vertical = 16.dp))
            ModViewStorage(data)
            HorizontalDivider(color = SharkordTheme.colors.dividerColor, modifier = Modifier.padding(vertical = 16.dp))
            ModViewDetails(data.user, data)
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
            title = { Text(stringResource(R.string.settings_kickUserTitle, user.name)) },
            text = {
                OutlinedTextField(
                    value = reasonInput,
                    onValueChange = { reasonInput = it },
                    label = { Text(stringResource(R.string.settings_reasonOptional)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.kickUser(user.id, reasonInput.takeIf { it.isNotBlank() })
                    showKickDialog = false
                    reasonInput = ""
                }) { Text(stringResource(R.string.common_kick)) }
            },
            dismissButton = {
                TextButton(onClick = { showKickDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showBanDialog) {
        AlertDialog(
            onDismissRequest = { showBanDialog = false },
            title = { Text(stringResource(R.string.settings_banUserTitle, user.name)) },
            text = {
                OutlinedTextField(
                    value = reasonInput,
                    onValueChange = { reasonInput = it },
                    label = { Text(stringResource(R.string.settings_reasonOptional)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.banUser(user.id, reasonInput.takeIf { it.isNotBlank() })
                    showBanDialog = false
                    reasonInput = ""
                }) { Text(stringResource(R.string.common_ban)) }
            },
            dismissButton = {
                TextButton(onClick = { showBanDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showAssignRoleDialog) {
        val userRoleIds = userRoles.map { it.id }
        val availableRoles = serverRoles.filter { it.id !in userRoleIds }

        AlertDialog(
            onDismissRequest = { showAssignRoleDialog = false },
            title = { Text(stringResource(R.string.settings_assignRoleTitle)) },
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
                        item { Text(stringResource(R.string.settings_noRolesToAssign), modifier = Modifier.padding(16.dp)) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAssignRoleDialog = false }) { Text(stringResource(R.string.common_close)) }
            }
        )
    }

    if (showDeleteUserDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteUserDialog = false },
            title = { Text(stringResource(R.string.settings_deleteUserTitle, user.name)) },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = wipeData, onCheckedChange = { wipeData = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_wipeAllDataLabel), color = SharkordTheme.colors.foregroundText)
                    }
                    if (wipeData) {
                        Text(stringResource(R.string.settings_wipeAllDataDesc1), color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    } else {
                        Text(stringResource(R.string.settings_wipeAllDataDesc2), color = SharkordTheme.colors.primaryText.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteUser(user.id, wipeData)
                    showDeleteUserDialog = false
                    onDismissRequest()
                }) { Text(stringResource(R.string.common_delete), color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteUserDialog = false }) { Text(stringResource(R.string.common_cancel)) }
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
                    SharkordTheme.colors.cardColor
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

            Text(user.name, color = SharkordTheme.colors.foregroundText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (!user.identity.isNullOrEmpty()) {
                Text(user.identity, color = SharkordTheme.colors.primaryText.copy(alpha = 0.6f), fontSize = 14.sp)
            }
            Text(stringResource(R.string.settings_joinedDate, joinDate), color = SharkordTheme.colors.primaryText.copy(alpha = 0.6f), fontSize = 12.sp)

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Button(
                    onClick = { showKickDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SharkordTheme.colors.cardColor),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.PersonRemove, contentDescription = null, modifier = Modifier.size(16.dp), tint = SharkordTheme.colors.primaryText.copy(alpha = 0.8f))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.common_kick), color = SharkordTheme.colors.primaryText.copy(alpha = 0.8f), fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        if (user.banned) viewModel.unbanUser(user.id) else showBanDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SharkordTheme.colors.cardColor),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Gavel, contentDescription = null, modifier = Modifier.size(16.dp), tint = SharkordTheme.colors.primaryText.copy(alpha = 0.8f))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (user.banned) stringResource(R.string.common_unban) else stringResource(R.string.common_ban), color = SharkordTheme.colors.primaryText.copy(alpha = 0.8f), fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        showDeleteUserDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SharkordTheme.colors.cardColor),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFED4245))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.common_delete), color = Color(0xFFED4245), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(stringResource(R.string.settings_rolesSectionTitle), color = SharkordTheme.colors.primaryText.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                userRoles.forEach { role ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SharkordTheme.colors.cardColor)
                            .clickable { viewModel.removeUserRole(user.id, role.id) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(role.name, color = SharkordTheme.colors.foregroundText, fontSize = 12.sp)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SharkordTheme.colors.cardColor)
                        .clickable { showAssignRoleDialog = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = SharkordTheme.colors.primaryText.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.settings_assignRoleTitle), color = SharkordTheme.colors.primaryText.copy(alpha = 0.8f), fontSize = 12.sp)
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
                .background(SharkordTheme.colors.bgColor)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(SharkordTheme.colors.cardColor)
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
                        color = SharkordTheme.colors.foregroundText,
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
    Text(stringResource(R.string.settings_serverActivityTitle), color = SharkordTheme.colors.foregroundText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(12.dp))
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ActivityStatBox(
            title = stringResource(R.string.settings_activityMessages), 
            value = data.messages.size.toString(), 
            modifier = Modifier.weight(1f).clickable { viewModel.setModViewScreen(ModViewScreen.MESSAGES) }
        )
        Spacer(modifier = Modifier.width(8.dp))
        ActivityStatBox(
            title = stringResource(R.string.settings_activityFiles), 
            value = data.storage.fileCount.toString(), 
            modifier = Modifier.weight(1f).clickable { viewModel.setModViewScreen(ModViewScreen.FILES) }
        )
        Spacer(modifier = Modifier.width(8.dp))
        val linksCount = data.messages.count { it.content.contains("http") }
        ActivityStatBox(
            title = stringResource(R.string.settings_activityLinks), 
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
            .background(SharkordTheme.colors.cardColor)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = SharkordTheme.colors.foregroundText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(title, color = SharkordTheme.colors.primaryText.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}

@Composable
private fun ModViewStorage(data: ModViewData) {
    Text(stringResource(R.string.settings_storageTitle), color = SharkordTheme.colors.foregroundText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(12.dp))
    
    val unlimitedText = stringResource(R.string.common_unlimited)
    val totalSpace = if (data.storage.quota == 0L) unlimitedText else formatBytes(data.storage.quota)
    val usedSpaceStr = formatBytes(data.storage.usedStorage)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SharkordTheme.colors.cardColor)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.settings_usedSpace), color = SharkordTheme.colors.primaryText.copy(alpha = 0.6f), fontSize = 14.sp)
            Text("$usedSpaceStr / $totalSpace", color = SharkordTheme.colors.foregroundText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { if (data.storage.quota == 0L) 0f else (data.storage.usedStorage.toFloat() / data.storage.quota.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = SharkordTheme.colors.accentColor,
            trackColor = SharkordTheme.colors.bgColor
        )
    }
}

@Composable
private fun ModViewDetails(user: User, data: ModViewData) {
    Text(stringResource(R.string.settings_detailsTitle), color = SharkordTheme.colors.foregroundText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(12.dp))
    
    val unknownText = stringResource(R.string.common_unknown)
    val lastLogin = data.logins.firstOrNull()
    val locationString = lastLogin?.let { 
        if (it.country != null || it.city != null) "${it.country ?: "N/A"} - ${it.city ?: "N/A"}" else unknownText 
    } ?: unknownText
    
    val details = listOf(
        stringResource(R.string.settings_detailUserId) to user.id.toString(),
        stringResource(R.string.settings_detailIdentity) to (user.identity ?: unknownText),
        stringResource(R.string.settings_detailIpAddress) to (lastLogin?.ip ?: unknownText),
        stringResource(R.string.settings_detailLocation) to locationString,
        stringResource(R.string.settings_detailJoinedServer) to (user.createdAt?.let { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(it)) } ?: unknownText),
        stringResource(R.string.settings_detailLastActive) to (lastLogin?.createdAt?.let { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(it)) } ?: unknownText),
        stringResource(R.string.settings_detailBanned) to if (user.banned) stringResource(R.string.common_yes) else "No"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SharkordTheme.colors.cardColor)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        details.forEach { (label, value) ->
            DetailRow(label = label, value = value)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = SharkordTheme.colors.primaryText.copy(alpha = 0.6f), fontSize = 14.sp)
        Text(value, color = SharkordTheme.colors.foregroundText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ModViewMessages(data: ModViewData) {
    if (data.messages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.settings_noMessagesFound), color = SharkordTheme.colors.primaryText.copy(alpha = 0.6f))
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(data.messages) { message ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SharkordTheme.colors.cardColor)
                        .padding(12.dp)
                ) {
                    Text(message.content, color = SharkordTheme.colors.foregroundText, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun ModViewLinks(data: ModViewData) {
    val messagesWithLinks = data.messages.filter { it.content.contains("http") }
    if (messagesWithLinks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.settings_noLinksFound), color = SharkordTheme.colors.primaryText.copy(alpha = 0.6f))
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(messagesWithLinks) { message ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SharkordTheme.colors.cardColor)
                        .padding(12.dp)
                ) {
                    Text(message.content, color = SharkordTheme.colors.foregroundText, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun ModViewFiles(data: ModViewData) {
    if (data.files.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.settings_noFilesFound), color = SharkordTheme.colors.primaryText.copy(alpha = 0.6f))
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(data.files) { file ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SharkordTheme.colors.cardColor)
                        .padding(12.dp)
                ) {
                    Text(file.name ?: stringResource(R.string.common_unknownFile), color = SharkordTheme.colors.foregroundText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${formatBytes((file.size ?: 0).toLong())} • ${file.mimeType ?: stringResource(R.string.common_unknownType)}", color = SharkordTheme.colors.primaryText.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
