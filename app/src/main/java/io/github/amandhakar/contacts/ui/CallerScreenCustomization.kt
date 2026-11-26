@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.amandhakar.contacts.ui

import java.io.File
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.amandhakar.contacts.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

private enum class CustomizationTab { Appearance, Advanced }

@Composable
fun CallerScreenCustomizationScreen(vm: AppViewModel, contactId: Long? = null) {
    val ctx = LocalContext.current
    var backgroundUri by remember(contactId) { mutableStateOf(if (contactId != null) vm.prefs.contactCallerBackgroundUri(contactId) else vm.prefs.callerBackgroundUri) }
    var fallbackPhotoUri by remember(contactId) { mutableStateOf(if (contactId != null) vm.prefs.contactCallerPhotoUri(contactId) else vm.prefs.callerFallbackPhotoUri) }
    var showCallLabel by remember { mutableStateOf(vm.prefs.callerShowCallLabel) }
    var nameSize by remember { mutableStateOf(vm.prefs.callerNameSizeSp.toFloat()) }
    var accentColor by remember { mutableStateOf(vm.prefs.callerAccentColor) }
    var buttonStyle by remember { mutableStateOf(vm.prefs.callerButtonStyle) }
    var tab by remember { mutableStateOf(CustomizationTab.Appearance) }
    var accentMenu by remember { mutableStateOf(false) }
    var buttonMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun copyImageIntoApp(uri: Uri, kind: String): String? = withContext(Dispatchers.IO) {
        try {
            val directory = File(ctx.filesDir, "caller-images").apply { mkdirs() }
            val target = File(directory, if (contactId == null) "global-$kind.img" else "contact-$contactId-$kind.img")
            val temporary = File(directory, target.name + ".tmp")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            if (target.exists()) target.delete()
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            Uri.fromFile(target).toString()
        } catch (_: Exception) { null }
    }

    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val saved = copyImageIntoApp(uri, "background")
            if (saved != null) {
                backgroundUri = saved
                if (contactId != null) vm.prefs.setContactCallerBackgroundUri(contactId, saved)
                else vm.prefs.callerBackgroundUri = saved
            }
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val saved = copyImageIntoApp(uri, "photo")
            if (saved != null) {
                fallbackPhotoUri = saved
                if (contactId != null) vm.prefs.setContactCallerPhotoUri(contactId, saved)
                else vm.prefs.callerFallbackPhotoUri = saved
            }
        }
    }

    Page(if (contactId == null) "Caller screen customization" else "Caller screen customization", vm) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(pad),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                CallerMiniPreview(
                    backgroundUri = backgroundUri,
                    photoUri = fallbackPhotoUri,
                    nameSize = nameSize,
                    showCallLabel = showCallLabel,
                )
            }
            item {
                TabRow(selectedTabIndex = tab.ordinal, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    CustomizationTab.values().forEach { item ->
                        Tab(selected = tab == item, onClick = { tab = item }, text = { Text(item.name) })
                    }
                }
            }
            if (tab == CustomizationTab.Appearance) {
                item {
                    Text("Personalize the look of your caller screen.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CustomizationGroup {
                        CustomizationRow(Icons.Filled.Image, "Background", if (backgroundUri == null) "Set a custom image or wallpaper" else "Custom wallpaper selected") {
                            backgroundPicker.launch(arrayOf("image/*"))
                        }
                        CustomizationDivider()
                        CustomizationRow(Icons.Filled.Person, "Contact image", if (fallbackPhotoUri == null) "Choose the photo shown on the caller screen" else "Custom caller image selected") {
                            photoPicker.launch(arrayOf("image/*"))
                        }
                    }
                    if (backgroundUri != null || fallbackPhotoUri != null) {
                        TextButton(onClick = {
                            backgroundUri = null
                            fallbackPhotoUri = null
                            if (contactId != null) {
                                vm.prefs.setContactCallerBackgroundUri(contactId, null)
                                vm.prefs.setContactCallerPhotoUri(contactId, null)
                            } else {
                                vm.prefs.callerBackgroundUri = null
                                vm.prefs.callerFallbackPhotoUri = null
                            }
                        }) { Icon(Icons.Filled.RestartAlt, null); Text("Reset contact images") }
                    }
                    if (contactId == null) {
                        Text("Images are saved separately for each contact from that contact's details.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("These images apply only to this contact. Other contacts are unchanged.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                item {
                    Text("These options apply globally to all calls.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CustomizationGroup {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.TextFields, tint = MaterialTheme.colorScheme.primary, contentDescription = null)
                                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                    Text("Name & number style", style = MaterialTheme.typography.titleMedium)
                                    Text("Caller name size · ${nameSize.toInt()} sp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Slider(value = nameSize, onValueChange = { nameSize = it }, onValueChangeFinished = { vm.prefs.callerNameSizeSp = nameSize.toInt() }, valueRange = 24f..36f)
                        }
                        CustomizationDivider()
                        CustomizationRow(Icons.Filled.Label, "Call info tag", "Show or hide caller category labels") {
                            showCallLabel = !showCallLabel
                            vm.prefs.callerShowCallLabel = showCallLabel
                        }
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.weight(1f))
                            Switch(checked = showCallLabel, onCheckedChange = { showCallLabel = it; vm.prefs.callerShowCallLabel = it })
                        }
                        CustomizationDivider()
                        CustomizationRow(Icons.Filled.Dashboard, "Button style", "Choose circular or rounded-square controls") {
                            buttonMenu = true
                        }
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
                            OutlinedButton(onClick = { buttonMenu = true }) { Text(if (buttonStyle == "rounded") "Rounded" else "Circular") }
                            DropdownMenu(expanded = buttonMenu, onDismissRequest = { buttonMenu = false }) {
                                DropdownMenuItem(text = { Text("Circular") }, onClick = { buttonStyle = "circle"; vm.prefs.callerButtonStyle = buttonStyle; buttonMenu = false })
                                DropdownMenuItem(text = { Text("Rounded") }, onClick = { buttonStyle = "rounded"; vm.prefs.callerButtonStyle = buttonStyle; buttonMenu = false })
                            }
                        }
                        CustomizationDivider()
                        CustomizationRow(Icons.Filled.ColorLens, "Color & theme", "Accent color for controls and highlights") {
                            accentMenu = true
                        }
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
                            OutlinedButton(onClick = { accentMenu = true }) { Text(accentLabel(accentColor)) }
                            DropdownMenu(expanded = accentMenu, onDismissRequest = { accentMenu = false }) {
                                listOf("#7DFFA0" to "Mint", "#80B8FF" to "Blue", "#D7A0FF" to "Purple", "#FFB86B" to "Amber").forEach { (hex, label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = { accentColor = hex; vm.prefs.callerAccentColor = hex; accentMenu = false })
                                }
                            }
                        }
                    }
                    TextButton(onClick = {
                        showCallLabel = true
                        nameSize = 30f
                        accentColor = "#7DFFA0"
                        buttonStyle = "circle"
                        vm.prefs.callerShowCallLabel = true
                        vm.prefs.callerNameSizeSp = 30
                        vm.prefs.callerAccentColor = "#7DFFA0"
                        vm.prefs.callerButtonStyle = "circle"
                    }) { Icon(Icons.Filled.RestartAlt, null); Text("Reset advanced settings") }
                }
            }
        }
    }
}

