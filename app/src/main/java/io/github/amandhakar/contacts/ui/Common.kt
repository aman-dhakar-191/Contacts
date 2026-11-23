package io.github.amandhakar.contacts.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ContactsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private val avatarColors = listOf(0xFF1B6B4A, 0xFF3F51B5, 0xFF8E24AA, 0xFFD84315, 0xFF00838F, 0xFF6D4C41, 0xFFC2185B, 0xFF455A64)

private val photoCache = object : android.util.LruCache<String, Bitmap>(80) {}

@Composable
fun Avatar(name: String, photoUri: String?, size: Dp = 44.dp) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(photoCache.get(photoUri ?: ""), photoUri) {
        if (photoUri == null) { value = null; return@produceState }
        photoCache.get(photoUri)?.let { value = it; return@produceState }
        value = withContext(Dispatchers.IO) {
            try {
                ctx.contentResolver.openInputStream(Uri.parse(photoUri))?.use { BitmapFactory.decodeStream(it) }
                    ?.also { photoCache.put(photoUri, it) }
            } catch (_: Exception) {
                null
            }
        }
    }
    val letter = name.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(avatarColors[(name.hashCode() and 0x7fffffff) % avatarColors.size])),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.size(size), contentScale = ContentScale.Crop)
        } else {
            Text(letter, color = Color.White, fontSize = (size.value * 0.42f).sp)
        }
    }
}
