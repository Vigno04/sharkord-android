package com.sharkord.android.ui.voice

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.data.network.ServerEvent
import com.sharkord.android.data.network.ServerEventHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.VideoTrack

data class VoiceUiState(
    val activeVoiceChannelId: Int? = null,
    val isConnectingToVoice: Boolean = false,
    val activeSpeakers: Set<String> = emptySet(),
    val cameraEnabled: Boolean = false,
    val isScreenSharing: Boolean = false,
    val localVideoTrack: VideoTrack? = null,
    val localScreenTrack: VideoTrack? = null,
    val remoteVideoTracks: Map<String, VideoTrack> = emptyMap(),
    // always available since EglBase is created at VoiceEngine init time
    val eglBaseContext: org.webrtc.EglBase.Context = SharkordClient.voiceEngine.eglBaseContext
)

class VoiceViewModel : ViewModel() {

    companion object {
        private const val TAG = "VoiceViewModel"
    }

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    private var audioLevelsJob: Job? = null
    private var localVideoJob: Job? = null
    private var localScreenJob: Job? = null
    private var remoteVideoJob: Job? = null
    private var eventJob: Job? = null

    private var preDeafenMicMuted: Boolean = false

    init {
        viewModelScope.launch {
            SharkordClient.voiceEngine.isConnected.collect { connected ->
                if (!connected && _uiState.value.activeVoiceChannelId != null) {
                    // voiceEngine disconnected but UI is still active (e.g. from Notification)
                    _uiState.update { 
                        it.copy(
                            activeVoiceChannelId = null, 
                            activeSpeakers = emptySet(), 
                            cameraEnabled = false, 
                            isScreenSharing = false,
                            localVideoTrack = null, 
                            localScreenTrack = null,
                            remoteVideoTracks = emptyMap()
                        ) 
                    }
                    audioLevelsJob?.cancel()
                    localVideoJob?.cancel()
                    remoteVideoJob?.cancel()
                }
            }
        }

        // observe server events to handle voice side effects (WebRTC consumers)
        eventJob = viewModelScope.launch {
            SharkordClient.webSocket.incomingEvents.collect { event ->
                val parsed = ServerEventHandler.parse(event) ?: return@collect
                val activeChannelId = _uiState.value.activeVoiceChannelId

                when (parsed) {
                    is ServerEvent.UserLeftVoice -> {
                        if (parsed.channelId == activeChannelId) {
                            SharkordClient.voiceEngine.clearRemoteProducersForUser(parsed.userId)
                        }
                    }
                    is ServerEvent.VoiceProducerClosed -> {
                        if (parsed.channelId == activeChannelId) {
                            SharkordClient.voiceEngine.removeRemoteProducer(parsed.remoteId, parsed.kind)
                        }
                    }
                    is ServerEvent.VoiceNewProducer -> {
                        if (parsed.channelId == activeChannelId) {
                            SharkordClient.voiceEngine.consumeRemoteProducer(parsed.remoteId, parsed.kind)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun joinVoiceChannel(channelId: Int, context: Context, channelName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnectingToVoice = true) }
            try {
                // disconnect from current voice channel first if switching
                if (_uiState.value.activeVoiceChannelId != null && _uiState.value.activeVoiceChannelId != channelId) {
                    try {
                        if (SharkordClient.voiceEngine.isConnected.value) {
                            SharkordClient.voiceEngine.leaveChannel()
                            SharkordClient.webSocket.sendMutationAwait("voice.leave", JsonObject())
                        }
                        // stop the service before starting a new connection
                        val stopIntent = android.content.Intent(context, com.sharkord.android.data.network.VoiceService::class.java).apply {
                            action = com.sharkord.android.data.network.VoiceService.ACTION_STOP
                        }
                        context.startService(stopIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to leave current voice channel before switching", e)
                    }
                }

                val input = JsonObject().apply {
                    addProperty("channelId", channelId)
                    add("state", JsonObject().apply {
                        addProperty("micMuted", false)
                        addProperty("soundMuted", false)
                    })
                }
                val routerCapabilities = SharkordClient.webSocket.sendMutationAwait("voice.join", input)
                SharkordClient.voiceEngine.joinChannel(channelId, routerCapabilities)
                SharkordClient.voiceEngine.setMicEnabled(true)
                SharkordClient.voiceEngine.setSoundEnabled(true)
                
                viewModelScope.launch {
                    delay(800) // Wait for hardware audio routing to settle into MODE_IN_COMMUNICATION
                    com.sharkord.android.audio.SoundEngine.playSound(com.sharkord.android.audio.SoundType.OWN_USER_JOINED_VOICE_CHANNEL)
                    
                    _uiState.update { 
                        it.copy(
                            activeVoiceChannelId = channelId,
                            isConnectingToVoice = false,
                            eglBaseContext = SharkordClient.voiceEngine.eglBaseContext
                        ) 
                    }
                }
                
                audioLevelsJob?.cancel()
                audioLevelsJob = viewModelScope.launch {
                    SharkordClient.voiceEngine.audioLevels.collect { levels ->
                        val speakers = levels.filter { it.value > 0.02f || it.value > 5f }.keys
                        _uiState.update { it.copy(activeSpeakers = speakers) }
                    }
                }
                
                localVideoJob?.cancel()
                localVideoJob = viewModelScope.launch {
                    SharkordClient.voiceEngine.videoEngine.localVideoTrackFlow.collect { track ->
                        _uiState.update { it.copy(localVideoTrack = track) }
                    }
                }
                
                localScreenJob?.cancel()
                localScreenJob = viewModelScope.launch {
                    SharkordClient.voiceEngine.videoEngine.localScreenTrackFlow.collect { track ->
                        _uiState.update { it.copy(localScreenTrack = track) }
                    }
                }
                
                remoteVideoJob?.cancel()
                remoteVideoJob = viewModelScope.launch {
                    SharkordClient.voiceEngine.videoEngine.remoteVideoTracks.collect { tracks ->
                        _uiState.update { it.copy(remoteVideoTracks = tracks) }
                    }
                }
                
                // start Foreground Service
                val startIntent = android.content.Intent(context, com.sharkord.android.data.network.VoiceService::class.java).apply {
                    action = com.sharkord.android.data.network.VoiceService.ACTION_START
                    putExtra("EXTRA_CHANNEL_NAME", channelName)
                }
                androidx.core.content.ContextCompat.startForegroundService(context, startIntent)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to join voice channel", e)
                _uiState.update { it.copy(isConnectingToVoice = false) }
            }
        }
    }

    fun leaveVoiceChannel(context: Context) {
        viewModelScope.launch {
            try {
                val stopIntent = android.content.Intent(context, com.sharkord.android.data.network.VoiceService::class.java).apply {
                    action = com.sharkord.android.data.network.VoiceService.ACTION_STOP
                }
                context.startService(stopIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send stop intent to voice service", e)
            }
        }
    }

    fun toggleMic(channelId: Int, currentMuted: Boolean, currentDeafened: Boolean) {
        val newMuted = !currentMuted
        val newDeafened = if (!newMuted && currentDeafened) false else currentDeafened
        
        preDeafenMicMuted = newMuted
        updateVoiceState(channelId, newMuted, newDeafened)
    }

    fun toggleDeafen(channelId: Int, currentMuted: Boolean, currentDeafened: Boolean) {
        val newDeafened = !currentDeafened
        val newMuted: Boolean

        if (newDeafened) {
            preDeafenMicMuted = currentMuted
            newMuted = true
        } else {
            newMuted = preDeafenMicMuted
        }

        updateVoiceState(channelId, newMuted, newDeafened)
    }

    private fun updateVoiceState(channelId: Int, micMuted: Boolean, soundMuted: Boolean) {
        SharkordClient.voiceEngine.setMicEnabled(!micMuted)
        SharkordClient.voiceEngine.setSoundEnabled(!soundMuted)

        // note: Optimistic UI state updates for other components (like voice user lists)
        // are handled by the ServerEvent system when the WebSocket pushes the state update back.
        // We do play the sound effect immediately:
        com.sharkord.android.audio.SoundEngine.playSound(
            if (micMuted) com.sharkord.android.audio.SoundType.OWN_USER_MUTED_MIC else com.sharkord.android.audio.SoundType.OWN_USER_UNMUTED_MIC
        )
        
        viewModelScope.launch {
            try {
                val input = JsonObject().apply {
                    addProperty("micMuted", micMuted)
                    addProperty("soundMuted", soundMuted)
                }
                SharkordClient.webSocket.sendMutationAwait("voice.updateState", input)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update voice state", e)
            }
        }
    }

    fun switchCamera(context: Context) {
        SharkordClient.voiceEngine.switchCamera(context)
    }

    fun toggleCamera(context: Context) {
        val newState = !_uiState.value.cameraEnabled
        _uiState.update { it.copy(cameraEnabled = newState) }
        
        com.sharkord.android.audio.SoundEngine.playSound(
            if (newState) com.sharkord.android.audio.SoundType.OWN_USER_STARTED_WEBCAM else com.sharkord.android.audio.SoundType.OWN_USER_STOPPED_WEBCAM
        )
        
        SharkordClient.voiceEngine.setCameraEnabled(context, newState)
        
        _uiState.value.activeVoiceChannelId?.let { channelId ->
            viewModelScope.launch {
                try {
                    if (!newState) {
                        val closeInput = JsonObject().apply {
                            addProperty("kind", "video")
                        }
                        SharkordClient.webSocket.sendMutationAwait("voice.closeProducer", closeInput)
                    }
                    val updateInput = JsonObject().apply {
                        addProperty("webcamEnabled", newState)
                    }
                    SharkordClient.webSocket.sendMutationAwait("voice.updateState", updateInput)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update camera state", e)
                }
            }
        }
    }

    fun toggleScreenShare(context: Context, enabled: Boolean, data: android.content.Intent?) {
        _uiState.update { it.copy(isScreenSharing = enabled) }
        
        com.sharkord.android.audio.SoundEngine.playSound(
            if (enabled) com.sharkord.android.audio.SoundType.OWN_USER_STARTED_WEBCAM else com.sharkord.android.audio.SoundType.OWN_USER_STOPPED_WEBCAM
        )

        val serviceIntent = android.content.Intent(context, com.sharkord.android.data.network.VoiceService::class.java).apply {
            action = if (enabled) com.sharkord.android.data.network.VoiceService.ACTION_START_SCREEN_SHARE else com.sharkord.android.data.network.VoiceService.ACTION_STOP_SCREEN_SHARE
            if (data != null) {
                putExtra("EXTRA_MEDIA_PROJECTION_INTENT", data)
            }
        }
        androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
        
        if (enabled && data != null) {
            _uiState.value.activeVoiceChannelId?.let { channelId ->
                viewModelScope.launch {
                    try {
                        val updateInput = JsonObject().apply {
                            addProperty("sharingScreen", true)
                        }
                        SharkordClient.webSocket.sendMutationAwait("voice.updateState", updateInput)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update screen share state", e)
                    }
                }
            }
        } else {
            _uiState.value.activeVoiceChannelId?.let { channelId ->
                viewModelScope.launch {
                    try {
                        val closeInput = JsonObject().apply {
                            addProperty("kind", "screen")
                        }
                        SharkordClient.webSocket.sendMutationAwait("voice.closeProducer", closeInput)
                        val updateInput = JsonObject().apply {
                            addProperty("sharingScreen", false)
                        }
                        SharkordClient.webSocket.sendMutationAwait("voice.updateState", updateInput)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to stop screen share", e)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioLevelsJob?.cancel()
        localVideoJob?.cancel()
        localScreenJob?.cancel()
        remoteVideoJob?.cancel()
        eventJob?.cancel()
    }
}
