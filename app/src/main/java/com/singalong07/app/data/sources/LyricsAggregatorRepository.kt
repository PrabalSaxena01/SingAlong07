package com.singalong07.app.data.sources

import com.singalong07.app.data.db.LyricsCacheDao
import com.singalong07.app.data.db.LyricsCacheEntity
import com.singalong07.app.domain.cleaner.TrackMetadataCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

class LyricsAggregatorRepository(
    private val sources: List<LyricsSource> = listOf(
        LrclibSource(),
        JioSaavnSource(),
        YouTubeMusicSource(),
        GeniusSource(),
        NeteaseSource(),
        KugouSource(),
        LyricsOvhSource()
    ),
    private val lyricsCacheDao: LyricsCacheDao? = null
) {
    // Fast memory cache (capped at 250 items) for instant UI responses
    private val memoryCache: MutableMap<String, LyricsResult> = java.util.Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, LyricsResult>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LyricsResult>?): Boolean {
                return size > 250
            }
        }
    )

    fun getAllSources(): List<LyricsSource> = sources

    fun getCachedLyricsCount(): Int = memoryCache.size

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        memoryCache.clear()
        lyricsCacheDao?.clearAllCache()
    }

    suspend fun getLyricsCascading(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Long? = null,
        preferredSourceId: String? = null
    ): Result<LyricsResult> = withContext(Dispatchers.IO) {
        val cleaned = TrackMetadataCleaner.clean(title, artist)
        val trackHash = "${cleaned.artist.lowercase()}__${cleaned.title.lowercase()}"
        val cacheKey = makeCacheKey(cleaned.artist, cleaned.title, preferredSourceId)

        // 1. Check in-memory LRU cache
        val memCached = memoryCache[cacheKey]
        if (memCached != null) {
            return@withContext Result.success(memCached)
        }

        // 2. Check persistent Room DB cache (stores cleaned song name, source, sync values, lyrics)
        val persistent = lyricsCacheDao?.getCachedLyrics(trackHash)
        if (persistent != null && (preferredSourceId.isNullOrBlank() || preferredSourceId == "auto" || persistent.sourceName.equals(preferredSourceId, ignoreCase = true))) {
            val cachedResult = LyricsResult(
                sourceName = persistent.sourceName,
                trackTitle = persistent.cleanedTitle,
                artistName = persistent.cleanedArtist,
                syncedLrc = persistent.syncedLrc,
                plainLyrics = persistent.plainLyrics,
                isWordByWord = persistent.isWordByWord
            )
            memoryCache[cacheKey] = cachedResult
            return@withContext Result.success(cachedResult)
        }

        // 3. If user explicitly picked a preferred source, query it first.
        if (!preferredSourceId.isNullOrBlank() && preferredSourceId != "auto") {
            val preferred = sources.firstOrNull { it.id == preferredSourceId }
            if (preferred != null) {
                val result = preferred.fetchLyrics(cleaned.title, cleaned.artist, album, durationSeconds)
                if (result.isSuccess) {
                    val lyricsResult = result.getOrThrow()
                    if (!lyricsResult.syncedLrc.isNullOrBlank() || !lyricsResult.plainLyrics.isNullOrBlank()) {
                        memoryCache[cacheKey] = lyricsResult
                        persistToRoom(trackHash, cleaned.title, cleaned.artist, lyricsResult, durationSeconds)
                        return@withContext Result.success(lyricsResult)
                    }
                }
            }
        }

        // 4. Cascade across available sources
        var plainFallbackResult: LyricsResult? = null

        for (source in sources) {
            val result = source.fetchLyrics(cleaned.title, cleaned.artist, album, durationSeconds)
            if (result.isSuccess) {
                val lyricsResult = result.getOrThrow()

                if (!lyricsResult.syncedLrc.isNullOrBlank()) {
                    memoryCache[cacheKey] = lyricsResult
                    persistToRoom(trackHash, cleaned.title, cleaned.artist, lyricsResult, durationSeconds)
                    return@withContext Result.success(lyricsResult)
                }

                if (plainFallbackResult == null && !lyricsResult.plainLyrics.isNullOrBlank()) {
                    plainFallbackResult = lyricsResult
                }
            }
        }

        if (plainFallbackResult != null) {
            memoryCache[cacheKey] = plainFallbackResult
            persistToRoom(trackHash, cleaned.title, cleaned.artist, plainFallbackResult, durationSeconds)
            return@withContext Result.success(plainFallbackResult)
        }

        Result.failure(NoSuchElementException("No lyrics found across ${sources.size} providers for ${cleaned.artist} - ${cleaned.title}"))
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

        val cleaned = TrackMetadataCleaner.clean(title, artist)
        val trackHash = "${cleaned.artist.lowercase()}__${cleaned.title.lowercase()}"
        val result = targetSource.fetchLyrics(cleaned.title, cleaned.artist, album, durationSeconds)
        if (result.isSuccess) {
            val res = result.getOrThrow()
            val cacheKey = makeCacheKey(cleaned.artist, cleaned.title, sourceId)
            memoryCache[cacheKey] = res
            persistToRoom(trackHash, cleaned.title, cleaned.artist, res, durationSeconds)
        }
        result
    }

    suspend fun updateCachedOffset(title: String, artist: String, offsetMs: Long) = withContext(Dispatchers.IO) {
        val cleaned = TrackMetadataCleaner.clean(title, artist)
        val trackHash = "${cleaned.artist.lowercase()}__${cleaned.title.lowercase()}"
        lyricsCacheDao?.updateSyncOffset(trackHash, offsetMs)
    }

    private suspend fun persistToRoom(
        trackHash: String,
        cleanedTitle: String,
        cleanedArtist: String,
        result: LyricsResult,
        durationSeconds: Long?
    ) {
        val dao = lyricsCacheDao ?: return
        val syncType = if (!result.syncedLrc.isNullOrBlank()) {
            if (result.isWordByWord) "WORD_BY_WORD" else "SYNCED_LRC"
        } else if (!result.plainLyrics.isNullOrBlank()) {
            "PACED_PLAIN"
        } else {
            "NOT_FOUND"
        }

        val entity = LyricsCacheEntity(
            trackHash = trackHash,
            cleanedTitle = cleanedTitle,
            cleanedArtist = cleanedArtist,
            sourceName = result.sourceName,
            syncType = syncType,
            userOffsetMs = 0L,
            durationMs = if (durationSeconds != null && durationSeconds > 0) durationSeconds * 1000L else 0L,
            isWordByWord = result.isWordByWord,
            syncedLrc = result.syncedLrc,
            plainLyrics = result.plainLyrics,
            cachedAt = System.currentTimeMillis()
        )
        dao.insertOrUpdate(entity)
    }

    suspend fun queryAllSourcesConcurrently(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Long? = null
    ): List<Pair<LyricsSource, Result<LyricsResult>>> = withContext(Dispatchers.IO) {
        val cleaned = TrackMetadataCleaner.clean(title, artist)
        sources.map { source ->
            async {
                val res = source.fetchLyrics(cleaned.title, cleaned.artist, album, durationSeconds)
                Pair(source, res)
            }
        }.awaitAll()
    }

    private fun makeCacheKey(artist: String, title: String, sourceId: String? = null): String {
        val base = "${artist.trim().lowercase()}__${title.trim().lowercase()}"
        return if (sourceId != null && sourceId != "auto") "${base}__${sourceId}" else base
    }
}
