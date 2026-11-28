package io.github.amandhakar.contacts

import io.github.amandhakar.contacts.util.RecordingDiagnostics
import io.github.amandhakar.contacts.util.RecordingDiagnostics.Attempt
import io.github.amandhakar.contacts.util.RecordingDiagnostics.Failure
import io.github.amandhakar.contacts.util.RecordingDiagnostics.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
