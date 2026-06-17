package com.sharkord.android.ui.home.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.sharkord.android.ui.components.rememberAsyncImagePainter
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.ui.theme.LocalSharkordColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicePanel(
    channelName: String,
    voiceUsers: List<VoiceUserDisplay>,
    isConnected: Boolean,
    isMuted: Boolean = true,
    isDeafened: Boolean = true,
    onDisconnectClick: () -> Unit,
    onConnectClick: () -> Unit,
    onToggleMicClick: (Boolean) -> Unit = {},
    onToggleDeafenClick: (Boolean) -> Unit = {},
    onOpenChatClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSharkordColors.current
    val context = LocalContext.current

    var showOutputDropdown by remember { mutableStateOf(false) }
    var showInputDropdown by remember { mutableStateOf(false) }
    var selectedOutputDeviceId by remember { mutableStateOf<Int?>(null) }
    var selectedInputDeviceId by remember { mutableStateOf<Int?>(null) }
    var deviceListTrigger by remember { mutableStateOf(0) }

    var availableOutputs by remember { mutableStateOf<List<AudioDeviceInfo>>(emptyList()) }
    var availableInputs by remember { mutableStateOf<List<AudioDeviceInfo>>(emptyList()) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    DisposableEffect(audioManager) {
        val callback = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                deviceListTrigger++
                val newBtOutput = addedDevices?.firstOrNull {
                    it.isSink && (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || 
                                  it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
                                  (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && it.type == 26))
                }
                if (newBtOutput != null) {
                    selectedOutputDeviceId = newBtOutput.id
                }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                deviceListTrigger++
                if (removedDevices?.any { it.id == selectedOutputDeviceId } == true) {
                    selectedOutputDeviceId = null
                }
                if (removedDevices?.any { it.id == selectedInputDeviceId } == true) {
                    selectedInputDeviceId = null
                }
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        onDispose {
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }

    LaunchedEffect(showOutputDropdown, showInputDropdown, isConnected, deviceListTrigger) {
        val dedupKey: (AudioDeviceInfo) -> String = {
            if (it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE || it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) "${it.type}"
            else it.productName?.toString()?.takeIf { name -> name.isNotBlank() } ?: it.id.toString()
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val allowedOutputTypes = setOf(
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                26 // TYPE_BLE_HEADSET
            )
            val allowedInputTypes = setOf(
                AudioDeviceInfo.TYPE_BUILTIN_MIC,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                26 // TYPE_BLE_HEADSET
            )

            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.isSink && it.type in allowedOutputTypes }
            val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).filter { it.isSource && it.type in allowedInputTypes }
            
            availableOutputs = outputs.distinctBy(dedupKey).toList()
            availableInputs = inputs.distinctBy(dedupKey).toList()
        }
    }

    LaunchedEffect(selectedOutputDeviceId, selectedInputDeviceId, isConnected) {
        if (!isConnected) return@LaunchedEffect
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val targetId = selectedOutputDeviceId ?: selectedInputDeviceId
            if (targetId != null) {
                val commDevices = audioManager.availableCommunicationDevices
                var commDevice = commDevices.firstOrNull { it.id == targetId }
                if (commDevice == null) {
                    val selectedDevice = availableInputs.find { it.id == targetId } ?: availableOutputs.find { it.id == targetId }
                    if (selectedDevice != null) {
                        val expectedType = if (selectedDevice.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) AudioDeviceInfo.TYPE_BLUETOOTH_SCO else selectedDevice.type
                        commDevice = commDevices.firstOrNull { it.type == expectedType && it.productName == selectedDevice.productName }
                        if (commDevice == null && expectedType == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                            commDevice = commDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                        }
                    }
                }
                
                if (commDevice != null) {
                    audioManager.setCommunicationDevice(commDevice)
                } else {
                    audioManager.clearCommunicationDevice()
                }
            } else {
                audioManager.clearCommunicationDevice()
            }
        } else {
            val isSpeaker = availableOutputs.find { it.id == selectedOutputDeviceId }?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = isSpeaker == true
        }
    }

    Column(modifier = modifier.background(colors.bgColor)) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Back",
                    tint = colors.foregroundText
                )
            }
            
            Icon(
                Icons.Default.VolumeUp,
                contentDescription = null,
                tint = colors.foregroundText,
                modifier = Modifier.padding(end = 8.dp).size(20.dp)
            )

            Text(
                text = channelName,
                color = colors.foregroundText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            // Audio Output Mode Button
            Box {
                IconButton(onClick = { showOutputDropdown = true }) {
                    val currentDevice = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).find { it.id == selectedOutputDeviceId } ?: availableOutputs.find { it.id == selectedOutputDeviceId }
                    val isBluetooth = currentDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || 
                                      currentDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
                                      (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && (currentDevice?.type == 26 || currentDevice?.type == 27)) ||
                                      currentDevice?.type == 23 // TYPE_HEARING_AID
                                      
                    if (selectedOutputDeviceId == null) {
                        Text(
                            text = "A",
                            color = colors.foregroundText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (isBluetooth) {
                        Icon(Icons.Default.Bluetooth, contentDescription = "Bluetooth", tint = colors.foregroundText)
                    } else if (currentDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
                        Icon(Icons.Default.Hearing, contentDescription = "Earpiece", tint = colors.foregroundText)
                    } else {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Speaker", tint = colors.foregroundText)
                    }
                }
                DropdownMenu(
                    expanded = showOutputDropdown,
                    onDismissRequest = { showOutputDropdown = false },
                    modifier = Modifier.background(colors.cardColor)
                ) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Text(
                                text = "A",
                                color = colors.foregroundText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        },
                        text = { Text("Auto", color = colors.foregroundText) },
                        onClick = {
                            selectedOutputDeviceId = null
                            showOutputDropdown = false
                        }
                    )
                    availableOutputs.forEach { device ->
                        DropdownMenuItem(
                            leadingIcon = {
                                val isBluetooth = device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || 
                                                  device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
                                                  (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && (device.type == 26 || device.type == 27)) ||
                                                  device.type == 23
                                if (isBluetooth) {
                                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = colors.foregroundText)
                                } else if (device.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
                                    Icon(Icons.Default.Hearing, contentDescription = null, tint = colors.foregroundText)
                                } else {
                                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = colors.foregroundText)
                                }
                            },
                            text = { 
                                val name = when (device.type) {
                                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
                                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Stereo"
                                    else -> device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Device ${device.id}"
                                }
                                Text(name, color = colors.foregroundText) 
                            },
                            onClick = {
                                selectedOutputDeviceId = device.id
                                showOutputDropdown = false
                            }
                        )
                    }
                }
            }

            // Audio Input Mode Button
            if (availableInputs.size > 1) {
                Box {
                IconButton(onClick = { showInputDropdown = true }) {
                    val currentMic = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).find { it.id == selectedInputDeviceId } ?: availableInputs.find { it.id == selectedInputDeviceId }
                    val isBluetoothMic = currentMic?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || 
                                         currentMic?.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
                                         (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && (currentMic?.type == 26 || currentMic?.type == 27)) ||
                                         currentMic?.type == 23
                                         
                    if (selectedInputDeviceId == null) {
                        Text(
                            text = "A",
                            color = colors.foregroundText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (isBluetoothMic) {
                        Icon(Icons.Default.Bluetooth, contentDescription = "Bluetooth Mic", tint = colors.foregroundText)
                    } else {
                        Icon(Icons.Default.Mic, contentDescription = "Select Audio Input", tint = colors.foregroundText)
                    }
                }
                DropdownMenu(
                    expanded = showInputDropdown,
                    onDismissRequest = { showInputDropdown = false },
                    modifier = Modifier.background(colors.cardColor)
                ) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Text(
                                text = "A",
                                color = colors.foregroundText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        },
                        text = { Text("Auto", color = colors.foregroundText) },
                        onClick = {
                            selectedInputDeviceId = null
                            showInputDropdown = false
                        }
                    )
                    availableInputs.forEach { device ->
                        DropdownMenuItem(
                            leadingIcon = {
                                val isBluetoothMic = device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || 
                                                     device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
                                                     (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && (device.type == 26 || device.type == 27)) ||
                                                     device.type == 23
                                if (isBluetoothMic) {
                                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = colors.foregroundText)
                                } else {
                                    Icon(Icons.Default.Mic, contentDescription = null, tint = colors.foregroundText)
                                }
                            },
                            text = { 
                                val name = when (device.type) {
                                    AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Phone Mic"
                                    else -> device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Mic ${device.id}"
                                }
                                Text(name, color = colors.foregroundText) 
                            },
                            onClick = {
                                selectedInputDeviceId = device.id
                                showInputDropdown = false
                            }
                        )
                    }
                }
            }
            }

            // Chat Button
            IconButton(onClick = onOpenChatClick) {
                Icon(
                    Icons.Default.ChatBubble,
                    contentDescription = "Open Chat",
                    tint = colors.foregroundText
                )
            }
        }

        Divider(color = colors.cardColor, thickness = 1.dp)

        // Main Content - Users Grid
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (voiceUsers.isEmpty()) {
                Text(
                    text = "No one is here right now.",
                    color = colors.primaryText,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val availableWidth = maxWidth - 32.dp // padding left + right
                    val availableHeight = maxHeight - 32.dp // padding top + bottom
                    
                    val minBoxWidthDp = 140f //controls minimum box width per user
                    val minBoxHeightDp = 120f //controls minimum box height per user
                    
                    val maxPossibleCols = maxOf(1, (availableWidth.value / minBoxWidthDp).toInt())
                    val maxPossibleRows = maxOf(1, (availableHeight.value / minBoxHeightDp).toInt())
                    var bestCols = 1
                    var bestDiff = Float.MAX_VALUE
                    
                    for (c in 1..maxPossibleCols) {
                        if (c > voiceUsers.size && voiceUsers.isNotEmpty()) break
                        
                        val r = kotlin.math.ceil(voiceUsers.size.toFloat() / c).toInt()
                        val visibleR = r.coerceAtMost(maxPossibleRows)
                        
                        val totalWSpacing = 16f * (c - 1)
                        val totalHSpacing = 16f * (visibleR - 1)
                        
                        val w = maxOf(1f, (availableWidth.value - totalWSpacing) / c)
                        val h = maxOf(minBoxHeightDp, (availableHeight.value - totalHSpacing) / maxOf(1, visibleR))
                        
                        val ratio = w / h
                        val diff = kotlin.math.abs(ratio - 1f) + kotlin.math.abs(1f / ratio - 1f)
                        
                        if (diff < bestDiff) {
                            bestDiff = diff
                            bestCols = c
                        }
                    }
                    
                    val cols = bestCols
                    val rows = kotlin.math.ceil(voiceUsers.size.toFloat() / cols).toInt()
                    val visibleRows = rows.coerceAtMost(maxPossibleRows)
                    
                    val totalSpacing = 16.dp * (visibleRows - 1)
                    val itemHeight = if (visibleRows > 0) {
                        ((availableHeight - totalSpacing) / visibleRows).coerceAtLeast(minBoxHeightDp.dp)
                    } else {
                        availableHeight.coerceAtLeast(minBoxHeightDp.dp)
                    }

                    val itemsInLastRow = if (voiceUsers.size % cols == 0) cols else voiceUsers.size % cols
                    val gridCols = if (voiceUsers.isNotEmpty()) lcm(cols, itemsInLastRow) else 1
                    val normalSpan = if (gridCols > 0) gridCols / cols else 1
                    val lastRowSpan = if (gridCols > 0) gridCols / itemsInLastRow else 1

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridCols),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            count = voiceUsers.size,
                            span = { index ->
                                val isLastRow = index >= voiceUsers.size - itemsInLastRow
                                androidx.compose.foundation.lazy.grid.GridItemSpan(if (isLastRow) lastRowSpan else normalSpan)
                            }
                        ) { index ->
                            val voiceUser = voiceUsers[index]
                            
                            val borderWidth by animateDpAsState(targetValue = if (voiceUser.isSpeaking) 3.dp else 0.dp)
                            val borderColor = if (voiceUser.isSpeaking) Color.Green else Color.Transparent
                            
                            Box(
                                modifier = Modifier
                                    .height(itemHeight)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colors.cardColor)
                                    .border(borderWidth, borderColor, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                // Avatar placeholder or Image
                                val avatarUrl = voiceUser.user.avatar?.name?.let { "${SharkordClient.currentServerUrl}/public/$it" }
                                val avatarPainter = rememberAsyncImagePainter(avatarUrl, fallbackResourceId = null)

                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color.DarkGray),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (avatarPainter != null) {
                                        Image(
                                            painter = avatarPainter,
                                            contentDescription = "User Avatar",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            text = voiceUser.user.name.take(1).uppercase(),
                                            color = Color.White,
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                // User Name Tag
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    val canFitBoth = maxWidth > 100.dp
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = voiceUser.user.name,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        
                                        val isDeafened = voiceUser.state.soundMuted
                                        val isMuted = voiceUser.state.micMuted
                                        
                                        if (isDeafened) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                Icons.Default.HeadsetOff,
                                                contentDescription = "Deafened",
                                                tint = Color.Red,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        if (isMuted && (!isDeafened || canFitBoth)) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                Icons.Default.MicOff,
                                                contentDescription = "Muted",
                                                tint = Color.Red,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Controls
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(colors.cardColor)
                .padding(vertical = 24.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isConnected) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mic Toggle
                    FloatingActionButton(
                        onClick = { onToggleMicClick(!isMuted) },
                        containerColor = if (isMuted) colors.bgColor else colors.foregroundText,
                        contentColor = if (isMuted) colors.foregroundText else colors.bgColor,
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, 
                            contentDescription = if (isMuted) "Unmute" else "Mute"
                        )
                    }

                    // Disconnect
                    FloatingActionButton(
                        onClick = onDisconnectClick,
                        containerColor = Color(0xFFED4245),
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "Disconnect", modifier = Modifier.size(32.dp))
                    }

                    // Deafen Toggle
                    FloatingActionButton(
                        onClick = { onToggleDeafenClick(!isDeafened) },
                        containerColor = if (isDeafened) colors.bgColor else colors.foregroundText,
                        contentColor = if (isDeafened) colors.foregroundText else colors.bgColor,
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(if (isDeafened) Icons.Default.HeadsetOff else Icons.Default.Headset, contentDescription = if (isDeafened) "Undeafen" else "Deafen")
                    }
                }
            } else {
                Button(
                    onClick = onConnectClick,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF23A559)) // Green connect button
                ) {
                    Text("Join Voice", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun gcd(a: Int, b: Int): Int {
    var num1 = a
    var num2 = b
    while (num2 != 0) {
        val temp = num2
        num2 = num1 % num2
        num1 = temp
    }
    return num1
}

private fun lcm(a: Int, b: Int): Int {
    return if (a == 0 || b == 0) 0 else (a * b) / gcd(a, b)
}
