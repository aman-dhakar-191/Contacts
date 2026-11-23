package io.github.amandhakar.contacts.ui

import android.content.Intent
import android.telecom.Call
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.amandhakar.contacts.call.CallInfo
import io.github.amandhakar.contacts.call.InCallActivity
import io.github.amandhakar.contacts.call.callStatusLabel
import kotlinx.coroutines.delay

/** Shown at the top of the app while a call is going on or ringing, so the call can always be found again. Tap to return to it. */
@Composable
fun CallBanner(info: CallInfo) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(info.state) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val seconds = if (info.state == Call.STATE_ACTIVE && info.connectTimeMillis > 0) ((now - info.connectTimeMillis) / 1000).coerceAtLeast(0) else null
    val ringing = info.state == Call.STATE_RINGING
    val bg = if (ringing) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primary
    val fg = if (ringing) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimary
    Surface(
        Modifier.fillMaxWidth().clickable { ctx.startActivity(Intent(ctx, InCallActivity::class.java)) },
        color = bg,
    ) {
        Row(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Call, null, Modifier.size(20.dp), tint = fg)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(info.name ?: info.number.ifBlank { "Unknown" }, color = fg, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(callStatusLabel(info.state, seconds) + (info.tag?.let { " · ${it.name}" } ?: ""), color = fg, style = MaterialTheme.typography.bodySmall)
            }
            Text("Tap to return", color = fg, style = MaterialTheme.typography.labelLarge)
        }
    }
}
