package com.sharkord.android.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.sharkord.android.R
import com.sharkord.android.data.network.SharkordClient
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared image loading composables.
 *
 * Extracted from LoginScreen and HomeScreen to fix the cross-package
 * dependency where HomeScreen imported from the login package.
 */

/**
 * Represents a cached image with decoded bitmap and fetch timestamp.
 */
data class ImageCacheEntry(
    val bitmap: Bitmap,
    val timestamp: Long
)

/**
 * Centralized thread-safe image loader manager that provides in-memory caching,
 * request deduplication, and rate-limiting to avoid redundant network requests.
 */
object ImageCacheManager {
    private val cache = ConcurrentHashMap<String, ImageCacheEntry>()
    private val inFlight = ConcurrentHashMap<String, Deferred<ImageCacheEntry?>>()
    private val mutex = Mutex()

    /**
     * Loads an image from the network or returns a cached one if it was loaded recently.
     */
    suspend fun loadImage(url: String): ImageCacheEntry? {
        if (url.isBlank()) return null

        val now = System.currentTimeMillis()
        val cached = cache[url]
        // If the image was successfully fetched less than 10 seconds ago, reuse it directly
        if (cached != null && (now - cached.timestamp) < 10000) {
            return cached
        }

        // De-duplicate in-flight requests for the exact same URL
        val deferred = coroutineScope {
            mutex.withLock {
                inFlight[url] ?: async(Dispatchers.IO) {
                    try {
                        val request = Request.Builder().url(url).build()
                        SharkordClient.okHttpClient.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val bytes = response.body?.bytes()
                                if (bytes != null) {
                                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    if (bmp != null) {
                                        val entry = ImageCacheEntry(
                                            bitmap = bmp,
                                            timestamp = System.currentTimeMillis()
                                        )
                                        cache[url] = entry
                                        entry
                                    } else null
                                } else null
                            } else null
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    } finally {
                        mutex.withLock {
                            inFlight.remove(url)
                        }
                    }
                }.also { inFlight[url] = it }
            }
        }

        val result = deferred.await()
        // Fallback to older cached entry if network fetch failed
        return result ?: cache[url]
    }
}

/**
 * Loads an image from a URL asynchronously and returns a [Painter].
 * If the image fails to load, is loading, or has an empty/null URL, it returns
 * the default fallbackResource painter (or null if fallbackResourceId is null).
 */
@Composable
fun rememberAsyncImagePainter(
    url: String?,
    fallbackResourceId: Int? = R.drawable.logo
): Painter? {
    if (url.isNullOrBlank()) {
        return fallbackResourceId?.let { painterResource(id = it) }
    }

    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var isFailed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        val entry = ImageCacheManager.loadImage(url)
        if (entry != null) {
            bitmap = entry.bitmap.asImageBitmap()
            isFailed = false
        } else {
            isFailed = true
        }
    }

    return when {
        bitmap != null -> BitmapPainter(bitmap!!)
        isFailed -> fallbackResourceId?.let { painterResource(id = it) }
        else -> fallbackResourceId?.let { painterResource(id = it) } // while loading
    }
}

/**
 * Extended image state that also extracts edge colors from the bitmap
 * for dynamic gradient backgrounds (used in server banner).
 */
data class ExtendedImageState(
    val painter: Painter?,
    val leftColor: Color,
    val rightColor: Color
)

/**
 * Loads an image and extracts left/right edge pixel colors for gradient effects.
 */
@Composable
fun rememberExtendedImageState(url: String?): ExtendedImageState {
    if (url.isNullOrBlank()) {
        return ExtendedImageState(null, Color.Transparent, Color.Transparent)
    }

    var painter by remember(url) { mutableStateOf<Painter?>(null) }
    var leftColor by remember(url) { mutableStateOf(Color.Transparent) }
    var rightColor by remember(url) { mutableStateOf(Color.Transparent) }

    LaunchedEffect(url) {
        val entry = ImageCacheManager.loadImage(url)
        if (entry != null) {
            val bmp = entry.bitmap
            // Extract edge colors on background thread
            withContext(Dispatchers.Default) {
                val leftCol = bmp.getPixel(0, bmp.height / 2)
                val rightCol = bmp.getPixel(bmp.width - 1, bmp.height / 2)
                
                leftColor = Color(leftCol)
                rightColor = Color(rightCol)
                painter = BitmapPainter(bmp.asImageBitmap())
            }
        }
    }

    return ExtendedImageState(painter, leftColor, rightColor)
}
