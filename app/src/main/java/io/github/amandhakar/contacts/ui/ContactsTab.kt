@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package io.github.amandhakar.contacts.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.amandhakar.contacts.AppViewModel
import io.github.amandhakar.contacts.Screen
import io.github.amandhakar.contacts.util.PhoneUtil
import kotlinx.coroutines.launch

private val HIDDEN_LABELS = setOf("My Contacts", "Starred in Android")

@Composable
fun ContactsTab(vm: AppViewModel) {
    val contacts by vm.contacts.collectAsState()
    val phones by vm.phones.collectAsState()
    val groups by vm.groups.collectAsState()
    val members by vm.groupMembers.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val labels = remember(groups) { groups.map { it.title }.filter { it !in HIDDEN_LABELS }.distinct() }
    val shown = remember(contacts, phones, query, members, vm.selectedGroup) {
        val q = query.trim()
        val qd = PhoneUtil.digits(q)
        contacts.filter { c ->
            (vm.selectedGroup == null || c.id in members) &&
                (q.isEmpty() || c.name.contains(q, ignoreCase = true) ||
                    (qd.length >= 3 && phones[c.id].orEmpty().any { PhoneUtil.digits(it.number).contains(qd) }))
        }
    }
    val grouped = remember(shown) { shown.groupBy { it.name.firstOrNull { ch -> ch.isLetter() }?.uppercaseChar()?.toString() ?: "#" } }
    val letterPositions = remember(grouped) {
        var i = 0
        buildMap { grouped.forEach { (l, list) -> put(l, i); i += 1 + list.size } }
    }

    Column(Modifier.fillMaxSize()) {
        SearchPill(query, { query = it }, "Search ${contacts.size} contacts", onSettings = { vm.push(Screen.Settings) })
        if (labels.isNotEmpty()) {
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = vm.selectedGroup == null, onClick = { vm.selectGroup(null) }, label = { Text("All") }) }
                items(labels, key = { it }) { t ->
                    FilterChip(selected = vm.selectedGroup == t, onClick = { vm.selectGroup(if (vm.selectedGroup == t) null else t) }, label = { Text(t) })
                }
            }
        }
        if (shown.isEmpty()) {
            EmptyState(
                Icons.Filled.PersonOff,
                if (contacts.isEmpty()) "No contacts yet" else "No matches",
                if (contacts.isEmpty()) "Tap + to add one, or add your Google account in Settings." else "Try a different name or number.",
            )
            return@Column
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
                grouped.forEach { (letter, list) ->
                    stickyHeader(key = "h$letter") {
                        Text(
                            letter,
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 24.dp, top = 6.dp, bottom = 6.dp),
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(list, key = { it.id }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.push(Screen.Detail(c.id)) }.padding(start = 16.dp, end = 36.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(c.name, c.photoUri, 48.dp)
                            Column(Modifier.padding(start = 16.dp)) {
                                Text(c.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                phones[c.id]?.firstOrNull()?.let {
                                    Text(it.number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            if (query.isBlank() && grouped.size > 4) {
                AlphabetIndex(
                    letters = grouped.keys.toList(),
                    modifier = Modifier.align(Alignment.CenterEnd),
                    onPick = { l -> letterPositions[l]?.let { scope.launch { listState.scrollToItem(it) } } },
                )
            }
        }
    }
}

@Composable
private fun SearchPill(value: String, onChange: (String) -> Unit, placeholder: String, onSettings: () -> Unit) {
    TextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, "Clear") }
            else IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
        },
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/** A–Z strip on the right edge: touch or drag to jump. */
@Composable
private fun AlphabetIndex(letters: List<String>, modifier: Modifier, onPick: (String) -> Unit) {
    Column(
        modifier.width(28.dp).fillMaxHeight(0.8f).clip(CircleShape)
            .pointerInput(letters) {
                awaitPointerEventScope {
                    while (true) {
                        val c = awaitPointerEvent().changes.firstOrNull() ?: continue
                        if (c.pressed && size.height > 0) {
                            val i = ((c.position.y / size.height) * letters.size).toInt().coerceIn(0, letters.size - 1)
                            onPick(letters[i]); c.consume()
                        }
                    }
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { Text(it, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary) }
    }
}
