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

    lateinit var audioCaptureManager: com.singalong07.app.domain.audio.AudioCaptureManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = SingAlongDatabase.getDatabase(this)
        preferencesManager = OverlayPreferencesManager()
        syncEngine = BiDirectionalSyncEngine()
        lyricsRepository = LyricsAggregatorRepository(lyricsCacheDao = database.lyricsCacheDao())
        controllerBridge = com.singalong07.app.service.media.MediaSessionControllerBridge(syncEngine)
        audioCaptureManager = com.singalong07.app.domain.audio.AudioCaptureManager()

        createNotificationChannels()
    }

    fun recordAndCatchAmbientAudio(context: android.content.Context) {
        android.widget.Toast.makeText(context, "Listening for music (5s)... Hold device near speaker", android.widget.Toast.LENGTH_SHORT).show()
        appScope.launch {
            val result = audioCaptureManager.captureAndIdentify(context)
            if (result.isSuccess) {
                val match = result.getOrThrow()
                android.widget.Toast.makeText(context, "Identified: ${match.title} - ${match.artist}", android.widget.Toast.LENGTH_SHORT).show()
                controllerBridge.updateDisplayMetadata(match.title, match.artist)
                syncEngine.startLoadingLyrics(180000L)

                val lyricsResult = lyricsRepository.getLyricsCascading(
                    title = match.title,
                    artist = match.artist,
                    album = match.album,
                    preferredSourceId = preferencesManager.settingsFlow.value.preferredProviderId
                )

                var lyricSource = "None"
                var syncType = "NOT_FOUND"

                if (lyricsResult.isSuccess) {
                    val lyrics = lyricsResult.getOrThrow()
                    lyricSource = lyrics.sourceName
                    if (!lyrics.syncedLrc.isNullOrBlank()) {
                        val parsed = com.singalong07.app.domain.parser.LrcParser.parse(lyrics.syncedLrc)
                        syncEngine.loadSyncedLyrics(parsed.lines, 180000L, parsed.isWordByWord, lyricSource)
                        syncType = if (parsed.isWordByWord) "WORD_BY_WORD" else "SYNCED_LRC"
                    } else if (!lyrics.plainLyrics.isNullOrBlank()) {
                        syncEngine.loadPlainLyricsWithPacingFallback(lyrics.plainLyrics, 180000L, lyricSource)
                        syncType = "PACED_PLAIN"
                    } else {
                        syncEngine.setLyricsNotFound(180000L)
                    }
                } else {
                    syncEngine.setLyricsNotFound(180000L)
                }

                // Log into Room DB history as Audio Captured!
                database.historyDao().insertHistory(
                    com.singalong07.app.data.db.ListeningHistoryEntity(
                        trackTitle = match.title,
                        artistName = match.artist,
                        albumName = match.album,
                        durationMs = 180000L,
                        sourcePackage = "ambient.audio",
                        lyricSource = lyricSource,
                        lyricSyncType = syncType,
                        userOffsetMs = 0L,
                        matchedViaFingerprint = true
                    )
                )
            } else {
                val ex = result.exceptionOrNull()
                val message = if (ex is SecurityException) {
                    "Microphone permission required for audio capture"
                } else {
                    "No match found. Try again or long-press for Google Sound Search"
                }
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    fun saveTrackOffset(offsetMs: Long) {
        val title = controllerBridge.currentTrackTitle.value ?: return
        val artist = controllerBridge.currentArtist.value ?: ""
        val cleaned = com.singalong07.app.domain.cleaner.TrackMetadataCleaner.clean(title, artist)
        val trackHash = "${cleaned.artist.lowercase()}__${cleaned.title.lowercase()}"
        appScope.launch(Dispatchers.IO) {
            database.historyDao().saveOffsetForTrack(
                com.singalong07.app.data.db.UserOffsetEntity(trackHash, offsetMs)
            )
            database.lyricsCacheDao().updateSyncOffset(trackHash, offsetMs)
        }
    }

    fun syncWithLine(lineIndex: Int) {
        val currentPos = syncEngine.syncState.value.currentPositionMs
        val offsetMs = syncEngine.syncWithLine(lineIndex, currentPos) ?: return
        saveTrackOffset(offsetMs)
        val offsetSec = String.format(java.util.Locale.US, "%.2f", offsetMs / 1000.0)
        val sign = if (offsetMs >= 0) "+" else ""
        android.widget.Toast.makeText(
            this,
            "Synced with line #${lineIndex + 1} (${sign}${offsetSec}s)",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

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
