package it.fast4x.riplay.ui.components.themed

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.fast4x.riplay.ui.components.RevealImage
import com.yambo.music.R
import it.fast4x.riplay.commonutils.thumbnail
import it.fast4x.riplay.ui.styling.mono
import it.fast4x.riplay.ui.styling.tight
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography

// Yammbo Noir: the pieces the browse screens (Home, Search, results) share.

/** Small uppercase mono label: section eyebrows ("SEGÚN LO QUE ESCUCHAS", "RECIENTES"…). */
@Composable
fun NoirEyebrow(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text = text.uppercase(),
        style = typography().xxxs.mono.copy(color = colorPalette().textSecondary, fontSize = 11.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/** Mono text action on the right of a section header ("VER TODO", "BORRAR"). */
@Composable
fun NoirTextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BasicText(
        text = text.uppercase(),
        style = typography().xxxs.mono.copy(color = colorPalette().text, fontSize = 11.sp),
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp)
    )
}

/**
 * Section header: optional eyebrow over a big bold title, with "see all" or any [trailing]
 * content on the right. Tapping the title runs [onTitleClick] (e.g. a sort menu).
 */
@Composable
fun NoirSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    onSeeAll: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 10.dp, top = 22.dp, bottom = 10.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = onTitleClick != null) { onTitleClick?.invoke() }
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
        if (onSeeAll != null) NoirTextAction(stringResource(R.string.noir_see_all), onSeeAll)
        trailing?.invoke(this)
    }
}

/** The solid round "play all" button: the one white action of a section. */
@Composable
fun NoirPlayCircle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.play
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colorPalette().text)
            .clickable(onClick = onClick)
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette().background0),
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Pill chip: solid white when [selected], outlined otherwise. */
@Composable
fun NoirChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    onLongClick: (() -> Unit)? = null,
    @DrawableRes trailingIcon: Int? = null
) {
    val shape = RoundedCornerShape(percent = 50)
    val content = if (selected) colorPalette().background0 else colorPalette().text
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(if (selected) colorPalette().text else colorPalette().background0)
            .border(1.dp, colorPalette().text.copy(alpha = if (selected) 0f else 0.16f), shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp)
    ) {
        if (icon != null) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(content.copy(alpha = if (selected) 1f else 0.7f)),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        BasicText(
            text = text,
            style = typography().xs.copy(color = content, fontWeight = FontWeight.Medium, fontSize = 15.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailingIcon != null) {
            Spacer(Modifier.width(6.dp))
            Image(
                painter = painterResource(trailingIcon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(content.copy(alpha = if (selected) 1f else 0.7f)),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** Chips that wrap onto as many lines as they need. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoirChipFlow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        content()
    }
}

/** Outlined tile with a label: moods, genres, YouTube chips. No colour, the brand is B/N. */
@Composable
fun NoirTile(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .clip(shape)
            .background(colorPalette().background1)
            .border(1.dp, colorPalette().text.copy(alpha = 0.10f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
    ) {
        BasicText(
            text = text,
            style = typography().xs.copy(
                color = colorPalette().text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The one big card of a screen: a cover filling the card, darkened towards the bottom, with an
 * eyebrow, a title, a subtitle and the white play circle. Colour comes only from the cover.
 * Tapping the title runs [onTitleClick] (e.g. a sort menu).
 */
@Composable
fun NoirHeroCard(
    image: String?,
    title: String,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    onTitleClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 12.dp)
            .fillMaxWidth()
            .height(196.dp)
            .clip(shape)
            .background(colorPalette().background1)
    ) {
        if (image != null)
            RevealImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.15f),
                        0.45f to Color.Black.copy(alpha = 0.35f),
                        1f to Color.Black.copy(alpha = 0.88f)
                    )
                )
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = onTitleClick != null) { onTitleClick?.invoke() }
            ) {
                if (!eyebrow.isNullOrBlank())
                    BasicText(
                        text = eyebrow.uppercase(),
                        style = typography().xxxs.mono.copy(color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                BasicText(
                    text = title,
                    style = typography().xxl.tight.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank())
                    BasicText(
                        text = subtitle,
                        style = typography().xs.copy(color = Color.White.copy(alpha = 0.75f)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
            }
            Spacer(Modifier.width(12.dp))
            // Always white over the darkened cover, whatever the theme.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onPlay)
            ) {
                Image(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.Black),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * One row of a results list (albums, artists, videos, playlists, podcasts): the cover, the name
 * and "Kind · details" under it. [imageShape] is a circle for artists; videos pass a 16:9 size
 * and their duration as [badge]. Clicks come in [modifier], so the ripple covers the whole row.
 */
@Composable
fun NoirResultRow(
    image: String?,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    imageShape: Shape = RoundedCornerShape(10.dp),
    imageWidth: Dp = 60.dp,
    imageHeight: Dp = 60.dp,
    imageModifier: Modifier = Modifier,
    badge: String? = null,
    explicit: Boolean = false,
    @DrawableRes trailingIcon: Int? = R.drawable.chevron_forward
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(imageWidth, imageHeight)
                .then(imageModifier)
                .clip(imageShape)
                .background(colorPalette().background2)
        ) {
            RevealImage(
                model = image.thumbnail(320),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (!badge.isNullOrBlank())
                BasicText(
                    text = badge,
                    style = typography().xxxs.mono.copy(color = Color.White, fontSize = 10.sp),
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
        }
        Spacer(Modifier.width(14.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.weight(1f)
        ) {
            BasicText(
                text = title,
                style = typography().s.copy(
                    color = colorPalette().text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (explicit) {
                    Image(
                        painter = painterResource(R.drawable.explicit),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette().textSecondary),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                if (!subtitle.isNullOrBlank())
                    BasicText(
                        text = subtitle,
                        style = typography().xs.copy(color = colorPalette().textSecondary, fontSize = 13.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
            }
        }
        if (trailingIcon != null) {
            Spacer(Modifier.width(10.dp))
            Image(
                painter = painterResource(trailingIcon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette().textSecondary.copy(alpha = 0.7f)),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** What a [NoirResultRow] looks like while its page loads. */
@Composable
fun NoirResultRowPlaceholder(
    modifier: Modifier = Modifier,
    imageShape: Shape = RoundedCornerShape(10.dp),
    imageWidth: Dp = 60.dp,
    imageHeight: Dp = 60.dp
) {
    val fill = colorPalette().background2
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(imageWidth, imageHeight)
                .clip(imageShape)
                .background(fill)
        )
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth(0.7f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(fill)
            )
            Box(
                Modifier
                    .fillMaxWidth(0.45f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(fill.copy(alpha = 0.7f))
            )
        }
    }
}
