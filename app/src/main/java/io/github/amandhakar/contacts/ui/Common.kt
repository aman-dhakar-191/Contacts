package io.github.amandhakar.contacts.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.amandhakar.contacts.data.SwipeAction
import io.github.amandhakar.contacts.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1B6B4A), onPrimary = Color.White,
    primaryContainer = Color(0xFFA0F3C9), onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4D6357), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0E8DA), onSecondaryContainer = Color(0xFF0A1F15),
    tertiary = Color(0xFF3C6472), onTertiary = Color.White,
    background = Color(0xFFF5FBF5), onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF5FBF5), onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DD), onSurfaceVariant = Color(0xFF3F4943),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEFF5EF),
    surfaceContainer = Color(0xFFE9EFE9), surfaceContainerHigh = Color(0xFFE3EAE4), surfaceContainerHighest = Color(0xFFDEE4DE),
    outline = Color(0xFF6F7973), outlineVariant = Color(0xFFBFC9C1),
    error = Color(0xFFBA1A1A), onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF84D7AE), onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF0F5238), onPrimaryContainer = Color(0xFFA0F3C9),
    secondary = Color(0xFFB3CCBF), onSecondary = Color(0xFF1F352A),
    secondaryContainer = Color(0xFF354B40), onSecondaryContainer = Color(0xFFD0E8DA),
    tertiary = Color(0xFFA3CDDD), onTertiary = Color(0xFF053543),
    background = Color(0xFF0F1512), onBackground = Color(0xFFDFE4DE),
    surface = Color(0xFF0F1512), onSurface = Color(0xFFDFE4DE),
    surfaceVariant = Color(0xFF3F4943), onSurfaceVariant = Color(0xFFBFC9C1),
    surfaceContainerLowest = Color(0xFF0A0F0D), surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E), surfaceContainerHigh = Color(0xFF252B28), surfaceContainerHighest = Color(0xFF303632),
    outline = Color(0xFF89938C), outlineVariant = Color(0xFF3F4943),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
)

/** Fixed brand colours (green), light and dark. Not wallpaper colours, so the app looks the same on every phone. */
@Composable
fun ContactsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme, content = content)
}

private val avatarColors = listOf(0xFF1B6B4A, 0xFF3F51B5, 0xFF8E24AA, 0xFFD84315, 0xFF00838F, 0xFF6D4C41, 0xFFC2185B, 0xFF455A64)

private val photoCache = object : android.util.LruCache<String, Bitmap>(120) {}

