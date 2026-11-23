package io.github.amandhakar.contacts

import android.telecom.Call
import io.github.amandhakar.contacts.call.callStatusLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class CallStatusTest {
    @Test fun ringingAndDialingAndHold() {
        assertEquals("Incoming call", callStatusLabel(Call.STATE_RINGING, null))
        assertEquals("Calling…", callStatusLabel(Call.STATE_DIALING, null))
        assertEquals("Calling…", callStatusLabel(Call.STATE_CONNECTING, null))
        assertEquals("On hold", callStatusLabel(Call.STATE_HOLDING, 30))
    }

    @Test fun activeShowsTalkTimeWhenKnown() {
        assertEquals("Ongoing call", callStatusLabel(Call.STATE_ACTIVE, null))
        assertEquals("Ongoing call · 0:05", callStatusLabel(Call.STATE_ACTIVE, 5))
        assertEquals("Ongoing call · 2:07", callStatusLabel(Call.STATE_ACTIVE, 127))
        assertEquals("Ongoing call · 65:00", callStatusLabel(Call.STATE_ACTIVE, 3900))
    }
}
