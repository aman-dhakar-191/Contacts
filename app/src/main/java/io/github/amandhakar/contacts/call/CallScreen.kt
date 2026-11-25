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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
    var reportWrong by remember { mutableStateOf(false) }
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
            if (shown.nameFromCallerId) {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    io.github.amandhakar.contacts.ui.LikelyChip()
                    Text("  name from caller ID, not in your contacts", color = Color(0xFF8A939B), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(statusText(shown, primary == null, now), color = Color(0xFFB0B8C0), fontSize = 18.sp)
            // Where the audio is going, when it is not the phone's own earpiece (Bluetooth, wired headset, speaker).
            if (primary != null && primary.state != Call.STATE_RINGING && audio?.route != null && audio?.route != CallAudioState.ROUTE_EARPIECE) {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(routeIcon(audio?.route), null, tint = Color(0xFF7DFFA0), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(AudioRoutes.currentLabel(audio), color = Color(0xFF7DFFA0), fontSize = 14.sp)
                }
            }
            shown.tag?.let { t ->
                Spacer(Modifier.height(10.dp))
                io.github.amandhakar.contacts.ui.CategoryBadge(t.name, t.color, Modifier.clickable { reportWrong = true })
                Text(
                    (if (t.via == io.github.amandhakar.contacts.identifier.Via.MANUAL) "flagged by you" else "rule: ${t.ruleName ?: ""}") + " · tap if wrong",
                    color = Color(0xFF8A939B), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
                )
                if (reportWrong) {
                    io.github.amandhakar.contacts.ui.WrongDialog(
                        shown.number, shown.name, t.name, t.via, t.ruleId, t.ruleName, onChanged = {}, onDismiss = { reportWrong = false },
                    )
                }
            }
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
                Box(Modifier.fillMaxWidth().padding(bottom = 32.dp), contentAlignment = Alignment.Center) {
                    AnswerSwipeButton(onAnswer = { CallHub.answer(primary.call) }, onDecline = { CallHub.reject(primary.call) })
                }
            } else if (primary != null) {
                val muted = audio?.isMuted == true
                val routes = AudioRoutes.available(audio)
                var routeMenu by remember { mutableStateOf(false) }
                val onPhone = audio?.route == CallAudioState.ROUTE_EARPIECE || audio?.route == null
                val held = primary.state == Call.STATE_HOLDING
                // Both rows use three equal columns (an empty cell where a button is missing) so the buttons line up.
                Row(Modifier.fillMaxWidth()) {
                    Cell { ToggleButton(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, "Mute", muted) { CallHub.setMuted(!muted) } }
                    Cell { ToggleButton(Icons.Filled.Dialpad, "Keypad", false) { showKeypad = true } }
                    Cell {
                        Box {
                            // One audio button: with only phone + speaker it toggles; with a headset or Bluetooth it opens a list.
                            ToggleButton(
                                routeIcon(audio?.route),
                                when (audio?.route) {
                                    CallAudioState.ROUTE_BLUETOOTH -> "Bluetooth"
                                    CallAudioState.ROUTE_WIRED_HEADSET -> "Headset"
                                    CallAudioState.ROUTE_SPEAKER -> "Speaker"
                                    else -> "Audio"
                                },
                                !onPhone,
                            ) {
                                if (routes.size <= 2) {
                                    CallHub.setSpeaker(audio?.route != CallAudioState.ROUTE_SPEAKER)
                                } else routeMenu = true
                            }
                            androidx.compose.material3.DropdownMenu(expanded = routeMenu, onDismissRequest = { routeMenu = false }) {
                                routes.forEach { r ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(r.label, fontWeight = if (r.route == audio?.route) androidx.compose.ui.text.font.FontWeight.Bold else null) },
                                        leadingIcon = { Icon(routeIcon(r.route), null) },
                                        onClick = { routeMenu = false; CallHub.setRoute(r.route) },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth()) {
                    Cell { ToggleButton(Icons.Filled.Pause, "Hold", held) { if (held) CallHub.unhold(primary.call) else CallHub.hold(primary.call) } }
                    Cell {
                        ToggleButton(Icons.Filled.FiberManualRecord, "Record", recording) {
                            if (recording) RecorderService.stop(ctx) else { RecorderState.reason.value = null; RecorderService.start(ctx, primary.number, primary.incoming) }
                        }
                    }
                    Cell {
                        if (other != null) {
                            ToggleButton(Icons.Filled.SwapCalls, "Swap", false) {
                                if (other.state == Call.STATE_HOLDING) CallHub.unhold(other.call) else CallHub.hold(primary.call)
                            }
                        }
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
        InCallKeypad(primary.call) { showKeypad = false }
    }
}

/** In-call keypad: the dialer's own display, match cards and keys. Pressed keys are sent as tones. */
@Composable
private fun InCallKeypad(call: Call, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var typed by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf<List<io.github.amandhakar.contacts.data.ContactSummary>>(emptyList()) }
    var phones by remember { mutableStateOf<Map<Long, List<io.github.amandhakar.contacts.data.PhoneEntry>>>(emptyMap()) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val repo = io.github.amandhakar.contacts.data.ContactsRepo(ctx)
            val c = repo.contacts(); val p = repo.allPhones()
            contacts = c; phones = p
        }
    }
    val hits = remember(typed, contacts, phones) { io.github.amandhakar.contacts.ui.dialHits(typed, contacts, phones, 3) }
    val clip = io.github.amandhakar.contacts.ui.rememberClipboardNumber()

    suspend fun tone(ch: Char) {
        call.playDtmfTone(ch); delay(150); call.stopDtmfTone(); delay(90)
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.94f),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 14.dp)) {
                // Matching contacts, in the same cards as the dialer (room for three so the keys do not jump).
                Column(
                    Modifier.padding(horizontal = 12.dp).height(180.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                ) {
                    hits.forEach { (c, p) -> io.github.amandhakar.contacts.ui.DialMatchRow(c, p, onClick = null) }
                }
                Box(Modifier.height(64.dp), contentAlignment = Alignment.Center) {
                    io.github.amandhakar.contacts.ui.DialNumberDisplay(
                        typed, "Tap keys to send tones", clip,
                        // A pasted number is shown and sent as tones, one digit at a time (e.g. a meeting PIN).
                        onPaste = { pasted ->
                            typed += pasted
                            scope.launch { pasted.forEach { if (it in "0123456789*#") tone(it) } }
                        },
                    )
                }
                io.github.amandhakar.contacts.ui.DialKeyGrid(keySize = 68.dp, spacing = 18.dp, onKey = { d ->
                    typed += d
                    scope.launch { tone(d[0]) }
                })
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                    if (typed.isNotEmpty()) TextButton(onClick = { typed = "" }) { Text("Clear") }
                    TextButton(onClick = onClose) { Text("Close") }
                }
            }
        }
    }
}

