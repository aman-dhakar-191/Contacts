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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import io.github.amandhakar.contacts.data.SwipeAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
internal fun Page(title: String, vm: AppViewModel, content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(title = { Text(title) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background), navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { content(it) }
}

@Composable
fun SettingsScreen(vm: AppViewModel, requestPermissions: () -> Unit, requestDefaultDialer: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts by vm.accounts.collectAsState()
    var autoRecord by remember { mutableStateOf(vm.prefs.autoRecord) }
    var recordUnknown by remember { mutableStateOf(vm.prefs.recordUnknown) }
    var telemetryOn by remember { mutableStateOf(io.github.amandhakar.contacts.util.Telemetry.isEnabled()) }
    var confirmCrash by remember { mutableStateOf(false) }
    val listCount = remember { io.github.amandhakar.contacts.data.RecordRules(ctx).entries().size }
    var speakerRec by remember { mutableStateOf(vm.prefs.speakerForRecording) }
    var swipeLeft by remember { mutableStateOf(vm.swipeLeftAction) }
    var swipeRight by remember { mutableStateOf(vm.swipeRightAction) }
    val google = accounts.filter { it.isGoogle }
    val local = accounts.firstOrNull { it.name == null }
    val others = accounts.filter { !it.isGoogle && it.name != null }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val n = withContext(Dispatchers.IO) {
                try {
                    val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                    vm.contactsRepo.importVcf(text, vm.defaultAccount())
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
            SectionLabel("Phone app")
            GroupCard {
                InfoRow(
                    Icons.Filled.Phone,
                    if (vm.isDefaultDialer) "Default phone app" else "Not your default phone app",
                    if (vm.isDefaultDialer) "Calls ring and are answered here." else "Set it to answer calls here and block numbers.",
                    trailing = { if (!vm.isDefaultDialer) Button(onClick = requestDefaultDialer) { Text("Set") } },
                )
                InfoRow(Icons.Filled.Security, "Permissions", "Check that contacts, phone, call log, microphone and notifications are allowed", onClick = requestPermissions)
            }

            SectionLabel("Google contacts")
            GroupCard {
                InfoRow(
                    Icons.Filled.AccountCircle, "Choose accounts",
                    "Showing: ${vm.displaySummary()} · new contacts save to ${vm.defaultAccount()?.let { it.name ?: "Phone" } ?: "Phone"}",
                    onClick = { vm.push(Screen.Accounts) },
                )
                if (google.isEmpty()) {
                    InfoRow(Icons.Filled.AccountCircle, "No Google account on this phone", "Add one and turn on contacts sync; your contacts will appear here.")
                }
                google.forEach { a ->
                    InfoRow(Icons.Filled.AccountCircle, a.name ?: "", if (a.count == 1) "1 contact" else "${a.count} contacts")
                }
                if (local != null) InfoRow(Icons.Filled.Phone, "On this phone only", if (local.count == 1) "1 contact, not synced" else "${local.count} contacts, not synced")
                if (others.isNotEmpty()) {
                    InfoRow(null, "Also shown (read-only): " + others.joinToString { it.title })
                }
                InfoRow(Icons.Filled.PersonAdd, "Add Google account", onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_ADD_ACCOUNT).putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google")))
                })
                InfoRow(Icons.Filled.Sync, "Sync now", "Edits you make here sync back to Google.", onClick = {
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
                })
                InfoRow(Icons.Filled.Tune, "Account sync settings", onClick = { ctx.startActivity(Intent(Settings.ACTION_SYNC_SETTINGS)) })
            }

            SectionLabel("Call recording")
            GroupCard {
                InfoRow(Icons.Filled.Mic, "Record all calls", "Start recording as soon as a call connects",
                    onClick = { autoRecord = !autoRecord; vm.prefs.autoRecord = autoRecord },
                    trailing = { Switch(autoRecord, { autoRecord = it; vm.prefs.autoRecord = it }) })
                InfoRow(Icons.Filled.Mic, "Record unknown numbers", "Calls from numbers not in your contacts, and hidden numbers",
                    onClick = { recordUnknown = !recordUnknown; vm.prefs.recordUnknown = recordUnknown },
                    trailing = { Switch(recordUnknown, { recordUnknown = it; vm.prefs.recordUnknown = it }) })
                InfoRow(Icons.Filled.PersonAdd, "Auto-record list", if (listCount == 0) "Numbers and contacts that are always recorded" else if (listCount == 1) "1 number is always recorded" else "$listCount numbers are always recorded",
                    onClick = { vm.push(Screen.RecordList) })
                InfoRow(Icons.Filled.VolumeUp, "Speaker fallback", "Use the speaker when only the microphone can be recorded",
                    onClick = { speakerRec = !speakerRec; vm.prefs.speakerForRecording = speakerRec },
                    trailing = { Switch(speakerRec, { speakerRec = it; vm.prefs.speakerForRecording = it }) })
                InfoRow(Icons.Filled.LibraryMusic, "Recordings", onClick = { vm.push(Screen.Recordings) })
                InfoRow(Icons.Filled.Science, "Recording test", "Find out which inputs this phone lets the app record, and whether the other side is captured", onClick = { vm.push(Screen.RecordTest) })
            }
            Text(
                "Most phones don't let apps capture the other person's voice directly, so a recording may only hold your side " +
                    "plus what the speaker plays. Many places require everyone on the call to agree to being recorded: tell them first.",
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionLabel("Contacts")
            GroupCard {
                InfoRow(Icons.Filled.MergeType, "Merge duplicates", onClick = { vm.push(Screen.Merge) })
                InfoRow(Icons.Filled.Block, "Blocked numbers", onClick = { vm.push(Screen.Blocked) })
                InfoRow(Icons.Filled.Download, "Import from .vcf file", onClick = { importLauncher.launch(arrayOf("text/x-vcard", "text/vcard", "text/directory", "*/*")) })
                InfoRow(Icons.Filled.Upload, "Export all to .vcf file", onClick = { exportLauncher.launch("contacts.vcf") })
            }
            SectionLabel("Swipe actions")
            GroupCard {
                InfoRow(
                    null, "Swipe left",
                    "Action revealed when swiping left in Recents and Contacts",
                    trailing = {
                        SwipeActionPicker(swipeLeft) {
                            swipeLeft = it
                            vm.updateSwipeLeftAction(it)
                        }
                    },
                )
                InfoRow(
                    null, "Swipe right",
                    "Action revealed when swiping right in Recents and Contacts",
                    trailing = {
                        SwipeActionPicker(swipeRight) {
                            swipeRight = it
                            vm.updateSwipeRightAction(it)
                        }
                    },
                )
            }

            SectionLabel("Identifier")
            GroupCard {
                val idStore = remember { io.github.amandhakar.contacts.identifier.Store.get(ctx) }
                val idRules = remember { idStore.rules() }
                InfoRow(
                    Icons.Filled.Shield, "Call identifier",
                    if (vm.prefs.identifierOn) "On · ${idRules.count { it.enabled }} of ${idRules.size} rules on" else "Rules off · flagged numbers are still shown",
                    onClick = { vm.push(Screen.Identifier) },
                )
            }

            SectionLabel("Diagnostics")
            GroupCard {
                InfoRow(Icons.Filled.BugReport, "Share crash reports and usage",
                    "Sends crashes and anonymous usage (screens, which recording input works). Never names, numbers, accounts or call content.",
                    onClick = { telemetryOn = !telemetryOn; io.github.amandhakar.contacts.util.Telemetry.setEnabled(telemetryOn) },
                    trailing = { Switch(telemetryOn, { telemetryOn = it; io.github.amandhakar.contacts.util.Telemetry.setEnabled(it) }) })
                InfoRow(Icons.Filled.Phone, "Live call chip / island", io.github.amandhakar.contacts.call.Notifications.liveUpdateStatus(ctx), onClick = {
                    try {
                        ctx.startActivity(Intent("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS").putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                    } catch (_: Exception) {
                        try { ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)) } catch (_: Exception) {}
                    }
                })
                InfoRow(Icons.Filled.Science, "Send a test report", "A harmless non-fatal report, to check it reaches Firebase", onClick = {
                    io.github.amandhakar.contacts.util.Telemetry.nonFatal("settings_test_report", RuntimeException("Test report from Settings"))
                    io.github.amandhakar.contacts.util.Telemetry.event("test_report")
                    Toast.makeText(ctx, if (telemetryOn) "Test report sent" else "Turn on sharing first", Toast.LENGTH_SHORT).show()
                })
                InfoRow(Icons.Filled.BugReport, "Test crash", "Closes the app on purpose. The report is sent next time you open it.", onClick = { confirmCrash = true })
            }
            if (confirmCrash) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirmCrash = false },
                    title = { Text("Crash the app?") },
                    text = { Text("The app will close. If it is your default phone app, an ongoing call is not affected, but open the app again afterwards.") },
                    confirmButton = { TextButton(onClick = { confirmCrash = false; io.github.amandhakar.contacts.util.Telemetry.testCrash() }) { Text("Crash") } },
                    dismissButton = { TextButton(onClick = { confirmCrash = false }) { Text("Cancel") } },
                )
            }
            Text(
                "Contacts ${io.github.amandhakar.contacts.BuildConfig.VERSION_NAME} · experimental",
                Modifier.fillMaxWidth().padding(24.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}


@Composable
private fun SwipeActionPicker(value: SwipeAction, onChange: (SwipeAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(value.label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SwipeAction.values().forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = { expanded = false; onChange(action) },
                )
            }
        }
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
