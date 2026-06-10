package com.sharkord.android.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
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
import com.sharkord.android.data.model.MarketplaceEntry
import com.sharkord.android.data.model.PluginInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerPluginsTab(
    viewModel: ServerSettingsViewModel,
    cardColor: Color,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var selectedPluginId by remember { mutableStateOf<String?>(null) }
    val tabs = listOf("Installed", "Marketplace")

    LaunchedEffect(Unit) {
        viewModel.fetchPlugins()
        viewModel.fetchMarketplacePlugins()
    }

    if (selectedPluginId != null) {
        val plugin = uiState.plugins.find { it.id == selectedPluginId }
        val marketplaceEntry = uiState.marketplaceEntries.find { it.plugin.id == selectedPluginId }

        if (plugin != null || marketplaceEntry != null) {
            PluginDetailsSheet(
                plugin = plugin,
                marketplaceEntry = marketplaceEntry,
                viewModel = viewModel,
                onDismiss = { selectedPluginId = null },
                cardColor = cardColor,
                foregroundText = foregroundText,
                primaryText = primaryText,
                accentColor = accentColor
            )
        } else {
            selectedPluginId = null
        }
    }

    var actionPluginId by remember { mutableStateOf<String?>(null) }

    if (actionPluginId != null) {
        if (uiState.pluginLogs != null) {
            PluginLogsSheet(
                pluginId = actionPluginId!!,
                logs = uiState.pluginLogs!!,
                onDismiss = {
                    actionPluginId = null
                    viewModel.clearPluginModals()
                },
                cardColor = cardColor,
                foregroundText = foregroundText,
                primaryText = primaryText,
                accentColor = accentColor
            )
        } else if (uiState.pluginCommands != null) {
            PluginCommandsSheet(
                pluginId = actionPluginId!!,
                commands = uiState.pluginCommands!!,
                viewModel = viewModel,
                onDismiss = {
                    actionPluginId = null
                    viewModel.clearPluginModals()
                },
                cardColor = cardColor,
                foregroundText = foregroundText,
                primaryText = primaryText,
                accentColor = accentColor
            )
        } else if (uiState.pluginSettings != null) {
            PluginSettingsSheet(
                pluginId = actionPluginId!!,
                settingsResponse = uiState.pluginSettings!!,
                viewModel = viewModel,
                onDismiss = {
                    actionPluginId = null
                    viewModel.clearPluginModals()
                },
                cardColor = cardColor,
                foregroundText = foregroundText,
                primaryText = primaryText,
                accentColor = accentColor
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "SERVER PLUGINS",
            color = foregroundText,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent,
            contentColor = primaryText,
            indicator = { tabPositions ->
                if (selectedTabIndex < tabPositions.size) {
                    TabRowDefaults.Indicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = accentColor
                    )
                }
            },
            divider = { HorizontalDivider(color = Color.White.copy(alpha = 0.1f)) }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            color = if (selectedTabIndex == index) foregroundText else primaryText.copy(alpha = 0.7f),
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTabIndex) {
                0 -> InstalledPluginsList(
                    plugins = uiState.plugins,
                    isLoading = uiState.isLoading && uiState.plugins.isEmpty(),
                    viewModel = viewModel,
                    onPluginClick = { selectedPluginId = it },
                    onOpenLogs = { 
                        actionPluginId = it
                        viewModel.fetchPluginLogs(it)
                    },
                    onOpenCommands = { 
                        actionPluginId = it
                        viewModel.fetchPluginCommands(it)
                    },
                    onOpenSettings = { 
                        actionPluginId = it
                        viewModel.fetchPluginSettings(it)
                    },
                    cardColor = cardColor,
                    foregroundText = foregroundText,
                    primaryText = primaryText,
                    accentColor = accentColor
                )
                1 -> MarketplacePluginsList(
                    entries = uiState.marketplaceEntries,
                    installedPlugins = uiState.plugins,
                    isLoading = uiState.isMarketplaceLoading && uiState.marketplaceEntries.isEmpty(),
                    viewModel = viewModel,
                    onPluginClick = { selectedPluginId = it },
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
fun InstalledPluginsList(
    plugins: List<PluginInfo>,
    isLoading: Boolean,
    viewModel: ServerSettingsViewModel,
    onPluginClick: (String) -> Unit,
    onOpenLogs: (String) -> Unit,
    onOpenCommands: (String) -> Unit,
    onOpenSettings: (String) -> Unit,
    cardColor: Color,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = accentColor)
        }
    } else if (plugins.isEmpty()) {
        Text("No plugins installed or available.", color = primaryText)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(plugins) { plugin ->
                PluginItemRow(
                    plugin = plugin,
                    viewModel = viewModel,
                    onClick = { onPluginClick(plugin.id) },
                    onOpenLogs = { onOpenLogs(plugin.id) },
                    onOpenCommands = { onOpenCommands(plugin.id) },
                    onOpenSettings = { onOpenSettings(plugin.id) },
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
fun MarketplacePluginsList(
    entries: List<MarketplaceEntry>,
    installedPlugins: List<PluginInfo>,
    isLoading: Boolean,
    viewModel: ServerSettingsViewModel,
    onPluginClick: (String) -> Unit,
    cardColor: Color,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredEntries = remember(searchQuery, entries) {
        if (searchQuery.isBlank()) entries
        else entries.filter {
            it.plugin.name.contains(searchQuery, ignoreCase = true) ||
            it.plugin.description.contains(searchQuery, ignoreCase = true) ||
            it.plugin.author.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search Marketplace", color = primaryText) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = cardColor,
                unfocusedContainerColor = cardColor,
                focusedTextColor = foregroundText,
                unfocusedTextColor = primaryText,
                focusedIndicatorColor = accentColor
            )
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accentColor)
            }
        } else if (filteredEntries.isEmpty()) {
            Text("No marketplace plugins found.", color = primaryText)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredEntries) { entry ->
                    MarketplaceItemRow(
                        entry = entry,
                        installedPlugins = installedPlugins,
                        viewModel = viewModel,
                        onClick = { onPluginClick(entry.plugin.id) },
                        cardColor = cardColor,
                        foregroundText = foregroundText,
                        primaryText = primaryText,
                        accentColor = accentColor
                    )
                }
            }
        }
    }
}

