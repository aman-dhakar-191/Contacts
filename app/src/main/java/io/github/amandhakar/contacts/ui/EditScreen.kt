@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.amandhakar.contacts.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import io.github.amandhakar.contacts.AppViewModel
import io.github.amandhakar.contacts.data.AccountInfo
import io.github.amandhakar.contacts.data.ContactDetail
import io.github.amandhakar.contacts.data.ContactDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun EditScreen(vm: AppViewModel, id: Long?, prefillNumber: String?) {
    val ctx = LocalContext.current
    val accounts by vm.accounts.collectAsState()
    val groups by vm.groups.collectAsState()

    var loaded by remember { mutableStateOf(id == null) }
    var detail by remember { mutableStateOf<ContactDetail?>(null) }
    var name by remember { mutableStateOf("") }
    val phones = remember { mutableStateListOf<Pair<Int, String>>() }
    val emails = remember { mutableStateListOf<String>() }
    var groupIds by remember { mutableStateOf(setOf<Long>()) }
    var account by remember { mutableStateOf<AccountInfo?>(null) }
    var accountMenu by remember { mutableStateOf(false) }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var photoPreview by remember { mutableStateOf<Bitmap?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != null) {
            val d = withContext(Dispatchers.IO) { vm.contactsRepo.detail(id) }
            if (d != null) {
                detail = d; name = d.name
                phones.clear(); phones.addAll(d.phones.map { it.type to it.number })
                emails.clear(); emails.addAll(d.emails)
                groupIds = d.groupIds
            }
            loaded = true
        } else {
            if (!prefillNumber.isNullOrBlank()) phones.add(Phone.TYPE_MOBILE to prefillNumber) else phones.add(Phone.TYPE_MOBILE to "")
            account = vm.defaultAccount()?.let { AccountInfo(it.name, it.type) }
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@rememberLauncherForActivityResult
            val scale = 512f / maxOf(src.width, src.height).coerceAtLeast(1)
            val bmp = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            photoBytes = out.toByteArray(); photoPreview = bmp
        } catch (_: Exception) {
            Toast.makeText(ctx, "Couldn't read that picture", Toast.LENGTH_SHORT).show()
        }
    }

    val effAccount = if (id == null) account else detail?.account
    val availableGroups = groups.filter { it.accountName == effAccount?.name && it.accountType == effAccount?.type }
    val choices = buildList {
        add(AccountInfo(null, null))
        accounts.filter { it.isGoogle }.forEach { add(AccountInfo(it.name, it.type)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == null) "New contact" else "Edit contact") },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    TextButton(
                        enabled = !saving && loaded && (name.isNotBlank() || phones.any { it.second.isNotBlank() }),
                        onClick = {
                            saving = true
                            val draft = ContactDraft(
                                id = id, rawContactId = detail?.rawContactId,
                                name = name.trim().ifBlank { phones.firstOrNull { it.second.isNotBlank() }?.second ?: "" },
                                phones = phones.toList(), emails = emails.toList(), groupIds = groupIds.filter { g -> availableGroups.any { it.id == g } }.toSet(),
                                account = account, photo = photoBytes,
                            )
                            vm.saveContact(draft) { ok ->
                                saving = false
                                if (ok) vm.pop() else Toast.makeText(ctx, "Couldn't save the contact", Toast.LENGTH_LONG).show()
                            }
                        },
                    ) { Text("Save") }
                },
            )
        },
    ) { pad ->
        if (!loaded) return@Scaffold
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pb = photoPreview
                if (pb != null) {
                    Image(pb.asImageBitmap(), null, Modifier.size(72.dp).clip(CircleShape).clickable { pickPhoto.launch("image/*") }, contentScale = ContentScale.Crop)
                } else {
                    Box72(name, detail?.photoUri) { pickPhoto.launch("image/*") }
                }
                TextButton(onClick = { pickPhoto.launch("image/*") }) { Text("Choose photo") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            phones.forEachIndexed { i, (type, num) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        num, { phones[i] = type to it }, label = { Text("Phone") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { phones.removeAt(i) }) { Icon(Icons.Filled.Close, "Remove") }
                }
            }
            TextButton(onClick = { phones.add(Phone.TYPE_MOBILE to "") }) { Text("Add phone") }
            emails.forEachIndexed { i, e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        e, { emails[i] = it }, label = { Text("Email") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { emails.removeAt(i) }) { Icon(Icons.Filled.Close, "Remove") }
                }
            }
            TextButton(onClick = { emails.add("") }) { Text("Add email") }

            if (availableGroups.isNotEmpty()) {
                Text("Labels", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                FlowRow {
                    availableGroups.forEach { g ->
                        FilterChip(
                            selected = g.id in groupIds,
                            onClick = { groupIds = if (g.id in groupIds) groupIds - g.id else groupIds + g.id },
                            label = { Text(g.title) }, modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }
            if (id != null) {
                Spacer(Modifier.height(12.dp))
                Text("Syncs with", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(detail?.account?.title ?: "", Modifier.padding(vertical = 4.dp))
            }
            if (id == null) {
                Spacer(Modifier.height(12.dp))
                Text("Save to", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { accountMenu = true }) { Text(account?.title ?: "Phone only (not synced)") }
                DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                    choices.forEach { a ->
                        DropdownMenuItem(text = { Text(a.title) }, onClick = { account = a; accountMenu = false; groupIds = emptySet() })
                    }
                }
            }
        }
    }
}

@Composable
private fun Box72(name: String, photoUri: String?, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.clickable(onClick = onClick)) { Avatar(name, photoUri, 72.dp) }
}
