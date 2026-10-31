@file:OptIn(ExperimentalSharedTransitionApi::class)

package it.fast4x.riplay.ui.components

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.DataSource
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/*
 * Yammbo Noir motion: the curves, durations and building blocks every screen shares, so the
 * whole app moves the same way. Values come from the motion board the user approved
 * (shared axis X between tabs, cover container transform, cascade, soft covers, like bounce,
 * collapsing title).
 */

/** Material 3 "emphasized": fast start, long gentle landing. Used for anything that travels. */
val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

const val CoverTransitionMillis = 480

// ---------------------------------------------------------------------------------------------
// Cascade: the first rows of a screen land one after another.
// ---------------------------------------------------------------------------------------------

/**
 * Uptime at which the current screen or tab was shown. Rows composed long after it (data that
 * arrived late, rows scrolled into view) appear without the entry animation.
 */
val LocalScreenEnteredAt = compositionLocalOf<Long?> { null }

private const val CascadeWindowMillis = 900L
private const val CascadeMaxIndex = 10

fun Modifier.cascadeEntry(index: Int): Modifier = composed {
    val enteredAt = LocalScreenEnteredAt.current
    val animate = remember {
        index < CascadeMaxIndex &&
            (enteredAt == null || SystemClock.uptimeMillis() - enteredAt < CascadeWindowMillis)
    }
    if (!animate) return@composed this

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(420, delayMillis = index * 35, easing = EmphasizedEasing))
    }
    val offsetPx = with(LocalDensity.current) { 14.dp.toPx() }
    this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * offsetPx
    }
}

// ---------------------------------------------------------------------------------------------
// Soft covers: a cover that comes from the network clears from a blur instead of popping in.
// ---------------------------------------------------------------------------------------------

/**
 * Drop-in for coil's AsyncImage on Noir covers. Images already in memory (revisits, the cover
 * handed over by a shared transition) are shown at once; the rest fade in from a blur.
 * The caller's modifier keeps its clip and background, which act as the placeholder.
 */
@Composable
fun RevealImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    // What the cover belongs to (e.g. the mediaId): a URL change under the same key crossfades
    // instead of restarting the blur-in.
    revealKey: Any? = model,
) {
    val scope = rememberCoroutineScope()
    val progress = remember(revealKey) { Animatable(0f) }
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = contentScale,
        alignment = alignment,
        onSuccess = { state ->
            if (state.result.dataSource == DataSource.MEMORY_CACHE) {
                // Undispatched so the first frame already draws it: no one-frame flash on revisits.
                scope.launch(start = CoroutineStart.UNDISPATCHED) { progress.snapTo(1f) }
            } else {
                scope.launch { progress.animateTo(1f, tween(450, easing = EmphasizedDecelerateEasing)) }
            }
        },
        onError = { scope.launch(start = CoroutineStart.UNDISPATCHED) { progress.snapTo(1f) } },
        modifier = modifier.graphicsLayer {
            val p = progress.value
            alpha = p
            val scale = 1.04f - 0.04f * p
            scaleX = scale
            scaleY = scale
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && p < 1f) {
                val radius = (1f - p) * 10.dp.toPx()
                renderEffect = if (radius > 0.5f) BlurEffect(radius, radius, TileMode.Decal) else null
            } else {
                renderEffect = null
            }
        }
    )
}

// ---------------------------------------------------------------------------------------------
// Like bounce: the heart squeezes, overshoots and drops a ring when a song is liked.
// ---------------------------------------------------------------------------------------------

private const val LikeSettleMillis = 1000L

/**
 * [key] identifies what the heart belongs to (the song). The liked state of a new song arrives
 * from the database a moment after it starts, so a change right after [key] changes is the
 * new song's state, not a tap, and doesn't animate.
 */
