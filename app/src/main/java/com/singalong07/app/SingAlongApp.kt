package com.singalong07.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.singalong07.app.data.db.SingAlongDatabase
import com.singalong07.app.data.preferences.OverlayPreferencesManager
import com.singalong07.app.data.sources.LyricsAggregatorRepository
import com.singalong07.app.domain.sync.BiDirectionalSyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SingAlongApp : Application() {

    lateinit var database: SingAlongDatabase
        private set

    lateinit var preferencesManager: OverlayPreferencesManager
        private set

    lateinit var syncEngine: BiDirectionalSyncEngine
        private set

    lateinit var lyricsRepository: LyricsAggregatorRepository
        private set

    lateinit var controllerBridge: com.singalong07.app.service.media.MediaSessionControllerBridge
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = SingAlongDatabase.getDatabase(this)
        preferencesManager = OverlayPreferencesManager()
        syncEngine = BiDirectionalSyncEngine()
        lyricsRepository = LyricsAggregatorRepository()
        controllerBridge = com.singalong07.app.service.media.MediaSessionControllerBridge(syncEngine)

        createNotificationChannels()
    }

    private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    fun reloadCurrentLyrics(preferredSourceId: String? = null) {
        val trackingService = com.singalong07.app.service.media.MusicTrackingNotificationService.instance
        if (trackingService != null && trackingService.hasActiveTrack()) {
            trackingService.reloadCurrentTrack(preferredSourceId)
            return
        }

        // Fallback: Query based on current metadata in controllerBridge
        val rawTitle = controllerBridge.currentTrackTitle.value ?: return
        val rawArtist = controllerBridge.currentArtist.value
        val durationMs = syncEngine.syncState.value.durationMs

        appScope.launch {
            syncEngine.startLoadingLyrics(durationMs)
            val cleaned = com.singalong07.app.domain.cleaner.TrackMetadataCleaner.clean(rawTitle, rawArtist)
            val provider = preferredSourceId ?: preferencesManager.settingsFlow.value.preferredProviderId

            val result = lyricsRepository.getLyricsCascading(
                title = cleaned.title,
                artist = cleaned.artist,
                durationSeconds = if (durationMs > 0) durationMs / 1000 else null,
                preferredSourceId = provider
            )

            if (result.isSuccess) {
                val lyrics = result.getOrThrow()
                if (!lyrics.syncedLrc.isNullOrBlank()) {
                    val parsed = com.singalong07.app.domain.parser.LrcParser.parse(lyrics.syncedLrc)
                    syncEngine.loadSyncedLyrics(
                        lines = parsed.lines,
                        durationMs = durationMs,
                        isWordByWord = parsed.isWordByWord,
                        sourceName = lyrics.sourceName
                    )
                } else if (!lyrics.plainLyrics.isNullOrBlank()) {
                    val safeDuration = if (durationMs > 0) durationMs else 180000L
                    syncEngine.loadPlainLyricsWithPacingFallback(lyrics.plainLyrics, safeDuration, lyrics.sourceName)
                } else {
                    syncEngine.setLyricsNotFound(durationMs)
                }
            } else {
                syncEngine.setLyricsNotFound(durationMs)
            }
        }
    }

    fun searchLyricsManual(
        title: String,
        artist: String,
        providerId: String = "auto"
    ) {
        val durationMs = syncEngine.syncState.value.durationMs
        controllerBridge.updateDisplayMetadata(title, artist)

        appScope.launch {
            syncEngine.startLoadingLyrics(durationMs)
            val cleaned = com.singalong07.app.domain.cleaner.TrackMetadataCleaner.clean(title, artist)
            val result = lyricsRepository.getLyricsCascading(
                title = cleaned.title,
                artist = cleaned.artist,
                durationSeconds = if (durationMs > 0) durationMs / 1000 else null,
                preferredSourceId = providerId
            )

            if (result.isSuccess) {
                val lyrics = result.getOrThrow()
                if (!lyrics.syncedLrc.isNullOrBlank()) {
                    val parsed = com.singalong07.app.domain.parser.LrcParser.parse(lyrics.syncedLrc)
                    syncEngine.loadSyncedLyrics(
                        lines = parsed.lines,
                        durationMs = durationMs,
                        isWordByWord = parsed.isWordByWord,
                        sourceName = lyrics.sourceName
                    )
                } else if (!lyrics.plainLyrics.isNullOrBlank()) {
                    val safeDuration = if (durationMs > 0) durationMs else 180000L
                    syncEngine.loadPlainLyricsWithPacingFallback(lyrics.plainLyrics, safeDuration, lyrics.sourceName)
                } else {
                    syncEngine.setLyricsNotFound(durationMs)
                }
            } else {
                syncEngine.setLyricsNotFound(durationMs)
            }
        }
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
