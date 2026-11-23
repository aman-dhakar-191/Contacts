@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.amandhakar.contacts.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.amandhakar.contacts.AppViewModel
import io.github.amandhakar.contacts.DISPLAY_ALL
import io.github.amandhakar.contacts.data.AccountInfo

private fun people(n: Int) = if (n == 1) "1 person" else "$n people"

/** One tap picks, a check marks the choice. Same idea as the system "Contacts to display" screen. */
@Composable
private fun ChoiceCard(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title, style = MaterialTheme.typography.titleMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Filled.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Which accounts' contacts the app shows, and which account new contacts are saved to (and so sync with). */
@Composable
fun AccountsScreen(vm: AppViewModel) {
    val accounts by vm.accounts.collectAsState()
    val total by vm.totalPeople.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val r = refresh + vm.speedDialVersion + accounts.size

    val choices = vm.choiceAccounts()
    val display = vm.displayChoice()
    val target = vm.defaultAccount() ?: AccountInfo(null, null)

    fun title(a: AccountInfo) = if (a.name == null) "Phone (${people(a.count)})" else "Google (${people(a.count)})"
    fun subtitle(a: AccountInfo) = a.name ?: "Stored on this phone only"

    Page("Accounts", vm) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionLabel("Contacts to display")
            ChoiceCard("All contacts (${people(total)})", "Every account on this phone", display == DISPLAY_ALL) { vm.showOnly(null); refresh++ }
            choices.forEach { a ->
                ChoiceCard(title(a), subtitle(a), display == a.key) { vm.showOnly(a); refresh++ }
            }
            Text(
                "This only changes what this app shows. Nothing is deleted and the accounts keep syncing. " +
                    "Names still appear for calls from contacts you hide.",
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionLabel("Save new contacts to")
            choices.forEach { a ->
                ChoiceCard(
                    title(a), if (a.name == null) "Stays on this phone, not backed up" else "${a.name} · syncs to Google",
                    target.key == a.key,
                ) { vm.setDefaultAccount(a); refresh++ }
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
