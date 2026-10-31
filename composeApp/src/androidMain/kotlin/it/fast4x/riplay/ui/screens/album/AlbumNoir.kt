package it.fast4x.riplay.ui.screens.album

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import com.yambo.music.R
import it.fast4x.riplay.commonutils.cleanPrefix
import it.fast4x.riplay.commonutils.thumbnail
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.ui.components.NoirWidth
import it.fast4x.riplay.ui.components.RevealImage
import it.fast4x.riplay.ui.components.rememberNoirLayout
import it.fast4x.riplay.ui.components.sharedCover
import it.fast4x.riplay.ui.components.themed.NoirEyebrow
import it.fast4x.riplay.ui.components.themed.NoirTextAction
import it.fast4x.riplay.ui.styling.mono
import it.fast4x.riplay.ui.styling.tight
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.getBitmapFromUrl
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Yammbo Noir album page: a cover-tinted top, the cover, title block, one row of square actions
// around the big white play, and a plain numbered track list.

/**
 * The dominant colour of the cover (null until it is read, or when it cannot be). It is the only
 * colour the album page adds to the black and white palette.
 */
@Composable
fun rememberCoverTint(url: String?): Color? {
    val context = LocalContext.current
    var tint by remember(url) { mutableStateOf<Color?>(null) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        tint = withContext(Dispatchers.IO) {
            runCatching {
                // Same size the header shows (thumbnail(800)), so this is a cache hit, not a second download.
                val bitmap = getBitmapFromUrl(context, url.thumbnail(800) ?: url)
                val small = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
                val argb = Palette.from(small).generate().getDominantColor(0)
                if (argb == 0) null else Color(argb)
            }.getOrNull()
        }
    }
    return tint
}

/**
 * Top of the album page. [coverUrl] is the real cover, [previewUrl] the one the previous screen
 * already had in memory (drawn under it so the first frame matches the shared-cover flight).
 * With [showCover] false (landscape, where the side panel owns the cover) only the text and the
 * actions are drawn.
 */
@Composable
fun AlbumNoirHeader(
    coverUrl: String?,
    previewUrl: String?,
    sharedKey: String?,
    showCover: Boolean,
    title: String,
    artist: String,
    meta: String,
    tint: Color?,
    onBack: () -> Unit,
    onMore: (() -> Unit)?,
    // Pages hosted under a Noir top bar (on-device) already have back and a status-bar gap:
    // [embedded] drops this header's own back/more icons and the gap they reserved.
    embedded: Boolean = false,
    // Replaces the single cover image (e.g. a four-cover mosaic for folders).
    coverContent: (@Composable BoxScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    val statusBarTop = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val colors = colorPalette()
    val page = colors.background0
    val glow by animateColorAsState(
        targetValue = lerp(page, tint ?: page, 0.6f),
        animationSpec = tween(600),
        label = "albumTint"
    )
    val shape = RoundedCornerShape(28.dp)
    val noirLayout = rememberNoirLayout()
    val coverCap = when (noirLayout.width) {
        NoirWidth.Compact -> 280.dp
        NoirWidth.Medium -> 320.dp
        NoirWidth.Expanded -> 360.dp
    }
    val side = (LocalConfiguration.current.screenWidthDp * 0.6f).dp.coerceAtMost(coverCap)

    Box(modifier = Modifier.fillMaxWidth()) {
        // Tint from the cover fading into the page colour.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to glow,
                        0.55f to glow.copy(alpha = 0.4f),
                        1f to page
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(Modifier.height(if (embedded) 12.dp else statusBarTop + 56.dp))

            if (showCover) {
                Box(
                    modifier = Modifier
                        .size(side)
                        .sharedCover(sharedKey, shape)
                        .shadow(
                            elevation = 28.dp,
                            shape = shape,
                            clip = false,
                            ambientColor = tint ?: Color.Black,
                            spotColor = tint ?: Color.Black
                        )
                        .clip(shape)
                        .background(colors.background2)
                ) {
                    if (coverContent != null)
                        coverContent()
                    else {
                        // The tapped cover, already in memory: shows in the first frame.
                        if (previewUrl != null)
                            RevealImage(
                                model = previewUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        if (coverUrl != null)
                            RevealImage(
                                model = coverUrl.thumbnail(800),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                    }
                }
                Spacer(Modifier.height(26.dp))
            }

            if (title.isNotBlank())
                BasicText(
                    text = title,
                    style = typography().xxl.tight.copy(
                        color = colors.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (title.length > 22) 26.sp else 32.sp,
                        lineHeight = if (title.length > 22) 30.sp else 36.sp,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            if (artist.isNotBlank())
                BasicText(
                    text = artist,
                    style = typography().s.copy(
                        color = colors.text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(top = 6.dp)
                )
            if (meta.isNotBlank())
                BasicText(
                    text = meta.uppercase(),
                    style = typography().xxxs.mono.copy(
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp)
                )

            Spacer(Modifier.height(22.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
                content = actions
            )
            Spacer(Modifier.height(18.dp))
        }

        // Plain white icons straight over the tint: no glass discs.
        if (!embedded)
            AlbumTopIcon(
                icon = R.drawable.chevron_back,
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = statusBarTop + 4.dp)
            )
        if (!embedded && onMore != null)
            AlbumTopIcon(
                icon = R.drawable.ellipsis_horizontal,
                onClick = onMore,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp, top = statusBarTop + 4.dp)
            )
    }
}

@Composable
private fun AlbumTopIcon(
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette().text),
            modifier = Modifier.size(24.dp)
        )
    }
}

/** Outlined rounded-square action of the album bar (download, library, shuffle, share). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumSquareAction(
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .border(1.dp, colorPalette().text.copy(alpha = 0.16f), shape)
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette().text),
            modifier = Modifier.size(21.dp)
        )
    }
}

/** The big solid white play of the album bar, with a black icon. */
@Composable
fun AlbumPlayAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(72.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(colorPalette().text)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Image(
            painter = painterResource(R.drawable.play),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette().background0),
            modifier = Modifier.size(30.dp)
        )
    }
}

