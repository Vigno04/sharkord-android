package com.sharkord.android.data.session

import android.content.Context
import android.content.SharedPreferences

/**
 * Centralizes all SharedPreferences access for authentication and session state.
 * Replaces the scattered `context.getSharedPreferences("sharkord_prefs", ...)` calls
 * that were previously in LoginViewModel, HomeScreen, and SharkordClient.
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Read

    val token: String?
        get() = prefs.getString(KEY_TOKEN, null)

    val serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)

    val serverLogoUrl: String?
        get() = prefs.getString(KEY_SERVER_LOGO_URL, null)

    val autoLogin: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LOGIN, false)

    // Write

    /**
     * Saves the current session after a successful login.
     * If [autoLogin] is false, the token is NOT persisted (user must re-login next launch).
     */
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

    /**
     * Saves only the server URL (used when the user enters a URL but hasn't logged in yet).
     */
    fun saveServerUrl(serverUrl: String) {
        prefs.edit().putString(KEY_SERVER_URL, serverUrl).apply()
    }

    /**
     * Saves only the server logo URL.
     */
    fun saveServerLogoUrl(logoUrl: String?) {
        prefs.edit().putString(KEY_SERVER_LOGO_URL, logoUrl).apply()
    }

    /**
     * Clears all session data. Used on logout.
     */
    fun clearSession() {
        prefs.edit().apply {
            remove(KEY_TOKEN)
            remove(KEY_SERVER_LOGO_URL)
            putBoolean(KEY_AUTO_LOGIN, false)
            apply()
        }
    }

    /**
     * Returns true if there is a saved token and server URL that can be used for auto-login.
     */
    fun hasValidSession(): Boolean {
        return autoLogin && !token.isNullOrBlank() && !serverUrl.isNullOrBlank()
    }

    var maxDiskCacheMb: Int
        get() = prefs.getInt(KEY_MAX_DISK_CACHE_MB, 250)
        set(value) {
            prefs.edit().putInt(KEY_MAX_DISK_CACHE_MB, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "sharkord_prefs"
        private const val KEY_TOKEN = "login_token"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_SERVER_LOGO_URL = "server_logo_url"
        private const val KEY_AUTO_LOGIN = "auto_login"
        private const val KEY_MAX_DISK_CACHE_MB = "max_disk_cache_mb"
    }
}

