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
    modifier: Modifier = Modifier
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
            kotlinx.coroutines.delay(500)
            val w = atomicWidth.get()
            val h = atomicHeight.get()
            val fps = atomicFps.get()
            if (w != videoWidth) videoWidth = w
            if (h != videoHeight) videoHeight = h
            if (fps != frameRate) frameRate = fps
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
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                SurfaceViewRenderer(context).apply {
                    // MUST be false to prevent black screens and BLASTBufferQueue rejections!
                    // hardware scaling conflicts heavily with Compose's layout system.
                    setEnableHardwareScaler(false)
                    
                    viewRef.set(this)
                    init(eglBaseContext, null)
                    setScalingType(if (isZoomedOut) ScalingType.SCALE_ASPECT_FIT else ScalingType.SCALE_ASPECT_FILL)
                    
                    clipToOutline = false
                    outlineProvider = null


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
                view.clipToOutline = false
                view.outlineProvider = null
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
                // webRTC's VideoLayoutMeasure needs to calculate the exact aspect-ratio bounds
                // (shrunken for FIT, or expanded beyond the container for FILL)
                // to allow this, we relax the minimum constraints to 0 (AT_MOST)
                val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
                val placeable = measurable.measure(looseConstraints)
                // compose normally clamps a child's size to the parent constraints
                // we bypass this by reporting the exact measured width/height back to Compose!
                // if it expanded (FILL), Compose allows it to exceed the bounds, and
                // our parent Box(contentAlignment = Center) perfectly centers it
                // android WindowManager then natively clips the overflowing SurfaceView
                // if it shrank (FIT), the Box centers the smaller view, showing letterboxes
                layout(placeable.width, placeable.height) {
                    placeable.place(0, 0)
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
