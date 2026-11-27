package io.github.amandhakar.contacts.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import io.github.amandhakar.contacts.util.SwipeGestures
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
 * A row with the swipe actions chosen in Settings.
 *
 * Swiping slides the row and shows the action behind it. Past the threshold the action fills with
 * its colour and the phone ticks; letting go there runs it, letting go earlier springs the row back.
 * To keep gesture navigation from running actions by accident, a swipe never starts in the system's
 * own gesture areas (back from either screen edge, home along the bottom), it has to be clearly
 * sideways, and nothing runs when the system takes the touch over.
 */
@Composable
fun SwipeActionRow(
    swipeLeft: SwipeAction,
    swipeRight: SwipeAction,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val thresholdPercent = remember(context) { Prefs(context).swipeActionThresholdPercent }

    // The system's gesture areas, in window pixels. Read here so they follow rotation and nav-mode changes.
    val gestureInsets = WindowInsets.systemGestures
    val guard by rememberUpdatedState(
        EdgeGuard(
            left = gestureInsets.getLeft(density, layoutDirection).toFloat(),
            right = gestureInsets.getRight(density, layoutDirection).toFloat(),
            bottom = gestureInsets.getBottom(density).toFloat(),
            minEdge = with(density) { 24.dp.toPx() },
            margin = with(density) { 16.dp.toPx() },
        ),
    )
    val latestOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val latestOnSwipeRight by rememberUpdatedState(onSwipeRight)

    var rowWidth by remember { mutableFloatStateOf(0f) }
    val rowOrigin = remember { floatArrayOf(0f, 0f) }
    var distance by remember { mutableFloatStateOf(0f) } // finger travel
    var offset by remember { mutableFloatStateOf(0f) }   // where the row is drawn
    var dragging by remember { mutableStateOf(false) }
    var committed by remember { mutableStateOf(SwipeAction.NONE) }
    val settleJob = remember { arrayOfNulls<Job>(1) }
    val threshold = SwipeGestures.threshold(rowWidth, thresholdPercent)

    val armed by remember(threshold, swipeLeft, swipeRight) {
        derivedStateOf { SwipeGestures.armed(distance, threshold, swipeLeft, swipeRight) }
    }
    // A firm tick when letting go would run the action, a light one when the swipe backs off again.
    var wasArmed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        val now = armed != SwipeAction.NONE
        if (now && dragging) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        else if (!now && wasArmed && dragging) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        wasArmed = now
    }

    Box(
        Modifier.fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { rowWidth = it.width.toFloat() }
            .onGloballyPositioned { val p = it.positionInWindow(); rowOrigin[0] = p.x; rowOrigin[1] = p.y }
            .semantics {
                // Swipes are invisible to TalkBack, so offer the same actions in its actions menu.
                customActions = listOfNotNull(
                    swipeRight.takeIf { it != SwipeAction.NONE }?.let { a -> CustomAccessibilityAction(a.label) { latestOnSwipeRight(); true } },
                    swipeLeft.takeIf { it != SwipeAction.NONE && it != swipeRight }?.let { a -> CustomAccessibilityAction(a.label) { latestOnSwipeLeft(); true } },
                )
            }
            .pointerInput(swipeLeft, swipeRight, threshold) {
                if (swipeLeft == SwipeAction.NONE && swipeRight == SwipeAction.NONE) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val g = guard
                    val root = view.rootView
                    val startsAtEdge = SwipeGestures.startsInSystemGesture(
                        rowOrigin[0] + down.position.x, rowOrigin[1] + down.position.y,
                        root.width.toFloat(), root.height.toFloat(),
                        g.left, g.right, g.bottom, g.minEdge, g.margin,
                    )
                    // Leave touches in the system's gesture areas alone: that is where back and home start.
                    if (startsAtEdge) return@awaitEachGesture

                    var overSlop = 0f
                    val drag = awaitTouchSlopOrCancellation(down.id) { change, over ->
                        val moved = change.position - down.position
                        // Only a clearly sideways move in a direction that has an action becomes a swipe;
                        // anything else is left to the list, so it can scroll.
                        if (SwipeGestures.isHorizontal(moved.x, moved.y) && SwipeGestures.allowed(moved.x, swipeLeft, swipeRight)) {
                            change.consume()
                            overSlop = over.x
                        }
                    } ?: return@awaitEachGesture

                    settleJob[0]?.cancel()
                    committed = SwipeAction.NONE
                    dragging = true
                    val width = size.width.toFloat()
                    distance = SwipeGestures.clampDistance(offset + overSlop, width, swipeLeft, swipeRight)
                    offset = SwipeGestures.visualOffset(distance, threshold)
                    // false when the system or the list took the touch over (a back gesture, a scroll).
                    val endedNormally = horizontalDrag(drag.id) { change ->
                        distance = SwipeGestures.clampDistance(distance + change.positionChange().x, width, swipeLeft, swipeRight)
                        offset = SwipeGestures.visualOffset(distance, threshold)
                        change.consume()
                    }
                    val action = SwipeGestures.onRelease(distance, threshold, endedNormally, swipeLeft, swipeRight)
                    val fromRight = distance > 0f
                    dragging = false
                    distance = 0f
                    committed = action
                    val from = offset
                    settleJob[0] = scope.launch {
                        animate(
                            from, 0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                        ) { v, _ -> offset = v }
                        committed = SwipeAction.NONE
                    }
                    if (action != SwipeAction.NONE) {
                        if (fromRight) latestOnSwipeRight() else latestOnSwipeLeft()
                    }
                }
            },
    ) {
        val shown = when {
            offset > 0.5f -> swipeRight
            offset < -0.5f -> swipeLeft
            else -> SwipeAction.NONE
        }
        if (shown != SwipeAction.NONE) {
            SwipeBackground(
                action = shown,
                fromLeft = offset > 0f,
                progress = SwipeGestures.progress(offset, threshold),
                armed = committed != SwipeAction.NONE || armed != SwipeAction.NONE,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            Modifier.fillMaxWidth()
                // offset BEFORE background: the opaque background must slide with the row,
                // otherwise it stays in place and hides the action behind it.
                .offset { IntOffset(offset.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.surface),
        ) {
            content()
        }
    }
}

private data class EdgeGuard(val left: Float, val right: Float, val bottom: Float, val minEdge: Float, val margin: Float)

/** The coloured area behind a row while it is swiped: muted until the threshold, the action's colour past it. */
@Composable
private fun SwipeBackground(action: SwipeAction, fromLeft: Boolean, progress: Float, armed: Boolean, modifier: Modifier = Modifier) {
    val icon = swipeIcon(action) ?: return
    val (actionColor, onActionColor) = swipeColors(action)
    val bg by animateColorAsState(if (armed) actionColor else MaterialTheme.colorScheme.surfaceContainerHighest, tween(150), label = "swipeBg")
    val fg by animateColorAsState(if (armed) onActionColor else MaterialTheme.colorScheme.onSurfaceVariant, tween(150), label = "swipeFg")
    val scale by animateFloatAsState(
        if (armed) 1.2f else 0.8f + 0.2f * progress,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "swipeScale",
    )
    val fade = 0.35f + 0.65f * progress
    Row(
        modifier.background(bg).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (fromLeft) Arrangement.Absolute.Left else Arrangement.Absolute.Right,
    ) {
        val iconView: @Composable () -> Unit = {
            Icon(
                icon, null,
                Modifier.size(24.dp).graphicsLayer { scaleX = scale; scaleY = scale; alpha = fade },
                tint = fg,
            )
        }
        val labelView: @Composable () -> Unit = {
            Text(
                action.label, Modifier.graphicsLayer { alpha = fade },
                style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1,
            )
        }
        if (fromLeft) { iconView(); Spacer(Modifier.width(12.dp)); labelView() }
        else { labelView(); Spacer(Modifier.width(12.dp)); iconView() }
    }
}

private fun swipeIcon(action: SwipeAction): ImageVector? = when (action) {
    SwipeAction.CALL -> Icons.Filled.Call
    SwipeAction.MESSAGE -> Icons.AutoMirrored.Filled.Message
    SwipeAction.VIEW_CONTACT -> Icons.Filled.Person
    SwipeAction.FAVORITE -> Icons.Filled.Star
    SwipeAction.WHATSAPP -> Icons.AutoMirrored.Filled.Chat
    SwipeAction.NONE -> null
}

/** Each action keeps its own colour in light and dark, so the colour alone says what will happen. */
@Composable
private fun swipeColors(action: SwipeAction): Pair<Color, Color> = when (action) {
    SwipeAction.CALL -> Color(0xFF1E8E3E) to Color.White
    SwipeAction.MESSAGE -> Color(0xFF1A73E8) to Color.White
    SwipeAction.WHATSAPP -> Color(0xFF128C7E) to Color.White
    SwipeAction.FAVORITE -> Color(0xFFF9AB00) to Color(0xFF202124)
    SwipeAction.VIEW_CONTACT -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
    SwipeAction.NONE -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
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
