package com.pca.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFD166),
    secondary = Color(0xFF9AB8FF),
    tertiary = Color(0xFFE07A5F),
    // B-35: spec §7.4 — OLED battery savings. On AMOLED (S21 Ultra)
    // pure-black pixels are physically off; the previous dark-navy was
    // a few percent on every pixel for the entire app surface.
    background = Color(0xFF000000),
    // Cards / surfaces stay slightly elevated so the eye can distinguish
    // them from the background — still very dark, still mostly off.
    surface = Color(0xFF0C1014),
    onPrimary = Color(0xFF1A1300),
    onBackground = Color(0xFFE6ECF2),
    onSurface = Color(0xFFE6ECF2),
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
    // Spec §7.4 — "Dark theme by default (OLED battery savings)". Always
    // dark; we don't follow the system-light setting because the spec is
    // explicit about OLED savings on the target device (S21 Ultra has an
    // AMOLED display). Light-theme support can be added later via a
    // user-facing toggle in Settings if needed.
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Type,
        content = content,
    )
}
