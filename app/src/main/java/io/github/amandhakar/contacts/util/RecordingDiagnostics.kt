package io.github.amandhakar.contacts.util

/**
 * Pure rules for call recording failures: which step failed, what to tell the user, and how the recording log is kept.
 * No Android types here, so it is covered by plain unit tests.
 */
object RecordingDiagnostics {

    /** The MediaRecorder step an input failed at. */
    enum class Step { SOURCE, CONFIGURE, PREPARE, START }

    /** One input that was tried and did not open. */
    data class Attempt(val sourceLabel: String, val sampleRate: Int, val step: Step, val error: String)

    /** Why a recording could not start, or stopped. */
    enum class Failure {
        /** RECORD_AUDIO not granted. */
        NO_PERMISSION,
        /** Android refused the microphone foreground service although the permission is granted (app not on screen). */
        NOT_IN_FOREGROUND,
        /** startForegroundService itself threw. */
        SERVICE_NOT_STARTED,
        /** Every input failed to open. */
        NO_INPUT,
        /** The recorder reported an error while it was running. */
        RECORDER_DIED,
    }

    /** Sample rates tried for an input, best first. Call inputs often only open at 16 or 8 kHz. */
    fun sampleRates(isCallInput: Boolean): List<Int> =
        if (isCallInput) listOf(16_000, 8_000, 44_100) else listOf(44_100, 16_000)

    /** AAC bit rate that suits the sample rate (high rates at low sample rates make some encoders refuse to start). */
    fun bitRate(sampleRate: Int): Int = when {
        sampleRate >= 44_100 -> 96_000
        sampleRate >= 16_000 -> 48_000
        else -> 24_000
    }

    /**
     * What a failed startForeground means. A SecurityException with the permission granted is Android's
     * "a microphone service may only start while the app is visible" rule.
     */
    fun classifyForegroundFailure(exceptionClass: String, permissionGranted: Boolean): Failure = when {
        !permissionGranted -> Failure.NO_PERMISSION
        exceptionClass.endsWith("SecurityException") || exceptionClass.endsWith("ForegroundServiceStartNotAllowedException") ->
            Failure.NOT_IN_FOREGROUND
        else -> Failure.SERVICE_NOT_STARTED
    }

    /** Short "Type: message" text for an exception, without anything that could hold a phone number. */
    fun errorText(exceptionClass: String, message: String?): String {
        val name = exceptionClass.substringAfterLast('.')
        val msg = message?.let(::scrubNumbers)?.replace('\n', ' ')?.trim()?.take(160)
        return if (msg.isNullOrEmpty()) name else "$name: $msg"
    }

    /**
     * Replaces anything that could be a phone number (5 or more digits) with "#". Negative numbers are kept, because
     * Android's error codes look like "-2147483648" and a phone number never starts with a minus.
     */
    fun scrubNumbers(text: String): String = text.replace(Regex("(?<![-\\d])\\+?\\d[\\d ()-]{3,}\\d")) { m ->
        if (m.value.count { it.isDigit() } >= 5) "#" else m.value
    }

    /** One line per input that did not open, for the log and the error message. */
    fun summary(attempts: List<Attempt>): String =
        attempts.groupBy { it.sourceLabel }.entries.joinToString("; ") { (label, list) ->
            val last = list.last()
            "$label: ${stepText(last.step)} (${last.error})"
        }

    fun stepText(step: Step): String = when (step) {
        Step.SOURCE -> "input refused"
        Step.CONFIGURE -> "settings refused"
        Step.PREPARE -> "prepare failed"
        Step.START -> "start failed"
    }

    /** The message shown on the call screen. Always says where the details are. */
    fun userMessage(failure: Failure, detail: String? = null): String {
        val more = " Details: Settings > Recording test > Recording log."
        return when (failure) {
            Failure.NO_PERMISSION ->
                "Recording needs the Microphone permission. Turn it on for Contacts in Android settings, then try again."
            Failure.NOT_IN_FOREGROUND ->
                "Android only lets recording start while Contacts is on screen. Open the call screen and tap Record." + more
            Failure.SERVICE_NOT_STARTED ->
                "Couldn't start recording" + (detail?.let { " ($it)" } ?: "") + "." + more
            Failure.NO_INPUT ->
                "No recording input would open on this phone." + more
            Failure.RECORDER_DIED ->
                "Recording stopped because the recorder failed" + (detail?.let { " ($it)" } ?: "") + "." + more
        }
    }

    /** Keeps the newest [max] lines. Lines are stored oldest first. */
    fun trim(lines: List<String>, max: Int): List<String> = if (lines.size <= max) lines else lines.takeLast(max)

    /** Log lines are stored as one string; a line never holds a newline. */
    fun encode(lines: List<String>): String = lines.joinToString("\n") { it.replace('\n', ' ') }

    fun decode(text: String?): List<String> = text?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    // ---- Which input goes first ----

