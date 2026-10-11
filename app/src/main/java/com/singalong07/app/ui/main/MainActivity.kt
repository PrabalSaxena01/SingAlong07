package com.singalong07.app.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.singalong07.app.SingAlongApp
import com.singalong07.app.data.db.ListeningHistoryEntity
import com.singalong07.app.data.export.HistoryCsvExporter
import com.singalong07.app.data.preferences.OverlaySettings
import com.singalong07.app.service.overlay.FloatingOverlayService
import com.singalong07.app.ui.theme.LyricFontScale
import com.singalong07.app.ui.theme.OverlayThemeMode
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Microphone permission granted for Audio Catching!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Microphone permission is required to capture ambient audio", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntents(intent)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MainScreen(
                    hasOverlayPermission = Settings.canDrawOverlays(this),
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onRequestNotificationPermission = { requestNotificationPermission() },
                    onRequestMicPermission = { requestMicPermission() },
                    onStartOverlay = { startOverlayService() },
                    onStopOverlay = { stopOverlayService() },
                    onPlayOnYouTube = { trackTitle, artistName -> playOnYouTube(trackTitle, artistName) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntents(intent)
    }

    private fun handleIntents(intent: Intent?) {
        if (intent?.getBooleanExtra("REQUEST_MIC_PERMISSION", false) == true) {
            requestMicPermission()
        }
    }

    private fun requestMicPermission() {
        micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    private fun playOnYouTube(trackTitle: String, artistName: String) {
        val query = "$trackTitle $artistName".trim()
        val appIntent = Intent(Intent.ACTION_SEARCH).apply {
            setPackage("com.google.android.youtube")
            putExtra("query", query)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(appIntent)
        } catch (_: Exception) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                startActivity(webIntent)
            } catch (_: Exception) {
                Toast.makeText(this, "Could not open YouTube", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun requestNotificationPermission() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun startOverlayService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant Overlay Permission first", Toast.LENGTH_SHORT).show()
            requestOverlayPermission()
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, FloatingOverlayService::class.java))
    }

    private fun stopOverlayService() {
        stopService(Intent(this, FloatingOverlayService::class.java))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    hasOverlayPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestMicPermission: () -> Unit = {},
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onPlayOnYouTube: (trackTitle: String, artistName: String) -> Unit = { _, _ -> }
) {
    val app = SingAlongApp.instance
    val historyList by app.database.historyDao().getAllHistoryFlow().collectAsState(initial = emptyList())
    val settings by app.preferencesManager.settingsFlow.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    // SAF CSV File Exporter Launcher
    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val exporter = HistoryCsvExporter(app)
                val allHistory = app.database.historyDao().getAllHistoryForExport()
                val result = exporter.exportToUri(allHistory, uri)
                if (result.isSuccess) {
                    Toast.makeText(app, "History exported successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(app, "Export failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF1DB954))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SingAlong07", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    if (selectedTab == 1) {
                        IconButton(onClick = {
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                            exportCsvLauncher.launch("singalong07_history_$timestamp.csv")
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Export CSV", tint = Color.White)
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.PlayCircle, contentDescription = "Player") },
                    label = { Text("Player") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.QueueMusic, contentDescription = "History") },
                    label = { Text("History") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    // Tab 0: Overlay Service & Live Player Controls
                    PlayerAndOverlayTab(
                        onRequestOverlayPermission = onRequestOverlayPermission,
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        onRequestMicPermission = onRequestMicPermission,
                        onStartOverlay = onStartOverlay,
                        onStopOverlay = onStopOverlay
                    )
                }
                1 -> {
                    // Tab 1: Dedicated Listening History
                    ListeningHistoryTab(
                        historyList = historyList,
                        onPlayOnYouTube = onPlayOnYouTube,
                        onExportCsv = {
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                            exportCsvLauncher.launch("singalong07_history_$timestamp.csv")
                        },
                        onClearHistory = {
                            coroutineScope.launch { app.database.historyDao().clearHistory() }
                        }
                    )
                }
                2 -> {
                    // Tab 2: Full App Settings, Providers, & Cache Control
                    AppSettingsTab(
                        settings = settings,
                        onUpdateTheme = { app.preferencesManager.updateTheme(it) },
                        onUpdateFontScale = { app.preferencesManager.updateFontScale(it) },
                        onUpdateOpacity = { app.preferencesManager.updateOpacity(it) },
                        onToggleTranslation = { app.preferencesManager.toggleTranslation(it) },
                        onToggleRomanization = { app.preferencesManager.toggleRomanization(it) },
                        onToggleClickThrough = { app.preferencesManager.toggleClickThrough(it) },
                        onUpdateProvider = {
                            app.preferencesManager.updatePreferredProvider(it)
                            app.reloadCurrentLyrics(it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerAndOverlayTab(
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestMicPermission: () -> Unit = {},
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit
) {
    val app = SingAlongApp.instance
    val currentTitle by app.controllerBridge.currentTrackTitle.collectAsState()
    val currentArtist by app.controllerBridge.currentArtist.collectAsState()
    val isPlaying by app.syncEngine.isPlayingFlow.collectAsState()
    val sourceName by app.syncEngine.sourceNameFlow.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            // Permission & Service Control Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Overlay Controls & Permissions", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (FloatingOverlayService.isRunning) Color(0xFF1DB954) else Color.Gray)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onStartOverlay,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Launch")
                        }
                        OutlinedButton(
                            onClick = onStopOverlay,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onRequestOverlayPermission,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text("Overlay", fontSize = 11.sp)
                        }
                        FilledTonalButton(
                            onClick = onRequestNotificationPermission,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text("Notify Sync", fontSize = 11.sp)
                        }
                        FilledTonalButton(
                            onClick = onRequestMicPermission,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text("Mic Catch", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        item {
            // Live Now Playing Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Now Playing Live Monitor",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.MusicNote else Icons.Default.Pause,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentTitle ?: "No Music Detected",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentArtist ?: "Start playback in any media player",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!sourceName.isNullOrBlank()) {
                                Text(
                                    text = "Synced via $sourceName",
                                    fontSize = 11.sp,
                                    color = Color(0xFF1DB954)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Transport controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { app.controllerBridge.skipToPrevious() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev")
                        }
                        FilledIconButton(
                            onClick = { app.controllerBridge.togglePlayPause() },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause"
                            )
                        }
                        IconButton(onClick = { app.controllerBridge.skipToNext() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
                        }
                    }
                }
            }
        }

        item {
            // Supported Universal Players Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Supported Universal Media Players", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Spotify • YouTube Music • Apple Music • JioSaavn • Amazon Music • Gaana • SoundCloud • Poweramp • VLC • YouTube App",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

enum class HistoryFilter {
    ALL,
    MEDIA_PLAYERS,
    AUDIO_CAPTURED
}

@Composable
fun ListeningHistoryTab(
    historyList: List<ListeningHistoryEntity>,
    onPlayOnYouTube: (trackTitle: String, artistName: String) -> Unit = { _, _ -> },
    onExportCsv: () -> Unit,
    onClearHistory: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }

    val audioCapturedCount = remember(historyList) { historyList.count { it.matchedViaFingerprint } }
    val mediaPlayersCount = remember(historyList) { historyList.count { !it.matchedViaFingerprint } }

    val displayedHistory = remember(historyList, selectedFilter) {
        when (selectedFilter) {
            HistoryFilter.ALL -> historyList
            HistoryFilter.MEDIA_PLAYERS -> historyList.filter { !it.matchedViaFingerprint }
            HistoryFilter.AUDIO_CAPTURED -> historyList.filter { it.matchedViaFingerprint }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Listening History (${historyList.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Deduplicated tracks with 1-tap YouTube play",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            Row {
                TextButton(onClick = onExportCsv) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", fontSize = 12.sp)
                }

                if (historyList.isNotEmpty()) {
                    TextButton(onClick = onClearHistory) {
                        Text("Clear", color = Color.Red, fontSize = 12.sp)
                    }
                }
            }
        }

        // Filter Chips Row: All, Media Players, Audio Captured
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == HistoryFilter.ALL,
                onClick = { selectedFilter = HistoryFilter.ALL },
                label = { Text("All (${historyList.size})", fontSize = 11.sp, fontWeight = if (selectedFilter == HistoryFilter.ALL) FontWeight.Bold else FontWeight.Normal) },
                leadingIcon = {
                    Icon(Icons.Default.QueueMusic, contentDescription = null, modifier = Modifier.size(15.dp))
                }
            )
            FilterChip(
                selected = selectedFilter == HistoryFilter.MEDIA_PLAYERS,
                onClick = { selectedFilter = HistoryFilter.MEDIA_PLAYERS },
                label = { Text("Media Players ($mediaPlayersCount)", fontSize = 11.sp, fontWeight = if (selectedFilter == HistoryFilter.MEDIA_PLAYERS) FontWeight.Bold else FontWeight.Normal) },
                leadingIcon = {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                }
            )
            FilterChip(
                selected = selectedFilter == HistoryFilter.AUDIO_CAPTURED,
                onClick = { selectedFilter = HistoryFilter.AUDIO_CAPTURED },
                label = { Text("Audio Captured ($audioCapturedCount)", fontSize = 11.sp, fontWeight = if (selectedFilter == HistoryFilter.AUDIO_CAPTURED) FontWeight.Bold else FontWeight.Normal) },
                leadingIcon = {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(15.dp))
                }
            )
        }

        if (displayedHistory.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (selectedFilter == HistoryFilter.AUDIO_CAPTURED) Icons.Default.GraphicEq else Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (selectedFilter == HistoryFilter.AUDIO_CAPTURED) "No captured audio yet" else "No tracks played yet",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Gray
                    )
                    Text(
                        text = if (selectedFilter == HistoryFilter.AUDIO_CAPTURED) "Tap the Audio Catch icon in the floating pill to identify ambient music" else "Play songs in YouTube Music, Spotify, or any player to track history",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(displayedHistory) { item ->
                    HistoryItemCard(
                        item = item,
                        onPlayOnYouTube = { onPlayOnYouTube(item.trackTitle, item.artistName) }
                    )
                }
            }
        }
    }
}

