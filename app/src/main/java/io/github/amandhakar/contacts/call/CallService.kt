package io.github.amandhakar.contacts.call

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import io.github.amandhakar.contacts.data.ContactsRepo
import io.github.amandhakar.contacts.data.RecordRules
import io.github.amandhakar.contacts.util.Telemetry
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
    private val autoDecided = HashSet<Call>()

    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    // Re-posts the live call notification each second so the timer in the island / pill keeps counting.
    private val clock = object : Runnable {
        override fun run() {
            val c = CallHub.current()
            if (c != null && c.state == Call.STATE_ACTIVE && c.connectTimeMillis > 0) updateNotifications()
            ui.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        CallHub.service = this
        val names = io.github.amandhakar.contacts.data.CallerNames(applicationContext)
        val prefs = io.github.amandhakar.contacts.data.Prefs(applicationContext)
        CallHub.rememberedName = { n -> if (prefs.rememberCallerNames) names.get(n) else null }
        ui.postDelayed(clock, 1000)
    }

    override fun onDestroy() {
        ui.removeCallbacks(clock)
        CallHub.service = null
        CallHub.rememberedName = null
        scope.cancel()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onCallAdded(call: Call) {
        val cb = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) = handleState(call, state)
            override fun onDetailsChanged(call: Call, details: Call.Details) { rememberCallerName(call); CallHub.refresh(); updateNotifications() }
        }
        call.registerCallback(cb)
        callbacks[call] = cb
        CallHub.add(call)
        Telemetry.event("call_added", "incoming" to (call.details.callDirection == Call.Details.DIRECTION_INCOMING))

        rememberCallerName(call)
        val number = call.details.handle?.schemeSpecificPart ?: ""
        val incomingCall = call.details.callDirection == Call.Details.DIRECTION_INCOMING
        scope.launch {
            val caller = ContactsRepo(applicationContext).lookup(number)
            CallHub.setCaller(call, caller)
            updateNotifications()
            // Categorise it (only does anything when the identifier is on); logged either way
            val tag = io.github.amandhakar.contacts.identifier.Identifier.classifyLive(applicationContext, number, caller?.name, incomingCall)
            CallHub.setTag(call, tag)
            updateNotifications()
        }

        updateNotifications()
        if (call.state != Call.STATE_RINGING) openCallScreen()
    }

    /** Keeps the caller-ID name Android gives for a number that is not in the contacts, for later calls. Never saved to contacts. */
    @Suppress("DEPRECATION")
    private fun rememberCallerName(call: Call) {
        val number = call.details.handle?.schemeSpecificPart ?: return
        val name = call.details.callerDisplayName?.takeIf { it.isNotBlank() } ?: return
        if (!io.github.amandhakar.contacts.data.Prefs(applicationContext).rememberCallerNames) return
        scope.launch {
            if (ContactsRepo(applicationContext).lookup(number) == null) {
                io.github.amandhakar.contacts.data.CallerNames(applicationContext).put(number, name)
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        val info = CallHub.calls.value.firstOrNull { it.call == call }
        callbacks.remove(call)?.let { call.unregisterCallback(it) }
        val wasAnswered = answered.remove(call)
        autoDecided.remove(call)
        CallHub.remove(call)
        updateNotifications()

        val cause = call.details.disconnectCause?.code
        if (info != null && info.incoming && !wasAnswered && cause == DisconnectCause.MISSED) {
            Telemetry.event("call_missed")
            Notifications.missed(this, info.number, info.name)
        }
        if (CallHub.calls.value.isEmpty()) RecorderService.stop(this)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        CallHub.setAudio(audioState)
        updateNotifications() // so the Speaker / Mute button labels follow the real state
    }

    @Volatile private var incomingShown: String? = null

    /** The one place that decides what is in the notification shade: Answer/Decline while ringing, else the live call. */
    @Synchronized private fun updateNotifications() {
        val calls = CallHub.calls.value
        val ringing = calls.firstOrNull { it.state == Call.STATE_RINGING }
        if (ringing != null) {
            val key = "${System.identityHashCode(ringing.call)}:${ringing.name}:${ringing.tag?.name}"
            if (key != incomingShown) { incomingShown = key; Notifications.incoming(this, ringing) }
        } else if (incomingShown != null) {
            incomingShown = null
            Notifications.cancelIncoming(this)
        }
        val current = CallHub.current()
        if (current != null) {
            val audio = CallHub.audio.value
            Notifications.ongoing(this, current, calls.size - 1, audio?.route == CallAudioState.ROUTE_SPEAKER, audio?.isMuted == true, AudioRoutes.notificationLabel(audio))
        } else {
            Notifications.cancelOngoing(this)
        }
    }

    private fun handleState(call: Call, @Suppress("UNUSED_PARAMETER") state: Int) {
        CallHub.refresh()
        updateNotifications()
        val info = CallHub.calls.value.firstOrNull { it.call == call } ?: return
        if (info.state == Call.STATE_ACTIVE) {
            answered += call
            // Decide once per call, so stopping a recording by hand isn't undone when the call comes off hold.
            if (autoDecided.add(call) && !RecorderState.active.value) {
                scope.launch {
                    val known = ContactsRepo(applicationContext).lookup(info.number) != null
                    val reason = RecordRules(applicationContext).reason(info.number, known)
                    if (reason != null && !RecorderState.active.value) {
                        RecorderState.reason.value = reason.label
                        RecorderService.start(applicationContext, info.number, info.incoming)
                    }
                }
            }
        }
    }

    private fun openCallScreen() {
        try {
            startActivity(Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Telemetry.nonFatal("openCallScreen", e)
        }
    }
}
