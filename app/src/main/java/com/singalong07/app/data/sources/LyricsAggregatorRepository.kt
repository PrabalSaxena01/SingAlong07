package com.singalong07.app.data.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class LyricsAggregatorRepository(
    private val sources: List<LyricsSource> = listOf(
        LrclibSource(),
        NeteaseSource()
    )
) {
    private val memoryCache = ConcurrentHashMap<String, LyricsResult>()

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
