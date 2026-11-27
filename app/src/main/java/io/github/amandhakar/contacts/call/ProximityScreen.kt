package io.github.amandhakar.contacts.call

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import io.github.amandhakar.contacts.util.Telemetry

/**
 * Should the screen go dark when something is close to the sensor (the phone is at the ear)?
 *
 * Only while a call is up and nothing is ringing (a ringing call has to be seen to be answered), and only while the
 * audio is on the phone's own earpiece: on speaker, Bluetooth or a wired headset the phone is not held to the ear.
 * No audio state yet counts as the earpiece, which is where Android starts a call.
 */
fun earDetectionWanted(callStates: List<Int>, audioRoute: Int?): Boolean {
    if (callStates.isEmpty() || callStates.any { it == Call.STATE_RINGING }) return false
    val offHook = callStates.any {
        it == Call.STATE_ACTIVE || it == Call.STATE_DIALING || it == Call.STATE_CONNECTING ||
            it == Call.STATE_HOLDING || it == Call.STATE_PULLING_CALL
    }
    if (!offHook) return false
    return audioRoute == null || audioRoute == CallAudioState.ROUTE_EARPIECE
}

/**
 * Ear detection during calls: holds Android's proximity wake lock, so the system turns the screen off (and ignores
 * touches) while the phone is near the face, and back on when it moves away. Owned by the call service, so it works
 * whether or not the call screen is in front.
 */
class ProximityScreen(context: Context) {
    private val lock: PowerManager.WakeLock? = run {
        val pm = context.getSystemService(PowerManager::class.java)
        if (pm != null && pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "contacts:ear-detection").apply { setReferenceCounted(false) }
        } else null
    }

    /** False on phones without a proximity sensor; then nothing here does anything. */
    val supported: Boolean get() = lock != null

    @Synchronized
    fun update(calls: List<CallInfo>, audio: CallAudioState?) {
        val wanted = earDetectionWanted(calls.map { it.state }, audio?.route)
        // Switching to speaker or a headset lights the screen at once; a call ending waits until the phone is
        // away from the face, so the screen doesn't come on against the cheek.
        val switchedAway = calls.isNotEmpty() && audio != null && audio.route != CallAudioState.ROUTE_EARPIECE
        set(wanted, immediately = switchedAway)
    }

    /** Lets go for good, e.g. when the call service is going away. */
    @Synchronized
    fun release() = set(false, immediately = false)

    @SuppressLint("WakelockTimeout") // Held only while a call is up; released when the call ends or the service goes.
    private fun set(on: Boolean, immediately: Boolean) {
        val l = lock ?: return
        try {
            if (on && !l.isHeld) l.acquire()
            else if (!on && l.isHeld) l.release(if (immediately) 0 else PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        } catch (e: Exception) {
            Telemetry.nonFatal("proximityLock", e)
        }
    }
}
