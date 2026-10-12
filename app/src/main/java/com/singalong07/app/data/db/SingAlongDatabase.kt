package com.singalong07.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ListeningHistoryEntity::class,
        UserOffsetEntity::class,
        LyricsCacheEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class SingAlongDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao
    abstract fun lyricsCacheDao(): LyricsCacheDao

    companion object {
        @Volatile
        private var INSTANCE: SingAlongDatabase? = null

        fun getDatabase(context: Context): SingAlongDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SingAlongDatabase::class.java,
                    "singalong07.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
