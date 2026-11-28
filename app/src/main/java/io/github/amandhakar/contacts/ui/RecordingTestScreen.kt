@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.amandhakar.contacts.ui

import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.amandhakar.contacts.AppViewModel
import io.github.amandhakar.contacts.call.AccessibilityHelper
import io.github.amandhakar.contacts.call.CallHub
import io.github.amandhakar.contacts.call.ProbeResult
import io.github.amandhakar.contacts.call.ProbeStatus
import io.github.amandhakar.contacts.call.RecorderState
import io.github.amandhakar.contacts.call.RecordingLog
import io.github.amandhakar.contacts.call.RecordingProbe
import io.github.amandhakar.contacts.data.Prefs
import io.github.amandhakar.contacts.data.RecSource

/** Settings > Recording test. Needs a live call: start one, put it on speaker and run the test. */
@Composable
fun RecordingTestScreen(vm: AppViewModel) {
    val calls by CallHub.calls.collectAsState()
    Page("Recording test", vm) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                ProbeControls(hasCall = calls.isNotEmpty())
                Spacer(Modifier.height(24.dp))
                RecordingLogSection()
            }
        }
    }
}

/** The last lines of [RecordingLog]: what happened when recording started, failed or stopped. Copy sends it all. */
@Composable
fun RecordingLogSection() {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { RecordingLog.load(ctx) }
    val lines by RecordingLog.lines.collectAsState()
    Text("Recording log", style = MaterialTheme.typography.titleMedium)
    Text(
        "What happened each time a recording started, failed or stopped. Stays on this phone and never holds numbers or names. " +
            "If recording fails, tap Copy and paste it to whoever is fixing it.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = lines.isNotEmpty(), onClick = {
            val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
            cm?.setPrimaryClip(android.content.ClipData.newPlainText("Recording log", RecordingLog.text(ctx)))
            Toast.makeText(ctx, "Recording log copied", Toast.LENGTH_SHORT).show()
        }) { Text("Copy") }
        TextButton(enabled = lines.isNotEmpty(), onClick = { RecordingLog.clear(ctx) }) { Text("Clear") }
    }
    if (lines.isEmpty()) {
        Text("Nothing yet. Start a recording in a call.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text(
            lines.takeLast(60).joinToString("\n"),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Instructions, run button and the results list. Shared by the Settings page and the in-call dialog. */
@Composable
fun ProbeControls(hasCall: Boolean) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val running by RecordingProbe.running.collectAsState()
    val step by RecordingProbe.step.collectAsState()
    val results by RecordingProbe.results.collectAsState()
    val recording by RecorderState.active.collectAsState()
    var preferred by remember { mutableStateOf(prefs.preferredSource) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    var playing by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { RecordingProbe.loadSaved(ctx) }
    DisposableEffect(Unit) { onDispose { player[0]?.release(); player[0] = null } }

    fun stop() { player[0]?.release(); player[0] = null; playing = null }
    fun play(path: String) {
        stop()
        try {
            val p = MediaPlayer()
            p.setDataSource(path); p.prepare(); p.start(); p.setOnCompletionListener { stop() }
            player[0] = p; playing = path
        } catch (_: Exception) { stop() }
    }

    Text(
        "1. Start a call. Tap Run; both of you keep talking for about ${RecordingProbe.totalSeconds} seconds.\n" +
            "2. Play each sample: you will hear who it caught.\n" +
            "3. Run it twice: once with the speaker ON, once with the speaker OFF (earpiece). An input that has the other " +
            "person with the speaker OFF is true two-way recording.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    val a11y = remember { mutableStateOf(AccessibilityHelper.isEnabled(ctx)) }
    LaunchedEffect(running) { a11y.value = AccessibilityHelper.isEnabled(ctx) }
    androidx.compose.material3.Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Accessibility helper: " + if (a11y.value) "ON" else "off", style = MaterialTheme.typography.titleSmall)
            Text(
                "Some phones allow more recording inputs when an accessibility service is on. This one does nothing and does not read " +
                    "the screen. Turn it on in Android settings, run the test again, and compare.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                try { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: Exception) {}
            }) { Text("Open accessibility settings") }
        }
    }
    Spacer(Modifier.height(12.dp))
    Button(
        enabled = hasCall && !running && !recording,
        onClick = { RecordingProbe.start(ctx)?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() } },
    ) { Text(if (running) "Testing…" else "Run test") }
    if (!hasCall) Text("No active call. Start a call first.", Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    if (recording) Text("Stop the current recording first.", Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    if (running) {
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(step ?: "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }

    if (results.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text(RecordingProbe.verdict(results), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        results.forEach { r ->
            ResultRow(r, playing == r.file, preferred == r.source.name,
                onPlay = { r.file?.let { f -> if (playing == f) stop() else play(f) } },
                onUse = { prefs.preferredSource = r.source.name; preferred = r.source.name })
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { prefs.preferredSource = "auto"; preferred = "auto" }) { Text(if (preferred == "auto") "Using Auto" else "Back to Auto") }
            TextButton(onClick = { stop(); RecordingProbe.clearSamples(ctx) }) { Text("Clear samples") }
        }
        Text(
            "Recording uses: " + (RecSource.entries.firstOrNull { it.name == preferred }?.label ?: "Auto (first input that captures sound)"),
            Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultRow(r: ProbeResult, playing: Boolean, chosen: Boolean, onPlay: () -> Unit, onUse: () -> Unit) {
    val (label, color) = when (r.status) {
        ProbeStatus.SIGNAL -> "Sound" to MaterialTheme.colorScheme.primary
        ProbeStatus.SILENT -> "Silent" to MaterialTheme.colorScheme.error
        ProbeStatus.BLOCKED -> "Blocked" to MaterialTheme.colorScheme.error
        ProbeStatus.ERROR -> "Error" to MaterialTheme.colorScheme.error
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPlay, enabled = r.file != null) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play sample")
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.source.label, style = MaterialTheme.typography.bodyLarge)
                Text("  $label", color = color, style = MaterialTheme.typography.labelLarge)
            }
            val level = if (r.status == ProbeStatus.SIGNAL || r.status == ProbeStatus.SILENT) "peak ${r.peak}, ${"%.0f".format(r.rmsDb)} dB · " else ""
            Text(level + r.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (r.status == ProbeStatus.SIGNAL) {
            TextButton(onClick = onUse, enabled = !chosen) { Text(if (chosen) "In use" else "Use") }
        }
    }
}
