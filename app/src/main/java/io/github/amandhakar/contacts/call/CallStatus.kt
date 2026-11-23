package io.github.amandhakar.contacts.call

import android.telecom.Call

/** Short status for a call, used by the in-app header. `seconds` is the talk time so far, or null. */
fun callStatusLabel(state: Int, seconds: Long?): String = when (state) {
    Call.STATE_RINGING -> "Incoming call"
    Call.STATE_HOLDING -> "On hold"
    Call.STATE_ACTIVE -> if (seconds != null) "Ongoing call · %d:%02d".format(seconds / 60, seconds % 60) else "Ongoing call"
    else -> "Calling…"
}
