@file:OptIn(ExperimentalFoundationApi::class)

package io.github.amandhakar.contacts.ui

import android.content.ClipboardManager
import android.content.Context
import android.telecom.PhoneAccountHandle
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.amandhakar.contacts.AppViewModel
import io.github.amandhakar.contacts.Screen
import io.github.amandhakar.contacts.call.Dialer
import io.github.amandhakar.contacts.util.PhoneUtil
import io.github.amandhakar.contacts.util.T9

private val KEYS = listOf(
    listOf("1" to "", "2" to "ABC", "3" to "DEF"),
    listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
    listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
    listOf("*" to "", "0" to "+", "#" to ""),
)

@Composable
fun KeypadTab(vm: AppViewModel) {
    val ctx = LocalContext.current
    val contacts by vm.contacts.collectAsState()
    val phones by vm.phones.collectAsState()
    var number by rememberSaveable { mutableStateOf("") }
    var pickSim by remember { mutableStateOf(false) }

    // A phone number on the clipboard is offered as a one-tap paste. Re-read whenever the app comes
    // to the front or the clip changes (Android only lets a foreground app read the clipboard).
    var clipNumber by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        fun refresh() { clipNumber = clipboardNumber(cm) }
        val clipListener = ClipboardManager.OnPrimaryClipChangedListener { refresh() }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        cm?.addPrimaryClipChangedListener(clipListener)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) refresh()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cm?.removePrimaryClipChangedListener(clipListener)
        }
    }

    LaunchedEffect(vm.dialPrefill) { vm.dialPrefill?.let { number = it; vm.dialPrefill = null } }

    val hits = remember(number, contacts, phones) {
        if (number.length < 2 || !number.all { it.isDigit() }) emptyList()
        else contacts.flatMap { c ->
            val ps = phones[c.id].orEmpty()
            val nameHit = T9.matches(c.name, number)
            ps.filter { nameHit || PhoneUtil.digits(it.number).contains(number) }.map { c to it }
        }.take(20)
    }
    val exact = remember(number, contacts, phones) {
        if (number.isBlank()) null else vm.numberIndex.value[PhoneUtil.key(number)]
    }

    fun place() {
        if (number.isBlank()) return
        if (Dialer.accounts(ctx).size > 1) pickSim = true else { Dialer.call(ctx, number); number = "" }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        // matches
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(hits, key = { "${it.first.id}:${it.second.number}" }) { (c, p) ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable {
                        number = PhoneUtil.digits(p.number).let { d -> if (p.number.startsWith("+")) "+$d" else d }
                    }.background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(c.name, c.photoUri, 40.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.label} · ${p.number}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // number
        Column(Modifier.fillMaxWidth().height(96.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                if (number.isEmpty()) "Enter a number" else number,
                Modifier.padding(horizontal = 24.dp).combinedClickable(
                    onClick = {},
                    onLongClick = { clipNumber?.let { number = number + it } },
                ),
                fontSize = when { number.isEmpty() -> 22.sp; number.length > 14 -> 26.sp; number.length > 10 -> 32.sp; else -> 40.sp },
                color = if (number.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center, maxLines = 1,
            )
            val pasteable = clipNumber?.takeIf { number.isEmpty() }
            if (pasteable != null) {
                androidx.compose.material3.AssistChip(
                    onClick = { number = pasteable },
                    label = { Text("Paste $pasteable", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Filled.ContentPaste, null, Modifier.size(18.dp)) },
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else if (exact != null) {
                Text(exact.name, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
            } else if (number.length >= 3) {
                TextButton(onClick = { vm.push(Screen.Edit(null, number)) }) { Text("Add to contacts") }
            }
        }

        // keys
        for (row in KEYS) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 5.dp)) {
                for ((d, letters) in row) {
                    Key(d, letters, onClick = { number += d }, onLong = {
                        when {
                            d == "0" -> number += "+"
                            d[0] in '1'..'9' -> {
                                val sd = vm.speedDial(d.toInt())
                                if (sd != null) Dialer.call(ctx, sd.second)
                                else Toast.makeText(ctx, "No speed dial on $d. Set one from a contact.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    })
                }
            }
        }

        // call row
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                if (number.isNotEmpty() && exact == null) {
                    IconButton(onClick = { vm.push(Screen.Edit(null, number)) }) { Icon(Icons.Filled.PersonAdd, "Add contact") }
                }
            }
            Box(
                Modifier.padding(horizontal = 20.dp).size(width = 120.dp, height = 68.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary).clickable { place() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Call, "Call", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp)) }
            Box(Modifier.size(64.dp).clip(CircleShape).combinedClickable(onClick = { number = number.dropLast(1) }, onLongClick = { number = "" }), contentAlignment = Alignment.Center) {
                if (number.isNotEmpty()) Icon(Icons.AutoMirrored.Filled.Backspace, "Delete")
            }
        }
    }

    if (pickSim) {
        val sims = remember { Dialer.accounts(ctx) }
        AlertDialog(
            onDismissRequest = { pickSim = false },
            title = { Text("Call with") },
            text = {
                Column {
                    sims.forEach { h: PhoneAccountHandle ->
                        TextButton(onClick = { pickSim = false; Dialer.call(ctx, number, h); number = "" }) { Text(Dialer.label(ctx, h), style = MaterialTheme.typography.titleMedium) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickSim = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Key(digit: String, letters: String, onClick: () -> Unit, onLong: () -> Unit) {
    Column(
        Modifier.size(76.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onClick, onLongClick = onLong),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(digit, fontSize = 28.sp)
        if (letters.isNotEmpty()) Text(letters, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The phone number on the clipboard, cleaned to keypad characters, or null if the clip is not
 * (just) a phone number. Accepts "+91 98765-43210", "(022) 1234 5678" etc.; rejects sentences.
 */
private fun clipboardNumber(cm: ClipboardManager?): String? {
    val text = try {
        cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
    } catch (_: Exception) { null } ?: return null
    val t = text.trim()
    if (t.isEmpty() || t.length > 32) return null
    if (!t.all { it.isDigit() || it in "+*#-.()/ \u00A0" }) return null
    val cleaned = t.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
    val digits = cleaned.count { it.isDigit() }
    return if (digits in 3..16 && cleaned.lastIndexOf('+') <= 0) cleaned else null
}
