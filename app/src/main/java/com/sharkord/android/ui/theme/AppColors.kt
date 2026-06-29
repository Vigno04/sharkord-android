package com.sharkord.android.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

data class SharkordColors(
    val bgColor: Color,
    val cardColor: Color,
    val primaryText: Color,
    val foregroundText: Color,
    val accentColor: Color,
    val dividerColor: Color,
    val isLight: Boolean = false
)

fun darkSharkordColors(): SharkordColors = SharkordColors(
    bgColor = Color(0xFF1C1C1C),
    cardColor = Color(0xFF2B2B2B),
    primaryText = Color(0xFFE8E8E8),
    foregroundText = Color(0xFFFAFAFA),
    accentColor = Color(0xFF5865F2),
    dividerColor = Color(0xFF2B2B2B)
)

// light theme equivalents
fun lightSharkordColors(): SharkordColors = SharkordColors(
    bgColor = Color(0xFFF4F5F7),
    cardColor = Color(0xFFFFFFFF),
    primaryText = Color(0xFF313338),
    foregroundText = Color(0xFF060607),
    accentColor = Color(0xFF5865F2),
    dividerColor = Color(0xFFE3E5E8),
    isLight = true
)

val LocalSharkordColors = compositionLocalOf { darkSharkordColors() }
