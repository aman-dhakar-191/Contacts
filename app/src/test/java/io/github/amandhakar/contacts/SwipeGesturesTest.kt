package io.github.amandhakar.contacts

import io.github.amandhakar.contacts.data.SwipeAction
import io.github.amandhakar.contacts.util.SwipeGestures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeGesturesTest {
    private val call = SwipeAction.CALL
    private val msg = SwipeAction.MESSAGE
    private val none = SwipeAction.NONE

    // A 1080 x 2400 window, back-gesture areas 60 px wide, a 48 px home bar, 66 px minimum, 33 px margin.
    private fun inGesture(x: Float, y: Float = 1000f, left: Float = 60f, right: Float = 60f, bottom: Float = 48f) =
        SwipeGestures.startsInSystemGesture(x, y, 1080f, 2400f, left, right, bottom, minEdge = 66f, margin = 33f)

    @Test fun touchesNearEitherEdgeBelongToTheSystem() {
        assertTrue(inGesture(5f))
        assertTrue(inGesture(90f)) // inside min edge + margin, though past the reported inset
        assertTrue(inGesture(1075f))
        assertTrue(inGesture(1000f))
        assertFalse(inGesture(540f))
        assertFalse(inGesture(120f))
    }

    @Test fun aWideBackAreaWidensTheGuard() {
        assertTrue(inGesture(150f, left = 130f))
        assertFalse(inGesture(150f, left = 60f))
    }

    @Test fun theHomeBarAreaBelongsToTheSystem() {
        assertTrue(inGesture(540f, y = 2390f))
        assertFalse(inGesture(540f, y = 2300f))
        assertFalse(inGesture(540f, y = 2390f, bottom = 0f)) // three-button navigation reports no bottom gesture area
    }

    @Test fun onlyClearlySidewaysMovesAreSwipes() {
        assertTrue(SwipeGestures.isHorizontal(30f, 5f))
        assertTrue(SwipeGestures.isHorizontal(-30f, 15f))
        assertFalse(SwipeGestures.isHorizontal(30f, 20f))
        assertFalse(SwipeGestures.isHorizontal(0f, 0f))
    }

    @Test fun aDirectionWithoutAnActionDoesNotMove() {
        assertTrue(SwipeGestures.allowed(10f, msg, call))
        assertFalse(SwipeGestures.allowed(10f, msg, none))
        assertFalse(SwipeGestures.allowed(-10f, none, call))
        assertEquals(0f, SwipeGestures.clampDistance(-300f, 1000f, none, call), 0f)
        assertEquals(1000f, SwipeGestures.clampDistance(5000f, 1000f, none, call), 0f)
    }

    @Test fun nothingRunsBeforeTheRowIsMeasured() {
        val t = SwipeGestures.threshold(0f, 60)
        assertEquals(SwipeAction.NONE, SwipeGestures.onRelease(500f, t, true, msg, call))
    }

    @Test fun releasingPastTheThresholdRunsTheAction() {
        val t = SwipeGestures.threshold(1000f, 60)
        assertEquals(600f, t, 0.01f)
        assertEquals(call, SwipeGestures.onRelease(650f, t, true, msg, call))
        assertEquals(msg, SwipeGestures.onRelease(-650f, t, true, msg, call))
        assertEquals(SwipeAction.NONE, SwipeGestures.onRelease(550f, t, true, msg, call))
    }

    @Test fun aCancelledSwipeNeverRunsAnything() {
        val t = SwipeGestures.threshold(1000f, 60)
        assertEquals(SwipeAction.NONE, SwipeGestures.onRelease(900f, t, false, msg, call))
    }

    @Test fun theRowFollowsTheFingerThenSlowsPastTheThreshold() {
        assertEquals(300f, SwipeGestures.visualOffset(300f, 600f), 0.01f)
        assertEquals(630f, SwipeGestures.visualOffset(700f, 600f), 0.01f)
        assertEquals(-630f, SwipeGestures.visualOffset(-700f, 600f), 0.01f)
        assertEquals(0.5f, SwipeGestures.progress(300f, 600f), 0.001f)
        assertEquals(1f, SwipeGestures.progress(900f, 600f), 0.001f)
    }

    @Test fun thresholdStaysInsideTheSettingsRange() {
        assertEquals(300f, SwipeGestures.threshold(1000f, 5), 0.01f)
        assertEquals(900f, SwipeGestures.threshold(1000f, 100), 0.01f)
    }
}
