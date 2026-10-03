package it.fast4x.riplay.ui.components.themed

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yambo.music.R
import it.fast4x.riplay.ui.components.glassSurface
import it.fast4x.riplay.ui.styling.center
import it.fast4x.riplay.ui.styling.color
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.resizeNoCrop
import it.fast4x.riplay.utils.typography

/**
 * Shared header for the Artist, Album, online Playlist and Podcast pages: one cover, one title
 * block, one primary row (play / shuffle) and one row of glass circles for everything else.
 *
 * Strictly monochrome: every colour comes from the palette, so light and dark both work.
 */
@Composable
fun MediaHeader(
    imageUrl: String?,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    // Artist photos are wide: they run edge to edge. Everything else is a square cover.
    fullWidthCover: Boolean = false,
    // Small "internet" mark on the cover for content that lives on YouTube.
    showOnlineBadge: Boolean = false,
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val statusBarTop = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    Box(modifier = modifier.fillMaxWidth()) {

        if (!fullWidthCover) MediaBackdrop(imageUrl)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (fullWidthCover) {
                if (imageUrl != null) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Photo is shown whole: FillWidth lets its own proportions set the height.
                        AsyncImage(
                            model = imageUrl.resizeNoCrop(1200, 1200),
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth()
                        )
                        // Bottom scrim so the title stays readable over any photo.
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.62f to Color.Transparent,
                                            0.86f to colorPalette().background0.copy(alpha = 0.6f),
                                            1f to colorPalette().background0
                                        )
                                    )
                                )
                        )
                        if (showOnlineBadge) OnlineBadge(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = statusBarTop + 8.dp, end = 12.dp)
                        )
                        MediaTitleBlock(
                            title = title,
                            subtitle = null,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                } else {
                    // Landscape or still loading: no photo, keep room for the back button.
                    Spacer(Modifier.height(statusBarTop + 52.dp))
                    MediaTitleBlock(title = title, subtitle = null)
                }
                if (!subtitle.isNullOrBlank()) {
                    BasicText(
                        text = subtitle,
                        style = typography().xs.semiBold.secondary.center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
                    )
                }
            } else {
                Spacer(Modifier.height(statusBarTop + 52.dp))
                MediaCover(imageUrl = imageUrl, showOnlineBadge = showOnlineBadge)
                Spacer(Modifier.height(18.dp))
                MediaTitleBlock(title = title, subtitle = subtitle)
            }

            if (onPlay != null && onShuffle != null) {
                Spacer(Modifier.height(16.dp))
                MediaPrimaryActions(onPlay = onPlay, onShuffle = onShuffle)
            }

            Spacer(Modifier.height(12.dp))
            MediaActionsRow(content = actions)
            Spacer(Modifier.height(4.dp))
        }

        MediaBackButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = statusBarTop + 2.dp)
        )
    }
}

/** Blurred, tinted copy of the cover (Android 12+), or a plain surface gradient before that. */
@Composable
private fun BoxScope.MediaBackdrop(imageUrl: String?) {
    val colors = colorPalette()
    Box(modifier = Modifier.matchParentSize()) {
        if (imageUrl != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Alpha over background0 darkens it in dark mode and washes it out in light mode,
            // so the same code reads correctly in both themes.
            AsyncImage(
                model = imageUrl.resizeNoCrop(300, 300),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(40.dp)
                    .alpha(0.5f)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to colors.background0.copy(alpha = 0.25f),
                                0.7f to colors.background0.copy(alpha = 0.7f),
                                1f to colors.background0
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(colors.background1, colors.background0))
                    )
            )
        }
    }
}

/** Square artwork shown whole (never cropped) on a rounded card. */
@Composable
fun MediaCover(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    showOnlineBadge: Boolean = false,
) {
    val colors = colorPalette()
    val isDark = colors.background1.luminance() < 0.5f
    val shape = RoundedCornerShape(16.dp)
    val side = (LocalConfiguration.current.screenWidthDp * 0.64f).dp.coerceAtMost(300.dp)

    Box(
        modifier = modifier
            .size(side)
            // A deep shadow rings a light page with a grey halo, so it is softer there.
            .shadow(if (isDark) 18.dp else 8.dp, shape, clip = false)
            .clip(shape)
            .background(colors.background2)
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl.resizeNoCrop(800, 800),
                contentDescription = null,
                // Fit: a non-square source gets bars instead of being cut.
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (showOnlineBadge) OnlineBadge(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        )
    }
}

