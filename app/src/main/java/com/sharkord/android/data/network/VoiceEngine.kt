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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import org.webrtc.AudioSource
import org.webrtc.EglBase
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.VideoTrack
import org.webrtc.VideoSource
import org.webrtc.VideoCapturer
import org.webrtc.SurfaceTextureHelper
import org.webrtc.Camera2Enumerator
import org.webrtc.Camera1Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory

enum class VoiceConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}

class VoiceEngine(private val context: Context, private val webSocketManager: WebSocketManager) {
    companion object {
        private const val TAG = "VoiceEngine"
        private var peerConnectionFactoryInitialized = false
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
    // map key is "$remoteId:${kind.value}"
    private val consumers = java.util.concurrent.ConcurrentHashMap<String, Consumer>()

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var localAudioTrack: AudioTrack? = null
    private var localAudioSource: AudioSource? = null
    private val eglBase: EglBase = EglBase.create()
    val eglBaseContext: EglBase.Context get() = eglBase.eglBaseContext
    val videoEngine = VideoEngine(context, eglBaseContext)

    var currentChannelId: Int? = null
        private set

    var isMicMuted = false
        private set
    var isSoundMuted = false
        private set
    val cameraEnabled: Boolean get() = videoEngine.cameraEnabled
    val isScreenSharing: Boolean get() = videoEngine.screenShareEnabled

    val supportedVideoCodecs: List<String> by lazy {
        try {
            val factory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
            factory.supportedCodecs.map { 
                var name = it.name.uppercase()
                if (name.startsWith("AV1")) name = "AV1"
                if (name.startsWith("H264")) name = "H264"
                name
            }.distinct()
        } catch (e: Exception) {
            listOf("VP8", "VP9", "H264")
        }
    }

    // subscription IDs
    private var onNewProducerSubId: Int? = null
    private var onProducerClosedSubId: Int? = null
    private var eventCollectionJob: Job? = null

    // prevent duplicate transport connect calls — mediasoup-droid may call onConnect
    // for every new consumer/producer on the same transport, but the server only
    // accepts the first connectTransport call
    private var producerTransportConnected = false
    private var consumerTransportConnected = false

    // mutex to serialize all consume operations. Consuming audio and video concurrently
    // causes an SDP m-line ordering conflict in WebRTC ("order of m-lines doesn't match")
    private val consumeMutex = Mutex()

    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { }

    init {
        MediasoupClient.initialize(context)
        org.webrtc.Logging.enableLogToDebugOutput(org.webrtc.Logging.Severity.LS_NONE)
    }

    // creates a fresh PeerConnectionFactory with both audio and video codecs
    // must be called fresh for each voice session because the audio device module
    // captures a reference to the session state
    private fun buildPeerConnectionFactory(): PeerConnectionFactory {
        if (!peerConnectionFactoryInitialized) {
            val options = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(options)
            peerConnectionFactoryInitialized = true
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setAudioAttributes(audioAttributes)
            .setUseHardwareAcousticEchoCanceler(false)
            .setUseHardwareNoiseSuppressor(false)
            .createAudioDeviceModule()

        val videoEncoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext,
            /* enableIntelVp8Encoder= */ true,
            /* enableH264HighProfile= */ true
        )
        val videoDecoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        return PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(videoEncoderFactory)
            .setVideoDecoderFactory(videoDecoderFactory)
            .createPeerConnectionFactory()
    }

    fun joinChannel(channelId: Int, routerRtpCapabilities: JsonObject) {
        if (_connectionState.value == VoiceConnectionState.CONNECTING ||
            _connectionState.value == VoiceConnectionState.CONNECTED
        ) {
            leaveChannel()
        }

        currentChannelId = channelId
        _connectionState.value = VoiceConnectionState.CONNECTING

        // build a fresh factory for each session
        peerConnectionFactory?.dispose()
        peerConnectionFactory = buildPeerConnectionFactory()

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

        scope.launch {
            try {
                // 1. Initialize Device
                device = Device()

                val pcOptions = org.mediasoup.droid.PeerConnection.Options()
                pcOptions.setFactory(peerConnectionFactory)

                // extract the actual RTP capabilities if it's wrapped in an outer object
                var actualCaps: JsonObject = routerRtpCapabilities
                if (routerRtpCapabilities.has("rtpCapabilities")) {
                    actualCaps = routerRtpCapabilities.getAsJsonObject("rtpCapabilities")
                } else if (routerRtpCapabilities.has("routerRtpCapabilities")) {
                    actualCaps = routerRtpCapabilities.getAsJsonObject("routerRtpCapabilities")
                } else if (routerRtpCapabilities.has("routerCapabilities")) {
                    actualCaps = routerRtpCapabilities.getAsJsonObject("routerCapabilities")
                }

                // force physical rotation by stripping video-orientation header extension
                if (actualCaps.has("headerExtensions")) {
                    val exts = actualCaps.getAsJsonArray("headerExtensions")
                    val filteredExts = com.google.gson.JsonArray()
                    for (i in 0 until exts.size()) {
                        val ext = exts.get(i).asJsonObject
                        val uri = ext.get("uri")?.asString
                        if (uri != "urn:3gpp:video-orientation") {
                            filteredExts.add(ext)
                        }
                    }
                    actualCaps.add("headerExtensions", filteredExts)
                }

                if (actualCaps.has("codecs")) {
                    val preferredCodec = SharkordClient.session.getVideoCodec(supportedVideoCodecs)
                    val codecs = actualCaps.getAsJsonArray("codecs")
                    val prioritizedCodecs = com.google.gson.JsonArray()
                    val otherCodecs = com.google.gson.JsonArray()
                    
                    for (i in 0 until codecs.size()) {
                        val codec = codecs.get(i).asJsonObject
                        val mimeType = codec.get("mimeType")?.asString ?: ""
                        if (mimeType.equals("video/$preferredCodec", ignoreCase = true)) {
                            prioritizedCodecs.add(codec)
                        } else {
                            otherCodecs.add(codec)
                        }
                    }
                    
                    prioritizedCodecs.addAll(otherCodecs)
                    actualCaps.add("codecs", prioritizedCodecs)
                }

                val rtpCapsStr = actualCaps.toString()

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
                                val remoteId = data.get("remoteId")?.asInt ?: data.get("producerId")?.asInt ?: return@collect
                                val kindStr = data.get("kind")?.asString ?: return@collect
                                if (kindStr == "audio") {
                                    consumeRemoteProducer(remoteId, StreamKind.AUDIO)
                                } else if (kindStr == "video") {
                                    consumeRemoteProducer(remoteId, StreamKind.VIDEO)
                                } else if (kindStr == "screen") {
                                    consumeRemoteProducer(remoteId, StreamKind.SCREEN)
                                } else if (kindStr == "screen_audio") {
                                    consumeRemoteProducer(remoteId, StreamKind.SCREEN_AUDIO)
                                }
                            }
                            "voice.onProducerClosed" -> {
                                val data = event.data
                                val remoteId = data.get("remoteId")?.asInt ?: data.get("producerId")?.asInt ?: return@collect
                                val kindStr = data.get("kind")?.asString ?: return@collect
                                if (kindStr == "audio") {
                                    removeRemoteProducer(remoteId, StreamKind.AUDIO)
                                } else if (kindStr == "video") {
                                    removeRemoteProducer(remoteId, StreamKind.VIDEO)
                                } else if (kindStr == "screen") {
                                    removeRemoteProducer(remoteId, StreamKind.SCREEN)
                                } else if (kindStr == "screen_audio") {
                                    removeRemoteProducer(remoteId, StreamKind.SCREEN_AUDIO)
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
                    if (producerTransportConnected) {
                        Log.d(TAG, "SendTransport onConnect: already connected, skipping")
                        return
                    }
                    producerTransportConnected = true
                    Log.d(TAG, "SendTransport onConnect")
                    runBlocking {
                        try {
                            val input = JsonObject().apply {
                                add("dtlsParameters", gson.fromJson(dtlsParameters, JsonObject::class.java))
                            }
                            webSocketManager.sendMutationAwait("voice.connectProducerTransport", input)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to connect producer transport", e)
                            producerTransportConnected = false
                        }
                    }
                }

                override fun onConnectionStateChange(transport: Transport, connectionState: String) {
                    Log.d(TAG, "SendTransport state changed: $connectionState")
                }

                override fun onProduce(transport: Transport, kind: String, rtpParameters: String, appData: String): String {
                    Log.d(TAG, "SendTransport onProduce: kind=$kind, appData=$appData")
                    return runBlocking {
                        var actualKind = kind
                        if (appData.isNotEmpty() && appData != "null") {
                            try {
                                val appDataObject = gson.fromJson(appData, com.google.gson.JsonObject::class.java)
                                if (appDataObject.has("kind")) {
                                    actualKind = appDataObject.get("kind").asString
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to parse appData JSON", e)
                            }
                        }

                        val input = JsonObject().apply {
                            addProperty("transportId", id)
                            addProperty("kind", actualKind)
                            add("rtpParameters", gson.fromJson(rtpParameters, JsonObject::class.java))
                        }
                        val produceResponse = webSocketManager.sendMutationAwait("voice.produce", input)
                        // server returns producer.id directly (a plain string), wrapped in {"value": "<id>"}
                        val producerId = produceResponse.get("value")?.asString
                        if (producerId.isNullOrEmpty()) {
                            throw RuntimeException("Server returned empty producer ID for kind=$kind")
                        }
                        Log.d(TAG, "onProduce got producerId=$producerId for kind=$kind")
                        producerId
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
                    if (consumerTransportConnected) {
                        Log.d(TAG, "RecvTransport onConnect: already connected, skipping")
                        return
                    }
                    consumerTransportConnected = true
                    Log.d(TAG, "RecvTransport onConnect")
                    runBlocking {
                        try {
                            val input = JsonObject().apply {
                                add("dtlsParameters", gson.fromJson(dtlsParameters, JsonObject::class.java))
                            }
                            webSocketManager.sendMutationAwait("voice.connectConsumerTransport", input)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to connect consumer transport", e)
                            consumerTransportConnected = false
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
        val factory = peerConnectionFactory ?: return
        if (sendTransport == null) return

        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }

        localAudioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("ARDAMSa0", localAudioSource)
        localAudioTrack?.setEnabled(!isMicMuted)

        val producerListener = object : Producer.Listener {
            override fun onTransportClose(producer: Producer) {
                Log.d(TAG, "Mic producer transport closed")
            }
        }

        val codecOptions = """{"opusStereo":false,"opusFec":true,"opusDtx":false,"opusMaxPlaybackRate":48000,"opusMaxAverageBitrate":128000}"""

        try {
            micProducer = sendTransport?.produce(
                producerListener,
                localAudioTrack,
                null,
                codecOptions,
                null,
                """{"kind":"audio"}"""
            )

            if (isMicMuted) {
                micProducer?.pause()
            }
            Log.d(TAG, "Microphone producer created: ${micProducer?.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to produce local audio", e)
        }
    }

    private suspend fun fetchAndConsumeExistingProducers() {
        try {
            val response = webSocketManager.sendQueryAwait("voice.getProducers", JsonObject())
            val data = gson.fromJson(response, JsonObject::class.java)

            // IMPORTANT: consume sequentially — concurrent consumption causes SDP m-line
            // ordering conflicts in WebRTC ("order of m-lines in answer doesn't match offer")
            val remoteAudioIds = data.getAsJsonArray("remoteAudioIds")
            remoteAudioIds?.forEach {
                val remoteId = it.asInt
                consumeRemoteProducerSuspend(remoteId, StreamKind.AUDIO)
            }

            val remoteVideoIds = data.getAsJsonArray("remoteVideoIds")
            remoteVideoIds?.forEach {
                val remoteId = it.asInt
                consumeRemoteProducerSuspend(remoteId, StreamKind.VIDEO)
            }

            val remoteScreenIds = data.getAsJsonArray("remoteScreenIds")
            remoteScreenIds?.forEach {
                val remoteId = it.asInt
                consumeRemoteProducerSuspend(remoteId, StreamKind.SCREEN)
            }

            val remoteScreenAudioIds = data.getAsJsonArray("remoteScreenAudioIds")
            remoteScreenAudioIds?.forEach {
                val remoteId = it.asInt
                consumeRemoteProducerSuspend(remoteId, StreamKind.SCREEN_AUDIO)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch existing producers", e)
        }
    }

    // fire-and-forget wrapper: schedules a sequential consume via the mutex
    // used by real-time events (onNewProducer)
    fun consumeRemoteProducer(remoteId: Int, kind: StreamKind) {
        scope.launch {
            consumeRemoteProducerSuspend(remoteId, kind)
        }
    }

    // suspend version that holds [consumeMutex] to ensure consume operations are
    // strictly sequential. Concurrent consumption causes an SDP m-line ordering
    // conflict in WebRTC: "order of m-lines in answer doesn't match order in offer"
    private suspend fun consumeRemoteProducerSuspend(remoteId: Int, kind: StreamKind) {
        consumeMutex.withLock {
            try {
                if (device == null || recvTransport == null) return

                val rtpCaps = device?.rtpCapabilities
                Log.d(TAG, "Consuming remote $remoteId ($kind) with rtpCaps length: ${rtpCaps?.length}")

                val input = JsonObject().apply {
                    addProperty("kind", kind.value)
                    addProperty("remoteId", remoteId)
                    add("rtpCapabilities", gson.fromJson(rtpCaps, JsonObject::class.java))
                }

                val response = webSocketManager.sendMutationAwait("voice.consume", input)
                val data = gson.fromJson(response, JsonObject::class.java)

                // server returns: { producerId, consumerId, consumerKind, consumerRtpParameters, consumerType, qualityLayers }
                val consumerId = data.get("consumerId").asString
                val producerId = data.get("producerId").asString
                val consumerKind = data.get("consumerKind")?.asString ?: kind.value
                val rtpParameters = data.get("consumerRtpParameters").toString()

                val consumerKey = "$remoteId:$consumerKind"
                
                val webrtcKind = when (kind) {
                    StreamKind.SCREEN, StreamKind.VIDEO -> "video"
                    StreamKind.SCREEN_AUDIO, StreamKind.AUDIO -> "audio"
                    else -> if (consumerKind.contains("video")) "video" else "audio"
                }

                // close any existing consumer for this key
                consumers[consumerKey]?.let {
                    if (!it.isClosed) it.close()
                    consumers.remove(consumerKey)
                }

                val consumerListener = object : Consumer.Listener {
                    override fun onTransportClose(consumer: Consumer) {
                        Log.d(TAG, "Consumer transport closed: ${consumer.id}")
                        consumers.remove(consumerKey)
                        if (webrtcKind == "video") {
                            videoEngine.removeRemoteVideoTrack(consumerKey)
                        }
                    }
                }

                val consumer = recvTransport?.consume(
                    consumerListener,
                    consumerId,
                    producerId,
                    webrtcKind,
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
                        Log.d(TAG, "Remote audio consumer ready for $remoteId")
                    } else if (track is VideoTrack) {
                        track.setEnabled(true)
                        videoEngine.addRemoteVideoTrack(consumerKey, track)
                        Log.d(TAG, "Remote video consumer ready for $remoteId")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to consume remote producer $remoteId ($kind)", e)
            }
        }
    }

    fun removeRemoteProducer(remoteId: Int, kind: StreamKind) {
        scope.launch {
            consumeMutex.withLock {
                val key = "$remoteId:${kind.value}"
                // remove video track from UI FIRST — this triggers Compose to remove
                // the SurfaceViewRenderer before we invalidate native resources
                if (kind == StreamKind.VIDEO || kind == StreamKind.SCREEN || kind == StreamKind.EXTERNAL_VIDEO) {
                    videoEngine.removeRemoteVideoTrack(key)
                    val consumerToClose = consumers.remove(key) ?: return@withLock
                    scope.launch {
                        delay(500)
                        try {
                            val track = consumerToClose.track
                            track?.setEnabled(false)
                        } catch (_: Exception) {}
                        if (!consumerToClose.isClosed) consumerToClose.close()
                    }
                } else {
                    consumers.remove(key)?.let { consumer ->
                        // disable the track to stop frame delivery to any remaining sinks
                        try {
                            val track = consumer.track
                            track?.setEnabled(false)
                        } catch (_: Exception) {}
                        if (!consumer.isClosed) consumer.close()
                    }
                }
            }
        }
    }

    fun clearRemoteProducersForUser(userId: Int) {
        scope.launch {
            consumeMutex.withLock {
                // remove video track from UI FIRST
                videoEngine.removeRemoteVideoTrack("$userId:video")
                videoEngine.removeRemoteVideoTrack("$userId:screen")
                videoEngine.removeRemoteVideoTrack("$userId:external_video")
                val keysToRemove = consumers.keys.filter { it.startsWith("$userId:") }
                keysToRemove.forEach { key ->
                    val consumerToClose = consumers.remove(key) ?: return@forEach
                    if (key.endsWith(":video")) {
                        scope.launch {
                            delay(500)
                            try {
                                consumerToClose.track?.setEnabled(false)
                            } catch (_: Exception) {}
                            if (!consumerToClose.isClosed) consumerToClose.close()
                        }
                    } else {
                        try {
                            consumerToClose.track?.setEnabled(false)
                        } catch (_: Exception) {}
                        if (!consumerToClose.isClosed) consumerToClose.close()
                    }
                }
            }
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

    fun setCameraEnabled(context: Context, enabled: Boolean) {
        videoEngine.setCameraEnabled(context, enabled, peerConnectionFactory, sendTransport)
    }

    fun setScreenShareEnabled(context: Context, intent: android.content.Intent?, enabled: Boolean) {
        videoEngine.setScreenShareEnabled(context, enabled, intent, peerConnectionFactory, sendTransport)
    }

    fun switchCamera(context: Context) {
        videoEngine.switchCamera(context, peerConnectionFactory, sendTransport)
    }

    private fun startAudioLevelPolling() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (true) {
                if (_isConnected.value) {
                    val newLevels = mutableMapOf<String, Float>()

                    // local mic
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

                    // remote consumers
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
                                    // strip the ":audio" suffix from key to get remoteId
                                    val remoteIdStr = key.substringBefore(":")
                                    newLevels[remoteIdStr] = level
                                }
                            } catch (e: Exception) { }
                        }
                    }
                    _audioLevels.value = newLevels
                }
                delay(200)
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

        // reset transport-connected guards so next join can call connectTransport again
        producerTransportConnected = false
        consumerTransportConnected = false

        // detach from UI immediately
        videoEngine.clearRemoteVideoTracks()

        val oldMicProducer = micProducer
        micProducer = null

        val consumersToClose = consumers.values.toList()
        consumers.clear()

        val oldSendTransport = sendTransport
        sendTransport = null

        val oldRecvTransport = recvTransport
        recvTransport = null

        val oldDevice = device
        device = null

        val oldLocalAudioTrack = localAudioTrack
        localAudioTrack = null

        val oldLocalAudioSource = localAudioSource
        localAudioSource = null

        val oldPeerConnectionFactory = peerConnectionFactory
        peerConnectionFactory = null

        scope.launch {
            videoEngine.dispose()

            delay(800) // Ensure UI unmounts AND VideoEngine finishes its 500ms cleanup
            
            // close producers
            oldMicProducer?.let { if (!it.isClosed) it.close() }

            // close all consumers
            consumersToClose.forEach { consumer ->
                try {
                    consumer.track?.setEnabled(false)
                } catch (_: Exception) {}
                if (!consumer.isClosed) consumer.close()
            }

            // close transports BEFORE disposing device
            oldSendTransport?.let { if (!it.isClosed) it.close() }
            oldRecvTransport?.let { if (!it.isClosed) it.close() }

            // dispose device
            oldDevice?.dispose()

            // dispose audio resources
            oldLocalAudioTrack?.dispose()
            oldLocalAudioSource?.dispose()

            // dispose factory - will be rebuilt fresh on next join
            oldPeerConnectionFactory?.dispose()
        }

        // release audio focus
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }

        audioManager.mode = AudioManager.MODE_NORMAL
    }
}
