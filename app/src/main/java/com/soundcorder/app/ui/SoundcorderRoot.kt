@file:OptIn(ExperimentalMaterial3Api::class)

package com.soundcorder.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soundcorder.app.audio.RecorderState
import com.soundcorder.app.data.Recording
import com.soundcorder.app.data.journey
import com.soundcorder.app.data.project
import com.soundcorder.app.data.recording
import com.soundcorder.app.ui.components.ConfirmDialog
import com.soundcorder.app.ui.components.PlayerBar
import com.soundcorder.app.ui.components.RecordSheetContent
import com.soundcorder.app.ui.components.TextPromptDialog
import com.soundcorder.app.ui.screens.JourneyDetailBody
import com.soundcorder.app.ui.screens.JourneyListBody
import com.soundcorder.app.ui.screens.ProjectDetailBody
import com.soundcorder.app.ui.screens.ProjectListBody

private enum class Dialog { NEW_PROJECT, NEW_JOURNEY, RENAME_PROJECT, RENAME_JOURNEY, DELETE_PROJECT, DELETE_JOURNEY }

@Composable
fun SoundcorderRoot(vm: SoundcorderViewModel = viewModel()) {
    val context = LocalContext.current
    val library by vm.data.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val recorderState by vm.recorderState.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var openJourneyId by rememberSaveable { mutableStateOf<String?>(null) }
    var journeyPicking by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<Dialog?>(null) }
    var overflowOpen by remember { mutableStateOf(false) }

    var showRecordSheet by rememberSaveable { mutableStateOf(false) }
    var recordTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val openProject = library.project(openProjectId)
    val openJourney = library.journey(openJourneyId)

    // A project/journey deleted elsewhere shouldn't leave us on a dead screen.
    LaunchedEffect(openProjectId, library.projects) {
        if (openProjectId != null && openProject == null) openProjectId = null
    }
    LaunchedEffect(openJourneyId, library.journeys) {
        if (openJourneyId != null && openJourney == null) {
            openJourneyId = null
            journeyPicking = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) vm.importAudio(uri, openProjectId ?: vm.defaultProjectId())
    }

    BackHandler(enabled = openProjectId != null || openJourneyId != null) {
        openProjectId = null
        openJourneyId = null
        journeyPicking = false
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            recordTargetId = openProjectId ?: vm.defaultProjectId()
            showRecordSheet = true
        }
    }

    fun requestRecord() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            recordTargetId = openProjectId ?: vm.defaultProjectId()
            showRecordSheet = true
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun shareRecording(rec: Recording) {
        vm.share(rec) { uri, mime ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share “${rec.title}”"))
        }
    }

    val inDetail = openProject != null || openJourney != null

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            when {
                openJourney != null -> TopAppBar(
                    title = { Text(openJourney.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { openJourneyId = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                            DropdownMenuItem(text = { Text("Rename journey") }, onClick = {
                                overflowOpen = false
                                dialog = Dialog.RENAME_JOURNEY
                            })
                            DropdownMenuItem(text = { Text("Delete journey") }, onClick = {
                                overflowOpen = false
                                dialog = Dialog.DELETE_JOURNEY
                            })
                        }
                    },
                )

                openProject != null -> TopAppBar(
                    title = { Text(openProject.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { openProjectId = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                            DropdownMenuItem(text = { Text("Import audio") }, onClick = {
                                overflowOpen = false
                                importLauncher.launch(arrayOf("audio/*"))
                            })
                            DropdownMenuItem(text = { Text("Rename project") }, onClick = {
                                overflowOpen = false
                                dialog = Dialog.RENAME_PROJECT
                            })
                            DropdownMenuItem(text = { Text("Delete project") }, onClick = {
                                overflowOpen = false
                                dialog = Dialog.DELETE_PROJECT
                            })
                        }
                    },
                )

                else -> CenterAlignedTopAppBar(
                    title = { Text("Soundcorder") },
                    actions = {
                        IconButton(onClick = {
                            dialog = if (tab == 0) Dialog.NEW_PROJECT else Dialog.NEW_JOURNEY
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = if (tab == 0) "New project" else "New journey")
                        }
                    },
                )
            }
        },
        bottomBar = {
            Column {
                val current = library.recording(playback.recordingId)
                if (playback.isLoaded && current != null) {
                    PlayerBar(
                        title = current.title,
                        state = playback,
                        onPlayPause = { vm.playPause(current) },
                        onSeek = vm::seek,
                        onClose = vm::closePlayer,
                    )
                }
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0 && !inDetail,
                        onClick = { tab = 0; openProjectId = null; openJourneyId = null },
                        icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                        label = { Text("Projects") },
                    )
                    NavigationBarItem(
                        selected = tab == 1 && !inDetail,
                        onClick = { tab = 1; openProjectId = null; openJourneyId = null },
                        icon = { Icon(Icons.Filled.Route, contentDescription = null) },
                        label = { Text("Journeys") },
                    )
                }
            }
        },
        floatingActionButton = {
            if (openJourney != null) {
                FloatingActionButton(onClick = { journeyPicking = true }) {
                    Icon(Icons.Filled.LibraryAdd, contentDescription = "Add recordings")
                }
            } else {
                FloatingActionButton(onClick = { requestRecord() }) {
                    Icon(Icons.Filled.Mic, contentDescription = "Record")
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            val listInset = PaddingValues(bottom = 88.dp)
            when {
                openJourney != null -> JourneyDetailBody(
                    journey = openJourney,
                    library = library,
                    playback = playback,
                    vm = vm,
                    onShare = ::shareRecording,
                    picking = journeyPicking,
                    onPickingChange = { journeyPicking = it },
                    contentPadding = listInset,
                )

                openProject != null -> ProjectDetailBody(
                    project = openProject,
                    library = library,
                    playback = playback,
                    vm = vm,
                    onImport = { importLauncher.launch(arrayOf("audio/*")) },
                    onShare = ::shareRecording,
                    contentPadding = listInset,
                )

                tab == 0 -> ProjectListBody(
                    library = library,
                    onOpen = { openProjectId = it },
                    contentPadding = listInset,
                )

                else -> JourneyListBody(
                    library = library,
                    onOpen = { openJourneyId = it },
                    contentPadding = listInset,
                )
            }
        }
    }

    if (showRecordSheet) {
        val target = recordTargetId ?: vm.defaultProjectId()
        ModalBottomSheet(
            onDismissRequest = {
                // Dismissing mid-recording keeps the sound rather than dropping it — losing
                // a recording should only happen on an explicit Discard.
                if (vm.isRecording) vm.stopRecordingInto(target)
                showRecordSheet = false
            },
            sheetState = sheetState,
        ) {
            RecordSheetContent(
                projects = library.projects,
                targetProjectId = target,
                onTargetChange = { recordTargetId = it },
                recorderState = recorderState,
                amplitudeProvider = {
                    (vm.recorderState.value as? RecorderState.Active)?.amplitude ?: 0
                },
                onStart = { vm.startRecording() },
                onStop = {
                    vm.stopRecordingInto(target)
                    showRecordSheet = false
                },
                onDiscard = {
                    vm.cancelRecording()
                    showRecordSheet = false
                },
            )
        }
    }

    when (dialog) {
        Dialog.NEW_PROJECT -> TextPromptDialog(
            title = "New project",
            label = "Project name",
            confirmLabel = "Create",
            onConfirm = {
                val id = vm.createProject(it)
                dialog = null
                tab = 0
                openJourneyId = null
                openProjectId = id
            },
            onDismiss = { dialog = null },
        )

        Dialog.NEW_JOURNEY -> TextPromptDialog(
            title = "New journey",
            label = "Journey name",
            confirmLabel = "Create",
            onConfirm = {
                val id = vm.createJourney(it)
                dialog = null
                tab = 1
                openProjectId = null
                openJourneyId = id
            },
            onDismiss = { dialog = null },
        )

        Dialog.RENAME_PROJECT -> openProject?.let { p ->
            TextPromptDialog(
                title = "Rename project",
                label = "Project name",
                initialValue = p.name,
                onConfirm = {
                    vm.renameProject(p.id, it)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        } ?: run { dialog = null }

        Dialog.RENAME_JOURNEY -> openJourney?.let { j ->
            TextPromptDialog(
                title = "Rename journey",
                label = "Journey name",
                initialValue = j.name,
                onConfirm = {
                    vm.renameJourney(j.id, it)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        } ?: run { dialog = null }

        Dialog.DELETE_PROJECT -> openProject?.let { p ->
            ConfirmDialog(
                title = "Delete “${p.name}”?",
                message = "Every recording in this project is deleted from this device too. This can't be undone.",
                onConfirm = {
                    vm.deleteProject(p.id)
                    dialog = null
                    openProjectId = null
                },
                onDismiss = { dialog = null },
            )
        } ?: run { dialog = null }

        Dialog.DELETE_JOURNEY -> openJourney?.let { j ->
            ConfirmDialog(
                title = "Delete “${j.name}”?",
                message = "The journey is removed. The recordings themselves stay in their projects.",
                onConfirm = {
                    vm.deleteJourney(j.id)
                    dialog = null
                    openJourneyId = null
                },
                onDismiss = { dialog = null },
            )
        } ?: run { dialog = null }

        null -> Unit
    }
}
