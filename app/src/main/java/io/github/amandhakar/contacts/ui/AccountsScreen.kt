@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.amandhakar.contacts.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.amandhakar.contacts.AppViewModel
import io.github.amandhakar.contacts.data.AccountInfo

/** Which accounts' contacts the app shows, and which account new contacts are saved to (and so sync with). */
@Composable
fun AccountsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val accounts by vm.accounts.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val r = refresh + vm.speedDialVersion

    // Accounts a new contact can be saved to: Google accounts, and phone-only.
    val targets = buildList {
        accounts.filter { it.isGoogle }.forEach { add(it) }
        add(AccountInfo(null, null))
    }
    val current = vm.defaultAccount()

    Page("Accounts", vm) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionLabel("Show contacts from")
            GroupCard {
                accounts.forEach { a ->
                    InfoRow(
                        if (a.name == null) Icons.Filled.Phone else Icons.Filled.AccountCircle,
                        a.title,
                        (if (a.count == 1) "1 contact" else "${a.count} contacts") + if (!a.isGoogle && a.name != null) " · read-only source" else "",
                        onClick = {
                            if (!vm.setAccountShown(a, !vm.isAccountShown(a))) Toast.makeText(ctx, "Show at least one account", Toast.LENGTH_SHORT).show()
                            refresh++
                        },
                        trailing = {
                            Switch(vm.isAccountShown(a), {
                                if (!vm.setAccountShown(a, it)) Toast.makeText(ctx, "Show at least one account", Toast.LENGTH_SHORT).show()
                                refresh++
                            })
                        },
                    )
                }
            }
            Text(
                "Hiding an account only changes what this app shows. Nothing is deleted, and the account keeps syncing. " +
                    "Names still appear for calls from hidden contacts.",
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionLabel("Save new contacts to")
            GroupCard {
                targets.forEach { a ->
                    InfoRow(
                        if (a.name == null) Icons.Filled.Phone else Icons.Filled.AccountCircle,
                        a.title,
                        if (a.name == null) "Stays on this phone, not backed up" else "Syncs to this Google account",
                        onClick = { vm.setDefaultAccount(a); refresh++ },
                        trailing = { RadioButton(selected = current?.key == a.key || (current == null && a.name == null), onClick = { vm.setDefaultAccount(a); refresh++ }) },
                    )
                }
            }
            Text(
                "Used for new contacts, \"Add to contacts\" and .vcf imports. You can still pick another account on the new-contact " +
                    "screen. Existing contacts keep syncing with the account they came from.",
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