@Composable
fun PluginItemRow(
    plugin: PluginInfo,
    viewModel: ServerSettingsViewModel,
    onClick: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenCommands: () -> Unit,
    onOpenSettings: () -> Unit,
    cardColor: Color,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color
) {
    val context = LocalContext.current
    var showRemoveDialog by remember { mutableStateOf(false) }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove Plugin") },
            text = { Text("Are you sure you want to remove ${plugin.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveDialog = false
                    viewModel.removePlugin(plugin.id)
                }) {
                    Text("Remove", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text("Cancel", color = primaryText)
                }
            },
            containerColor = cardColor,
            titleContentColor = foregroundText,
            textContentColor = primaryText
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cardColor)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (plugin.logo != null) {
                    val painter = com.sharkord.android.ui.components.rememberAsyncImagePainter(plugin.logo)
                    if (painter != null) {
                        androidx.compose.foundation.Image(
                            painter = painter,
                            contentDescription = "Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color.Gray))
                    }
                } else {
                    Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray))
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(plugin.name, color = foregroundText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(plugin.description, color = primaryText, fontSize = 14.sp, maxLines = 2)
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (plugin.loadError != null) {
                    Badge(containerColor = Color.Red, contentColor = Color.White) {
                        Text("ERROR")
                    }
                } else {
                    Badge(
                        containerColor = if (plugin.enabled) accentColor else Color.DarkGray,
                        contentColor = Color.White
                    ) {
                        Text(if (plugin.enabled) "ENABLED" else "DISABLED")
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = plugin.enabled,
                    onCheckedChange = { viewModel.togglePlugin(plugin.id, it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f))
                )
            }
        }
        
        if (plugin.loadError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Error: ${plugin.loadError}", color = Color.Red, fontSize = 12.sp)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("v${plugin.version}", color = primaryText, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(16.dp))
                Text("By ${plugin.author}", color = primaryText, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenLogs) {
                    Icon(Icons.Default.Description, contentDescription = "Logs", tint = primaryText)
                }
                IconButton(
                    onClick = onOpenCommands,
                    enabled = plugin.enabled
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = "Commands", tint = if (plugin.enabled) primaryText else Color.Gray)
                }
                IconButton(
                    onClick = onOpenSettings,
                    enabled = plugin.enabled
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = if (plugin.enabled) primaryText else Color.Gray)
                }
                IconButton(onClick = { showRemoveDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red)
                }
            }
        }
    }
}

