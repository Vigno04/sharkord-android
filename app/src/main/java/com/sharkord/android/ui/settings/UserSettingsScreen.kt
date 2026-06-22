package com.sharkord.android.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.foundation.Image
import com.sharkord.android.data.network.SharkordClient
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraEnumerationAndroid.CaptureFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: UserSettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val bgColor = Color(0xFF1C1C1C)
    val cardColor = Color(0xFF2B2B2B)
    val primaryText = Color(0xFFE8E8E8)
    val foregroundText = Color(0xFFFAFAFA)
    val accentColor = Color(0xFF5865F2)
    
    val tabs = listOf("Profile", "Devices", "Password", "Notifications", "App Settings", "Others")
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
                title = { Text("User Settings", color = foregroundText, fontWeight = FontWeight.Bold) },
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
                divider = { Divider(color = Color.White.copy(alpha = 0.1f)) }
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

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accentColor)
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (page) {
                            0 -> ProfileTabContent(viewModel, cardColor, foregroundText, primaryText, accentColor)
                            1 -> DevicesTabContent(viewModel, cardColor, foregroundText, primaryText, accentColor)
                            2 -> PasswordTabContent(viewModel, cardColor, foregroundText, primaryText, accentColor)
                            3 -> NotificationsTabContent(cardColor, foregroundText, primaryText, accentColor)
                            4 -> AppSettingsTabContent(viewModel, cardColor, foregroundText, primaryText, accentColor)
                            5 -> OthersTabContent(cardColor, foregroundText, primaryText, accentColor)
                        }
                    }
                }
            }
        }
    }
}

