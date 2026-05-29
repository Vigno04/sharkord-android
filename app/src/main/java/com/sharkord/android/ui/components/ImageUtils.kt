package com.sharkord.android.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
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
    private const val TAG = "ImageCacheManager"
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
                        Log.d(TAG, "Requesting image download from: $url")
                        val request = Request.Builder().url(url).build()
                        SharkordClient.okHttpClient.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val bytes = response.body?.bytes()
                                if (bytes != null) {
                                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    if (bmp != null) {
                                        Log.d(TAG, "Successfully downloaded and decoded image from $url (${bytes.size} bytes)")
                                        val entry = ImageCacheEntry(
                                            bitmap = bmp,
                                            timestamp = System.currentTimeMillis()
                                        )
                                        cache[url] = entry
                                        entry
                                    } else {
                                        Log.e(TAG, "Failed to decode bitmap from downloaded bytes for $url")
                                        null
                                    }
                                } else {
                                    Log.e(TAG, "Downloaded response body was empty for $url")
                                    null
                                }
                            } else {
                                Log.e(TAG, "Server returned failure code ${response.code} for image $url")
                                null
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception thrown while loading image from $url: ${e.message}", e)
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
 * Three-state result for async image loading.
 */
sealed class AsyncImageState {
    object Loading : AsyncImageState()
    data class Success(val painter: Painter) : AsyncImageState()
    object Failure : AsyncImageState()
    object Empty : AsyncImageState() // url was null/blank
}

/**
 * Loads an image from a URL and returns a tri-state [AsyncImageState].
 *
 * - [AsyncImageState.Empty]   — url was null / blank
 * - [AsyncImageState.Loading] — fetch in-flight
 * - [AsyncImageState.Success] — bitmap ready
 * - [AsyncImageState.Failure] — network / decode error
 */
@Composable
fun rememberAsyncImageState(url: String?): AsyncImageState {
    if (url.isNullOrBlank()) return AsyncImageState.Empty

    var state by remember(url) { mutableStateOf<AsyncImageState>(AsyncImageState.Loading) }

    LaunchedEffect(url) {
        state = AsyncImageState.Loading
        val entry = ImageCacheManager.loadImage(url)
        state = if (entry != null) {
            AsyncImageState.Success(BitmapPainter(entry.bitmap.asImageBitmap()))
        } else {
            AsyncImageState.Failure
        }
    }

    return state
}

/**
 * Loads an image from a URL asynchronously and returns a [Painter].
 *
 * - Returns **null** while loading OR when [url] is null/blank.
 *   Callers should render their own placeholder (spinner, initials, etc.).
 * - Returns the [fallbackResourceId] painter only on a real load **failure**.
 *   Pass `null` for [fallbackResourceId] to get `null` on failure too.
 */
@Composable
fun rememberAsyncImagePainter(
    url: String?,
    fallbackResourceId: Int? = null
): Painter? {
    val imageState = rememberAsyncImageState(url)
    return when (imageState) {
        is AsyncImageState.Success -> imageState.painter
        is AsyncImageState.Failure -> fallbackResourceId?.let { painterResource(id = it) }
        is AsyncImageState.Loading -> null   // caller shows spinner
        is AsyncImageState.Empty   -> null   // caller shows initials / icon
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
