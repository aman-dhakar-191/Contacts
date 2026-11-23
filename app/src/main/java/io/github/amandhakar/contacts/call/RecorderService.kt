package io.github.amandhakar.contacts.call

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import io.github.amandhakar.contacts.data.Prefs
import io.github.amandhakar.contacts.data.RecSource
import io.github.amandhakar.contacts.data.RecordingsRepo
import io.github.amandhakar.contacts.util.AudioStats
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

object RecorderState {
    val active = MutableStateFlow(false)
    /** true when the other side is captured too (call audio); false for microphone-type inputs. */
    val bothSides = MutableStateFlow(false)
    /** Label of the input in use, e.g. "Microphone". */
    val sourceLabel = MutableStateFlow("")
    /** Why recording started by itself ("unknown number", "auto-record list", "all calls"), or null when started by hand. */
    val reason = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)
    /** Shown once as a message, e.g. when a silent input was swapped for the next one. */
    val warning = MutableStateFlow<String?>(null)
}

/**
 * Records the current call. Tries the chosen input (see the recording test in Settings), then falls back down the list.
 * Every half second it reads the loudness: if the first seconds are completely silent the input is blocked, so it moves on to
 * the next one, and a recording that stays silent is deleted instead of being kept as an empty file.
 */
class RecorderService : Service() {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var current: RecSource? = null
    private val queue = ArrayDeque<RecSource>()
    private var number = ""
    private var incoming = false
    private var peak = 0
    private var elapsedMs = 0
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            val r = recorder ?: return
            try { peak = maxOf(peak, r.maxAmplitude) } catch (_: Exception) {}
            elapsedMs += TICK_MS
            if (elapsedMs >= CHECK_AFTER_MS && AudioStats.isSilent(peak) && queue.isNotEmpty()) {
                val bad = current
                discardCurrent()
                if (startNext()) RecorderState.warning.value = "${bad?.label ?: "That input"} was silent, switched to ${current?.label}."
                else {
                    RecorderState.warning.value = "No input captured sound. Run the recording test in Settings."
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return
            }
            handler.postDelayed(this, TICK_MS.toLong())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent.getStringExtra(EXTRA_NUMBER) ?: "", intent.getBooleanExtra(EXTRA_INCOMING, false))
            else -> { finishRecording(); stopSelf() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        finishRecording()
        super.onDestroy()
    }

    private fun start(num: String, inc: Boolean) {
        if (recorder != null) return
        number = num; incoming = inc
        try {
            ServiceCompat.startForeground(
                this, Notifications.ID_RECORDING, Notifications.recording(this),
                if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
            )
        } catch (e: Exception) {
            RecorderState.error.value = "Allow the Microphone permission to record calls."
            stopSelf(); return
        }
        queue.clear(); queue.addAll(RecSource.chain(Prefs(this).preferredSource))
        if (!startNext()) {
            RecorderState.error.value = "This phone doesn't allow recording calls."
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** Starts recording on the next input in the queue that opens. */
    private fun startNext(): Boolean {
        val repo = RecordingsRepo(this)
        while (queue.isNotEmpty()) {
            val src = queue.removeFirst()
            val f = repo.newFile(incoming, src == RecSource.VOICE_CALL, number)
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            try {
                r.setAudioSource(src.id)
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioEncodingBitRate(96_000)
                r.setAudioSamplingRate(44_100)
                r.setOutputFile(f.absolutePath)
                r.prepare()
                r.start()
            } catch (_: Exception) {
                try { r.release() } catch (_: Exception) {}
                f.delete()
                continue
            }
            recorder = r; file = f; current = src
            peak = 0; elapsedMs = 0
            RecorderState.bothSides.value = src == RecSource.VOICE_CALL
            RecorderState.sourceLabel.value = src.label
            RecorderState.active.value = true
            RecorderState.error.value = null
            if (src != RecSource.VOICE_CALL && Prefs(this).speakerForRecording) CallHub.setSpeaker(true)
            handler.removeCallbacks(tick)
            handler.postDelayed(tick, TICK_MS.toLong())
            return true
        }
        return false
    }

    private fun stopRecorder() {
        handler.removeCallbacks(tick)
        val r = recorder ?: return
        recorder = null
        try { r.stop() } catch (_: Exception) { file?.delete() } // stop() throws when nothing was captured
        try { r.release() } catch (_: Exception) {}
    }

    private fun discardCurrent() {
        val f = file
        stopRecorder()
        f?.delete()
        file = null
        RecorderState.active.value = false
    }

    private fun finishRecording() {
        val f = file
        val wasSilent = recorder != null && AudioStats.isSilent(peak)
        stopRecorder()
        if (f != null && (f.length() < 2_000 || wasSilent)) {
            f.delete()
            if (wasSilent) RecorderState.warning.value = "The recording was silent and was discarded. Run the recording test in Settings."
        }
        file = null; current = null
        RecorderState.active.value = false
        RecorderState.reason.value = null
    }

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val EXTRA_NUMBER = "number"
        const val EXTRA_INCOMING = "incoming"
        private const val TICK_MS = 500
        private const val CHECK_AFTER_MS = 6_000

        fun start(ctx: Context, number: String, incoming: Boolean) {
            try {
                ctx.startForegroundService(
                    Intent(ctx, RecorderService::class.java).setAction(ACTION_START)
                        .putExtra(EXTRA_NUMBER, number).putExtra(EXTRA_INCOMING, incoming),
                )
            } catch (_: Exception) {
                RecorderState.error.value = "Couldn't start recording."
            }
        }

        fun stop(ctx: Context) {
            if (!RecorderState.active.value) return
            try { ctx.startService(Intent(ctx, RecorderService::class.java).setAction(ACTION_STOP)) } catch (_: Exception) {}
        }
    }
}
