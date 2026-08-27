package com.soundcorder.app.data

import kotlinx.serialization.Serializable

/**
 * The recorded (or imported) sound itself is the artifact. Soundcorder never transcribes it — a
 * [Recording] is audio, kept and revisited as audio.
 */
@Serializable
data class Recording(
    val id: String,
    val title: String,
    /** File name inside the app's `recordings/` directory. */
    val fileName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val createdAt: Long,
    val source: RecordingSource,
)

@Serializable
enum class RecordingSource { RECORDED, IMPORTED }

/** A body of work. Every recording has exactly one home project. */
@Serializable
data class Project(
    val id: String,
    val name: String,
    val createdAt: Long,
)

/** An ordered selection of recordings — a trip, a theme, a sequence to revisit together. */
@Serializable
data class Journey(
    val id: String,
    val name: String,
    val createdAt: Long,
    val recordingIds: List<String> = emptyList(),
)

@Serializable
data class LibraryData(
    val version: Int = 1,
    val projects: List<Project> = emptyList(),
    val journeys: List<Journey> = emptyList(),
    val recordings: List<Recording> = emptyList(),
    /** recordingId -> home projectId. */
    val homeProject: Map<String, String> = emptyMap(),
    /** Project a new recording lands in by default. */
    val lastProjectId: String? = null,
)
