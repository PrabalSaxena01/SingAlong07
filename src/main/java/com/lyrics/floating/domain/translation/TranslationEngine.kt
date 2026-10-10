package com.lyrics.floating.domain.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Interface for translation providers (e.g. On-Device ML Kit, DeepL, LibreTranslate).
 */
interface LyricTranslationProvider {
    suspend fun translateLines(
        lines: List<String>,
        sourceLang: String?,
        targetLang: String
    ): Result<List<String>>
}

/**
 * Orchestrates multi-layered translation resolution for synchronized lyrics.
 */
class TranslationEngine(
    private val mlKitProvider: LyricTranslationProvider? = null,
    private val cloudProvider: LyricTranslationProvider? = null
) {
    // In-memory memory cache keyed by: "lineText_targetLang"
    private val translationCache = ConcurrentHashMap<String, String>()

    /**
     * Enhances a list of parsed lines with translations.
     * If pre-synced translated LRC is already provided by the API (e.g. Netease tlyric),
     * it merges them directly. Otherwise, it dispatches to on-device ML Kit / cloud fallback.
     */
    suspend fun enhanceWithTranslations(
        originalLines: List<DualLanguageLyricLine>,
        preSyncedTranslations: Map<Long, String>?,
        targetLang: String
    ): List<DualLanguageLyricLine> = withContext(Dispatchers.Default) {
        if (originalLines.isEmpty()) return@withContext emptyList()

        // 1. Direct merge if API provided synchronized translated lines
        if (!preSyncedTranslations.isNullOrEmpty()) {
            return@withContext originalLines.map { line ->
                val matchingTranslation = preSyncedTranslations[line.timestampMs]
                    ?: preSyncedTranslations.entries.minByOrNull { Math.abs(it.key - line.timestampMs) }
                        ?.takeIf { Math.abs(it.key - line.timestampMs) <= 300 }?.value

                if (matchingTranslation != null) {
                    line.copy(translatedText = matchingTranslation)
                } else {
                    line
                }
            }
        }

        // 2. Check cache for untranslated lines
        val linesToTranslate = mutableListOf<String>()
        val lineIndices = mutableListOf<Int>()

        originalLines.forEachIndexed { index, line ->
            val cacheKey = "${line.originalText}_$targetLang"
            val cached = translationCache[cacheKey]
            if (cached == null && line.originalText.isNotBlank()) {
                linesToTranslate.add(line.originalText)
                lineIndices.add(index)
            }
        }

        // If everything is cached, return enriched list
        if (linesToTranslate.isEmpty()) {
            return@withContext originalLines.map { line ->
                val cached = translationCache["${line.originalText}_$targetLang"]
                if (cached != null) line.copy(translatedText = cached) else line
            }
        }

        // 3. Fallback Translation Pipeline: ML Kit On-Device -> Cloud Provider
        val translatedBatches = translateBatchWithFallback(linesToTranslate, targetLang)

        // Store back in cache
        translatedBatches.forEachIndexed { i, translatedText ->
            val originalText = linesToTranslate[i]
            translationCache["${originalText}_$targetLang"] = translatedText
        }

        // Reconstruct enriched lines
        originalLines.map { line ->
            val cached = translationCache["${line.originalText}_$targetLang"]
            if (cached != null) line.copy(translatedText = cached) else line
        }
    }

    private suspend fun translateBatchWithFallback(
        lines: List<String>,
        targetLang: String
    ): List<String> {
        // Attempt 1: On-Device ML Kit (fast, offline, free)
        if (mlKitProvider != null) {
            val result = mlKitProvider.translateLines(lines, null, targetLang)
            if (result.isSuccess) {
                return result.getOrThrow()
            }
        }

        // Attempt 2: Cloud Translation Provider (LibreTranslate / Google)
        if (cloudProvider != null) {
            val result = cloudProvider.translateLines(lines, null, targetLang)
            if (result.isSuccess) {
                return result.getOrThrow()
            }
        }

        // Fallback: Return original lines if translation fails gracefully
        return lines
    }
}
