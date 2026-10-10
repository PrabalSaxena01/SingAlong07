package com.lyrics.floating.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Available theme styles for the floating overlay.
 */
enum class OverlayThemeMode {
    DYNAMIC_PALETTE,  // Extracted dynamically from current album art
    AMOLED_DARK,      // Deep black #000000 with crisp white text
    FROSTED_GLASS,    // Translucent blurred background with soft glow
    NEON_CYBERPUNK,   // High-contrast cyan/magenta styling
    PAPER_LIGHT       // Soft high-contrast light mode
}

/**
 * Typography scale presets for lyrics rendering.
 */
enum class LyricFontScale(val primarySize: TextUnit, val subTextSize: TextUnit, val lineHeight: TextUnit) {
    COMPACT(primarySize = 13.sp, subTextSize = 11.sp, lineHeight = 18.sp),
    BALANCED(primarySize = 16.sp, subTextSize = 13.sp, lineHeight = 22.sp),
    COMFORTABLE(primarySize = 20.sp, subTextSize = 15.sp, lineHeight = 28.sp),
    JUMBO(primarySize = 25.sp, subTextSize = 18.sp, lineHeight = 34.sp)
}

/**
 * Runtime color palette applied to the floating overlay window.
 */
data class OverlayColorScheme(
    val backgroundColor: Color,
    val surfaceColor: Color,
    val activeLineColor: Color,
    val inactiveLineColor: Color,
    val translationColor: Color,
    val accentColor: Color,
    val glowColor: Color? = null,
    val backgroundAlpha: Float = 0.85f
)

object OverlayThemes {
    val AmoledDark = OverlayColorScheme(
        backgroundColor = Color(0xFF000000),
        surfaceColor = Color(0xFF121212),
        activeLineColor = Color(0xFFFFFFFF),
        inactiveLineColor = Color(0xFF6E6E6E),
        translationColor = Color(0xFF9E9E9E),
        accentColor = Color(0xFF1DB954), // Spotify Green accent
        glowColor = null,
        backgroundAlpha = 0.92f
    )

    val FrostedGlass = OverlayColorScheme(
        backgroundColor = Color(0xFF1E2129),
        surfaceColor = Color(0xFF2C3240),
        activeLineColor = Color(0xFFFFFFFF),
        inactiveLineColor = Color(0xFF8C93A4),
        translationColor = Color(0xFFB0B7C6),
        accentColor = Color(0xFF4E95FF),
        glowColor = Color(0x334E95FF),
        backgroundAlpha = 0.65f
    )

    val NeonCyberpunk = OverlayColorScheme(
        backgroundColor = Color(0xFF0A0017),
        surfaceColor = Color(0xFF1A0033),
        activeLineColor = Color(0xFF00FFFF), // Electric Cyan
        inactiveLineColor = Color(0xFF704595),
        translationColor = Color(0xFFFF007F), // Neon Pink
        accentColor = Color(0xFF00FF99),
        glowColor = Color(0x6600FFFF),
        backgroundAlpha = 0.88f
    )

    val PaperLight = OverlayColorScheme(
        backgroundColor = Color(0xFFF7F8FA),
        surfaceColor = Color(0xFFFFFFFF),
        activeLineColor = Color(0xFF1A1A1A),
        inactiveLineColor = Color(0xFF8A8F99),
        translationColor = Color(0xFF5A606E),
        accentColor = Color(0xFF0066FF),
        glowColor = null,
        backgroundAlpha = 0.90f
    )

    /**
     * Builds a dynamic color scheme derived from extracted Android Palette swatch colors.
     */
    fun createDynamicPalette(
        dominantColor: Color,
        vibrantColor: Color,
        darkMutedColor: Color,
        alpha: Float = 0.80f
    ): OverlayColorScheme {
        return OverlayColorScheme(
            backgroundColor = darkMutedColor,
            surfaceColor = darkMutedColor.copy(alpha = 0.7f),
            activeLineColor = Color.White,
            inactiveLineColor = Color.White.copy(alpha = 0.45f),
            translationColor = vibrantColor.copy(alpha = 0.85f),
            accentColor = vibrantColor,
            glowColor = vibrantColor.copy(alpha = 0.35f),
            backgroundAlpha = alpha
        )
    }
}