@Composable
fun AppSettingsTab(
    settings: OverlaySettings,
    onUpdateTheme: (OverlayThemeMode) -> Unit,
    onUpdateFontScale: (LyricFontScale) -> Unit,
    onUpdateOpacity: (Float) -> Unit,
    onToggleTranslation: (Boolean) -> Unit,
    onToggleRomanization: (Boolean) -> Unit,
    onToggleClickThrough: (Boolean) -> Unit,
    onUpdateProvider: (String) -> Unit
) {
    val app = SingAlongApp.instance
    val coroutineScope = rememberCoroutineScope()
    var cachedLyricsCount by remember { mutableIntStateOf(app.lyricsRepository.getCachedLyricsCount()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Text("Lyrics Providers", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "auto" to "Auto Best",
                    "jiosaavn" to "Bollywood",
                    "lrclib" to "LRCLIB",
                    "ytmusic" to "YouTube Music",
                    "genius" to "Genius",
                    "kugou" to "KuGou",
                    "netease" to "Netease",
                    "lyricsovh" to "Lyrics.ovh"
                ).forEach { (id, label) ->
                    val isSelected = settings.preferredProviderId == id
                    FilterChip(
                        selected = isSelected,
                        onClick = { onUpdateProvider(id) },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
        }

        item {
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Text("Overlay Appearance", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))

            // 2x2 Grid prevents label clipping on narrower screens
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeOptionCard(
                        label = "Amoled",
                        color = Color.Black,
                        isSelected = settings.themeMode == OverlayThemeMode.AMOLED_DARK,
                        onClick = { onUpdateTheme(OverlayThemeMode.AMOLED_DARK) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeOptionCard(
                        label = "Frosted",
                        color = Color(0xFF2C3240),
                        isSelected = settings.themeMode == OverlayThemeMode.FROSTED_GLASS,
                        onClick = { onUpdateTheme(OverlayThemeMode.FROSTED_GLASS) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeOptionCard(
                        label = "Neon",
                        color = Color(0xFF00FFFF),
                        isSelected = settings.themeMode == OverlayThemeMode.NEON_CYBERPUNK,
                        onClick = { onUpdateTheme(OverlayThemeMode.NEON_CYBERPUNK) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeOptionCard(
                        label = "Palette",
                        color = Color(0xFFE91E63),
                        isSelected = settings.themeMode == OverlayThemeMode.DYNAMIC_PALETTE,
                        onClick = { onUpdateTheme(OverlayThemeMode.DYNAMIC_PALETTE) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Text("Typography Scale", fontSize = 14.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LyricFontScale.values().forEach { scale ->
                    val isSelected = settings.fontScale == scale
                    FilterChip(
                        selected = isSelected,
                        onClick = { onUpdateFontScale(scale) },
                        label = { Text(scale.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp) }
                    )
                }
            }
        }

        item {
            Text("Background Opacity: ${(settings.backgroundOpacity * 100).toInt()}%", fontSize = 14.sp)
            Slider(
                value = settings.backgroundOpacity,
                onValueChange = onUpdateOpacity,
                valueRange = 0.2f..1.0f,
                steps = 8
            )
        }

        item {
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Text("Internationalization & Accessibility", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show Translated Sub-lines",
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    fontSize = 14.sp
                )
                Switch(checked = settings.isTranslationEnabled, onCheckedChange = onToggleTranslation)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show Romanized Pronunciation (Romaji/Pinyin)",
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    fontSize = 14.sp
                )
                Switch(checked = settings.isRomanizationEnabled, onCheckedChange = onToggleRomanization)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Click-Through Ghost Mode", fontSize = 14.sp)
                    Text("Taps pass directly to background apps", fontSize = 11.sp, color = Color.Gray)
                }
                Switch(checked = settings.isClickThroughEnabled, onCheckedChange = onToggleClickThrough)
            }
        }

        item {
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Text("Cache & Storage Management", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Lyrics In-Memory Cache", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("$cachedLyricsCount songs stored in RAM", fontSize = 12.sp, color = Color.Gray)
                        }
                        OutlinedButton(
                            onClick = {
                                app.lyricsRepository.clearCache()
                                cachedLyricsCount = 0
                                Toast.makeText(app, "Lyrics cache cleared!", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Clear Cache", fontSize = 11.sp)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Manual Sync Calibrations", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Saved per-track timing offsets", fontSize = 12.sp, color = Color.Gray)
                        }
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    app.database.historyDao().clearOffsets()
                                    Toast.makeText(app, "All timing calibrations reset!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Reset Offsets", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeOptionCard(
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

fun friendlyPlayerName(packageName: String): String {
    return when {
        packageName.contains("spotify") -> "Spotify"
        packageName.contains("youtube.music") -> "YT Music"
        packageName.contains("youtube") -> "YouTube"
        packageName.contains("apple") -> "Apple Music"
        packageName.contains("jiosaavn") || packageName.contains("saavn") -> "JioSaavn"
        packageName.contains("amazon") -> "Amazon Music"
        packageName.contains("gaana") -> "Gaana"
        packageName.contains("soundcloud") -> "SoundCloud"
        packageName.contains("kugou") -> "KuGou"
        packageName.contains("netease") -> "Netease"
        packageName.contains("googlequicksearchbox") -> "Google Sound Search"
        packageName.contains("ambient.audio") -> "Audio Capture"
        else -> "Media Player"
    }
}

@Composable
fun HistoryItemCard(
    item: ListeningHistoryEntity,
    onPlayOnYouTube: () -> Unit = {}
) {
    val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(item.timestamp))

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        border = if (item.matchedViaFingerprint) {
            androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00B4D8).copy(alpha = 0.40f))
        } else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.trackTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.matchedViaFingerprint) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF00B4D8).copy(alpha = 0.16f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = Color(0xFF00B4D8),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Audio Captured",
                                    color = Color(0xFF00B4D8),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Text(
                    text = item.artistName,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val playerLabel = if (item.matchedViaFingerprint) "Acoustic ID" else friendlyPlayerName(item.sourcePackage)
                    Text(
                        text = "$playerLabel • ${item.lyricSource ?: "No source"} • ${item.lyricSyncType}",
                        color = if (item.matchedViaFingerprint) Color(0xFF00B4D8) else Color(0xFF1DB954),
                        fontSize = 10.sp
                    )
                    if (item.userOffsetMs != 0L) {
                        Text(
                            text = " • Offset ${item.userOffsetMs / 1000.0}s",
                            color = Color(0xFF4E95FF),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = dateStr, color = Color.Gray, fontSize = 10.sp)

                // Direct Play to YouTube Button
                FilledTonalButton(
                    onClick = onPlayOnYouTube,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFFF0000).copy(alpha = 0.12f),
                        contentColor = Color(0xFFFF0000)
                    ),
                    modifier = Modifier.height(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play on YouTube",
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(text = "YouTube", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
