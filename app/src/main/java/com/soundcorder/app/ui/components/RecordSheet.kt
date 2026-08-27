@file:OptIn(ExperimentalMaterial3Api::class)

package com.soundcorder.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soundcorder.app.audio.RecorderState
import com.soundcorder.app.data.Project
import com.soundcorder.app.ui.formatDuration
import com.soundcorder.app.ui.theme.BigTimerTextStyle

@Composable
fun RecordSheetContent(
    projects: List<Project>,
    targetProjectId: String,
    onTargetChange: (String) -> Unit,
    recorderState: RecorderState,
    amplitudeProvider: () -> Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDiscard: () -> Unit,
) {
    val active = recorderState as? RecorderState.Active
    val recording = active != null
    val elapsed = active?.elapsedMs ?: 0L
    val targetName = projects.firstOrNull { it.id == targetProjectId }?.name ?: "project"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp, top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (recording) "Recording" else "New recording",
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = formatDuration(elapsed),
            style = BigTimerTextStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(16.dp))

        LevelMeter(
            amplitudeProvider = amplitudeProvider,
            running = recording,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(20.dp))

        ProjectPicker(
            projects = projects,
            selectedId = targetProjectId,
            enabled = !recording,
            onSelect = onTargetChange,
        )

        Spacer(Modifier.height(24.dp))

        if (!recording) {
            FilledIconButton(
                onClick = onStart,
                modifier = Modifier.size(84.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Start recording",
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Records into “$targetName”",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onDiscard, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Discard", modifier = Modifier.size(28.dp))
                    }
                    Text("Discard", style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledIconButton(
                        onClick = onStop,
                        modifier = Modifier.size(84.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop and save", modifier = Modifier.size(36.dp))
                    }
                    Text("Save", style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.size(56.dp))
                }
            }
        }
    }
}

@Composable
private fun ProjectPicker(
    projects: List<Project>,
    selectedId: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val selected = projects.firstOrNull { it.id == selectedId }?.name ?: "Select project"
    Box {
        AssistChip(
            onClick = { if (enabled) open = true },
            label = { Text(selected) },
            leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) },
            enabled = enabled,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            projects.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.name) },
                    onClick = {
                        open = false
                        onSelect(p.id)
                    },
                )
            }
        }
    }
}
