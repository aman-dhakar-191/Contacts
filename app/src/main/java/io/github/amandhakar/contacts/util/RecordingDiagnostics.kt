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
}
