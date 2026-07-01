package com.sharkord.android.data.session

import android.content.Context
import android.content.SharedPreferences

// centralizes all SharedPreferences access for authentication and session state
// replaces the scattered `context.getSharedPreferences("sharkord_prefs", ...)` calls
// that were previously in LoginViewModel, HomeScreen, and SharkordClient
class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val securePrefs: SharedPreferences by lazy {
        try {
            createEncryptedPrefs(context)
        } catch (e: Exception) {
            // Handle Keystore corruption (e.g. AEADBadTagException, KeyStoreException)
            context.deleteSharedPreferences("secret_biometric_prefs")
            try {
                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                keyStore.deleteEntry(androidx.security.crypto.MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            } catch (ignored: Exception) {
            }
            createEncryptedPrefs(context)
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = androidx.security.crypto.MasterKey.Builder(context.applicationContext)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        return androidx.security.crypto.EncryptedSharedPreferences.create(
            context.applicationContext,
            "secret_biometric_prefs",
            masterKey,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // read

    val token: String?
        get() = prefs.getString(KEY_TOKEN, null)

    val serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)

    val serverLogoUrl: String?
        get() = prefs.getString(KEY_SERVER_LOGO_URL, null)

    var autoLogin: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LOGIN, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_LOGIN, value).apply()

    var alwaysRequireBiometrics: Boolean
        get() = prefs.getBoolean(KEY_ALWAYS_REQUIRE_BIOMETRICS, false)
        set(value) = prefs.edit().putBoolean(KEY_ALWAYS_REQUIRE_BIOMETRICS, value).apply()

    // write

    // saves the current session after a successful login
    // if [autoLogin] is false, the token is NOT persisted (user must re-login next launch)
    fun saveSession(serverUrl: String, token: String, logoUrl: String?, autoLogin: Boolean) {
        prefs.edit().apply {
            putString(KEY_SERVER_URL, serverUrl)
            putBoolean(KEY_AUTO_LOGIN, autoLogin)
            putString(KEY_SERVER_LOGO_URL, logoUrl)
            if (autoLogin) {
                putString(KEY_TOKEN, token)
            } else {
                remove(KEY_TOKEN)
            }
            apply()
        }
    }

    // saves only the server URL (used when the user enters a URL but hasn't logged in yet)
    fun saveServerUrl(serverUrl: String) {
        prefs.edit().putString(KEY_SERVER_URL, serverUrl).apply()
    }

    // saves only the server logo URL
    fun saveServerLogoUrl(logoUrl: String?) {
        prefs.edit().putString(KEY_SERVER_LOGO_URL, logoUrl).apply()
    }

    // clears all session data. Used on logout
    fun clearSession() {
        prefs.edit().apply {
            remove(KEY_TOKEN)
            remove(KEY_SERVER_LOGO_URL)
            putBoolean(KEY_AUTO_LOGIN, false)
            apply()
        }
    }

    // biometric auth
    fun saveBiometricCredentials(identity: String, pass: String) {
        securePrefs.edit().apply {
            putString(KEY_BIO_IDENTITY, identity)
            putString(KEY_BIO_PASSWORD, pass)
            apply()
        }
    }

    fun getBiometricCredentials(): Pair<String, String>? {
        val identity = securePrefs.getString(KEY_BIO_IDENTITY, null)
        val pass = securePrefs.getString(KEY_BIO_PASSWORD, null)
        if (identity != null && pass != null) {
            return Pair(identity, pass)
        }
        return null
    }

    fun hasBiometricCredentials(): Boolean {
        return securePrefs.contains(KEY_BIO_IDENTITY) && securePrefs.contains(KEY_BIO_PASSWORD)
    }

    fun clearBiometricCredentials() {
        securePrefs.edit().apply {
            remove(KEY_BIO_IDENTITY)
            remove(KEY_BIO_PASSWORD)
            apply()
        }
    }

    // returns true if there is a saved token and server URL that can be used for auto-login
    fun hasValidSession(): Boolean {
        return autoLogin && !token.isNullOrBlank() && !serverUrl.isNullOrBlank()
    }

    var maxDiskCacheMb: Int
        get() = prefs.getInt(KEY_MAX_DISK_CACHE_MB, 250)
        set(value) {
            prefs.edit().putInt(KEY_MAX_DISK_CACHE_MB, value).apply()
        }

    // devices Settings

    var defaultAudioRoute: String
        get() = prefs.getString(KEY_DEFAULT_AUDIO_ROUTE, "None") ?: "None"
        set(value) = prefs.edit().putString(KEY_DEFAULT_AUDIO_ROUTE, value).apply()

    var echoCancellation: Boolean
        get() = prefs.getBoolean(KEY_ECHO_CANCELLATION, true)
        set(value) = prefs.edit().putBoolean(KEY_ECHO_CANCELLATION, value).apply()

    var noiseSuppression: Boolean
        get() = prefs.getBoolean(KEY_NOISE_SUPPRESSION, true)
        set(value) = prefs.edit().putBoolean(KEY_NOISE_SUPPRESSION, value).apply()

    var autoGainControl: Boolean
        get() = prefs.getBoolean(KEY_AUTO_GAIN_CONTROL, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_GAIN_CONTROL, value).apply()

    var defaultCamera: String
        get() = prefs.getString(KEY_DEFAULT_CAMERA, "Front") ?: "Front"
        set(value) = prefs.edit().putString(KEY_DEFAULT_CAMERA, value).apply()

    var frontVideoResolution: String
        get() = prefs.getString(KEY_FRONT_VIDEO_RESOLUTION, "1280x720") ?: "1280x720"
        set(value) = prefs.edit().putString(KEY_FRONT_VIDEO_RESOLUTION, value).apply()

    var frontVideoFps: Int
        get() = prefs.getInt(KEY_FRONT_VIDEO_FPS, 30)
        set(value) = prefs.edit().putInt(KEY_FRONT_VIDEO_FPS, value).apply()

    var backVideoResolution: String
        get() = prefs.getString(KEY_BACK_VIDEO_RESOLUTION, "1280x720") ?: "1280x720"
        set(value) = prefs.edit().putString(KEY_BACK_VIDEO_RESOLUTION, value).apply()

    var backVideoFps: Int
        get() = prefs.getInt(KEY_BACK_VIDEO_FPS, 30)
        set(value) = prefs.edit().putInt(KEY_BACK_VIDEO_FPS, value).apply()

    var mirrorFrontCamera: Boolean
        get() = prefs.getBoolean(KEY_MIRROR_FRONT_CAMERA, true)
        set(value) = prefs.edit().putBoolean(KEY_MIRROR_FRONT_CAMERA, value).apply()

    var screenShareResolution: String
        get() = prefs.getString(KEY_SCREEN_SHARE_RESOLUTION, "1280x720") ?: "1280x720"
        set(value) = prefs.edit().putString(KEY_SCREEN_SHARE_RESOLUTION, value).apply()

    var screenShareFps: Int
        get() = prefs.getInt(KEY_SCREEN_SHARE_FPS, 30)
        set(value) = prefs.edit().putInt(KEY_SCREEN_SHARE_FPS, value).apply()

    fun getVideoCodec(supportedCodecs: List<String>): String {
        val saved = prefs.getString(KEY_VIDEO_CODEC, null)
        if (saved != null && supportedCodecs.contains(saved)) {
            return saved
        }
        
        // default compression to quality priority
        val preferenceOrder = listOf("AV1", "VP9", "H264", "VP8")
        for (pref in preferenceOrder) {
            if (supportedCodecs.contains(pref)) {
                return pref
            }
        }
        
        return supportedCodecs.firstOrNull() ?: "VP8"
    }

    fun setVideoCodec(value: String) {
        prefs.edit().putString(KEY_VIDEO_CODEC, value).apply()
    }

    var compressMedia: Boolean
        get() = prefs.getBoolean(KEY_COMPRESS_MEDIA, false)
        set(value) = prefs.edit().putBoolean(KEY_COMPRESS_MEDIA, value).apply()

    var mediaCodec: String
        get() = prefs.getString(KEY_MEDIA_CODEC, "H.264") ?: "H.264"
        set(value) = prefs.edit().putString(KEY_MEDIA_CODEC, value).apply()

    var mediaQuality: String
        get() = prefs.getString(KEY_MEDIA_QUALITY, "Medium") ?: "Medium"
        set(value) = prefs.edit().putString(KEY_MEDIA_QUALITY, value).apply()

    companion object {
        private const val PREFS_NAME = "sharkord_prefs"
        private const val KEY_TOKEN = "login_token"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_SERVER_LOGO_URL = "server_logo_url"
        private const val KEY_AUTO_LOGIN = "auto_login"
        private const val KEY_MAX_DISK_CACHE_MB = "max_disk_cache_mb"
        private const val KEY_BIO_IDENTITY = "bio_identity"
        private const val KEY_BIO_PASSWORD = "bio_password"
        private const val KEY_ALWAYS_REQUIRE_BIOMETRICS = "always_require_biometrics"

        // device settings keys
        private const val KEY_DEFAULT_AUDIO_ROUTE = "default_audio_route"
        private const val KEY_ECHO_CANCELLATION = "echo_cancellation"
        private const val KEY_NOISE_SUPPRESSION = "noise_suppression"
        private const val KEY_AUTO_GAIN_CONTROL = "auto_gain_control"
        private const val KEY_DEFAULT_CAMERA = "default_camera"
        private const val KEY_FRONT_VIDEO_RESOLUTION = "front_video_resolution"
        private const val KEY_FRONT_VIDEO_FPS = "front_video_fps"
        private const val KEY_BACK_VIDEO_RESOLUTION = "back_video_resolution"
        private const val KEY_BACK_VIDEO_FPS = "back_video_fps"
        private const val KEY_MIRROR_FRONT_CAMERA = "mirror_front_camera"
        private const val KEY_SCREEN_SHARE_RESOLUTION = "screen_share_resolution"
        private const val KEY_SCREEN_SHARE_FPS = "screen_share_fps"
        private const val KEY_VIDEO_CODEC = "video_codec"
        private const val KEY_COMPRESS_MEDIA = "compress_media"
        private const val KEY_MEDIA_CODEC = "app_media_codec"
        private const val KEY_MEDIA_QUALITY = "app_media_quality"
    }
}
