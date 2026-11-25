package io.github.amandhakar.contacts.call

import android.content.Context
import android.os.Build
import android.telecom.Call
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A diagnostic: everything Android hands the app about a call (the Call.Details getters and all its extras), kept for the
 * last few calls so you can see what your phone and carrier really supply. Stays on this phone, shown in Settings >
 * Experimental > Call info, never sent anywhere. Can be switched off and cleared there.
 */
object CallInfoLog {
    private const val MAX = 20
    private const val PREFS = "call_info_log"

    data class Entry(val id: String, val fields: List<Pair<String, String>>)

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(ctx: Context) = sp(ctx).getBoolean("enabled", true)
    fun setEnabled(ctx: Context, on: Boolean) { sp(ctx).edit().putBoolean("enabled", on).apply() }

    /** Records or updates the entry for this call. Safe to call often: it replaces the entry of the same call. */
    @Synchronized fun record(ctx: Context, call: Call) {
        if (!enabled(ctx)) return
        val fields = try { snapshot(call) } catch (_: Throwable) { return }
        val id = fields.firstOrNull { it.first == "callId" }?.second ?: return
        val list = load(ctx).filter { it.id != id }.toMutableList()
        list.add(0, Entry(id, fields))
        save(ctx, list.take(MAX))
    }

    /** Adds how a call ended to its entry. */
    @Synchronized fun recordEnd(ctx: Context, call: Call, cause: String?) {
        if (!enabled(ctx)) return
        val fields = try { snapshot(call) } catch (_: Throwable) { return }
        val id = fields.firstOrNull { it.first == "callId" }?.second ?: return
        val withEnd = fields + ("endedAt" to now()) + ("disconnectCause" to (cause ?: "null"))
        val list = load(ctx).filter { it.id != id }.toMutableList()
        list.add(0, Entry(id, withEnd))
        save(ctx, list.take(MAX))
    }

    fun entries(ctx: Context): List<Entry> = load(ctx)

    fun clear(ctx: Context) { sp(ctx).edit().remove("entries").apply() }

    private fun load(ctx: Context): List<Entry> = try {
        val arr = JSONArray(sp(ctx).getString("entries", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val f = o.getJSONArray("fields")
            Entry(o.getString("id"), (0 until f.length()).map { j -> f.getJSONArray(j).let { it.getString(0) to it.getString(1) } })
        }
    } catch (_: Exception) { emptyList() }

    private fun save(ctx: Context, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().put("id", e.id).put("fields", JSONArray().also { f -> e.fields.forEach { (k, v) -> f.put(JSONArray().put(k).put(v)) } }))
        }
        sp(ctx).edit().putString("entries", arr.toString()).apply()
    }

    private fun now() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    @Suppress("DEPRECATION")
    private fun snapshot(call: Call): List<Pair<String, String>> {
        val d = call.details
        val out = ArrayList<Pair<String, String>>()
        // Every getter runs on its own: one that this Android version lacks or refuses must not hide the rest.
        fun add(key: String, value: () -> Any?) {
            out += key to (try { value()?.toString() ?: "null" } catch (t: Throwable) { "error: ${t.javaClass.simpleName}" })
        }
        // Newer getters are read by name so an older or different Android cannot break the build or the call.
        fun viaReflection(key: String, method: String) = add(key) { Call.Details::class.java.getMethod(method).invoke(d) }

        add("callId") { "${d.creationTimeMillis}:${d.handle}" }
        add("updatedAt") { now() }
        add("state") { stateName(call.state) }
        add("direction") { when (d.callDirection) { Call.Details.DIRECTION_INCOMING -> "incoming"; Call.Details.DIRECTION_OUTGOING -> "outgoing"; else -> "unknown(${d.callDirection})" } }
        add("handle") { d.handle }
        add("handlePresentation") { presentation(d.handlePresentation) }
        add("callerDisplayName") { d.callerDisplayName }
        add("callerDisplayNamePresentation") { presentation(d.callerDisplayNamePresentation) }
        viaReflection("contactDisplayName", "getContactDisplayName")
        if (Build.VERSION.SDK_INT >= 30) add("callerNumberVerificationStatus") { verification(d.callerNumberVerificationStatus) }
        viaReflection("callScreeningAppName", "getCallScreeningAppName")
        add("accountHandle") { d.accountHandle?.let { "${it.componentName.flattenToShortString()} / ${it.id}" } }
        add("callProperties") { properties(d.callProperties) }
        add("callCapabilities") { "0x" + Integer.toHexString(d.callCapabilities) }
        add("videoState") { d.videoState }
        add("creationTime") { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(d.creationTimeMillis)) }
        add("connectTime") { if (d.connectTimeMillis > 0) SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(d.connectTimeMillis)) else "not connected" }
        add("statusHints") { d.statusHints?.let { "${it.label} / ${it.extras}" } }
        add("gatewayInfo") { d.gatewayInfo?.let { "${it.gatewayProviderPackageName} -> ${it.originalAddress}" } }
        d.extras?.let { b -> b.keySet().sorted().forEach { k -> add("extra.$k") { b.get(k) } } }
        d.intentExtras?.let { b -> b.keySet().sorted().forEach { k -> add("intentExtra.$k") { b.get(k) } } }
        return out
    }

    private fun stateName(s: Int) = when (s) {
        Call.STATE_NEW -> "new"; Call.STATE_DIALING -> "dialing"; Call.STATE_RINGING -> "ringing"; Call.STATE_HOLDING -> "holding"
        Call.STATE_ACTIVE -> "active"; Call.STATE_DISCONNECTED -> "disconnected"; Call.STATE_CONNECTING -> "connecting"
        Call.STATE_DISCONNECTING -> "disconnecting"; Call.STATE_SELECT_PHONE_ACCOUNT -> "select account"; else -> "state $s"
    }

    /** TelecomManager presentation constants: 1 allowed, 2 restricted, 3 unknown, 4 payphone. */
    private fun presentation(p: Int) = when (p) {
        1 -> "allowed (1)"; 2 -> "restricted / private (2)"; 3 -> "unknown (3)"; 4 -> "payphone (4)"; else -> "$p"
    }

    /** Connection.VERIFICATION_STATUS_*: 0 not verified, 1 passed, 2 failed (STIR/SHAKEN). */
    private fun verification(v: Int) = when (v) { 0 -> "not verified (0)"; 1 -> "passed (1)"; 2 -> "failed (2)"; else -> "$v" }

    private fun properties(p: Int): String {
        val names = buildList {
            if (p and Call.Details.PROPERTY_CONFERENCE != 0) add("conference")
            if (p and Call.Details.PROPERTY_EMERGENCY_CALL != 0) add("emergency")
            if (p and Call.Details.PROPERTY_HIGH_DEF_AUDIO != 0) add("HD audio")
            if (p and Call.Details.PROPERTY_WIFI != 0) add("Wi-Fi calling")
            if (p and Call.Details.PROPERTY_SELF_MANAGED != 0) add("self managed")
            if (p and Call.Details.PROPERTY_RTT != 0) add("RTT")
            if (p and Call.Details.PROPERTY_IS_EXTERNAL_CALL != 0) add("external")
        }
        return "0x" + Integer.toHexString(p) + if (names.isEmpty()) "" else " (${names.joinToString(", ")})"
    }
}
