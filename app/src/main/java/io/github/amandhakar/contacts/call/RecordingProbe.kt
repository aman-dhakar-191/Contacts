package io.github.amandhakar.contacts.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import androidx.core.content.ContextCompat
import io.github.amandhakar.contacts.data.Prefs
import io.github.amandhakar.contacts.data.RecSource
import io.github.amandhakar.contacts.util.AudioStats
import io.github.amandhakar.contacts.util.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

enum class ProbeStatus { SIGNAL, SILENT, BLOCKED, ERROR }

data class ProbeResult(
    val source: RecSource,
    val status: ProbeStatus,
    val peak: Int,
    val rmsDb: Double,
    /** Android reported that it is feeding this app silence on purpose. */
    val silencedBySystem: Boolean,
    val file: String?,
    val note: String,
)

/**
 * Records a few seconds from every audio input Android offers during a call and measures what came out.
 * Tells you which inputs work on this phone, and lets you listen to the samples to hear who is on them.
 */
object RecordingProbe {
    const val SECONDS_PER_SOURCE = 3
    private const val RATE = 16_000

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running
    private val _step = MutableStateFlow<String?>(null)
    val step: StateFlow<String?> = _step
    private val _results = MutableStateFlow<List<ProbeResult>>(emptyList())
    val results: StateFlow<List<ProbeResult>> = _results

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val totalSeconds get() = RecSource.entries.size * SECONDS_PER_SOURCE

    fun loadSaved(ctx: Context) {
        if (_results.value.isNotEmpty()) return
        _results.value = parse(Prefs(ctx).lastProbe)
    }

