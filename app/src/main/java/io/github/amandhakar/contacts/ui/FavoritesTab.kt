@file:OptIn(ExperimentalFoundationApi::class)

package io.github.amandhakar.contacts.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

    LazyVerticalGrid(
        columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (speed.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text("Speed dial", Modifier.padding(start = 4.dp, bottom = 6.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        speed.forEach { (slot, e) ->
                            SuggestionChip(onClick = { Dialer.call(ctx, e.second) }, label = { Text("$slot · ${e.first}", maxLines = 1) })
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Favorites", Modifier.padding(start = 4.dp, top = if (speed.isNotEmpty()) 8.dp else 0.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        if (starred.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(Icons.Filled.StarBorder, "No favorites yet", "Open a contact and tap the star to pin them here.")
            }
        }
        items(starred, key = { it.id }) { c ->
            val first = phones[c.id]?.firstOrNull()
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                    .combinedClickable(
                        onClick = { if (phones[c.id]?.size == 1 && first != null) Dialer.call(ctx, first.number) else vm.push(Screen.Detail(c.id)) },
                        onLongClick = { vm.push(Screen.Detail(c.id)) },
                    ).padding(vertical = 14.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Avatar(c.name, c.photoUri, 72.dp)
                Text(c.name, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}
