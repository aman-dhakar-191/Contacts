package io.github.amandhakar.contacts.call

import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import io.github.amandhakar.contacts.data.ContactsRepo
import io.github.amandhakar.contacts.data.Prefs
import io.github.amandhakar.contacts.data.RecordRules
import io.github.amandhakar.contacts.util.RecordingDiagnostics
import io.github.amandhakar.contacts.util.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Android binds this while the app is the default phone app and a call exists. */
class CallService : InCallService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val callbacks = HashMap<Call, Call.Callback>()
    private val answered = HashSet<Call>()
    private val autoDecided = HashSet<Call>()
    private var proximity: ProximityScreen? = null

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
        // Ear detection: screen off while the phone is at the ear during a call, back on when it is lowered.
        val ear = ProximityScreen(applicationContext)
        proximity = ear
        if (ear.supported) {
            scope.launch { combine(CallHub.calls, CallHub.audio) { c, a -> c to a }.collect { (c, a) -> ear.update(c, a) } }
        }
    }

    override fun onDestroy() {
        ui.removeCallbacks(clock)
        proximity?.release()
        proximity = null
        CallHub.service = null
        CallHub.rememberedName = null
        scope.cancel()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onCallAdded(call: Call) {
        val cb = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) = handleState(call, state)
            override fun onDetailsChanged(call: Call, details: Call.Details) {
                rememberCallerName(call); CallInfoLog.record(applicationContext, call); CallHub.refresh(); updateNotifications()
                // Backup for phones that don't report the change to "active" through onStateChanged.
                maybeAutoRecord(call, "details changed")
            }
        }
        call.registerCallback(cb)
        callbacks[call] = cb
        CallHub.add(call)
        Telemetry.event("call_added", "incoming" to (call.details.callDirection == Call.Details.DIRECTION_INCOMING))

        rememberCallerName(call)
        CallInfoLog.record(applicationContext, call)
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
        maybeAutoRecord(call, "call added")
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
        CallInfoLog.recordEnd(applicationContext, call, call.details.disconnectCause?.let { "${it.code} ${it.reason ?: ""}".trim() })
        if (info != null && info.incoming && !wasAnswered && cause == DisconnectCause.MISSED) {
            Telemetry.event("call_missed")
            Notifications.missed(this, info.number, info.name, info.nameFromCallerId)
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
            val key = "${System.identityHashCode(ringing.call)}:${ringing.name}:${ringing.callerAlert?.alert}:${ringing.tag?.name}"
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
        if (info.state == Call.STATE_ACTIVE) answered += call
        maybeAutoRecord(call, "state changed")
    }

    @Suppress("DEPRECATION")
    private fun stateOf(call: Call): Int = if (Build.VERSION.SDK_INT >= 31) call.details.state else call.state

    /**
     * Starts recording by itself when the call is connected and a rule says so (all calls, unknown numbers, auto-record list).
     * Decided once per call, so stopping a recording by hand isn't undone when the call comes off hold. Called on every
     * state and details change, because some phones skip one of them. Every decision goes in the recording log.
     */
    private fun maybeAutoRecord(call: Call, trigger: String) {
        val state = stateOf(call)
        if (state != Call.STATE_ACTIVE) return
        if (!autoDecided.add(call)) return
        val number = call.details.handle?.schemeSpecificPart ?: ""
        val incoming = call.details.callDirection == Call.Details.DIRECTION_INCOMING
        scope.launch {
            try {
                val prefs = Prefs(applicationContext)
                val known = ContactsRepo(applicationContext).lookup(number) != null
                val inList = RecordRules(applicationContext).contains(number)
                val reason = RecordRules.decide(prefs.autoRecord, prefs.recordUnknown, inList, known)
                val busy = RecorderState.active.value
                RecordingLog.add(
                    applicationContext,
                    "auto-record check ($trigger, ${if (incoming) "incoming" else "outgoing"}): " +
                        RecordRules.explain(prefs.autoRecord, prefs.recordUnknown, inList, known) +
                        if (busy) "; already recording" else "",
                )
                if (reason != null && !busy) {
                    RecorderState.reason.value = reason.label
                    RecorderService.start(applicationContext, number, incoming)
                }
            } catch (e: Exception) {
                RecordingLog.add(applicationContext, "auto-record check failed: ${RecordingDiagnostics.errorText(e.javaClass.name, e.message)}")
                Telemetry.nonFatal("auto_record", e)
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
