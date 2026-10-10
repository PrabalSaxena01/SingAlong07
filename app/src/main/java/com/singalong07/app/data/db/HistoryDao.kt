package com.singalong07.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("""
        SELECT * FROM listening_history 
        WHERE id IN (
            SELECT MAX(id) FROM listening_history 
            GROUP BY LOWER(TRIM(trackTitle)), LOWER(TRIM(artistName)), timestamp / 600000
        ) 
        ORDER BY timestamp DESC LIMIT 500
    """)
    fun getAllHistoryFlow(): Flow<List<ListeningHistoryEntity>>

    @Query("""
        SELECT * FROM listening_history 
        WHERE id IN (
            SELECT MAX(id) FROM listening_history 
            GROUP BY LOWER(TRIM(trackTitle)), LOWER(TRIM(artistName)), timestamp / 600000
        ) 
        ORDER BY timestamp DESC
    """)
    suspend fun getAllHistoryForExport(): List<ListeningHistoryEntity>

    @Query("SELECT * FROM listening_history ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestHistory(): ListeningHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: ListeningHistoryEntity): Long

    @Update
    suspend fun updateHistory(item: ListeningHistoryEntity)

    @Query("DELETE FROM listening_history")
    suspend fun clearHistory()

    // Offsets
    @Query("SELECT offsetMs FROM user_offsets WHERE trackHash = :trackHash LIMIT 1")
    suspend fun getOffsetForTrack(trackHash: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveOffsetForTrack(offset: UserOffsetEntity)

    @Query("DELETE FROM user_offsets")
    suspend fun clearOffsets()
}
