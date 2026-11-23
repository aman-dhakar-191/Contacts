@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.amandhakar.contacts.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.amandhakar.contacts.AppViewModel
import io.github.amandhakar.contacts.Screen

@Composable
fun AppRoot(vm: AppViewModel, requestPermissions: () -> Unit, requestDefaultDialer: () -> Unit) {
    BackHandler(enabled = vm.stack.isNotEmpty()) { vm.pop() }
    if (!vm.permissionsOk && !vm.skipPermissions) {
        Onboarding(requestPermissions) { vm.skipPermissions = true }
        return
    }
    when (val top = vm.stack.lastOrNull()) {
        null -> MainTabs(vm, requestPermissions, requestDefaultDialer)
        is Screen.Detail -> DetailScreen(vm, top.id)
        is Screen.Edit -> EditScreen(vm, top.id, top.number)
        Screen.Settings -> SettingsScreen(vm, requestPermissions, requestDefaultDialer)
        Screen.Blocked -> BlockedScreen(vm)
        Screen.Recordings -> RecordingsScreen(vm)
        Screen.Merge -> MergeScreen(vm)
    }
}

@Composable
private fun Onboarding(requestPermissions: () -> Unit, skip: () -> Unit) {
    Scaffold { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Contacts", style = androidx.compose.material3.MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(16.dp))
            Text(
                "To show your contacts and call history, place and answer calls, record calls and show " +
                    "notifications, this app needs the Contacts, Phone, Call log, Microphone and Notifications permissions.",
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = requestPermissions) { Text("Allow permissions") }
            TextButton(onClick = skip) { Text("Not now") }
        }
    }
}

@Composable
private fun MainTabs(vm: AppViewModel, requestPermissions: () -> Unit, requestDefaultDialer: () -> Unit) {
    val titles = listOf("Favorites", "Recents", "Contacts", "Keypad")
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titles[vm.tab]) },
                actions = {
                    if (vm.tab == 1) HistoryMenu(vm)
                    IconButton(onClick = { vm.push(Screen.Settings) }) { Icon(Icons.Filled.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            if (vm.tab == 2) FloatingActionButton(onClick = { vm.push(Screen.Edit(null)) }) { Icon(Icons.Filled.Add, "Add contact") }
        },
        bottomBar = {
            NavigationBar {
                listOf(Icons.Filled.Star, Icons.Filled.History, Icons.Filled.Person, Icons.Filled.Dialpad).forEachIndexed { i, icon ->
                    NavigationBarItem(selected = vm.tab == i, onClick = { vm.tab = i }, icon = { Icon(icon, titles[i]) }, label = { Text(titles[i]) })
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!vm.isDefaultDialer) {
                Card(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Set as default phone app to answer calls here.", Modifier.weight(1f))
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
