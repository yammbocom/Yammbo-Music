package it.fast4x.riplay.ui.screens.newreleases

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.fast4x.riplay.ui.components.CoverHandoff
import it.fast4x.riplay.ui.components.RevealImage
import it.fast4x.riplay.ui.components.collapsingTitle
import it.fast4x.riplay.ui.components.pressable
import it.fast4x.riplay.ui.components.sharedCover
import it.fast4x.riplay.ui.components.themed.NoirEyebrow
import it.fast4x.riplay.ui.styling.tight
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography

// Yammbo Noir: the pieces the secondary browse screens (new releases, moods and genres) share.

/** Big bold page title (40sp) that collapses into the top bar as the page scrolls. */
@Composable
fun NoirPageTitle(
    text: String,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding, end = horizontalPadding, top = 6.dp, bottom = 10.dp)
    ) {
        BasicText(
            text = text,
            style = typography().xxl.tight.copy(
                color = colorPalette().text,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).collapsingTitle(progress)
        )
    }
}

/** Collapse progress (0..1) of the title drawn by item 0 of a LazyVerticalGrid. */
@Composable
fun rememberGridTitleCollapse(state: LazyGridState): () -> Float {
    val distance = with(LocalDensity.current) { 72.dp.toPx() }
    return remember(state, distance) {
        {
            val info = state.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == 0 }
            when {
                item != null -> ((info.viewportStartOffset - item.offset.y) / distance).coerceIn(0f, 1f)
                state.firstVisibleItemIndex > 0 -> 1f
                else -> 0f
            }
        }
    }
}

/**
 * A cover with the title and the artist (or any subtitle) under it. The width comes from
 * [modifier] (a grid cell or a fixed shelf width); the cover is square unless [aspect] says otherwise (16:9 videos). With a [sharedKey]
 * the cover flies into the page it opens ([browseId] is that page's id).
 */
@Composable
fun NoirMediaCard(
    cover: String?,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sharedKey: String? = null,
    browseId: String? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    aspect: Float = 1f
) {
    Column(
        modifier = modifier.pressable(onClick = {
            if (sharedKey != null && browseId != null)
                CoverHandoff.set(key = sharedKey, browseId = browseId, url = cover, title = title)
            onClick()
        })
    ) {
        RevealImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .sharedCover(sharedKey, shape)
                .clip(shape)
                .background(colorPalette().background2)
        )
        Spacer(Modifier.height(8.dp))
        BasicText(
            text = title,
            style = typography().xs.copy(
                color = colorPalette().text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            BasicText(
                text = subtitle,
                style = typography().xxs.copy(color = colorPalette().textSecondary, fontSize = 13.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Section title inside a padded grid (the grid already provides the side margins). */
@Composable
fun NoirGridSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        if (!eyebrow.isNullOrBlank()) NoirEyebrow(eyebrow)
        BasicText(
            text = title,
            style = typography().xl.tight.copy(
                color = colorPalette().text,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
