package com.soundcorder.app.ui

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soundcorder.app.audio.AudioPlayer
import com.soundcorder.app.audio.AudioRecorder
import com.soundcorder.app.audio.PlaybackState
import com.soundcorder.app.audio.RecorderState
import com.soundcorder.app.data.AudioLibrary
import com.soundcorder.app.data.LibraryData
import com.soundcorder.app.data.Recording
import com.soundcorder.app.data.RecordingSource
import com.soundcorder.app.data.project
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SoundcorderViewModel(app: Application) : AndroidViewModel(app) {

    private val library = AudioLibrary(app, viewModelScope)
    private val recorder = AudioRecorder(app)
    private val player = AudioPlayer()

    val data: StateFlow<LibraryData> = library.data
    val recorderState: StateFlow<RecorderState> = recorder.state
    val playback: StateFlow<PlaybackState> = player.state

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    val isRecording: Boolean get() = recorder.isRecording

    fun defaultProjectId(): String = library.defaultProjectId()

    // --- recording --------------------------------------------------------

    fun startRecording() {
        if (recorder.isRecording) return
        val file = library.newRecordingFile("m4a")
        if (!recorder.start(file)) {
            _messages.tryEmit("Couldn't start recording — check the microphone permission")
        }
    }

    fun stopRecordingInto(projectId: String) {
        val result = recorder.stop()
        if (result == null) {
            _messages.tryEmit("That was too short to keep")
            return
        }
        library.addRecording(
            file = result.file,
            durationMs = result.durationMs,
            mimeType = result.mimeType,
            projectId = projectId,
            title = "",
            source = RecordingSource.RECORDED,
        )
        val name = library.data.value.project(projectId)?.name
        _messages.tryEmit(if (name != null) "Saved to $name" else "Saved")
    }

    fun cancelRecording() = recorder.cancel()

    // --- playback --------------------------------------------------------

    fun playPause(recording: Recording) = player.toggle(recording.id, library.fileFor(recording))

    fun seek(ms: Int) = player.seekTo(ms)

    fun closePlayer() = player.stop()

    private fun stopPlayerIfPlaying(recordingIds: Set<String>) {
        if (player.state.value.recordingId in recordingIds) player.stop()
    }

    // --- import / share -------------------------------------------------

    fun importAudio(uri: Uri, projectId: String) {
        viewModelScope.launch {
            val rec = library.importAudio(uri, projectId)
            _messages.tryEmit(if (rec != null) "Imported \"${rec.title}\"" else "Couldn't read that file")
        }
    }

    fun share(recording: Recording, onReady: (Uri, String) -> Unit) {
        viewModelScope.launch {
            val file = library.exportCopy(recording)
            val context = getApplication<Application>()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            onReady(uri, recording.mimeType)
        }
    }

    // --- recordings -----------------------------------------------------

    fun renameRecording(id: String, title: String) = library.renameRecording(id, title)

    fun deleteRecording(id: String) {
        stopPlayerIfPlaying(setOf(id))
        library.deleteRecording(id)
        _messages.tryEmit("Recording deleted")
    }

    fun moveRecording(id: String, projectId: String) = library.moveRecording(id, projectId)

    // --- projects -----------------------------------------------------

    fun createProject(name: String): String = library.createProject(name).id

    fun renameProject(id: String, name: String) = library.renameProject(id, name)

    fun deleteProject(id: String) {
        val doomed = library.data.value.recordings
            .filter { library.data.value.homeProject[it.id] == id }
            .map { it.id }
            .toSet()
        stopPlayerIfPlaying(doomed)
        library.deleteProject(id)
        _messages.tryEmit("Project deleted")
    }

    // --- journeys ---------------------------------------------------

    fun createJourney(name: String): String = library.createJourney(name).id

    fun renameJourney(id: String, name: String) = library.renameJourney(id, name)

    fun deleteJourney(id: String) {
        library.deleteJourney(id)
        _messages.tryEmit("Journey deleted")
    }

    fun addToJourney(journeyId: String, recordingIds: List<String>) {
        library.addToJourney(journeyId, recordingIds)
        _messages.tryEmit(
            if (recordingIds.size == 1) "Added to journey" else "Added ${recordingIds.size} to journey",
        )
    }

    fun removeFromJourney(journeyId: String, recordingId: String) {
        library.removeFromJourney(journeyId, recordingId)
    }

    override fun onCleared() {
        player.release()
        recorder.release()
    }
}
