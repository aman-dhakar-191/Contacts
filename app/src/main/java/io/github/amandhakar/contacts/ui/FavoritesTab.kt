package io.github.amandhakar.contacts.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.amandhakar.contacts.AppViewModel
import io.github.amandhakar.contacts.Screen
import io.github.amandhakar.contacts.call.Dialer

@Composable
fun FavoritesTab(vm: AppViewModel) {
    val ctx = LocalContext.current
    val contacts by vm.contacts.collectAsState()
    val phones by vm.phones.collectAsState()
    val starred = contacts.filter { it.starred }
    val version = vm.speedDialVersion
    val speed = remember(version) { (1..9).mapNotNull { s -> vm.speedDial(s)?.let { s to it } } }

    LazyColumn(Modifier.fillMaxSize()) {
        if (speed.isNotEmpty()) {
            item { Text("Speed dial", Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            items(speed, key = { "sd${it.first}" }) { (slot, entry) ->
                Row(Modifier.fillMaxWidth().clickable { Dialer.call(ctx, entry.second) }.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$slot", Modifier.padding(end = 16.dp), style = MaterialTheme.typography.titleLarge)
                    Column {
                        Text(entry.first, style = MaterialTheme.typography.bodyLarge)
                        Text(entry.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { Text("Favorites", Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        if (starred.isEmpty()) {
            item { Text("Star a contact to see it here.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(starred, key = { "f${it.id}" }) { c ->
            val first = phones[c.id]?.firstOrNull()
            Row(
                Modifier.fillMaxWidth().clickable { if (phones[c.id]?.size == 1 && first != null) Dialer.call(ctx, first.number) else vm.push(Screen.Detail(c.id)) }
                    .padding(16.dp, 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(c.name, c.photoUri)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(c.name, style = MaterialTheme.typography.bodyLarge)
                    first?.let { Text(it.number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}
