package io.github.amandhakar.contacts.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.CallAudioState

/** Buttons on the call notifications. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DECLINE -> {
                CallHub.ringing()?.let { CallHub.reject(it.call) }
                Notifications.cancelIncoming(context)
            }
            ACTION_HANGUP -> CallHub.current()?.let { CallHub.hangUp(it.call) }
            ACTION_SPEAKER -> CallHub.setSpeaker(CallHub.audio.value?.route != CallAudioState.ROUTE_SPEAKER)
            ACTION_MUTE -> CallHub.setMuted(CallHub.audio.value?.isMuted != true)
        }
    }

    companion object {
        const val ACTION_DECLINE = "io.github.amandhakar.contacts.DECLINE"
        const val ACTION_HANGUP = "io.github.amandhakar.contacts.HANGUP"
        const val ACTION_SPEAKER = "io.github.amandhakar.contacts.SPEAKER"
        const val ACTION_MUTE = "io.github.amandhakar.contacts.MUTE"
    }
}
