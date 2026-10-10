package com.singalong07.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.singalong07.app.data.db.SingAlongDatabase
import com.singalong07.app.data.preferences.OverlayPreferencesManager
import com.singalong07.app.data.sources.LyricsAggregatorRepository
import com.singalong07.app.domain.sync.BiDirectionalSyncEngine

class SingAlongApp : Application() {

    lateinit var database: SingAlongDatabase
        private set

    lateinit var preferencesManager: OverlayPreferencesManager
        private set

    lateinit var syncEngine: BiDirectionalSyncEngine
        private set

    lateinit var lyricsRepository: LyricsAggregatorRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = SingAlongDatabase.getDatabase(this)
        preferencesManager = OverlayPreferencesManager()
        syncEngine = BiDirectionalSyncEngine()
        lyricsRepository = LyricsAggregatorRepository()

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_OVERLAY_SERVICE,
                "SingAlong07 Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the SingAlong07 floating lyrics overlay active"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_OVERLAY_SERVICE = "singalong07_overlay_channel"
        lateinit var instance: SingAlongApp
            private set
    }
}