// Sits on top of artwork, so it carries its own dark disc instead of using theme colours.
@Composable
private fun OnlineBadge(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(30.dp)
            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
    ) {
        Image(
            painter = painterResource(R.drawable.internet),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun MediaTitleBlock(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        if (title.isNotBlank()) {
            BasicText(
                text = title,
                style = typography().l.semiBold.center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!subtitle.isNullOrBlank()) {
            BasicText(
                text = subtitle,
                style = typography().xs.semiBold.secondary.center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** Filled "Play" pill (inverted colours) next to a glass "Shuffle" pill. */
@Composable
fun MediaPrimaryActions(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = colorPalette()
    val pillShape = RoundedCornerShape(22.dp)

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .widthIn(min = 132.dp)
                .height(44.dp)
                .alpha(if (enabled) 1f else 0.5f)
                .clip(pillShape)
                .background(colors.text)
                .clickable(enabled = enabled, onClick = onPlay)
                .padding(horizontal = 22.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.play),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.background0),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(8.dp))
            BasicText(
                text = stringResource(R.string.mediahdr_play),
                style = typography().xs.semiBold.color(colors.background0),
                maxLines = 1
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .widthIn(min = 132.dp)
                .height(44.dp)
                .alpha(if (enabled) 1f else 0.5f)
                .glassSurface(shape = pillShape, elevation = 6.dp)
                .clickable(enabled = enabled, onClick = onShuffle)
                .padding(horizontal = 22.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.shuffle),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.text),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(8.dp))
            BasicText(
                text = stringResource(R.string.mediahdr_shuffle),
                style = typography().xs.semiBold.color(colors.text),
                maxLines = 1
            )
        }
    }
}

/**
 * Secondary actions: scrolls when it does not fit and stays centred when it does.
 * Put [MediaActionButton] / [MediaActionPill] inside.
 */
@Composable
fun MediaActionsRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                // Min width = the screen, so Center arrangement works inside the scroller.
                .widthIn(min = maxWidth)
                // Vertical room so the circles' shadows are not clipped by the scroller.
                .padding(horizontal = 16.dp, vertical = 6.dp),
            content = content
        )
    }
}

/** 40.dp glass circle with a 20.dp icon; long press shows a hint when [onLongClick] is set. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaActionButton(
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    tint: Color = colorPalette().text,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .glassSurface(shape = CircleShape, elevation = 6.dp)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(20.dp)
        )
    }
}

/** 40.dp glass pill with a text label (for example Follow / Following). */
@Composable
fun MediaActionPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(40.dp)
            .glassSurface(shape = RoundedCornerShape(20.dp), elevation = 6.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp)
    ) {
        BasicText(
            text = text,
            style = typography().xxs.semiBold.color(colorPalette().text),
            maxLines = 1
        )
    }
}

/** Floating glass back button; the pages draw no app bar, so this is the only way out. */
@Composable
fun MediaBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .glassSurface(shape = CircleShape, elevation = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Image(
            painter = painterResource(R.drawable.chevron_back),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette().text),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Full-screen failed state with a retry pill. Opaque and touch-absorbing so it can be emitted
 * next to the screen's loader without the list underneath reacting.
 */
@Composable
fun LoadFailed(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val colors = colorPalette()
    val statusBarTop = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background0)
            .pointerInput(Unit) {}
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
        ) {
            BasicText(
                text = stringResource(R.string.mediahdr_load_failed),
                style = typography().s.semiBold.center,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.text)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 28.dp)
            ) {
                BasicText(
                    text = stringResource(R.string.mediahdr_retry),
                    style = typography().xs.semiBold.color(colors.background0),
                    maxLines = 1
                )
            }
        }
        if (onBack != null) {
            MediaBackButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = statusBarTop + 2.dp)
            )
        }
    }
}
