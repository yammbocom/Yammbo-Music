package it.fast4x.riplay.ui.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Suppress("ClassName")
object Dimensions {
    val itemsVerticalPadding = 8.dp

    val navigationRailWidth = 50.dp
    val navigationRailWidthLandscape = 128.dp
    val navigationRailIconOffset = 6.dp
    val headerHeight = 140.dp
    val halfheaderHeight = 60.dp
    val miniPlayerHeight = 60.dp
    val collapsedPlayer = 84.dp
    val navigationBarHeight = 64.dp
    val contentWidthRightBar = 0.88f
    val additionalVerticalSpaceForFloatingAction = 40.dp
    /**
     * Height of the persistent bottom chrome (promo bar + mini player + nav pill + system bar), measured
     * by NoirBottomChrome; 0 while it is hidden. Snapshot state, so screens re-lay out when it changes
     * (e.g. the "Hazte Premium" bar appears or goes away).
     */
    var bottomChromeHeight by mutableStateOf(0.dp)

    /**
     * Space at the end of scrolling screens so their last rows can scroll clear of the bottom chrome.
     * 200 dp covered the mini player and the nav pill, but not the extra "Hazte Premium" bar of free
     * accounts: it now grows to the measured chrome height plus a small margin when that is taller.
     */
    val bottomSpacer: Dp
        get() = maxOf(200.dp, bottomChromeHeight + 16.dp)
    val fadeSpacingTop = 30.dp
    val fadeSpacingBottom = 64.dp
    val musicAnimationHeight = 20.dp



    object thumbnails {
        val album = 128.dp
        val artist = 128.dp
        val song = 54.dp
        val playlist = album

        object player {
            val song: Dp
                @Composable
                get() = with(LocalConfiguration.current) {
                    minOf(screenHeightDp, screenWidthDp)
                }.dp
        }
    }

}

inline val Dp.px: Int
    @Composable
    inline get() = with(LocalDensity.current) { roundToPx() }
