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

@Composable
fun KeypadTab(vm: AppViewModel) {
    val ctx = LocalContext.current
    val contacts by vm.contacts.collectAsState()
    val phones by vm.phones.collectAsState()
    var number by rememberSaveable { mutableStateOf("") }
    var pickSim by remember { mutableStateOf(false) }

    val clipNumber = rememberClipboardNumber()

    LaunchedEffect(vm.dialPrefill) { vm.dialPrefill?.let { number = it; vm.dialPrefill = null } }

    val hits = remember(number, contacts, phones) { dialHits(number, contacts, phones, 20) }
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
                DialMatchRow(c, p, onClick = {
                    number = PhoneUtil.digits(p.number).let { d -> if (p.number.startsWith("+")) "+$d" else d }
                })
            }
        }

        // number
        Column(Modifier.fillMaxWidth().height(96.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            DialNumberDisplay(number, "Enter a number", clipNumber, onPaste = { number += it })
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
        DialKeyGrid(keySize = 76.dp, spacing = 20.dp, onKey = { number += it }, onLong = { d ->
            when {
                d == "0" -> number += "+"
                d[0] in '1'..'9' -> {
                    val sd = vm.speedDial(d.toInt())
                    if (sd != null) Dialer.call(ctx, sd.second)
                    else Toast.makeText(ctx, "No speed dial on $d. Set one from a contact.", Toast.LENGTH_SHORT).show()
                }
            }
        })

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
