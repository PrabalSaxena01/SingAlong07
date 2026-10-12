package com.singalong07.app.ui.overlay

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.singalong07.app.SingAlongApp
import com.singalong07.app.service.media.MediaSessionControllerBridge
import com.singalong07.app.ui.theme.OverlayThemeMode
import com.singalong07.app.ui.theme.OverlayThemes

@Composable
fun FloatingOverlayRoot(
    controllerBridge: MediaSessionControllerBridge,
    onWindowResizeRequested: (isExpanded: Boolean) -> Unit,
    onRequestFocus: (Boolean) -> Unit = {},
    onDragWindow: (Float, Float) -> Unit = { _, _ -> },
    onDragWindowEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val app = SingAlongApp.instance
    val settings by app.preferencesManager.settingsFlow.collectAsState()
    val isPlaying by app.syncEngine.isPlayingFlow.collectAsState()

    val currentTitle by controllerBridge.currentTrackTitle.collectAsState()
    val currentArtist by controllerBridge.currentArtist.collectAsState()
    val dynamicScheme by controllerBridge.dynamicColorScheme.collectAsState()
    val albumArtBitmap by controllerBridge.albumArtBitmap.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current

    val handleCatchAudio: () -> Unit = {
        app.triggerGoogleSoundSearch(context)
    }

    val handleLaunchGoogleSoundSearch: () -> Unit = handleCatchAudio

    var isExpanded by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showEditSearch by remember { mutableStateOf(false) }

    // Request soft keyboard window focus when editing search query
    LaunchedEffect(showEditSearch) {
        onRequestFocus(showEditSearch)
    }

    // Resolve active color scheme
    val colorScheme = when (settings.themeMode) {
        OverlayThemeMode.AMOLED_DARK -> OverlayThemes.AmoledDark
        OverlayThemeMode.FROSTED_GLASS -> OverlayThemes.FrostedGlass
        OverlayThemeMode.NEON_CYBERPUNK -> OverlayThemes.NeonCyberpunk
        OverlayThemeMode.PAPER_LIGHT -> OverlayThemes.PaperLight
        OverlayThemeMode.DYNAMIC_PALETTE -> dynamicScheme ?: OverlayThemes.FrostedGlass
    }

    // Trigger WindowManager width/height changes when expanded state changes
    LaunchedEffect(isExpanded) {
        onWindowResizeRequested(isExpanded)
    }

    Box(modifier = modifier) {
        AnimatedContent(
            targetState = isExpanded,
            transitionSpec = {
                (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                 scaleIn(initialScale = 0.88f))
                .togetherWith(
                    fadeOut(animationSpec = tween(150)) +
                    scaleOut(targetScale = 0.88f)
                )
            },
            label = "pill_card_morph"
        ) { expanded ->
            if (expanded) {
                val syncState by app.syncEngine.syncState.collectAsState()
                Box(
                    modifier = Modifier
                        .width(320.dp)
                        .height(440.dp)
                ) {
                    FloatingLyricsCardView(
                        syncState = syncState,
                        settings = settings,
                        colorScheme = colorScheme,
                        trackTitle = currentTitle,
                        artistName = currentArtist,
                        onLineClicked = { lineIndex -> controllerBridge.seekToLine(lineIndex) },
                        onSeekToTimestamp = { targetMs -> controllerBridge.seekToTimestamp(targetMs) },
                        onTogglePlayPause = { controllerBridge.togglePlayPause() },
                        onSkipNext = { controllerBridge.skipToNext() },
                        onSkipPrevious = { controllerBridge.skipToPrevious() },
                        onNudgeOffset = { delta ->
                            val newOffset = app.syncEngine.adjustOffset(delta)
                            app.saveTrackOffset(newOffset)
                        },
                        onResetOffset = {
                            app.syncEngine.resetOffset()
                            app.saveTrackOffset(0L)
                        },
                        onSyncWithLine = { lineIndex ->
                            app.syncWithLine(lineIndex)
                        },
                        onSwitchProvider = { providerId ->
                            app.preferencesManager.updatePreferredProvider(providerId)
                            app.reloadCurrentLyrics(providerId)
                        },
                        onRetryLyrics = { app.reloadCurrentLyrics() },
                        onToggleGhostMode = { app.preferencesManager.toggleClickThrough(!settings.isClickThroughEnabled) },
                        onOpenEditSearch = { showEditSearch = true },
                        onOpenSettings = { showSettings = true },
                        onMinimizeToPill = { isExpanded = false },
                        onCatchAudio = handleCatchAudio,
                        onLaunchGoogleSoundSearch = handleLaunchGoogleSoundSearch,
                        onDragWindow = onDragWindow,
                        onDragWindowEnd = onDragWindowEnd
                    )

                    if (showEditSearch) {
                        EditSearchLyricsSheet(
                            initialTitle = currentTitle.orEmpty(),
                            initialArtist = currentArtist.orEmpty(),
                            preferredProviderId = settings.preferredProviderId,
                            colorScheme = colorScheme,
                            onDismiss = { showEditSearch = false },
                            onSearch = { title, artist, providerId ->
                                app.preferencesManager.updatePreferredProvider(providerId)
                                app.searchLyricsManual(title, artist, providerId)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (showSettings) {
                        ThemeAndFontSettingsSheet(
                            settings = settings,
                            onThemeSelect = { app.preferencesManager.updateTheme(it) },
                            onFontScaleSelect = { app.preferencesManager.updateFontScale(it) },
                            onOpacityChange = { app.preferencesManager.updateOpacity(it) },
                            onToggleTranslation = { app.preferencesManager.toggleTranslation(it) },
                            onToggleRomanization = { app.preferencesManager.toggleRomanization(it) },
                            onToggleClickThrough = { app.preferencesManager.toggleClickThrough(it) },
                            onProviderSelect = { providerId ->
                                app.preferencesManager.updatePreferredProvider(providerId)
                                app.reloadCurrentLyrics(providerId)
                            },
                            onDismiss = { showSettings = false },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                FloatingPillView(
                    trackTitle = currentTitle ?: "SingAlong07",
                    artistName = currentArtist ?: "No Media",
                    isPlaying = isPlaying,
                    colorScheme = colorScheme,
                    albumArt = albumArtBitmap,
                    isAudioCatching = false,
                    audioCatchStatus = null,
                    onCatchAudio = handleCatchAudio,
                    onLaunchGoogleSoundSearch = handleLaunchGoogleSoundSearch,
                    onExpandToCard = { isExpanded = true },
                    onDragWindow = onDragWindow,
                    onDragWindowEnd = onDragWindowEnd
                )
            }
        }
    }
}
