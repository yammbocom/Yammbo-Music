package it.fast4x.riplay.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Window width buckets, same breakpoints as Material's WindowSizeClass. */
enum class NoirWidth { Compact, Medium, Expanded }

/** Maximum content widths, so pages stay readable on tablets instead of stretching edge to edge. */
object NoirContentMax {
    val Form = 440.dp
    val Reading = 640.dp
    val List = 720.dp
    val Grid = 1040.dp
    val Chrome = 600.dp
    /** Mini player and nav pill side by side (tablet in landscape). */
    val ChromeWide = 1120.dp
}

@Immutable
data class NoirLayout(
    val width: NoirWidth,
    val screenWidth: Dp,
    val isLandscape: Boolean,
    /** Smallest side of the screen: 600 dp or more is a tablet in either orientation. */
    val smallestWidth: Dp = screenWidth,
) {
    val isCompact: Boolean get() = width == NoirWidth.Compact

    /** A tablet in either orientation (a phone in landscape is wide but not a tablet). */
    val isTablet: Boolean get() = smallestWidth >= 600.dp

    /** Tablet held sideways: the layouts that split the screen in two panes use this. */
    val isTabletLandscape: Boolean get() = isTablet && isLandscape

    /** Multiplier for fixed-size shelf cards: phones keep their size, tablets get bigger cards. */
    val shelfScale: Float
        get() = when (width) {
            NoirWidth.Compact -> 1f
            NoirWidth.Medium -> 1.2f
            NoirWidth.Expanded -> 1.35f
        }

    /** Column count for a hand-built grid whose items need at least [minItem] each. */
    fun columns(
        minItem: Dp,
        horizontalPadding: Dp = 32.dp,
        spacing: Dp = 12.dp,
        min: Int = 2,
        max: Int = 6,
    ): Int {
        val page = if (screenWidth >= NoirFillFrom) screenWidth - NoirWideGutter * 2 else minOf(screenWidth, NoirContentMax.Grid)
        val available = (page - horizontalPadding).coerceAtLeast(0.dp)
        return ((available + spacing) / (minItem + spacing)).toInt().coerceIn(min, max)
    }

    /**
     * Side padding that centres content at most [max] wide; never below [minPadding].
     * Meant for LazyColumn/LazyGrid contentPadding, so the list still scrolls edge to edge.
     */
    fun sidePadding(max: Dp, minPadding: Dp = 16.dp): Dp =
        if (max >= NoirContentMax.List && screenWidth >= NoirFillFrom) NoirWideGutter.coerceAtLeast(minPadding)
        else ((screenWidth - max) / 2).coerceAtLeast(minPadding)
}

@Composable
fun rememberNoirLayout(): NoirLayout {
    val config = LocalConfiguration.current
    return remember(config.screenWidthDp, config.orientation, config.smallestScreenWidthDp) {
        val w = config.screenWidthDp
        NoirLayout(
            width = when {
                w < 600 -> NoirWidth.Compact
                w < 840 -> NoirWidth.Medium
                else -> NoirWidth.Expanded
            },
            screenWidth = w.dp,
            isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE,
            smallestWidth = config.smallestScreenWidthDp.dp,
        )
    }
}

/** Width from which a list or grid page stops being a centred column and fills the screen. */
val NoirFillFrom = 960.dp

/** Side margin of list and grid pages once they fill a wide screen. */
val NoirWideGutter = 24.dp

/**
 * Caps this element at [max] and centres it; on phones (narrower than [max]) it is a plain
 * fillMaxWidth. List and grid widths stop capping on a screen at least [NoirFillFrom] wide (a
 * tablet held sideways): a 720 dp column there leaves two empty black bands, so the content
 * spans the width, [NoirWideGutter] from each edge. Forms and reading text keep their cap.
 */
fun Modifier.noirContentWidth(max: Dp = NoirContentMax.List): Modifier = composed {
    // The screen, not the parent: a capped element nested in another must not lose the
    // gutter twice, and a narrower pane (the artist page on a tablet) fills its own width.
    val screen = LocalConfiguration.current.screenWidthDp.dp
    val fill = max >= NoirContentMax.List && screen >= NoirFillFrom
    layout { measurable, constraints ->
        if (!constraints.hasBoundedWidth) {
            // Unbounded parent (horizontal scroll): just the cap, as the old widthIn(max) did.
            val placeable = measurable.measure(constraints.copy(maxWidth = max.roundToPx()))
            return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        val available = constraints.maxWidth
        val cap = minOf(available, if (fill) (screen - NoirWideGutter * 2).roundToPx() else max.roundToPx())
        val placeable = measurable.measure(constraints.copy(minWidth = cap, maxWidth = cap))
        layout(available, placeable.height) { placeable.place((available - cap) / 2, 0) }
    }
}
