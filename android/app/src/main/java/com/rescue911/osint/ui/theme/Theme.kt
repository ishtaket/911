package com.rescue911.osint.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = EmergencyRed,
    onPrimary = OnDark,
    primaryContainer = EmergencyRedDeep,
    onPrimaryContainer = OnDark,
    secondary = Gold,
    onSecondary = DeepNavy,
    secondaryContainer = GoldDeep,
    onSecondaryContainer = DeepNavy,
    tertiary = Gold,
    background = DeepNavy,
    onBackground = OnDark,
    surface = NavySurface,
    onSurface = OnDark,
    surfaceVariant = Steel,
    onSurfaceVariant = MutedText,
    error = Critical,
    onError = OnDark,
)

// Light scheme is provided as a fallback; default to dark.
private val LightColors = lightColorScheme(
    primary = EmergencyRedDeep,
    secondary = GoldDeep,
)

private val Rescue911Typography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
)

@Composable
fun Rescue911Theme(
    useDarkTheme: Boolean = true, // operational dark by default
    content: @Composable () -> Unit
) {
    val colors = if (useDarkTheme || isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = Rescue911Typography,
        content = content
    )
}