@Composable
fun MarketplaceItemRow(
    entry: MarketplaceEntry,
    installedPlugins: List<PluginInfo>,
    viewModel: ServerSettingsViewModel,
    onClick: () -> Unit,
    cardColor: Color,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color
) {
    val context = LocalContext.current
    val installed = installedPlugins.find { it.id == entry.plugin.id }
    val latestVersion = entry.versions.firstOrNull()?.version ?: "0.0.0"
    
    val canUpdate = installed != null && installed.version != latestVersion

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cardColor)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                val painter = com.sharkord.android.ui.components.rememberAsyncImagePainter(entry.plugin.logo)
                if (painter != null) {
                    androidx.compose.foundation.Image(
                        painter = painter,
                        contentDescription = "Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray))
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.plugin.name, color = foregroundText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(entry.plugin.description, color = primaryText, fontSize = 14.sp, maxLines = 2)
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (installed == null) {
                    Button(
                        onClick = { viewModel.installPlugin(entry.plugin.id, latestVersion) },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Text("Install")
                    }
                } else if (canUpdate) {
                    Button(
                        onClick = { viewModel.updatePlugin(entry.plugin.id, latestVersion) },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Text("Update to $latestVersion")
                    }
                } else {
                    Button(
                        onClick = { },
                        enabled = false,
                        colors = ButtonDefaults.buttonColors(disabledContainerColor = Color.DarkGray, disabledContentColor = Color.LightGray)
                    ) {
                        Text("Installed")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Latest: v$latestVersion", color = primaryText, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Text("By ${entry.plugin.author}", color = primaryText, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginDetailsSheet(
    plugin: PluginInfo?,
    marketplaceEntry: MarketplaceEntry?,
    viewModel: ServerSettingsViewModel,
    onDismiss: () -> Unit,
    cardColor: Color,
    foregroundText: Color,
    primaryText: Color,
    accentColor: Color
) {
    val context = LocalContext.current
    val name = plugin?.name ?: marketplaceEntry?.plugin?.name ?: ""
    val description = plugin?.description ?: marketplaceEntry?.plugin?.description ?: ""
    val author = plugin?.author ?: marketplaceEntry?.plugin?.author ?: ""
    val version = plugin?.version ?: marketplaceEntry?.versions?.firstOrNull()?.version ?: ""
    val logo = plugin?.logo ?: marketplaceEntry?.plugin?.logo
    val homepage = plugin?.homepage ?: marketplaceEntry?.plugin?.homepage
    val tags = marketplaceEntry?.plugin?.tags ?: emptyList()
    val categories = marketplaceEntry?.plugin?.categories ?: emptyList()
    val screenshots = marketplaceEntry?.plugin?.screenshots ?: emptyList()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = cardColor
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (logo != null) {
                    val painter = com.sharkord.android.ui.components.rememberAsyncImagePainter(logo)
                    if (painter != null) {
                        androidx.compose.foundation.Image(
                            painter = painter,
                            contentDescription = "Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(Color.Gray))
                    }
                } else {
                    Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(name, color = foregroundText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("By $author • v$version", color = primaryText, fontSize = 14.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("Description", color = foregroundText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, color = primaryText, fontSize = 14.sp)
            
            if (tags.isNotEmpty() || categories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Tags & Categories", color = foregroundText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val allChips = tags + categories
                    allChips.forEach { tag ->
                        Badge(containerColor = Color.DarkGray, contentColor = primaryText) {
                            Text(tag, modifier = Modifier.padding(4.dp))
                        }
                    }
                }
            }

            if (screenshots.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Screenshots", color = foregroundText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(screenshots) { url ->
                        val painter = com.sharkord.android.ui.components.rememberAsyncImagePainter(url)
                        if (painter != null) {
                            androidx.compose.foundation.Image(
                                painter = painter,
                                contentDescription = "Screenshot",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.height(200.dp).width(300.dp).clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Box(modifier = Modifier.height(200.dp).width(300.dp).clip(RoundedCornerShape(8.dp)).background(Color.Gray))
                        }
                    }
                }
            }
            
            if (homepage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(homepage))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Invalid URL", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Visit Homepage")
                }
            }

            if (marketplaceEntry != null) {
                val latestVersion = marketplaceEntry.versions.firstOrNull()?.version ?: "0.0.0"
                val canUpdate = plugin != null && plugin.version != latestVersion

                Spacer(modifier = Modifier.height(8.dp))
                if (plugin == null) {
                    Button(
                        onClick = {
                            viewModel.installPlugin(marketplaceEntry.plugin.id, latestVersion)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Install")
                    }
                } else if (canUpdate) {
                    Button(
                        onClick = {
                            viewModel.updatePlugin(marketplaceEntry.plugin.id, latestVersion)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Update to $latestVersion")
                    }
                } else {
                    Button(
                        onClick = { },
                        enabled = false,
                        colors = ButtonDefaults.buttonColors(disabledContainerColor = Color.DarkGray, disabledContentColor = Color.LightGray),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Installed")
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
