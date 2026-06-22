package com.sharkord.android.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sharkord.android.data.model.Role
import com.sharkord.android.data.network.SharkordClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: ServerSettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val bgColor = Color(0xFF1C1C1C)
    val cardColor = Color(0xFF2B2B2B)
    val primaryText = Color(0xFFE8E8E8)
    val foregroundText = Color(0xFFFAFAFA)
    val accentColor = Color(0xFF5865F2)
    
    val data = uiState.serverData
    val userRoles = data?.roles?.filter { role ->
        data.users.find { it.id == data.ownUserId }?.roleIds?.contains(role.id) == true
    } ?: emptyList()
    val userPermissions = userRoles.flatMap { it.permissions }.map { it.uppercase() }.toSet()
    val isOwner = data?.ownUserId == 1

    fun hasPerm(p: String) = isOwner || userPermissions.contains(p)

    val tabs = remember(data) {
        val list = mutableListOf<String>()
        if (hasPerm("MANAGE_SETTINGS")) list.add("General")
        if (hasPerm("MANAGE_ROLES")) list.add("Roles")
        if (hasPerm("MANAGE_EMOJIS")) list.add("Emojis")
        if (hasPerm("MANAGE_INVITES")) list.add("Invites")
        if (hasPerm("MANAGE_USERS")) list.add("Users")
        if (hasPerm("MANAGE_PLUGINS")) list.add("Plugins")
        if (hasPerm("MANAGE_STORAGE")) list.add("Storage")
        if (hasPerm("MANAGE_UPDATES")) list.add("Updates")
        if (list.isEmpty()) list.add("No Access")
        list
    }
    
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    // show toast for success/error messages
    LaunchedEffect(uiState.successMessage, uiState.error) {
        uiState.successMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
        uiState.error?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server Settings", color = foregroundText, fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = bgColor,
                contentColor = primaryText,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    if (pagerState.currentPage < tabPositions.size) {
                        TabRowDefaults.Indicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            color = accentColor
                        )
                    }
                },
                divider = { HorizontalDivider(color = Color.White.copy(alpha = 0.1f)) }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = {
                            Text(
                                text = title,
                                color = if (pagerState.currentPage == index) foregroundText else primaryText.copy(alpha = 0.7f),
                                fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val tabName = tabs.getOrNull(page) ?: "Unknown"
                        when (tabName) {
                            "General" -> ServerGeneralTab(viewModel, cardColor, foregroundText, primaryText, accentColor)
                            "Roles" -> ServerRolesTab(uiState.serverData?.roles ?: emptyList(), viewModel, cardColor, foregroundText, primaryText, accentColor)
                            "Emojis" -> ServerEmojisTab(uiState.serverData?.emojis ?: emptyList(), viewModel, cardColor, foregroundText, primaryText, accentColor)
                            "Invites" -> ServerInvitesTab(uiState.activeInvites, uiState.serverData?.roles ?: emptyList(), viewModel, cardColor, foregroundText, primaryText, accentColor)
                            "Users" -> ServerUsersTab(uiState.serverData?.users ?: emptyList(), viewModel, cardColor, foregroundText, primaryText, accentColor)
                            "Plugins" -> ServerPluginsTab(viewModel, cardColor, foregroundText, primaryText, accentColor)
                            "Storage" -> ServerStorageTab(viewModel)
                            "Updates" -> ServerUpdatesTab(viewModel, cardColor, foregroundText, primaryText, accentColor)
                            else -> PlaceholderTab(tabName, primaryText)
                        }
                    }
                }
                
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = accentColor)
                    }
                }
            }

            if (uiState.isModViewOpen) {
                ModViewSheet(
                    uiState = uiState,
                    viewModel = viewModel,
                    onDismissRequest = { viewModel.closeModView() }
                )
            }
        }
    }
}

