package com.soundcorder.app.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

sealed interface RecorderState {
    data object Idle : RecorderState

    /** [amplitude] is the peak since the last read, 0..32767. */
    data class Active(val elapsedMs: Long, val amplitude: Int) : RecorderState

    data class Error(val message: String) : RecorderState
}

data class RecordingResult(val file: File, val durationMs: Long, val mimeType: String)

/**
 * Thin wrapper over [MediaRecorder]. Captures AAC in an MP4 container (`.m4a`) — universally
 * playable, and exported as-is with no conversion.
 */
class AudioRecorder(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var ticker: Job? = null

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt = 0L

    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    val isRecording: Boolean get() = recorder != null

    fun start(output: File): Boolean {
        if (recorder != null) return false
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(160_000)
            r.setAudioSamplingRate(44_100)
            r.setAudioChannels(2)
            r.setOutputFile(output.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            outputFile = output
            startedAt = SystemClock.elapsedRealtime()
            _state.value = RecorderState.Active(0L, 0)
            startTicking()
            true
        } catch (t: Throwable) {
            runCatching { r.release() }
            output.delete()
            _state.value = RecorderState.Error(t.message ?: "Could not start recording")
            false
        }
    }

    fun stop(): RecordingResult? {
        val r = recorder ?: return null
        val file = outputFile
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        teardown()
        return try {
            r.stop()
            r.release()
            if (file != null && file.exists() && file.length() > 0L) {
                RecordingResult(file, elapsed, "audio/mp4")
            } else {
                file?.delete()
                null
            }
        } catch (t: Throwable) {
            // stop() throws if called before any frames were written (a near-instant tap).
            runCatching { r.release() }
            file?.delete()
            null
        }
    }

    fun cancel() {
        val r = recorder ?: return
        val file = outputFile
        teardown()
        runCatching { r.stop() }
        runCatching { r.release() }
        file?.delete()
    }

    fun release() {
        cancel()
        scope.cancel()
    }

    private fun amplitude(): Int = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    private fun startTicking() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive && recorder != null) {
                _state.value = RecorderState.Active(
                    elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                    amplitude = amplitude(),
                )
                delay(70)
            }
        }
    }

    private fun teardown() {
        ticker?.cancel()
        ticker = null
        recorder = null
        outputFile = null
        _state.value = RecorderState.Idle
    }
}
