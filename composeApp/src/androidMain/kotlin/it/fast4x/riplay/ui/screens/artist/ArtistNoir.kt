package it.fast4x.riplay.ui.screens.artist

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yambo.music.R
import it.fast4x.environment.Environment
import it.fast4x.riplay.commonutils.cleanPrefix
import it.fast4x.riplay.commonutils.thumbnail
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.RevealImage
import it.fast4x.riplay.ui.components.noirContentWidth
import it.fast4x.riplay.ui.components.sharedCover
import it.fast4x.riplay.ui.components.themed.MediaBackButton
import it.fast4x.riplay.ui.components.themed.NoirEyebrow
import it.fast4x.riplay.ui.components.themed.NoirSectionHeader
import it.fast4x.riplay.ui.styling.mono
import it.fast4x.riplay.ui.styling.tight
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography

// Yammbo Noir artist page: the photo leads, the name sits big over its fade, one white play
// circle, and the shelves below in the same shapes Home uses.

/** The photo edge to edge with the name over its fade and the back button over the photo. */
@Composable
fun ArtistHero(
    imageUrl: String?,
    name: String,
    eyebrow: String?,
    onBack: () -> Unit,
    // Cover handoff from the screen that opened this page (see CoverHandoff).
    sharedKey: String? = null,
    previewUrl: String? = null
) {
    val statusBarTop = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    // On tablets the photo is capped at list width and centred (less crop than a 1000dp-wide slice).
    val heroWidthDp = minOf(LocalConfiguration.current.screenWidthDp.toFloat(), NoirContentMax.List.value)
    val height = (heroWidthDp * 0.92f).dp.coerceAtMost(440.dp)
    val page = colorPalette().background0
    Box(
        modifier = Modifier
            .noirContentWidth(NoirContentMax.List)
            .height(if (imageUrl != null || previewUrl != null) height else statusBarTop + 120.dp)
            .sharedCover(sharedKey, RectangleShape)
            .background(colorPalette().background1)
    ) {
        // The tapped cover, already in memory: shows in the first frame while the page loads.
        if (previewUrl != null)
            RevealImage(
                model = previewUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize()
            )
        if (imageUrl != null)
            RevealImage(
                model = imageUrl.thumbnail(1200),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize()
            )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to page.copy(alpha = 0.35f),
                        0.22f to page.copy(alpha = 0f),
                        0.55f to page.copy(alpha = 0.1f),
                        0.85f to page.copy(alpha = 0.85f),
                        1f to page
                    )
                )
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            if (!eyebrow.isNullOrBlank()) NoirEyebrow(eyebrow)
            BasicText(
                text = cleanPrefix(name),
                style = typography().xxl.tight.copy(
                    color = colorPalette().text,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (name.length > 18) 34.sp else 44.sp,
                    lineHeight = if (name.length > 18) 38.sp else 46.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        MediaBackButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = statusBarTop + 2.dp)
        )
    }
}

/** Outlined round icon action of the artist bar (shuffle, radio, download, share). */
@Composable
fun ArtistIconAction(
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .border(1.dp, colorPalette().text.copy(alpha = 0.16f), CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette().text),
            modifier = Modifier.size(19.dp)
        )
    }
}

