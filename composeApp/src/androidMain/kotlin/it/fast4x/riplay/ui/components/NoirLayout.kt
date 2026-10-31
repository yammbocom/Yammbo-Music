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
}

@Immutable
data class NoirLayout(
    val width: NoirWidth,
    val screenWidth: Dp,
    val isLandscape: Boolean,
) {
    val isCompact: Boolean get() = width == NoirWidth.Compact

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
        val available = (minOf(screenWidth, NoirContentMax.Grid) - horizontalPadding).coerceAtLeast(0.dp)
        return ((available + spacing) / (minItem + spacing)).toInt().coerceIn(min, max)
    }

    /**
     * Side padding that centres content at most [max] wide; never below [minPadding].
     * Meant for LazyColumn/LazyGrid contentPadding, so the list still scrolls edge to edge.
     */
    fun sidePadding(max: Dp, minPadding: Dp = 16.dp): Dp =
        ((screenWidth - max) / 2).coerceAtLeast(minPadding)
}

@Composable
fun rememberNoirLayout(): NoirLayout {
    val config = LocalConfiguration.current
    return remember(config.screenWidthDp, config.orientation) {
        val w = config.screenWidthDp
        NoirLayout(
            width = when {
                w < 600 -> NoirWidth.Compact
                w < 840 -> NoirWidth.Medium
                else -> NoirWidth.Expanded
            },
            screenWidth = w.dp,
            isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE,
        )
    }
}

/** Caps this element at [max] and centres it; on phones (narrower than [max]) it is a plain fillMaxWidth. */
fun Modifier.noirContentWidth(max: Dp = NoirContentMax.List): Modifier =
    fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = max)
        .fillMaxWidth()