@Composable
private fun CustomizationGroup(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) { Column(content = { content() }) }
}

@Composable
private fun CustomizationDivider() {
    androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
}

@Composable
private fun CustomizationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CallerMiniPreview(
    backgroundUri: String?,
    photoUri: String?,
    nameSize: Float,
    showCallLabel: Boolean,
) {
    Box(
        Modifier.fillMaxWidth().height(236.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xFF14231D)),
        contentAlignment = Alignment.Center,
    ) {
        CallerScreenBackdrop(backgroundUri)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Avatar("Aman Dhaker", photoUri, 64.dp)
            Spacer(Modifier.height(8.dp))
            Text("Aman Dhaker", color = Color.White, fontSize = (nameSize * 0.72f).sp, fontWeight = FontWeight.Medium)
            Text("9660617067", color = Color(0xFFD0D8D3), style = MaterialTheme.typography.bodySmall)
            if (showCallLabel) {
                Surface(color = Color(0xFF2E8B45), shape = RoundedCornerShape(20.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Text("Personal", Modifier.padding(horizontal = 14.dp, vertical = 5.dp), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            Text("Calling…", Modifier.padding(top = 8.dp), color = Color.White)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                listOf("●", "⠿", "◖").forEach { Text(it, color = Color.White, style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

@Composable
fun CallerScreenBackdrop(uri: String?) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = if (uri.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            try { context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }
        }
    }
    val image = bitmap
    if (image != null) {
        androidx.compose.foundation.Image(image.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color(0x9908100D)))
    }
}

private fun accentLabel(hex: String): String = when (hex) {
    "#80B8FF" -> "Blue"
    "#D7A0FF" -> "Purple"
    "#FFB86B" -> "Amber"
    else -> "Mint"
}
