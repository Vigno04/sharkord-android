package com.sharkord.android.ui.home.components.chat

import com.sharkord.android.ui.theme.SharkordTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.sharkord.android.R
import com.sharkord.android.data.model.FileInfo
import com.sharkord.android.data.network.SharkordClient
import com.sharkord.android.ui.components.AsyncImageState
import com.sharkord.android.ui.components.rememberAsyncImageState
import com.sharkord.android.ui.home.components.CustomVideoPlayer

@Composable
fun MediaLightboxViewer(
    file: FileInfo,
    onClose: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extension = file.originalName?.substringAfterLast('.', "")?.lowercase() ?: ""
    val mimeType = file.mimeType?.lowercase() ?: ""
    val isImage = mimeType.startsWith("image/") || extension in listOf("png", "jpg", "jpeg", "webp", "gif")
    val isVideo = mimeType.startsWith("video/") || extension in listOf("mp4", "mkv", "mov", "webm", "avi")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
    ) {
        if (isImage) {
            val imageUrl = "${SharkordClient.currentServerUrl}/public/${file.name}"
            val imageState = rememberAsyncImageState(imageUrl)

            var scale by remember { mutableFloatStateOf(1f) }
            var offsetX by remember { mutableFloatStateOf(0f) }
            var offsetY by remember { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                when (imageState) {
                    is AsyncImageState.Success -> {
                        Image(
                            painter = imageState.painter,
                            contentDescription = file.displayName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                )
                        )
                    }
                    is AsyncImageState.Loading -> {
                        CircularProgressIndicator(color = SharkordTheme.colors.foregroundText)
                    }
                    else -> {
                        Text(text = stringResource(id = R.string.chat_failedLoadImage), color = SharkordTheme.colors.foregroundText)
                    }
                }
            }
        } else if (isVideo) {
            val videoUrl = "${SharkordClient.currentServerUrl}/public/${file.name}"
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CustomVideoPlayer(
                    videoUrl = videoUrl,
                    autoPlay = true,
                    isOverlayActive = false,
                    onFullscreenClick = onClose,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(id = R.string.chat_unsupportedMediaType), color = SharkordTheme.colors.foregroundText, fontSize = 16.sp)
            }
        }

        // top Controls
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.4f))
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = SharkordTheme.colors.foregroundText)
            }

            Text(
                text = file.displayName,
                color = SharkordTheme.colors.foregroundText,
                fontSize = 14.sp,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            )

            Row {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = SharkordTheme.colors.foregroundText)
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = SharkordTheme.colors.foregroundText)
                }
            }
        }
    }
}
