package io.github.amandhakar.contacts

import android.telecom.Call
import android.telecom.CallAudioState
import io.github.amandhakar.contacts.call.earDetectionWanted
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EarDetectionTest {
    @Test fun onDuringCallOnEarpiece() {
        assertTrue(earDetectionWanted(listOf(Call.STATE_ACTIVE), CallAudioState.ROUTE_EARPIECE))
        assertTrue(earDetectionWanted(listOf(Call.STATE_DIALING), CallAudioState.ROUTE_EARPIECE))
        assertTrue(earDetectionWanted(listOf(Call.STATE_CONNECTING), null))
        assertTrue(earDetectionWanted(listOf(Call.STATE_HOLDING, Call.STATE_ACTIVE), CallAudioState.ROUTE_EARPIECE))
    }

    @Test fun offWhenNotHeldToTheEar() {
        assertFalse(earDetectionWanted(listOf(Call.STATE_ACTIVE), CallAudioState.ROUTE_SPEAKER))
        assertFalse(earDetectionWanted(listOf(Call.STATE_ACTIVE), CallAudioState.ROUTE_BLUETOOTH))
        assertFalse(earDetectionWanted(listOf(Call.STATE_ACTIVE), CallAudioState.ROUTE_WIRED_HEADSET))
    }

    @Test fun offWhileRingingOrWithNoCall() {
        assertFalse(earDetectionWanted(emptyList(), CallAudioState.ROUTE_EARPIECE))
        assertFalse(earDetectionWanted(listOf(Call.STATE_RINGING), CallAudioState.ROUTE_EARPIECE))
        // A second call ringing during a call: the screen has to be visible to answer it.
        assertFalse(earDetectionWanted(listOf(Call.STATE_ACTIVE, Call.STATE_RINGING), CallAudioState.ROUTE_EARPIECE))
        assertFalse(earDetectionWanted(listOf(Call.STATE_DISCONNECTED), CallAudioState.ROUTE_EARPIECE))
    }
}
