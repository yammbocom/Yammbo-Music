package it.fast4x.riplay.ui.components.themed

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import it.fast4x.riplay.commonutils.cleanPrefix
import it.fast4x.riplay.ui.components.RevealImage
import it.fast4x.riplay.ui.components.StationArtwork
import it.fast4x.riplay.ui.styling.mono
import it.fast4x.riplay.ui.styling.tight
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.isRadio
import it.fast4x.riplay.utils.typography

/**
 * Yammbo Noir header of a song menu: rounded cover, bold title, artist and duration in mono,
 * and round outlined actions (heart, share) on the right.
 *
 * [thumbnailUrl] must already be sized for the cover (`.thumbnail(px)`).
 */
@Composable
fun MenuMediaHeader(
    mediaItem: MediaItem,
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = colorPalette()
    val coverShape = RoundedCornerShape(14.dp)
    val metadata = mediaItem.mediaMetadata
    val duration = metadata.extras?.getString("durationText")
        ?.takeUnless { mediaItem.isRadio || it.isBlank() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 4.dp)
    ) {
        if (mediaItem.isRadio)
            StationArtwork(
                model = metadata.artworkUri,
                shape = coverShape,
                modifier = Modifier.size(64.dp)
            )
        else
            RevealImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(coverShape)
                    .background(colors.background2)
            )

        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.weight(1f)
        ) {
            BasicText(
                text = cleanPrefix(metadata.title?.toString().orEmpty()),
                style = typography().m.tight.copy(
                    color = colors.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val artist = metadata.artist?.toString().orEmpty()
            if (artist.isNotBlank())
                BasicText(
                    text = artist,
                    style = typography().xs.copy(color = colors.textSecondary, fontSize = 14.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            if (duration != null)
                BasicText(
                    text = duration,
                    style = typography().xxs.mono.copy(
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        letterSpacing = 0.sp
                    ),
                    maxLines = 1
                )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

/** Round outlined icon button of a menu header. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MenuHeaderAction(
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    tint: Color = colorPalette().text,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(1.dp, colorPalette().text.copy(alpha = 0.16f), CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(22.dp)
        )
    }
}
