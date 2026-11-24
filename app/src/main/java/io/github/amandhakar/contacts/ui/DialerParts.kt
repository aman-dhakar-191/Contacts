@file:OptIn(ExperimentalFoundationApi::class)

package io.github.amandhakar.contacts.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.amandhakar.contacts.data.ContactSummary
import io.github.amandhakar.contacts.data.PhoneEntry
import io.github.amandhakar.contacts.util.PhoneUtil
import io.github.amandhakar.contacts.util.T9

/*
 * Pieces of the dialer shared by the Keypad tab and the in-call keypad, so both look and behave the same.
 */

internal val KEYS = listOf(
    listOf("1" to "", "2" to "ABC", "3" to "DEF"),
    listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
    listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
    listOf("*" to "", "0" to "+", "#" to ""),
)

@Composable
internal fun Key(digit: String, letters: String, onClick: () -> Unit, onLong: () -> Unit = {}, size: Dp = 76.dp) {
    Column(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onClick, onLongClick = onLong),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(digit, fontSize = 28.sp)
        if (letters.isNotEmpty()) Text(letters, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The 4x3 grid of round keys. `onLong` gets the digit that was held. */
@Composable
internal fun DialKeyGrid(keySize: Dp, spacing: Dp, onKey: (String) -> Unit, onLong: (String) -> Unit = {}) {
    for (row in KEYS) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing), modifier = Modifier.padding(vertical = 5.dp)) {
            for ((d, letters) in row) Key(d, letters, onClick = { onKey(d) }, onLong = { onLong(d) }, size = keySize)
        }
    }
}

/** The number being dialled. Holding it offers to paste a phone number from the clipboard. */
@Composable
internal fun DialNumberDisplay(number: String, placeholder: String, clip: String?, onPaste: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box(contentAlignment = Alignment.Center) {
        Text(
            number.ifEmpty { placeholder },
            Modifier.padding(horizontal = 24.dp).combinedClickable(onClick = {}, onLongClick = { if (clip != null) menu = true }),
            fontSize = when { number.isEmpty() -> 22.sp; number.length > 14 -> 26.sp; number.length > 10 -> 32.sp; else -> 40.sp },
            color = if (number.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center, maxLines = 1,
        )
        DropdownMenu(expanded = menu && clip != null, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Paste ${clip.orEmpty()}") },
                leadingIcon = { Icon(Icons.Filled.ContentPaste, null) },
                onClick = { menu = false; clip?.let(onPaste) },
            )
        }
    }
}

/** A matching contact, as a rounded card. Pass `onClick = null` for a plain, non-tappable row. */
@Composable
internal fun DialMatchRow(c: ContactSummary, p: PhoneEntry, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(c.name, c.photoUri, 40.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${p.label} · ${p.number}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Contacts whose number contains the digits, or whose name matches them on a phone keypad (T9). */
internal fun dialHits(number: String, contacts: List<ContactSummary>, phones: Map<Long, List<PhoneEntry>>, limit: Int): List<Pair<ContactSummary, PhoneEntry>> {
    if (number.length < 2 || !number.all { it.isDigit() }) return emptyList()
    return contacts.flatMap { c ->
        val nameHit = T9.matches(c.name, number)
        phones[c.id].orEmpty().filter { nameHit || PhoneUtil.digits(it.number).contains(number) }.map { c to it }
    }.take(limit)
}

/**
 * A phone number on the clipboard, or null. Re-read when the screen comes to the front or the clip changes
 * (Android only lets a foreground app read the clipboard).
 */
@Composable
internal fun rememberClipboardNumber(): String? {
    val ctx = LocalContext.current
    var clip by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        fun refresh() { clip = clipboardNumber(cm) }
        val listener = ClipboardManager.OnPrimaryClipChangedListener { refresh() }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        cm?.addPrimaryClipChangedListener(listener)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) refresh()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cm?.removePrimaryClipChangedListener(listener)
        }
    }
    return clip
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
    if (!t.all { it.isDigit() || it in "+*#-.()/  " }) return null
    val cleaned = t.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
    val digits = cleaned.count { it.isDigit() }
    return if (digits in 3..16 && cleaned.lastIndexOf('+') <= 0) cleaned else null
}
