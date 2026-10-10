package com.lyrics.floating.domain.translation

/**
 * Encapsulates a single line of lyrics with optional phonetic and translated counterparts.
 */
data class DualLanguageLyricLine(
    val timestampMs: Long,
    val originalText: String,
    val phoneticText: String? = null,    // e.g. Romaji for Japanese, Pinyin for Chinese, Hangul romanization
    val translatedText: String? = null,  // Translated into user's target language (e.g. English)
    val isExplicitOrInstrumental: Boolean = false
) {
    /**
     * Determines whether there is secondary text to display.
     */
    fun hasSecondaryLine(showTranslation: Boolean, showRomanization: Boolean): Boolean {
        return (showTranslation && !translatedText.isNullOrBlank()) ||
               (showRomanization && !phoneticText.isNullOrBlank())
    }

    /**
     * Retrieves the preferred secondary line text based on user settings.
     */
    fun getSecondaryDisplay(showTranslation: Boolean, showRomanization: Boolean): String? {
        return when {
            showTranslation && !translatedText.isNullOrBlank() -> translatedText
            showRomanization && !phoneticText.isNullOrBlank() -> phoneticText
            else -> null
        }
    }
}
