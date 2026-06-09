package com.sharkord.android.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

data class SharkordColors(
    val bgColor: Color = Color(0xFF1C1C1C),
    val cardColor: Color = Color(0xFF2B2B2B),
    val primaryText: Color = Color(0xFFE8E8E8),
    val foregroundText: Color = Color(0xFFFAFAFA),
    val accentColor: Color = Color(0xFFE8E8E8)
)

val LocalSharkordColors = compositionLocalOf { SharkordColors() }
