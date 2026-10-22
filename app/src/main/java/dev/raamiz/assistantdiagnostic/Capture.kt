package dev.raamiz.assistantdiagnostic

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import java.util.concurrent.atomic.AtomicBoolean

/** Audio is discarded in memory. Worker owns release; dispatch requires proof of termination. */
class Capture(context: Context) {
    private val accepting = AtomicBoolean(true)
    private val size = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    private val recorder: AudioRecord
    @Volatile var released = false; private set
    @Volatile var failure: Throwable? = null; private set
    private val worker: Thread
    init {
        check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "Grant microphone permission before capture" }
        require(size > 0) { "Audio configuration unavailable" }
        recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size * 2)
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Recorder initialization failed" }
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Recorder did not start" }
        } catch (e: Throwable) { recorder.release(); throw e }
        worker = Thread({
            try {
                val buffer = ShortArray(size)
                while (accepting.get()) {
                    val count = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (count < 0 && accepting.get()) error("Capture read failed: $count")
                }
            } catch (e: Throwable) { failure = e }
            finally {
                try { recorder.release(); released = true } catch (e: Throwable) { failure = e }
            }
        }, "diagnostic-capture").also { it.start() }
    }
    fun disable() { accepting.set(false) }
    // Called off main: stop may block. The main controller has an independent deadline.
    fun stopAndJoin(): Boolean {
        disable()
        try { recorder.stop() } catch (e: Throwable) { failure = e }
        worker.join(1900)
        return !worker.isAlive && released && failure == null
    }
}
