package io.github.amandhakar.contacts.call

import android.Manifest
import android.app.ActivityManager
import android.app.Service
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.amandhakar.contacts.data.Prefs
import io.github.amandhakar.contacts.data.RecSource
import io.github.amandhakar.contacts.data.RecordingsRepo
import io.github.amandhakar.contacts.util.AudioStats
import io.github.amandhakar.contacts.util.RecordingDiagnostics
import io.github.amandhakar.contacts.util.RecordingDiagnostics.Attempt
import io.github.amandhakar.contacts.util.RecordingDiagnostics.Failure
import io.github.amandhakar.contacts.util.RecordingDiagnostics.Step
import io.github.amandhakar.contacts.util.Telemetry
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
 * Every step is written to [RecordingLog], so a failure on a phone can be read back from Settings > Recording test.
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
    private var currentRate = 0
    private var silencedChecks = 0
    /** Full order of inputs for this call, the inputs that wouldn't open, and how many times every input was silent. */
    private var order: List<RecSource> = emptyList()
    private val wontOpen = HashSet<RecSource>()
    private var round = 0
    private var lastRoute: RecordingDiagnostics.Route? = null
    private val routeMs = HashMap<RecordingDiagnostics.Route, Int>()
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            val r = recorder ?: return
            try { peak = maxOf(peak, r.maxAmplitude) } catch (_: Exception) {}
            elapsedMs += TICK_MS
            trackRoute()
            val silenced = if (elapsedMs > SILENCED_WATCH_MS) null else try {
                r.activeRecordingConfiguration?.isClientSilenced
            } catch (_: Exception) { null }
            silencedChecks = if (silenced == true) silencedChecks + 1 else 0
            // Android's silenced flag only switches input on the first pass; later rounds wait out the silence window.
            val silencedNow = round == 0 && RecordingDiagnostics.shouldSwitchSilenced(elapsedMs, silencedChecks, queue.isNotEmpty())
            val silentTooLong = elapsedMs >= RecordingDiagnostics.silenceWindowMs(round) && AudioStats.isSilent(peak)
            if (silencedNow || silentTooLong) {
                val bad = current
                log(
                    if (silencedNow) "${bad?.name} silenced by Android after ${elapsedMs / 1000.0} s, trying the next input"
                    else "${bad?.name} silent for ${elapsedMs / 1000} s (peak $peak), trying the next input",
                )
                if (queue.isEmpty()) {
                    // Every input was silent. Never settle on a silent one: go round again, starting on the best other input.
                    round++
                    val next = RecordingDiagnostics.nextRound(order.map { it.name }, wontOpen.map { it.name }.toSet(), bad?.name)
                    queue.addAll(next.mapNotNull { n -> RecSource.entries.firstOrNull { it.name == n } })
                    log(
                        "every input silent so far, trying again (round ${round + 1}, " +
                            "${RecordingDiagnostics.silenceWindowMs(round) / 1000} s each): ${queue.joinToString { it.name }}",
                    )
                }
                discardCurrent()
                val started = startNext()
                Telemetry.event(
                    "recording_silent_switch", "from" to bad?.name, "to" to (if (started) current?.name else "none"), "round" to round,
                )
                if (started) {
                    if (round == 0) RecorderState.warning.value = "${bad?.label ?: "That input"} was silent, switched to ${current?.label}."
                } else {
                    log("no input captured sound, stopped")
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
        if (recorder != null) { log("start ignored: already recording ${current?.name}"); return }
        number = num; incoming = inc
        try {
            ServiceCompat.startForeground(
                this, Notifications.ID_RECORDING, Notifications.recording(this),
                if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
            )
        } catch (e: Exception) {
            val failure = RecordingDiagnostics.classifyForegroundFailure(e.javaClass.name, hasMicPermission(this))
            log("foreground service refused (${failure.name}): ${RecordingDiagnostics.errorText(e.javaClass.name, e.message)}; ${appState(this)}")
            Telemetry.event("recording_failed", "reason" to failure.name.lowercase(), "error" to e.javaClass.simpleName)
            Telemetry.nonFatal("recording_foreground", e)
            RecorderState.error.value = RecordingDiagnostics.userMessage(failure)
            stopSelf(); return
        }
        val prefs = Prefs(this)
        val probe = RecordingProbe.parse(prefs.lastProbe)
        val worked = probe.filter { it.status == ProbeStatus.SIGNAL }.map { it.source.name }.toSet()
        val failedTest = probe.filter { it.status != ProbeStatus.SIGNAL }.map { it.source.name }.toSet()
        val picked = prefs.preferredSource.takeIf { it != "auto" }
        val order = RecordingDiagnostics.orderInputs(
            RecSource.chain(prefs.preferredSource).map { it.name }, picked, prefs.lastWorkingSource, worked, failedTest,
        )
        this.order = order.mapNotNull { n -> RecSource.entries.firstOrNull { it.name == n } }
        wontOpen.clear(); round = 0
        queue.clear(); queue.addAll(this.order)
        log(
            "inputs to try: ${queue.joinToString { it.name }} (picked ${picked ?: "auto"}, last with sound " +
                "${prefs.lastWorkingSource ?: "none"}, test heard ${worked.ifEmpty { setOf("none") }.joinToString("/")})",
        )
        if (!startNext()) {
            Telemetry.event("recording_failed", "reason" to "no_input_opened")
            RecorderState.error.value = RecordingDiagnostics.userMessage(Failure.NO_INPUT)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** Starts recording on the next input in the queue that opens. Each input is tried at a few sample rates. */
    private fun startNext(): Boolean {
        val repo = RecordingsRepo(this)
        val failed = ArrayList<Attempt>()
        while (queue.isNotEmpty()) {
            val src = queue.removeFirst()
            for (rate in RecordingDiagnostics.sampleRates(src.capturesFarSide || src == RecSource.VOICE_UPLINK)) {
                val f = repo.newFile(incoming, src == RecSource.VOICE_CALL, number)
                @Suppress("DEPRECATION")
                val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
                var step = Step.SOURCE
                try {
                    r.setAudioSource(src.id)
                    step = Step.CONFIGURE
                    r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    r.setAudioChannels(1)
                    r.setAudioEncodingBitRate(RecordingDiagnostics.bitRate(rate))
                    r.setAudioSamplingRate(rate)
                    r.setOutputFile(f.absolutePath)
                    step = Step.PREPARE
                    r.prepare()
                    step = Step.START
                    r.start()
                } catch (e: Exception) {
                    try { r.release() } catch (_: Exception) {}
                    f.delete()
                    val a = Attempt(src.label, rate, step, RecordingDiagnostics.errorText(e.javaClass.name, e.message))
                    failed += a
                    log("${src.name} @ $rate Hz: ${RecordingDiagnostics.stepText(step)}: ${a.error}")
                    // Android refuses the input itself: other sample rates won't help.
                    if (step == Step.SOURCE) { wontOpen += src; break }
                    if (rate == RecordingDiagnostics.sampleRates(src.capturesFarSide || src == RecSource.VOICE_UPLINK).last()) wontOpen += src
                    continue
                }
                r.setOnErrorListener { _, what, extra -> onRecorderError(what, extra) }
                recorder = r; file = f; current = src; currentRate = rate
                log("recording ${src.name} @ $rate Hz" + if (failed.isEmpty()) "" else " (after: ${RecordingDiagnostics.summary(failed)})")
                Telemetry.event(
                    "recording_started", "source" to src.name, "both_sides" to (src == RecSource.VOICE_CALL),
                    "auto_reason" to (RecorderState.reason.value ?: "manual"), "rate" to rate,
                )
                peak = 0; elapsedMs = 0; silencedChecks = 0; routeMs.clear(); lastRoute = null
                trackRoute(0)
                RecorderState.bothSides.value = src == RecSource.VOICE_CALL
                RecorderState.sourceLabel.value = src.label
                RecorderState.active.value = true
                RecorderState.error.value = null
                if (src != RecSource.VOICE_CALL && Prefs(this).speakerForRecording) CallHub.setSpeaker(true)
                handler.removeCallbacks(tick)
                handler.postDelayed(tick, TICK_MS.toLong())
                return true
            }
        }
        if (failed.isNotEmpty()) log("no input opened: ${RecordingDiagnostics.summary(failed)}")
        return false
    }

    /** Notes where the call sound goes (speaker, earpiece, Bluetooth) and logs each change, so a recording can say it. */
    private fun trackRoute(ms: Int = TICK_MS) {
        val route = AudioRoutes.recordingRoute(CallHub.audio.value)
        routeMs[route] = (routeMs[route] ?: 0) + ms
        if (route != lastRoute) {
            log(if (lastRoute == null) "route at start: ${route.key}" else "route now ${route.key} (at ${elapsedMs / 1000} s)")
            lastRoute = route
        }
    }

    /** MediaRecorder reported an error while running (e.g. the audio server restarted). Keep what was recorded. */
    private fun onRecorderError(what: Int, extra: Int) {
        log("recorder error on ${current?.name}: what=$what extra=$extra")
        Telemetry.event("recording_error", "source" to current?.name, "what" to what, "extra" to extra)
        RecorderState.error.value = RecordingDiagnostics.userMessage(Failure.RECORDER_DIED, "code $what/$extra")
        finishRecording()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
        var f = file
        val src = current
        val wasRecording = recorder != null
        val wasSilent = wasRecording && AudioStats.isSilent(peak)
        val seconds = elapsedMs / 1000
        stopRecorder()
        if (f != null && (f.length() < 2_000 || wasSilent)) {
            f.delete()
            if (wasSilent) Telemetry.event("recording_discarded_silent", "source" to current?.name)
            if (wasSilent) RecorderState.warning.value = "The recording was silent and was discarded. Run the recording test in Settings."
        }
        if (wasRecording) {
            val kept = f != null && f.exists()
            val route = RecordingDiagnostics.overallRoute(routeMs)
            if (kept && src != null && src != RecSource.VOICE_CALL) {
                f = RecordingsRepo(this).retag(f!!, RecordingDiagnostics.captureTag(false, src.name, route))
            }
            if (kept && src != null && !wasSilent && seconds >= 3) Prefs(this).lastWorkingSource = src.name
            val routes = routeMs.entries.filter { it.value > 0 }.joinToString { "${it.key.key} ${it.value / 1000} s" }
            log(
                "stopped ${src?.name} @ $currentRate Hz after $seconds s, peak $peak, route ${route.key} ($routes), " +
                    if (kept) "saved ${f!!.length() / 1024} KB" else if (wasSilent) "discarded (silent)" else "discarded (empty)",
            )
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
        /** Android's silenced flag is only read in the first seconds, where switching input still saves the call. */
        private const val SILENCED_WATCH_MS = 10_000

        fun start(ctx: Context, number: String, incoming: Boolean) {
            val app = ctx.applicationContext
            val trigger = RecorderState.reason.value ?: "tapped Record"
            RecordingLog.add(app, "start ($trigger): ${deviceInfo()}; ${appState(app)}")
            if (!hasMicPermission(app)) {
                // Starting the service without the permission would make Android refuse it and could crash the app.
                RecordingLog.add(app, "not started: Microphone permission is off")
                Telemetry.event("recording_failed", "reason" to "no_permission")
                RecorderState.error.value = RecordingDiagnostics.userMessage(Failure.NO_PERMISSION)
                return
            }
            try {
                ctx.startForegroundService(
                    Intent(ctx, RecorderService::class.java).setAction(ACTION_START)
                        .putExtra(EXTRA_NUMBER, number).putExtra(EXTRA_INCOMING, incoming),
                )
            } catch (e: Exception) {
                val failure = RecordingDiagnostics.classifyForegroundFailure(e.javaClass.name, true)
                val text = RecordingDiagnostics.errorText(e.javaClass.name, e.message)
                RecordingLog.add(app, "service not started (${failure.name}): $text")
                Telemetry.event("recording_failed", "reason" to "service_not_started", "error" to e.javaClass.simpleName)
                RecorderState.error.value = RecordingDiagnostics.userMessage(
                    if (failure == Failure.NOT_IN_FOREGROUND) failure else Failure.SERVICE_NOT_STARTED, e.javaClass.simpleName,
                )
            }
        }

        fun stop(ctx: Context) {
            if (!RecorderState.active.value) return
            RecordingLog.add(ctx.applicationContext, "stop requested")
            try { ctx.startService(Intent(ctx, RecorderService::class.java).setAction(ACTION_STOP)) } catch (_: Exception) {}
        }

        fun hasMicPermission(ctx: Context) =
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        private fun deviceInfo() =
            "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}, build ${Build.VERSION.INCREMENTAL}"

        /** Permission, default-phone-app role, whether the app is on screen, and the audio mode: what decides if recording may start. */
        fun appState(ctx: Context): String {
            val mic = hasMicPermission(ctx)
            val dialer = try { ctx.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_DIALER) } catch (_: Exception) { null }
            val importance = try {
                ActivityManager.RunningAppProcessInfo().also { ActivityManager.getMyMemoryState(it) }.importance
            } catch (_: Exception) { -1 }
            val mode = try { ctx.getSystemService(AudioManager::class.java).mode } catch (_: Exception) { -1 }
            val a11y = try { AccessibilityHelper.isEnabled(ctx) } catch (_: Exception) { null }
            val route = AudioRoutes.recordingRoute(CallHub.audio.value)
            return "mic permission ${if (mic) "on" else "OFF"}, default phone app ${dialer ?: "?"}, " +
                "importance $importance (100 = on screen), audio mode $mode (2 = in call), " +
                "accessibility helper ${when (a11y) { true -> "on"; false -> "off"; null -> "?" }}, route ${route.key}"
        }
    }

    private fun log(msg: String) = RecordingLog.add(applicationContext, msg)
}
