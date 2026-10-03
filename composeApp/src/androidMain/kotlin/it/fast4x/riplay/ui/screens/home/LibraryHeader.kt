package it.fast4x.riplay.ui.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yambo.music.R
import it.fast4x.riplay.ui.components.glassSurface
import it.fast4x.riplay.ui.styling.bold
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography

/*
 * Shared building blocks for the library screens (Canciones, Artistas, Albumes, Listas, Radios).
 * Strictly monochrome: emphasis is inversion (text fill, background0 content), everything else
 * is a glass surface built from the palette.
 */

/** "12 songs" style text from a plurals resource. */
@Composable
fun libraryCountText(pluralsId: Int, count: Int): String =
    LocalContext.current.resources.getQuantityString(pluralsId, count, count)

// Press feedback shared by the pills and circles: a quick, non-bouncy scale-down.
@Composable
private fun rememberPressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    pressedScale: Float = 0.95f
): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
        label = "library-press"
    )
    return scale
}

/** Large title with a quiet subtitle and an optional trailing slot (e.g. the sort pill). */
@Composable
fun LibraryTitleBlock(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    val colors = colorPalette()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = typography().xxl.bold,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = typography().xs,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing()
    }
}

/**
 * Horizontally scrollable pill chips. Selected = inverted fill, the rest glass.
 * Replaces ButtonsRow, whose chevron overlay and dropdown clipped the labels.
 */
@Composable
fun <T> LibraryChipsRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val selectedIndex = options.indexOfFirst { it.first == selected }

    // Keep the active chip in view after a tab switch or when the options change.
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(
            items = options,
            key = { index, _ -> index },
            contentType = { _, _ -> "chip" }
        ) { _, option ->
            LibraryChip(
                label = option.second,
                selected = option.first == selected,
                onClick = { onSelect(option.first) }
            )
        }
    }
}

@Composable
private fun LibraryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = colorPalette()
    val shape = RoundedCornerShape(20.dp)
    val fill by animateColorAsState(
        targetValue = if (selected) colors.text else Color.Transparent,
        label = "library-chip-fill"
    )
    val content by animateColorAsState(
        targetValue = if (selected) colors.background0 else colors.text,
        label = "library-chip-content"
    )
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source, enabled = true, pressedScale = 0.96f)

    Box(
        modifier = Modifier
            .height(36.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glassSurface(shape = shape, elevation = 2.dp)
            .background(fill)
            .combinedClickableCompat(source, enabled = true, onClick = onClick, onLongClick = null)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = typography().xs.semiBold,
            color = content,
            maxLines = 1,
            softWrap = false
        )
    }
}

// Thin wrapper so every control shares the same click setup (ripple clipped by the glass shape).
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.combinedClickableCompat(
    source: MutableInteractionSource,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
): Modifier = this.combinedClickable(
    interactionSource = source,
    indication = LocalIndication.current,
    enabled = enabled,
    onLongClick = onLongClick,
    onClick = onClick
)

/** Primary call to action: 44dp pill, text-colored fill, background-colored content. */
@Composable
fun LibraryPrimaryPill(
    label: String,
    iconId: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = colorPalette()
    val shape = RoundedCornerShape(20.dp)
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source, enabled)
    val contentAlpha = if (enabled) 1f else 0.4f

    Row(
        modifier = modifier
            .height(44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = contentAlpha }
            .clip(shape)
            .background(colors.text)
            .combinedClickableCompat(source, enabled, onClick, null)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconId),
            contentDescription = null,
            tint = colors.background0,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = label,
            style = typography().xs.semiBold,
            color = colors.background0,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Secondary pill on a glass surface, same height as the primary one. */
@Composable
fun LibraryGlassPill(
    label: String,
    iconId: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = colorPalette()
    val shape = RoundedCornerShape(20.dp)
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source, enabled)
    val contentAlpha = if (enabled) 1f else 0.4f

    Row(
        modifier = modifier
            .height(44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = contentAlpha }
            .glassSurface(shape = shape, elevation = 6.dp)
            .combinedClickableCompat(source, enabled, onClick, null)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconId),
            contentDescription = null,
            tint = colors.text,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = label,
            style = typography().xs.semiBold,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 40dp floating glass circle. [active] inverts it to flag an on state (e.g. auto shuffle). */
@Composable
fun LibraryCircleButton(
    iconId: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    active: Boolean = false
) {
    val colors = colorPalette()
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source, enabled, pressedScale = 0.92f)
    val tint = when {
        active -> colors.background0
        enabled -> colors.text
        else -> colors.textDisabled
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glassSurface(
                shape = CircleShape,
                alpha = if (active) 1f else 0.82f,
                elevation = 6.dp,
                fill = if (active) colors.text else null
            )
            .combinedClickableCompat(source, enabled, onClick, onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconId),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Sort control: the label opens the sort menu, the arrow flips the order.
 * Long press anywhere also opens the menu (the old gesture).
 */
@Composable
fun LibrarySortPill(
    label: String,
    arrowRotation: Float,
    onOpenMenu: () -> Unit,
    onToggleOrder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = colorPalette()
    val shape = RoundedCornerShape(20.dp)
    val labelSource = remember { MutableInteractionSource() }
    val arrowSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .height(36.dp)
            .glassSurface(shape = shape, elevation = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = typography().xs.semiBold,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 140.dp)
                .combinedClickableCompat(labelSource, true, onOpenMenu, null)
                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .combinedClickableCompat(arrowSource, true, onToggleOrder, onOpenMenu),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_up),
                contentDescription = null,
                tint = colors.text,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = arrowRotation }
            )
        }
    }
}

/** Glass dock that hosts a TabToolBar so its icon buttons read as one floating control. */
@Composable
fun LibraryToolbarDock(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .glassSurface(shape = RoundedCornerShape(20.dp), elevation = 6.dp)
            .padding(vertical = 2.dp)
    ) {
        content()
    }
}

/** Centered icon in a soft circle, a title, one helpful line and an optional action. */
@Composable
fun LibraryEmptyState(
    iconId: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    val colors = colorPalette()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(colors.background1),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconId),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            style = typography().m.semiBold,
            color = colors.text,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            style = typography().xs,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.text)
                    .combinedClickableCompat(
                        remember { MutableInteractionSource() }, true, onAction, null
                    )
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionLabel,
                    style = typography().xs.semiBold,
                    color = colors.background0,
                    maxLines = 1
                )
            }
        }
    }
}