fun Modifier.likeBounce(liked: Boolean, ringColor: Color, key: Any? = null): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val ring = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(liked) }
    val keyChangedAt = remember(key) { SystemClock.uptimeMillis() }
    LaunchedEffect(liked) {
        // Only an un-liked -> liked change animates; the first composition just records the state.
        val settled = SystemClock.uptimeMillis() - keyChangedAt > LikeSettleMillis
        if (liked && !previous && settled) {
            launch {
                scale.animateTo(1f, keyframes {
                    durationMillis = 500
                    0.7f at 110
                    1.3f at 300
                    1f at 500
                })
            }
            launch {
                ring.snapTo(0f)
                ring.animateTo(1f, tween(520, easing = EmphasizedDecelerateEasing))
            }
        }
        previous = liked
    }
    this
        .drawBehind {
            val p = ring.value
            if (p < 1f) {
                drawCircle(
                    color = ringColor.copy(alpha = 0.8f * (1f - p)),
                    radius = size.minDimension / 2f * (0.4f + 1.5f * p),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
}

// ---------------------------------------------------------------------------------------------
// Collapsing title: the big page title fades as the page scrolls and reappears small in the
// top app bar.
// ---------------------------------------------------------------------------------------------

/** What the top app bar shows centered. Written by the screen on display, read by the bar. */
object TopBarTitle {
    var text by mutableStateOf("")
    var progress by mutableFloatStateOf(0f)
    internal var owner: Any? = null
}

private val TitleCollapseDistance = 72.dp

/** Collapse progress (0..1) of a title at the top of a scrolling Column. */
@Composable
fun rememberTitleCollapse(scroll: ScrollState): () -> Float {
    val distance = with(LocalDensity.current) { TitleCollapseDistance.toPx() }
    return remember(scroll, distance) { { (scroll.value / distance).coerceIn(0f, 1f) } }
}

/** Collapse progress (0..1) of the title drawn by item [titleItemIndex] of a LazyColumn. */
@Composable
fun rememberTitleCollapse(list: LazyListState, titleItemIndex: Int): () -> Float {
    val distance = with(LocalDensity.current) { TitleCollapseDistance.toPx() }
    return remember(list, titleItemIndex, distance) {
        {
            val info = list.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == titleItemIndex }
            when {
                item != null -> ((info.viewportStartOffset - item.offset) / distance).coerceIn(0f, 1f)
                list.firstVisibleItemIndex > titleItemIndex -> 1f
                else -> 0f
            }
        }
    }
}

/** Applies the collapse to the big title itself. */
fun Modifier.collapsingTitle(progress: () -> Float): Modifier = this.graphicsLayer {
    val p = progress()
    alpha = 1f - p
    translationY = -10.dp.toPx() * p
    val scale = 1f - 0.12f * p
    scaleX = scale
    scaleY = scale
    transformOrigin = TransformOrigin(0f, 0.5f)
}

/**
 * Publishes [text] to the top app bar while this composable is on screen. Only the latest
 * screen to start owns the bar, so the tab leaving during a transition can't clear the new one.
 */
@Composable
fun CollapsingTitleEffect(text: String, progress: () -> Float) {
    val owner = remember { Any() }
    val currentText by rememberUpdatedState(text)
    LaunchedEffect(owner) {
        TopBarTitle.owner = owner
        snapshotFlow { progress() }.collect {
            if (TopBarTitle.owner === owner) {
                TopBarTitle.text = currentText
                TopBarTitle.progress = it
            }
        }
    }
    DisposableEffect(owner) {
        onDispose {
            if (TopBarTitle.owner === owner) {
                TopBarTitle.owner = null
                TopBarTitle.progress = 0f
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Cover container transform: the tapped cover flies into the header of the page it opens.
// ---------------------------------------------------------------------------------------------

val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The NavHost destination's scope, not the inner tab/page AnimatedContent ones. */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * The cover the user just tapped. The page it opens reads it to share the same transition key
 * and to draw the very image already in memory in its first frame (its own cover only arrives
 * after the page loads). The NavHost also reads it to pick the cover transition over the push.
 */
object CoverHandoff {
    data class Entry(val key: String, val browseId: String, val url: String?, val title: String? = null)

    // One entry per page, not a single slot: going album A -> album B -> back must still find
    // A's cover so A keeps its header and flies home on the next pop.
    private const val MaxEntries = 16
    private val entries = LinkedHashMap<String, Entry>()

    fun set(key: String, browseId: String, url: String?, title: String? = null) {
        entries.remove(browseId)
        entries[browseId] = Entry(key, browseId, url, title)
        while (entries.size > MaxEntries) entries.remove(entries.keys.first())
    }

    fun forPage(browseId: String?): Entry? = browseId?.let { entries[it] }
}

private val CoverBoundsTransform = BoundsTransform { _, _ ->
    tween(CoverTransitionMillis, easing = EmphasizedEasing)
}

/**
 * Marks a cover (source or destination) as the same element across a navigation. A null key,
 * or no shared scope around (previews, landscape, screens outside the NavHost), leaves it as is.
 */
@Composable
fun Modifier.sharedCover(key: String?, shape: Shape): Modifier {
    val transition = LocalSharedTransitionScope.current
    val visibility = LocalNavAnimatedVisibilityScope.current
    if (key == null || transition == null || visibility == null) return this
    return with(transition) {
        this@sharedCover.sharedBounds(
            sharedContentState = rememberSharedContentState("cover:$key"),
            animatedVisibilityScope = visibility,
            boundsTransform = CoverBoundsTransform,
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}
