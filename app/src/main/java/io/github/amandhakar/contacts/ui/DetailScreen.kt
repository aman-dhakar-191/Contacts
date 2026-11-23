@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.amandhakar.contacts.ui

import android.content.Intent
import android.net.Uri
import android.provider.CallLog
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.amandhakar.contacts.AppViewModel
import io.github.amandhakar.contacts.Screen
import io.github.amandhakar.contacts.call.Dialer
import io.github.amandhakar.contacts.data.ContactDetail
import io.github.amandhakar.contacts.util.PhoneUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun DetailScreen(vm: AppViewModel, id: Long) {
    val ctx = LocalContext.current
    val contacts by vm.contacts.collectAsState() // reload trigger
    val history by vm.history.collectAsState()
    val groups by vm.groups.collectAsState()
    val detail by produceState<ContactDetail?>(null, id, contacts) {
        value = withContext(Dispatchers.IO) { vm.contactsRepo.detail(id) }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var topMenu by remember { mutableStateOf(false) }
    val d = detail

    fun sms(number: String) { try { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))) } catch (_: Exception) {} }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (d != null) {
                        IconButton(onClick = { topMenu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = topMenu, onDismissRequest = { topMenu = false }) {
                            DropdownMenuItem(text = { Text("Edit") }, onClick = { topMenu = false; vm.push(Screen.Edit(d.id)) })
                            DropdownMenuItem(text = { Text("Delete contact") }, onClick = { topMenu = false; confirmDelete = true })
                        }
                    }
                },
            )
        },
    ) { pad ->
        if (d == null) { Text("Loading…", Modifier.padding(pad).padding(24.dp)); return@Scaffold }
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(d.name, d.photoUri, 120.dp)
            Spacer(Modifier.height(14.dp))
            Text(d.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 24.dp))
            d.account?.name?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(20.dp))

            val first = d.phones.firstOrNull()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ActionButton(Icons.Filled.Call, "Call", enabled = first != null, filled = true) { first?.let { Dialer.call(ctx, it.number) } }
                ActionButton(Icons.AutoMirrored.Filled.Message, "Message", enabled = first != null) { first?.let { sms(it.number) } }
                ActionButton(if (d.starred) Icons.Filled.Star else Icons.Filled.StarBorder, if (d.starred) "Favorite" else "Favorite") { vm.setStarred(d.id, !d.starred) }
                ActionButton(Icons.Filled.Edit, "Edit") { vm.push(Screen.Edit(d.id)) }
            }

            if (d.phones.isNotEmpty()) {
                SectionLabel("Phone")
                GroupCard {
                    d.phones.forEach { p ->
                        var menu by remember { mutableStateOf(false) }
                        var flagging by remember { mutableStateOf(false) }
                        InfoRow(
                            Icons.Filled.Call, p.number, p.label, onClick = { Dialer.call(ctx, p.number) },
                            trailing = {
                                if (flagging) FlagDialog(vm, p.number, d.name) { flagging = false }
                                IconButton(onClick = { sms(p.number) }) { Icon(Icons.AutoMirrored.Filled.Message, "Message") }
                                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    val rules = io.github.amandhakar.contacts.data.RecordRules(ctx)
                                    val always = rules.contains(p.number)
                                    DropdownMenuItem(
                                        text = { Text(if (always) "Stop auto-recording this number" else "Always record this number") },
                                        onClick = {
                                            menu = false
                                            if (always) rules.remove(p.number) else rules.add(p.number, d.name)
                                            Toast.makeText(ctx, if (always) "Won't be recorded automatically" else "Calls with this number will be recorded", Toast.LENGTH_SHORT).show()
                                        },
                                    )
                                    DropdownMenuItem(text = { Text("Flag as…") }, onClick = { menu = false; flagging = true })
                                    DropdownMenuItem(text = { Text("WhatsApp") }, onClick = {
                                        menu = false
                                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${PhoneUtil.digits(p.number)}"))) } catch (_: Exception) {}
                                    })
                                    for (slot in 1..9) {
                                        DropdownMenuItem(text = { Text("Set as speed dial $slot") }, onClick = {
                                            menu = false; vm.setSpeedDial(slot, d.name, p.number)
                                            Toast.makeText(ctx, "Speed dial $slot set. Long-press $slot on the keypad.", Toast.LENGTH_SHORT).show()
                                        })
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Block number") }, leadingIcon = { Icon(Icons.Filled.Block, null) },
                                        onClick = {
                                            menu = false
                                            val ok = vm.blockedRepo.block(p.number)
                                            Toast.makeText(ctx, if (ok) "Blocked" else "Set as default phone app to block numbers", Toast.LENGTH_SHORT).show()
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
            }
            if (d.emails.isNotEmpty()) {
                SectionLabel("Email")
                GroupCard {
                    d.emails.forEach { e ->
                        InfoRow(Icons.Filled.Email, e, onClick = { try { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$e"))) } catch (_: Exception) {} })
                    }
                }
            }
            val labelNames = groups.filter { it.id in d.groupIds && it.title != "My Contacts" }.map { it.title }.distinct()
            if (labelNames.isNotEmpty()) {
                SectionLabel("Labels")
                GroupCard { InfoRow(Icons.Filled.Label, labelNames.joinToString(", ")) }
            }

            val keys = d.phones.map { PhoneUtil.key(it.number) }.toSet()
            val calls = history.filter { PhoneUtil.key(it.number) in keys }.take(15)
            SectionLabel("Recent calls")
            GroupCard {
                if (calls.isEmpty()) InfoRow(null, "No calls with this contact yet")
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
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, Modifier.padding(end = 16.dp), tint = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        Text(kind, Modifier.weight(1f), color = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(c.date)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            OutlinedButton(onClick = { vm.pop(); vm.tab = 1 }, Modifier.padding(16.dp)) { Text("Open full call history") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete && d != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${d.name}?") },
            text = { Text("This removes the contact from this phone and from synced accounts.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteContact(d.lookupKey); vm.pop() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, enabled: Boolean = true, filled: Boolean = false, onClick: () -> Unit) {
    val bg = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(width = 72.dp, height = 52.dp).clip(CircleShape).background(if (enabled) bg else bg.copy(alpha = 0.4f))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = fg) }
        Text(label, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
