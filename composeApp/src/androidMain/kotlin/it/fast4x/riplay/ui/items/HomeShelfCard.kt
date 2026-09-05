package it.fast4x.riplay.ui.items

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yambo.music.R
import it.fast4x.riplay.commonutils.cleanPrefix
import it.fast4x.riplay.commonutils.thumbnail
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.applyIf
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.thumbnailShape
import it.fast4x.riplay.utils.typography

/**
 * Square cover with the title underneath, the shape every Home shelf uses.
 *
 * The YouTube Music feed sections used to reuse the full-width list rows meant for a vertical
 * list, which inside a horizontal shelf came out as long banners that looked nothing like the
 * albums and playlists next to them. Songs and videos from those sections go through this card.
 */
@Composable
fun HomeShelfCard(
    thumbnailUrl: String?,
    title: String?,
    subtitle: String?,
    thumbnailSizePx: Int,
    thumbnailSizeDp: Dp,
    disableScrollingText: Boolean,
    modifier: Modifier = Modifier,
    @androidx.annotation.DrawableRes placeholder: Int = R.drawable.musical_notes,
) {
    ItemContainer(
        alternative = true,
        thumbnailSizeDp = thumbnailSizeDp,
        horizontalAlignment = Alignment.Start,
        modifier = modifier
    ) {
        if (thumbnailUrl.isNullOrBlank())
            Image(
                painter = painterResource(placeholder),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette().textDisabled),
                modifier = Modifier
                    .clip(thumbnailShape())
                    .background(colorPalette().background1)
                    .requiredSize(thumbnailSizeDp)
                    .padding(24.dp)
            )
        else
            AsyncImage(
                model = thumbnailUrl.thumbnail(thumbnailSizePx)?.let { cleanPrefix(it) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .clip(thumbnailShape())
                    .requiredSize(thumbnailSizeDp)
            )

        ItemInfoContainer {
            BasicText(
                text = cleanPrefix(title ?: ""),
                style = typography().xs.semiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.applyIf(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
            )
            if (!subtitle.isNullOrBlank())
                BasicText(
                    text = cleanPrefix(subtitle),
                    style = typography().xs.semiBold.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.applyIf(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                )
        }
    }
}
