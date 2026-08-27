package com.soundcorder.app.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.soundcorder.app.audio.PlaybackState
import com.soundcorder.app.data.Journey
import com.soundcorder.app.data.LibraryData
import com.soundcorder.app.data.Project
import com.soundcorder.app.data.Recording
import com.soundcorder.app.data.projectsNewestFirst
import com.soundcorder.app.data.recordingsInJourney
import com.soundcorder.app.data.recordingsInProject
import com.soundcorder.app.ui.SoundcorderViewModel
import com.soundcorder.app.ui.components.ConfirmDialog
import com.soundcorder.app.ui.components.EmptyState
import com.soundcorder.app.ui.components.PickRecordingsDialog
import com.soundcorder.app.ui.components.RecordingRow
import com.soundcorder.app.ui.components.RowAction
import com.soundcorder.app.ui.components.SingleChoiceDialog
import com.soundcorder.app.ui.components.TextPromptDialog

@Composable
fun ProjectDetailBody(
    project: Project,
    library: LibraryData,
    playback: PlaybackState,
    vm: SoundcorderViewModel,
    onImport: () -> Unit,
    onShare: (Recording) -> Unit,
    contentPadding: PaddingValues,
) {
    val recordings = remember(library, project.id) { library.recordingsInProject(project.id) }

    var renaming by remember { mutableStateOf<Recording?>(null) }
    var deleting by remember { mutableStateOf<Recording?>(null) }
    var addingToJourney by remember { mutableStateOf<Recording?>(null) }
    var moving by remember { mutableStateOf<Recording?>(null) }

    if (recordings.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.GraphicEq,
            title = "No sounds yet",
            message = "Tap the mic to record into “${project.name}”, or bring in an audio file you already have.",
            action = { Button(onClick = onImport) { Text("Import audio") } },
        )
    } else {
        LazyColumn(contentPadding = contentPadding) {
            items(recordings, key = { it.id }) { rec ->
                val isCurrent = playback.recordingId == rec.id
                RecordingRow(
                    recording = rec,
                    isCurrent = isCurrent,
                    isPlaying = playback.isPlaying,
                    progress = if (isCurrent) playback.progress else 0f,
                    onPlayPause = { vm.playPause(rec) },
                    actions = listOf(
                        RowAction("Rename") { renaming = rec },
                        RowAction("Add to journey") { addingToJourney = rec },
                        RowAction("Move to project") { moving = rec },
                        RowAction("Share") { onShare(rec) },
                        RowAction("Delete", destructive = true) { deleting = rec },
                    ),
                )
                HorizontalDivider()
            }
        }
    }

    renaming?.let { rec ->
        TextPromptDialog(
            title = "Rename recording",
            label = "Title",
            initialValue = rec.title,
            onConfirm = {
                vm.renameRecording(rec.id, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { rec ->
        ConfirmDialog(
            title = "Delete “${rec.title}”?",
            message = "The audio file is removed from this device. This can't be undone.",
            onConfirm = {
                vm.deleteRecording(rec.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }

    addingToJourney?.let { rec ->
        val journeys = library.journeys.sortedByDescending { it.createdAt }
        if (journeys.isEmpty()) {
            ConfirmDialog(
                title = "No journeys yet",
                message = "Create a journey first from the Journeys tab, then add sounds to it.",
                confirmLabel = "OK",
                onConfirm = { addingToJourney = null },
                onDismiss = { addingToJourney = null },
            )
        } else {
            SingleChoiceDialog(
                title = "Add “${rec.title}” to…",
                options = journeys.map { it.id to it.name },
                onSelect = { journeyId ->
                    vm.addToJourney(journeyId, listOf(rec.id))
                    addingToJourney = null
                },
                onDismiss = { addingToJourney = null },
            )
        }
    }

    moving?.let { rec ->
        val options = library.projectsNewestFirst()
            .filter { it.id != project.id }
            .map { it.id to it.name }
        if (options.isEmpty()) {
            ConfirmDialog(
                title = "Only one project",
                message = "Create another project to move recordings between them.",
                confirmLabel = "OK",
                onConfirm = { moving = null },
                onDismiss = { moving = null },
            )
        } else {
            SingleChoiceDialog(
                title = "Move “${rec.title}” to…",
                options = options,
                onSelect = { projectId ->
                    vm.moveRecording(rec.id, projectId)
                    moving = null
                },
                onDismiss = { moving = null },
            )
        }
    }
}

@Composable
fun JourneyDetailBody(
    journey: Journey,
    library: LibraryData,
    playback: PlaybackState,
    vm: SoundcorderViewModel,
    onShare: (Recording) -> Unit,
    picking: Boolean,
    onPickingChange: (Boolean) -> Unit,
    contentPadding: PaddingValues,
) {
    val recordings = remember(library, journey.id) { library.recordingsInJourney(journey.id) }

    if (recordings.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Route,
            title = "This journey is empty",
            message = "Add sounds you've already recorded to build a sequence you can revisit together.",
            action = { Button(onClick = { onPickingChange(true) }) { Text("Add recordings") } },
        )
    } else {
        LazyColumn(contentPadding = contentPadding) {
            items(recordings, key = { it.id }) { rec ->
                val isCurrent = playback.recordingId == rec.id
                RecordingRow(
                    recording = rec,
                    isCurrent = isCurrent,
                    isPlaying = playback.isPlaying,
                    progress = if (isCurrent) playback.progress else 0f,
                    onPlayPause = { vm.playPause(rec) },
                    actions = listOf(
                        RowAction("Share") { onShare(rec) },
                        RowAction("Remove from journey", destructive = true) {
                            vm.removeFromJourney(journey.id, rec.id)
                        },
                    ),
                )
                HorizontalDivider()
            }
        }
    }

    if (picking) {
        val inJourney = journey.recordingIds.toSet()
        PickRecordingsDialog(
            title = "Add to “${journey.name}”",
            candidates = library.recordings
                .filterNot { it.id in inJourney }
                .sortedByDescending { it.createdAt },
            onConfirm = { ids ->
                if (ids.isNotEmpty()) vm.addToJourney(journey.id, ids)
                onPickingChange(false)
            },
            onDismiss = { onPickingChange(false) },
        )
    }
}
