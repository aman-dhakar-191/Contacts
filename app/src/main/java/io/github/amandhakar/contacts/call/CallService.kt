package io.github.amandhakar.contacts.call

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import io.github.amandhakar.contacts.data.ContactsRepo
import io.github.amandhakar.contacts.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Android binds this while the app is the default phone app and a call exists. */
class CallService : InCallService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val callbacks = HashMap<Call, Call.Callback>()
    private val answered = HashSet<Call>()

    override fun onCreate() {
        super.onCreate()
        CallHub.service = this
    }

    override fun onDestroy() {
        CallHub.service = null
        scope.cancel()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onCallAdded(call: Call) {
        val cb = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) = handleState(call, state)
            override fun onDetailsChanged(call: Call, details: Call.Details) = CallHub.refresh()
        }
        call.registerCallback(cb)
        callbacks[call] = cb
        CallHub.add(call)

        val number = call.details.handle?.schemeSpecificPart ?: ""
        scope.launch { CallHub.setCaller(call, ContactsRepo(applicationContext).lookup(number)) }

        if (call.state == Call.STATE_RINGING) {
            CallHub.ringing()?.let { Notifications.incoming(this, it) }
        } else {
            openCallScreen()
        }
    }

    override fun onCallRemoved(call: Call) {
        val info = CallHub.calls.value.firstOrNull { it.call == call }
        callbacks.remove(call)?.let { call.unregisterCallback(it) }
        val wasAnswered = answered.remove(call)
        CallHub.remove(call)
        Notifications.cancelIncoming(this)

        val cause = call.details.disconnectCause?.code
        if (info != null && info.incoming && !wasAnswered && cause == DisconnectCause.MISSED) {
            Notifications.missed(this, info.number, info.name)
        }
        if (CallHub.calls.value.isEmpty()) {
            Notifications.cancelOngoing(this)
            RecorderService.stop(this)
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) = CallHub.setAudio(audioState)

    private fun handleState(call: Call, @Suppress("UNUSED_PARAMETER") state: Int) {
        CallHub.refresh()
        val info = CallHub.calls.value.firstOrNull { it.call == call } ?: return
        if (info.state != Call.STATE_RINGING) Notifications.cancelIncoming(this)
        if (info.state == Call.STATE_ACTIVE) {
            answered += call
            Notifications.ongoing(this, info)
            if (Prefs(this).autoRecord && !RecorderState.active.value) {
                RecorderService.start(this, info.number, info.incoming)
            }
        }
    }

    private fun openCallScreen() {
        try {
            startActivity(Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
}
