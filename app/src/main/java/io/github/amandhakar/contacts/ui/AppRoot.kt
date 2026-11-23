@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.amandhakar.contacts.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.amandhakar.contacts.AppViewModel
import io.github.amandhakar.contacts.Screen

@Composable
fun AppRoot(vm: AppViewModel, requestPermissions: () -> Unit, requestDefaultDialer: () -> Unit) {
    BackHandler(enabled = vm.stack.isNotEmpty()) { vm.pop() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (!vm.permissionsOk && !vm.skipPermissions) {
            Onboarding(requestPermissions) { vm.skipPermissions = true }
            return@Surface
        }
        when (val top = vm.stack.lastOrNull()) {
            null -> MainTabs(vm, requestDefaultDialer)
            is Screen.Detail -> DetailScreen(vm, top.id)
            is Screen.Edit -> EditScreen(vm, top.id, top.number)
            Screen.Settings -> SettingsScreen(vm, requestPermissions, requestDefaultDialer)
            Screen.Blocked -> BlockedScreen(vm)
            Screen.Recordings -> RecordingsScreen(vm)
            Screen.Merge -> MergeScreen(vm)
            Screen.RecordTest -> RecordingTestScreen(vm)
        }
    }
}

@Composable
private fun Onboarding(requestPermissions: () -> Unit, skip: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Call, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.height(24.dp))
        Text("Contacts", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "To show your contacts and call history, place and answer calls, record calls and show notifications, " +
                "this app needs the Contacts, Phone, Call log, Microphone and Notifications permissions.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = requestPermissions, Modifier.fillMaxWidth()) { Text("Allow permissions") }
        TextButton(onClick = skip) { Text("Not now") }
    }
}

@Composable
private fun MainTabs(vm: AppViewModel, requestDefaultDialer: () -> Unit) {
    val titles = listOf("Favorites", "Recents", "Contacts", "Keypad")
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (vm.tab == 2) FloatingActionButton(onClick = { vm.push(Screen.Edit(null)) }) { Icon(Icons.Filled.Add, "Add contact") }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                listOf(Icons.Filled.Star, Icons.Filled.History, Icons.Filled.Person, Icons.Filled.Dialpad).forEachIndexed { i, icon ->
                    NavigationBarItem(selected = vm.tab == i, onClick = { vm.tab = i }, icon = { Icon(icon, titles[i]) }, label = { Text(titles[i]) })
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (vm.tab != 2) {
                ScreenHeader(titles[vm.tab]) {
                    if (vm.tab == 1) HistoryMenu(vm)
                    IconButton(onClick = { vm.push(Screen.Settings) }) { Icon(Icons.Filled.Settings, "Settings") }
                }
            } else {
                Spacer(Modifier.height(4.dp))
            }
            if (!vm.isDefaultDialer) {
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Make this your phone app to answer calls here.", Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        TextButton(onClick = requestDefaultDialer) { Text("Set") }
                    }
                }
            }
            when (vm.tab) {
                0 -> FavoritesTab(vm)
                1 -> RecentsTab(vm)
                2 -> ContactsTab(vm)
                else -> KeypadTab(vm)
            }
        }
    }
}