@Composable
fun PlaceholderTab(tabName: String, primaryText: Color) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$tabName Settings coming soon...", color = primaryText)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerGeneralTab(
    viewModel: ServerSettingsViewModel,
    cardColor: Color,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    
    var showAvatarPicker by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            viewModel.uploadServerLogo(context, it)
            showAvatarPicker = false
        }
    }
    
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraUri != null) {
            viewModel.uploadServerLogo(context, cameraUri!!)
            showAvatarPicker = false
        }
    }
    
    fun openCamera() {
        val file = createImageFile(context)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        cameraUri = uri
        cameraLauncher.launch(uri)
    }

    if (showAvatarPicker) {
        ModalBottomSheet(
            onDismissRequest = { showAvatarPicker = false },
            containerColor = cardColor
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Update Server Logo", color = foregroundText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { openCamera() }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = primaryText)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Take Photo", color = primaryText)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { galleryLauncher.launch("image/*") }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = primaryText)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Choose from Gallery", color = primaryText)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    val adminSettings = uiState.adminSettings

    if (adminSettings == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = accentColor)
        }
        return
    }

    Column(modifier = Modifier.verticalScroll(scrollState).fillMaxSize()) {
        SettingsSection(title = "GENERAL INFORMATION", cardColor = cardColor, foregroundText = foregroundText) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray)
                        .clickable { showAvatarPicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    val logoUrl = SharkordClient.currentServerLogoUrl
                    val logoPainter = com.sharkord.android.ui.components.rememberAsyncImagePainter(logoUrl)
                    if (logoPainter != null) {
                        androidx.compose.foundation.Image(
                            painter = logoPainter,
                            contentDescription = "Server Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text("No Logo", color = Color.Gray, fontSize = 10.sp)
                    }
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Change Logo", tint = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                Text("Update your server's logo to make it recognizable.", color = primaryText, fontSize = 12.sp, modifier = Modifier.weight(1f))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            OutlinedTextField(
                value = adminSettings.name,
                onValueChange = { viewModel.updateAdminSettings(adminSettings.copy(name = it)) },
                label = { Text("Server Name", color = primaryText) },
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
                value = adminSettings.description ?: "",
                onValueChange = { viewModel.updateAdminSettings(adminSettings.copy(description = it)) },
                label = { Text("Server Description", color = primaryText) },
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
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = adminSettings.password ?: "",
                onValueChange = { viewModel.updateAdminSettings(adminSettings.copy(password = it.takeIf { it.isNotEmpty() })) },
                label = { Text("Password", color = primaryText) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = foregroundText,
                    unfocusedTextColor = primaryText,
                    focusedIndicatorColor = accentColor
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            AdminSwitch(
                title = "Consenti nuovi utenti",
                description = "Consenti a chiunque di registrarsi e unirsi al server. Se disabilitato, potranno entrare solo gli utenti invitati.",
                checked = adminSettings.allowNewUsers,
                onCheckedChange = { viewModel.updateAdminSettings(adminSettings.copy(allowNewUsers = it)) },
                foregroundText = foregroundText, primaryText = primaryText, accentColor = accentColor
            )

            AdminSwitch(
                title = "Plugin",
                description = "Abilita o disabilita i plugin del server.",
                checked = adminSettings.enablePlugins,
                onCheckedChange = { viewModel.updateAdminSettings(adminSettings.copy(enablePlugins = it)) },
                foregroundText = foregroundText, primaryText = primaryText, accentColor = accentColor
            )

            AdminSwitch(
                title = "Simulcast",
                description = "Allow users to send multiple video quality layers so receivers can adapt to weaker connections. This will increase bandwidth and CPU usage in the server, but can improve the experience for users on slower connections.",
                checked = adminSettings.webRtcSimulcastEnabled,
                onCheckedChange = { viewModel.updateAdminSettings(adminSettings.copy(webRtcSimulcastEnabled = it)) },
                foregroundText = foregroundText, primaryText = primaryText, accentColor = accentColor
            )

            AdminSwitch(
                title = "Messaggi diretti",
                description = "Consenti agli utenti di inviarsi messaggi diretti. Se disabilitato, potranno comunicare solo nei canali.",
                checked = adminSettings.directMessagesEnabled,
                onCheckedChange = { viewModel.updateAdminSettings(adminSettings.copy(directMessagesEnabled = it)) },
                foregroundText = foregroundText, primaryText = primaryText, accentColor = accentColor
            )

            AdminSwitch(
                title = "Ricerca",
                description = "Consenti agli utenti di cercare messaggi e file in tutto il server.",
                checked = adminSettings.enableSearch,
                onCheckedChange = { viewModel.updateAdminSettings(adminSettings.copy(enableSearch = it)) },
                foregroundText = foregroundText, primaryText = primaryText, accentColor = accentColor
            )

            AdminSwitch(
                title = "Chiedi la password solo al primo accesso",
                description = "Se abilitato, gli utenti devono inserire la password del server solo la prima volta che entrano.",
                checked = adminSettings.onlyAskForPasswordOnFirstJoin,
                onCheckedChange = { viewModel.updateAdminSettings(adminSettings.copy(onlyAskForPasswordOnFirstJoin = it)) },
                foregroundText = foregroundText, primaryText = primaryText, accentColor = accentColor
            )

            AdminSwitch(
                title = "Mostra finestra di benvenuto",
                description = "Mostra agli utenti una finestra di configurazione del profilo la prima volta che entrano nel server.",
                checked = adminSettings.showWelcomeDialog,
                onCheckedChange = { viewModel.updateAdminSettings(adminSettings.copy(showWelcomeDialog = it)) },
                foregroundText = foregroundText, primaryText = primaryText, accentColor = accentColor
            )

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.saveGeneralSettings() },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                modifier = Modifier.align(Alignment.End),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Save Changes")
            }
        }
    }
}

