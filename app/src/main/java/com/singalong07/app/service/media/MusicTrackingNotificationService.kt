package com.singalong07.app.service.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.singalong07.app.SingAlongApp
import com.singalong07.app.data.db.ListeningHistoryEntity
import com.singalong07.app.domain.cleaner.TrackMetadataCleaner
import com.singalong07.app.domain.parser.LrcParser
import kotlinx.coroutines.*

class MusicTrackingNotificationService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaSessionManager: MediaSessionManager? = null
    private var controllerBridge: MediaSessionControllerBridge? = null
    private var lastTrackHash: String = ""

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        handleActiveControllers(controllers)
    }

    override fun onCreate() {
        super.onCreate()
        val app = SingAlongApp.instance
        controllerBridge = MediaSessionControllerBridge(app.syncEngine, serviceScope)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        val component = ComponentName(this, MusicTrackingNotificationService::class.java)

        try {
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, component)
            val initialControllers = mediaSessionManager?.getActiveSessions(component)
            handleActiveControllers(initialControllers)
        } catch (e: SecurityException) {
            // Handled when notification access permission is granted
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        controllerBridge?.detachMediaController()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        // Check for active controllers upon any new media notification
        try {
            val component = ComponentName(this, MusicTrackingNotificationService::class.java)
            val controllers = mediaSessionManager?.getActiveSessions(component)
            handleActiveControllers(controllers)
        } catch (_: Exception) {}
    }

    private fun handleActiveControllers(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) return

        // Prioritize playing controller
        val activePlaying = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull() ?: return

        controllerBridge?.attachMediaController(activePlaying)
        processTrackMetadata(activePlaying)
    }

    private fun processTrackMetadata(controller: MediaController) {
        val metadata = controller.metadata ?: return
        val rawTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return
        val rawArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)
        val sourcePackage = controller.packageName

        val cleaned = TrackMetadataCleaner.clean(rawTitle, rawArtist)
        val trackHash = "${cleaned.artist.lowercase()}__${cleaned.title.lowercase()}"

        // Deduplicate rapid updates for the same song
        if (trackHash == lastTrackHash && lastTrackHash.isNotBlank()) return
        lastTrackHash = trackHash

        serviceScope.launch {
            val app = SingAlongApp.instance

            // 1. Restore saved user timing calibration offset from Room DB
            val savedOffset = app.database.historyDao().getOffsetForTrack(trackHash) ?: 0L
            app.syncEngine.setOffset(savedOffset)

            // 2. Fetch lyrics from multi-source cascading aggregator
            val result = app.lyricsRepository.getLyricsCascading(
                title = cleaned.title,
                artist = cleaned.artist,
                album = album,
                durationSeconds = if (durationMs > 0) durationMs / 1000 else null
            )

            var lyricSource = "None"
            var syncType = "NOT_FOUND"

            if (result.isSuccess) {
                val lyrics = result.getOrThrow()
                lyricSource = lyrics.sourceName

                if (!lyrics.syncedLrc.isNullOrBlank()) {
                    val parsed = LrcParser.parse(lyrics.syncedLrc)
                    app.syncEngine.loadSyncedLyrics(
                        lines = parsed.lines,
                        durationMs = durationMs,
                        isWordByWord = parsed.isWordByWord
                    )
                    syncType = if (parsed.isWordByWord) "WORD_BY_WORD" else "SYNCED_LRC"
                } else if (!lyrics.plainLyrics.isNullOrBlank()) {
                    app.syncEngine.loadPlainLyricsWithPacingFallback(lyrics.plainLyrics, durationMs)
                    syncType = "PACED_PLAIN"
                }
            } else {
                app.syncEngine.loadSyncedLyrics(emptyList(), durationMs)
            }

            // 3. Log into local Room DB Listening History
            app.database.historyDao().insertHistory(
                ListeningHistoryEntity(
                    trackTitle = cleaned.title,
                    artistName = cleaned.artist,
                    albumName = album,
                    durationMs = durationMs,
                    sourcePackage = sourcePackage,
                    lyricSource = lyricSource,
                    lyricSyncType = syncType,
                    userOffsetMs = savedOffset
                )
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
