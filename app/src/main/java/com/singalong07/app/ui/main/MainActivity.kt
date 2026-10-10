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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MainScreen(
                    hasOverlayPermission = Settings.canDrawOverlays(this),
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onRequestNotificationPermission = { requestNotificationPermission() },
                    onStartOverlay = { startOverlayService() },
                    onStopOverlay = { stopOverlayService() }
                )
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
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit
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
                    if (selectedTab == 0) {
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
                    icon = { Icon(Icons.Default.QueueMusic, contentDescription = "History") },
                    label = { Text("History & Player") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("App Settings") }
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
            if (selectedTab == 0) {
                // Tab 0: History & Service Controls
                HistoryAndControlsTab(
                    historyList = historyList,
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onStartOverlay = onStartOverlay,
                    onStopOverlay = onStopOverlay,
                    onExportCsv = {
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        exportCsvLauncher.launch("singalong07_history_$timestamp.csv")
                    },
                    onClearHistory = {
                        coroutineScope.launch { app.database.historyDao().clearHistory() }
                    }
                )
            } else {
                // Tab 1: Full In-App Settings & Providers
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

@Composable
fun HistoryAndControlsTab(
    historyList: List<ListeningHistoryEntity>,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onExportCsv: () -> Unit,
    onClearHistory: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Permission & Service Control Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Overlay Controls & Permissions", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                        Text("Launch Overlay")
                    }
                    OutlinedButton(
                        onClick = onStopOverlay,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Stop Overlay")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onRequestOverlayPermission,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Overlay Access", fontSize = 11.sp)
                    }
                    FilledTonalButton(
                        onClick = onRequestNotificationPermission,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Notification Sync", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // History Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Listening History (${historyList.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row {
                TextButton(onClick = onExportCsv) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export CSV", fontSize = 12.sp)
                }

                if (historyList.isNotEmpty()) {
                    TextButton(onClick = onClearHistory) {
                        Text("Clear", color = Color.Red, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // History List
        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Play music in Spotify, YouTube, or Apple Music to start syncing lyrics!",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(historyList) { item ->
                    HistoryItemCard(item)
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Text("Lyrics Providers", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "auto" to "Auto Best",
                    "jiosaavn" to "Bollywood",
                    "lrclib" to "LRCLIB",
                    "netease" to "Netease"
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

        item {
            Text("Typography Scale", fontSize = 14.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show Translated Sub-lines", fontSize = 14.sp)
                Switch(checked = settings.isTranslationEnabled, onCheckedChange = onToggleTranslation)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show Romanized Pronunciation (Romaji/Pinyin)", fontSize = 14.sp)
                Switch(checked = settings.isRomanizationEnabled, onCheckedChange = onToggleRomanization)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Click-Through Ghost Mode", fontSize = 14.sp)
                    Text("Taps pass directly to background apps", fontSize = 11.sp, color = Color.Gray)
                }
                Switch(checked = settings.isClickThroughEnabled, onCheckedChange = onToggleClickThrough)
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

@Composable
fun HistoryItemCard(item: ListeningHistoryEntity) {
    val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(item.timestamp))

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.trackTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(text = item.artistName, color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${item.lyricSource ?: "No source"} • ${item.lyricSyncType}",
                        color = Color(0xFF1DB954),
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
            Text(text = dateStr, color = Color.Gray, fontSize = 11.sp)
        }
    }
}
