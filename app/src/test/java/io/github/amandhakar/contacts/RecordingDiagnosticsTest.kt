package io.github.amandhakar.contacts

import io.github.amandhakar.contacts.util.RecordingDiagnostics
import io.github.amandhakar.contacts.util.RecordingDiagnostics.Attempt
import io.github.amandhakar.contacts.util.RecordingDiagnostics.Capture
import io.github.amandhakar.contacts.util.RecordingDiagnostics.Failure
import io.github.amandhakar.contacts.util.RecordingDiagnostics.Route
import io.github.amandhakar.contacts.util.RecordingDiagnostics.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingDiagnosticsTest {
    @Test fun missingPermissionWinsOverEverything() {
        assertEquals(Failure.NO_PERMISSION, RecordingDiagnostics.classifyForegroundFailure("java.lang.SecurityException", false))
        assertEquals(Failure.NO_PERMISSION, RecordingDiagnostics.classifyForegroundFailure("java.lang.IllegalStateException", false))
    }

    @Test fun securityExceptionWithPermissionMeansNotOnScreen() {
        assertEquals(Failure.NOT_IN_FOREGROUND, RecordingDiagnostics.classifyForegroundFailure("java.lang.SecurityException", true))
        assertEquals(
            Failure.NOT_IN_FOREGROUND,
            RecordingDiagnostics.classifyForegroundFailure("android.app.ForegroundServiceStartNotAllowedException", true),
        )
        // A wrong service type is a build mistake, not the app being off screen.
        assertEquals(
            Failure.SERVICE_NOT_STARTED,
            RecordingDiagnostics.classifyForegroundFailure("android.app.InvalidForegroundServiceTypeException", true),
        )
        assertEquals(Failure.SERVICE_NOT_STARTED, RecordingDiagnostics.classifyForegroundFailure("java.lang.IllegalStateException", true))
    }

    @Test fun callInputsTryLowRatesFirst() {
        assertEquals(16_000, RecordingDiagnostics.sampleRates(true).first())
        assertTrue(8_000 in RecordingDiagnostics.sampleRates(true))
        assertEquals(44_100, RecordingDiagnostics.sampleRates(false).first())
        assertTrue(16_000 in RecordingDiagnostics.sampleRates(false))
    }

    @Test fun bitRateFollowsSampleRate() {
        assertEquals(96_000, RecordingDiagnostics.bitRate(44_100))
        assertEquals(48_000, RecordingDiagnostics.bitRate(16_000))
        assertEquals(24_000, RecordingDiagnostics.bitRate(8_000))
    }

    @Test fun phoneNumbersAreScrubbedButErrorCodesKept() {
        assertEquals("call to # failed", RecordingDiagnostics.scrubNumbers("call to +91 98765 43210 failed"))
        assertEquals("start failed: -2147483648", RecordingDiagnostics.scrubNumbers("start failed: -2147483648"))
        assertEquals("error -38", RecordingDiagnostics.scrubNumbers("error -38"))
        assertEquals("code 1234", RecordingDiagnostics.scrubNumbers("code 1234"))
    }

    @Test fun errorTextUsesShortClassName() {
        assertEquals("RuntimeException: start failed.", RecordingDiagnostics.errorText("java.lang.RuntimeException", "start failed."))
        assertEquals("IllegalStateException", RecordingDiagnostics.errorText("java.lang.IllegalStateException", null))
        assertEquals("IOException: a b", RecordingDiagnostics.errorText("java.io.IOException", " a\nb "))
        assertTrue(RecordingDiagnostics.errorText("X", "y".repeat(500)).length <= 165)
    }

    @Test fun summaryShowsLastFailurePerInput() {
        val s = RecordingDiagnostics.summary(
            listOf(
                Attempt("Call audio", 16_000, Step.START, "RuntimeException: start failed."),
                Attempt("Call audio", 8_000, Step.PREPARE, "IOException"),
                Attempt("Microphone", 44_100, Step.SOURCE, "RuntimeException: setAudioSource failed."),
            ),
        )
        assertEquals("Call audio: prepare failed (IOException); Microphone: input refused (RuntimeException: setAudioSource failed.)", s)
    }

    @Test fun everyMessagePointsAtTheLogExceptPermission() {
        for (f in Failure.entries) {
            val m = RecordingDiagnostics.userMessage(f, "code 1/2")
            if (f == Failure.NO_PERMISSION) assertTrue(m.contains("Microphone permission"))
            else assertTrue(m, m.contains("Recording log"))
        }
        assertTrue(RecordingDiagnostics.userMessage(Failure.RECORDER_DIED, "code 1/2").contains("(code 1/2)"))
        assertFalse(RecordingDiagnostics.userMessage(Failure.SERVICE_NOT_STARTED).contains("()"))
    }

    @Test fun logKeepsNewestLinesAndRoundTrips() {
        val lines = (1..10).map { "line $it" }
        assertEquals((6..10).map { "line $it" }, RecordingDiagnostics.trim(lines, 5))
        assertEquals(lines, RecordingDiagnostics.trim(lines, 50))
        val withBreak = listOf("a", "b\nc")
        assertEquals(listOf("a", "b c"), RecordingDiagnostics.decode(RecordingDiagnostics.encode(withBreak)))
        assertEquals(emptyList<String>(), RecordingDiagnostics.decode(null))
        assertEquals(emptyList<String>(), RecordingDiagnostics.decode(""))
    }

    private val chain = listOf("VOICE_CALL", "VOICE_RECOGNITION", "VOICE_COMMUNICATION", "UNPROCESSED", "MIC")

    @Test fun pickedInputThatTestFoundSilentNoLongerGoesFirst() {
        // Aman's POCO X6: MIC picked, but the test found it silenced and only voice recognition heard sound.
        val order = RecordingDiagnostics.orderInputs(
            listOf("MIC") + chain.filter { it != "MIC" }, "MIC", null,
            testWorked = setOf("VOICE_RECOGNITION"),
            testFailed = setOf("VOICE_CALL", "VOICE_COMMUNICATION", "UNPROCESSED", "MIC"),
        )
        assertEquals("VOICE_RECOGNITION", order.first())
        assertEquals(chain.toSet(), order.toSet())
        assertEquals(chain.size, order.size)
    }

    @Test fun lastInputWithSoundGoesFirstWithoutATest() {
        val order = RecordingDiagnostics.orderInputs(chain, null, "VOICE_RECOGNITION", emptySet(), emptySet())
        assertEquals(listOf("VOICE_RECOGNITION", "VOICE_CALL", "VOICE_COMMUNICATION", "UNPROCESSED", "MIC"), order)
    }

    @Test fun pickedInputThatWorksStaysFirst() {
        val order = RecordingDiagnostics.orderInputs(chain, "UNPROCESSED", "VOICE_RECOGNITION", setOf("UNPROCESSED"), setOf("MIC"))
        assertEquals(listOf("UNPROCESSED", "VOICE_RECOGNITION", "VOICE_CALL", "VOICE_COMMUNICATION", "MIC"), order)
    }

    @Test fun noHistoryKeepsTheDefaultOrder() {
        assertEquals(chain, RecordingDiagnostics.orderInputs(chain, null, null, emptySet(), emptySet()))
        // A saved input that isn't in the list is ignored.
        assertEquals(chain, RecordingDiagnostics.orderInputs(chain, "GONE", "GONE", emptySet(), emptySet()))
    }

    @Test fun silencedSwitchNeedsTwoChecksAndAnotherInput() {
        assertTrue(RecordingDiagnostics.shouldSwitchSilenced(1_000, 2, true))
        assertFalse(RecordingDiagnostics.shouldSwitchSilenced(1_000, 1, true))
        assertFalse(RecordingDiagnostics.shouldSwitchSilenced(500, 2, true))
        assertFalse(RecordingDiagnostics.shouldSwitchSilenced(5_000, 4, false))
    }

    @Test fun shortRouteBlipsDontCount() {
        assertEquals(Route.SPEAKER, RecordingDiagnostics.overallRoute(mapOf(Route.EARPIECE to 500, Route.SPEAKER to 12_000)))
        assertEquals(Route.MIXED, RecordingDiagnostics.overallRoute(mapOf(Route.EARPIECE to 4_000, Route.SPEAKER to 6_000)))
        assertEquals(Route.BLUETOOTH, RecordingDiagnostics.overallRoute(mapOf(Route.BLUETOOTH to 1_500)))
        assertEquals(Route.EARPIECE, RecordingDiagnostics.overallRoute(mapOf(Route.UNKNOWN to 9_000, Route.EARPIECE to 3_000)))
        assertEquals(Route.UNKNOWN, RecordingDiagnostics.overallRoute(emptyMap()))
        assertEquals(Route.UNKNOWN, RecordingDiagnostics.overallRoute(mapOf(Route.UNKNOWN to 9_000)))
    }

    @Test fun captureTagRoundTripsWithoutUnderscores() {
        assertEquals("both", RecordingDiagnostics.captureTag(true, "VOICE_CALL", Route.SPEAKER))
        val tag = RecordingDiagnostics.captureTag(false, "VOICE_RECOGNITION", Route.SPEAKER)
        assertEquals("mic-voicerecognition-speaker", tag)
        assertFalse(tag.contains('_'))
        assertEquals(Capture(false, "voicerecognition", Route.SPEAKER), RecordingDiagnostics.parseCaptureTag(tag))
        assertEquals(Capture(true, null, null), RecordingDiagnostics.parseCaptureTag("both"))
        assertEquals(Capture(false, null, null), RecordingDiagnostics.parseCaptureTag("mic"))
        assertEquals("mic-mic-unknown", RecordingDiagnostics.captureTag(false, "MIC", null))
    }

    @Test fun labelsSayWhatWasRecorded() {
        assertEquals("call audio", RecordingDiagnostics.captureLabel(RecordingDiagnostics.parseCaptureTag("both")))
        assertEquals("microphone", RecordingDiagnostics.captureLabel(RecordingDiagnostics.parseCaptureTag("mic")))
        assertEquals(
            "microphone (voice recognition), speaker on",
            RecordingDiagnostics.captureLabel(RecordingDiagnostics.parseCaptureTag("mic-voicerecognition-speaker")),
        )
        assertEquals("microphone, Bluetooth", RecordingDiagnostics.captureLabel(RecordingDiagnostics.parseCaptureTag("mic-mic-bt")))
        assertEquals(
            "microphone (voice communication), route changed during the call",
            RecordingDiagnostics.captureLabel(RecordingDiagnostics.parseCaptureTag("mic-voicecommunication-mixed")),
        )
    }

    @Test fun retagKeepsDateDirectionAndNumber() {
        assertEquals(
            "1764338000000_in_mic-voicerecognition-earpiece_+919876543210.m4a",
            RecordingDiagnostics.withTag("1764338000000_in_mic_+919876543210.m4a", "mic-voicerecognition-earpiece"),
        )
        assertNull(RecordingDiagnostics.withTag("notes.txt", "both"))
        assertNull(RecordingDiagnostics.withTag("123_in.m4a", "both"))
    }
}
