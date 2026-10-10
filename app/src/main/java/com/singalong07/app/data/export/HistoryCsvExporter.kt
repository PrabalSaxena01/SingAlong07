package com.singalong07.app.data.export

import android.content.Context
import android.net.Uri
import com.singalong07.app.data.db.ListeningHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryCsvExporter(private val context: Context? = null) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * Converts a list of history entities into standard RFC-4180 CSV string format.
     */
    fun generateCsvString(historyList: List<ListeningHistoryEntity>): String {
        val sb = StringBuilder()
        sb.append("ID,Timestamp,Track,Artist,Album,Duration_Sec,Player_App,Lyric_Source,Sync_Type,Offset_Ms,Audio_Fingerprinted\n")

        historyList.forEach { item ->
            val fields = listOf(
                item.id.toString(),
                dateFormat.format(Date(item.timestamp)),
                escapeCsvField(item.trackTitle),
                escapeCsvField(item.artistName),
                escapeCsvField(item.albumName ?: ""),
                (item.durationMs / 1000).toString(),
                escapeCsvField(item.sourcePackage),
                escapeCsvField(item.lyricSource ?: "None"),
                item.lyricSyncType,
                item.userOffsetMs.toString(),
                item.matchedViaFingerprint.toString()
            )
            sb.append(fields.joinToString(",")).append("\n")
        }
        return sb.toString()
    }

    /**
     * Exports history directly to a target Android Storage Access Framework (SAF) Uri.
     */
    suspend fun exportToUri(historyList: List<ListeningHistoryEntity>, outputUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val ctx = context ?: throw IllegalStateException("Context required to write to SAF Uri")
            val outputStream: OutputStream = ctx.contentResolver.openOutputStream(outputUri)
                ?: throw IllegalStateException("Could not open output stream for $outputUri")

            outputStream.use { stream ->
                BufferedWriter(OutputStreamWriter(stream, Charsets.UTF_8)).use { writer ->
                    writer.write(generateCsvString(historyList))
                    writer.flush()
                }
            }
        }
    }

    /**
     * RFC-4180 CSV escaping for quotes, commas, and line breaks,
     * with OWASP CSV Injection (Formula Injection) mitigation.
     */
    fun escapeCsvField(field: String): String {
        var sanitized = field
        // OWASP mitigation: Prevent Excel/Calc DDE formula execution
        if (sanitized.startsWith("=") || sanitized.startsWith("+") || sanitized.startsWith("-") || 
            sanitized.startsWith("@") || sanitized.startsWith("\t") || sanitized.startsWith("\r")
        ) {
            sanitized = "'$sanitized"
        }

        return if (sanitized.contains(",") || sanitized.contains("\"") || sanitized.contains("\n") || sanitized.contains("\r")) {
            "\"" + sanitized.replace("\"", "\"\"") + "\""
        } else {
            sanitized
        }
    }
}
