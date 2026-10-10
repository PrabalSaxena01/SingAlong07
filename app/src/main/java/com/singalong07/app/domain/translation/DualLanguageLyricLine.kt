package com.singalong07.app.domain.translation

/**
 * Encapsulates a single line of lyrics with optional phonetic and translated counterparts.
 */
data class DualLanguageLyricLine(
    val timestampMs: Long,
    val originalText: String,
    val phoneticText: String? = null,    // e.g. Romaji for Japanese, Pinyin for Chinese
    val translatedText: String? = null,  // Translated into user's target language (e.g. English)
    val isExplicitOrInstrumental: Boolean = false
) {
    fun hasSecondaryLine(showTranslation: Boolean, showRomanization: Boolean): Boolean {
        return (showTranslation && !translatedText.isNullOrBlank()) ||
               (showRomanization && !phoneticText.isNullOrBlank())
    }

    fun getSecondaryDisplay(showTranslation: Boolean, showRomanization: Boolean): String? {
        return when {
            showTranslation && !translatedText.isNullOrBlank() -> translatedText
            showRomanization && !phoneticText.isNullOrBlank() -> phoneticText
            else -> null
        }
    }
}
