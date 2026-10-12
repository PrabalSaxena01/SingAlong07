package com.singalong07.app.domain.audio

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AudioMatchResult(
    val title: String,
    val artist: String,
    val album: String?,
    val isMatched: Boolean
)

/**
 * Audio Capture Manager for identifying songs playing outside media players
 * (e.g. Instagram Reels, YouTube Shorts, games, radio, or ambient environment).
 *
 * Supports both:
 * 1. Google Sound Search Activity Intent integration.
 * 2. In-App Acoustic Fingerprint Recognition.
 */
class AudioCaptureManager {

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _statusText = MutableStateFlow<String?>(null)
    val statusText: StateFlow<String?> = _statusText.asStateFlow()

    fun hasRecordPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Directly triggers Google Sound Search / Music Search intent in a very small
     * floating mini-window launcher (Shazam-style) and auto-extracts via Accessibility.
     */
    fun launchGoogleMusicSearch(context: Context): Boolean {
        if (!com.singalong07.app.service.accessibility.SingAlongAccessibilityService.isAccessibilityEnabled(context)) {
            android.widget.Toast.makeText(
                context,
                "Tip: Enable SingAlong07 in Accessibility to auto-extract & auto-close Google seamlessly!",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }

        val metrics = context.resources.displayMetrics
        val density = metrics.density
        // Very small compact dimensions (Shazam pop-up style): 220dp x 180dp
        val targetWidthPx = (220 * density).toInt().coerceIn((metrics.widthPixels * 0.35).toInt(), (metrics.widthPixels * 0.55).toInt())
        val targetHeightPx = (180 * density).toInt().coerceIn((metrics.heightPixels * 0.18).toInt(), (metrics.heightPixels * 0.28).toInt())
        val left = (metrics.widthPixels - targetWidthPx) / 2
        val top = (metrics.heightPixels - targetHeightPx - (40 * density).toInt()).coerceAtLeast(0)
        val bounds = android.graphics.Rect(left, top, left + targetWidthPx, top + targetHeightPx)

        val activityOptions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.app.ActivityOptions.makeBasic().apply {
                setLaunchBounds(bounds)
            }.toBundle()
        } else null

        val candidates = listOf(
            Intent("com.google.android.googlequicksearchbox.MUSIC_SEARCH").apply {
                setPackage("com.google.android.googlequicksearchbox")
            },
            Intent().apply {
                component = android.content.ComponentName(
                    "com.google.android.googlequicksearchbox",
                    "com.google.android.googlequicksearchbox.MusicSearchActivity"
                )
            },
            Intent().apply {
                component = android.content.ComponentName(
                    "com.google.android.googlequicksearchbox",
                    "com.google.android.apps.gsa.soundsearch.SoundSearchActivity"
                )
            },
            Intent(Intent.ACTION_VIEW, Uri.parse("googleapp://soundsearch")),
            Intent("com.google.android.googlequicksearchbox.MUSIC_SEARCH"),
            Intent("com.google.android.gms.actions.SEARCH_ACTION").apply {
                putExtra("query", "What's this song?")
            },
            Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, "What song is this?")
            }
        )

        for (intent in candidates) {
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            // OEM floating mini-window extras (Samsung One UI, Xiaomi HyperOS/MIUI, ColorOS)
            intent.putExtra("android.intent.extra.WINDOW_MODE", 5) // Freeform
            intent.putExtra("com.samsung.android.intent.extra.WINDOW_MODE", 5)
            intent.putExtra("miui.intent.extra.FREEFORM_LAUNCH", true)
            intent.putExtra("miui.intent.extra.OPEN_IN_MINI_WINDOW", true)
            intent.putExtra("oppo.intent.extra.OPEN_IN_MINI_WINDOW", true)

            try {
                val activities = context.packageManager.queryIntentActivities(intent, 0)
                if (activities.isNotEmpty()) {
                    context.startActivity(intent, activityOptions)
                    return true
                }
            } catch (_: Exception) {}
        }

        // Direct launch fallback without package manager query
        for (intent in candidates) {
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            intent.putExtra("android.intent.extra.WINDOW_MODE", 5)
            intent.putExtra("com.samsung.android.intent.extra.WINDOW_MODE", 5)
            intent.putExtra("miui.intent.extra.FREEFORM_LAUNCH", true)
            try {
                context.startActivity(intent, activityOptions)
                return true
            } catch (_: Exception) {}
        }
        return false
    }

    /**
     * Records a 5-second PCM snippet from the device microphone / speakers,
     * packages it as a valid WAV byte array, and sends it to acoustic recognition.
     */
    suspend fun captureAndIdentify(context: Context): Result<AudioMatchResult> = withContext(Dispatchers.IO) {
        if (!hasRecordPermission(context)) {
            _statusText.value = "Microphone permission required"
            return@withContext Result.failure(SecurityException("RECORD_AUDIO permission not granted"))
        }

        _isCapturing.value = true
        _statusText.value = "Listening to audio..."

        try {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = minBufferSize.coerceAtLeast(sampleRate * 2)

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                _statusText.value = "Audio recorder unavailable"
                return@withContext Result.failure(IllegalStateException("AudioRecord initialization failed"))
            }

            val pcmStream = ByteArrayOutputStream()
            val buffer = ByteArray(2048)
            val captureDurationMs = 5000L
            val startTime = System.currentTimeMillis()

            try {
                record.startRecording()
                while (System.currentTimeMillis() - startTime < captureDurationMs) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        pcmStream.write(buffer, 0, read)
                    }
                }
            } finally {
                try {
                    record.stop()
                    record.release()
                } catch (_: Exception) {}
            }

            _statusText.value = "Analyzing acoustic fingerprint..."
            val rawPcm = pcmStream.toByteArray()
            if (rawPcm.isEmpty()) {
                _statusText.value = "No audio captured"
                return@withContext Result.failure(IllegalStateException("Empty audio buffer"))
            }

            val wavBytes = writeWavHeader(rawPcm, sampleRate, 1, 16)
            val result = queryAudDApi(wavBytes)
            if (result != null) {
                _statusText.value = "Matched: ${result.title}"
                Result.success(result)
            } else {
                _statusText.value = "No match found"
                Result.failure(NoSuchElementException("Song could not be identified from acoustic capture"))
            }
        } catch (e: Exception) {
            _statusText.value = "Capture failed: ${e.message}"
            Result.failure(e)
        } finally {
            _isCapturing.value = false
        }
    }

    private fun queryAudDApi(wavBytes: ByteArray): AudioMatchResult? {
        val boundary = "===Boundary" + System.currentTimeMillis() + "==="
        val lineEnd = "\r\n"
        val twoHyphens = "--"

        var connection: HttpURLConnection? = null
        return try {
            val url = URL("https://api.audd.io/?api_token=test")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
                doInput = true
                useCaches = false
                setRequestProperty("Connection", "Keep-Alive")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("User-Agent", "SingAlong07/1.0 (Android)")
            }

            connection.outputStream.use { outputStream ->
                val dos = DataOutputStream(outputStream)

                // Part: file
                dos.writeBytes(twoHyphens + boundary + lineEnd)
                dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"snippet.wav\"" + lineEnd)
                dos.writeBytes("Content-Type: audio/wav" + lineEnd)
                dos.writeBytes(lineEnd)
                dos.write(wavBytes)
                dos.writeBytes(lineEnd)

                // Part: return
                dos.writeBytes(twoHyphens + boundary + lineEnd)
                dos.writeBytes("Content-Disposition: form-data; name=\"return\"" + lineEnd)
                dos.writeBytes(lineEnd)
                dos.writeBytes("lyrics,timecode" + lineEnd)

                // End boundary
                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
                dos.flush()
            }

            if (connection.responseCode in 200..299) {
                val responseStr = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseStr)
                if (json.optString("status") == "success") {
                    val resultObj = json.optJSONObject("result") ?: return null
                    val title = resultObj.optString("title", "").trim()
                    val artist = resultObj.optString("artist", "").trim()
                    val album = resultObj.optString("album", "").trim().takeIf { it.isNotBlank() }

                    if (title.isNotBlank()) {
                        AudioMatchResult(
                            title = title,
                            artist = artist.ifBlank { "Unknown Artist" },
                            album = album,
                            isMatched = true
                        )
                    } else null
                } else null
            } else null
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun writeWavHeader(pcmData: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val totalAudioLen = pcmData.size
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()

        val wavOut = ByteArray(header.size + pcmData.size)
        System.arraycopy(header, 0, wavOut, 0, header.size)
        System.arraycopy(pcmData, 0, wavOut, header.size, pcmData.size)
        return wavOut
    }
}
