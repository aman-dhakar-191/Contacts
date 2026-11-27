package io.github.amandhakar.contacts.ui

import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.amandhakar.contacts.AppViewModel
import io.github.amandhakar.contacts.Screen
import io.github.amandhakar.contacts.call.Dialer
import io.github.amandhakar.contacts.util.PhoneUtil
import io.github.amandhakar.contacts.util.SwipeGestures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil

/**
 * What a tap on a Recents entry opens when the number is not a saved contact:
 * every call with that number, its recordings, and the things you can do with it.
 * Nothing here dials unless the Call button is tapped.
 */
@Composable
fun CallDetailsScreen(vm: AppViewModel, number: String) {
    val ctx = LocalContext.current
    val history by vm.history.collectAsState()
    val index by vm.numberIndex.collectAsState()
    val tags by vm.identified.collectAsState()
    val key = PhoneUtil.key(number)
    val calls = remember(history, key) { history.filter { PhoneUtil.key(it.number) == key } }
    val contact = index[key]
    val name = contact?.name ?: calls.firstNotNullOfOrNull { it.cachedName?.takeIf { n -> n.isNotBlank() } }
    val tag = calls.firstNotNullOfOrNull { tags[it.id] }
    val recordings by produceState(0, key) {
        value = withContext(Dispatchers.IO) {
            try { vm.recordingsRepo.list().count { PhoneUtil.key(it.number) == key } } catch (_: Exception) { 0 }
        }
    }

    Page("Call details", vm) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(name ?: number, contact?.photoUri, 96.dp)
            Spacer(Modifier.height(12.dp))
            Text(name ?: number.ifBlank { "Unknown" }, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp))
            if (name != null && number.isNotBlank()) {
                Text(number, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (tag != null) CategoryChip(tag.name, tag.color, Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(20.dp))

            val hasNumber = number.isNotBlank()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                QuickAction(Icons.Filled.Call, "Call", enabled = hasNumber, filled = true) { Dialer.call(ctx, number) }
                QuickAction(Icons.AutoMirrored.Filled.Message, "Message", enabled = hasNumber) {
                    try { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))) } catch (_: Exception) {}
                }
                if (contact != null) {
                    QuickAction(Icons.Filled.Person, "Contact") { vm.push(Screen.Detail(contact.id)) }
                } else {
                    QuickAction(Icons.Filled.PersonAdd, "Add", enabled = hasNumber) { vm.push(Screen.Edit(null, number)) }
                }
                QuickAction(Icons.Filled.Block, "Block", enabled = hasNumber) {
                    val ok = vm.blockedRepo.block(number)
                    Toast.makeText(ctx, if (ok) "Blocked $number" else "Set as default phone app to block numbers", Toast.LENGTH_SHORT).show()
                }
            }

            if (recordings > 0) {
                GroupCard(Modifier.padding(top = 18.dp)) {
                    InfoRow(
                        Icons.Filled.Mic, "Recordings",
                        if (recordings == 1) "1 recording with this number" else "$recordings recordings with this number",
                        onClick = { vm.push(Screen.Recordings) },
                    )
                }
            }

            SectionLabel("Calls")
            GroupCard {
                if (calls.isEmpty()) InfoRow(null, "No calls with this number")
                calls.forEach { c ->
                    val missed = c.type == CallLog.Calls.MISSED_TYPE || c.type == CallLog.Calls.REJECTED_TYPE
                    val (icon, kind) = when (c.type) {
                        CallLog.Calls.INCOMING_TYPE -> Icons.Filled.CallReceived to "Incoming"
                        CallLog.Calls.OUTGOING_TYPE -> Icons.Filled.CallMade to "Outgoing"
                        CallLog.Calls.MISSED_TYPE -> Icons.Filled.CallMissed to "Missed"
                        CallLog.Calls.REJECTED_TYPE -> Icons.Filled.CallMissed to "Declined"
                        CallLog.Calls.BLOCKED_TYPE -> Icons.Filled.Block to "Blocked"
                        else -> Icons.Filled.Call to "Call"
                    }
                    val tint = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, Modifier.padding(end = 16.dp), tint = tint)
                        Column(Modifier.weight(1f)) {
                            Text(kind, color = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                            if (c.durationSec > 0) {
                                Text(callLength(c.durationSec), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(c.date)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun callLength(sec: Long): String {
    val m = sec / 60
    val s = sec % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, enabled: Boolean = true, filled: Boolean = false, onClick: () -> Unit) {
    val bg = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(width = 64.dp, height = 48.dp).clip(CircleShape).background(if (enabled) bg else bg.copy(alpha = 0.4f))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = fg) }
        Text(label, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Shown while a call started by a swipe waits out its undo window. Cancel (or back) stops it,
 * Call now skips the wait. The bar counts down and fills a line so the time left is obvious.
 */
@Composable
fun PendingCallBar(vm: AppViewModel, pending: AppViewModel.PendingCall, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val total = SwipeGestures.CALL_UNDO_MS
    val remaining = remember(pending) { Animatable(1f) }
    LaunchedEffect(pending) {
        val elapsed = (System.currentTimeMillis() - pending.startedAt).coerceIn(0L, total)
        remaining.snapTo(1f - elapsed.toFloat() / total)
        remaining.animateTo(0f, tween((total - elapsed).toInt().coerceAtLeast(1), easing = LinearEasing))
    }
    val seconds = ceil(remaining.value * total / 1000f).toInt().coerceAtLeast(1)
    val accent = MaterialTheme.colorScheme.inversePrimary
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
    ) {
        Column {
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Call, null, Modifier.size(20.dp), tint = accent)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(pending.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Calling in $seconds s", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { vm.cancelPendingCall() }, colors = ButtonDefaults.textButtonColors(contentColor = accent)) { Text("Cancel") }
                TextButton(onClick = { vm.callPendingNow(ctx) }, colors = ButtonDefaults.textButtonColors(contentColor = accent)) { Text("Call now") }
            }
            LinearProgressIndicator(
                progress = { remaining.value },
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp).height(3.dp),
                color = accent,
                trackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.2f),
            )
        }
    }
}
