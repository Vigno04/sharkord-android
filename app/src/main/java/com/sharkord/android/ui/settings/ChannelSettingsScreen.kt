package com.sharkord.android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelSettingsScreen(
    channelId: Int,
    onBackClick: () -> Unit,
) {
    val factory = remember(channelId) { ChannelSettingsViewModel.Factory(channelId) }
    val viewModel: ChannelSettingsViewModel = viewModel(factory = factory)
    
    val uiState by viewModel.uiState.collectAsState()

    val bgColor = Color(0xFF1C1C1C)
    val foregroundText = Color(0xFFFAFAFA)
    val accentColor = Color(0xFF5865F2)

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("General", "Permissions")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Channel Settings", color = foregroundText, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = foregroundText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
            )
        },
        containerColor = bgColor
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accentColor)
            }
        } else if (uiState.errorMessage != null && uiState.channel == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.errorMessage ?: "Unknown error", color = Color.Red)
            }
        } else {
            Column(modifier = Modifier.padding(paddingValues)) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = bgColor,
                    contentColor = foregroundText,
                    edgePadding = 8.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = accentColor
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) },
                            selectedContentColor = accentColor,
                            unselectedContentColor = Color(0xFFE8E8E8)
                        )
                    }
                }

                when (selectedTabIndex) {
                    0 -> GeneralTabContent(viewModel, onBackClick)
                    1 -> PermissionsTabContent(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralTabContent(
    viewModel: ChannelSettingsViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val cardColor = Color(0xFF2B2B2B)
    val primaryText = Color(0xFFE8E8E8)
    val foregroundText = Color(0xFFFAFAFA)
    val accentColor = Color(0xFF5865F2)

    var name by remember { mutableStateOf(uiState.channel?.name ?: "") }
    var topic by remember { mutableStateOf(uiState.channel?.description ?: "") }
    var isPrivate by remember { mutableStateOf(uiState.channel?.private ?: false) }

    LaunchedEffect(uiState.channel) {
        uiState.channel?.let {
            name = it.name
            topic = it.description ?: ""
            isPrivate = it.private
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsSection(title = "GENERAL SETTINGS", cardColor = cardColor, foregroundText = foregroundText) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Channel Name", color = primaryText) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = foregroundText,
                    unfocusedTextColor = primaryText,
                    focusedIndicatorColor = accentColor
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                label = { Text("Channel Topic", color = primaryText) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = foregroundText,
                    unfocusedTextColor = primaryText,
                    focusedIndicatorColor = accentColor
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Private Channel", color = foregroundText)
                    Text("Only selected members and roles will be able to view this channel.", color = primaryText, fontSize = 12.sp)
                }
                Switch(
                    checked = isPrivate,
                    onCheckedChange = { isPrivate = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = accentColor,
                        checkedTrackColor = accentColor.copy(alpha = 0.5f)
                    )
                )
            }

            if (uiState.errorMessage != null && uiState.channel != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(uiState.errorMessage!!, color = Color.Red, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.updateChannel(
                        name = name,
                        topic = topic.ifBlank { null },
                        isPrivate = isPrivate,
                        onSuccess = { onBackClick() }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                modifier = Modifier.align(Alignment.End),
                enabled = !uiState.isSaving && name.isNotBlank()
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Save Changes")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsTabContent(
    viewModel: ChannelSettingsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val cardColor = Color(0xFF2B2B2B)
    val primaryText = Color(0xFFE8E8E8)
    val foregroundText = Color(0xFFFAFAFA)
    val accentColor = Color(0xFF5865F2)

    if (uiState.channel?.private == false) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "This channel is public.\nMake it private in the General tab to manage specific permissions.",
                color = primaryText,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        return
    }

    val serverRoles = uiState.serverData?.roles ?: emptyList()
    val serverUsers = uiState.serverData?.users ?: emptyList()

    // Represents the currently selected override entity (either a roleId or userId)
    var selectedRoleId by remember { mutableStateOf<Int?>(null) }
    var selectedUserId by remember { mutableStateOf<Int?>(null) }
    
    var showAddRoleDropdown by remember { mutableStateOf(false) }
    var showAddUserDropdown by remember { mutableStateOf(false) }
    
    // We compute the current permission override map for the selected entity
    val currentPermissionsMap = remember(selectedRoleId, selectedUserId, uiState.rolePermissions, uiState.userPermissions) {
        val map = mutableMapOf<String, Boolean>()
        if (selectedRoleId != null) {
            uiState.rolePermissions.filter { it.roleId == selectedRoleId }.forEach { map[it.permission] = it.allow }
        } else if (selectedUserId != null) {
            uiState.userPermissions.filter { it.userId == selectedUserId }.forEach { map[it.permission] = it.allow }
        }
        map
    }

    if (uiState.isPermissionsLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = accentColor)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsSection(title = "ROLE OVERRIDES", cardColor = cardColor, foregroundText = foregroundText) {
            val roleIdsWithOverrides = uiState.rolePermissions.map { it.roleId }.distinct()
            
            // FlowRow for Roles
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                roleIdsWithOverrides.forEach { roleId ->
                    val roleName = serverRoles.find { it.id == roleId }?.name ?: "Role $roleId"
                    val isSelected = selectedRoleId == roleId
                    FilterChip(
                        selected = isSelected,
                        onClick = { 
                            selectedRoleId = if (isSelected) null else roleId
                            selectedUserId = null 
                        },
                        label = { Text(roleName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor.copy(alpha = 0.2f),
                            selectedLabelColor = accentColor
                        )
                    )
                }

                Box {
                    Button(onClick = { showAddRoleDropdown = true }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
                        Text("+ " + androidx.compose.ui.res.stringResource(id = com.sharkord.android.R.string.common_add))
                    }
                    DropdownMenu(expanded = showAddRoleDropdown, onDismissRequest = { showAddRoleDropdown = false }) {
                        serverRoles.filter { it.id !in roleIdsWithOverrides }.forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role.name) },
                                onClick = {
                                    showAddRoleDropdown = false
                                    selectedRoleId = role.id
                                    selectedUserId = null
                                    viewModel.updatePermissions(role.id, null, isCreate = true, permissions = emptyList())
                                }
                            )
                        }
                    }
                }
            }
        }

        SettingsSection(title = "USER OVERRIDES", cardColor = cardColor, foregroundText = foregroundText) {
            val userIdsWithOverrides = uiState.userPermissions.map { it.userId }.distinct()
            
            // FlowRow for Users
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                userIdsWithOverrides.forEach { userId ->
                    val userName = serverUsers.find { it.id == userId }?.name ?: "User $userId"
                    val isSelected = selectedUserId == userId
                    FilterChip(
                        selected = isSelected,
                        onClick = { 
                            selectedUserId = if (isSelected) null else userId
                            selectedRoleId = null 
                        },
                        label = { Text(userName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor.copy(alpha = 0.2f),
                            selectedLabelColor = accentColor
                        )
                    )
                }

                Box {
                    Button(onClick = { showAddUserDropdown = true }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
                        Text("+ " + androidx.compose.ui.res.stringResource(id = com.sharkord.android.R.string.common_add))
                    }
                    DropdownMenu(expanded = showAddUserDropdown, onDismissRequest = { showAddUserDropdown = false }) {
                        serverUsers.filter { it.id !in userIdsWithOverrides }.forEach { user ->
                            DropdownMenuItem(
                                text = { Text(user.name) },
                                onClick = {
                                    showAddUserDropdown = false
                                    selectedUserId = user.id
                                    selectedRoleId = null
                                    viewModel.updatePermissions(null, user.id, isCreate = true, permissions = emptyList())
                                }
                            )
                        }
                    }
                }
            }
        }

        if (selectedRoleId != null || selectedUserId != null) {
            val selectedName = if (selectedRoleId != null) {
                serverRoles.find { it.id == selectedRoleId }?.name ?: "Role"
            } else {
                serverUsers.find { it.id == selectedUserId }?.name ?: "User"
            }
            
            SettingsSection(title = "EDIT PERMISSIONS FOR ${selectedName.uppercase()}", cardColor = cardColor, foregroundText = foregroundText) {
                val context = androidx.compose.ui.platform.LocalContext.current
                com.sharkord.android.data.model.ChannelPermission.values().forEach { permission ->
                    val isAllowed = currentPermissionsMap[permission.value] == true
                    
                    val permissionStringId = context.resources.getIdentifier("permissions_channel_${permission.value}", "string", context.packageName)
                    val permissionLabel = if (permissionStringId != 0) context.getString(permissionStringId) else permission.value
                    
                    val descStringId = context.resources.getIdentifier("permissions_channelDescriptions_${permission.value}", "string", context.packageName)
                    val permissionDesc = if (descStringId != 0) context.getString(descStringId) else ""

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(permissionLabel, color = foregroundText)
                            if (permissionDesc.isNotBlank()) {
                                Text(permissionDesc, color = primaryText, fontSize = 12.sp)
                            }
                        }
                        Switch(
                            checked = isAllowed,
                            onCheckedChange = { newValue ->
                                val newMap = currentPermissionsMap.toMutableMap()
                                newMap[permission.value] = newValue
                                val activePerms = newMap.filterValues { it }.keys.toList()
                                viewModel.updatePermissions(selectedRoleId, selectedUserId, false, activePerms)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = accentColor,
                                checkedTrackColor = accentColor.copy(alpha = 0.5f)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        viewModel.deletePermissions(selectedRoleId, selectedUserId)
                        selectedRoleId = null
                        selectedUserId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(androidx.compose.ui.res.stringResource(id = com.sharkord.android.R.string.common_deleteLabel), color = Color.White)
                }
            }
        }
    }
}
