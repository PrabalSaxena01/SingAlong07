package com.singalong07.app.data.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class LyricsAggregatorRepository(
    private val sources: List<LyricsSource> = listOf(
        LrclibSource(),
        JioSaavnSource(),
        NeteaseSource(),
        KugouSource(),
        LyricsOvhSource()
    )
) {
    private val memoryCache = ConcurrentHashMap<String, LyricsResult>()

    fun getAllSources(): List<LyricsSource> = sources

    suspend fun getLyricsCascading(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Long? = null,
        preferredSourceId: String? = null
    ): Result<LyricsResult> = withContext(Dispatchers.IO) {
        val cacheKey = makeCacheKey(artist, title, preferredSourceId)
        val cached = memoryCache[cacheKey]
        if (cached != null) {
            return@withContext Result.success(cached)
        }

        // If user has a preferred source, prioritize it first
        val sortedSources = if (!preferredSourceId.isNullOrBlank() && preferredSourceId != "auto") {
            sources.sortedByDescending { it.id == preferredSourceId }
        } else {
            sources
        }

        var plainFallbackResult: LyricsResult? = null

        for (source in sortedSources) {
            val result = source.fetchLyrics(title, artist, album, durationSeconds)
            if (result.isSuccess) {
                val lyricsResult = result.getOrThrow()

                if (!lyricsResult.syncedLrc.isNullOrBlank()) {
                    memoryCache[cacheKey] = lyricsResult
                    return@withContext Result.success(lyricsResult)
                }

                if (plainFallbackResult == null && !lyricsResult.plainLyrics.isNullOrBlank()) {
                    plainFallbackResult = lyricsResult
                }
            }
        }

        if (plainFallbackResult != null) {
            memoryCache[cacheKey] = plainFallbackResult
            return@withContext Result.success(plainFallbackResult)
        }

        Result.failure(NoSuchElementException("No lyrics found across ${sources.size} providers for $artist - $title"))
    }

    suspend fun fetchFromSpecificSource(
        sourceId: String,
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Long? = null
    ): Result<LyricsResult> = withContext(Dispatchers.IO) {
        val targetSource = sources.firstOrNull { it.id == sourceId }
            ?: return@withContext Result.failure(IllegalArgumentException("Unknown source id: $sourceId"))

        val result = targetSource.fetchLyrics(title, artist, album, durationSeconds)
        if (result.isSuccess) {
            val res = result.getOrThrow()
            val cacheKey = makeCacheKey(artist, title, sourceId)
            memoryCache[cacheKey] = res
        }
        result
    }

    suspend fun queryAllSourcesConcurrently(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Long? = null
    ): List<Pair<LyricsSource, Result<LyricsResult>>> = withContext(Dispatchers.IO) {
        sources.map { source ->
            async {
                val res = source.fetchLyrics(title, artist, album, durationSeconds)
                Pair(source, res)
            }
        }.awaitAll()
    }

    private fun makeCacheKey(artist: String, title: String, sourceId: String? = null): String {
        val base = "${artist.trim().lowercase()}__${title.trim().lowercase()}"
        return if (sourceId != null && sourceId != "auto") "${base}__${sourceId}" else base
    }
}
