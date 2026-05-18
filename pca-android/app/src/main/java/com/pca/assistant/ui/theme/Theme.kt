package com.pca.assistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFD166),
    secondary = Color(0xFF9AB8FF),
    tertiary = Color(0xFFE07A5F),
    background = Color(0xFF0B0F14),
    surface = Color(0xFF111720),
    onPrimary = Color(0xFF1A1300),
    onBackground = Color(0xFFE6ECF2),
    onSurface = Color(0xFFE6ECF2),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF705500),
    secondary = Color(0xFF1F3A8A),
    background = Color(0xFFF7F7F2),
    surface = Color(0xFFFFFFFF),
)

private val Type = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun PcaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Type,
        content = content,
    )
}
