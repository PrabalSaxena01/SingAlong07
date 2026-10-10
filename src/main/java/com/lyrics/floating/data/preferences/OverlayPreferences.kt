package com.lyrics.floating.data.preferences

import com.lyrics.floating.ui.theme.LyricFontScale
import com.lyrics.floating.ui.theme.OverlayThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * User-configurable settings for the floating overlay.
 */
data class OverlaySettings(
    val themeMode: OverlayThemeMode = OverlayThemeMode.AMOLED_DARK,
    val fontScale: LyricFontScale = LyricFontScale.BALANCED,
    val backgroundOpacity: Float = 0.85f,
    val isTranslationEnabled: Boolean = true,
    val targetLanguageCode: String = "en",
    val isRomanizationEnabled: Boolean = true,
    val isClickThroughEnabled: Boolean = false,
    val autoMinimizeWhenIdle: Boolean = true
)

/**
 * In-memory & DataStore-backed preferences manager for overlay customization.
 */
class OverlayPreferencesManager {
    private val _settingsFlow = MutableStateFlow(OverlaySettings())
    val settingsFlow: StateFlow<OverlaySettings> = _settingsFlow.asStateFlow()

    fun updateTheme(themeMode: OverlayThemeMode) {
        _settingsFlow.update { it.copy(themeMode = themeMode) }
    }

    fun updateFontScale(fontScale: LyricFontScale) {
        _settingsFlow.update { it.copy(fontScale = fontScale) }
    }

    fun updateOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0.2f, 1.0f)
        _settingsFlow.update { it.copy(backgroundOpacity = clamped) }
    }

    fun toggleTranslation(enabled: Boolean) {
        _settingsFlow.update { it.copy(isTranslationEnabled = enabled) }
    }

    fun updateTargetLanguage(langCode: String) {
        _settingsFlow.update { it.copy(targetLanguageCode = langCode) }
    }

    fun toggleRomanization(enabled: Boolean) {
        _settingsFlow.update { it.copy(isRomanizationEnabled = enabled) }
    }

    fun toggleClickThrough(enabled: Boolean) {
        _settingsFlow.update { it.copy(isClickThroughEnabled = enabled) }
    }
}
