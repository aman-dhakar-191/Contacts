package io.github.amandhakar.contacts.data

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var autoRecord: Boolean
        get() = sp.getBoolean("auto_record", false)
        set(v) = sp.edit().putBoolean("auto_record", v).apply()

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