/**
 * One track: its number (bars while it is the current one), title and artists, a small mark when
 * it is downloaded and the duration in mono. Clicks come in [modifier]; in select mode the
 * duration is replaced by a checkbox.
 */
@Composable
fun AlbumTrackRow(
    index: Int,
    song: Song,
    isCurrent: Boolean,
    isDownloaded: Boolean,
    selectMode: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = colorPalette()
    // Disliked songs stay in the list but dimmed, as they cannot be played.
    val dimmed = song.likedAt == -1L
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .alpha(if (dimmed) 0.45f else 1f)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.width(32.dp)) {
            if (isCurrent)
                Image(
                    painter = painterResource(R.drawable.equalizer),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.text),
                    modifier = Modifier.size(16.dp)
                )
            else
                BasicText(
                    text = (index + 1).toString(),
                    style = typography().s.mono.copy(
                        color = colors.textSecondary,
                        fontSize = 14.sp,
                        letterSpacing = 0.sp
                    ),
                    maxLines = 1
                )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            BasicText(
                text = cleanPrefix(song.title),
                style = typography().s.copy(
                    color = colors.text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val artists = cleanPrefix(song.artistsText.orEmpty())
            if (artists.isNotBlank())
                BasicText(
                    text = artists,
                    style = typography().xs.copy(color = colors.textSecondary, fontSize = 13.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
        }
        if (isDownloaded) {
            Spacer(Modifier.width(10.dp))
            Image(
                painter = painterResource(R.drawable.downloaded),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.textSecondary),
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        if (selectMode)
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = colors.accent,
                    uncheckedColor = colors.text
                ),
                modifier = Modifier.scale(0.7f)
            )
        else
            BasicText(
                text = song.durationText.orEmpty(),
                style = typography().xs.mono.copy(
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    letterSpacing = 0.sp
                ),
                maxLines = 1
            )
    }
}

/**
 * Pill search field of the list pages (online playlist, podcast): same look as the History one.
 * [fieldModifier] goes on the text field itself (focus requester, focus listener).
 */
@Composable
fun NoirListSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    onDone: () -> Unit = {},
) {
    val colors = colorPalette()
    val shape = RoundedCornerShape(percent = 50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(colors.background1)
            .border(1.dp, colors.text.copy(alpha = 0.10f), shape)
            .padding(horizontal = 16.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.search),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.textSecondary),
            modifier = Modifier.size(18.dp)
        )
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            if (value.isEmpty()) {
                BasicText(
                    text = hint,
                    style = typography().s.copy(color = colors.textSecondary, fontSize = 15.sp),
                    maxLines = 1
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = typography().s.copy(color = colors.text, fontSize = 15.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                cursorBrush = SolidColor(colors.text),
                modifier = fieldModifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Image(
                painter = painterResource(R.drawable.close),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.textSecondary),
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onValueChange("") }
            )
        }
    }
}

/**
 * "About" block of the list pages: mono eyebrow with a small text action to translate, then the
 * description in secondary colour (4 lines, tap to expand or collapse) and an optional attribution.
 */
@Composable
fun NoirDescriptionBlock(
    eyebrow: String,
    text: String,
    translated: Boolean,
    translateLabel: String,
    originalLabel: String,
    onToggleTranslate: () -> Unit,
    modifier: Modifier = Modifier,
    attribution: String? = null,
) {
    val colors = colorPalette()
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            NoirEyebrow(text = eyebrow, modifier = Modifier.weight(1f))
            NoirTextAction(
                text = if (translated) originalLabel else translateLabel,
                onClick = onToggleTranslate
            )
        }
        Spacer(Modifier.height(10.dp))
        BasicText(
            text = text,
            style = typography().s.copy(
                color = colors.textSecondary,
                fontSize = 15.sp,
                lineHeight = 22.sp
            ),
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
        )
        if (!attribution.isNullOrBlank())
            BasicText(
                text = attribution,
                style = typography().xxs.copy(color = colors.textDisabled),
                modifier = Modifier.padding(top = 10.dp)
            )
    }
}
