package com.singalong07.app.domain.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

interface LyricTranslationProvider {
    suspend fun translateLines(
        lines: List<String>,
        sourceLang: String?,
        targetLang: String
    ): Result<List<String>>
}

class TranslationEngine(
    private val mlKitProvider: LyricTranslationProvider? = null,
    private val cloudProvider: LyricTranslationProvider? = null
) {
    private val translationCache = ConcurrentHashMap<String, String>()

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

        if (linesToTranslate.isEmpty()) {
            return@withContext originalLines.map { line ->
                val cached = translationCache["${line.originalText}_$targetLang"]
                if (cached != null) line.copy(translatedText = cached) else line
            }
        }

        val translatedBatches = translateBatchWithFallback(linesToTranslate, targetLang)
        translatedBatches.forEachIndexed { i, translatedText ->
            val originalText = linesToTranslate[i]
            translationCache["${originalText}_$targetLang"] = translatedText
        }

        originalLines.map { line ->
            val cached = translationCache["${line.originalText}_$targetLang"]
            if (cached != null) line.copy(translatedText = cached) else line
        }
    }

    private suspend fun translateBatchWithFallback(
        lines: List<String>,
        targetLang: String
    ): List<String> {
        if (mlKitProvider != null) {
            val result = mlKitProvider.translateLines(lines, null, targetLang)
            if (result.isSuccess) return result.getOrThrow()
        }

        if (cloudProvider != null) {
            val result = cloudProvider.translateLines(lines, null, targetLang)
            if (result.isSuccess) return result.getOrThrow()
        }

        return lines
    }
}
