package com.lyrics.floating.data.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Aggregates multiple lyric sources with intelligent priority cascading,
 * caching, and multi-source alternative querying.
 */
class LyricsAggregatorRepository(
    private val sources: List<LyricsSource> = listOf(
        LrclibSource(),
        NeteaseSource()
    )
) {
    // In-memory cache keyed by "artist_title" hash
    private val memoryCache = ConcurrentHashMap<String, LyricsResult>()

    /**
     * Cascading search: Tries sources sequentially in order of user priority.
     * Stops at the first source that returns valid synchronized lyrics.
     */
    suspend fun getLyricsCascading(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Long? = null
    ): Result<LyricsResult> = withContext(Dispatchers.IO) {
        val cacheKey = makeCacheKey(artist, title)
        val cached = memoryCache[cacheKey]
        if (cached != null) {
            return@withContext Result.success(cached)
        }

        var plainFallbackResult: LyricsResult? = null

        for (source in sources) {
            val result = source.fetchLyrics(title, artist, album, durationSeconds)
            if (result.isSuccess) {
                val lyricsResult = result.getOrThrow()

                // If source has synchronized LRC, take it immediately!
                if (!lyricsResult.syncedLrc.isNullOrBlank()) {
                    memoryCache[cacheKey] = lyricsResult
                    return@withContext Result.success(lyricsResult)
                }

                // If source only has plain lyrics, save as fallback while checking remaining sources for synced
                if (plainFallbackResult == null && !lyricsResult.plainLyrics.isNullOrBlank()) {
                    plainFallbackResult = lyricsResult
                }
            }
        }

        // Return plain fallback if no synced source matched
        if (plainFallbackResult != null) {
            memoryCache[cacheKey] = plainFallbackResult
            return@withContext Result.success(plainFallbackResult)
        }

        Result.failure(NoSuchElementException("No lyrics found across ${sources.size} providers for $artist - $title"))
    }

    /**
     * Parallel search across all providers to let the user manually pick or compare
     * lyrics from alternative sources.
     */
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

    private fun makeCacheKey(artist: String, title: String): String {
        return "${artist.trim().lowercase()}__${title.trim().lowercase()}"
    }
}
