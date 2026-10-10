package com.singalong07.app.service.media

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import com.singalong07.app.domain.sync.BiDirectionalSyncEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MediaSessionControllerBridge(
    private val syncEngine: BiDirectionalSyncEngine,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    private var activeMediaController: MediaController? = null
    private var tickerJob: Job? = null

    private val _currentTrackTitle = MutableStateFlow<String?>(null)
    val currentTrackTitle: StateFlow<String?> = _currentTrackTitle.asStateFlow()

    private val _currentArtist = MutableStateFlow<String?>(null)
    val currentArtist: StateFlow<String?> = _currentArtist.asStateFlow()

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            handlePlaybackStateChange(state)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            _currentTrackTitle.value = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            _currentArtist.value = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        }
    }

    fun attachMediaController(controller: MediaController) {
        detachMediaController()
        activeMediaController = controller
        controller.registerCallback(callback)

        val metadata = controller.metadata
        _currentTrackTitle.value = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        _currentArtist.value = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)

        handlePlaybackStateChange(controller.playbackState)
    }

    fun detachMediaController() {
        tickerJob?.cancel()
        activeMediaController?.unregisterCallback(callback)
        activeMediaController = null
    }

    fun seekToLine(lineIndex: Int) {
        val targetMs = syncEngine.calculateSeekTimestampForLine(lineIndex) ?: return
        seekToTimestamp(targetMs)
    }

    fun seekToTimestamp(timestampMs: Long) {
        activeMediaController?.transportControls?.seekTo(timestampMs)
        val isPlaying = activeMediaController?.playbackState?.state == PlaybackState.STATE_PLAYING
        syncEngine.updatePlaybackPosition(timestampMs, isPlaying)
    }

    fun togglePlayPause() {
        val state = activeMediaController?.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING) {
            activeMediaController?.transportControls?.pause()
        } else {
            activeMediaController?.transportControls?.play()
        }
    }

    fun skipToNext() {
        activeMediaController?.transportControls?.skipToNext()
    }

    fun skipToPrevious() {
        activeMediaController?.transportControls?.skipToPrevious()
    }

    private fun handlePlaybackStateChange(state: PlaybackState?) {
        tickerJob?.cancel()
        if (state == null) return

        val isPlaying = state.state == PlaybackState.STATE_PLAYING
        val basePosition = state.position
        val lastUpdateTime = state.lastPositionUpdateTime
        val playbackSpeed = state.playbackSpeed.takeIf { it > 0f } ?: 1.0f

        if (isPlaying) {
            startInterpolationTicker(basePosition, lastUpdateTime, playbackSpeed)
        } else {
            syncEngine.updatePlaybackPosition(basePosition, isPlaying = false)
        }
    }

    private fun startInterpolationTicker(basePositionMs: Long, updateTime: Long, speed: Float) {
        tickerJob = coroutineScope.launch {
            while (isActive) {
                val timeDelta = SystemClock.elapsedRealtime() - updateTime
                val estimatedCurrentMs = basePositionMs + (timeDelta * speed).toLong()

                syncEngine.updatePlaybackPosition(estimatedCurrentMs, isPlaying = true)
                delay(33L) // ~30 fps ticker
            }
        }
    }
}