    /** Returns false (and does nothing) if a test or a recording is already running, or there is no microphone permission. */
    fun start(ctx: Context): String? {
        val app = ctx.applicationContext
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            RecordingLog.add(app, "recording test not run: Microphone permission is off")
            return "Allow the Microphone permission first."
        }
        if (RecorderState.active.value) return "Stop the current recording first."
        if (_running.value) return null
        _running.value = true
        scope.launch {
            try {
                val out = ArrayList<ProbeResult>()
                for ((i, src) in RecSource.entries.withIndex()) {
                    _step.value = "Recording ${src.label} (${i + 1} of ${RecSource.entries.size})…"
                    out += probe(app, src)
                    _results.value = out.toList()
                }
                Prefs(app).lastProbe = toJson(out)
                RecordingLog.add(app, "recording test: " + out.joinToString("; ") { "${it.source.name} ${it.status.name} peak ${it.peak}" + if (it.silencedBySystem) " silenced" else "" } +
                    "; accessibility helper ${if (AccessibilityHelper.isEnabled(app)) "on" else "off"}")
                Telemetry.event("recording_test", *out.map { it.source.name to it.status.name }.toTypedArray(),
                    "a11y" to AccessibilityHelper.isEnabled(app), "far_side" to out.any { it.source.capturesFarSide && it.status == ProbeStatus.SIGNAL })
            } finally {
                _step.value = null
                _running.value = false
            }
        }
        return null
    }

    private fun dir(ctx: Context): File =
        (ctx.getExternalFilesDir("recording-tests") ?: File(ctx.filesDir, "recording-tests")).also { it.mkdirs() }

    fun clearSamples(ctx: Context) {
        dir(ctx).listFiles()?.forEach { it.delete() }
        Prefs(ctx).lastProbe = null
        _results.value = emptyList()
    }

    @Suppress("MissingPermission")
    private suspend fun probe(ctx: Context, src: RecSource): ProbeResult {
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord.Builder()
                .setAudioSource(src.id)
                .setAudioFormat(
                    AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build(),
                )
                .setBufferSizeInBytes(maxOf(minBuf, RATE) * 2)
                .build()
        } catch (e: Exception) {
            return ProbeResult(src, ProbeStatus.BLOCKED, 0, -90.0, false, null, "Not available to apps (${e.javaClass.simpleName}).")
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return ProbeResult(src, ProbeStatus.BLOCKED, 0, -90.0, false, null, "Android refused to open it.")
        }
        try {
            rec.startRecording()
        } catch (e: Exception) {
            rec.release()
            return ProbeResult(src, ProbeStatus.ERROR, 0, -90.0, false, null, "Couldn't start: ${e.message}")
        }
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            rec.release()
            return ProbeResult(src, ProbeStatus.BLOCKED, 0, -90.0, false, null, "It would not start recording.")
        }

        val pcm = ByteArrayOutputStream()
        val chunk = ShortArray(RATE / 10)
        var peak = 0
        var sumSq = 0.0
        var n = 0L
        var silenced = false
        val target = RATE.toLong() * SECONDS_PER_SOURCE
        try {
            while (n < target) {
                val read = rec.read(chunk, 0, chunk.size)
                if (read <= 0) { delay(20); if (read < 0) break else continue }
                val lv = AudioStats.level(chunk, read)
                if (lv.peak > peak) peak = lv.peak
                for (i in 0 until read) sumSq += chunk[i].toDouble() * chunk[i]
                n += read
                val bytes = ByteArray(read * 2)
                for (i in 0 until read) { bytes[i * 2] = (chunk[i].toInt() and 0xff).toByte(); bytes[i * 2 + 1] = (chunk[i].toInt() shr 8).toByte() }
                pcm.write(bytes)
                if (!silenced) silenced = rec.activeRecordingConfiguration?.isClientSilenced == true
            }
        } catch (e: Exception) {
            rec.release()
            return ProbeResult(src, ProbeStatus.ERROR, peak, -90.0, silenced, null, "Failed while recording: ${e.message}")
        }
        rec.stop(); rec.release()

        val rms = if (n > 0) Math.sqrt(sumSq / n) else 0.0
        val db = if (rms < 1.0) -90.0 else 20 * Math.log10(rms / 32768.0)
        val data = pcm.toByteArray()
        val f = File(dir(ctx), "${System.currentTimeMillis()}_${src.name}.wav")
        f.outputStream().use { it.write(AudioStats.wavHeader(data.size, RATE)); it.write(data) }
        val silent = AudioStats.isSilent(peak)
        val status = if (silent) ProbeStatus.SILENT else ProbeStatus.SIGNAL
        val note = when {
            silenced -> "Android is silencing this app on purpose."
            silent -> "Opened fine but recorded only silence."
            else -> "Captured sound."
        }
        return ProbeResult(src, status, peak, db, silenced, f.absolutePath, note)
    }

    /** One-sentence summary of what the results mean. */
    fun verdict(results: List<ProbeResult>): String {
        if (results.isEmpty()) return "No test run yet."
        val ok = results.filter { it.status == ProbeStatus.SIGNAL }
        val far = ok.filter { it.source.capturesFarSide }
        return when {
            far.isNotEmpty() -> "This phone lets apps capture the call audio (${far.joinToString { it.source.label }}). Listen to the samples to confirm you can hear the other person."
            ok.isNotEmpty() -> "Only microphone inputs work (${ok.joinToString { it.source.label }}). That records your voice, and the other person only if the speaker is on."
            else -> "Nothing was captured. Every input was blocked or silent. Was a call active, with sound on both sides?"
        }
    }

    // ---- saving ----

    fun toJson(list: List<ProbeResult>): String = JSONArray().also { arr ->
        list.forEach {
            arr.put(JSONObject().put("s", it.source.name).put("st", it.status.name).put("p", it.peak).put("db", it.rmsDb)
                .put("sil", it.silencedBySystem).put("f", it.file ?: JSONObject.NULL).put("n", it.note))
        }
    }.toString()

    fun parse(json: String?): List<ProbeResult> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val src = RecSource.entries.firstOrNull { it.name == o.getString("s") } ?: return@mapNotNull null
                ProbeResult(
                    src, ProbeStatus.valueOf(o.getString("st")), o.getInt("p"), o.getDouble("db"), o.getBoolean("sil"),
                    if (o.isNull("f")) null else o.getString("f").takeIf { File(it).exists() }, o.getString("n"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