private fun routeIcon(route: Int?): ImageVector = when (route) {
    CallAudioState.ROUTE_SPEAKER -> Icons.Filled.VolumeUp
    CallAudioState.ROUTE_BLUETOOTH -> Icons.Filled.Bluetooth
    CallAudioState.ROUTE_WIRED_HEADSET -> Icons.Filled.Headset
    else -> Icons.Filled.PhoneInTalk
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

/** One equal-width column of a control row. */
@Composable
private fun RowScope.Cell(content: @Composable () -> Unit) {
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { content() }
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

/**
 * The one call button for a ringing call: drag it up to answer, down to decline. Pulsing arrows above and
 * below say it can be swiped. Past the threshold it ticks, changes colour and says what will happen on release.
 */
@Composable
private fun AnswerSwipeButton(onAnswer: () -> Unit, onDecline: () -> Unit) {
    val size = 80.dp
    val maxPx = with(LocalDensity.current) { 96.dp.toPx() }
    val thresholdPx = maxPx * 0.6f
    var dy by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val answer by rememberUpdatedState(onAnswer)
    val decline by rememberUpdatedState(onDecline)
    val goingUp = dy <= -thresholdPx
    val goingDown = dy >= thresholdPx
    LaunchedEffect(goingUp, goingDown) { if (goingUp || goingDown) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "hint").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(tween(900), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "hintAlpha",
    )
    val hintAlpha = if (dragging) 0.25f else pulse
    val green = Color(0xFF2E7D32)
    val red = Color(0xFFD32F2F)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.KeyboardArrowUp, null, tint = Color(0xFF7DFFA0).copy(alpha = hintAlpha), modifier = Modifier.size(32.dp))
        Text(if (goingUp) "Release to answer" else "Swipe up to answer", color = Color(0xFF7DFFA0).copy(alpha = if (goingDown) 0.3f else 1f), fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.offset { IntOffset(0, dy.roundToInt()) }
                .size(size).clip(CircleShape)
                .background(when { goingDown -> red; goingUp -> Color(0xFF43A047); else -> green })
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            val end = dy
                            dragging = false
                            scope.launch { animate(end, 0f, animationSpec = tween(160)) { v, _ -> dy = v } }
                            if (end <= -thresholdPx) answer() else if (end >= thresholdPx) decline()
                        },
                        onDragCancel = {
                            dragging = false
                            scope.launch { animate(dy, 0f, animationSpec = tween(160)) { v, _ -> dy = v } }
                        },
                        onVerticalDrag = { change, amount -> change.consume(); dy = (dy + amount).coerceIn(-maxPx, maxPx) },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (goingDown) Icons.Filled.CallEnd else Icons.Filled.Call,
                if (goingDown) "Decline" else "Answer", tint = Color.White,
                modifier = Modifier.size(if (goingUp || goingDown) 44.dp else 36.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(if (goingDown) "Release to decline" else "Swipe down to decline", color = Color(0xFFFF8A80).copy(alpha = if (goingUp) 0.3f else 1f), fontSize = 14.sp)
        Icon(Icons.Filled.KeyboardArrowDown, null, tint = Color(0xFFFF8A80).copy(alpha = hintAlpha), modifier = Modifier.size(32.dp))
    }
}