@Composable
fun Avatar(name: String, photoUri: String?, size: Dp = 48.dp) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(photoCache.get(photoUri ?: ""), photoUri) {
        if (photoUri == null) { value = null; return@produceState }
        photoCache.get(photoUri)?.let { value = it; return@produceState }
        value = withContext(Dispatchers.IO) {
            try {
                // Scaled while decoding, so a large custom caller photo cannot fill memory (Contacts thumbnails are already small).
                io.github.amandhakar.contacts.util.CallerImages.decode(ctx, Uri.parse(photoUri), 512)
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
        if (b != null) Image(b.asImageBitmap(), null, Modifier.size(size), contentScale = ContentScale.Crop)
        else Text(letter, color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Medium)
    }
}


/**
 * A row with configurable global swipe actions. Action panes stay behind the
 * row and an intentional swipe past the threshold executes the action directly.
 */
@Composable
fun SwipeActionRow(
    swipeLeft: SwipeAction,
    swipeRight: SwipeAction,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    content: @Composable () -> Unit,
) {
    // A deliberate, longer swipe avoids accidental actions during edge-navigation gestures.
    val revealWidth = 144.dp
    val edgeGuard = 32.dp
    val revealPx = with(LocalDensity.current) { revealWidth.toPx() }
    val context = LocalContext.current
    val thresholdPercent = remember(context) { Prefs(context).swipeActionThresholdPercent }
    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    var gestureDistance by remember { mutableFloatStateOf(0f) }
    val commitThresholdPx = rowWidthPx * (thresholdPercent / 100f)
    val edgeGuardPx = with(LocalDensity.current) { edgeGuard.toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var returnJob by remember { mutableStateOf<Job?>(null) }
    val haptic = LocalHapticFeedback.current
    val latestOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val latestOnSwipeRight by rememberUpdatedState(onSwipeRight)
    val minOffset = if (swipeLeft != SwipeAction.NONE) -revealPx else 0f
    val maxOffset = if (swipeRight != SwipeAction.NONE) revealPx else 0f
    val threshold = commitThresholdPx
    // Past the threshold, letting go runs the action: say so with colour and a short tick.
    val rightArmed by remember(threshold) { derivedStateOf { gestureDistance >= threshold } }
    val leftArmed by remember(threshold) { derivedStateOf { gestureDistance <= -threshold } }
    LaunchedEffect(rightArmed, leftArmed) {
        if (rightArmed || leftArmed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Box(Modifier.fillMaxWidth().onSizeChanged { rowWidthPx = it.width.toFloat() }) {
        Row(Modifier.matchParentSize(), verticalAlignment = Alignment.CenterVertically) {
            if (swipeRight != SwipeAction.NONE) {
                SwipeActionSlot(swipeRight, rightArmed, Modifier.width(revealWidth))
            }
            Spacer(Modifier.weight(1f))
            if (swipeLeft != SwipeAction.NONE) {
                SwipeActionSlot(swipeLeft, leftArmed, Modifier.width(revealWidth))
            }
        }
        Box(
            Modifier.fillMaxWidth()
                // offset BEFORE background: the opaque background must slide with the row,
                // otherwise it stays in place and hides the action panes behind it.
                .offset { IntOffset(dragOffset.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(swipeLeft, swipeRight, revealPx, commitThresholdPx, edgeGuardPx) {
                    var gestureEligible = false
                    detectHorizontalDragGestures(
                        onDragStart = { start ->
                            // Do not interpret Android's left/right system-edge gestures as row actions.
                            gestureEligible = start.x > edgeGuardPx && start.x < size.width - edgeGuardPx
                            if (gestureEligible) {
                                returnJob?.cancel()
                                dragging = true
                                gestureDistance = 0f
                            }
                        },
                        onHorizontalDrag = { change, amount ->
                            if (gestureEligible) {
                                change.consume()
                                gestureDistance = (gestureDistance + amount).coerceIn(-size.width.toFloat(), size.width.toFloat())
                                dragOffset = (dragOffset + amount).coerceIn(minOffset, maxOffset)
                            }
                        },
                        onDragEnd = {
                            if (gestureEligible) {
                                val completedOffset = dragOffset
                                val completedDistance = gestureDistance
                                gestureDistance = 0f
                                dragging = false
                                returnJob = scope.launch { animate(completedOffset, 0f, animationSpec = tween(180)) { v, _ -> dragOffset = v } }
                                if (completedDistance >= threshold && swipeRight != SwipeAction.NONE) {
                                    latestOnSwipeRight()
                                } else if (completedDistance <= -threshold && swipeLeft != SwipeAction.NONE) {
                                    latestOnSwipeLeft()
                                }
                            }
                            gestureEligible = false
                        },
                        onDragCancel = {
                            if (gestureEligible) {
                                dragging = false
                                gestureDistance = 0f
                                returnJob = scope.launch { animate(dragOffset, 0f, animationSpec = tween(180)) { v, _ -> dragOffset = v } }
                            }
                            gestureEligible = false
                        },
                    )
                },
        ) {
            content()
        }
    }
}

@Composable
private fun SwipeActionSlot(action: SwipeAction, armed: Boolean, modifier: Modifier = Modifier) {
    val icon = when (action) {
        SwipeAction.CALL -> Icons.Filled.Call
        SwipeAction.MESSAGE -> Icons.Filled.Message
        SwipeAction.VIEW_CONTACT -> Icons.Filled.Person
        SwipeAction.FAVORITE -> Icons.Filled.Star
        SwipeAction.WHATSAPP -> Icons.Filled.Chat
        SwipeAction.NONE -> return
    }
    Column(
        modifier.fillMaxHeight()
            .background(if (armed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = action.label,
            modifier = Modifier.size(if (armed) 28.dp else 24.dp),
            tint = if (armed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            action.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (armed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Small all-caps-free section label used above groups of rows. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
    )
}

/** Rounded container that groups related rows. */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer,
    ) { Column(Modifier.padding(vertical = 8.dp), content = content) }
}

/** One row: leading icon, title, optional supporting text and trailing slot. */
@Composable
fun InfoRow(
    icon: ImageVector?, title: String, subtitle: String? = null,
    onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.padding(end = 16.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(title, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
        Text(body, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Page title row used on the four main tabs. */
@Composable
fun ScreenHeader(title: String, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
        actions()
    }
}
