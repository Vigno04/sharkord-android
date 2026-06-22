package com.sharkord.android.data.session

import android.content.Context
import android.content.SharedPreferences

// centralizes all SharedPreferences access for authentication and session state
// replaces the scattered `context.getSharedPreferences("sharkord_prefs", ...)` calls
// that were previously in LoginViewModel, HomeScreen, and SharkordClient
class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // read

    val token: String?
        get() = prefs.getString(KEY_TOKEN, null)

    val serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)

    val serverLogoUrl: String?
        get() = prefs.getString(KEY_SERVER_LOGO_URL, null)

    val autoLogin: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LOGIN, false)

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

    var videoResolution: String
        get() = prefs.getString(KEY_VIDEO_RESOLUTION, "1280x720") ?: "1280x720"
        set(value) = prefs.edit().putString(KEY_VIDEO_RESOLUTION, value).apply()

    var videoFps: Int
        get() = prefs.getInt(KEY_VIDEO_FPS, 30)
        set(value) = prefs.edit().putInt(KEY_VIDEO_FPS, value).apply()

    var mirrorFrontCamera: Boolean
        get() = prefs.getBoolean(KEY_MIRROR_FRONT_CAMERA, true)
        set(value) = prefs.edit().putBoolean(KEY_MIRROR_FRONT_CAMERA, value).apply()

    var screenShareOptimizeFor: String
        get() = prefs.getString(KEY_SCREEN_SHARE_OPTIMIZE_FOR, "Quality") ?: "Quality"
        set(value) = prefs.edit().putString(KEY_SCREEN_SHARE_OPTIMIZE_FOR, value).apply()

    companion object {
        private const val PREFS_NAME = "sharkord_prefs"
        private const val KEY_TOKEN = "login_token"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_SERVER_LOGO_URL = "server_logo_url"
        private const val KEY_AUTO_LOGIN = "auto_login"
        private const val KEY_MAX_DISK_CACHE_MB = "max_disk_cache_mb"

        // device settings keys
        private const val KEY_DEFAULT_AUDIO_ROUTE = "default_audio_route"
        private const val KEY_ECHO_CANCELLATION = "echo_cancellation"
        private const val KEY_NOISE_SUPPRESSION = "noise_suppression"
        private const val KEY_AUTO_GAIN_CONTROL = "auto_gain_control"
        private const val KEY_DEFAULT_CAMERA = "default_camera"
        private const val KEY_VIDEO_RESOLUTION = "video_resolution"
        private const val KEY_VIDEO_FPS = "video_fps"
        private const val KEY_MIRROR_FRONT_CAMERA = "mirror_front_camera"
        private const val KEY_SCREEN_SHARE_OPTIMIZE_FOR = "screen_share_optimize_for"
    }
}
