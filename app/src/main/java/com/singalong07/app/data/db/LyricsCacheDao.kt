package com.singalong07.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsCacheDao {

    @Query("SELECT * FROM lyrics_cache WHERE trackHash = :trackHash LIMIT 1")
    suspend fun getCachedLyrics(trackHash: String): LyricsCacheEntity?

    @Query("SELECT * FROM lyrics_cache ORDER BY cachedAt DESC")
    fun getAllCachedFlow(): Flow<List<LyricsCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(cache: LyricsCacheEntity)

    @Query("UPDATE lyrics_cache SET userOffsetMs = :offsetMs WHERE trackHash = :trackHash")
    suspend fun updateSyncOffset(trackHash: String, offsetMs: Long)

    @Query("DELETE FROM lyrics_cache WHERE trackHash = :trackHash")
    suspend fun deleteCacheForTrack(trackHash: String)

    @Query("DELETE FROM lyrics_cache")
    suspend fun clearAllCache()

    @Query("SELECT COUNT(*) FROM lyrics_cache")
    suspend fun getCacheCount(): Int
}
