package com.sharkord.android.ui.home.components

import com.sharkord.android.ui.theme.SharkordTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon.ScalingType
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

// self-contained WebRTC video renderer that manages the full lifecycle of the
// surfaceViewRenderer internally, avoiding compose state race conditions
// key design decisions:
// - No setZOrderMediaOverlay: dynamically adding/removing overlay surfaces in a
// lazyVerticalGrid causes fatal surface-layer conflicts (resource ID 0xffffffff crash)
// - Thread-safe stats: WebRTC's onFrame runs on its own thread; we use AtomicIntegers
// and poll them on the main thread via LaunchedEffect
// - Single AndroidView with onRelease: sink binding happens in factory/update, cleanup
// in onRelease. No separate DisposableEffect needed, eliminating race conditions
// between compose state updates and effect re-runs
@Composable
fun WebRtcVideoRenderer(
    videoTrack: VideoTrack,
    eglBaseContext: EglBase.Context,
    isZoomedOut: Boolean = false,
    modifier: Modifier = Modifier,
    cornerRadiusDp: Float = 0f,
    setZOrderMediaOverlay: Boolean = false
) {
    var videoWidth by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var videoHeight by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var frameRate by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    // thread-safe counters updated from WebRTC's rendering thread
    val atomicWidth = remember { java.util.concurrent.atomic.AtomicInteger(0) }
    val atomicHeight = remember { java.util.concurrent.atomic.AtomicInteger(0) }
    val atomicFrames = remember { java.util.concurrent.atomic.AtomicInteger(0) }
    val atomicLastTime = remember { java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis()) }
    val atomicFps = remember { java.util.concurrent.atomic.AtomicInteger(0) }

    // poll stats from the atomic counters on the main thread
    LaunchedEffect(Unit) {
        while (true) {
            val w = atomicWidth.get()
            val h = atomicHeight.get()
            val fps = atomicFps.get()
            if (w != videoWidth) videoWidth = w
            if (h != videoHeight) videoHeight = h
            if (fps != frameRate) frameRate = fps
            
            if (videoWidth == 0) {
                kotlinx.coroutines.delay(16)
            } else {
                kotlinx.coroutines.delay(500)
            }
        }
    }

    val statsSink = remember {
        object : org.webrtc.VideoSink {
            override fun onFrame(frame: org.webrtc.VideoFrame) {
                val w = frame.buffer.width
                val h = frame.buffer.height
                if (atomicWidth.get() != w) atomicWidth.set(w)
                if (atomicHeight.get() != h) atomicHeight.set(h)
                val count = atomicFrames.incrementAndGet()
                val now = System.currentTimeMillis()
                val last = atomicLastTime.get()
                if (now - last >= 1000) {
                    if (atomicLastTime.compareAndSet(last, now)) {
                        atomicFps.set(count)
                        atomicFrames.set(0)
                    }
                }
            }
        }
    }

    // stable holder so we can track what track is currently bound without
    // triggering recomposition when the reference changes
    val currentTrackRef = remember { java.util.concurrent.atomic.AtomicReference<VideoTrack?>(null) }
    // tracks whether the SurfaceHolder's surface is currently available
    val surfaceReadyRef = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    // this flag instantly cuts off frames to the EGL renderer when the view is being released
    // it prevents WebRTC from pushing frames to a surface that is concurrently being
    // destroyed by the Android WindowManager, avoiding deadlocks in the EGL render thread
    val isReceivingFrames = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    val viewRef = remember { java.util.concurrent.atomic.AtomicReference<SurfaceViewRenderer?>(null) }
    val proxySink = remember {
        object : org.webrtc.VideoSink {
            override fun onFrame(frame: org.webrtc.VideoFrame) {
                if (isReceivingFrames.get()) {
                    viewRef.get()?.onFrame(frame)
                }
            }
        }
    }

    Box(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                SurfaceViewRenderer(context).apply {
                    // MUST be true to prevent black screens and BLASTBufferQueue rejections
                    // on modern Android (Android 11+) which enforce buffer size matching.
                    setEnableHardwareScaler(true)
                    setZOrderMediaOverlay(setZOrderMediaOverlay)
                    
                    viewRef.set(this)
                    init(eglBaseContext, null)
                    setScalingType(if (isZoomedOut) ScalingType.SCALE_ASPECT_FIT else ScalingType.SCALE_ASPECT_FILL)
                    
                    if (cornerRadiusDp > 0f) {
                        clipToOutline = true
                        outlineProvider = object : android.view.ViewOutlineProvider() {
                            override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                                outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusDp * resources.displayMetrics.density)
                            }
                        }
                    } else {
                        clipToOutline = false
                        outlineProvider = null
                    }


                    // defer addSink until the Surface is actually created
                    // calling addSink before surfaceCreated delivers frames to an
                    // uninitialised surface which causes a BLAST rejection
                    holder.addCallback(object : android.view.SurfaceHolder.Callback {
                        override fun surfaceCreated(h: android.view.SurfaceHolder) {
                            surfaceReadyRef.set(true)
                            val track = currentTrackRef.get()
                            if (track != null) {
                                // delay binding to allow Compose layout and SurfaceView dimensions to stabilize
                                postDelayed({
                                    if (surfaceReadyRef.get() && currentTrackRef.get() == track) {
                                        try {
                                            track.addSink(proxySink)
                                            track.addSink(statsSink)
                                        } catch (_: Exception) {}
                                    }
                                }, 250)
                            }
                        }
                        override fun surfaceChanged(h: android.view.SurfaceHolder, f: Int, w: Int, h2: Int) {}
                        override fun surfaceDestroyed(h: android.view.SurfaceHolder) {
                            surfaceReadyRef.set(false)
                            val track = currentTrackRef.get()
                            if (track != null) {
                                try {
                                    track.removeSink(proxySink)
                                    track.removeSink(statsSink)
                                } catch (_: Exception) {}
                            }
                        }
                    })
                    currentTrackRef.set(videoTrack)
                }
            },
            update = { view ->
                view.setScalingType(if (isZoomedOut) ScalingType.SCALE_ASPECT_FIT else ScalingType.SCALE_ASPECT_FILL)
                if (cornerRadiusDp > 0f) {
                    view.clipToOutline = true
                    view.outlineProvider = object : android.view.ViewOutlineProvider() {
                        override fun getOutline(v: android.view.View, outline: android.graphics.Outline) {
                            outline.setRoundRect(0, 0, v.width, v.height, cornerRadiusDp * v.resources.displayMetrics.density)
                        }
                    }
                } else {
                    view.clipToOutline = false
                    view.outlineProvider = null
                }
                view.requestLayout()
                // if the video track changed, rebind (only if surface is ready)
                val prevTrack = currentTrackRef.get()
                if (prevTrack !== videoTrack) {
                    if (surfaceReadyRef.get()) {
                        try {
                            prevTrack?.removeSink(proxySink)
                            prevTrack?.removeSink(statsSink)
                        } catch (_: Exception) {}
                        
                        view.postDelayed({
                            if (surfaceReadyRef.get() && currentTrackRef.get() == videoTrack) {
                                try {
                                    videoTrack.addSink(proxySink)
                                    videoTrack.addSink(statsSink)
                                } catch (_: Exception) {}
                            }
                        }, 250)
                    }
                    currentTrackRef.set(videoTrack)
                }
            },
            modifier = Modifier.layout { measurable, constraints ->
                val videoAspectRatio = if (videoWidth > 0 && videoHeight > 0) videoWidth.toFloat() / videoHeight else 1f
                val constraintWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
                val constraintHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
                
                var targetWidth = constraintWidth
                var targetHeight = constraintHeight
                
                if (constraintWidth > 0 && constraintHeight > 0) {
                    val constraintAspectRatio = constraintWidth.toFloat() / constraintHeight
                    if (isZoomedOut) { 
                        // FIT mode: shrink the SurfaceView to match the video aspect ratio precisely,
                        // so it doesn't paint black bars that hide the background avatar.
                        if (videoAspectRatio > constraintAspectRatio) {
                            targetWidth = constraintWidth
                            targetHeight = (constraintWidth / videoAspectRatio).toInt()
                        } else {
                            targetHeight = constraintHeight
                            targetWidth = (constraintHeight * videoAspectRatio).toInt()
                        }
                    } else { 
                        // FILL mode: SurfaceView takes exact container size. 
                        // SurfaceViewRenderer's SCALE_ASPECT_FILL will crop the video internally.
                        targetWidth = constraintWidth
                        targetHeight = constraintHeight
                    }
                }
                
                // In FIT mode, exactConstraints will be smaller than container.
                // In FILL mode, exactConstraints will be exactly the container size.
                val exactConstraints = androidx.compose.ui.unit.Constraints.fixed(targetWidth, targetHeight)
                val placeable = measurable.measure(exactConstraints)
                
                // Report the CONTAINER size to parent so we never expand the grid cell.
                val layoutWidth = if (constraintWidth > 0) constraintWidth else targetWidth
                val layoutHeight = if (constraintHeight > 0) constraintHeight else targetHeight
                
                layout(layoutWidth, layoutHeight) {
                    // Center the view within the container
                    placeable.place(
                        (layoutWidth - placeable.width) / 2,
                        (layoutHeight - placeable.height) / 2
                    )
                }
            },
            onRelease = { view ->
                isReceivingFrames.set(false)
                try {
                    currentTrackRef.get()?.removeSink(proxySink)
                    currentTrackRef.get()?.removeSink(statsSink)
                } catch (_: Exception) {}
                currentTrackRef.set(null)
                // since we use a proxySink to instantly cut off frames,
                // the EGL render thread will NOT deadlock during surface destruction
                // we must release synchronously on the main thread, otherwise releasing
                // the EGL context on a background thread while surfaceDestroyed is called
                // on the main thread causes a fatal ANR race condition!
                try {
                    view.release()
                } catch (_: Exception) {}
            }
        )

        if (videoWidth > 0 && videoHeight > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${videoWidth}x${videoHeight} @ ${frameRate}fps",
                    color = SharkordTheme.colors.foregroundText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
