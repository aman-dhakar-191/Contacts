@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.amandhakar.contacts.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.amandhakar.contacts.AppViewModel
import io.github.amandhakar.contacts.Screen
import io.github.amandhakar.contacts.data.Prefs
import io.github.amandhakar.contacts.identifier.Category
import io.github.amandhakar.contacts.identifier.CondType
import io.github.amandhakar.contacts.identifier.Condition
import io.github.amandhakar.contacts.identifier.Engine
import io.github.amandhakar.contacts.identifier.LogFilter
import io.github.amandhakar.contacts.identifier.LogRow
import io.github.amandhakar.contacts.identifier.Rule
import io.github.amandhakar.contacts.identifier.Source
import io.github.amandhakar.contacts.identifier.Store
import io.github.amandhakar.contacts.identifier.Via
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private val SWATCHES = listOf(0xFFD32F2F, 0xFF1976D2, 0xFFF57C00, 0xFF388E3C, 0xFF7B1FA2, 0xFF00838F, 0xFF5D4037, 0xFF455A64).map { it.toInt() }

/** A small coloured label, e.g. "Spam". */
@Composable
fun CategoryChip(name: String, color: Int, modifier: Modifier = Modifier) {
    val c = Color(color)
    Surface(modifier, shape = RoundedCornerShape(8.dp), color = c.copy(alpha = 0.18f)) {
        Text(name, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = c, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** The Identifier section: master switch, rules, categories, manual flags and the processing log. */
@Composable
fun IdentifierScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val prefs = remember { Prefs(ctx) }
    var on by remember { mutableStateOf(prefs.identifierOn) }
    var tick by remember { mutableIntStateOf(0) }
    val rules = remember(tick) { store.rules() }
    val cats = remember(tick) { store.categories() }
    val flags = remember(tick) { store.flagRows() }
    val hits = remember(tick) { store.ruleHits() }
    val wrong = remember(tick) { store.wrongCounts() }
    val logCount = remember(tick) { store.logCount() }
    val correctionCount = remember(tick) { store.correctionCount() }
    val catById = cats.associateBy { it.id }
    var editing by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }

    fun changed() { tick++; vm.reload() }

    Page("Identifier", vm) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            GroupCard(Modifier.padding(top = 8.dp)) {
                InfoRow(
                    Icons.Filled.Shield, "Identifier",
                    if (on) "On · ${rules.count { it.enabled }} of ${rules.size} rules on · ${flags.size} flagged numbers" else "Off: nothing is categorised or logged",
                    onClick = { on = !on; prefs.identifierOn = on; changed() },
                    trailing = { Switch(on, { on = it; prefs.identifierOn = it; changed() }) },
                )
            }
            Text(
                "Puts calls into categories such as Spam or Delivery, in the call history, on the call screen and in the incoming-call " +
                    "notification. A number you flag by hand always wins; otherwise the first rule that matches decides. " +
                    "Everything here stays on this phone.",
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionLabel("Rules (first match wins)")
            GroupCard {
                if (rules.isEmpty()) InfoRow(null, "No rules yet")
                rules.forEachIndexed { i, r ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.push(Screen.IdentifierRule(r.id)) }.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(r.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                catById[r.categoryId]?.let { CategoryChip(it.name, it.color, Modifier.padding(start = 8.dp)) }
                            }
                            Text(r.describe(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            val n = hits[r.id] ?: 0
                            val w = wrong[r.id] ?: 0
                            Text(
                                r.error?.let { "Switched off: $it" } ?: (if (n == 0) "Has not matched a call yet" else "Matched $n call${if (n == 1) "" else "s"}") +
                                    (if (w > 0) " · reported wrong for $w number${if (w == 1) "" else "s"}" else ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (r.error != null || w > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { store.moveRule(r.id, -1); changed() }, enabled = i > 0, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = { store.moveRule(r.id, 1); changed() }, enabled = i < rules.lastIndex, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.KeyboardArrowDown, "Move down") }
                        Switch(r.enabled, { store.setRuleEnabled(r.id, it); changed() }, Modifier.padding(start = 4.dp))
                    }
                    if (i < rules.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
                InfoRow(Icons.Filled.Add, "Add rule", onClick = { vm.push(Screen.IdentifierRule(0)) })
            }

            SectionLabel("Categories")
            GroupCard {
                cats.forEach { c ->
                    InfoRow(null, c.name, onClick = { editing = c }, trailing = {
                        Box(Modifier.size(18.dp).clip(CircleShape).background(Color(c.color)))
                    })
                }
                InfoRow(Icons.Filled.Add, "Add category", onClick = { creating = true })
            }

            SectionLabel("Flagged numbers (manual)")
            GroupCard {
                if (flags.isEmpty()) InfoRow(null, "None yet", "Long-press a call in Recents, or open a contact's number menu, and choose Flag as…")
                flags.forEach { f ->
                    val c = catById[f.categoryId]
                    InfoRow(null, f.number, if (c == null) "Never categorised" else null, trailing = {
                        c?.let { CategoryChip(it.name, it.color) }
                        IconButton(onClick = { store.clearFlag(f.number); changed() }) { Icon(Icons.Filled.Close, "Remove flag") }
                    })
                }
            }

            SectionLabel("Processing")
            GroupCard {
                InfoRow(null, "Process call history again", "Runs every rule on every call in the history again, and logs it", onClick = {
                    store.clearResults(); prefs.identifierVersion = prefs.identifierVersion + 1
                    Toast.makeText(ctx, if (on) "Processing the call history" else "Turn the identifier on first", Toast.LENGTH_SHORT).show()
                    changed()
                })
                InfoRow(null, "Processing log", "$logCount records kept on this phone", onClick = { vm.push(Screen.IdentifierLog) })
                InfoRow(null, "Reported wrong", if (correctionCount == 0) "When a category is wrong, report it from the log, Recents or the call screen" else "$correctionCount report${if (correctionCount == 1) "" else "s"} · see which rules are wrong, export as CSV", onClick = { vm.push(Screen.IdentifierCorrections) })
            }
        }
    }

    if (creating) CategoryDialog(null, onDismiss = { creating = false }) { name, color -> store.addCategory(name, color); creating = false; changed() }
    editing?.let { cat ->
        CategoryDialog(cat, onDismiss = { editing = null }, onDelete = {
            store.deleteCategory(cat.id); editing = null; changed()
        }) { name, color -> store.updateCategory(cat.id, name, color); editing = null; changed() }
    }
}

@Composable
private fun CategoryDialog(existing: Category?, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null, onSave: (String, Int) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var color by remember { mutableIntStateOf(existing?.color ?: SWATCHES[0]) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New category" else "Edit category") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SWATCHES.forEach { s ->
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Color(s)).clickable { color = s }
                                .then(if (s == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                        )
                    }
                }
                if (existing != null && onDelete != null) {
                    Spacer(Modifier.height(8.dp))
                    if (confirmDelete) {
                        Text("Rules using it are switched off and its flags are removed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onDelete) { Text("Yes, delete it", color = MaterialTheme.colorScheme.error) }
                    } else {
                        TextButton(onClick = { confirmDelete = true }) { Text("Delete category", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, color) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Add or edit one rule: a name, the category it assigns, and the conditions that must all match. */
@Composable
fun RuleEditScreen(vm: AppViewModel, id: Long) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val cats = remember { store.categories() }
    val existing = remember(id) { if (id == 0L) null else store.rule(id) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var catId by remember { mutableLongStateOf(existing?.categoryId ?: cats.firstOrNull()?.id ?: 0L) }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    val conds = remember { mutableStateListOf<Condition>().also { it.addAll(existing?.conditions ?: listOf(Condition(CondType.PREFIX, ""))) } }
    var problem by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New rule" else "Edit rule") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = {
                        val rule = Rule(id, name.trim(), catId, enabled, existing?.sort ?: 0, conds.toList(), null)
                        problem = when {
                            name.isBlank() -> "Give the rule a name."
                            cats.none { it.id == catId } -> "Add a category first (Identifier → Categories)."
                            else -> Engine.validate(rule)
                        }
                        if (problem == null) { store.saveRule(rule); vm.reload(); vm.pop() }
                    }) { Text("Save") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
            OutlinedTextField(name, { name = it }, label = { Text("Rule name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Text("Then the call is", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cats.forEach { c ->
                    FilterChip(selected = catId == c.id, onClick = { catId = c.id }, label = { Text(c.name) })
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("When all of these are true", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            conds.forEachIndexed { i, c ->
                var menu by remember { mutableStateOf(false) }
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(onClick = { menu = true }, Modifier.fillMaxWidth()) { Text(c.type.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                CondType.entries.forEach { t ->
                                    DropdownMenuItem(text = { Text(t.label) }, onClick = { menu = false; conds[i] = Condition(t, if (t.needsParam) c.param else "") })
                                }
                            }
                        }
                        IconButton(onClick = { if (conds.size > 1) conds.removeAt(i) }, enabled = conds.size > 1) { Icon(Icons.Filled.Close, "Remove condition") }
                    }
                    if (c.type.needsParam) {
                        val err = if (c.param.isNotEmpty()) Engine.paramError(c) else null
                        OutlinedTextField(
                            c.param, { conds[i] = c.copy(param = it) }, singleLine = true, isError = err != null,
                            placeholder = { Text(c.type.hint) }, supportingText = err?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = if (c.type == CondType.REGEX) FontFamily.Monospace else FontFamily.Default),
                        )
                    }
                }
            }
            TextButton(onClick = { conds.add(Condition(CondType.UNKNOWN)) }) { Text("Add condition") }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rule is on", Modifier.weight(1f))
                Switch(enabled, { enabled = it })
            }
            problem?.let { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error) }
            existing?.error?.let { Text("This rule was switched off: $it", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (existing != null) {
                Spacer(Modifier.height(16.dp))
                if (confirmDelete) {
                    Button(onClick = { store.deleteRule(existing.id); vm.reload(); vm.pop() }) { Text("Yes, delete this rule") }
                } else {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete rule", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

/** Every record the identifier has processed, newest first, with which rules were tried and why they did or didn't match. */
@Composable
fun IdentifierLogScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    var filter by remember { mutableStateOf(LogFilter.ALL) }
    var tick by remember { mutableIntStateOf(0) }
    var rows by remember { mutableStateOf<List<LogRow>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(filter, tick) {
        withContext(Dispatchers.IO) { store.log(filter, 300) to store.logCount() }.let { rows = it.first; total = it.second }
    }
    Page("Processing log", vm) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LogFilter.entries.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) }) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$total records, latest ${Store.LOG_KEEP} kept, stored only on this phone",
                            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { confirmClear = true }, enabled = total > 0) { Text("Clear") }
                    }
                }
            }
            if (rows.isEmpty()) item { EmptyState(Icons.Filled.Shield, "Nothing logged", "Records appear here as calls are processed while the identifier is on.") }
            items(rows, key = { it.id }) { r -> LogRowItem(r, vm) }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the log?") },
            text = { Text("This removes the log only. Categories already shown on calls stay until the next processing.") },
            confirmButton = { TextButton(onClick = { store.clearLog(); confirmClear = false; tick++ }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LogRowItem(r: LogRow, vm: AppViewModel) {
    var open by remember(r.id) { mutableStateOf(false) }
    var reporting by remember(r.id) { mutableStateOf(false) }
    val outcome = when {
        r.error != null && r.category == null -> "Problem"
        r.category == null -> "No category"
        r.via == Via.MANUAL -> "${r.category} · manual flag"
        else -> "${r.category} · ${r.ruleName ?: "rule"}"
    }
    Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.name?.takeIf { it.isNotBlank() } ?: r.number.ifBlank { "Hidden number" }, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(r.ts)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            outcome, style = MaterialTheme.typography.bodyMedium,
            color = if (r.error != null) MaterialTheme.colorScheme.error else if (r.category != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${if (r.source == Source.LIVE) "Live call" else "Call history"} · ${r.direction.lowercase()} · ${r.ms} ms" + if (r.number.isNotBlank() && r.name != null) " · ${r.number}" else "",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (r.error != null) Text(r.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        if (open) {
            Spacer(Modifier.height(6.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("Rules tried, in order", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    if (r.trace.isEmpty()) Text("none", style = MaterialTheme.typography.bodySmall)
                    r.trace.forEach { t -> Text("${t.rule}: ${t.result}", style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (r.number.isNotBlank()) {
                TextButton(onClick = { reporting = true }) { Text("This is wrong…") }
            }
        }
    }
    if (reporting) {
        WrongDialog(r.number, r.name, r.category, r.via, r.ruleId, r.ruleName, onChanged = { vm.reload() }, onDismiss = { reporting = false })
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

/** "Flag as…" picker: sets or clears the manual category of a number. */
@Composable
fun FlagDialog(vm: AppViewModel, number: String, name: String?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val cats = remember { store.categories() }
    val current = remember { store.flagOf(number) }
    val on = remember { Prefs(ctx).identifierOn }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Flag ${name ?: number}") },
        text = {
            Column {
                if (!on) Text("The identifier is off, so flags are saved but not used until you turn it on (Settings → Identifier).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (cats.isEmpty()) Text("No categories yet.")
                cats.forEach { c ->
                    Row(Modifier.fillMaxWidth().clickable {
                        if (!store.setFlag(number, c.id)) Toast.makeText(ctx, "That number is too short to flag", Toast.LENGTH_SHORT).show()
                        vm.reload(); onDismiss()
                    }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(Color(c.color)))
                        Text(c.name, Modifier.padding(start = 12.dp).weight(1f))
                        if (current == c.id) Text("flagged", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (cats.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().clickable {
                        store.setFlag(number, Engine.EXEMPT); vm.reload(); onDismiss()
                    }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                        Text("No category (never label this number)", Modifier.padding(start = 12.dp).weight(1f))
                        if (current == Engine.EXEMPT) Text("flagged", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (current != null) TextButton(onClick = { store.clearFlag(number); vm.reload(); onDismiss() }) { Text("Remove flag") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * "This category is wrong": says what it should have been, with an optional note, and keeps that as data (which rule was wrong,
 * for which number). Can also flag the number straight away so it is right from now on.
 */
@Composable
fun WrongDialog(
    number: String, name: String?, wasCategory: String?, wasVia: Via, wasRuleId: Long?, wasRuleName: String?,
    onChanged: () -> Unit, onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val cats = remember { store.categories() }
    var should by remember { mutableStateOf<Category?>(null) }
    var none by remember { mutableStateOf(wasCategory != null) }   // "no category" is the default answer when something was assigned
    var note by remember { mutableStateOf("") }
    var flagNow by remember { mutableStateOf(true) }
    val sameAsWas = !none && should?.name == wasCategory
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wrong for ${name ?: number}?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "It was: " + (wasCategory ?: "no category") + when (wasVia) {
                        Via.MANUAL -> " (flagged by you)"
                        Via.RULE -> wasRuleName?.let { " (rule: $it)" } ?: ""
                        Via.NONE -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("It should be", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = none, onClick = { none = true; should = null }, label = { Text("No category") })
                    cats.forEach { c -> FilterChip(selected = !none && should?.id == c.id, onClick = { none = false; should = c }, label = { Text(c.name) }) }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().clickable { flagNow = !flagNow }.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Switch(flagNow, { flagNow = it })
                    Text("Also flag this number so it is right from now on", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall)
                }
                if (sameAsWas) Text("That is the same category it already has.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = !sameAsWas && (none || should != null), onClick = {
                store.addCorrection(number, name, wasCategory, wasVia, wasRuleId, wasRuleName, if (none) null else should?.name, note)
                if (flagNow) store.setFlag(number, if (none) Engine.EXEMPT else should!!.id)
                Toast.makeText(ctx, if (flagNow) "Reported and flagged" else "Reported", Toast.LENGTH_SHORT).show()
                onChanged(); onDismiss()
            }) { Text("Report") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** What was reported wrong, grouped by the rule that was wrong, with an export of the reports and of the processing log as CSV. */
@Composable
fun CorrectionsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    var tick by remember { mutableIntStateOf(0) }
    val items = remember(tick) { store.corrections() }
    val rules = remember(tick) { store.rules().associateBy { it.id } }
    val hits = remember(tick) { store.ruleHits() }
    var confirmClear by remember { mutableStateOf(false) }

    fun write(uri: android.net.Uri?, text: String, what: String) {
        if (uri == null) return
        val ok = try { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null } catch (_: Exception) { false }
        Toast.makeText(ctx, if (ok) "Exported $what" else "Export failed", Toast.LENGTH_SHORT).show()
    }
    val exportCorrections = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")) {
        write(it, io.github.amandhakar.contacts.identifier.Export.corrections(store.corrections()), "the reports")
    }
    val exportLog = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")) {
        write(it, io.github.amandhakar.contacts.identifier.Export.log(store.log(LogFilter.ALL, Store.LOG_KEEP)), "the log")
    }

    Page("Reported wrong", vm) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    "Every time you report a wrong category it is kept here with the rule that made it. Use it to see which rules are wrong, " +
                        "then fix or switch them off, or export it and look at the data.",
                    Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val byRule = items.filter { it.wasRuleId != null }.groupBy { it.wasRuleId!! }
            if (byRule.isNotEmpty()) {
                item { SectionLabel("Rules reported wrong") }
                byRule.forEach { (ruleId, list) ->
                    item(key = "rule$ruleId") {
                        val rule = rules[ruleId]
                        val numbers = list.map { it.number }.distinct()
                        val matched = hits[ruleId] ?: 0
                        GroupCard(Modifier.padding(bottom = 8.dp)) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(rule?.name ?: list.first().wasRuleName ?: "Deleted rule", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Wrong for ${numbers.size} number${if (numbers.size == 1) "" else "s"}" +
                                        if (matched > 0) " of $matched matched call${if (matched == 1) "" else "s"} in the log" else "",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                                )
                                if (rule != null) Text(rule.describe(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(numbers.joinToString(", "), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                                Row {
                                    if (rule != null) {
                                        TextButton(onClick = { vm.push(Screen.IdentifierRule(rule.id)) }) { Text("Edit rule") }
                                        if (rule.enabled) TextButton(onClick = { store.setRuleEnabled(rule.id, false); tick++; vm.reload() }) { Text("Switch off") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { SectionLabel("All reports") }
            if (items.isEmpty()) item { EmptyState(Icons.Filled.Shield, "Nothing reported", "Choose “This is wrong…” in the log, in a call's menu in Recents, or tap the category on the call screen.") }
            items(items, key = { "c${it.id}" }) { c ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name?.takeIf { it.isNotBlank() } ?: c.number, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            (c.wasCategory ?: "no category") + "  →  " + (c.shouldCategory ?: "no category") +
                                (c.wasRuleName?.let { " · rule: $it" } ?: if (c.wasVia == Via.MANUAL) " · was a manual flag" else ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (c.note.isNotBlank()) Text(c.note, style = MaterialTheme.typography.bodySmall)
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(c.ts)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { store.deleteCorrection(c.id); tick++ }) { Icon(Icons.Filled.Close, "Delete report") }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportCorrections.launch("identifier-reports.csv") }, Modifier.fillMaxWidth(), enabled = items.isNotEmpty()) { Text("Export reports (CSV)") }
                    OutlinedButton(onClick = { exportLog.launch("identifier-log.csv") }, Modifier.fillMaxWidth()) { Text("Export processing log (CSV)") }
                    TextButton(onClick = { confirmClear = true }, enabled = items.isNotEmpty()) { Text("Delete all reports", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all reports?") },
            text = { Text("Flags you set stay. Only the list of reports is removed.") },
            confirmButton = { TextButton(onClick = { store.clearCorrections(); confirmClear = false; tick++ }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
