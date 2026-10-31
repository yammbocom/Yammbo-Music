package it.fast4x.riplay.ui.screens.player.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import it.fast4x.riplay.ui.components.noirContentWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import coil.compose.AsyncImage
import com.yambo.music.R
import it.fast4x.riplay.commonutils.thumbnail
import it.fast4x.riplay.ui.styling.mono
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography

// Yammbo Noir: the pieces of the full-screen player that the online and the local player share.

/**
 * The player background: the blurred cover filling the screen, fading to the page colour so the
 * controls at the bottom sit on a plain surface. [cover] is expected to be blurred already.
 * [veil] washes the light theme out further, so black lyrics stay readable over the cover.
 */
fun Modifier.noirCoverBackdrop(cover: Painter, light: Boolean, veil: Boolean = false): Modifier {
    val base = if (light) Color.White else Color.Black
    return this
        .background(base)
        .paint(cover, sizeToIntrinsics = false, contentScale = ContentScale.Crop, alpha = 0.9f)
        .background(
            Brush.verticalGradient(
                0f to base.copy(alpha = 0.45f),
                0.4f to base.copy(alpha = 0.3f),
                0.78f to base.copy(alpha = 0.92f),
                1f to base
            )
        )
        .background(if (light && veil) Color.White.copy(alpha = 0.6f) else Color.Transparent)
}

@Composable
private fun NoirIconButton(icon: Int, onClick: () -> Unit, size: Dp = 22.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette().text),
            modifier = Modifier.size(size)
        )
    }
}

/** Minimize, the Yammbo Music logo (with the queue's name under it, when known) and the menu. */
@Composable
fun NoirPlayerTopBar(
    source: String?,
    onDismiss: () -> Unit,
    onMenu: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onLogo: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .noirContentWidth(560.dp)
            .padding(horizontal = 8.dp)
            .height(52.dp)
    ) {
        NoirIconButton(icon = R.drawable.chevron_down, onClick = onDismiss, size = 24.dp)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f)
        ) {
            Image(
                painter = painterResource(R.drawable.yambo_icon),
                contentDescription = stringResource(R.string.app_name),
                colorFilter = ColorFilter.tint(colorPalette().text),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = onLogo != null) { onLogo?.invoke() }
                    .size(24.dp)
            )
            if (!source.isNullOrBlank())
                BasicText(
                    text = source,
                    style = typography().xxxs.copy(color = colorPalette().textSecondary, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
        }
        if (onMenu != null)
            NoirIconButton(icon = R.drawable.ellipsis_vertical, onClick = onMenu)
        else
            Spacer(Modifier.width(44.dp))
    }
}

/**
 * "SONG | VIDEO" over the top-left corner of the cover. The active one is the kind playing now;
 * tapping the other looks up the other version, the same as the old video button did.
 */
@Composable
fun NoirKindChips(isVideo: Boolean, onSwitch: () -> Unit, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        NoirKindChip(stringResource(R.string.noir_chip_song), active = !isVideo, onClick = { if (isVideo) onSwitch() })
        NoirKindChip(stringResource(R.string.noir_chip_video), active = isVideo, onClick = { if (!isVideo) onSwitch() })
    }
}

@Composable
private fun NoirKindChip(text: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    BasicText(
        text = text.uppercase(),
        style = typography().xxxs.mono.copy(
            fontSize = 9.sp,
            color = if (active) Color.Black else Color.White
        ),
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (active) Color.White else Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = if (active) 0f else 0.22f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

class NoirAction(
    val icon: Int,
    val label: String,
    val active: Boolean = false,
    // Replaces the icon, e.g. the Cast button, which draws itself.
    val content: (@Composable () -> Unit)? = null,
    val onClick: () -> Unit = {}
)

/** The row of labelled actions under the controls (lyrics, queue, cast, sleep, save, share). */
@Composable
fun NoirActionRow(actions: List<NoirAction>, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .noirContentWidth(560.dp)
            .padding(horizontal = 14.dp)
    ) {
        actions.forEach { action ->
            val tint = if (action.active) colorPalette().text else colorPalette().textSecondary
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .widthIn(min = 52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                        onClick = action.onClick
                    )
                    .padding(vertical = 6.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
                    if (action.content != null) action.content.invoke()
                    else Image(
                        painter = painterResource(action.icon),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(tint),
                        modifier = Modifier.size(22.dp)
                    )
                }
                BasicText(
                    text = action.label,
                    style = typography().xxxs.copy(color = tint, fontSize = 10.sp),
                    maxLines = 1
                )
            }
        }
    }
}

/** The sheet edge at the bottom of the player: the next song, tapping opens the queue. */
@Composable
fun NoirUpNextBar(
    next: MediaItem?,
    bottomInset: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val isDark = colorPalette().isDark
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .noirContentWidth(560.dp)
            .clip(shape)
            .background(if (isDark) Color(0xff0E0E0F) else colorPalette().background1)
            .border(1.dp, colorPalette().text.copy(alpha = 0.08f), shape)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 16.dp, bottom = bottomInset)
            .height(58.dp)
    ) {
        BasicText(
            text = stringResource(R.string.queue_up_next).uppercase(),
            style = typography().xxxs.mono.copy(color = colorPalette().textSecondary),
            maxLines = 1
        )
        val metadata = next?.takeIf { it.mediaId.isNotEmpty() }?.mediaMetadata
        if (metadata != null) {
            AsyncImage(
                model = metadata.artworkUri.toString().thumbnail(120),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colorPalette().background2)
            )
            BasicText(
                text = listOfNotNull(metadata.title, metadata.artist).joinToString(" · "),
                style = typography().xs.copy(color = colorPalette().text, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        } else {
            BasicText(
                text = stringResource(R.string.noir_queue_end),
                style = typography().xs.copy(color = colorPalette().textSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Image(
            painter = painterResource(R.drawable.chevron_up),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette().textSecondary),
            modifier = Modifier.size(18.dp)
        )
    }
}

/** Under the progress bar: elapsed, the stream quality in mono, and the time left. */
@Composable
fun NoirTimeRow(elapsed: String, quality: String?, remaining: String, modifier: Modifier = Modifier) {
    val style = typography().xxxs.mono.copy(color = colorPalette().textSecondary, fontSize = 10.sp)
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.noirContentWidth(560.dp)
    ) {
        BasicText(text = elapsed, style = style, maxLines = 1)
        if (!quality.isNullOrBlank()) BasicText(text = quality.uppercase(), style = style, maxLines = 1)
        BasicText(text = remaining, style = style, maxLines = 1)
    }
}

/** The big cover in the player. */
val NoirCoverShape = RoundedCornerShape(28.dp)
