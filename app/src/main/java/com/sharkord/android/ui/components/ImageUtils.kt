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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import android.util.LruCache

// shared image loading composables
// extracted from LoginScreen and HomeScreen to fix the cross-package
// dependency where HomeScreen imported from the login package

// represents a cached image with decoded bitmap and fetch timestamp
data class ImageCacheEntry(
    val bitmap: Bitmap
)

// centralized thread-safe image loader manager that provides in-memory caching,
// request deduplication, and rate-limiting to avoid redundant network requests
object ImageCacheManager {
    private const val TAG = "ImageCacheManager"
    
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = maxMemoryKb / 4

    private val cache = object : LruCache<String, ImageCacheEntry>(cacheSizeKb) {
        override fun sizeOf(key: String, value: ImageCacheEntry): Int {
            return value.bitmap.byteCount / 1024
        }
    }
    private val inFlight = ConcurrentHashMap<String, Deferred<ImageCacheEntry?>>()
    private val mutex = Mutex()

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private var hasTrimmedDiskCache = false

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    internal fun getDiskCacheFile(url: String): java.io.File {
        // use a simple hash to generate a safe filename
        val fileName = url.hashCode().toString() + ".img_cache"
        val cacheDir = java.io.File(SharkordClient.applicationContext.cacheDir, "images")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        return java.io.File(cacheDir, fileName)
    }



