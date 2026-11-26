package io.github.amandhakar.contacts.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Label
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.amandhakar.contacts.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CallerScreenCustomizationScreen(vm: AppViewModel, contactId: Long? = null) {
    val ctx = LocalContext.current
    var backgroundUri by remember(contactId) { mutableStateOf(contactId?.let { vm.prefs.contactCallerBackgroundUri(it) }) }
    var fallbackPhotoUri by remember(contactId) { mutableStateOf(contactId?.let { vm.prefs.contactCallerPhotoUri(it) }) }
    var showCallLabel by remember { mutableStateOf(vm.prefs.callerShowCallLabel) }
    var nameSize by remember { mutableStateOf(vm.prefs.callerNameSizeSp.toFloat()) }
    var accentColor by remember { mutableStateOf(vm.prefs.callerAccentColor) }
    var buttonStyle by remember { mutableStateOf(vm.prefs.callerButtonStyle) }
    var accentMenu by remember { mutableStateOf(false) }
    var buttonMenu by remember { mutableStateOf(false) }

    fun keepUri(uri: Uri): String {
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { }
        return uri.toString()
    }

    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            backgroundUri = keepUri(uri)
            contactId?.let { vm.prefs.setContactCallerBackgroundUri(it, backgroundUri) }
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            fallbackPhotoUri = keepUri(uri)
            contactId?.let { vm.prefs.setContactCallerPhotoUri(it, fallbackPhotoUri) }
        }
    }

    Page(if (contactId == null) "Caller screen customization" else "Caller screen · contact", vm) { pad ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).then(Modifier)
                .padding(pad),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
        ) {
            item {
                Text(
                    if (contactId == null) "Customize the global call-screen layout. Background and profile images are configured separately for each contact." else "Choose images used only when calling this contact. Other contacts keep their own images.",
                    Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SectionLabel("Appearance")
                GroupCard {
                    if (contactId != null) InfoRow(
                        androidx.compose.material.icons.Icons.Filled.Image, "Background image",
                        if (backgroundUri == null) "Use a photo or wallpaper behind call controls" else "Custom background selected",
                        onClick = { backgroundPicker.launch(arrayOf("image/*")) },
                    )
                    if (backgroundUri != null) InfoRow(null, "Remove background", "Return to the original dark gradient", onClick = {
                        backgroundUri = null
                        contactId?.let { vm.prefs.setContactCallerBackgroundUri(it, null) }
                    })
                    if (contactId != null) InfoRow(
                        androidx.compose.material.icons.Icons.Filled.Person, "Caller profile image",
                        if (fallbackPhotoUri == null) "Used when a caller has no contact photo" else "Custom fallback image selected",
                        onClick = { photoPicker.launch(arrayOf("image/*")) },
                    )
                    if (fallbackPhotoUri != null) InfoRow(null, "Remove default image", "Return to generated initials", onClick = {
                        fallbackPhotoUri = null
                        contactId?.let { vm.prefs.setContactCallerPhotoUri(it, null) }
                    })
                }
                if (contactId == null) {
                SectionLabel("Name & number style")
                GroupCard {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Caller name size · ${nameSize.toInt()} sp", style = MaterialTheme.typography.titleSmall)
                        Slider(
                            value = nameSize, onValueChange = { nameSize = it },
                            onValueChangeFinished = { vm.prefs.callerNameSizeSp = nameSize.toInt() },
                            valueRange = 24f..36f,
                        )
                    }
                    InfoRow(
                        androidx.compose.material.icons.Icons.Filled.Palette, "Accent color",
                        "Used for active call controls and highlights",
                        trailing = {
                            Box {
                                OutlinedButton(onClick = { accentMenu = true }) { Text(accentLabel(accentColor)) }
                                DropdownMenu(expanded = accentMenu, onDismissRequest = { accentMenu = false }) {
                                    listOf("#7DFFA0" to "Mint", "#80B8FF" to "Blue", "#D7A0FF" to "Purple", "#FFB86B" to "Amber").forEach { (hex, label) ->
                                        DropdownMenuItem(text = { Text(label) }, onClick = {
                                            accentColor = hex
                                            vm.prefs.callerAccentColor = hex
                                            accentMenu = false
                                        })
                                    }
                                }
                            }
                        },
                    )
                    InfoRow(
                        androidx.compose.material.icons.Icons.Filled.Dashboard, "Button style",
                        "Choose circular or rounded-square controls",
                        trailing = {
                            Box {
                                OutlinedButton(onClick = { buttonMenu = true }) { Text(if (buttonStyle == "rounded") "Rounded" else "Circular") }
                                DropdownMenu(expanded = buttonMenu, onDismissRequest = { buttonMenu = false }) {
                                    DropdownMenuItem(text = { Text("Circular") }, onClick = {
                                        buttonStyle = "circle"; vm.prefs.callerButtonStyle = buttonStyle; buttonMenu = false
                                    })
                                    DropdownMenuItem(text = { Text("Rounded") }, onClick = {
                                        buttonStyle = "rounded"; vm.prefs.callerButtonStyle = buttonStyle; buttonMenu = false
                                    })
                                }
                            }
                        },
                    )
                }
                SectionLabel("Call information")
                GroupCard {
                    InfoRow(
                        androidx.compose.material.icons.Icons.Filled.Label, "Show call category",
                        "Show caller-ID category tags when available",
                        onClick = { showCallLabel = !showCallLabel; vm.prefs.callerShowCallLabel = showCallLabel },
                        trailing = {
                            androidx.compose.material3.Switch(
                                checked = showCallLabel,
                                onCheckedChange = { showCallLabel = it; vm.prefs.callerShowCallLabel = it },
                            )
                        },
                    )
                }
                }
                SectionLabel("Preview")
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(300.dp)
                        .clip(RoundedCornerShape(28.dp)).background(Color(0xFF14231D)),
                ) {
                    CallerScreenBackdrop(backgroundUri)
                    Column(
                        Modifier.fillMaxSize().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Avatar("Aman Dhaker", fallbackPhotoUri, 88.dp)
                        Spacer(Modifier.height(14.dp))
                        Text("Aman Dhaker", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontSize = nameSize.sp)
                        Text("9660617067", style = MaterialTheme.typography.bodyLarge, color = Color(0xFFD0D8D3))
                        if (showCallLabel) {
                            androidx.compose.material3.Surface(
                                color = Color(0xFF2E8B45), shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.padding(top = 10.dp),
                            ) { Text("Personal", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = Color.White) }
                        }
                        Text("Calling…", Modifier.padding(top = 14.dp), color = Color.White)
                        Text("●     ⠿     ◖", Modifier.padding(top = 28.dp), color = Color.White)
                    }
                }
                androidx.compose.material3.TextButton(
                    onClick = {
                        backgroundUri = null
                        fallbackPhotoUri = null
                        contactId?.let {
                            vm.prefs.setContactCallerBackgroundUri(it, null)
                            vm.prefs.setContactCallerPhotoUri(it, null)
                        } ?: run {
                            showCallLabel = true
                            nameSize = 30f
                            accentColor = "#7DFFA0"
                            buttonStyle = "circle"
                            vm.prefs.callerShowCallLabel = true
                            vm.prefs.callerNameSizeSp = 30
                            vm.prefs.callerAccentColor = "#7DFFA0"
                            vm.prefs.callerButtonStyle = "circle"
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text(if (contactId == null) "Reset appearance settings" else "Reset images for this contact") }
            }
        }
    }
}

/** Optional wallpaper layer for the live call UI. Invalid or revoked image URIs fall back safely. */
@Composable
fun CallerScreenBackdrop(uri: String?) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = if (uri.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) { null }
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
