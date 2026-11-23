@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.amandhakar.contacts.ui

import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val detail by produceState<ContactDetail?>(null, id, contacts) {
        value = withContext(Dispatchers.IO) { vm.contactsRepo.detail(id) }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val d = detail

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (d != null) {
                        IconButton(onClick = { vm.setStarred(d.id, !d.starred) }) {
                            Icon(if (d.starred) Icons.Filled.Star else Icons.Filled.StarBorder, "Favorite")
                        }
                        IconButton(onClick = { vm.push(Screen.Edit(d.id)) }) { Icon(Icons.Filled.Edit, "Edit") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                    }
                },
            )
        },
    ) { pad ->
        if (d == null) { Text("Loading…", Modifier.padding(pad).padding(24.dp)); return@Scaffold }
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(d.name, d.photoUri, 96.dp)
                Spacer(Modifier.height(12.dp))
                Text(d.name, style = MaterialTheme.typography.headlineSmall)
                d.account?.let { if (it.name != null) Text(it.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            d.phones.forEach { p ->
                var menu by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().clickable { Dialer.call(ctx, p.number) }.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Call, null)
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text(p.number, style = MaterialTheme.typography.bodyLarge)
                        Text(p.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { try { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${p.number}"))) } catch (_: Exception) {} }) {
                        Icon(Icons.AutoMirrored.Filled.Message, "Message")
                    }
                    TextButton(onClick = { menu = true }) { Text("More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("WhatsApp") }, onClick = {
                            menu = false
                            val digits = PhoneUtil.digits(p.number)
                            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits"))) } catch (_: Exception) {}
                        })
                        for (slot in 1..9) {
                            DropdownMenuItem(text = { Text("Set as speed dial $slot") }, onClick = {
                                menu = false; vm.setSpeedDial(slot, d.name, p.number)
                                Toast.makeText(ctx, "Speed dial $slot set (long-press $slot on the keypad)", Toast.LENGTH_SHORT).show()
                            })
                        }
                        DropdownMenuItem(text = { Text("Block number") }, onClick = {
                            menu = false
                            val ok = vm.blockedRepo.block(p.number)
                            Toast.makeText(ctx, if (ok) "Blocked" else "Set as default phone app to block numbers", Toast.LENGTH_SHORT).show()
                        })
                    }
                }
            }
            d.emails.forEach { e ->
                Row(Modifier.fillMaxWidth().clickable { try { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$e"))) } catch (_: Exception) {} }.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Email, null)
                    Text(e, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
            val groupNames = vm.groups.collectAsState().value.filter { it.id in d.groupIds }.map { it.title }
            if (groupNames.isNotEmpty()) {
                Text("Labels: " + groupNames.joinToString(", "), Modifier.padding(16.dp, 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val keys = d.phones.map { PhoneUtil.key(it.number) }.toSet()
            val calls = history.filter { PhoneUtil.key(it.number) in keys }.take(15)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Recent calls", Modifier.padding(16.dp, 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (calls.isEmpty()) Text("No calls with this contact.", Modifier.padding(16.dp, 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            calls.forEach { c ->
                val kind = when (c.type) {
                    CallLog.Calls.INCOMING_TYPE -> "Incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                    CallLog.Calls.MISSED_TYPE -> "Missed"
                    CallLog.Calls.REJECTED_TYPE -> "Declined"
                    CallLog.Calls.BLOCKED_TYPE -> "Blocked"
                    else -> "Call"
                }
                Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(kind)
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(c.date)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedButton(onClick = { vm.pop(); vm.tab = 1 }, Modifier.padding(16.dp)) { Text("Open full call history") }
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
