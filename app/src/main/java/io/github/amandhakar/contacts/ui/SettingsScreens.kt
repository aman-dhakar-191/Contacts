@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.amandhakar.contacts.ui

import android.accounts.Account
import android.content.ContentResolver
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.amandhakar.contacts.AppViewModel
import io.github.amandhakar.contacts.Screen
import io.github.amandhakar.contacts.data.BlockedNumber
import io.github.amandhakar.contacts.data.ContactSummary
import io.github.amandhakar.contacts.data.RecordingEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
private fun Page(title: String, vm: AppViewModel, content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { content(it) }
}

@Composable
private fun SectionTitle(t: String) =
    Text(t, Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

@Composable
fun SettingsScreen(vm: AppViewModel, requestPermissions: () -> Unit, requestDefaultDialer: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts by vm.accounts.collectAsState()
    var autoRecord by remember { mutableStateOf(vm.prefs.autoRecord) }
    var speakerRec by remember { mutableStateOf(vm.prefs.speakerForRecording) }
    val google = accounts.filter { it.isGoogle }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val n = withContext(Dispatchers.IO) {
                try {
                    val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                    vm.contactsRepo.importVcf(text, google.firstOrNull())
                } catch (_: Exception) { -1 }
            }
            vm.reload()
            Toast.makeText(ctx, if (n < 0) "Couldn't read that file" else "Imported $n contacts", Toast.LENGTH_LONG).show()
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val keys = vm.contacts.value.map { it.lookupKey }.filter { it.isNotEmpty() }
                    ctx.contentResolver.openOutputStream(uri)?.use { out ->
                        keys.chunked(80).forEach { vm.contactsRepo.exportVcf(it, out) }
                    }
                    true
                } catch (_: Exception) { false }
            }
            Toast.makeText(ctx, if (ok) "Exported ${vm.contacts.value.size} contacts" else "Export failed", Toast.LENGTH_LONG).show()
        }
    }

    Page("Settings", vm) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionTitle("Phone app")
            Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (vm.isDefaultDialer) "This is your default phone app." else "Not the default phone app yet: it can't answer calls or block numbers.", Modifier.weight(1f))
                if (!vm.isDefaultDialer) TextButton(onClick = requestDefaultDialer) { Text("Set") }
            }
            TextButton(onClick = requestPermissions, Modifier.padding(horizontal = 8.dp)) { Text("Re-check permissions") }

            HorizontalDivider()
            SectionTitle("Google contacts")
            Text(
                "Contacts from a Google account on this phone appear here automatically; edits sync back to Google. " +
                    "If your contacts are missing, add the account and turn on contacts sync.",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium,
            )
            accounts.forEach { a ->
                Text("• ${a.title}: ${a.count} contacts" + if (a.isGoogle) " (Google)" else "", Modifier.padding(16.dp, 2.dp))
            }
            if (google.isEmpty()) Text("No Google account found in contacts.", Modifier.padding(16.dp, 4.dp), color = MaterialTheme.colorScheme.error)
            Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_ADD_ACCOUNT).putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google")))
                }) { Text("Add Google account") }
                TextButton(onClick = {
                    google.forEach { a ->
                        val n = a.name ?: return@forEach
                        val t = a.type ?: return@forEach
                        ContentResolver.requestSync(
                            Account(n, t), ContactsContract.AUTHORITY,
                            Bundle().apply {
                                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                                putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                            },
                        )
                    }
                    Toast.makeText(ctx, if (google.isEmpty()) "No Google account to sync" else "Sync requested", Toast.LENGTH_SHORT).show()
                    vm.reload()
                }) { Text("Sync now") }
            }
            TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_SYNC_SETTINGS)) }, Modifier.padding(horizontal = 8.dp)) { Text("Open account sync settings") }

            HorizontalDivider()
            SectionTitle("Call recording")
            SwitchRow("Record all calls automatically", autoRecord) { autoRecord = it; vm.prefs.autoRecord = it }
            SwitchRow("Use speaker when only the microphone can be recorded", speakerRec) { speakerRec = it; vm.prefs.speakerForRecording = it }
            Text(
                "Most phones don't let apps capture the other person's voice directly, so the recording may only " +
                    "contain your side plus whatever the speaker plays. Many places require everyone on the call to " +
                    "agree to being recorded: tell them first.",
                Modifier.padding(16.dp, 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { vm.push(Screen.Recordings) }, Modifier.padding(horizontal = 8.dp)) { Text("Open recordings") }

            HorizontalDivider()
            SectionTitle("Contacts")
            TextButton(onClick = { vm.push(Screen.Merge) }, Modifier.padding(horizontal = 8.dp)) { Text("Merge duplicates") }
            TextButton(onClick = { vm.push(Screen.Blocked) }, Modifier.padding(horizontal = 8.dp)) { Text("Blocked numbers") }
            TextButton(onClick = { importLauncher.launch(arrayOf("text/x-vcard", "text/vcard", "text/directory", "*/*")) }, Modifier.padding(horizontal = 8.dp)) { Text("Import from .vcf file") }
            TextButton(onClick = { exportLauncher.launch("contacts.vcf") }, Modifier.padding(horizontal = 8.dp)) { Text("Export all to .vcf file") }
            Text("Version ${io.github.amandhakar.contacts.BuildConfig.VERSION_NAME} (experimental)", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
fun BlockedScreen(vm: AppViewModel) {
    val list = remember { mutableStateListOf<BlockedNumber>() }
    var number by remember { mutableStateOf("") }
    val can = remember { vm.blockedRepo.canBlock() }
    val ctx = LocalContext.current
    fun load() { list.clear(); list.addAll(vm.blockedRepo.list()) }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { load() } }

    Page("Blocked numbers", vm) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!can) Text("Set this as the default phone app to manage blocked numbers.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(number, { number = it }, label = { Text("Number to block") }, singleLine = true, modifier = Modifier.weight(1f))
                Button(enabled = can && number.isNotBlank(), onClick = {
                    val ok = vm.blockedRepo.block(number.trim())
                    if (!ok) Toast.makeText(ctx, "Couldn't block that number", Toast.LENGTH_SHORT).show()
                    number = ""; load()
                }, modifier = Modifier.padding(start = 8.dp)) { Text("Block") }
            }
            LazyColumn {
                items(list, key = { it.id }) { b ->
                    Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(b.number, Modifier.weight(1f))
                        TextButton(onClick = { vm.blockedRepo.unblock(b.id); load() }) { Text("Unblock") }
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingsScreen(vm: AppViewModel) {
    val list = remember { mutableStateListOf<RecordingEntry>() }
    val index by vm.numberIndex.collectAsState()
    var playing by remember { mutableStateOf<String?>(null) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    fun load() { list.clear(); list.addAll(vm.recordingsRepo.list()) }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { load() } }
    DisposableEffect(Unit) { onDispose { player[0]?.release(); player[0] = null } }

    fun stop() { player[0]?.release(); player[0] = null; playing = null }
    fun play(path: String) {
        stop()
        try {
            val p = MediaPlayer()
            p.setDataSource(path); p.prepare(); p.start()
            p.setOnCompletionListener { stop() }
            player[0] = p; playing = path
        } catch (_: Exception) { stop() }
    }

    Page("Recordings", vm) { pad ->
        if (list.isEmpty()) {
            Text("No recordings yet. Use the Record button during a call.", Modifier.padding(pad).padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Page
        }
        LazyColumn(Modifier.padding(pad)) {
            items(list, key = { it.path }) { r ->
                val who = index[io.github.amandhakar.contacts.util.PhoneUtil.key(r.number)]?.name ?: r.number
                Row(Modifier.fillMaxWidth().padding(16.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (playing == r.path) stop() else play(r.path) }) {
                        Icon(if (playing == r.path) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(who)
                        Text(
                            (if (r.incoming) "Incoming" else "Outgoing") + " · " +
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(r.date)) +
                                " · " + (r.sizeBytes / 1024) + " KB" + if (r.bothSides) "" else " · your side only",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { if (playing == r.path) stop(); vm.recordingsRepo.delete(r.path); load() }) { Icon(Icons.Filled.Delete, "Delete") }
                }
            }
        }
    }
}

@Composable
fun MergeScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<List<ContactSummary>>?>(null) }
    suspend fun load() { groups = withContext(Dispatchers.IO) { vm.contactsRepo.duplicates() } }
    LaunchedEffect(Unit) { load() }

    Page("Merge duplicates", vm) { pad ->
        val g = groups
        when {
            g == null -> Text("Looking for duplicates…", Modifier.padding(pad).padding(24.dp))
            g.isEmpty() -> Text("No duplicates found.", Modifier.padding(pad).padding(24.dp))
            else -> LazyColumn(Modifier.padding(pad)) {
                items(g, key = { it.first().id }) { dup ->
                    Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { dup.forEach { Text(it.name) } }
                        OutlinedButton(onClick = {
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { vm.contactsRepo.merge(dup.map { it.id }) }
                                Toast.makeText(ctx, if (ok) "Merged" else "Couldn't merge", Toast.LENGTH_SHORT).show()
                                vm.reload(); load()
                            }
                        }) { Text("Merge") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