    // loads an image from the network or returns a cached one if it was loaded recently
    suspend fun loadImage(url: String): ImageCacheEntry? {
        if (url.isBlank() || !url.startsWith("http", ignoreCase = true)) return null

        if (!hasTrimmedDiskCache) {
            hasTrimmedDiskCache = true
            com.sharkord.android.utils.DiskCacheManager.trim(SharkordClient.applicationContext)
        }

        val cached = cache.get(url)
        if (cached != null) {
            return cached
        }

        // de-duplicate in-flight requests for the exact same URL
        val deferred = mutex.withLock {
            inFlight[url] ?: scope.async {
                try {
                    val diskFile = getDiskCacheFile(url)
                    if (diskFile.exists() && diskFile.length() > 0) {
                        // update last modified time to keep it fresh for LRU disk eviction
                        diskFile.setLastModified(System.currentTimeMillis())
                        
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(diskFile.absolutePath, options)
                        options.inSampleSize = calculateInSampleSize(options, 400, 400)
                        options.inJustDecodeBounds = false
                        val bmp = BitmapFactory.decodeFile(diskFile.absolutePath, options)
                        if (bmp != null) {
                            Log.d(TAG, "Loaded image from disk cache: $url")
                            val entry = ImageCacheEntry(bitmap = bmp)
                            cache.put(url, entry)
                            return@async entry
                        } else {
                            diskFile.delete() // Invalid cache file, delete it
                        }
                    }

                    Log.d(TAG, "Requesting image download from: $url")
                    val tempFile = java.io.File(diskFile.absolutePath + ".tmp")
                    val success = com.sharkord.android.data.network.ParallelDownloader.downloadFile(SharkordClient.okHttpClient, url, tempFile)
                    
                    if (success) {
                        // Store the full size image too so we don't need to re-download it
                        val fullDiskFile = getDiskCacheFile("full_$url")
                        try {
                            if (!fullDiskFile.exists()) {
                                tempFile.copyTo(fullDiskFile, overwrite = true)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to copy tempFile to fullDiskFile: ${e.message}")
                        }

                        val bmp = processAndSaveImage(diskFile, tempFile, null, 400, 100)
                        tempFile.delete()
                        
                        if (bmp != null) {
                            Log.d(TAG, "Successfully downloaded and processed image from $url")
                            val entry = ImageCacheEntry(bitmap = bmp)
                            cache.put(url, entry)
                            entry
                        } else {
                            Log.e(TAG, "Failed to decode/process bitmap from downloaded file for $url")
                            null
                        }
                    } else {
                        Log.e(TAG, "Failed to download image $url")
                        tempFile.delete()
                        null
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e(TAG, "Exception thrown while loading image from $url: ${e.message}", e)
                    null
                } finally {
                    mutex.withLock {
                        inFlight.remove(url)
                    }
                }
            }.also { inFlight[url] = it }
        }

        return try {
            val result = deferred.await()
            result ?: cache.get(url)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            cache.get(url)
        }
    }

    // extracts and caches a thumbnail frame from a remote video
    suspend fun loadVideoThumbnail(videoUrl: String): Bitmap? {
        if (videoUrl.isBlank() || !videoUrl.startsWith("http", ignoreCase = true)) return null
        
        val cached = cache.get(videoUrl)
        if (cached != null) {
            return cached.bitmap
        }
        
        return withContext(Dispatchers.IO) {
            val diskFile = getDiskCacheFile(videoUrl)
            if (diskFile.exists() && diskFile.length() > 0) {
                diskFile.setLastModified(System.currentTimeMillis())
                val bmp = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bmp != null) {
                    val entry = ImageCacheEntry(bmp)
                    cache.put(videoUrl, entry)
                    return@withContext bmp
                } else {
                    diskFile.delete()
                }
            }

            var retriever: android.media.MediaMetadataRetriever? = null
            var tempFile: java.io.File? = null
            try {
                Log.d(TAG, "Retrieving video thumbnail from: $videoUrl")
                
                retriever = android.media.MediaMetadataRetriever()
                var bmp: Bitmap? = null
                
                // 1. Check if we already have the full video downloaded in the cache
                val fileName = videoUrl.substringAfterLast("/")
                val appCacheDir = SharkordClient.applicationContext.cacheDir
                val localFullVideo = appCacheDir.listFiles()?.find { it.name.startsWith("shared_${fileName}_") }
                
                if (localFullVideo != null && localFullVideo.exists() && localFullVideo.length() > 0) {
                    try {
                        retriever.setDataSource(localFullVideo.absolutePath)
                        bmp = retriever.frameAtTime ?: retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        if (bmp != null) Log.d(TAG, "Generated thumbnail from fully cached video file")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to read cached full video file", e)
                    }
                }
                
                // 2. If no full file (or failed), try partial download
                if (bmp == null) {
                    tempFile = java.io.File(diskFile.absolutePath + ".tmp")
                    val success = com.sharkord.android.data.network.ParallelDownloader.downloadPartial(
                        SharkordClient.okHttpClient, videoUrl, tempFile, 5 * 1024 * 1024
                    )
                    
                    if (success && tempFile.exists()) {
                        try {
                            retriever.release()
                            retriever = android.media.MediaMetadataRetriever()
                            retriever.setDataSource(tempFile.absolutePath)
                            bmp = retriever.frameAtTime ?: retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to read partial file", e)
                        }
                    }
                }

                // 3. If partial download also fails, fallback to network stream
                if (bmp == null) {
                    try {
                        // The partial file may be missing the moov atom if it's at the end of the file.
                        // Fallback to letting MediaMetadataRetriever handle the network stream directly.
                        retriever.release()
                        retriever = android.media.MediaMetadataRetriever()
                        val safeVideoUrl = videoUrl.replace(" ", "%20")
                        retriever.setDataSource(safeVideoUrl, HashMap<String, String>())
                        bmp = retriever.frameAtTime ?: retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to retrieve frame from network URL: ${e.message}")
                    }
                }
                if (bmp != null) {
                    val finalBmp = processAndSaveImage(diskFile, null, bmp, 400, 100)
                    if (finalBmp != null) {
                        val entry = ImageCacheEntry(finalBmp)
                        cache.put(videoUrl, entry)
                        finalBmp
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Failed to retrieve video thumbnail for $videoUrl: ${e.message}")
                null
            } finally {
                try {
                    retriever?.release()
                } catch (_: Exception) {}
                tempFile?.delete()
            }
        }
    }

    // retrieves an image from the cache synchronously, avoiding loading states if it already exists
    fun getCachedImage(url: String): ImageCacheEntry? {
        return cache.get(url)
    }

    // clears the cache
    fun clearCache() {
        cache.evictAll()
        inFlight.clear()
    }

    suspend fun loadFullImage(url: String): ImageCacheEntry? {
        if (url.isBlank() || !url.startsWith("http", ignoreCase = true)) return null

        val fullUrl = "full_$url"
        val cached = cache.get(fullUrl)
        if (cached != null) return cached

        val deferred = mutex.withLock {
            inFlight[fullUrl] ?: scope.async {
                try {
                    val diskFile = getDiskCacheFile(fullUrl)
                    if (diskFile.exists() && diskFile.length() > 0) {
                        diskFile.setLastModified(System.currentTimeMillis())
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(diskFile.absolutePath, options)
                        options.inSampleSize = calculateInSampleSize(options, 2000, 2000)
                        options.inJustDecodeBounds = false
                        val bmp = BitmapFactory.decodeFile(diskFile.absolutePath, options)
                        if (bmp != null) {
                            val entry = ImageCacheEntry(bitmap = bmp)
                            cache.put(fullUrl, entry)
                            return@async entry
                        } else {
                            diskFile.delete()
                        }
                    }

                    val tempFile = java.io.File(diskFile.absolutePath + ".tmp")
                    val success = com.sharkord.android.data.network.ParallelDownloader.downloadFile(SharkordClient.okHttpClient, url, tempFile)
                    
                    if (success) {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(tempFile.absolutePath, options)
                        options.inSampleSize = calculateInSampleSize(options, 2000, 2000)
                        options.inJustDecodeBounds = false
                        val sourceBmp = BitmapFactory.decodeFile(tempFile.absolutePath, options)
                        
                        if (sourceBmp != null) {
                            try {
                                java.io.FileOutputStream(diskFile).use { out ->
                                    @Suppress("DEPRECATION")
                                    sourceBmp.compress(Bitmap.CompressFormat.WEBP, 85, out)
                                }
                            } catch (e: Exception) {}
                            tempFile.delete()
                            
                            val entry = ImageCacheEntry(bitmap = sourceBmp)
                            cache.put(fullUrl, entry)
                            entry
                        } else {
                            tempFile.delete()
                            null
                        }
                    } else {
                        tempFile.delete()
                        null
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    null
                } finally {
                    mutex.withLock {
                        inFlight.remove(fullUrl)
                    }
                }
            }.also { inFlight[fullUrl] = it }
        }

        return try {
            val result = deferred.await()
            result ?: cache.get(fullUrl)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            cache.get(fullUrl)
        }
    }

    private fun processAndSaveImage(
        diskFile: java.io.File,
        originalFile: java.io.File?,
        originalBitmap: Bitmap?,
        targetSizePx: Int = 400,
        maxKbSize: Int = 100
    ): Bitmap? {
        val sizeBytes = originalFile?.length() ?: 0L
        val needsCompression = originalFile == null || sizeBytes > maxKbSize * 1024

        var sourceBmp = originalBitmap
        var didDecode = false

        if (originalFile != null && sourceBmp == null) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(originalFile.absolutePath, options)
            
            val width = options.outWidth
            val height = options.outHeight
            val maxDim = maxOf(width, height)
            
            val needsResizing = maxDim > targetSizePx * 1.5f

            if (!needsCompression && !needsResizing) {
                try {
                    if (originalFile.absolutePath != diskFile.absolutePath) {
                        originalFile.copyTo(diskFile, overwrite = true)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e(TAG, "Failed to save raw image to disk: ${e.message}")
                }
                options.inSampleSize = calculateInSampleSize(options, targetSizePx, targetSizePx)
                options.inJustDecodeBounds = false
                return BitmapFactory.decodeFile(originalFile.absolutePath, options)
            }

            options.inSampleSize = calculateInSampleSize(options, targetSizePx, targetSizePx)
            options.inJustDecodeBounds = false
            sourceBmp = BitmapFactory.decodeFile(originalFile.absolutePath, options)
            didDecode = true
        }

        if (sourceBmp == null) return null

        val size = minOf(sourceBmp.width, sourceBmp.height)
        val xOffset = (sourceBmp.width - size) / 2
        val yOffset = (sourceBmp.height - size) / 2
        
        var squaredBmp = sourceBmp
        if (sourceBmp.width != sourceBmp.height) {
            squaredBmp = Bitmap.createBitmap(sourceBmp, xOffset, yOffset, size, size)
            if (sourceBmp != squaredBmp && (didDecode || originalBitmap != null)) {
                sourceBmp.recycle()
            }
        }

        var finalBmp = squaredBmp
        if (squaredBmp.width > targetSizePx) {
            finalBmp = Bitmap.createScaledBitmap(squaredBmp, targetSizePx, targetSizePx, true)
            if (squaredBmp != finalBmp) {
                squaredBmp.recycle()
            }
        }

        if (originalFile == null || needsCompression || squaredBmp != sourceBmp || finalBmp != squaredBmp) {
            try {
                java.io.FileOutputStream(diskFile).use { out ->
                    @Suppress("DEPRECATION")
                    finalBmp.compress(Bitmap.CompressFormat.WEBP, 65, out)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Failed to compress image to disk: ${e.message}")
            }
        }

        return finalBmp
    }
}

// three-state result for async image loading
sealed class AsyncImageState {
    object Loading : AsyncImageState()
    data class Success(val painter: Painter) : AsyncImageState()
    object Failure : AsyncImageState()
    object Empty : AsyncImageState() // url was null/blank
}

@Composable
fun rememberFullImageState(url: String?): AsyncImageState {
    if (url.isNullOrBlank()) return AsyncImageState.Empty

    val fullUrl = "full_$url"
    val cached = ImageCacheManager.getCachedImage(fullUrl)
    var state by remember(url) { 
        mutableStateOf<AsyncImageState>(
            if (cached != null) AsyncImageState.Success(BitmapPainter(cached.bitmap.asImageBitmap()))
            else AsyncImageState.Loading
        ) 
    }

    LaunchedEffect(url) {
        if (cached == null) {
            val entry = ImageCacheManager.loadFullImage(url)
            state = if (entry != null) {
                AsyncImageState.Success(BitmapPainter(entry.bitmap.asImageBitmap()))
            } else {
                AsyncImageState.Failure
            }
        }
    }

    return state
}

// loads an image from a URL and returns a tri-state [AsyncImageState]
// - [AsyncImageState.Empty]   — url was null / blank
// - [AsyncImageState.Loading] — fetch in-flight
// - [AsyncImageState.Success] — bitmap ready
// - [AsyncImageState.Failure] — network / decode error
@Composable
fun rememberAsyncImageState(url: String?): AsyncImageState {
    if (url.isNullOrBlank()) return AsyncImageState.Empty

    val cached = ImageCacheManager.getCachedImage(url)
    var state by remember(url) { 
        mutableStateOf<AsyncImageState>(
            if (cached != null) AsyncImageState.Success(BitmapPainter(cached.bitmap.asImageBitmap()))
            else AsyncImageState.Loading
        ) 
    }

    LaunchedEffect(url) {
        if (cached == null) {
            val entry = ImageCacheManager.loadImage(url)
            state = if (entry != null) {
                AsyncImageState.Success(BitmapPainter(entry.bitmap.asImageBitmap()))
            } else {
                AsyncImageState.Failure
            }
        }
    }

    return state
}

// loads a video thumbnail from a URL and returns a tri-state [AsyncImageState]
@Composable
fun rememberVideoThumbnailState(url: String?): AsyncImageState {
    if (url.isNullOrBlank()) return AsyncImageState.Empty

    val cached = ImageCacheManager.getCachedImage(url)
    var state by remember(url) { 
        mutableStateOf<AsyncImageState>(
            if (cached != null) AsyncImageState.Success(BitmapPainter(cached.bitmap.asImageBitmap()))
            else AsyncImageState.Loading
        ) 
    }

    LaunchedEffect(url) {
        if (cached == null) {
            val bmp = ImageCacheManager.loadVideoThumbnail(url)
            state = if (bmp != null) {
                AsyncImageState.Success(BitmapPainter(bmp.asImageBitmap()))
            } else {
                AsyncImageState.Failure
            }
        }
    }

    return state
}

// loads an image from a URL asynchronously and returns a [Painter]
// - Returns **null** while loading OR when [url] is null/blank
// callers should render their own placeholder (spinner, initials, etc.)
// - Returns the [fallbackResourceId] painter only on a real load **failure**
// pass `null` for [fallbackResourceId] to get `null` on failure too
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

// extended image state that also extracts edge colors from the bitmap
// for dynamic gradient backgrounds (used in server banner)
data class ExtendedImageState(
    val painter: Painter?,
    val leftColor: Color,
    val rightColor: Color
)

// loads an image and extracts left/right edge pixel colors for gradient effects
@Composable
fun rememberExtendedImageState(url: String?): ExtendedImageState {
    if (url.isNullOrBlank()) {
        return ExtendedImageState(null, Color.Transparent, Color.Transparent)
    }

    val cached = ImageCacheManager.getCachedImage(url)

    var painter by remember(url) { 
        mutableStateOf<Painter?>(if (cached != null) BitmapPainter(cached.bitmap.asImageBitmap()) else null) 
    }
    var leftColor by remember(url) { 
        mutableStateOf(if (cached != null) Color(cached.bitmap.getPixel(0, cached.bitmap.height / 2)) else Color.Transparent) 
    }
    var rightColor by remember(url) { 
        mutableStateOf(if (cached != null) Color(cached.bitmap.getPixel(cached.bitmap.width - 1, cached.bitmap.height / 2)) else Color.Transparent) 
    }

    LaunchedEffect(url) {
        if (cached == null) {
            val entry = ImageCacheManager.loadImage(url)
            if (entry != null) {
                val bmp = entry.bitmap
                // extract edge colors on background thread
                withContext(Dispatchers.Default) {
                    val leftCol = bmp.getPixel(0, bmp.height / 2)
                    val rightCol = bmp.getPixel(bmp.width - 1, bmp.height / 2)
                    
                    leftColor = Color(leftCol)
                    rightColor = Color(rightCol)
                    painter = BitmapPainter(bmp.asImageBitmap())
                }
            }
        }
    }

    return ExtendedImageState(painter, leftColor, rightColor)
}
