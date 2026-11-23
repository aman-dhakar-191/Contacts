package io.github.amandhakar.contacts.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DECLINE) {
            CallHub.ringing()?.let { CallHub.reject(it.call) }
            Notifications.cancelIncoming(context)
        }
    }

    companion object {
        const val ACTION_DECLINE = "io.github.amandhakar.contacts.DECLINE"
    }
}
