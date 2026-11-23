package io.github.amandhakar.contacts.call

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import io.github.amandhakar.contacts.data.Prefs
import io.github.amandhakar.contacts.data.RecordingsRepo
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

object RecorderState {
    val active = MutableStateFlow(false)
    /** true when both sides are captured; false when only the microphone is (the other person is heard through the speaker). */
    val bothSides = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
}

/**
 * Records the current call. Android does not let normal apps capture the call audio stream on most
 * phones, so this tries the call source first and falls back to the microphone.
 */
class RecorderService : Service() {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

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

    private fun start(number: String, incoming: Boolean) {
        if (recorder != null) return
        try {
            ServiceCompat.startForeground(
                this, Notifications.ID_RECORDING, Notifications.recording(this),
                if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
            )
        } catch (e: Exception) {
            RecorderState.error.value = "Allow the Microphone permission to record calls."
            stopSelf(); return
        }
        val repo = RecordingsRepo(this)
        val sources = listOf(MediaRecorder.AudioSource.VOICE_CALL, MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)
        for (src in sources) {
            val f = repo.newFile(incoming, src == MediaRecorder.AudioSource.VOICE_CALL, number)
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            try {
                r.setAudioSource(src)
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioEncodingBitRate(96_000)
                r.setAudioSamplingRate(44_100)
                r.setOutputFile(f.absolutePath)
                r.prepare()
                r.start()
                recorder = r; file = f
                RecorderState.bothSides.value = src == MediaRecorder.AudioSource.VOICE_CALL
                RecorderState.active.value = true
                RecorderState.error.value = null
                if (src != MediaRecorder.AudioSource.VOICE_CALL && Prefs(this).speakerForRecording) CallHub.setSpeaker(true)
                return
            } catch (_: Exception) {
                try { r.release() } catch (_: Exception) {}
                f.delete()
            }
        }
        RecorderState.error.value = "This phone doesn't allow recording calls."
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun finishRecording() {
        val r = recorder ?: run { RecorderState.active.value = false; return }
        recorder = null
        try { r.stop() } catch (_: Exception) { file?.delete() } // stop() throws when nothing was captured
        try { r.release() } catch (_: Exception) {}
        file?.takeIf { it.exists() && it.length() < 2_000 }?.delete()
        file = null
        RecorderState.active.value = false
    }

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val EXTRA_NUMBER = "number"
        const val EXTRA_INCOMING = "incoming"

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