// helper for temporary file creation
fun createImageFile(context: Context): File {
    val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val storageDir: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    return File.createTempFile(
        "JPEG_${timeStamp}_",
        ".jpg",
        storageDir
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileTabContent(
    viewModel: UserSettingsViewModel,
    cardColor: Color, 
    foregroundText: Color, 
    primaryText: Color, 
    accentColor: Color
) {
    val name by viewModel.name.collectAsState()
    val bio by viewModel.bio.collectAsState()
    val bannerColor by viewModel.bannerColor.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    var showAvatarPicker by remember { mutableStateOf(false) }
    var showBannerPicker by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            if (showAvatarPicker) {
                viewModel.uploadAvatar(context, it)
                showAvatarPicker = false
            } else if (showBannerPicker) {
                viewModel.uploadBanner(context, it)
                showBannerPicker = false
            }
        }
    }
    
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraUri != null) {
            if (showAvatarPicker) {
                viewModel.uploadAvatar(context, cameraUri!!)
                showAvatarPicker = false
            } else if (showBannerPicker) {
                viewModel.uploadBanner(context, cameraUri!!)
                showBannerPicker = false
            }
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

    if (showAvatarPicker || showBannerPicker) {
        ModalBottomSheet(
            onDismissRequest = { 
                showAvatarPicker = false
                showBannerPicker = false
            },
            containerColor = cardColor
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(if (showAvatarPicker) "Update Avatar" else "Update Banner", color = foregroundText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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

    SettingsSection(title = "USER PROFILE", cardColor = cardColor, foregroundText = foregroundText) {
        
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray)
                    .clickable { showAvatarPicker = true },
                contentAlignment = Alignment.Center
            ) {
                val avatarUrl = uiState.user?.avatar?.name?.let { "${SharkordClient.currentServerUrl}/public/$it" }
                val avatarPainter = com.sharkord.android.ui.components.rememberAsyncImagePainter(avatarUrl)
                if (avatarPainter != null) {
                    Image(
                        painter = avatarPainter,
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("No Avatar", color = Color.Gray, fontSize = 10.sp)
                }
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Change Avatar", tint = Color.White)
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Box(
                modifier = Modifier
                    .height(80.dp)
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        try { Color(android.graphics.Color.parseColor(bannerColor)) } 
                        catch (e: Exception) { Color.DarkGray }
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .clickable { showBannerPicker = true },
                contentAlignment = Alignment.Center
            ) {
                val bannerUrl = uiState.user?.banner?.name?.let { "${SharkordClient.currentServerUrl}/public/$it" }
                val bannerPainter = com.sharkord.android.ui.components.rememberAsyncImagePainter(bannerUrl)
                if (bannerPainter != null) {
                    Image(
                        painter = bannerPainter,
                        contentDescription = "Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Image, contentDescription = "Change Banner", tint = Color.White)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = name,
            onValueChange = { viewModel.name.value = it },
            label = { Text("Display Name", color = primaryText) },
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
            value = bio,
            onValueChange = { viewModel.bio.value = it },
            label = { Text("Bio", color = primaryText) },
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
        val colorPresets = listOf(
            "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5",
            "#2196F3", "#03A9F4", "#00BCD4", "#009688", "#4CAF50",
            "#8BC34A", "#CDDC39", "#FFEB3B", "#FFC107", "#FF9800",
            "#FF5722", "#795548", "#9E9E9E", "#607D8B", "#000000",
            "#FFFFFF", "#2B2B2B"
        )
        Text("Banner Color", color = foregroundText, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(colorPresets) { colorHex ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            try {
                                Color(android.graphics.Color.parseColor(colorHex))
                            } catch (e: Exception) {
                                Color.Transparent
                            }
                        )
                        .clickable { viewModel.bannerColor.value = colorHex }
                        .border(
                            width = if (bannerColor.equals(colorHex, ignoreCase = true)) 2.dp else 0.dp,
                            color = if (bannerColor.equals(colorHex, ignoreCase = true)) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = bannerColor,
            onValueChange = { viewModel.bannerColor.value = it },
            label = { Text("Banner Color (Hex)", color = primaryText) },
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
        Button(
            onClick = { viewModel.saveProfile() },
            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
            modifier = Modifier.align(Alignment.End),
            enabled = !uiState.isSavingProfile
        ) {
            if (uiState.isSavingProfile) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Save Changes")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesTabContent(viewModel: UserSettingsViewModel, cardColor: Color, foregroundText: Color, primaryText: Color, accentColor: Color) {
    val defaultAudioRoute by viewModel.defaultAudioRoute.collectAsState()
    val echoCancellation by viewModel.echoCancellation.collectAsState()
    val noiseSuppression by viewModel.noiseSuppression.collectAsState()
    val autoGainControl by viewModel.autoGainControl.collectAsState()

    val defaultCamera by viewModel.defaultCamera.collectAsState()
    val videoResolution by viewModel.videoResolution.collectAsState()
    val videoFps by viewModel.videoFps.collectAsState()
    val mirrorFrontCamera by viewModel.mirrorFrontCamera.collectAsState()

    val screenShareOptimizeFor by viewModel.screenShareOptimizeFor.collectAsState()

    var expandedAudioRoute by remember { mutableStateOf(false) }
    var expandedCamera by remember { mutableStateOf(false) }
    var expandedVideoResolution by remember { mutableStateOf(false) }
    var expandedVideoFps by remember { mutableStateOf(false) }
    var expandedOptimizeFor by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var availableResolutions by remember { mutableStateOf<List<String>>(emptyList()) }
    var availableFpsList by remember { mutableStateOf<List<Int>>(emptyList()) }

    LaunchedEffect(defaultCamera) {
        val enumerator: CameraEnumerator = if (Camera2Enumerator.isSupported(context)) {
            Camera2Enumerator(context)
        } else {
            Camera1Enumerator(true)
        }
        val deviceNames = enumerator.deviceNames
        var targetDevice: String? = null
        for (name in deviceNames) {
            val isFront = enumerator.isFrontFacing(name)
            if ((defaultCamera == "Front" && isFront) || (defaultCamera == "Back" && !isFront)) {
                targetDevice = name
                break
            }
        }
        if (targetDevice == null) {
            targetDevice = deviceNames.firstOrNull()
        }
        
        if (targetDevice != null) {
            val formats = enumerator.getSupportedFormats(targetDevice)
            availableResolutions = formats.map { "${it.width}x${it.height}" }
                .distinct()
                .sortedByDescending { it.split("x")[0].toInt() * it.split("x")[1].toInt() }
            availableFpsList = formats.map { it.framerate.max / 1000 }
                .distinct()
                .sortedDescending()
        } else {
            availableResolutions = listOf("1280x720")
            availableFpsList = listOf(30)
        }
    }

    SettingsSection(title = "AUDIO SETTINGS", cardColor = cardColor, foregroundText = foregroundText) {
        ExposedDropdownMenuBox(
            expanded = expandedAudioRoute,
            onExpandedChange = { expandedAudioRoute = !expandedAudioRoute },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = defaultAudioRoute,
                onValueChange = {},
                readOnly = true,
                label = { Text("Default Audio Route") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAudioRoute) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = foregroundText, unfocusedTextColor = primaryText
                )
            )
            ExposedDropdownMenu(expanded = expandedAudioRoute, onDismissRequest = { expandedAudioRoute = false }) {
                listOf("None", "Speaker", "Earpiece", "Bluetooth").forEach { route ->
                    DropdownMenuItem(text = { Text(route) }, onClick = { viewModel.saveDefaultAudioRoute(route); expandedAudioRoute = false })
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Hardware Echo Cancellation", color = foregroundText, modifier = Modifier.weight(1f))
            Switch(checked = echoCancellation, onCheckedChange = { viewModel.saveEchoCancellation(it) }, colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f)))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Hardware Noise Suppression", color = foregroundText, modifier = Modifier.weight(1f))
            Switch(checked = noiseSuppression, onCheckedChange = { viewModel.saveNoiseSuppression(it) }, colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f)))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Auto Gain Control", color = foregroundText, modifier = Modifier.weight(1f))
            Switch(checked = autoGainControl, onCheckedChange = { viewModel.saveAutoGainControl(it) }, colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f)))
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    SettingsSection(title = "VIDEO SETTINGS", cardColor = cardColor, foregroundText = foregroundText) {
        ExposedDropdownMenuBox(
            expanded = expandedCamera,
            onExpandedChange = { expandedCamera = !expandedCamera },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = defaultCamera,
                onValueChange = {},
                readOnly = true,
                label = { Text("Default Camera") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCamera) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = foregroundText, unfocusedTextColor = primaryText
                )
            )
            ExposedDropdownMenu(expanded = expandedCamera, onDismissRequest = { expandedCamera = false }) {
                listOf("Front", "Back").forEach { cam ->
                    DropdownMenuItem(text = { Text(cam) }, onClick = { viewModel.saveDefaultCamera(cam); expandedCamera = false })
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        ExposedDropdownMenuBox(
            expanded = expandedVideoResolution,
            onExpandedChange = { expandedVideoResolution = !expandedVideoResolution },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = videoResolution,
                onValueChange = {},
                readOnly = true,
                label = { Text("Video Resolution") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVideoResolution) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = foregroundText, unfocusedTextColor = primaryText
                )
            )
            ExposedDropdownMenu(expanded = expandedVideoResolution, onDismissRequest = { expandedVideoResolution = false }) {
                availableResolutions.forEach { res ->
                    DropdownMenuItem(text = { Text(res) }, onClick = { viewModel.saveVideoResolution(res); expandedVideoResolution = false })
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        ExposedDropdownMenuBox(
            expanded = expandedVideoFps,
            onExpandedChange = { expandedVideoFps = !expandedVideoFps },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = "$videoFps fps",
                onValueChange = {},
                readOnly = true,
                label = { Text("Video Framerate") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVideoFps) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = foregroundText, unfocusedTextColor = primaryText
                )
            )
            ExposedDropdownMenu(expanded = expandedVideoFps, onDismissRequest = { expandedVideoFps = false }) {
                availableFpsList.forEach { fps ->
                    DropdownMenuItem(text = { Text("$fps fps") }, onClick = { viewModel.saveVideoFps(fps); expandedVideoFps = false })
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Mirror Front Camera", color = foregroundText, modifier = Modifier.weight(1f))
            Switch(checked = mirrorFrontCamera, onCheckedChange = { viewModel.saveMirrorFrontCamera(it) }, colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f)))
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    SettingsSection(title = "SCREEN SHARE SETTINGS", cardColor = cardColor, foregroundText = foregroundText) {
        ExposedDropdownMenuBox(
            expanded = expandedOptimizeFor,
            onExpandedChange = { expandedOptimizeFor = !expandedOptimizeFor },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = screenShareOptimizeFor,
                onValueChange = {},
                readOnly = true,
                label = { Text("Optimize For") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedOptimizeFor) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = foregroundText, unfocusedTextColor = primaryText
                )
            )
            ExposedDropdownMenu(expanded = expandedOptimizeFor, onDismissRequest = { expandedOptimizeFor = false }) {
                listOf("Performance", "Quality").forEach { opt ->
                    DropdownMenuItem(text = { Text(opt) }, onClick = { viewModel.saveScreenShareOptimizeFor(opt); expandedOptimizeFor = false })
                }
            }
        }
    }
}

@Composable
fun PasswordTabContent(viewModel: UserSettingsViewModel, cardColor: Color, foregroundText: Color, primaryText: Color, accentColor: Color) {
    val current by viewModel.currentPassword.collectAsState()
    val new by viewModel.newPassword.collectAsState()
    val confirm by viewModel.confirmNewPassword.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    SettingsSection(title = "CHANGE PASSWORD", cardColor = cardColor, foregroundText = foregroundText) {
        OutlinedTextField(
            value = current,
            onValueChange = { viewModel.currentPassword.value = it },
            label = { Text("Current Password", color = primaryText) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedTextColor = foregroundText, unfocusedTextColor = primaryText, focusedIndicatorColor = accentColor
            )
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = new,
            onValueChange = { viewModel.newPassword.value = it },
            label = { Text("New Password", color = primaryText) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedTextColor = foregroundText, unfocusedTextColor = primaryText, focusedIndicatorColor = accentColor
            )
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { viewModel.confirmNewPassword.value = it },
            label = { Text("Confirm New Password", color = primaryText) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedTextColor = foregroundText, unfocusedTextColor = primaryText, focusedIndicatorColor = accentColor
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { viewModel.updatePassword() },
            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
            modifier = Modifier.align(Alignment.End),
            enabled = !uiState.isSavingPassword && current.isNotEmpty() && new.isNotEmpty() && confirm.isNotEmpty()
        ) {
            if (uiState.isSavingPassword) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Update Password")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsTabContent(cardColor: Color, foregroundText: Color, primaryText: Color, accentColor: Color) {
    var allMessages by remember { mutableStateOf(false) }
    var mentionsOnly by remember { mutableStateOf(true) }
    var dmNotifications by remember { mutableStateOf(true) }
    var repliesNotifications by remember { mutableStateOf(true) }
    var syncFreq by remember { mutableStateOf("Real-time (Push)") }
    var expandedFreq by remember { mutableStateOf(false) }

    SettingsSection(title = "NOTIFICATION PREFERENCES", cardColor = cardColor, foregroundText = foregroundText) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("All Messages", color = foregroundText)
                Text("Get a notification for every new message", color = primaryText, fontSize = 12.sp)
            }
            Switch(checked = allMessages, onCheckedChange = { allMessages = it }, colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f)))
        }
        Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.1f))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Mentions Only", color = foregroundText)
                Text("Only get notified when you are @mentioned", color = primaryText, fontSize = 12.sp)
            }
            Switch(checked = mentionsOnly, onCheckedChange = { mentionsOnly = it }, colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f)))
        }
        Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.1f))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Direct Messages", color = foregroundText)
                Text("Get notified when someone sends you a DM", color = primaryText, fontSize = 12.sp)
            }
            Switch(checked = dmNotifications, onCheckedChange = { dmNotifications = it }, colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f)))
        }
        Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.1f))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Replies", color = foregroundText)
                Text("Get notified when someone replies to your message", color = primaryText, fontSize = 12.sp)
            }
            Switch(checked = repliesNotifications, onCheckedChange = { repliesNotifications = it }, colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f)))
        }
        
        Divider(modifier = Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.1f))
        
        Text("Sync Frequency", color = foregroundText, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        ExposedDropdownMenuBox(
            expanded = expandedFreq,
            onExpandedChange = { expandedFreq = !expandedFreq }
        ) {
            OutlinedTextField(
                value = syncFreq,
                onValueChange = {},
                readOnly = true,
                label = { Text("Pull Frequency") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFreq) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = foregroundText, unfocusedTextColor = primaryText
                )
            )
            ExposedDropdownMenu(expanded = expandedFreq, onDismissRequest = { expandedFreq = false }) {
                listOf("Real-time (Push)", "Every 15 minutes", "Hourly", "Manual").forEach { freq ->
                    DropdownMenuItem(text = { Text(freq) }, onClick = { syncFreq = freq; expandedFreq = false })
                }
            }
        }
    }
}

