package com.soundcorder.app.data

/** Pure derivations over [LibraryData], shared by the repository and Compose screens. */

fun LibraryData.project(id: String?): Project? = projects.find { it.id == id }

fun LibraryData.journey(id: String?): Journey? = journeys.find { it.id == id }

fun LibraryData.recording(id: String?): Recording? = recordings.find { it.id == id }

fun LibraryData.recordingsInProject(projectId: String): List<Recording> =
    recordings
        .filter { homeProject[it.id] == projectId }
        .sortedByDescending { it.createdAt }

fun LibraryData.recordingsInJourney(journeyId: String): List<Recording> {
    val j = journeys.find { it.id == journeyId } ?: return emptyList()
    val byId = recordings.associateBy { it.id }
    return j.recordingIds.mapNotNull { byId[it] }
}

fun LibraryData.countInProject(projectId: String): Int =
    recordings.count { homeProject[it.id] == projectId }

fun LibraryData.projectsNewestFirst(): List<Project> = projects.sortedByDescending { it.createdAt }

fun LibraryData.journeysNewestFirst(): List<Journey> = journeys.sortedByDescending { it.createdAt }

fun LibraryData.homeProjectOf(recordingId: String): String? = homeProject[recordingId]
