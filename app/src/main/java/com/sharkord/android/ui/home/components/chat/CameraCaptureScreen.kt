package com.sharkord.android.ui.home.components.chat

import com.sharkord.android.ui.theme.SharkordTheme
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.VideoEncoderSettings
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.sharkord.android.data.network.SharkordClient

@Composable
fun CameraCaptureScreen(
    onPhotoCaptured: (String, Uri) -> Unit,
    onVideoCaptured: (String, Uri) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        hasPermissions = cameraGranted && audioGranted
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    if (hasPermissions) {
        CameraPreviewContent(onPhotoCaptured, onVideoCaptured, onClose)
    } else {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera and Audio permissions are required.", color = SharkordTheme.colors.foregroundText)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { 
                    permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                }) {
                    Text(stringResource(com.sharkord.android.R.string.common_grantPermissions))
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onClose) {
                    Text("Close", color = SharkordTheme.colors.primaryText.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewContent(
    onPhotoCaptured: (String, Uri) -> Unit,
    onVideoCaptured: (String, Uri) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var previewUseCase by remember { mutableStateOf<Preview?>(null) }
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCaptureUseCase by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var isCompressing by remember { mutableStateOf(false) }
    
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var cameraInfo by remember { mutableStateOf<CameraInfo?>(null) }
    
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    val previewView = remember { PreviewView(context) }

    val bindCamera = {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            previewUseCase = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCaptureUseCase = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCaptureUseCase = VideoCapture.withOutput(recorder)

            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    previewUseCase,
                    imageCaptureUseCase,
                    videoCaptureUseCase
                )
                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo
            } catch (exc: Exception) {
                Log.e("CameraCaptureScreen", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(lensFacing) {
        bindCamera()
    }

    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
                .pointerInput(cameraInfo, cameraControl) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (cameraInfo != null && cameraControl != null) {
                            val currentZoomRatio = cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                            val minZoomRatio = cameraInfo?.zoomState?.value?.minZoomRatio ?: 1f
                            val maxZoomRatio = cameraInfo?.zoomState?.value?.maxZoomRatio ?: 1f
                            val newZoomRatio = (currentZoomRatio * zoom).coerceIn(minZoomRatio, maxZoomRatio)
                            cameraControl?.setZoomRatio(newZoomRatio)
                        }
                    }
                }
                .pointerInput(cameraInfo, cameraControl) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (cameraInfo != null && cameraControl != null) {
                                val factory = previewView.meteringPointFactory
                                val point = factory.createPoint(offset.x, offset.y)
                                val action = FocusMeteringAction.Builder(point).build()
                                cameraControl?.startFocusAndMetering(action)
                            }
                        }
                    )
                }
        )

        // Close button
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).padding(top = 32.dp, start = 16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = SharkordTheme.colors.foregroundText)
        }

        // Flip Camera button
        IconButton(
            onClick = {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    CameraSelector.LENS_FACING_BACK
                } else {
                    CameraSelector.LENS_FACING_FRONT
                }
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 16.dp)
        ) {
            Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Flip Camera", tint = SharkordTheme.colors.foregroundText)
        }

        // Recording Indicator
        if (isRecording) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp)
                    .background(Color.Red.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("RECORDING", color = SharkordTheme.colors.foregroundText, fontWeight = FontWeight.Bold)
            }
        }

        // Capture Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .size(80.dp)
                .border(4.dp, Color.White, CircleShape)
                .padding(8.dp)
                .clip(CircleShape)
                .background(if (isRecording) Color.Transparent else Color.White.copy(alpha = 0.5f))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (isRecording) {
                                activeRecording?.stop()
                                activeRecording = null
                            } else {
                                // Take Photo
                                val imageCapture = imageCaptureUseCase ?: return@detectTapGestures
                                imageCapture.targetRotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
                                imageCapture.takePicture(
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                                            val rawBitmap = image.toBitmap()
                                            val rotationDegrees = image.imageInfo.rotationDegrees
                                            val matrix = android.graphics.Matrix()
                                            matrix.postRotate(rotationDegrees.toFloat())
                                            
                                            // mirror front camera
                                            if (lensFacing == androidx.camera.core.CameraSelector.LENS_FACING_FRONT) {
                                                matrix.postScale(-1f, 1f)
                                            }
                                            
                                            val rotatedBitmap = android.graphics.Bitmap.createBitmap(
                                                rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true
                                            )
                                            
                                            val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
                                            
                                            var quality = 95
                                            var compressFormat = android.graphics.Bitmap.CompressFormat.JPEG
                                            var extension = "jpg"
                                            
                                            if (SharkordClient.session.compressMedia) {
                                                @Suppress("DEPRECATION")
                                                compressFormat = android.graphics.Bitmap.CompressFormat.WEBP
                                                extension = "webp"
                                                quality = when (SharkordClient.session.mediaQuality) {
                                                    "High" -> 90
                                                    "Medium" -> 70
                                                    "Low" -> 50
                                                    else -> 70
                                                }
                                            }
                                            
                                            val file = File(context.cacheDir, "${name}.${extension}")
                                            val outputStream = java.io.FileOutputStream(file)
                                            
                                            rotatedBitmap.compress(compressFormat, quality, outputStream)
                                            outputStream.flush()
                                            outputStream.close()
                                            image.close()
                                            
                                            val savedUri = Uri.fromFile(file)
                                            onPhotoCaptured("${name}.${extension}", savedUri)
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            Log.e("CameraCaptureScreen", "Photo capture failed", exception)
                                        }
                                    }
                                )
                            }
                        },
                        onLongPress = {
                            if (!isRecording) {
                                // Start Video Recording
                                val videoCapture = videoCaptureUseCase ?: return@detectTapGestures
                                val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
                                val file = File(context.cacheDir, "${name}.mp4")
                                val outputOptions = FileOutputOptions.Builder(file).build()

                                val recording = videoCapture.output
                                    .prepareRecording(context, outputOptions)
                                    .apply {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                            withAudioEnabled()
                                        }
                                    }
                                    .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                                        when (recordEvent) {
                                            is VideoRecordEvent.Start -> {
                                                isRecording = true
                                            }
                                            is VideoRecordEvent.Finalize -> {
                                                isRecording = false
                                                if (!recordEvent.hasError()) {
                                                    if (SharkordClient.session.compressMedia) {
                                                        isCompressing = true
                                                        val compressedFile = File(context.cacheDir, "compressed_${name}.mp4")
                                                        val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
                                                        val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()
                                                        
                                                        val codecName = SharkordClient.session.mediaCodec
                                                        val mimeType = when (codecName) {
                                                            "AV1" -> MimeTypes.VIDEO_AV1
                                                            "HEVC" -> MimeTypes.VIDEO_H265
                                                            else -> MimeTypes.VIDEO_H264
                                                        }
                                                        
                                                        val targetBitrate = when (SharkordClient.session.mediaQuality) {
                                                            "High" -> 5_000_000 // 5 Mbps
                                                            "Medium" -> 2_500_000 // 2.5 Mbps
                                                            "Low" -> 1_000_000 // 1 Mbps
                                                            else -> 2_500_000
                                                        }
                                                        
                                                        val videoEncoderSettings = VideoEncoderSettings.Builder()
                                                            .setBitrate(targetBitrate)
                                                            .build()
                                                        
                                                        val encoderFactory = DefaultEncoderFactory.Builder(context)
                                                            .setRequestedVideoEncoderSettings(videoEncoderSettings)
                                                            .build()
                                                        
                                                        val transformer = Transformer.Builder(context)
                                                            .setVideoMimeType(mimeType)
                                                            .setEncoderFactory(encoderFactory)
                                                            .addListener(object : Transformer.Listener {
                                                                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                                                    isCompressing = false
                                                                    if (compressedFile.length() < file.length()) {
                                                                        onVideoCaptured("compressed_${name}.mp4", Uri.fromFile(compressedFile))
                                                                        file.delete()
                                                                    } else {
                                                                        compressedFile.delete()
                                                                        onVideoCaptured("${name}.mp4", Uri.fromFile(file))
                                                                    }
                                                                }
                                                                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                                                    isCompressing = false
                                                                    Log.e("CameraCaptureScreen", "Transformer error", exportException)
                                                                    onVideoCaptured("${name}.mp4", Uri.fromFile(file))
                                                                }
                                                            })
                                                            .build()
                                                        transformer.start(editedMediaItem, compressedFile.absolutePath)
                                                    } else {
                                                        val savedUri = Uri.fromFile(file)
                                                        onVideoCaptured("${name}.mp4", savedUri)
                                                    }
                                                } else {
                                                    Log.e("CameraCaptureScreen", "Video capture failed: ${recordEvent.error}")
                                                }
                                            }
                                        }
                                    }
                                activeRecording = recording
                            }
                        }
                    )
                }
        ) {
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Red)
                        .align(Alignment.Center)
                )
            }
        }
        
        Text(
            text = "Tap for photo, hold for video",
            color = SharkordTheme.colors.foregroundText,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        )
        
        if (isCompressing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .pointerInput(Unit) {
                        detectTapGestures { } // intercept and block taps
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = SharkordTheme.colors.foregroundText)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Compressing video...", color = SharkordTheme.colors.foregroundText)
                }
            }
        }
    }
}
