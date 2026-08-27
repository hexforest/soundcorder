package com.soundcorder.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.soundcorder.app.data.LibraryData
import com.soundcorder.app.data.countInProject
import com.soundcorder.app.data.journeysNewestFirst
import com.soundcorder.app.data.projectsNewestFirst
import com.soundcorder.app.ui.components.EmptyState

@Composable
fun ProjectListBody(
    library: LibraryData,
    onOpen: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val projects = library.projectsNewestFirst()
    if (projects.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.FolderOpen,
            title = "No projects yet",
            message = "A project is a home for related sounds. Add one to get started.",
        )
        return
    }
    LazyColumn(contentPadding = contentPadding) {
        items(projects, key = { it.id }) { project ->
            ListItem(
                headlineContent = { Text(project.name) },
                supportingContent = { Text(countLabel(library.countInProject(project.id), "sound")) },
                leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { onOpen(project.id) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun JourneyListBody(
    library: LibraryData,
    onOpen: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val journeys = library.journeysNewestFirst()
    if (journeys.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Route,
            title = "No journeys yet",
            message = "A journey is an ordered set of sounds to revisit together — a trip, a theme, a day.",
        )
        return
    }
    LazyColumn(contentPadding = contentPadding) {
        items(journeys, key = { it.id }) { journey ->
            ListItem(
                headlineContent = { Text(journey.name) },
                supportingContent = { Text(countLabel(journey.recordingIds.size, "sound")) },
                leadingContent = { Icon(Icons.Filled.Route, contentDescription = null) },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { onOpen(journey.id) },
            )
            HorizontalDivider()
        }
    }
}

private fun countLabel(count: Int, noun: String): String =
    when (count) {
        0 -> "Empty"
        1 -> "1 $noun"
        else -> "$count ${noun}s"
    }
