package com.soundcorder.app.audio

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackState(
    val recordingId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
) {
    val isLoaded: Boolean get() = recordingId != null
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** One [MediaPlayer] at a time, driving a small [PlaybackState] the player bar renders. */
class AudioPlayer {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var player: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** Play [recordingId], or toggle pause/resume if it is already the loaded track. */
    fun toggle(recordingId: String, file: File) {
        val current = _state.value
        if (current.recordingId == recordingId && player != null) {
            if (current.isPlaying) pause() else resume()
        } else {
            openAndPlay(recordingId, file)
        }
    }

    fun pause() {
        player?.let { if (it.isPlaying) it.pause() }
        progressJob?.cancel()
        _state.update { it.copy(isPlaying = false) }
    }

    fun seekTo(ms: Int) {
        val mp = player ?: return
        val target = ms.coerceIn(0, mp.duration.coerceAtLeast(0))
        mp.seekTo(target)
        _state.update { it.copy(positionMs = target) }
    }

    fun stop() {
        stopInternal()
        _state.value = PlaybackState()
    }

    fun release() {
        stopInternal()
        scope.cancel()
    }

    private fun openAndPlay(recordingId: String, file: File) {
        stopInternal()
        val mp = MediaPlayer()
        try {
            mp.setDataSource(file.absolutePath)
            mp.prepare()
        } catch (t: Throwable) {
            runCatching { mp.release() }
            _state.value = PlaybackState()
            return
        }
        mp.setOnCompletionListener {
            progressJob?.cancel()
            _state.update { it.copy(isPlaying = false, positionMs = it.durationMs) }
        }
        player = mp
        mp.start()
        _state.value = PlaybackState(
            recordingId = recordingId,
            isPlaying = true,
            positionMs = 0,
            durationMs = mp.duration.coerceAtLeast(0),
        )
        startProgress()
    }

    private fun resume() {
        val mp = player ?: return
        // Completion leaves position at the end — start over from there.
        if (_state.value.positionMs >= _state.value.durationMs && _state.value.durationMs > 0) {
            mp.seekTo(0)
            _state.update { it.copy(positionMs = 0) }
        }
        mp.start()
        _state.update { it.copy(isPlaying = true) }
        startProgress()
    }

    private fun stopInternal() {
        progressJob?.cancel()
        progressJob = null
        player?.let { mp ->
            runCatching { mp.stop() }
            runCatching { mp.release() }
        }
        player = null
    }

    private fun startProgress() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val mp = player ?: break
                val pos = runCatching { mp.currentPosition }.getOrDefault(0)
                _state.update { it.copy(positionMs = pos) }
                delay(50)
            }
        }
    }
}
