package io.github.amandhakar.contacts.call

import android.telecom.Call
import android.telecom.CallAudioState
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.amandhakar.contacts.ui.Avatar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CallScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val calls by CallHub.calls.collectAsState()
    val audio by CallHub.audio.collectAsState()
    val recording by RecorderState.active.collectAsState()
    val bothSides by RecorderState.bothSides.collectAsState()
    val recError by RecorderState.error.collectAsState()
    val recWarning by RecorderState.warning.collectAsState()
    val recLabel by RecorderState.sourceLabel.collectAsState()
    val recReason by RecorderState.reason.collectAsState()
    var showTest by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var last by remember { mutableStateOf<CallInfo?>(null) }
    val primary = calls.firstOrNull { it.state == Call.STATE_RINGING }
        ?: calls.firstOrNull { it.state != Call.STATE_HOLDING && it.state != Call.STATE_DISCONNECTED }
        ?: calls.firstOrNull()
    if (primary != null) last = primary
    val shown = primary ?: last
    val other = calls.firstOrNull { it != primary }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showKeypad by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { while (true) { delay(500); now = System.currentTimeMillis() } }
    LaunchedEffect(calls.isEmpty()) {
        if (calls.isEmpty()) { delay(if (last == null) 100 else 1200); onClose() }
    }
    LaunchedEffect(recWarning) {
        recWarning?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show(); RecorderState.warning.value = null }
    }
    LaunchedEffect(recError) {
        recError?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show(); RecorderState.error.value = null }
    }

    val bg = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF16261F), Color(0xFF0B100E)))
    Box(Modifier.fillMaxSize().background(bg).safeDrawingPadding()) {
        if (shown == null) return@Box
        androidx.compose.material3.TextButton(onClick = { showTest = true }, modifier = Modifier.align(Alignment.TopEnd)) {
            Text("Rec test", color = Color(0xFFB0B8C0))
        }
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(40.dp))
            Avatar(shown.name ?: shown.number, shown.photoUri, 112.dp)
            Spacer(Modifier.height(20.dp))
            Text(shown.name ?: shown.number.ifBlank { "Unknown" }, color = Color.White, fontSize = 30.sp, textAlign = TextAlign.Center)
            if (shown.name != null && shown.number.isNotBlank()) Text(shown.number, color = Color(0xFFB0B8C0), fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text(statusText(shown, primary == null, now), color = Color(0xFFB0B8C0), fontSize = 18.sp)
            if (recording) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FiberManualRecord, null, tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(
                        (if (bothSides) "Recording · $recLabel" else "Recording · $recLabel (your side + speaker)") +
                            (recReason?.let { " · auto: $it" } ?: ""),
                        color = Color(0xFFFF8A80), fontSize = 14.sp,
                    )
                }
            }
            if (other != null && primary != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "${other.name ?: other.number} is " + if (other.state == Call.STATE_HOLDING) "on hold" else "calling",
                    color = Color(0xFFFFCC80), fontSize = 14.sp,
                )
            }
            Spacer(Modifier.weight(1f))

            if (primary != null && primary.state == Call.STATE_RINGING) {
                Row(Modifier.fillMaxWidth().padding(bottom = 48.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundButton(Icons.Filled.CallEnd, "Decline", Color(0xFFD32F2F), size = 72.dp) { CallHub.reject(primary.call) }
                    RoundButton(Icons.Filled.Call, "Answer", Color(0xFF2E7D32), size = 72.dp) { CallHub.answer(primary.call) }
                }
            } else if (primary != null) {
                val muted = audio?.isMuted == true
                val speaker = audio?.route == CallAudioState.ROUTE_SPEAKER
                val held = primary.state == Call.STATE_HOLDING
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ToggleButton(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, "Mute", muted) { CallHub.setMuted(!muted) }
                    ToggleButton(Icons.Filled.Dialpad, "Keypad", false) { showKeypad = true }
                    ToggleButton(Icons.Filled.VolumeUp, "Speaker", speaker) { CallHub.setSpeaker(!speaker) }
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ToggleButton(Icons.Filled.Pause, "Hold", held) { if (held) CallHub.unhold(primary.call) else CallHub.hold(primary.call) }
                    ToggleButton(Icons.Filled.FiberManualRecord, "Record", recording) {
                        if (recording) RecorderService.stop(ctx) else { RecorderState.reason.value = null; RecorderService.start(ctx, primary.number, primary.incoming) }
                    }
                    if (other != null) {
                        ToggleButton(Icons.Filled.SwapCalls, "Swap", false) {
                            if (other.state == Call.STATE_HOLDING) CallHub.unhold(other.call) else CallHub.hold(primary.call)
                        }
                    } else {
                        Spacer(Modifier.size(64.dp))
                    }
                }
                Spacer(Modifier.height(32.dp))
                RoundButton(Icons.Filled.CallEnd, "End call", Color(0xFFD32F2F), size = 72.dp) { CallHub.hangUp(primary.call) }
                Spacer(Modifier.height(32.dp))
            } else {
                Spacer(Modifier.height(100.dp))
            }
        }
    }

    if (showTest) {
        AlertDialog(
            onDismissRequest = { showTest = false },
            confirmButton = { TextButton(onClick = { showTest = false }) { Text("Close") } },
            title = { Text("Recording test") },
            text = {
                androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) {
                    io.github.amandhakar.contacts.ui.ProbeControls(hasCall = calls.isNotEmpty())
                }
            },
        )
    }

    if (showKeypad && primary != null) {
        AlertDialog(
            onDismissRequest = { showKeypad = false },
            confirmButton = { TextButton(onClick = { showKeypad = false }) { Text("Close") } },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    for (row in listOf("123", "456", "789", "*0#")) {
                        Row {
                            for (ch in row) {
                                Box(
                                    Modifier.padding(6.dp).size(60.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .clickable {
                                            primary.call.playDtmfTone(ch)
                                            scope.launch { delay(150); primary.call.stopDtmfTone() }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) { Text(ch.toString(), fontSize = 24.sp) }
                            }
                        }
                    }
                }
            },
        )
    }
}

private fun statusText(info: CallInfo, ended: Boolean, now: Long): String {
    if (ended) return "Call ended"
    return when (info.state) {
        Call.STATE_RINGING -> "Incoming call"
        Call.STATE_ACTIVE -> if (info.connectTimeMillis > 0) {
            val s = ((now - info.connectTimeMillis) / 1000).coerceAtLeast(0)
            "%02d:%02d".format(s / 60, s % 60)
        } else "Connected"
        Call.STATE_HOLDING -> "On hold"
        Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> "Call ended"
        Call.STATE_CONNECTING -> "Connecting…"
        else -> "Calling…"
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, color: Color, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size).clip(CircleShape).background(color).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(size * 0.45f))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color(0xFFB0B8C0), fontSize = 13.sp)
    }
}

@Composable
private fun ToggleButton(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.size(width = 84.dp, height = 90.dp)) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(if (on) Color.White else Color(0xFF26352E)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = if (on) Color.Black else Color.White, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color(0xFFB0B8C0), fontSize = 12.sp)
    }
}
