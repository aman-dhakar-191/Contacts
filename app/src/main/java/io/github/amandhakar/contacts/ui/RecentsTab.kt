@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package io.github.amandhakar.contacts.ui

import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Missed", "Incoming", "Outgoing").forEachIndexed { i, t ->
                FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(t) })
            }
        }
        if (shown.isEmpty()) {
            Text("No calls.", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        val byDay = remember(shown) { shown.groupBy { dayLabel(it.date) } }
        LazyColumn(Modifier.fillMaxSize()) {
            byDay.forEach { (day, list) ->
                stickyHeader(key = "d$day") {
                    Text(
                        day, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(list, key = { it.id }) { e ->
                    val contact = index[PhoneUtil.key(e.number)]
                    val name = contact?.name ?: e.cachedName?.takeIf { it.isNotBlank() } ?: e.number.ifBlank { "Unknown" }
                    CallRow(vm, e, name, contact?.id, contact?.photoUri, onCall = { Dialer.call(ctx, e.number) })
                }
            }
        }
    }
}

@Composable
private fun CallRow(vm: AppViewModel, e: CallEntry, name: String, contactId: Long?, photo: String?, onCall: () -> Unit) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val missed = e.type == CallLog.Calls.MISSED_TYPE || e.type == CallLog.Calls.REJECTED_TYPE
    val icon = when (e.type) {
        CallLog.Calls.INCOMING_TYPE -> Icons.Filled.CallReceived
        CallLog.Calls.OUTGOING_TYPE -> Icons.Filled.CallMade
        CallLog.Calls.BLOCKED_TYPE -> Icons.Filled.Block
        else -> Icons.Filled.CallMissed
    }
    Row(Modifier.fillMaxWidth().clickable(enabled = e.number.isNotBlank(), onClick = onCall).padding(start = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(name, photo)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = if (missed) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.padding(end = 4.dp), tint = if (missed) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.date)) + durationText(e),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
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
                DropdownMenuItem(text = { Text("Block number") }, onClick = {
                    menu = false
                    val ok = vm.blockedRepo.block(e.number)
                    Toast.makeText(ctx, if (ok) "Blocked ${e.number}" else "Set as default phone app to block numbers", Toast.LENGTH_SHORT).show()
                })
            }
            DropdownMenuItem(text = { Text("Delete entry") }, onClick = { menu = false; vm.deleteCall(e.id) })
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
