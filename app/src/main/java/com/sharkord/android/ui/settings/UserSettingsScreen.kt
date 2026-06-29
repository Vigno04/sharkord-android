package com.sharkord.android.ui.settings

import com.sharkord.android.ui.theme.SharkordTheme
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.sharkord.android.R
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

    val bgColor = SharkordTheme.colors.bgColor
    val cardColor = SharkordTheme.colors.cardColor
    val primaryText = SharkordTheme.colors.primaryText
    val foregroundText = SharkordTheme.colors.foregroundText
    val accentColor = SharkordTheme.colors.accentColor
    
    val tabs = listOf("Profile", "Call Settings", "Password", "Notifications", "App Settings")
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
                title = { Text(stringResource(R.string.profile_user_settings_title), color = foregroundText, fontWeight = FontWeight.Bold) },
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
            val isInVoiceChannel by remember {
                if (com.sharkord.android.data.network.SharkordClient.isVoiceEngineInitialized) {
                    com.sharkord.android.data.network.SharkordClient.voiceEngine.isConnected
                } else {
                    kotlinx.coroutines.flow.MutableStateFlow(false)
                }
            }.collectAsState()

            if (isInVoiceChannel && pagerState.currentPage == 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE53935))
                        .padding(12.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        text = "Changing settings will take effect when re entering a new chanel",
                        color = SharkordTheme.colors.foregroundText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
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
                divider = { Divider(color = SharkordTheme.colors.foregroundText.copy(alpha = 0.1f)) }
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
                    Text(stringResource(R.string.settings_takePhoto), color = primaryText)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { galleryLauncher.launch("image/*") }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = primaryText)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.settings_chooseFromGallery), color = primaryText)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    SettingsSection(title = stringResource(com.sharkord.android.R.string.settings_userProfileGroup), cardColor = cardColor, foregroundText = foregroundText) {
        
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(SharkordTheme.colors.cardColor)
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
                    Text(stringResource(R.string.settings_noAvatar), color = SharkordTheme.colors.primaryText.copy(alpha = 0.6f), fontSize = 10.sp)
                }
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Change Avatar", tint = SharkordTheme.colors.foregroundText)
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
                        catch (e: Exception) { SharkordTheme.colors.cardColor }
                    )
                    .border(1.dp, SharkordTheme.colors.dividerColor, RoundedCornerShape(8.dp))
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
                    Icon(Icons.Default.Image, contentDescription = "Change Banner", tint = SharkordTheme.colors.foregroundText)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = name,
            onValueChange = { viewModel.name.value = it },
            label = { Text(stringResource(R.string.settings_displayNameLabel), color = primaryText) },
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
            label = { Text(stringResource(R.string.settings_bioLabel), color = primaryText) },
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
        Text(stringResource(R.string.settings_bannerColorLabel), color = foregroundText, fontSize = 14.sp)
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
            label = { Text(stringResource(R.string.settings_bannerColorLabel) + " (Hex)", color = primaryText) },
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
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = SharkordTheme.colors.foregroundText)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(stringResource(R.string.common_saveChanges))
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    SettingsSection(title = stringResource(com.sharkord.android.R.string.settings_dangerZoneGroup), cardColor = cardColor, foregroundText = foregroundText) {
        Text(stringResource(R.string.settings_deleteAccount), color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.settings_deleteAccountConfirm), color = primaryText, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { /*TODO*/ },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
        ) {
            Text(stringResource(R.string.settings_deleteAccount), color = SharkordTheme.colors.foregroundText)
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
    val frontVideoResolution by viewModel.frontVideoResolution.collectAsState()
    val frontVideoFps by viewModel.frontVideoFps.collectAsState()
    val backVideoResolution by viewModel.backVideoResolution.collectAsState()
    val backVideoFps by viewModel.backVideoFps.collectAsState()
    val mirrorFrontCamera by viewModel.mirrorFrontCamera.collectAsState()
    
    val videoCodec by viewModel.videoCodec.collectAsState()
    val availableVideoCodecs by viewModel.availableVideoCodecs.collectAsState()

    val screenShareResolution by viewModel.screenShareResolution.collectAsState()
    val screenShareFps by viewModel.screenShareFps.collectAsState()

    var expandedAudioRoute by remember { mutableStateOf(false) }
    var expandedCamera by remember { mutableStateOf(false) }
    var expandedFrontVideoResolution by remember { mutableStateOf(false) }
    var expandedBackVideoResolution by remember { mutableStateOf(false) }
    var expandedScreenShareResolution by remember { mutableStateOf(false) }
    var expandedScreenShareFps by remember { mutableStateOf(false) }
    var expandedVideoCodec by remember { mutableStateOf(false) }
    var expandedScreenShareCodec by remember { mutableStateOf(false) }

    val context = LocalContext.current
    
    // Screen share options derived from device capabilities
    val availableScreenShareResolutions = remember {
        val displayMetrics = context.resources.displayMetrics
        val w = displayMetrics.widthPixels
        val h = displayMetrics.heightPixels
        val nativeRes = "${maxOf(w, h)}x${minOf(w, h)}"
        val resList = mutableListOf(
            "${w}x${h} (Native)",
            "${w / 2}x${h / 2} (1/2 Native)",
            "${w / 3}x${h / 3} (1/3 Native)",
            "${w / 4}x${h / 4} (1/4 Native)"
        )
        // clean up duplicates if native matches one of the standards
        resList.distinctBy { it.split(" ")[0] }
    }
    
    val availableScreenShareFpsList = remember {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        val maxRefreshRate = windowManager.defaultDisplay.refreshRate.toInt()
        val fpsList = mutableListOf(maxRefreshRate, 120, 60, 30, 15)
        fpsList.filter { it <= maxRefreshRate }.distinct().sortedDescending()
    }
    var availableFrontFormats by remember { mutableStateOf<List<String>>(emptyList()) }
    var availableBackFormats by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        val enumerator: CameraEnumerator = if (Camera2Enumerator.isSupported(context)) {
            Camera2Enumerator(context)
        } else {
            Camera1Enumerator(true)
        }
        for (name in enumerator.deviceNames) {
            val formats = enumerator.getSupportedFormats(name)
            val formatsByFps = formats.groupBy { it.framerate.max / 1000 }
            val filteredFormats = mutableListOf<String>()
            
            for ((fps, fpsFormats) in formatsByFps) {
                val sortedFormats = fpsFormats.sortedByDescending { it.width * it.height }
                var lastArea = -1
                for (format in sortedFormats) {
                    val area = format.width * format.height
                    if (lastArea == -1 || area <= lastArea * 0.85) {
                        filteredFormats.add("${format.width}x${format.height} @ ${fps}fps")
                        lastArea = area
                    }
                }
            }
            
            val combinedList = filteredFormats
                .sortedWith(compareByDescending<String> {
                    val parts = it.split(" @ ")
                    val res = parts[0].split("x")
                    res[0].toInt() * res[1].toInt()
                }.thenByDescending {
                    val parts = it.split(" @ ")
                    parts[1].replace("fps", "").trim().toInt()
                })
                
            if (enumerator.isFrontFacing(name) && availableFrontFormats.isEmpty()) {
                availableFrontFormats = combinedList
            } else if (!enumerator.isFrontFacing(name) && availableBackFormats.isEmpty()) {
                availableBackFormats = combinedList
            }
        }
        
        if (availableFrontFormats.isEmpty()) {
            availableFrontFormats = listOf("1280x720 @ 30fps")
        }
        if (availableBackFormats.isEmpty()) {
            availableBackFormats = listOf("1280x720 @ 30fps")
        }
    }

    SettingsSection(title = stringResource(com.sharkord.android.R.string.settings_audioSettingsGroup), cardColor = cardColor, foregroundText = foregroundText) {
        ExposedDropdownMenuBox(
            expanded = expandedAudioRoute,
            onExpandedChange = { expandedAudioRoute = !expandedAudioRoute },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = defaultAudioRoute,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_defaultAudioRoute)) },
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
            Text(stringResource(R.string.settings_echoCancellationLabel), color = foregroundText, modifier = Modifier.weight(1f))
            Switch(checked = echoCancellation, onCheckedChange = { viewModel.saveEchoCancellation(it) }, colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f)))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_noiseSuppressionLabel), color = foregroundText, modifier = Modifier.weight(1f))
            Switch(checked = noiseSuppression, onCheckedChange = { viewModel.saveNoiseSuppression(it) }, colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f)))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_autoGainControlLabel), color = foregroundText, modifier = Modifier.weight(1f))
            Switch(checked = autoGainControl, onCheckedChange = { viewModel.saveAutoGainControl(it) }, colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f)))
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    SettingsSection(title = stringResource(com.sharkord.android.R.string.settings_videoSettingsGroup), cardColor = cardColor, foregroundText = foregroundText) {
        ExposedDropdownMenuBox(
            expanded = expandedCamera,
            onExpandedChange = { expandedCamera = !expandedCamera },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = defaultCamera,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_defaultCamera)) },
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
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.settings_frontCameraSettings), color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        
        ExposedDropdownMenuBox(
            expanded = expandedFrontVideoResolution,
            onExpandedChange = { expandedFrontVideoResolution = !expandedFrontVideoResolution },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = "$frontVideoResolution @ ${frontVideoFps}fps",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_cameraFormatLabel)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFrontVideoResolution) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = foregroundText, unfocusedTextColor = primaryText
                )
            )
            ExposedDropdownMenu(expanded = expandedFrontVideoResolution, onDismissRequest = { expandedFrontVideoResolution = false }) {
                availableFrontFormats.forEach { formatStr ->
                    DropdownMenuItem(text = { Text(formatStr) }, onClick = { 
                        val parts = formatStr.split(" @ ")
                        viewModel.saveFrontVideoResolution(parts[0])
                        viewModel.saveFrontVideoFps(parts[1].replace("fps", "").trim().toInt())
                        expandedFrontVideoResolution = false 
                    })
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.settings_backCameraSettings), color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        
        ExposedDropdownMenuBox(
            expanded = expandedBackVideoResolution,
            onExpandedChange = { expandedBackVideoResolution = !expandedBackVideoResolution },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = "$backVideoResolution @ ${backVideoFps}fps",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_cameraFormatLabel)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedBackVideoResolution) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = foregroundText, unfocusedTextColor = primaryText
                )
            )
            ExposedDropdownMenu(expanded = expandedBackVideoResolution, onDismissRequest = { expandedBackVideoResolution = false }) {
                availableBackFormats.forEach { formatStr ->
                    DropdownMenuItem(text = { Text(formatStr) }, onClick = { 
                        val parts = formatStr.split(" @ ")
                        viewModel.saveBackVideoResolution(parts[0])
                        viewModel.saveBackVideoFps(parts[1].replace("fps", "").trim().toInt())
                        expandedBackVideoResolution = false 
                    })
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_mirrorOwnVideoLabel), color = foregroundText, modifier = Modifier.weight(1f))
            Switch(checked = mirrorFrontCamera, onCheckedChange = { viewModel.saveMirrorFrontCamera(it) }, colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f)))
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        ExposedDropdownMenuBox(
            expanded = expandedVideoCodec,
            onExpandedChange = { expandedVideoCodec = !expandedVideoCodec },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = videoCodec,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_videoCodecLabel)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVideoCodec) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = foregroundText, unfocusedTextColor = primaryText
                )
            )
            ExposedDropdownMenu(expanded = expandedVideoCodec, onDismissRequest = { expandedVideoCodec = false }) {
                availableVideoCodecs.forEach { codec ->
                    DropdownMenuItem(text = { Text(codec) }, onClick = { viewModel.saveVideoCodec(codec); expandedVideoCodec = false })
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    SettingsSection(title = stringResource(com.sharkord.android.R.string.settings_screenShareSettingsGroup), cardColor = cardColor, foregroundText = foregroundText) {
        ExposedDropdownMenuBox(
            expanded = expandedScreenShareResolution,
            onExpandedChange = { expandedScreenShareResolution = !expandedScreenShareResolution },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = screenShareResolution,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_videoResolutionLabel)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedScreenShareResolution) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = foregroundText, unfocusedTextColor = primaryText
                )
            )
            ExposedDropdownMenu(expanded = expandedScreenShareResolution, onDismissRequest = { expandedScreenShareResolution = false }) {
                availableScreenShareResolutions.forEach { resOption ->
                    val actualValue = resOption.split(" ")[0]
                    DropdownMenuItem(text = { Text(resOption) }, onClick = { viewModel.saveScreenShareResolution(actualValue); expandedScreenShareResolution = false })
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        ExposedDropdownMenuBox(
            expanded = expandedScreenShareFps,
            onExpandedChange = { expandedScreenShareFps = !expandedScreenShareFps },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = "$screenShareFps fps",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_videoFramerateLabel)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedScreenShareFps) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = foregroundText, unfocusedTextColor = primaryText
                )
            )
            ExposedDropdownMenu(expanded = expandedScreenShareFps, onDismissRequest = { expandedScreenShareFps = false }) {
                availableScreenShareFpsList.forEach { fps ->
                    DropdownMenuItem(text = { Text("$fps fps") }, onClick = { viewModel.saveScreenShareFps(fps); expandedScreenShareFps = false })
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        ExposedDropdownMenuBox(
            expanded = expandedScreenShareCodec,
            onExpandedChange = { expandedScreenShareCodec = !expandedScreenShareCodec },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = videoCodec,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.settings_screenShareCodecLabel)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedScreenShareCodec) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = foregroundText, unfocusedTextColor = primaryText
                )
            )
            ExposedDropdownMenu(expanded = expandedScreenShareCodec, onDismissRequest = { expandedScreenShareCodec = false }) {
                availableVideoCodecs.forEach { codec ->
                    DropdownMenuItem(text = { Text(codec) }, onClick = { viewModel.saveVideoCodec(codec); expandedScreenShareCodec = false })
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
    
    SettingsSection(title = stringResource(com.sharkord.android.R.string.settings_passwordTitle), cardColor = cardColor, foregroundText = foregroundText) {
        OutlinedTextField(
            value = current,
            onValueChange = { viewModel.currentPassword.value = it },
            label = { Text(stringResource(R.string.settings_currentPasswordLabel), color = primaryText) },
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
            label = { Text(stringResource(R.string.settings_newPasswordLabel), color = primaryText) },
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
            label = { Text(stringResource(R.string.settings_confirmNewPasswordLabel), color = primaryText) },
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
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = SharkordTheme.colors.foregroundText)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(stringResource(R.string.settings_updatePasswordBtn))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsTabContent(cardColor: Color, foregroundText: Color, primaryText: Color, accentColor: Color) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("SharkordSettings", android.content.Context.MODE_PRIVATE) }
    
    var allMessages by remember { mutableStateOf(prefs.getBoolean("notif_all_messages", false)) }
    var mentionsOnly by remember { mutableStateOf(prefs.getBoolean("notif_mentions_only", false)) }
    var dmNotifications by remember { mutableStateOf(prefs.getBoolean("notif_dms", false)) }
    var repliesNotifications by remember { mutableStateOf(prefs.getBoolean("notif_replies", false)) }
    var syncFreq by remember { mutableStateOf(prefs.getString("notif_sync_freq", "Off") ?: "Off") }
    var expandedFreq by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) {}

    fun checkAndRequestPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Helper to schedule work
    val updateWorkManager = { freq: String ->
        if (freq != "Off") checkAndRequestPermission()
        val workManager = androidx.work.WorkManager.getInstance(context)
        when (freq) {
            "15 minutes" -> {
                val req = androidx.work.PeriodicWorkRequestBuilder<com.sharkord.android.data.network.MessageSyncWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
                    .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                    .build()
                workManager.enqueueUniquePeriodicWork("MessageSync", androidx.work.ExistingPeriodicWorkPolicy.UPDATE, req)
            }
            "30 minutes" -> {
                val req = androidx.work.PeriodicWorkRequestBuilder<com.sharkord.android.data.network.MessageSyncWorker>(30, java.util.concurrent.TimeUnit.MINUTES)
                    .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                    .build()
                workManager.enqueueUniquePeriodicWork("MessageSync", androidx.work.ExistingPeriodicWorkPolicy.UPDATE, req)
            }
            "1 Hour" -> {
                val req = androidx.work.PeriodicWorkRequestBuilder<com.sharkord.android.data.network.MessageSyncWorker>(1, java.util.concurrent.TimeUnit.HOURS)
                    .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                    .build()
                workManager.enqueueUniquePeriodicWork("MessageSync", androidx.work.ExistingPeriodicWorkPolicy.UPDATE, req)
            }
            else -> {
                workManager.cancelUniqueWork("MessageSync")
            }
        }
    }

    SettingsSection(title = stringResource(com.sharkord.android.R.string.settings_notificationsTitle), cardColor = cardColor, foregroundText = foregroundText) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_allMessagesLabel), color = foregroundText)
                Text(stringResource(R.string.settings_allMessagesDesc), color = primaryText, fontSize = 12.sp)
            }
            Switch(
                checked = allMessages, 
                onCheckedChange = { 
                    allMessages = it
                    prefs.edit().putBoolean("notif_all_messages", it).apply()
                    if (it) checkAndRequestPermission()
                }, 
                colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f))
            )
        }
        Divider(modifier = Modifier.padding(vertical = 12.dp), color = SharkordTheme.colors.foregroundText.copy(alpha = 0.1f))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_mentionsOnlyLabel), color = foregroundText)
                Text(stringResource(R.string.settings_mentionsOnlyDesc), color = primaryText, fontSize = 12.sp)
            }
            Switch(
                checked = mentionsOnly, 
                onCheckedChange = { 
                    mentionsOnly = it
                    prefs.edit().putBoolean("notif_mentions_only", it).apply()
                    if (it) checkAndRequestPermission()
                }, 
                colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f))
            )
        }
        Divider(modifier = Modifier.padding(vertical = 12.dp), color = SharkordTheme.colors.foregroundText.copy(alpha = 0.1f))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_dmNotificationsLabel), color = foregroundText)
                Text(stringResource(R.string.settings_dmNotificationsDesc), color = primaryText, fontSize = 12.sp)
            }
            Switch(
                checked = dmNotifications, 
                onCheckedChange = { 
                    dmNotifications = it
                    prefs.edit().putBoolean("notif_dms", it).apply()
                    if (it) checkAndRequestPermission()
                }, 
                colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f))
            )
        }
        Divider(modifier = Modifier.padding(vertical = 12.dp), color = SharkordTheme.colors.foregroundText.copy(alpha = 0.1f))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_repliesNotificationsLabel), color = foregroundText)
                Text(stringResource(R.string.settings_repliesNotificationsDesc), color = primaryText, fontSize = 12.sp)
            }
            Switch(
                checked = repliesNotifications, 
                onCheckedChange = { 
                    repliesNotifications = it
                    prefs.edit().putBoolean("notif_replies", it).apply()
                    if (it) checkAndRequestPermission()
                }, 
                colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f))
            )
        }
        
        Divider(modifier = Modifier.padding(vertical = 16.dp), color = SharkordTheme.colors.foregroundText.copy(alpha = 0.1f))
        
        Text("BACKGROUND SYNC (PULL)", color = foregroundText, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Because there is no Push Notification payload waking up the app, the background worker has to temporarily connect to the server to check for unread messages. This will use a small amount of background data.",
            color = primaryText, fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        ExposedDropdownMenuBox(
            expanded = expandedFreq,
            onExpandedChange = { expandedFreq = !expandedFreq }
        ) {
            OutlinedTextField(
                value = syncFreq,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(com.sharkord.android.R.string.settings_pullFrequency)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFreq) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = foregroundText, unfocusedTextColor = primaryText
                )
            )
            ExposedDropdownMenu(expanded = expandedFreq, onDismissRequest = { expandedFreq = false }) {
                listOf("Off", "15 minutes", "30 minutes", "1 Hour").forEach { freq ->
                    DropdownMenuItem(text = { Text(freq) }, onClick = { 
                        syncFreq = freq
                        expandedFreq = false
                        prefs.edit().putString("notif_sync_freq", freq).apply()
                        updateWorkManager(freq)
                    })
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Battery optimization warning
        if (syncFreq != "Off") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardColor, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = "Warning", tint = accentColor, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Battery Optimization", color = foregroundText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Android may restrict background syncing if this app is battery optimized. For strictly reliable notifications, please disable battery optimization for Sharkord.",
                        color = primaryText, fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Text("Open Settings", color = Color.White)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsTabContent(viewModel: UserSettingsViewModel, cardColor: Color, foregroundText: Color, primaryText: Color, accentColor: Color) {
    val maxDiskCacheMb by viewModel.maxDiskCacheMb.collectAsState()
    val autoLogin by viewModel.autoLogin.collectAsState()
    val alwaysRequireBiometrics by viewModel.alwaysRequireBiometrics.collectAsState()
    val hasBiometrics by viewModel.hasBiometrics.collectAsState()

    val displaySize = if (maxDiskCacheMb >= 1024) {
        String.format(java.util.Locale.US, "%.1f GB", maxDiskCacheMb / 1024f)
    } else {
        "${maxDiskCacheMb} MB"
    }

    SettingsSection(title = stringResource(com.sharkord.android.R.string.settings_securityAccessGroup), cardColor = cardColor, foregroundText = foregroundText) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.connect_autoLoginLabel), color = foregroundText)
                Text("Remember credentials and bypass login screen.", color = primaryText, fontSize = 12.sp)
            }
            Switch(checked = autoLogin, onCheckedChange = { viewModel.saveAutoLogin(it) }, colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f)))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_requireBiometrics), color = foregroundText)
                Text(stringResource(R.string.settings_requireBiometricsDesc), color = primaryText, fontSize = 12.sp)
            }
            Switch(checked = alwaysRequireBiometrics, onCheckedChange = { viewModel.saveAlwaysRequireBiometrics(it) }, enabled = hasBiometrics, colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f)))
        }
        
        if (hasBiometrics) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.removeBiometrics() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_removeBiometrics), color = SharkordTheme.colors.foregroundText)
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    SettingsSection(title = stringResource(com.sharkord.android.R.string.settings_storagePreferencesGroup), cardColor = cardColor, foregroundText = foregroundText) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_maxDiskCacheSize), color = foregroundText)
                Text(stringResource(R.string.settings_maxDiskCacheSizeDesc), color = primaryText, fontSize = 12.sp)
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

    Spacer(modifier = Modifier.height(16.dp))

    val compressMedia by viewModel.compressMedia.collectAsState()
    val mediaCodec by viewModel.mediaCodec.collectAsState()
    val mediaQuality by viewModel.mediaQuality.collectAsState()
    var expandedMediaCodec by remember { mutableStateOf(false) }
    var expandedMediaQuality by remember { mutableStateOf(false) }

    SettingsSection(title = stringResource(com.sharkord.android.R.string.settings_mediaCompressionGroup), cardColor = cardColor, foregroundText = foregroundText) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_compressMediaLabel), color = foregroundText)
                Text(stringResource(R.string.settings_compressMediaDesc), color = primaryText, fontSize = 12.sp)
            }
            Switch(checked = compressMedia, onCheckedChange = { viewModel.saveCompressMedia(it) }, colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f)))
        }

        if (compressMedia) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // codec dropdown
            ExposedDropdownMenuBox(
                expanded = expandedMediaCodec,
                onExpandedChange = { expandedMediaCodec = !expandedMediaCodec }
            ) {
                OutlinedTextField(
                    value = mediaCodec,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.settings_mediaCodecLabel)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMediaCodec) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = foregroundText, unfocusedTextColor = primaryText
                    )
                )
                ExposedDropdownMenu(expanded = expandedMediaCodec, onDismissRequest = { expandedMediaCodec = false }) {
                    val supportedCodecs = remember { com.sharkord.android.utils.VideoCodecUtils.getSupportedHardwareEncoders() }
                    supportedCodecs.forEach { codec ->
                        DropdownMenuItem(text = { Text(codec) }, onClick = { viewModel.saveMediaCodec(codec); expandedMediaCodec = false })
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // quality dropdown
            ExposedDropdownMenuBox(
                expanded = expandedMediaQuality,
                onExpandedChange = { expandedMediaQuality = !expandedMediaQuality }
            ) {
                OutlinedTextField(
                    value = mediaQuality,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.settings_mediaQualityLabel)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMediaQuality) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = foregroundText, unfocusedTextColor = primaryText
                    )
                )
                ExposedDropdownMenu(expanded = expandedMediaQuality, onDismissRequest = { expandedMediaQuality = false }) {
                    listOf("High", "Medium", "Low").forEach { quality ->
                        DropdownMenuItem(text = { Text(quality) }, onClick = { viewModel.saveMediaQuality(quality); expandedMediaQuality = false })
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, cardColor: Color, foregroundText: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = SharkordTheme.colors.primaryText.copy(alpha = 0.6f),
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
