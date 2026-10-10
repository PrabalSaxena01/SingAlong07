package com.singalong07.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM listening_history ORDER BY timestamp DESC LIMIT 500")
    fun getAllHistoryFlow(): Flow<List<ListeningHistoryEntity>>

    @Query("SELECT * FROM listening_history ORDER BY timestamp DESC")
    suspend fun getAllHistoryForExport(): List<ListeningHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: ListeningHistoryEntity): Long

    @Query("DELETE FROM listening_history")
    suspend fun clearHistory()

    // Offsets
    @Query("SELECT offsetMs FROM user_offsets WHERE trackHash = :trackHash LIMIT 1")
    suspend fun getOffsetForTrack(trackHash: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveOffsetForTrack(offset: UserOffsetEntity)
}
