package com.singalong07.app.service.media

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
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
    private var lyricsFetchJob: Job? = null

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        handleActiveControllers(controllers)
    }

    override fun onCreate() {
        super.onCreate()
        val app = SingAlongApp.instance
        controllerBridge = app.controllerBridge

        // Listen for metadata changes directly from the active media session!
        controllerBridge?.onMetadataChangedCallback = { controller ->
            processTrackMetadata(controller)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        val component = ComponentName(this, MusicTrackingNotificationService::class.java)

        try {
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, component)
            val initialControllers = mediaSessionManager?.getActiveSessions(component)
            handleActiveControllers(initialControllers)
        } catch (_: SecurityException) {}

        scanExistingNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        } catch (_: Exception) {}
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null || sbn.packageName == packageName) return

        val extras = sbn.notification.extras

        // 1. Check if notification has a direct MediaSession token attached
        val sessionToken: MediaSession.Token? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        }

        if (sessionToken != null) {
            try {
                val directController = MediaController(this, sessionToken)
                controllerBridge?.attachMediaController(directController)
                processTrackMetadata(directController)
                return
            } catch (_: Exception) {}
        }

        // 2. Query system active sessions
        try {
            val component = ComponentName(this, MusicTrackingNotificationService::class.java)
            val controllers = mediaSessionManager?.getActiveSessions(component)
            if (!controllers.isNullOrEmpty()) {
                handleActiveControllers(controllers)
                return
            }
        } catch (_: Exception) {}

        // 3. Fallback for non-session media: Extract Title and Artist directly from notification extras
        if (sbn.isOngoing || sbn.notification.category == Notification.CATEGORY_TRANSPORT) {
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val artist = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            if (!title.isNullOrBlank() && title != "SingAlong07") {
                processRawMetadata(title, artist, sbn.packageName)
            }
        }
    }

    private fun scanExistingNotifications() {
        try {
            val activeNotifications = activeNotifications ?: return
            for (sbn in activeNotifications) {
                if (sbn.packageName != packageName && (sbn.isOngoing || sbn.notification.category == Notification.CATEGORY_TRANSPORT)) {
                    onNotificationPosted(sbn)
                    break
                }
            }
        } catch (_: Exception) {}
    }

    private fun handleActiveControllers(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) return

        val activePlaying = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull() ?: return

        controllerBridge?.attachMediaController(activePlaying)
        processTrackMetadata(activePlaying)
    }

    private fun processTrackMetadata(controller: MediaController) {
        val metadata = controller.metadata ?: return
        val rawTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.description?.title?.toString() ?: return
        val rawArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.description?.subtitle?.toString()
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
            ?: metadata.description?.description?.toString()
        val durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)
        val sourcePackage = controller.packageName

        fetchAndSynchronizeLyrics(rawTitle, rawArtist, album, durationMs, sourcePackage)
    }

    private fun processRawMetadata(rawTitle: String, rawArtist: String?, sourcePackage: String) {
        fetchAndSynchronizeLyrics(rawTitle, rawArtist, null, 0L, sourcePackage)
    }

    private fun fetchAndSynchronizeLyrics(
        rawTitle: String,
        rawArtist: String?,
        album: String?,
        durationMs: Long,
        sourcePackage: String
    ) {
        val cleaned = TrackMetadataCleaner.clean(rawTitle, rawArtist)
        val trackHash = "${cleaned.artist.lowercase()}__${cleaned.title.lowercase()}"

        if (trackHash == lastTrackHash && lastTrackHash.isNotBlank()) return
        lastTrackHash = trackHash

        // CRITICAL BUG FIX: Cancel any in-flight lyrics fetch so old queries don't overwrite the new track!
        lyricsFetchJob?.cancel()
        lyricsFetchJob = serviceScope.launch {
            val app = SingAlongApp.instance
            val preferredSource = app.preferencesManager.settingsFlow.value.preferredProviderId

            // 1. Restore saved user timing calibration offset from Room DB
            val savedOffset = app.database.historyDao().getOffsetForTrack(trackHash) ?: 0L
            app.syncEngine.setOffset(savedOffset)

            // 2. Fetch lyrics from multi-source aggregator with preferred source priority
            val result = app.lyricsRepository.getLyricsCascading(
                title = cleaned.title,
                artist = cleaned.artist,
                album = album,
                durationSeconds = if (durationMs > 0) durationMs / 1000 else null,
                preferredSourceId = preferredSource
            )

            // If user skipped to another song while network was fetching, discard this result!
            if (!isActive || trackHash != lastTrackHash) return@launch

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
                    val safeDuration = if (durationMs > 0) durationMs else 180000L
                    app.syncEngine.loadPlainLyricsWithPacingFallback(lyrics.plainLyrics, safeDuration)
                    syncType = "PACED_PLAIN"
                }
            } else {
                // Only clear if this is still the active track
                if (trackHash == lastTrackHash) {
                    app.syncEngine.loadSyncedLyrics(emptyList(), durationMs)
                }
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
        lyricsFetchJob?.cancel()
        serviceScope.cancel()
    }
}
