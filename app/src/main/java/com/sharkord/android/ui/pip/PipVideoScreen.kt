package com.sharkord.android.ui.pip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.setValue
import com.sharkord.android.ui.home.components.WebRtcVideoRenderer
import com.sharkord.android.ui.voice.VoiceViewModel

@Composable
fun PipVideoScreen(voiceViewModel: VoiceViewModel) {
    val uiState by voiceViewModel.uiState.collectAsState()
    val prominentTrack = uiState.prominentVideoTrack
    var showVideo by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(250) // wait for the main UI SurfaceViews to be fully destroyed
        showVideo = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (showVideo && prominentTrack != null) {
            WebRtcVideoRenderer(
                videoTrack = prominentTrack,
                eglBaseContext = uiState.eglBaseContext,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("Voice Call", color = Color.White)
        }
    }
}
