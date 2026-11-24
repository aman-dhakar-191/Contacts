package io.github.amandhakar.contacts.call

import android.telecom.CallAudioState

/** Where the call audio can go (phone earpiece, wired headset, Bluetooth, speaker) and how to name it. */
object AudioRoutes {
    data class Route(val route: Int, val label: String)

    /** Routes the phone offers right now, in a fixed order. */
    fun available(a: CallAudioState?): List<Route> {
        val mask = a?.supportedRouteMask ?: return emptyList()
        return buildList {
            if (mask and CallAudioState.ROUTE_EARPIECE != 0) add(Route(CallAudioState.ROUTE_EARPIECE, "Phone"))
            if (mask and CallAudioState.ROUTE_WIRED_HEADSET != 0) add(Route(CallAudioState.ROUTE_WIRED_HEADSET, "Wired headset"))
            if (mask and CallAudioState.ROUTE_BLUETOOTH != 0) add(Route(CallAudioState.ROUTE_BLUETOOTH, bluetoothName(a) ?: "Bluetooth"))
            if (mask and CallAudioState.ROUTE_SPEAKER != 0) add(Route(CallAudioState.ROUTE_SPEAKER, "Speaker"))
        }
    }

    /** Short name of where the audio is going now, e.g. "Bluetooth · Buds", "Wired headset", "Speaker", "Phone". */
    fun currentLabel(a: CallAudioState?): String = when (a?.route) {
        CallAudioState.ROUTE_SPEAKER -> "Speaker"
        CallAudioState.ROUTE_BLUETOOTH -> bluetoothName(a)?.let { "Bluetooth · $it" } ?: "Bluetooth"
        CallAudioState.ROUTE_WIRED_HEADSET -> "Wired headset"
        CallAudioState.ROUTE_EARPIECE -> "Phone"
        else -> "Phone"
    }

    /** The Bluetooth device name, only if Android lets us read it (needs the Bluetooth permission on Android 12+). */
    @Suppress("DEPRECATION")
    private fun bluetoothName(a: CallAudioState): String? = try {
        a.activeBluetoothDevice?.name?.takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }

    /** For the notification line: null when the audio is on the phone itself (nothing worth saying). */
    fun notificationLabel(a: CallAudioState?): String? = if (a?.route == CallAudioState.ROUTE_EARPIECE || a == null) null else currentLabel(a)
}
