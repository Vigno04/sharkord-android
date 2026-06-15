package com.sharkord.android.data.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sharkord.android.data.model.StreamKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import com.google.gson.JsonArray
import org.mediasoup.droid.Device
import org.mediasoup.droid.MediasoupClient
import org.mediasoup.droid.Producer
import org.mediasoup.droid.SendTransport
import org.mediasoup.droid.RecvTransport
import org.mediasoup.droid.Consumer
import org.mediasoup.droid.Transport
import org.webrtc.AudioTrack
import org.webrtc.PeerConnectionFactory
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.AudioSource
import org.webrtc.EglBase
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.audio.AudioDeviceModule

enum class VoiceConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}

class VoiceEngine(private val context: Context, private val webSocketManager: WebSocketManager) {
    companion object {
        private const val TAG = "VoiceEngine"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    private val _connectionState = MutableStateFlow(VoiceConnectionState.DISCONNECTED)
    val connectionState: StateFlow<VoiceConnectionState> = _connectionState.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _audioLevels = MutableStateFlow<Map<String, Float>>(emptyMap())
    val audioLevels: StateFlow<Map<String, Float>> = _audioLevels.asStateFlow()
    
    private var statsJob: Job? = null

    private var device: Device? = null
    private var sendTransport: SendTransport? = null
    private var recvTransport: RecvTransport? = null
    private var micProducer: Producer? = null
    // Map key is "$remoteId:${kind.value}"
    private val consumers = mutableMapOf<String, Consumer>()

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var localAudioTrack: AudioTrack? = null
    private var localAudioSource: AudioSource? = null
    private var eglBase: EglBase? = null

    private var currentChannelId: Int? = null
    
    // Voice State Flags
    private var isMicMuted = false
    private var isSoundMuted = false

    // Subscription IDs
    private var onNewProducerSubId: Int? = null
    private var onProducerClosedSubId: Int? = null
    private var eventCollectionJob: Job? = null

    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { }

    init {
        MediasoupClient.initialize(context)
        eglBase = EglBase.create()
        initializePeerConnectionFactory()
    }

    private fun initializePeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setAudioAttributes(audioAttributes)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        val builder = PeerConnectionFactory.builder()
        builder.setOptions(PeerConnectionFactory.Options())
        builder.setAudioDeviceModule(audioDeviceModule)
        
        peerConnectionFactory = builder.createPeerConnectionFactory()
    }

