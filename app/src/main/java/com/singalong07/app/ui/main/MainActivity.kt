package com.singalong07.app.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.singalong07.app.SingAlongApp
import com.singalong07.app.data.db.ListeningHistoryEntity
import com.singalong07.app.data.export.HistoryCsvExporter
import com.singalong07.app.service.overlay.FloatingOverlayService
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
    val coroutineScope = rememberCoroutineScope()

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
                    IconButton(onClick = {
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        exportCsvLauncher.launch("singalong07_history_$timestamp.csv")
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export CSV", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Permission & Service Control Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Service Controls & Permissions", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                    TextButton(onClick = {
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        exportCsvLauncher.launch("singalong07_history_$timestamp.csv")
                    }) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export CSV", fontSize = 12.sp)
                    }

                    if (historyList.isNotEmpty()) {
                        TextButton(onClick = {
                            coroutineScope.launch { app.database.historyDao().clearHistory() }
                        }) {
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
