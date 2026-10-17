package com.sharkord.android.utils

import androidx.compose.ui.graphics.Color

object ColorUtils {
    /**
     * Normalizes a hex color to match the server's HEX_COLOR_REGEX:
     * `^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$`
     */
    fun normalizeHexColor(color: String?, defaultColor: String = "#FFFFFF"): String {
        if (color.isNullOrBlank()) return defaultColor
        val trimmed = color.trim().removePrefix("#")
        return when {
            trimmed.length == 3 && trimmed.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' } ->
                "#${trimmed.uppercase()}"
            trimmed.length == 6 && trimmed.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' } ->
                "#${trimmed.uppercase()}"
            trimmed.length == 8 && trimmed.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' } ->
                "#${trimmed.substring(2).uppercase()}" // strip alpha (ARGB)
            else -> defaultColor
        }
    }

    /**
     * Safely parses a hex color string into a Compose Color, returning null on failure.
     */
    fun parseColorOrNull(colorHex: String?): Color? {
        if (colorHex.isNullOrBlank()) return null
        val clean = colorHex.trim()
        val hex = if (clean.startsWith("#")) clean else "#$clean"
        return try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (_: Exception) {
            null
        }
    }
}