@Composable
fun AppSettingsTabContent(viewModel: UserSettingsViewModel, cardColor: Color, foregroundText: Color, primaryText: Color, accentColor: Color) {
    val maxDiskCacheMb by viewModel.maxDiskCacheMb.collectAsState()

    val displaySize = if (maxDiskCacheMb >= 1024) {
        String.format(java.util.Locale.US, "%.1f GB", maxDiskCacheMb / 1024f)
    } else {
        "${maxDiskCacheMb} MB"
    }

    SettingsSection(title = "STORAGE PREFERENCES", cardColor = cardColor, foregroundText = foregroundText) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Max Disk Cache Size", color = foregroundText)
                Text("Limit how much local storage is used for images.", color = primaryText, fontSize = 12.sp)
            }
            Text(displaySize, color = accentColor, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = maxDiskCacheMb.toFloat(),
            onValueChange = { viewModel.saveMaxDiskCacheMb(it.toInt()) },
            valueRange = 50f..10000f,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = accentColor.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun OthersTabContent(cardColor: Color, foregroundText: Color, primaryText: Color, accentColor: Color) {
    SettingsSection(title = "DANGER ZONE", cardColor = cardColor, foregroundText = foregroundText) {
        Text("Delete Account", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
        Text("This action is irreversible.", color = primaryText, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { /*TODO*/ },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
        ) {
            Text("Delete Account", color = Color.White)
        }
    }
}

@Composable
fun SettingsSection(title: String, cardColor: Color, foregroundText: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(cardColor)
                .padding(16.dp)
        ) {
            content()
        }
    }
}
