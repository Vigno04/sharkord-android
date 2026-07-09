package com.sharkord.android

import android.os.Bundle
import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.sharkord.android.ui.navigation.AppNavigation
import com.sharkord.android.ui.theme.SharkordTheme

class MainActivity : FragmentActivity() {


    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val isCallGoing = if (com.sharkord.android.data.network.SharkordClient.isVoiceEngineInitialized) {
            com.sharkord.android.data.network.SharkordClient.voiceEngine.isConnected.value
        } else false
        
        if (isCallGoing && com.sharkord.android.data.network.SharkordClient.session.enableFloatingPip) {
            val hasVideo = com.sharkord.android.data.network.SharkordClient.voiceEngine.videoEngine.remoteVideoTracks.value.isNotEmpty()
            if (hasVideo) {
                if (android.provider.Settings.canDrawOverlays(this)) {
                    val intent = Intent(this, com.sharkord.android.data.network.VoiceService::class.java).apply {
                        action = com.sharkord.android.data.network.VoiceService.ACTION_SHOW_OVERLAY
                    }
                    startService(intent)
                }
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.sharkord.android.data.network.SharkordClient.initialize(applicationContext)
        enableEdgeToEdge()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        setContent {
            val voiceViewModel: com.sharkord.android.ui.voice.VoiceViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val voiceUiState by voiceViewModel.uiState.collectAsState()
            val context = androidx.compose.ui.platform.LocalContext.current
            
            val overlayPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
            ) {
                if (android.provider.Settings.canDrawOverlays(context)) {
                    com.sharkord.android.data.network.SharkordClient.session.enableFloatingPip = true
                }
            }

            val isConnected = voiceUiState.activeVoiceChannelId != null

            androidx.compose.runtime.LaunchedEffect(isConnected) {
                if (isConnected && com.sharkord.android.data.network.SharkordClient.session.enableFloatingPip) {
                    if (!android.provider.Settings.canDrawOverlays(context)) {
                        com.sharkord.android.data.network.SharkordClient.session.enableFloatingPip = false
                        val intent = Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}")
                        )
                        overlayPermissionLauncher.launch(intent)
                    }
                }
            }
            
            SharkordTheme {
                val prefs = remember { context.getSharedPreferences("SharkordSettings", android.content.Context.MODE_PRIVATE) }
                var updateInfo by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.sharkord.android.utils.UpdateInfo?>(null) }
                var showUpdateDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    val info = com.sharkord.android.utils.UpdateManager.checkForUpdates(context)
                    if (info != null && info.hasUpdate) {
                        val neverRemindGlobal = prefs.getBoolean("never_remind_updates", false)
                        val skippedVersion = prefs.getString("skip_update_version", null)
                        if (!neverRemindGlobal && skippedVersion != info.latestVersion) {
                            updateInfo = info
                            showUpdateDialog = true
                        }
                    }
                }

                if (showUpdateDialog && updateInfo != null) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showUpdateDialog = false },
                        containerColor = SharkordTheme.colors.cardColor,
                        titleContentColor = SharkordTheme.colors.foregroundText,
                        textContentColor = SharkordTheme.colors.primaryText,
                        title = { androidx.compose.material3.Text("Update Available") },
                        text = { androidx.compose.material3.Text("Version ${updateInfo!!.latestVersion} is available. Do you want to download it?") },
                        confirmButton = {
                            androidx.compose.foundation.layout.Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                            ) {
                                androidx.compose.material3.Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        val url = updateInfo!!.releaseUrl
                                        val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                        context.startActivity(browserIntent)
                                        showUpdateDialog = false
                                    },
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = SharkordTheme.colors.accentColor)
                                ) {
                                    androidx.compose.material3.Text("Download")
                                }
                                androidx.compose.material3.OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { showUpdateDialog = false },
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SharkordTheme.colors.primaryText.copy(alpha = 0.5f))
                                ) {
                                    androidx.compose.material3.Text("Remind me later", color = SharkordTheme.colors.primaryText)
                                }
                                androidx.compose.material3.OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        prefs.edit().putString("skip_update_version", updateInfo!!.latestVersion).apply()
                                        showUpdateDialog = false
                                    },
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SharkordTheme.colors.primaryText.copy(alpha = 0.5f))
                                ) {
                                    androidx.compose.material3.Text("Skip this version", color = SharkordTheme.colors.primaryText)
                                }
                                androidx.compose.material3.OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        prefs.edit().putBoolean("never_remind_updates", true).apply()
                                        showUpdateDialog = false
                                    },
                                    border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFEF4444).copy(alpha = 0.5f))
                                ) {
                                    androidx.compose.material3.Text("Never remind me", color = androidx.compose.ui.graphics.Color(0xFFEF4444))
                                }
                            }
                        }
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = WindowInsets.systemBars
                    ) { innerPadding ->
                        AppNavigation(
                            modifier = Modifier
                                .padding(innerPadding)
                                .consumeWindowInsets(innerPadding),
                            voiceViewModel = voiceViewModel
                        )
                    }
                }
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { 
            setIntent(it)
            handleIntent(it) 
        }
    }

    private fun handleIntent(intent: Intent) {
        if (intent.hasExtra("target_channel_id")) {
            val channelId = intent.getIntExtra("target_channel_id", -1)
            if (channelId != -1) {
                com.sharkord.android.ui.navigation.MessageNavigationManager.jumpToChannel(channelId)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val isCallGoing = if (com.sharkord.android.data.network.SharkordClient.isVoiceEngineInitialized) {
            com.sharkord.android.data.network.SharkordClient.voiceEngine.isConnected.value
        } else false

        if (!isCallGoing) {
            com.sharkord.android.data.network.SharkordClient.webSocket.pauseConnection()
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, com.sharkord.android.data.network.VoiceService::class.java).apply {
            action = com.sharkord.android.data.network.VoiceService.ACTION_SET_APP_VISIBLE
            putExtra("EXTRA_VISIBLE", true)
        }
        try {
            startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStop() {
        super.onStop()
        val intent = Intent(this, com.sharkord.android.data.network.VoiceService::class.java).apply {
            action = com.sharkord.android.data.network.VoiceService.ACTION_SET_APP_VISIBLE
            putExtra("EXTRA_VISIBLE", false)
        }
        try {
            startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        // force immediate reconnect if we were disconnected (e.g., from network loss or screen off)
        com.sharkord.android.data.network.SharkordClient.webSocket.resumeConnection()
        
        val intent = Intent(this, com.sharkord.android.data.network.VoiceService::class.java).apply {
            action = com.sharkord.android.data.network.VoiceService.ACTION_SET_APP_VISIBLE
            putExtra("EXTRA_VISIBLE", true)
        }
        startService(intent)
    }
}