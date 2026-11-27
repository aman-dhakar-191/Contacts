package io.github.amandhakar.contacts.util

import io.github.amandhakar.contacts.data.SwipeAction
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

/**
 * The rules behind row swipe actions, kept free of Android so they can be unit tested.
 * All values are in pixels of the same coordinate space (the app window).
 */
object SwipeGestures {

    /** A swipe only counts when it is clearly sideways: this many times more horizontal than vertical. */
    const val HORIZONTAL_RATIO = 2f

    /** How far the row still moves past the threshold, as a share of the finger's travel (feels like a soft stop). */
    const val PAST_THRESHOLD_RESISTANCE = 0.3f

    /** Time a swipe call waits before dialling, so an accidental swipe can be cancelled. */
    const val CALL_UNDO_MS = 3000L

    /**
     * True when a touch that went down at ([x], [y]) is inside an area the system uses for its own
     * gestures (back from the left or right edge, home and app switching along the bottom).
     * Each inset is widened to at least [minEdge] (some phones report 0 or a too-narrow area)
     * and then by [margin], because people start a back swipe a little inside the edge.
     */
    fun startsInSystemGesture(
        x: Float, y: Float,
        windowWidth: Float, windowHeight: Float,
        leftInset: Float, rightInset: Float, bottomInset: Float,
        minEdge: Float, margin: Float,
    ): Boolean {
        val left = max(leftInset, minEdge) + margin
        val right = max(rightInset, minEdge) + margin
        if (x < left) return true
        if (windowWidth > 0f && x > windowWidth - right) return true
        if (windowHeight > 0f && bottomInset > 0f && y > windowHeight - bottomInset) return true
        return false
    }

    /** True when a movement of ([dx], [dy]) from the touch point reads as a sideways swipe, not a scroll. */
    fun isHorizontal(dx: Float, dy: Float): Boolean = abs(dx) > 0f && abs(dx) >= abs(dy) * HORIZONTAL_RATIO

    /** True when the row may move in the direction of [dx]: right needs a right action, left a left action. */
    fun allowed(dx: Float, swipeLeft: SwipeAction, swipeRight: SwipeAction): Boolean = when {
        dx > 0f -> swipeRight != SwipeAction.NONE
        dx < 0f -> swipeLeft != SwipeAction.NONE
        else -> false
    }

    /** Distance the finger must travel to run an action. Until the row is measured nothing can run. */
    fun threshold(rowWidth: Float, percent: Int): Float =
        if (rowWidth <= 0f) Float.POSITIVE_INFINITY else rowWidth * (percent.coerceIn(30, 90) / 100f)

    /** Keeps the finger's travel inside the directions that have an action, and inside the row. */
    fun clampDistance(distance: Float, rowWidth: Float, swipeLeft: SwipeAction, swipeRight: SwipeAction): Float {
        val min = if (swipeLeft != SwipeAction.NONE) -rowWidth else 0f
        val max = if (swipeRight != SwipeAction.NONE) rowWidth else 0f
        return distance.coerceIn(min, max)
    }

    /** Where the row is drawn: it follows the finger up to the threshold, then moves slower. */
    fun visualOffset(distance: Float, threshold: Float): Float {
        val d = abs(distance)
        if (threshold.isInfinite() || d <= threshold) return distance
        return sign(distance) * (threshold + (d - threshold) * PAST_THRESHOLD_RESISTANCE)
    }

    /** 0 at rest, 1 at the threshold. */
    fun progress(distance: Float, threshold: Float): Float =
        if (threshold.isInfinite() || threshold <= 0f) 0f else (abs(distance) / threshold).coerceIn(0f, 1f)

    /** The action that letting go now would run, or NONE. */
    fun armed(distance: Float, threshold: Float, swipeLeft: SwipeAction, swipeRight: SwipeAction): SwipeAction = when {
        threshold.isInfinite() -> SwipeAction.NONE
        distance >= threshold -> swipeRight
        distance <= -threshold -> swipeLeft
        else -> SwipeAction.NONE
    }

    /**
     * The action to run when the finger lifts. A swipe the system took over (back gesture,
     * a scroll, the touch being cancelled) never runs anything.
     */
    fun onRelease(
        distance: Float, threshold: Float, endedNormally: Boolean,
        swipeLeft: SwipeAction, swipeRight: SwipeAction,
    ): SwipeAction = if (!endedNormally) SwipeAction.NONE else armed(distance, threshold, swipeLeft, swipeRight)
}