/** Follow / Following: outlined while not following, solid once followed. */
@Composable
fun ArtistFollowPill(following: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    BasicText(
        text = stringResource(if (following) R.string.following else R.string.follow),
        style = typography().xs.copy(
            color = if (following) colorPalette().background0 else colorPalette().text,
            fontWeight = FontWeight.SemiBold
        ),
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (following) colorPalette().text else colorPalette().background0)
            .border(1.dp, colorPalette().text.copy(alpha = if (following) 0f else 0.3f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    )
}

/** The bar under the hero: follow and the icon actions on the left, the white play on the right. */
@Composable
fun ArtistActionBar(
    onPlay: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .noirContentWidth(NoirContentMax.List)
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp)
    ) {
        content()
        Spacer(Modifier.weight(1f))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colorPalette().text)
                .clickable(onClick = onPlay)
        ) {
            Image(
                painter = painterResource(R.drawable.play),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette().background0),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** Section title of the artist page, with "see all" when YouTube has a full list behind it. */
@Composable
fun ArtistSectionHeader(title: String, onSeeAll: (() -> Unit)?) {
    Box(modifier = Modifier.noirContentWidth(NoirContentMax.List)) {
        NoirSectionHeader(title = title, onSeeAll = onSeeAll)
    }
}

/**
 * One song of the artist: its rank (popular songs) or a 16:9 still (videos), the cover, the
 * name and what YouTube says under it. Clicks come in [modifier].
 */
@Composable
fun ArtistSongRow(
    song: Environment.SongItem,
    rank: Int?,
    wide: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .noirContentWidth(NoirContentMax.List)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        if (rank != null && !wide) {
            BasicText(
                text = rank.toString(),
                style = typography().s.mono.copy(
                    color = colorPalette().textSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1,
                modifier = Modifier.width(26.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        RevealImage(
            model = song.thumbnail?.url.thumbnail(if (wide) 320 else 160),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(if (wide) 96.dp else 52.dp, if (wide) 54.dp else 52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colorPalette().background2)
        )
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            BasicText(
                text = cleanPrefix(song.info?.name.orEmpty()),
                style = typography().s.copy(color = colorPalette().text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.explicit) {
                    Image(
                        painter = painterResource(R.drawable.explicit),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette().textSecondary),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                val subtitle = listOfNotNull(
                    song.album?.name?.takeIf { it.isNotBlank() }
                        ?: song.authors?.mapNotNull { it.name }?.filter { it.isNotBlank() }?.joinToString(", ")
                            ?.takeIf { it.isNotEmpty() },
                    song.durationText?.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                BasicText(
                    text = subtitle,
                    style = typography().xs.copy(color = colorPalette().textSecondary, fontSize = 13.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Square (or round, for artists) cover with its name and a line under it, for the shelves. */
@Composable
fun ArtistShelfCard(
    image: String?,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 152.dp,
    imageHeight: Dp = width,
    imageShape: Shape = RoundedCornerShape(16.dp),
    centered: Boolean = false,
    sharedKey: String? = null
) {
    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .width(width)
            .clickable(onClick = onClick)
    ) {
        RevealImage(
            model = image.thumbnail(480),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width, imageHeight)
                .sharedCover(sharedKey, imageShape)
                .clip(imageShape)
                .background(colorPalette().background2)
        )
        Spacer(Modifier.height(5.dp))
        BasicText(
            text = title,
            style = typography().xs.copy(
                color = colorPalette().text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start
            ),
            maxLines = if (centered) 2 else 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!subtitle.isNullOrBlank())
            BasicText(
                text = subtitle,
                style = typography().xxs.copy(
                    color = colorPalette().textSecondary,
                    fontSize = 13.sp,
                    textAlign = if (centered) TextAlign.Center else TextAlign.Start
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
    }
}

/** A plain row card ("In your library") with a chevron. */
@Composable
fun ArtistLinkRow(@DrawableRes icon: Int, text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .noirContentWidth(NoirContentMax.List)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(colorPalette().background1)
            .border(1.dp, colorPalette().text.copy(alpha = 0.08f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette().text),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        BasicText(
            text = text,
            style = typography().s.copy(color = colorPalette().text, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Image(
            painter = painterResource(R.drawable.chevron_forward),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette().textSecondary),
            modifier = Modifier.size(16.dp)
        )
    }
}

/** "About": the biography on a card, four lines until tapped. */
@Composable
fun ArtistAboutCard(description: String, imageUrl: String?, subscribers: String?) {
    val attributionsIndex = description.lastIndexOf("\n\nFrom Wikipedia")
    val text = if (attributionsIndex == -1) description else description.substring(0, attributionsIndex)
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Column(modifier = Modifier.noirContentWidth(NoirContentMax.List)) {
    NoirSectionHeader(title = stringResource(R.string.noir_about))
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(colorPalette().background1)
            .border(1.dp, colorPalette().text.copy(alpha = 0.08f), shape)
            .clickable { expanded = !expanded }
    ) {
        if (imageUrl != null)
            RevealImage(
                model = imageUrl.thumbnail(1000),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            if (!subscribers.isNullOrBlank())
                BasicText(
                    text = subscribers,
                    style = typography().s.copy(color = colorPalette().text, fontWeight = FontWeight.Bold)
                )
            BasicText(
                text = text.trim(),
                style = typography().xs.copy(color = colorPalette().textSecondary, fontSize = 14.sp, lineHeight = 20.sp),
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis
            )
            if (attributionsIndex != -1 && expanded)
                BasicText(
                    text = stringResource(R.string.from_wikipedia_cca),
                    style = typography().xxs.copy(color = colorPalette().textSecondary.copy(alpha = 0.7f))
                )
        }
    }
    }
}
