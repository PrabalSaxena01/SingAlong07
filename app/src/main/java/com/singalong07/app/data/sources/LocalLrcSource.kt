package com.singalong07.app.data.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalLrcSource(
    private val localLyricsDirectory: File? = null
) : LyricsSource {
    override val id: String = "local_lrc"
    override val displayName: String = "Local Storage (.lrc file)"
    override val supportsSynced: Boolean = true
    override val supportsTranslations: Boolean = false
    override val requiresApiKey: Boolean = false

    override suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Long?
    ): Result<LyricsResult> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = localLyricsDirectory
                ?: throw NoSuchElementException("No local lyrics directory configured")

            if (!dir.exists() || !dir.isDirectory) {
                throw NoSuchElementException("Local directory does not exist: ${dir.absolutePath}")
            }

            val sanitizedTitle = title.replace(Regex("""[\\/:*?"<>|]"""), "")
            val sanitizedArtist = artist.replace(Regex("""[\\/:*?"<>|]"""), "")

            val candidateNames = listOf(
                "$sanitizedArtist - $sanitizedTitle.lrc",
                "$sanitizedTitle - $sanitizedArtist.lrc",
                "$sanitizedTitle.lrc"
            )

            var foundFile: File? = null
            for (candidate in candidateNames) {
                val f = File(dir, candidate)
                if (f.exists() && f.isFile) {
                    foundFile = f
                    break
                }
            }

            if (foundFile == null) {
                val allLrcFiles = dir.listFiles { file -> file.extension.equals("lrc", ignoreCase = true) }
                foundFile = allLrcFiles?.firstOrNull { file ->
                    val lower = file.nameWithoutExtension.lowercase()
                    lower.contains(sanitizedTitle.lowercase()) &&
                    (sanitizedArtist.isBlank() || lower.contains(sanitizedArtist.lowercase()))
                }
            }

            if (foundFile == null) {
                throw NoSuchElementException("No matching .lrc file found locally for $artist - $title")
            }

            val lrcContent = foundFile.readText(Charsets.UTF_8)
            LyricsResult(
                sourceName = displayName,
                trackTitle = title,
                artistName = artist,
                syncedLrc = lrcContent,
                sourceConfidence = 1.0f
            )
        }
    }
}
