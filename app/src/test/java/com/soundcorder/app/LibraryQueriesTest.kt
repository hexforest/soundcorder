package com.soundcorder.app

import com.soundcorder.app.data.Journey
import com.soundcorder.app.data.LibraryData
import com.soundcorder.app.data.Project
import com.soundcorder.app.data.Recording
import com.soundcorder.app.data.RecordingSource
import com.soundcorder.app.data.countInProject
import com.soundcorder.app.data.recordingsInJourney
import com.soundcorder.app.data.recordingsInProject
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryQueriesTest {

    private fun rec(id: String, createdAt: Long) = Recording(
        id = id,
        title = id,
        fileName = "$id.m4a",
        durationMs = 1000,
        sizeBytes = 1000,
        mimeType = "audio/mp4",
        createdAt = createdAt,
        source = RecordingSource.RECORDED,
    )

    private val library = LibraryData(
        projects = listOf(Project("p1", "One", 0), Project("p2", "Two", 0)),
        journeys = listOf(Journey("j1", "Trip", 0, recordingIds = listOf("c", "a", "missing"))),
        recordings = listOf(rec("a", 100), rec("b", 300), rec("c", 200)),
        homeProject = mapOf("a" to "p1", "b" to "p1", "c" to "p2"),
    )

    @Test
    fun recordingsInProject_filtersAndSortsNewestFirst() {
        assertEquals(listOf("b", "a"), library.recordingsInProject("p1").map { it.id })
        assertEquals(listOf("c"), library.recordingsInProject("p2").map { it.id })
    }

    @Test
    fun recordingsInJourney_keepsOrderAndDropsMissing() {
        assertEquals(listOf("c", "a"), library.recordingsInJourney("j1").map { it.id })
    }

    @Test
    fun recordingsInJourney_unknownJourneyIsEmpty() {
        assertEquals(emptyList<String>(), library.recordingsInJourney("nope").map { it.id })
    }

    @Test
    fun countInProject_countsHomeProjectOnly() {
        assertEquals(2, library.countInProject("p1"))
        assertEquals(1, library.countInProject("p2"))
        assertEquals(0, library.countInProject("p3"))
    }
}
