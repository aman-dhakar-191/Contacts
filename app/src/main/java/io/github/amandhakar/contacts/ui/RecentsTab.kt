@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package io.github.amandhakar.contacts.ui

import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.amandhakar.contacts.AppViewModel
import io.github.amandhakar.contacts.Screen
import io.github.amandhakar.contacts.call.Dialer
import io.github.amandhakar.contacts.data.CallEntry
import io.github.amandhakar.contacts.data.SwipeAction
import io.github.amandhakar.contacts.util.PhoneUtil
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

private var filterState = mutableIntStateOf(0) // 0 all, 1 missed, 2 incoming, 3 outgoing

@Composable
fun HistoryMenu(vm: AppViewModel) {
    var confirm by remember { mutableStateOf(false) }
    IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.DeleteSweep, "Clear call history") }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Clear call history?") },
            text = { Text("This deletes every entry in the call log.") },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); confirm = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun RecentsTab(vm: AppViewModel) {
    val ctx = LocalContext.current
    val history by vm.history.collectAsState()
    val index by vm.numberIndex.collectAsState()
    val tags by vm.identified.collectAsState()
    var filter by filterState

    LaunchedEffect(Unit) { vm.markMissedRead() }

    val shown = remember(history, filter) {
        history.filter {
            when (filter) {
                1 -> it.type == CallLog.Calls.MISSED_TYPE || it.type == CallLog.Calls.REJECTED_TYPE
                2 -> it.type == CallLog.Calls.INCOMING_TYPE
                3 -> it.type == CallLog.Calls.OUTGOING_TYPE
                else -> true
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        val options = listOf("All", "Missed", "Incoming", "Outgoing")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            options.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = filter == i, onClick = { filter = i },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    label = { Text(t, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                    icon = {},
                )
            }
        }
        if (shown.isEmpty()) {
            EmptyState(Icons.Filled.History, "No calls", if (filter == 0) "Your calls will show up here." else "Nothing in this filter.")
            return@Column
        }
        val byDay = remember(shown) { shown.groupBy { dayLabel(it.date) } }
        // Caller-ID names remembered from earlier incoming calls (only when the switch in Settings is on).
        val remembered = remember { if (vm.prefs.rememberCallerNames) io.github.amandhakar.contacts.data.CallerNames(ctx) else null }
        LazyColumn(Modifier.fillMaxSize()) {
            byDay.forEach { (day, list) ->
                stickyHeader(key = "d$day") {
                    Text(
                        day, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 24.dp, top = 8.dp, bottom = 6.dp),
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(list, key = { it.id }) { e ->
                    val contact = index[PhoneUtil.key(e.number)]
                    val name = contact?.name ?: e.cachedName?.takeIf { it.isNotBlank() }
                        ?: remembered?.get(e.number) ?: e.number.ifBlank { "Unknown" }
                    CallRow(vm, e, name, contact?.id, contact?.photoUri, contact?.starred ?: false, tags[e.id])
                }
            }
        }
    }
}

@Composable
private fun CallRow(vm: AppViewModel, e: CallEntry, name: String, contactId: Long?, photo: String?, starred: Boolean, tag: io.github.amandhakar.contacts.identifier.Tag?) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var flagging by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    val missed = e.type == CallLog.Calls.MISSED_TYPE || e.type == CallLog.Calls.REJECTED_TYPE
    val icon = when (e.type) {
        CallLog.Calls.INCOMING_TYPE -> Icons.Filled.CallReceived
        CallLog.Calls.OUTGOING_TYPE -> Icons.Filled.CallMade
        CallLog.Calls.BLOCKED_TYPE -> Icons.Filled.Block
        else -> Icons.Filled.CallMissed
    }
    fun canPerform(action: SwipeAction): Boolean = when (action) {
        SwipeAction.NONE -> false
        SwipeAction.CALL, SwipeAction.MESSAGE, SwipeAction.WHATSAPP -> e.number.isNotBlank()
        SwipeAction.FAVORITE -> contactId != null
        SwipeAction.VIEW_CONTACT -> true
    }
    fun perform(action: SwipeAction) {
        when (action) {
            SwipeAction.CALL -> if (e.number.isNotBlank()) Dialer.call(ctx, e.number)
            SwipeAction.MESSAGE -> if (e.number.isNotBlank()) {
                try { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${e.number}"))) } catch (_: Exception) {}
            }
            SwipeAction.VIEW_CONTACT -> if (contactId != null) vm.push(Screen.Detail(contactId))
                else if (e.number.isNotBlank()) vm.push(Screen.Edit(null, e.number))
            SwipeAction.FAVORITE -> contactId?.let { vm.setStarred(it, !starred) }
            SwipeAction.WHATSAPP -> if (e.number.isNotBlank()) {
                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${PhoneUtil.digits(e.number)}"))) } catch (_: Exception) {}
            }
            SwipeAction.NONE -> Unit
        }
    }
    val leftAction = vm.swipeLeftAction.takeIf { canPerform(it) } ?: SwipeAction.NONE
    val rightAction = vm.swipeRightAction.takeIf { canPerform(it) } ?: SwipeAction.NONE
    SwipeActionRow(
        swipeLeft = leftAction,
        swipeRight = rightAction,
        onSwipeLeft = { perform(leftAction) },
        onSwipeRight = { perform(rightAction) },
    ) {
        Box(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().combinedClickable(
                    onClick = {},   // a tap no longer places a call; use the swipe action or the long-press menu
                    onLongClick = { menu = true },
                ).padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(name, photo, 48.dp)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name, style = MaterialTheme.typography.bodyLarge, color = if (missed) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        )
                        if (tag != null) CategoryChip(tag.name, tag.color, Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, Modifier.padding(end = 4.dp), tint = if (missed) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.date)) + durationText(e),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (contactId != null) {
                    DropdownMenuItem(text = { Text("View contact") }, onClick = { menu = false; vm.push(Screen.Detail(contactId)) })
                } else if (e.number.isNotBlank()) {
                    DropdownMenuItem(text = { Text("Add to contacts") }, onClick = { menu = false; vm.push(Screen.Edit(null, e.number)) })
                }
                if (e.number.isNotBlank()) {
                    DropdownMenuItem(text = { Text("Send message") }, onClick = {
                        menu = false
                        try { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${e.number}"))) } catch (_: Exception) {}
                    })
                    val rules = io.github.amandhakar.contacts.data.RecordRules(ctx)
                    val always = rules.contains(e.number)
                    DropdownMenuItem(text = { Text(if (always) "Stop auto-recording" else "Always record") }, onClick = {
                        menu = false
                        if (always) rules.remove(e.number) else rules.add(e.number, name.takeIf { it != e.number })
                        Toast.makeText(ctx, if (always) "Won't be recorded automatically" else "Calls with this number will be recorded", Toast.LENGTH_SHORT).show()
                    })
                    DropdownMenuItem(text = { Text("Flag as…") }, onClick = { menu = false; flagging = true })
                    DropdownMenuItem(
                        text = { Text(if (tag != null) "This category is wrong…" else "Should be categorised…") },
                        onClick = { menu = false; reporting = true },
                    )
                    DropdownMenuItem(text = { Text("Block number") }, onClick = {
                        menu = false
                        val ok = vm.blockedRepo.block(e.number)
                        Toast.makeText(ctx, if (ok) "Blocked ${e.number}" else "Set as default phone app to block numbers", Toast.LENGTH_SHORT).show()
                    })
                }
                DropdownMenuItem(text = { Text("Delete entry") }, onClick = { menu = false; vm.deleteCall(e.id) })
            }
            if (flagging) FlagDialog(vm, e.number, name.takeIf { it != e.number }) { flagging = false }
            if (reporting) {
                WrongDialog(e.number, name.takeIf { it != e.number }, tag?.name, tag?.via ?: io.github.amandhakar.contacts.identifier.Via.NONE, tag?.ruleId, tag?.ruleName,
                    onChanged = { vm.reload() }, onDismiss = { reporting = false })
            }
        }
    }
}

private fun durationText(e: CallEntry): String {
    if (e.durationSec <= 0) return ""
    val m = e.durationSec / 60
    val s = e.durationSec % 60
    return " · " + if (m > 0) "${m}m ${s}s" else "${s}s"
}

private fun dayLabel(t: Long): String {
    val now = Calendar.getInstance()
    val d = Calendar.getInstance().apply { timeInMillis = t }
    fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (same(now, d)) return "Today"
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (same(now, d)) return "Yesterday"
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t))
}
