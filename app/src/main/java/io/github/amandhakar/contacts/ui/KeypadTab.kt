@file:OptIn(ExperimentalFoundationApi::class)

package io.github.amandhakar.contacts.ui

import android.telecom.PhoneAccountHandle
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.amandhakar.contacts.AppViewModel
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

    LaunchedEffect(vm.dialPrefill) {
        vm.dialPrefill?.let { number = it; vm.dialPrefill = null }
    }

    val hits = remember(number, contacts, phones) {
        if (number.length < 2 || !number.all { it.isDigit() }) emptyList()
        else contacts.flatMap { c ->
            val ps = phones[c.id].orEmpty()
            val nameHit = T9.matches(c.name, number)
            ps.filter { nameHit || PhoneUtil.digits(it.number).contains(number) }.map { c to it }
        }.take(30)
    }

    fun place() {
        if (number.isBlank()) return
        if (Dialer.accounts(ctx).size > 1) pickSim = true else { Dialer.call(ctx, number); number = "" }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(hits, key = { "${it.first.id}:${it.second.number}" }) { (c, p) ->
                Row(Modifier.fillMaxWidth().clickable { number = PhoneUtil.digits(p.number).let { d -> if (p.number.startsWith("+")) "+$d" else d } }.padding(16.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(c.name, c.photoUri, 36.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(c.name)
                        Text("${p.label} · ${p.number}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(number, Modifier.weight(1f).padding(horizontal = 16.dp), fontSize = 28.sp, textAlign = TextAlign.Center, maxLines = 1)
            if (number.isNotEmpty()) {
                Box(Modifier.combinedClickable(onClick = { number = number.dropLast(1) }, onLongClick = { number = "" }).padding(12.dp)) {
                    Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.Backspace, "Delete")
                }
            }
        }
        for (row in KEYS) {
            Row(horizontalArrangement = Arrangement.Center) {
                for ((d, letters) in row) {
                    Key(d, letters,
                        onClick = { number += d },
                        onLong = {
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
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.size(68.dp).clip(CircleShape).background(Color(0xFF2E7D32)).clickable { place() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Call, "Call", tint = Color.White) }
        Spacer(Modifier.height(12.dp))
    }

    if (pickSim) {
        val sims = remember { Dialer.accounts(ctx) }
        AlertDialog(
            onDismissRequest = { pickSim = false },
            title = { Text("Call with") },
            text = {
                Column {
                    sims.forEach { h: PhoneAccountHandle ->
                        TextButton(onClick = { pickSim = false; Dialer.call(ctx, number, h); number = "" }) { Text(Dialer.label(ctx, h)) }
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
        Modifier.padding(4.dp).size(width = 92.dp, height = 62.dp).clip(CircleShape).combinedClickable(onClick = onClick, onLongClick = onLong),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(digit, fontSize = 26.sp)
        if (letters.isNotEmpty()) Text(letters, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
