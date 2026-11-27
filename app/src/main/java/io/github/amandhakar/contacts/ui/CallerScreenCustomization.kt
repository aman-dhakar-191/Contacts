@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.amandhakar.contacts.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.amandhakar.contacts.AppViewModel
import io.github.amandhakar.contacts.data.ContactDetail
import io.github.amandhakar.contacts.util.CallerImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class CustomizationTab(val label: String) { Appearance("Images"), Advanced("Style") }

/** The accent colours offered for the call screen. The first is the default. */
private val accentChoices = listOf("#7DFFA0" to "Mint", "#80B8FF" to "Blue", "#D7A0FF" to "Purple", "#FFB86B" to "Amber", "#FF8A80" to "Coral")
private const val DEFAULT_ACCENT = "#7DFFA0"
private const val DEFAULT_NAME_SIZE = 30

/** The saved accent as a colour, falling back to mint when it cannot be read. */
fun callerAccentColor(hex: String?): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex ?: DEFAULT_ACCENT)) }.getOrDefault(Color(0xFF7DFFA0))

/**
 * Caller screen settings. With [contactId] the images are only for that contact; without it they are the
 * wallpaper and default caller image for everyone. The style options are always for every call.
 */
@Composable
fun CallerScreenCustomizationScreen(vm: AppViewModel, contactId: Long? = null) {
    val ctx = LocalContext.current
    val prefs = vm.prefs
    val contact by produceState<ContactDetail?>(null, contactId) {
        value = contactId?.let { id -> withContext(Dispatchers.IO) { runCatching { vm.contactsRepo.detail(id) }.getOrNull() } }
    }
    var backgroundUri by remember(contactId) { mutableStateOf(if (contactId != null) prefs.contactCallerBackgroundUri(contactId) else prefs.callerBackgroundUri) }
    var photoUri by remember(contactId) { mutableStateOf(if (contactId != null) prefs.contactCallerPhotoUri(contactId) else prefs.callerFallbackPhotoUri) }
    var showCallLabel by remember { mutableStateOf(prefs.callerShowCallLabel) }
    var nameSize by remember { mutableStateOf(prefs.callerNameSizeSp.toFloat()) }
    var accentHex by remember { mutableStateOf(prefs.callerAccentColor) }
    var buttonStyle by remember { mutableStateOf(prefs.callerButtonStyle) }
    var tab by remember { mutableStateOf(CustomizationTab.Appearance) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun saveBackground(uri: String?) {
        backgroundUri = uri
        if (contactId != null) prefs.setContactCallerBackgroundUri(contactId, uri) else prefs.callerBackgroundUri = uri
    }
    fun savePhoto(uri: String?) {
        photoUri = uri
        if (contactId != null) prefs.setContactCallerPhotoUri(contactId, uri) else prefs.callerFallbackPhotoUri = uri
    }
    fun pick(source: Uri, kind: String, maxSide: Int, save: (String) -> Unit) {
        busy = true
        scope.launch {
            val saved = withContext(Dispatchers.IO) { CallerImages.import(ctx, source, CallerImages.slot(contactId, kind), maxSide) }
            busy = false
            if (saved != null) save(saved) else Toast.makeText(ctx, "Couldn't open that image. Try another one.", Toast.LENGTH_LONG).show()
        }
    }

    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pick(uri, "background", CallerImages.BACKGROUND_MAX) { saveBackground(it) }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pick(uri, "photo", CallerImages.PHOTO_MAX) { savePhoto(it) }
    }

    // The preview shows what a call will really look like, including what falls back from Settings.
    val previewBackground = backgroundUri ?: if (contactId != null) prefs.callerBackgroundUri else null
    val previewPhoto = photoUri ?: if (contactId != null) (contact?.photoUri ?: prefs.callerFallbackPhotoUri) else null
    val title = contact?.name?.let { "Caller screen · $it" } ?: "Caller screen"

    Page(title, vm) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(pad),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                CallerMiniPreview(
                    name = contact?.name ?: "Caller name",
                    backgroundUri = previewBackground,
                    photoUri = previewPhoto,
                    nameSize = nameSize,
                    showCallLabel = showCallLabel,
                    accent = callerAccentColor(accentHex),
                    rounded = buttonStyle == "rounded",
                )
            }
            item {
                TabRow(selectedTabIndex = tab.ordinal, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    CustomizationTab.values().forEach { item ->
                        Tab(selected = tab == item, onClick = { tab = item }, text = { Text(item.label) })
                    }
                }
            }
            if (tab == CustomizationTab.Appearance) {
                item {
                    Text(
                        if (contactId == null) "Used on every call. A contact's own images, set from their contact page, take their place."
                        else "Only for calls with ${contact?.name ?: "this contact"}. Anything left unset uses what's in Settings.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    CustomizationGroup {
                        CustomizationRow(
                            Icons.Filled.Image, "Wallpaper",
                            when {
                                busy -> "Saving…"
                                backgroundUri != null -> "Custom wallpaper set"
                                else -> "Choose a picture for behind the call screen"
                            },
                            onClick = { if (!busy) backgroundPicker.launch(arrayOf("image/*")) },
                        ) {
                            if (backgroundUri != null) TextButton(onClick = {
                                saveBackground(null)
                                CallerImages.delete(ctx, CallerImages.slot(contactId, "background"))
                            }) { Text("Remove") }
                        }
                        CustomizationDivider()
                        CustomizationRow(
                            Icons.Filled.Person, if (contactId == null) "Default caller image" else "Caller photo",
                            when {
                                busy -> "Saving…"
                                photoUri != null -> "Custom image set"
                                contactId == null -> "Shown for callers who have no photo"
                                else -> "Shown instead of their contact photo"
                            },
                            onClick = { if (!busy) photoPicker.launch(arrayOf("image/*")) },
                        ) {
                            if (photoUri != null) TextButton(onClick = {
                                savePhoto(null)
                                CallerImages.delete(ctx, CallerImages.slot(contactId, "photo"))
                            }) { Text("Remove") }
                        }
                    }
                }
            } else {
                item {
                    Text("These apply to every call.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    CustomizationGroup {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.TextFields, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                    Text("Name size", style = MaterialTheme.typography.titleSmall)
                                    Text("${nameSize.toInt()} sp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Slider(
                                value = nameSize, onValueChange = { nameSize = it },
                                onValueChangeFinished = { prefs.callerNameSizeSp = nameSize.toInt() },
                                valueRange = 24f..36f, steps = 11,
                            )
                        }
                        CustomizationDivider()
                        CustomizationRow(
                            Icons.Filled.Label, "Category label", "Show the caller's category, such as Work or Spam",
                            onClick = { showCallLabel = !showCallLabel; prefs.callerShowCallLabel = showCallLabel },
                        ) { Switch(checked = showCallLabel, onCheckedChange = { showCallLabel = it; prefs.callerShowCallLabel = it }) }
                        CustomizationDivider()
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Dashboard, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("Button shape", Modifier.padding(start = 16.dp), style = MaterialTheme.typography.titleSmall)
                            }
                            Row(Modifier.padding(start = 42.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("circle" to "Circle", "rounded" to "Rounded square").forEach { (key, label) ->
                                    FilterChip(
                                        selected = buttonStyle == key,
                                        onClick = { buttonStyle = key; prefs.callerButtonStyle = key },
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }
                        CustomizationDivider()
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.ColorLens, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.padding(start = 16.dp)) {
                                    Text("Accent colour", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        accentChoices.firstOrNull { it.first.equals(accentHex, ignoreCase = true) }?.second ?: "Mint",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Row(Modifier.padding(start = 42.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                accentChoices.forEach { (hex, label) ->
                                    val selected = hex.equals(accentHex, ignoreCase = true)
                                    Box(
                                        Modifier.size(36.dp).clip(CircleShape).background(callerAccentColor(hex))
                                            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                            .clickable(onClickLabel = label) { accentHex = hex; prefs.callerAccentColor = hex },
                                        contentAlignment = Alignment.Center,
                                    ) { if (selected) Icon(Icons.Filled.Check, label, tint = Color(0xFF0B100E), modifier = Modifier.size(20.dp)) }
                                }
                            }
                        }
                    }
                    TextButton(onClick = {
                        showCallLabel = true; nameSize = DEFAULT_NAME_SIZE.toFloat(); accentHex = DEFAULT_ACCENT; buttonStyle = "circle"
                        prefs.callerShowCallLabel = true
                        prefs.callerNameSizeSp = DEFAULT_NAME_SIZE
                        prefs.callerAccentColor = DEFAULT_ACCENT
                        prefs.callerButtonStyle = "circle"
                    }) { Icon(Icons.Filled.RestartAlt, null); Spacer(Modifier.size(6.dp)); Text("Reset style") }
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
    ) { Column { content() } }
}

@Composable
private fun CustomizationDivider() {
    androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
}

@Composable
private fun CustomizationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun CallerMiniPreview(
    name: String,
    backgroundUri: String?,
    photoUri: String?,
    nameSize: Float,
    showCallLabel: Boolean,
    accent: Color,
    rounded: Boolean,
) {
    Box(
        Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(26.dp))
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF16261F), Color(0xFF0B100E)))),
        contentAlignment = Alignment.Center,
    ) {
        CallerScreenBackdrop(backgroundUri)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Avatar(name, photoUri, 64.dp)
            Spacer(Modifier.height(8.dp))
            Text(name, color = Color.White, fontSize = (nameSize * 0.72f).sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text("Mobile", color = Color(0xFFD0D8D3), style = MaterialTheme.typography.bodySmall)
            if (showCallLabel) {
                Surface(color = Color(0xFF2E8B45), shape = RoundedCornerShape(20.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Text("Personal", Modifier.padding(horizontal = 14.dp, vertical = 5.dp), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            Text("02:14", Modifier.padding(top = 8.dp), color = Color.White)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PreviewButton(Icons.Filled.MicOff, on = true, accent = accent, rounded = rounded)
                PreviewButton(Icons.Filled.Dialpad, on = false, accent = accent, rounded = rounded)
                PreviewButton(Icons.Filled.Pause, on = false, accent = accent, rounded = rounded)
            }
        }
    }
}

@Composable
private fun PreviewButton(icon: ImageVector, on: Boolean, accent: Color, rounded: Boolean) {
    Box(
        Modifier.size(40.dp).clip(if (rounded) RoundedCornerShape(12.dp) else CircleShape).background(if (on) accent else Color(0xFF26352E)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (on) Color(0xFF0B100E) else Color.White, modifier = Modifier.size(20.dp)) }
}

/** The caller-screen wallpaper with a dark scrim so white text stays readable. Draws nothing when there is no image. */
@Composable
fun CallerScreenBackdrop(uri: String?) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = if (uri.isNullOrBlank()) null else withContext(Dispatchers.IO) { CallerImages.decode(context, uri, CallerImages.BACKGROUND_MAX) }
    }
    val image = bitmap
    if (image != null) {
        androidx.compose.foundation.Image(image.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color(0x9908100D)))
    }
}
