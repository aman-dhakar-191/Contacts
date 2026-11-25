package io.github.amandhakar.contacts.call

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import io.github.amandhakar.contacts.data.CallerInfo
import io.github.amandhakar.contacts.identifier.Tag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference

data class CallInfo(
    val call: Call,
    val number: String,
    val name: String?,
    val photoUri: String?,
    val state: Int,
    val incoming: Boolean,
    val connectTimeMillis: Long,
    /** Category given by the identifier, if it is on and something matched. */
    val tag: Tag? = null,
    /** The name is only a guess from caller ID (or one remembered from an earlier call), not a saved contact. */
    val nameFromCallerId: Boolean = false,
)

/** The calls Android Telecom is currently showing us, as snapshots the UI can observe. */
object CallHub {
    /** Looks up a caller-ID name remembered from an earlier call (set by the call service). */
    @Volatile var rememberedName: ((String) -> String?)? = null

    private val tracked = ArrayList<Call>()
    private val callers = HashMap<Call, CallerInfo>()
    private val tags = HashMap<Call, Tag>()
    private val _calls = MutableStateFlow<List<CallInfo>>(emptyList())
    val calls: StateFlow<List<CallInfo>> = _calls

    private val _audio = MutableStateFlow<CallAudioState?>(null)
    val audio: StateFlow<CallAudioState?> = _audio

    private var serviceRef: WeakReference<InCallService>? = null
    var service: InCallService?
        get() = serviceRef?.get()
        set(v) { serviceRef = v?.let { WeakReference(it) } }

    @Synchronized fun add(call: Call) { if (call !in tracked) tracked += call; refresh() }

    @Synchronized fun remove(call: Call) { tracked -= call; callers -= call; tags -= call; refresh() }

    @Synchronized fun setTag(call: Call, tag: Tag?) {
        if (tag != null) tags[call] = tag else tags.remove(call)
        refresh()
    }

    @Synchronized fun setCaller(call: Call, info: CallerInfo?) {
        if (info != null) callers[call] = info
        refresh()
    }

    fun setAudio(state: CallAudioState?) { _audio.value = state }

    @Suppress("DEPRECATION")
    @Synchronized fun refresh() {
        _calls.value = tracked.map { c ->
            val d = c.details
            val number = d.handle?.schemeSpecificPart ?: ""
            val caller = callers[c]
            val shownName = caller?.name ?: d.callerDisplayName?.takeIf { it.isNotBlank() } ?: rememberedName?.invoke(number)
            CallInfo(
                call = c, number = number,
                name = shownName,
                photoUri = caller?.photoUri,
                state = c.state,
                incoming = d.callDirection == Call.Details.DIRECTION_INCOMING,
                connectTimeMillis = d.connectTimeMillis,
                tag = tags[c],
                nameFromCallerId = caller == null && shownName != null,
            )
        }
    }

    /** The call the live notification and its buttons act on: the first one that is not ringing or ending. */
    fun current(): CallInfo? = _calls.value.firstOrNull {
        it.state != Call.STATE_RINGING && it.state != Call.STATE_DISCONNECTED && it.state != Call.STATE_DISCONNECTING
    }

    fun ringing(): CallInfo? = _calls.value.firstOrNull { it.state == Call.STATE_RINGING }

    fun answer(call: Call) = call.answer(VideoProfile.STATE_AUDIO_ONLY)
    fun reject(call: Call) = call.reject(false, null)
    fun hangUp(call: Call) = call.disconnect()
    fun hold(call: Call) = call.hold()
    fun unhold(call: Call) = call.unhold()

    fun setMuted(muted: Boolean) { service?.setMuted(muted) }

    fun setRoute(route: Int) { service?.setAudioRoute(route) }

    fun setSpeaker(on: Boolean) {
        service?.setAudioRoute(if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE)
    }
}