@Composable
fun AdminSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = foregroundText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, color = primaryText, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f))
        )
    }
}

@Composable
fun ServerRolesTab(
    roles: List<Role>,
    viewModel: ServerSettingsViewModel,
    cardColor: Color,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color
) {
    var selectedRole by remember { mutableStateOf<Role?>(null) }

    if (selectedRole != null) {
        RoleEditor(
            role = selectedRole!!,
            onDismiss = { selectedRole = null },
            onSave = { id, name, color, perms ->
                viewModel.updateRole(id, name, color, perms)
                selectedRole = null
            },
            onDelete = { id ->
                viewModel.deleteRole(id)
                selectedRole = null
            },
            cardColor = cardColor,
            foregroundText = foregroundText,
            primaryText = primaryText,
            accentColor = accentColor
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SERVER ROLES", color = foregroundText, fontWeight = FontWeight.Bold)
            Button(
                onClick = { viewModel.createRole() },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text("Create Role")
            }
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(roles) { role ->
                RoleItemRow(
                    role = role,
                    onClick = { selectedRole = role },
                    cardColor = cardColor,
                    foregroundText = foregroundText,
                    primaryText = primaryText
                )
            }
        }
    }
}

@Composable
fun RoleItemRow(role: Role, onClick: () -> Unit, cardColor: Color, foregroundText: Color, primaryText: Color) {
    val roleColorInt = try { android.graphics.Color.parseColor(role.color) } catch (e: Exception) { android.graphics.Color.GRAY }
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
                .size(16.dp)
                .clip(CircleShape)
                .background(Color(roleColorInt))
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(role.name, color = foregroundText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("${role.permissions.size} permissions", color = primaryText, fontSize = 12.sp)
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onClick) {
            Icon(androidx.compose.material.icons.Icons.Default.Edit, contentDescription = "Edit Role", tint = primaryText)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleEditor(
    role: Role,
    onDismiss: () -> Unit,
    onSave: (Int, String, String, List<String>) -> Unit,
    onDelete: (Int) -> Unit,
    cardColor: Color,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color
) {
    var editName by remember { mutableStateOf(role.name) }
    var editColor by remember { mutableStateOf(role.color) }
    var editPermissions by remember { mutableStateOf(role.permissions.toSet()) }

    val allPermissions = listOf(
        "SEND_MESSAGES", "REACT_TO_MESSAGES", "PIN_MESSAGES", "UPLOAD_FILES", 
        "JOIN_VOICE_CHANNELS", "SHARE_SCREEN", "ENABLE_WEBCAM",
        "MANAGE_CHANNELS", "MANAGE_CHANNEL_PERMISSIONS", "MANAGE_CATEGORIES", 
        "MANAGE_ROLES", "MANAGE_EMOJIS", "MANAGE_SETTINGS", "MANAGE_USERS", 
        "MANAGE_MESSAGES", "MANAGE_STORAGE", "MANAGE_INVITES", "MANAGE_UPDATES", 
        "MANAGE_PLUGINS", "USE_PLUGINS", "VIEW_USER_SENSITIVE_DATA"
    )

    val colorPresets = listOf(
        "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5",
        "#2196F3", "#03A9F4", "#00BCD4", "#009688", "#4CAF50",
        "#8BC34A", "#CDDC39", "#FFEB3B", "#FFC107", "#FF9800",
        "#FF5722", "#795548", "#9E9E9E", "#607D8B", "#000000",
        "#FFFFFF", "#2B2B2B"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = cardColor,
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Edit Role", color = foregroundText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (role.id != 1 && role.name.lowercase() != "everyone") {
                        TextButton(onClick = { onDelete(role.id) }) {
                            Text("Delete", color = Color(0xFFED4245))
                        }
                    }
                    Button(
                        onClick = { onSave(role.id, editName, editColor, editPermissions.toList()) },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Text("Save")
                    }
                }
            }

            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Role Name", color = primaryText) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = foregroundText, unfocusedTextColor = primaryText, focusedIndicatorColor = accentColor
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("Role Color", color = foregroundText, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.foundation.lazy.LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(colorPresets) { hex ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Transparent })
                                .clickable { editColor = hex }
                                .border(
                                    width = if (editColor.equals(hex, ignoreCase = true)) 2.dp else 0.dp,
                                    color = if (editColor.equals(hex, ignoreCase = true)) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editColor,
                    onValueChange = { editColor = it },
                    label = { Text("Hex Color", color = primaryText) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = foregroundText, unfocusedTextColor = primaryText, focusedIndicatorColor = accentColor
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))
                Text("Permissions", color = foregroundText, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                allPermissions.forEach { perm ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(perm.replace("_", " "), color = primaryText, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = editPermissions.contains(perm),
                            onCheckedChange = { checked ->
                                if (checked) editPermissions = editPermissions + perm
                                else editPermissions = editPermissions - perm
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
