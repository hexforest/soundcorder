package com.soundcorder.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Single source of truth for the sound library. Metadata is a JSON file in `filesDir`; the audio
 * files sit alongside it in `recordings/`. Deliberately no database and no cloud — import and export
 * are free, nothing is capped, nothing is locked in.
 */
class AudioLibrary(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    val recordingsDir: File = File(context.filesDir, "recordings").apply { mkdirs() }
    private val sharedDir: File = File(context.cacheDir, "shared").apply { mkdirs() }
    private val dataFile: File = File(context.filesDir, "library.json")

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val _data = MutableStateFlow(loadOrCreate())
    val data: StateFlow<LibraryData> = _data.asStateFlow()

    val snapshot: LibraryData get() = _data.value

    // --- reads ----------------------------------------------------------------

    fun fileFor(recording: Recording): File = File(recordingsDir, recording.fileName)

    fun defaultProjectId(): String {
        val d = _data.value
        return d.lastProjectId?.takeIf { id -> d.projects.any { it.id == id } }
            ?: d.projects.firstOrNull()?.id
            ?: createProject("My Sounds").id
    }

    // --- recording ingest ---------------------------------------------------

    fun newRecordingFile(extension: String): File =
        File(recordingsDir, "rec_${System.currentTimeMillis()}_${shortId()}.$extension")

    fun addRecording(
        file: File,
        durationMs: Long,
        mimeType: String,
        projectId: String,
        title: String,
        source: RecordingSource,
    ): Recording {
        val rec = Recording(
            id = UUID.randomUUID().toString(),
            title = title.trim().ifBlank { nextDefaultTitle() },
            fileName = file.name,
            durationMs = durationMs,
            sizeBytes = file.length(),
            mimeType = mimeType,
            createdAt = System.currentTimeMillis(),
            source = source,
        )
        mutate { d ->
            d.copy(
                recordings = d.recordings + rec,
                homeProject = d.homeProject + (rec.id to projectId),
                lastProjectId = projectId,
            )
        }
        return rec
    }

    suspend fun importAudio(uri: Uri, projectId: String): Recording? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "audio/*"
        val displayName = queryDisplayName(uri)
        val ext = extensionFor(displayName, mime)
        val dest = File(recordingsDir, "imp_${System.currentTimeMillis()}_${shortId()}.$ext")
        try {
            resolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
        } catch (t: Throwable) {
            dest.delete()
            return@withContext null
        }
        if (dest.length() == 0L) {
            dest.delete()
            return@withContext null
        }
        val duration = durationOf(dest)
        val title = (displayName ?: dest.name).substringBeforeLast('.')
        addRecording(dest, duration, mime, projectId, title, RecordingSource.IMPORTED)
    }

    // --- recordings ----------------------------------------------------------

    fun renameRecording(id: String, title: String) = mutate { d ->
        d.copy(recordings = d.recordings.map { if (it.id == id) it.copy(title = title.trim()) else it })
    }

    fun deleteRecording(id: String) {
        _data.value.recordings.find { it.id == id }?.let { File(recordingsDir, it.fileName).delete() }
        mutate { d ->
            d.copy(
                recordings = d.recordings.filterNot { it.id == id },
                homeProject = d.homeProject - id,
                journeys = d.journeys.map { it.copy(recordingIds = it.recordingIds - id) },
            )
        }
    }

    fun moveRecording(id: String, projectId: String) = mutate { d ->
        d.copy(homeProject = d.homeProject + (id to projectId))
    }

    // --- projects ----------------------------------------------------------

    fun createProject(name: String): Project {
        val p = Project(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Untitled project" },
            createdAt = System.currentTimeMillis(),
        )
        mutate { d -> d.copy(projects = d.projects + p) }
        return p
    }

    fun renameProject(id: String, name: String) = mutate { d ->
        d.copy(projects = d.projects.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    /** Removes the project and every recording that lived in it, files included. */
    fun deleteProject(id: String) {
        val d0 = _data.value
        val doomed = d0.recordings.filter { d0.homeProject[it.id] == id }
        doomed.forEach { File(recordingsDir, it.fileName).delete() }
        val doomedIds = doomed.map { it.id }.toSet()
        mutate { d ->
            d.copy(
                projects = d.projects.filterNot { it.id == id },
                recordings = d.recordings.filterNot { it.id in doomedIds },
                homeProject = d.homeProject.filterKeys { it !in doomedIds },
                journeys = d.journeys.map { j -> j.copy(recordingIds = j.recordingIds.filterNot { it in doomedIds }) },
                lastProjectId = d.lastProjectId?.takeIf { it != id },
            )
        }
    }

    // --- journeys --------------------------------------------------------

    fun createJourney(name: String): Journey {
        val j = Journey(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Untitled journey" },
            createdAt = System.currentTimeMillis(),
        )
        mutate { d -> d.copy(journeys = d.journeys + j) }
        return j
    }

    fun renameJourney(id: String, name: String) = mutate { d ->
        d.copy(journeys = d.journeys.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    fun deleteJourney(id: String) = mutate { d ->
        d.copy(journeys = d.journeys.filterNot { it.id == id })
    }

    fun addToJourney(journeyId: String, recordingIds: List<String>) = mutate { d ->
        d.copy(journeys = d.journeys.map { j ->
            if (j.id != journeyId) j
            else j.copy(recordingIds = j.recordingIds + recordingIds.filterNot { it in j.recordingIds })
        })
    }

    fun removeFromJourney(journeyId: String, recordingId: String) = mutate { d ->
        d.copy(journeys = d.journeys.map { j ->
            if (j.id != journeyId) j else j.copy(recordingIds = j.recordingIds - recordingId)
        })
    }

    // --- sharing --------------------------------------------------------

    /** Copies a recording into cache under a readable name, ready to hand to a share sheet. */
    suspend fun exportCopy(recording: Recording): File = withContext(Dispatchers.IO) {
        sharedDir.listFiles()?.forEach { it.delete() }
        val safe = recording.title.replace(Regex("[^A-Za-z0-9 _-]"), "_").trim().ifBlank { "recording" }
        val ext = recording.fileName.substringAfterLast('.', "m4a")
        val out = File(sharedDir, "$safe.$ext")
        File(recordingsDir, recording.fileName).copyTo(out, overwrite = true)
        out
    }

    // --- internals -----------------------------------------------------

    private inline fun mutate(block: (LibraryData) -> LibraryData) {
        val next = block(_data.value)
        _data.value = next
        scope.launch(Dispatchers.IO) { runCatching { dataFile.writeText(json.encodeToString(next)) } }
    }

    private fun loadOrCreate(): LibraryData {
        val loaded = runCatching {
            if (dataFile.exists()) json.decodeFromString<LibraryData>(dataFile.readText()) else null
        }.getOrNull()
        if (loaded != null && loaded.projects.isNotEmpty()) return loaded
        val first = Project(UUID.randomUUID().toString(), "My Sounds", System.currentTimeMillis())
        val data = (loaded ?: LibraryData()).copy(projects = listOf(first), lastProjectId = first.id)
        runCatching { dataFile.writeText(json.encodeToString(data)) }
        return data
    }

    private fun durationOf(file: File): Long {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(file.absolutePath)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (t: Throwable) {
            0L
        } finally {
            mmr.release()
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    private fun extensionFor(displayName: String?, mime: String): String {
        displayName?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() && it.length <= 5 }
            ?.let { return it.lowercase() }
        return when (mime) {
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/mp4", "audio/aac", "audio/m4a", "audio/x-m4a" -> "m4a"
            "audio/ogg", "application/ogg" -> "ogg"
            "audio/opus" -> "opus"
            "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/3gpp" -> "3gp"
            else -> "audio"
        }
    }

    private fun nextDefaultTitle(): String = "Sound ${_data.value.recordings.size + 1}"

    private fun shortId(): String = UUID.randomUUID().toString().substring(0, 8)
}
