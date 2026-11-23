@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package io.github.amandhakar.contacts.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.amandhakar.contacts.AppViewModel
import io.github.amandhakar.contacts.Screen
import io.github.amandhakar.contacts.util.PhoneUtil

@Composable
fun ContactsTab(vm: AppViewModel) {
    val contacts by vm.contacts.collectAsState()
    val phones by vm.phones.collectAsState()
    val groups by vm.groups.collectAsState()
    val members by vm.groupMembers.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }

    val shown = remember(contacts, phones, query, members, vm.selectedGroup) {
        val q = query.trim()
        val qd = PhoneUtil.digits(q)
        contacts.filter { c ->
            (vm.selectedGroup == null || c.id in members) &&
                (q.isEmpty() || c.name.contains(q, ignoreCase = true) ||
                    (qd.length >= 3 && phones[c.id].orEmpty().any { PhoneUtil.digits(it.number).contains(qd) }))
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Search ${contacts.size} contacts") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        )
        if (groups.isNotEmpty()) {
            LazyRow(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = vm.selectedGroup == null, onClick = { vm.selectGroup(null) }, label = { Text("All") }) }
                items(groups, key = { it.id }) { g ->
                    FilterChip(selected = vm.selectedGroup == g.id, onClick = { vm.selectGroup(g.id) }, label = { Text(g.title) })
                }
            }
        }
        if (shown.isEmpty()) {
            Text(
                if (contacts.isEmpty()) "No contacts yet. Tap + to add one, or import them in Settings." else "No matches.",
                Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        val grouped = remember(shown) { shown.groupBy { it.name.firstOrNull { ch -> ch.isLetter() }?.uppercaseChar()?.toString() ?: "#" } }
        LazyColumn(Modifier.fillMaxSize()) {
            grouped.forEach { (letter, list) ->
                stickyHeader(key = "h$letter") {
                    Text(
                        letter, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(list, key = { it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.push(Screen.Detail(c.id)) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(c.name, c.photoUri)
                        Text(c.name, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
