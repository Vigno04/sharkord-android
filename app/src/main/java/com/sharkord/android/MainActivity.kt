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
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.sharkord.android.ui.navigation.AppNavigation
import com.sharkord.android.ui.theme.SharkordTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        setContent {
            SharkordTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.systemBars
                ) { innerPadding ->
                    AppNavigation(
                        modifier = Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                    )
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

    override fun onResume() {
        super.onResume()
        // force immediate reconnect if we were disconnected (e.g., from network loss or screen off)
        com.sharkord.android.data.network.SharkordClient.webSocket.resumeConnection()
    }
}