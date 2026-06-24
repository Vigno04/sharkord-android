package com.sharkord.android.data.network

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.mediasoup.droid.Producer
import org.mediasoup.droid.SendTransport
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.ScreenCapturerAndroid

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock

class VideoEngine(
    private val context: Context,
    val eglBaseContext: EglBase.Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    companion object {
        private const val TAG = "VideoEngine"
    }

    private val _localVideoTrackFlow = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrackFlow: StateFlow<VideoTrack?> = _localVideoTrackFlow.asStateFlow()

    private val _remoteVideoTracks = MutableStateFlow<Map<String, VideoTrack>>(emptyMap())
    val remoteVideoTracks: StateFlow<Map<String, VideoTrack>> = _remoteVideoTracks.asStateFlow()

    private val _localScreenTrackFlow = MutableStateFlow<VideoTrack?>(null)
    val localScreenTrackFlow: StateFlow<VideoTrack?> = _localScreenTrackFlow.asStateFlow()

    var cameraEnabled = false
        private set

    var screenShareEnabled = false
        private set

    private var videoProducer: Producer? = null
    private var localVideoTrack: VideoTrack? = null
    private var localVideoSource: VideoSource? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var screenProducer: Producer? = null
    private var screenVideoTrack: VideoTrack? = null
    private var screenVideoSource: VideoSource? = null
    private var screenCapturer: VideoCapturer? = null
    private var screenSurfaceTextureHelper: SurfaceTextureHelper? = null

    fun addRemoteVideoTrack(remoteId: String, track: VideoTrack) {
        _remoteVideoTracks.update { map ->
            map.toMutableMap().also { it[remoteId] = track }
        }
    }

    fun removeRemoteVideoTrack(remoteId: String) {
        _remoteVideoTracks.update { map ->
            map.toMutableMap().also { it.remove(remoteId) }
        }
    }

    fun clearRemoteVideoTracks() {
        _remoteVideoTracks.value = emptyMap()
    }

    private val cameraMutex = kotlinx.coroutines.sync.Mutex()

    fun setCameraEnabled(
        activeContext: Context,
        enabled: Boolean,
        factory: PeerConnectionFactory?,
        sendTransport: SendTransport?
    ) {
        if (cameraEnabled == enabled) return
        cameraEnabled = enabled

        scope.launch {
            cameraMutex.withLock {
                if (cameraEnabled && localVideoTrack == null) {
                    startLocalVideo(activeContext, factory, sendTransport)
                } else if (!cameraEnabled && localVideoTrack != null) {
                    stopLocalVideoSuspend()
                }
            }
        }
    }

    private fun startLocalVideo(activeContext: Context, factory: PeerConnectionFactory?, sendTransport: SendTransport?) {
        if (factory == null || sendTransport == null) return
        if (localVideoTrack != null) return

        val enumerator: CameraEnumerator = if (Camera2Enumerator.isSupported(context)) {
            Camera2Enumerator(context)
        } else {
            Camera1Enumerator(true)
        }

        val deviceNames = enumerator.deviceNames
        var selectedDeviceName: String? = null
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                selectedDeviceName = deviceName
                break
            }
        }
        if (selectedDeviceName == null) {
            for (deviceName in deviceNames) {
                if (!enumerator.isFrontFacing(deviceName)) {
                    selectedDeviceName = deviceName
                    break
                }
            }
        }

        if (selectedDeviceName == null) {
            Log.e(TAG, "No camera found")
            cameraEnabled = false
            return
        }

        videoCapturer = enumerator.createCapturer(selectedDeviceName, null)
        if (videoCapturer == null) {
            Log.e(TAG, "Failed to create video capturer")
            cameraEnabled = false
            return
        }

        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext)
        localVideoSource = factory.createVideoSource(videoCapturer!!.isScreencast)
        videoCapturer?.initialize(surfaceTextureHelper, activeContext, localVideoSource?.capturerObserver)
        
        val isFront = enumerator.isFrontFacing(selectedDeviceName)
        val resParts = if (isFront) {
            SharkordClient.session.frontVideoResolution.split("x")
        } else {
            SharkordClient.session.backVideoResolution.split("x")
        }
        val targetWidth = resParts.getOrNull(0)?.toIntOrNull() ?: 1280
        val targetHeight = resParts.getOrNull(1)?.toIntOrNull() ?: 720
        val targetFps = if (isFront) SharkordClient.session.frontVideoFps else SharkordClient.session.backVideoFps

        var finalWidth = targetWidth
        var finalHeight = targetHeight
        var finalFps = targetFps

        try {
            val formats = enumerator.getSupportedFormats(selectedDeviceName)
            if (!formats.isNullOrEmpty()) {
                var bestFormat = formats[0]
                var minDiff = Long.MAX_VALUE
                val targetPixels = targetWidth.toLong() * targetHeight.toLong()
                for (format in formats) {
                    val pixels = format.width.toLong() * format.height.toLong()
                    val diff = Math.abs(pixels - targetPixels)
                    if (diff < minDiff) {
                        minDiff = diff
                        bestFormat = format
                    }
                }
                finalWidth = bestFormat.width
                finalHeight = bestFormat.height
                val maxSupportedFps = bestFormat.framerate.max / 1000
                if (finalFps > maxSupportedFps) {
                    finalFps = maxSupportedFps
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding supported formats", e)
        }

        videoCapturer?.startCapture(finalWidth, finalHeight, finalFps)

        localVideoTrack = factory.createVideoTrack("ARDAMSv0", localVideoSource)
        localVideoTrack?.setEnabled(true)
        _localVideoTrackFlow.value = localVideoTrack

        val producerListener = object : Producer.Listener {
            override fun onTransportClose(producer: Producer) {
                Log.d(TAG, "Video producer transport closed")
            }
        }

        try {
            videoProducer = sendTransport.produce(
                producerListener,
                localVideoTrack,
                null,
                null,
                null,
                """{"kind":"video"}"""
            )
            Log.d(TAG, "Video producer created: ${videoProducer?.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to produce local video", e)
        }
    }

    fun stopLocalVideo() {
        cameraEnabled = false
        scope.launch {
            cameraMutex.withLock {
                stopLocalVideoSuspend()
            }
        }
    }

    suspend fun dispose() {
        cameraEnabled = false
        screenShareEnabled = false
        cameraMutex.withLock {
            stopLocalVideoSuspend()
            stopLocalScreenShareSuspend()
        }
    }

    fun switchCamera(
        activeContext: Context,
        factory: PeerConnectionFactory?,
        sendTransport: SendTransport?
    ) {
        scope.launch {
            cameraMutex.withLock {
                if (!cameraEnabled || localVideoTrack == null || videoCapturer !is org.webrtc.CameraVideoCapturer) return@withLock
                val current = SharkordClient.session.defaultCamera
                SharkordClient.session.defaultCamera = if (current == "Front") "Back" else "Front"
                
                val isFront = SharkordClient.session.defaultCamera == "Front"
                val resParts = if (isFront) {
                    SharkordClient.session.frontVideoResolution.split("x")
                } else {
                    SharkordClient.session.backVideoResolution.split("x")
                }
                val targetWidth = resParts.getOrNull(0)?.toIntOrNull() ?: 1280
                val targetHeight = resParts.getOrNull(1)?.toIntOrNull() ?: 720
                val targetFps = if (isFront) SharkordClient.session.frontVideoFps else SharkordClient.session.backVideoFps

                (videoCapturer as org.webrtc.CameraVideoCapturer).switchCamera(object : org.webrtc.CameraVideoCapturer.CameraSwitchHandler {
                    override fun onCameraSwitchDone(isFrontFacing: Boolean) {
                        videoCapturer?.changeCaptureFormat(targetWidth, targetHeight, targetFps)
                    }
                    override fun onCameraSwitchError(errorDescription: String?) {
                        Log.e(TAG, "Camera switch error: $errorDescription")
                    }
                })
            }
        }
    }

    private suspend fun stopLocalVideoSuspend() {
        val oldProducer = videoProducer
        val oldLocalVideoTrack = localVideoTrack
        val oldVideoCapturer = videoCapturer
        val oldLocalVideoSource = localVideoSource
        val oldSurfaceTextureHelper = surfaceTextureHelper

        videoProducer = null
        _localVideoTrackFlow.value = null
        localVideoTrack = null
        videoCapturer = null
        localVideoSource = null
        surfaceTextureHelper = null

        if (oldVideoCapturer == null) return

        delay(500)
        
        oldProducer?.let {
            if (!it.isClosed) it.close()
        }

        oldLocalVideoTrack?.dispose()

        try {
            oldVideoCapturer.stopCapture()
        } catch (e: InterruptedException) {
            Log.e(TAG, "Failed to stop capture", e)
        } catch (e: Exception) {
            Log.e(TAG, "Exception stopping capture", e)
        }
        oldVideoCapturer.dispose()

        oldLocalVideoSource?.dispose()
        oldSurfaceTextureHelper?.dispose()
    }

    fun setScreenShareEnabled(
        activeContext: Context,
        enabled: Boolean,
        intent: Intent?,
        factory: PeerConnectionFactory?,
        sendTransport: SendTransport?
    ) {
        if (screenShareEnabled == enabled) return
        screenShareEnabled = enabled

        scope.launch {
            cameraMutex.withLock {
                if (screenShareEnabled && screenVideoTrack == null && intent != null) {
                    startLocalScreenShare(activeContext, intent, factory, sendTransport)
                } else if (!screenShareEnabled && screenVideoTrack != null) {
                    stopLocalScreenShareSuspend()
                }
            }
        }
    }

    private fun startLocalScreenShare(activeContext: Context, intent: Intent, factory: PeerConnectionFactory?, sendTransport: SendTransport?) {
        if (factory == null || sendTransport == null) return
        if (screenVideoTrack != null) return

        screenCapturer = ScreenCapturerAndroid(intent, object : android.media.projection.MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "Screen sharing stopped")
                screenShareEnabled = false
                scope.launch {
                    cameraMutex.withLock { stopLocalScreenShareSuspend() }
                }
            }
        })
        
        screenSurfaceTextureHelper = SurfaceTextureHelper.create("ScreenCaptureThread", eglBaseContext)
        screenVideoSource = factory.createVideoSource(true)
        screenCapturer?.initialize(screenSurfaceTextureHelper, activeContext, screenVideoSource?.capturerObserver)
        
        val displayMetrics = activeContext.resources.displayMetrics
        val resString = com.sharkord.android.data.network.SharkordClient.session.screenShareResolution
        val resParts = resString.split("x")
        val width = resParts.getOrNull(0)?.toIntOrNull() ?: displayMetrics.widthPixels
        val height = resParts.getOrNull(1)?.toIntOrNull() ?: displayMetrics.heightPixels
        val fps = com.sharkord.android.data.network.SharkordClient.session.screenShareFps
        screenCapturer?.startCapture(width, height, fps)

        screenVideoTrack = factory.createVideoTrack("ARDAMSv0_screen", screenVideoSource)
        screenVideoTrack?.setEnabled(true)
        _localScreenTrackFlow.value = screenVideoTrack

        val producerListener = object : Producer.Listener {
            override fun onTransportClose(producer: Producer) {
                Log.d(TAG, "Screen producer transport closed")
            }
        }

        try {
            screenProducer = sendTransport.produce(
                producerListener,
                screenVideoTrack,
                null,
                null,
                null,
                """{"kind":"screen"}"""
            )
            Log.d(TAG, "Screen producer created: ${screenProducer?.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to produce local screen", e)
        }
    }

    private suspend fun stopLocalScreenShareSuspend() {
        val oldProducer = screenProducer
        val oldTrack = screenVideoTrack
        val oldCapturer = screenCapturer
        val oldSource = screenVideoSource
        val oldSurface = screenSurfaceTextureHelper

        screenProducer = null
        _localScreenTrackFlow.value = null
        screenVideoTrack = null
        screenCapturer = null
        screenVideoSource = null
        screenSurfaceTextureHelper = null

        if (oldCapturer == null) return

        delay(500)
        
        oldProducer?.let {
            if (!it.isClosed) it.close()
        }

        oldTrack?.dispose()

        try {
            oldCapturer.stopCapture()
        } catch (e: InterruptedException) {
            Log.e(TAG, "Failed to stop screen capture", e)
        } catch (e: Exception) {
            Log.e(TAG, "Exception stopping screen capture", e)
        }
        oldCapturer.dispose()

        oldSource?.dispose()
        oldSurface?.dispose()
    }

}
