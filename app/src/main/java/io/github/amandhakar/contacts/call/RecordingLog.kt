package io.github.amandhakar.contacts.call

import android.content.Context
import android.util.Log
import io.github.amandhakar.contacts.util.RecordingDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What happened each time a recording started, failed or stopped: the inputs tried, the step that failed and Android's
 * error. Kept on this phone only (Settings > Recording test > Recording log), never sent anywhere, and never holds a
 * phone number or a name. Also written to logcat with the tag [TAG].
 */
object RecordingLog {
    const val TAG = "ContactsRec"
    private const val MAX_LINES = 300
    private const val PREFS = "recording_log"

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines
    private var loaded = false

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized fun load(ctx: Context) {
        if (loaded) return
        _lines.value = RecordingDiagnostics.decode(sp(ctx).getString("lines", null))
        loaded = true
    }

    @Synchronized fun add(ctx: Context, msg: String) {
        Log.i(TAG, msg)
        try {
            load(ctx)
            val line = "${SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())} $msg"
            val next = RecordingDiagnostics.trim(_lines.value + line, MAX_LINES)
            _lines.value = next
            sp(ctx).edit().putString("lines", RecordingDiagnostics.encode(next)).apply()
        } catch (_: Throwable) {
            // The log must never break a call.
        }
    }

    @Synchronized fun clear(ctx: Context) {
        sp(ctx).edit().remove("lines").apply()
        _lines.value = emptyList()
        loaded = true
    }

    fun text(ctx: Context): String { load(ctx); return _lines.value.joinToString("\n") }
}
