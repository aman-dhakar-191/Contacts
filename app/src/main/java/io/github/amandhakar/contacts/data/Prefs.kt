package io.github.amandhakar.contacts.data

import android.content.Context

enum class SwipeAction(val label: String, val key: String) {
    CALL("Call", "call"),
    MESSAGE("Message", "message"),
    VIEW_CONTACT("View contact", "view"),
    FAVORITE("Favorite", "favorite"),
    WHATSAPP("WhatsApp", "whatsapp"),
    NONE("None", "none");

    companion object {
        fun fromKey(key: String): SwipeAction = values().firstOrNull { it.key == key } ?: NONE
        fun fromStringOrDefault(value: String?, fallback: String): SwipeAction = fromKey(value ?: fallback)
    }
}

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** One global gesture mapping shared by Recents and Contacts. */
    var swipeLeftAction: SwipeAction
        get() = SwipeAction.fromKey(sp.getString("swipe_left_action", "message") ?: "message")
        set(v) = sp.edit().putString("swipe_left_action", v.key).apply()

    var swipeRightAction: SwipeAction
        get() = SwipeAction.fromStringOrDefault(sp.getString("swipe_right_action", "call"), "call")
        set(v) = sp.edit().putString("swipe_right_action", v.key).apply()

    /** Percentage of the full contact row width required to execute a swipe action. */
    var swipeActionThresholdPercent: Int
        get() = sp.getInt("swipe_action_threshold_percent", 60).coerceIn(30, 90)
        set(v) = sp.edit().putInt("swipe_action_threshold_percent", v.coerceIn(30, 90)).apply()

    var autoRecord: Boolean
        get() = sp.getBoolean("auto_record", false)
        set(v) = sp.edit().putBoolean("auto_record", v).apply()

    /** Also record every call from a number that is not in the contacts (and hidden numbers). */
    var recordUnknown: Boolean
        get() = sp.getBoolean("record_unknown", false)
        set(v) = sp.edit().putBoolean("record_unknown", v).apply()

    /** Numbers that are always recorded, one per line: key|number|label. See RecordRules. */
    var recordList: String
        get() = sp.getString("record_list", "") ?: ""
        set(v) = sp.edit().putString("record_list", v).apply()

    /** [AccountInfo.key]s of accounts whose contacts are not shown. Empty = show everything. */
    var hiddenAccounts: Set<String>
        get() = sp.getStringSet("hidden_accounts", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("hidden_accounts", v).apply()

    /** [AccountInfo.key] that new contacts are saved to (and so sync to); null = first Google account. */
    var defaultAccount: String?
        get() = sp.getString("default_account", null)
        set(v) = sp.edit().putString("default_account", v).apply()

    /** Send crash reports and anonymous usage to Firebase. On by default; the switch is in Settings. */
    var telemetry: Boolean
        get() = sp.getBoolean("telemetry", true)
        set(v) = sp.edit().putBoolean("telemetry", v).apply()

    /** Master switch of the call identifier (rules + manual flags). Off until the user turns it on. */
    var identifierOn: Boolean
        get() = sp.getBoolean("identifier_on", false)
        set(v) = sp.edit().putBoolean("identifier_on", v).apply()

    /** Bumped whenever identifier rules, categories or flags change, so cached results are redone. */
    var identifierVersion: Int
        get() = sp.getInt("identifier_version", 1)
        set(v) = sp.edit().putInt("identifier_version", v).apply()

    /** Remember the caller-ID name Android gives for unknown callers, to show it on later calls and in Recents. */
    var rememberCallerNames: Boolean
        get() = sp.getBoolean("remember_caller_names", true)
        set(v) = sp.edit().putBoolean("remember_caller_names", v).apply()

    /** Experimental: show the name of the connected Bluetooth device in calls. Needs the Bluetooth permission. */
    var bluetoothNames: Boolean
        get() = sp.getBoolean("bt_names", true)
        set(v) = sp.edit().putBoolean("bt_names", v).apply()

    /** When only the microphone can be recorded, switch to speaker so the other side is audible. */
    var speakerForRecording: Boolean
        get() = sp.getBoolean("speaker_for_recording", true)
        set(v) = sp.edit().putBoolean("speaker_for_recording", v).apply()

    /** A [RecSource] enum name, or "auto". Set from the recording test. */
    var preferredSource: String
        get() = sp.getString("preferred_source", "auto") ?: "auto"
        set(v) = sp.edit().putString("preferred_source", v).apply()

    /** JSON of the last recording test, see RecordingProbe. */
    var lastProbe: String?
        get() = sp.getString("last_probe", null)
        set(v) = sp.edit().putString("last_probe", v).apply()

    fun speedDial(slot: Int): Pair<String, String>? {
        val v = sp.getString("sd_$slot", null) ?: return null
        val i = v.indexOf('|')
        return if (i < 0) null else v.substring(0, i) to v.substring(i + 1)
    }

    fun setSpeedDial(slot: Int, name: String, number: String) =
        sp.edit().putString("sd_$slot", "${name.replace('|', ' ')}|$number").apply()

    fun clearSpeedDial(slot: Int) = sp.edit().remove("sd_$slot").apply()
}
