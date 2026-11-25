package io.github.amandhakar.contacts.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.CallLog
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.amandhakar.contacts.AppViewModel
import io.github.amandhakar.contacts.call.CallInfoLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One block of "name: value" lines. */
private data class Block(val title: String, val lines: List<Pair<String, String>>)

/**
 * Settings > Experimental > Call info. Shows what Android gives this app about calls, so we can see what a phone and carrier
 * really fill in: the live details captured while calls happen, and every column the system call log holds for recent calls.
 */
@Composable
fun CallInfoScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var recording by remember { mutableStateOf(CallInfoLog.enabled(ctx)) }
    var live by remember { mutableStateOf(CallInfoLog.entries(ctx)) }
    var logBlocks by remember { mutableStateOf<List<Block>>(emptyList()) }
    var columns by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        val (blocks, cols) = withContext(Dispatchers.IO) { readCallLog(ctx) }
        logBlocks = blocks; columns = cols
    }

    val liveBlocks = live.map { e ->
        val title = listOf("direction", "handle", "updatedAt").mapNotNull { k -> e.fields.firstOrNull { it.first == k }?.second }.joinToString(" · ")
        Block(title, e.fields)
    }

    Page("Call info", vm) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Everything Android tells this app about calls. It stays on this phone and is never sent anywhere. " +
                    "Make or take a few calls, then look here: a field that is missing or says null is something your phone or carrier does not supply.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Record details of new calls", style = MaterialTheme.typography.bodyLarge)
                    Text("Keeps the last 20 calls. Turn off to stop.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(recording, { recording = it; CallInfoLog.setEnabled(ctx, it) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    copyText(ctx, render(liveBlocks, logBlocks, columns))
                    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy all") }
                OutlinedButton(onClick = { CallInfoLog.clear(ctx); live = emptyList(); Toast.makeText(ctx, "Cleared", Toast.LENGTH_SHORT).show() }) { Text("Clear recorded calls") }
            }

            SectionLabel("Live details while the call happens")
            if (liveBlocks.isEmpty()) Text("Nothing yet. Make or take a call.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            liveBlocks.forEach { BlockCard(it) }

            SectionLabel("System call log, every column")
            if (columns.isNotEmpty()) Text("Columns: " + columns.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (logBlocks.isEmpty()) Text("No call log rows, or the call log permission is not allowed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            logBlocks.forEach { BlockCard(it) }
        }
    }
}

@Composable
private fun BlockCard(b: Block) {
    GroupCard(Modifier.padding(0.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(b.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            b.lines.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                    Text(k, Modifier.weight(0.42f), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(v, Modifier.weight(0.58f), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }
    }
}

/** Every column of the latest call-log rows (the provider is asked for all of them, so phone-maker columns show up too). */
private fun readCallLog(ctx: Context): Pair<List<Block>, List<String>> = try {
    ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, null, null, null, "${CallLog.Calls.DATE} DESC")?.use { c ->
        val cols = c.columnNames.toList()
        val blocks = ArrayList<Block>()
        while (blocks.size < 15 && c.moveToNext()) {
            val lines = cols.mapIndexedNotNull { i, name ->
                val v = try { if (c.getType(i) == android.database.Cursor.FIELD_TYPE_BLOB) "(binary)" else c.getString(i) } catch (_: Exception) { null }
                if (v.isNullOrEmpty()) null else name to v
            }
            val date = lines.firstOrNull { it.first == CallLog.Calls.DATE }?.second?.toLongOrNull()
            val title = (date?.let { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(it)) } ?: "call") +
                " · " + (lines.firstOrNull { it.first == CallLog.Calls.NUMBER }?.second ?: "")
            blocks += Block(title, lines)
        }
        blocks to cols
    } ?: (emptyList<Block>() to emptyList())
} catch (_: Exception) { emptyList<Block>() to emptyList() }

private fun render(live: List<Block>, log: List<Block>, columns: List<String>): String = buildString {
    appendLine("== Live details ==")
    live.forEach { b -> appendLine("-- ${b.title}"); b.lines.forEach { (k, v) -> appendLine("$k: $v") } }
    appendLine("== System call log ==")
    appendLine("columns: " + columns.joinToString(", "))
    log.forEach { b -> appendLine("-- ${b.title}"); b.lines.forEach { (k, v) -> appendLine("$k: $v") } }
}

private fun copyText(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("Call info", text))
}
