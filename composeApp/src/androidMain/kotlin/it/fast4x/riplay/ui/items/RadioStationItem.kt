package it.fast4x.riplay.ui.items

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.yambo.music.R
import it.fast4x.riplay.LocalPlayerServiceBinder
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.extensions.liveradio.RadioArtwork
import it.fast4x.riplay.extensions.liveradio.RadioStation
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.color
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.DisposableListener
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.thumbnailShape
import it.fast4x.riplay.utils.typography

/** Below this many pixels an icon is a favicon, not artwork: worth trying for something sharper. */
private const val SHARP_ARTWORK_MIN_PX = 96

/**
 * Artwork box shared by every radio item. The letter placeholder always sits underneath because
 * icons are often missing or 404. `primary` (the directory favicon) loads first; when it fails
 * or turns out tiny, `alternative` (the homepage icon service) is tried and wins only if it is
 * actually larger. Whichever is shown gets reported through [onResolved].
 */
@Composable
fun RadioArtworkBox(
    letter: String,
    primary: String?,
    alternative: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    onResolved: ((String) -> Unit)? = null,
) {
    // -1 = still loading, 0 = failed, otherwise the bitmap width in pixels
    var primaryWidth by remember(primary) { mutableIntStateOf(if (primary == null) 0 else -1) }
    var alternativeWidth by remember(alternative) { mutableIntStateOf(-1) }

    val tryAlternative = alternative != null && primaryWidth in 0 until SHARP_ARTWORK_MIN_PX
    val showAlternative = tryAlternative && alternativeWidth > 0 && alternativeWidth > primaryWidth

    val shown = when {
        showAlternative -> alternative
        primaryWidth > 0 -> primary
        else -> null
    }
    LaunchedEffect(shown) {
        if (shown != null) onResolved?.invoke(shown)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(thumbnailShape())
            .background(colorPalette().background2)
            .size(size)
    ) {
        BasicText(
            text = letter,
            style = typography().l.semiBold.color(colorPalette().textSecondary)
                .copy(fontSize = (size.value / 2.4f).sp),
        )
        if (primary != null && primaryWidth != 0) {
            AsyncImage(
                model = primary,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                alpha = if (showAlternative) 0f else 1f,
                onSuccess = { primaryWidth = it.result.drawable.intrinsicWidth.coerceAtLeast(1) },
                onError = { primaryWidth = 0 },
                modifier = Modifier.fillMaxSize()
            )
        }
        if (tryAlternative && alternativeWidth != 0) {
            AsyncImage(
                model = alternative,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                alpha = if (showAlternative) 1f else 0f,
                onSuccess = { alternativeWidth = it.result.drawable.intrinsicWidth.coerceAtLeast(1) },
                onError = { alternativeWidth = 0 },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun RadioStationArtwork(
    station: RadioStation,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    RadioArtworkBox(
        letter = station.displayName.take(1).uppercase(),
        primary = station.faviconUrl,
        alternative = station.homepageIconUrl,
        size = size,
        modifier = modifier,
        onResolved = { RadioArtwork.remember(station.stationuuid, it) }
    )
}

/** Carousel card for the Home shelf. */
@Composable
fun RadioStationCard(
    station: RadioStation,
    thumbnailSizeDp: Dp,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .width(thumbnailSizeDp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        RadioStationArtwork(station = station, size = thumbnailSizeDp)
        BasicText(
            text = station.displayName,
            style = typography().xs.semiBold.color(if (isPlaying) colorPalette().accent else colorPalette().text),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        BasicText(
            text = station.country.ifBlank { station.tagList.firstOrNull() ?: "" },
            style = typography().xxs.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/** List row for the Radio tab. */
@Composable
fun RadioStationRow(
    station: RadioStation,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val details = buildList {
        if (station.country.isNotBlank()) add(station.country)
        station.tagList.take(3).joinToString(", ").takeIf { it.isNotBlank() }?.let { add(it) }
        val quality = listOf(station.codec, if (station.bitrate > 0) "${station.bitrate} kbps" else "")
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (quality.isNotBlank()) add(quality)
    }
    RadioRow(
        title = station.displayName,
        subtitle = details.joinToString(" · "),
        isPlaying = isPlaying,
        modifier = modifier,
        onClick = onClick,
        artwork = { RadioStationArtwork(station = station, size = Dimensions.thumbnails.song) }
    )
}

/** List row for a hearted station, which lives in the database as a Song. */
@Composable
fun RadioFavoriteRow(
    song: Song,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val artworkUrl = song.thumbnailUrl?.takeIf { it.startsWith("http", ignoreCase = true) }
    RadioRow(
        title = song.title,
        subtitle = song.artistsText.orEmpty(),
        isPlaying = isPlaying,
        modifier = modifier,
        onClick = onClick,
        artwork = {
            RadioArtworkBox(
                letter = song.title.trim().take(1).uppercase(),
                primary = artworkUrl,
                alternative = null,
                size = Dimensions.thumbnails.song
            )
        }
    )
}

@Composable
private fun RadioRow(
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    artwork: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        artwork()
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 8.dp)
        ) {
            BasicText(
                text = title,
                style = typography().xs.semiBold.color(if (isPlaying) colorPalette().accent else colorPalette().text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                BasicText(
                    text = subtitle,
                    style = typography().xxs.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        if (isPlaying) {
            Image(
                painter = painterResource(R.drawable.radio),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette().accent),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Rounded filter chip used by the Radio tab (genres, country, order, load more). */
@Composable
fun FilterPill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    @DrawableRes trailingIcon: Int? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val background = if (selected) colorPalette().accent else colorPalette().background2
    val foreground = if (selected) colorPalette().onAccent else colorPalette().text
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        BasicText(
            text = text,
            style = typography().xs.semiBold.color(foreground),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailingIcon?.let { icon ->
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(foreground),
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(14.dp)
            )
        }
    }
}

/** Replaces the seek bar of the full player for a live stream: there is nothing to scrub. */
@Composable
fun LiveStreamIndicator(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(colorPalette().accent)
        )
        BasicText(
            text = stringResource(R.string.live_radio_badge),
            style = typography().xs.semiBold.color(colorPalette().accent),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/** Media id of whatever the local player holds now, kept fresh across song changes. */
@Composable
fun rememberNowPlayingMediaId(): String? {
    val player = LocalPlayerServiceBinder.current?.player
    var mediaId by remember(player) { mutableStateOf(player?.currentMediaItem?.mediaId) }
    player?.DisposableListener {
        object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaId = mediaItem?.mediaId
            }
        }
    }
    return mediaId
}
