@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.amandhakar.contacts.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.amandhakar.contacts.AppViewModel
import io.github.amandhakar.contacts.data.RecordRules
import io.github.amandhakar.contacts.util.PhoneUtil

/** Numbers that are always recorded. Add a typed number, or pick contacts (all their numbers are added). */
@Composable
fun RecordListScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val rules = remember { RecordRules(ctx) }
    var version by remember { mutableIntStateOf(0) }
    val entries = remember(version) { rules.entries() }
    var number by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }

    Page("Auto-record list", vm) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "Calls to or from these numbers are recorded as soon as they connect. Many places require everyone on the call " +
                        "to agree to being recorded: make sure they know.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        number, { number = it }, label = { Text("Phone number") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = PhoneUtil.key(number).length >= RecordRules.MIN_DIGITS,
                        onClick = {
                            val name = vm.numberIndex.value[PhoneUtil.key(number)]?.name
                            if (!rules.add(number, name)) Toast.makeText(ctx, "Already on the list", Toast.LENGTH_SHORT).show()
                            number = ""; version++
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text("Add") }
                }
                OutlinedButton(onClick = { picking = true }, Modifier.padding(top = 8.dp)) { Text("Add from contacts") }
            }
            if (entries.isEmpty()) {
                EmptyState(Icons.Filled.Mic, "Nothing on the list", "Add a number or pick contacts to record their calls automatically.")
            }
            LazyColumn(Modifier.padding(top = 8.dp)) {
                items(entries, key = { it.key }) { e ->
                    val name = vm.numberIndex.value[e.key]?.name ?: e.label.ifBlank { null }
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name ?: e.number, style = MaterialTheme.typography.bodyLarge)
                            if (name != null) Text(e.number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { rules.remove(e.number); version++ }) { Icon(Icons.Filled.Close, "Remove") }
                    }
                }
            }
        }
    }

    if (picking) {
        val contacts by vm.contacts.collectAsState()
        val phones by vm.phones.collectAsState()
        var query by remember { mutableStateOf("") }
        val shown = remember(contacts, query) {
            contacts.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }.take(100)
        }
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = { TextButton(onClick = { picking = false }) { Text("Done") } },
            title = { Text("Add contacts") },
            text = {
                Column {
                    OutlinedTextField(query, { query = it }, placeholder = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Column(Modifier.height(320.dp).verticalScroll(rememberScrollState())) {
                        shown.forEach { c ->
                            val nums = phones[c.id].orEmpty()
                            val added = nums.isNotEmpty() && nums.all { rules.contains(it.number) }
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = nums.isNotEmpty()) {
                                    nums.forEach { rules.add(it.number, c.name) }; version++
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(c.name, c.photoUri, 36.dp)
                                Text(c.name, Modifier.weight(1f).padding(start = 12.dp), maxLines = 1)
                                if (added) Icon(Icons.Filled.Check, "Added", tint = MaterialTheme.colorScheme.primary)
                                else if (nums.isEmpty()) Text("no number", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
        )
    }
}
