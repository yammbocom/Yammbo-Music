package it.fast4x.riplay.ui.screens.player.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.noirContentWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.yambo.music.R
import it.fast4x.riplay.commonutils.cleanPrefix
import it.fast4x.riplay.ui.components.RevealImage
import it.fast4x.riplay.ui.components.StationArtwork
import it.fast4x.riplay.ui.components.themed.NoirEyebrow
import it.fast4x.riplay.ui.components.themed.NoirTextAction
import it.fast4x.riplay.ui.components.themed.NowPlayingSongIndicator
import it.fast4x.riplay.ui.styling.mono
import it.fast4x.riplay.ui.styling.tight
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.isRadio
import it.fast4x.riplay.utils.typography

// Yammbo Noir queue: header with count and total time, rows with a rounded cover, the current one
// marked by the equalizer, and a toolbar of outlined round buttons.

/** Size of the cover of a queue row. */
val NoirQueueCoverSize = 52.dp

/**
 * Header of the queue sheet: mono eyebrow with the count and total time, the big title, and the
 * round actions (open the queues list, new queue) and the close text on the right.
 */
@Composable
fun NoirQueueHeader(
    title: String,
    eyebrow: String,
    queuesExpanded: Boolean,
    onToggleQueues: () -> Unit,
    onAddQueue: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .noirContentWidth(NoirContentMax.Reading)
            .padding(start = 20.dp, end = 14.dp, top = 6.dp, bottom = 10.dp)
    ) {
        NoirEyebrow(eyebrow)
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = title,
                style = typography().xxl.tight.copy(
                    color = colorPalette().text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onToggleQueues)
            )
            NoirQueueToolButton(
                icon = if (queuesExpanded) R.drawable.chevron_up else R.drawable.chevron_down,
                onClick = onToggleQueues,
                size = 38.dp
            )
            Box(Modifier.width(8.dp))
            NoirQueueToolButton(
                icon = R.drawable.addqueue,
                onClick = onAddQueue,
                size = 38.dp
            )
            NoirTextAction(
                text = stringResource(R.string.noir_close),
                onClick = onClose,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/**
 * Round outlined icon button of the queue toolbar. [active] fills it white with a black icon
 * (a mode that is on: search open, reorder unlocked, discover on).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoirQueueToolButton(
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    active: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 44.dp
) {
    val colors = colorPalette()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(if (active) colors.text else Color.Transparent)
            .border(1.dp, colors.text.copy(alpha = if (active) 0f else 0.16f), CircleShape)
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (active) colors.background0 else colors.text),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * One queue row. The current item is marked by the animated equalizer over its cover, a hairline
 * card and a "now playing" eyebrow, with the same white text as the others (no grey slab).
 * Clicks come in [modifier]; in select mode a checkbox replaces the drag handle, and [handle] is
 * the slot for the reorder handle (null while reordering is locked).
 */
@Composable
fun NoirQueueRow(
    mediaItem: MediaItem,
    isCurrent: Boolean,
    player: ExoPlayer?,
    thumbnailUrl: String?,
    selectMode: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    handle: (@Composable () -> Unit)? = null
) {
    val colors = colorPalette()
    val coverShape = RoundedCornerShape(12.dp)
    val cardShape = RoundedCornerShape(18.dp)
    val metadata = mediaItem.mediaMetadata
    val artist = metadata.artist?.toString().orEmpty()
    val duration = metadata.extras?.getString("durationText").orEmpty()

    Box(
        modifier = Modifier
            .noirContentWidth(NoirContentMax.Reading)
            .padding(horizontal = 12.dp, vertical = 2.dp)
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(cardShape)
                .then(
                    if (isCurrent) Modifier.border(1.dp, colors.text.copy(alpha = 0.12f), cardShape)
                    else Modifier
                )
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            if (isCurrent)
                NoirEyebrow(
                    text = stringResource(R.string.noir_now_playing),
                    modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
                )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.heightIn(min = NoirQueueCoverSize)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(NoirQueueCoverSize)
                ) {
                    if (mediaItem.isRadio)
                        StationArtwork(
                            model = metadata.artworkUri,
                            shape = coverShape,
                            modifier = Modifier.size(NoirQueueCoverSize)
                        )
                    else
                        RevealImage(
                            model = thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(NoirQueueCoverSize)
                                .clip(coverShape)
                                .background(colors.background2)
                        )
                    if (isCurrent)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(NoirQueueCoverSize)
                                .clip(coverShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                        ) {
                            NowPlayingSongIndicator(
                                mediaId = mediaItem.mediaId,
                                player = player,
                                containerSize = NoirQueueCoverSize
                            )
                        }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    BasicText(
                        text = cleanPrefix(metadata.title?.toString().orEmpty()),
                        style = typography().s.copy(
                            color = colors.text,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (artist.isNotBlank())
                            BasicText(
                                text = artist,
                                style = typography().xs.copy(
                                    color = colors.textSecondary,
                                    fontSize = 13.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        if (duration.isNotBlank()) {
                            if (artist.isNotBlank())
                                BasicText(
                                    text = "  ·  ",
                                    style = typography().xs.copy(
                                        color = colors.textSecondary,
                                        fontSize = 13.sp
                                    ),
                                    maxLines = 1
                                )
                            BasicText(
                                text = duration,
                                style = typography().xs.mono.copy(
                                    color = colors.textSecondary,
                                    fontSize = 12.sp,
                                    letterSpacing = 0.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }

                if (selectMode)
                    Checkbox(
                        checked = checked,
                        onCheckedChange = onCheckedChange,
                        colors = CheckboxDefaults.colors(
                            checkedColor = colors.accent,
                            uncheckedColor = colors.text
                        ),
                        modifier = Modifier.scale(0.8f)
                    )
                else
                    handle?.invoke()
            }
        }
    }
}
