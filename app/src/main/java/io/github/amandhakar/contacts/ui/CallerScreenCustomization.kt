package io.github.amandhakar.contacts.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
import io.github.amandhakar.contacts.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CallerScreenCustomizationScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var backgroundUri by remember { mutableStateOf(vm.prefs.callerBackgroundUri) }
    var fallbackPhotoUri by remember { mutableStateOf(vm.prefs.callerFallbackPhotoUri) }
    var showCallLabel by remember { mutableStateOf(vm.prefs.callerShowCallLabel) }

    fun keepUri(uri: Uri): String {
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { }
        return uri.toString()
    }

    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            backgroundUri = keepUri(uri)
            vm.prefs.callerBackgroundUri = backgroundUri
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            fallbackPhotoUri = keepUri(uri)
            vm.prefs.callerFallbackPhotoUri = fallbackPhotoUri
        }
    }

    Page("Caller screen customization", vm) { pad ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).then(Modifier)
                .padding(pad),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
        ) {
            item {
                Text(
                    "Personalize the screen shown while calls are ringing or connected. Changes are optional; your current layout stays the default until you choose something.",
                    Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SectionLabel("Appearance")
                GroupCard {
                    InfoRow(
                        androidx.compose.material.icons.Icons.Filled.Image, "Background image",
                        if (backgroundUri == null) "Use a photo or wallpaper behind call controls" else "Custom background selected",
                        onClick = { backgroundPicker.launch(arrayOf("image/*")) },
                    )
                    if (backgroundUri != null) InfoRow(null, "Remove background", "Return to the original dark gradient", onClick = {
                        backgroundUri = null
                        vm.prefs.callerBackgroundUri = null
                    })
                    InfoRow(
                        androidx.compose.material.icons.Icons.Filled.Person, "Default contact image",
                        if (fallbackPhotoUri == null) "Used when a caller has no contact photo" else "Custom fallback image selected",
                        onClick = { photoPicker.launch(arrayOf("image/*")) },
                    )
                    if (fallbackPhotoUri != null) InfoRow(null, "Remove default image", "Return to generated initials", onClick = {
                        fallbackPhotoUri = null
                        vm.prefs.callerFallbackPhotoUri = null
                    })
                }
                SectionLabel("Call information")
                GroupCard {
                    InfoRow(
                        androidx.compose.material.icons.Icons.Filled.Label, "Show call category",
                        "Show labels such as Personal or Work when available",
                        onClick = { showCallLabel = !showCallLabel; vm.prefs.callerShowCallLabel = showCallLabel },
                        trailing = {
                            androidx.compose.material3.Switch(
                                checked = showCallLabel,
                                onCheckedChange = { showCallLabel = it; vm.prefs.callerShowCallLabel = it },
                            )
                        },
                    )
                }
                SectionLabel("Preview")
                GroupCard {
                    Box(Modifier.fillMaxSize().padding(16.dp)) {
                        Text(
                            "Aman Dhaker\n9660617067\nCalling…",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                androidx.compose.material3.TextButton(
                    onClick = {
                        backgroundUri = null
                        fallbackPhotoUri = null
                        showCallLabel = true
                        vm.prefs.callerBackgroundUri = null
                        vm.prefs.callerFallbackPhotoUri = null
                        vm.prefs.callerShowCallLabel = true
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("Reset all customizations") }
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
        Image(image.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color(0x9908100D)))
    }
}