    /**
     * Orders the inputs to try (enum names). The input that last made a real call recording with sound goes first, because
     * a real call is the best evidence; then the input picked in Settings, unless the last recording test found it blocked
     * or silent; then inputs the test heard sound on; then the rest; inputs the test found blocked or silent go last. So a
     * phone where only one input works starts on it, instead of losing the first seconds of every call to a silent input.
     */
    fun orderInputs(
        base: List<String>,
        preferred: String?,
        lastWorking: String?,
        testWorked: Set<String>,
        testFailed: Set<String>,
    ): List<String> {
        val head = ArrayList<String>()
        lastWorking?.takeIf { it in base }?.let(head::add)
        preferred?.takeIf { it in base && it !in testFailed && it !in head }?.let(head::add)
        val worked = base.filter { it in testWorked && it !in head }
        val failed = base.filter { it in testFailed && it !in head && it !in testWorked }
        val rest = base.filter { it !in head && it !in worked && it !in failed }
        return head + worked + rest + failed
    }

    /** How long after starting Android's "this app is being fed silence" flag is trusted enough to switch input. */
    const val SILENCED_CHECK_MS = 1_000

    /**
     * Switch to the next input now: Android has said on two checks in a row that it is feeding this app silence on this
     * input, and there is another input to try. Saves waiting the full silence check on phones that block the microphone.
     */
    fun shouldSwitchSilenced(elapsedMs: Int, silencedChecksInARow: Int, hasNext: Boolean): Boolean =
        hasNext && silencedChecksInARow >= 2 && elapsedMs >= SILENCED_CHECK_MS

    // ---- When every input is silent ----

    /**
     * How long an input may stay completely silent before the next one is tried. Round 0 is the first pass through the
     * inputs; after that the inputs are tried again in turn with a longer wait each round (12 s, 24 s, 48 s, then every
     * 60 s), because the call may not have had sound yet, or Android may stop silencing an input later in the call.
     */
    fun silenceWindowMs(round: Int): Int = minOf(6_000 shl round.coerceIn(0, 4), 60_000)

    /**
     * The inputs for the next round after every input was silent: the usual order without the inputs that wouldn't open,
     * with the input in use moved to the end, so the round starts on the best other input.
     */
    fun nextRound(order: List<String>, wontOpen: Set<String>, current: String?): List<String> {
        val usable = order.filter { it !in wontOpen }
        return usable.filter { it != current } + usable.filter { it == current }
    }

    // ---- What a recording holds (kept in its file name) ----

    /** Where the call sound went while recording. */
    enum class Route(val key: String, val label: String) {
        SPEAKER("speaker", "speaker on"),
        EARPIECE("earpiece", "earpiece"),
        BLUETOOTH("bt", "Bluetooth"),
        WIRED("wired", "wired headset"),
        MIXED("mixed", "route changed during the call"),
        UNKNOWN("unknown", "route unknown");

        companion object {
            fun fromKey(k: String?) = entries.firstOrNull { it.key == k }
        }
    }

    /**
     * The route for a whole recording, from how long the sound went each way. A route used under 2 s doesn't count
     * (turning the speaker on right after recording starts would otherwise make every recording "route changed").
     */
    fun overallRoute(msPerRoute: Map<Route, Int>): Route {
        val real = msPerRoute.filter { it.key != Route.UNKNOWN && it.value >= MIN_ROUTE_MS }.keys
        return when {
            real.size == 1 -> real.first()
            real.size > 1 -> Route.MIXED
            else -> msPerRoute.filter { it.key != Route.UNKNOWN && it.value > 0 }.maxByOrNull { it.value }?.key ?: Route.UNKNOWN
        }
    }

    const val MIN_ROUTE_MS = 2_000

    /**
     * The file-name tag (third field of `<millis>_<in|out>_<tag>_<number>.m4a`). "both" = call audio; "mic" = an older
     * microphone recording; "mic-<source>-<route>" = a microphone recording with the input and the route, e.g.
     * "mic-voicerecognition-speaker". No underscores, because they split the file name.
     */
    fun captureTag(bothSides: Boolean, sourceName: String?, route: Route?): String {
        if (bothSides) return "both"
        val src = sourceName?.lowercase()?.filter { it.isLetterOrDigit() }.orEmpty()
        return if (src.isEmpty() && route == null) "mic" else "mic-$src-${(route ?: Route.UNKNOWN).key}"
    }

    data class Capture(val bothSides: Boolean, val source: String?, val route: Route?)

    fun parseCaptureTag(tag: String): Capture {
        if (tag == "both") return Capture(true, null, null)
        val p = tag.split('-')
        if (p.size < 3 || p[0] != "mic") return Capture(false, null, null)
        return Capture(false, p[1].ifEmpty { null }, Route.fromKey(p[2]))
    }

    /** Short words for the recordings list, e.g. "call audio" or "microphone (voice recognition), speaker on". */
    fun captureLabel(c: Capture): String {
        if (c.bothSides) return "call audio"
        val src = when (c.source) {
            null -> null
            "voicerecognition" -> "voice recognition"
            "voicecommunication" -> "voice communication"
            "unprocessed" -> "unprocessed"
            "mic" -> null
            else -> c.source
        }
        return "microphone" + (src?.let { " ($it)" } ?: "") + (c.route?.let { ", ${it.label}" } ?: "")
    }

    /** Replaces the tag in a recording file name, keeping everything else. Returns null if the name isn't one of ours. */
    fun withTag(fileName: String, tag: String): String? {
        if (!fileName.endsWith(".m4a")) return null
        val p = fileName.removeSuffix(".m4a").split('_', limit = 4)
        if (p.size < 4) return null
        return "${p[0]}_${p[1]}_${tag}_${p[3]}.m4a"
    }
}