    fun joinChannel(channelId: Int, routerRtpCapabilities: JsonObject) {
        if (_connectionState.value == VoiceConnectionState.CONNECTING || _connectionState.value == VoiceConnectionState.CONNECTED) {
            leaveChannel()
        }
        
        currentChannelId = channelId
        _connectionState.value = VoiceConnectionState.CONNECTING

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN)
        }
        
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = true

        scope.launch {
            try {
                // 1. Initialize Device
                device = Device()
                
                val pcOptions = org.mediasoup.droid.PeerConnection.Options()
                pcOptions.setFactory(peerConnectionFactory)
                
                // Extract the actual RTP capabilities if it's wrapped in an outer object
                var rtpCapsStr = routerRtpCapabilities.toString()
                if (routerRtpCapabilities.has("rtpCapabilities")) {
                    rtpCapsStr = routerRtpCapabilities.getAsJsonObject("rtpCapabilities").toString()
                } else if (routerRtpCapabilities.has("routerRtpCapabilities")) {
                    rtpCapsStr = routerRtpCapabilities.getAsJsonObject("routerRtpCapabilities").toString()
                } else if (routerRtpCapabilities.has("routerCapabilities")) {
                    rtpCapsStr = routerRtpCapabilities.getAsJsonObject("routerCapabilities").toString()
                }
                
                Log.d(TAG, "Device loading with rtpCaps: $rtpCapsStr")
                device?.load(rtpCapsStr, pcOptions)
                Log.d(TAG, "Device loaded successfully")

                // 2. Create Transports
                setupSendTransport(pcOptions)
                setupRecvTransport(pcOptions)

                // 3. Start local audio
                startLocalAudio()

                // 4. Fetch existing producers
                fetchAndConsumeExistingProducers()

                // 5. Subscribe to events
                onNewProducerSubId = webSocketManager.subscribe("voice.onNewProducer")
                onProducerClosedSubId = webSocketManager.subscribe("voice.onProducerClosed")
                
                eventCollectionJob = scope.launch {
                    webSocketManager.incomingEvents.collect { event ->
                        when (event.path) {
                            "voice.onNewProducer" -> {
                                val data = event.data
                                val remoteId = data.get("producerId")?.asInt ?: return@collect
                                val kindStr = data.get("kind")?.asString ?: return@collect
                                if (kindStr == "audio") {
                                    consumeRemoteProducer(remoteId, StreamKind.AUDIO)
                                }
                            }
                            "voice.onProducerClosed" -> {
                                val data = event.data
                                val remoteId = data.get("producerId")?.asInt ?: return@collect
                                val kindStr = data.get("kind")?.asString ?: return@collect
                                if (kindStr == "audio") {
                                    removeRemoteProducer(remoteId, StreamKind.AUDIO)
                                }
                            }
                        }
                    }
                }

                // 6. Start audio polling
                startAudioLevelPolling()

                _connectionState.value = VoiceConnectionState.CONNECTED
                _isConnected.value = true
                Log.d(TAG, "Successfully joined voice channel $channelId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to join voice channel", e)
                _connectionState.value = VoiceConnectionState.FAILED
                leaveChannel()
            }
        }
    }

    private suspend fun setupSendTransport(pcOptions: org.mediasoup.droid.PeerConnection.Options) {
        try {
            val response = webSocketManager.sendMutationAwait("voice.createProducerTransport", JsonObject())
            val params = gson.fromJson(response, JsonObject::class.java)
            
            val id = params.get("id").asString
            val iceParameters = params.get("iceParameters").toString()
            val iceCandidates = params.get("iceCandidates").toString()
            val dtlsParameters = params.get("dtlsParameters").toString()

            val listener = object : SendTransport.Listener {
                override fun onConnect(transport: Transport, dtlsParameters: String) {
                    Log.d(TAG, "SendTransport onConnect")
                    runBlocking {
                        try {
                            val input = JsonObject().apply {
                                add("dtlsParameters", gson.fromJson(dtlsParameters, JsonObject::class.java))
                            }
                            webSocketManager.sendMutationAwait("voice.connectProducerTransport", input)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to connect producer transport", e)
                        }
                    }
                }

                override fun onConnectionStateChange(transport: Transport, connectionState: String) {
                    Log.d(TAG, "SendTransport state changed: $connectionState")
                }

                override fun onProduce(transport: Transport, kind: String, rtpParameters: String, appData: String): String {
                    Log.d(TAG, "SendTransport onProduce: kind=$kind")
                    return runBlocking {
                        try {
                            val input = JsonObject().apply {
                                addProperty("transportId", id)
                                addProperty("kind", kind)
                                add("rtpParameters", gson.fromJson(rtpParameters, JsonObject::class.java))
                            }
                            val produceResponse = webSocketManager.sendMutationAwait("voice.produce", input)
                            produceResponse.get("value").asString
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to produce", e)
                            ""
                        }
                    }
                }

                override fun onProduceData(transport: Transport, sctpStreamParameters: String, label: String, protocol: String, appData: String): String {
                    Log.d(TAG, "SendTransport onProduceData")
                    return ""
                }
            }

            sendTransport = device?.createSendTransport(listener, id, iceParameters, iceCandidates, dtlsParameters, null, pcOptions, "{}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup send transport", e)
            throw e
        }
    }

    private suspend fun setupRecvTransport(pcOptions: org.mediasoup.droid.PeerConnection.Options) {
        try {
            val response = webSocketManager.sendMutationAwait("voice.createConsumerTransport", JsonObject())
            val params = gson.fromJson(response, JsonObject::class.java)
            
            val id = params.get("id").asString
            val iceParameters = params.get("iceParameters").toString()
            val iceCandidates = params.get("iceCandidates").toString()
            val dtlsParameters = params.get("dtlsParameters").toString()

            val listener = object : RecvTransport.Listener {
                override fun onConnect(transport: Transport, dtlsParameters: String) {
                    Log.d(TAG, "RecvTransport onConnect")
                    runBlocking {
                        try {
                            val input = JsonObject().apply {
                                add("dtlsParameters", gson.fromJson(dtlsParameters, JsonObject::class.java))
                            }
                            webSocketManager.sendMutationAwait("voice.connectConsumerTransport", input)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to connect consumer transport", e)
                        }
                    }
                }

                override fun onConnectionStateChange(transport: Transport, connectionState: String) {
                    Log.d(TAG, "RecvTransport state changed: $connectionState")
                }
            }

            recvTransport = device?.createRecvTransport(listener, id, iceParameters, iceCandidates, dtlsParameters, null, pcOptions, "{}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup recv transport", e)
            throw e
        }
    }

    private fun startLocalAudio() {
        if (localAudioTrack != null) return

        if (peerConnectionFactory == null || sendTransport == null) return

        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }

        localAudioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("ARDAMSa0", localAudioSource)
        localAudioTrack?.setEnabled(!isMicMuted)

        val producerListener = object : Producer.Listener {
            override fun onTransportClose(producer: Producer) {
                Log.d(TAG, "Mic producer transport closed")
            }
        }

        try {
            micProducer = sendTransport?.produce(
                producerListener,
                localAudioTrack,
                null,
                null,
                null
            )
            
            if (isMicMuted) {
                micProducer?.pause()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to produce local audio", e)
        }
    }

    private suspend fun fetchAndConsumeExistingProducers() {
        try {
            val response = webSocketManager.sendQueryAwait("voice.getProducers", JsonObject())
            val data = gson.fromJson(response, JsonObject::class.java)
            
            val remoteAudioIds = data.getAsJsonArray("remoteAudioIds")
            remoteAudioIds?.forEach {
                val remoteId = it.asInt
                consumeRemoteProducer(remoteId, StreamKind.AUDIO)
            }
            
            // Assuming we aren't supporting video/screen right now, but we could loop remoteVideoIds too.
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch existing producers", e)
        }
    }

    fun consumeRemoteProducer(remoteId: Int, kind: StreamKind) {
        scope.launch {
            try {
                if (device == null || recvTransport == null) return@launch
                
                val rtpCaps = device?.rtpCapabilities
                Log.d(TAG, "Consuming remote $remoteId with rtpCaps: $rtpCaps")
                val input = JsonObject().apply {
                    addProperty("kind", kind.value)
                    addProperty("remoteId", remoteId)
                    add("rtpCapabilities", gson.fromJson(rtpCaps, JsonObject::class.java))
                }
                
                val response = webSocketManager.sendMutationAwait("voice.consume", input)
                val data = gson.fromJson(response, JsonObject::class.java)
                
                val consumerId = data.get("consumerId").asString
                val producerId = data.get("producerId").asString
                val rtpParameters = data.get("consumerRtpParameters").toString()
                
                val consumerKey = "$remoteId:${kind.value}"
                val consumerListener = object : Consumer.Listener {
                    override fun onTransportClose(consumer: Consumer) {
                        Log.d(TAG, "Consumer transport closed: ${consumer.id}")
                        consumers.remove(consumerKey)
                    }
                }

                val consumer = recvTransport?.consume(
                    consumerListener,
                    consumerId,
                    producerId,
                    kind.value,
                    rtpParameters,
                    null
                )
                
                if (consumer != null) {
                    consumers[consumerKey] = consumer
                    
                    val track = consumer.track
                    if (track is AudioTrack) {
                        track.setEnabled(true)
                        if (isSoundMuted) {
                            consumer.pause()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to consume remote producer $remoteId", e)
            }
        }
    }

    fun removeRemoteProducer(remoteId: Int, kind: StreamKind) {
        val key = "$remoteId:${kind.value}"
        consumers[key]?.let { consumer ->
            consumer.close()
            consumers.remove(key)
        }
    }

    fun clearRemoteProducersForUser(userId: Int) {
        val keysToRemove = consumers.keys.filter { it.startsWith("$userId:") }
        keysToRemove.forEach { key ->
            consumers[key]?.close()
            consumers.remove(key)
        }
    }
    
    fun setMicEnabled(enabled: Boolean) {
        isMicMuted = !enabled
        localAudioTrack?.setEnabled(enabled)
        if (enabled) {
            micProducer?.resume()
        } else {
            micProducer?.pause()
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        isSoundMuted = !enabled
        consumers.values.forEach { consumer ->
            if (consumer.kind == "audio") {
                if (enabled) {
                    consumer.resume()
                } else {
                    consumer.pause()
                }
            }
        }
    }

    private fun startAudioLevelPolling() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (true) {
                if (_isConnected.value) {
                    val newLevels = mutableMapOf<String, Float>()
                    
                    // Local mic
                    if (micProducer != null && !isMicMuted) {
                        try {
                            val statsStr = micProducer?.stats
                            if (statsStr != null) {
                                val statsArray = gson.fromJson(statsStr, JsonArray::class.java)
                                var level = 0f
                                statsArray.forEach { statElem ->
                                    val stat = statElem.asJsonObject
                                    val type = stat.get("type")?.asString
                                    if (type == "media-source" || type == "outbound-rtp") {
                                        if (stat.has("audioLevel")) {
                                            level = stat.get("audioLevel").asFloat
                                        }
                                    }
                                }
                                newLevels["local"] = level
                            }
                        } catch (e: Exception) { }
                    }

                    // Remote consumers
                    consumers.forEach { (key, consumer) ->
                        if (consumer.kind == "audio" && !isSoundMuted && !consumer.isPaused) {
                            try {
                                val statsStr = consumer.stats
                                if (statsStr != null) {
                                    val statsArray = gson.fromJson(statsStr, JsonArray::class.java)
                                    var level = 0f
                                    statsArray.forEach { statElem ->
                                        val stat = statElem.asJsonObject
                                        val type = stat.get("type")?.asString
                                        if (type == "inbound-rtp" || type == "track") {
                                            if (stat.has("audioLevel")) {
                                                level = stat.get("audioLevel").asFloat
                                            }
                                        }
                                    }
                                    // Strip the ":audio" suffix from key to get remoteId
                                    val remoteIdStr = key.substringBefore(":")
                                    newLevels[remoteIdStr] = level
                                }
                            } catch (e: Exception) { }
                        }
                    }
                    _audioLevels.value = newLevels
                }
                kotlinx.coroutines.delay(200)
            }
        }
    }

    fun leaveChannel() {
        statsJob?.cancel()
        statsJob = null
        _audioLevels.value = emptyMap()

        eventCollectionJob?.cancel()
        eventCollectionJob = null
        
        onNewProducerSubId?.let { webSocketManager.unsubscribe(it) }
        onNewProducerSubId = null
        onProducerClosedSubId?.let { webSocketManager.unsubscribe(it) }
        onProducerClosedSubId = null

        _connectionState.value = VoiceConnectionState.DISCONNECTED
        _isConnected.value = false
        currentChannelId = null

        micProducer?.close()
        micProducer = null

        consumers.values.forEach { it.close() }
        consumers.clear()

        sendTransport?.close()
        sendTransport = null

        recvTransport?.close()
        recvTransport = null

        device?.dispose()
        device = null

        localAudioTrack?.dispose()
        localAudioTrack = null

        localAudioSource?.dispose()
        localAudioSource = null
        
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        
        audioManager.mode = AudioManager.MODE_NORMAL
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = false
        
        // Let peer connection factory stay active, as well as eglBase.
    }
}
