package it.fast4x.riplay.ui.screens.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.fast4x.riplay.commonutils.thumbnail
import it.fast4x.riplay.ui.components.RevealImage
import it.fast4x.riplay.ui.components.cascadeEntry
import it.fast4x.riplay.ui.components.themed.NoirEyebrow
import it.fast4x.riplay.ui.styling.mono
import it.fast4x.riplay.ui.styling.tight
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography

// Yammbo Noir: the pieces of the Statistics screen.

/** Segmented control: every option shares the width, the selected one is solid. */
@Composable
fun <T> StatsSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(colorPalette().background0)
            .border(1.dp, colorPalette().text.copy(alpha = 0.16f), shape)
            .padding(3.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(shape)
                    .background(if (isSelected) colorPalette().text else Color.Transparent)
                    .combinedClickable(onClick = { onSelect(value) })
            ) {
                BasicText(
                    text = label,
                    style = typography().xs.copy(
                        color = if (isSelected) colorPalette().background0 else colorPalette().text,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }
    }
}

/** One big number in a hairline tile. */
@Composable
fun RowScope.StatsTile(value: String, label: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .weight(1f)
            .clip(shape)
            .background(colorPalette().background1)
            .border(1.dp, colorPalette().text.copy(alpha = 0.10f), shape)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        BasicText(
            text = value,
            style = typography().xxl.tight.copy(
                color = colorPalette().text,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        NoirEyebrow(label)
    }
}

/**
 * The number one of the period: big cover, a tint taken from the cover behind it, and the
 * play time under the name. [circle] is for artists.
 */
@Composable
fun StatsSpotlight(
    eyebrow: String,
    title: String,
    subtitle: String?,
    image: String?,
    tint: Color?,
    circle: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    val colors = colorPalette()
    val base = colors.background1
    val washed = tint?.copy(alpha = 0.32f)?.compositeOver(base) ?: base
    val imageShape: Shape = if (circle) CircleShape else RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(washed, base)))
            .border(1.dp, colors.text.copy(alpha = 0.10f), shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .then(imageModifier)
                .clip(imageShape)
                .background(colors.background2)
        ) {
            RevealImage(
                model = image.thumbnail(480),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(120.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            NoirEyebrow(eyebrow)
            BasicText(
                text = title,
                style = typography().xl.tight.copy(
                    color = colors.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank())
                BasicText(
                    text = subtitle,
                    style = typography().xs.copy(color = colors.textSecondary, fontSize = 13.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
        }
    }
}

/**
 * A ranked row: the mono position in its own column left of the cover (never over it), the cover,
 * the name and a line of detail. The top three get a bigger position.
 */
@Composable
fun StatsRankRow(
    rank: Int,
    image: String?,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    circle: Boolean = false,
    imageModifier: Modifier = Modifier,
    index: Int = rank - 1
) {
    val colors = colorPalette()
    val top = rank <= 3
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .cascadeEntry(index)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        BasicText(
            text = "%02d".format(rank),
            style = typography().s.mono.copy(
                color = if (top) colors.text else colors.textSecondary,
                fontWeight = if (top) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (top) 18.sp else 13.sp,
                textAlign = TextAlign.Start
            ),
            maxLines = 1,
            modifier = Modifier.width(34.dp)
        )
        Box(
            modifier = Modifier
                .size(52.dp)
                .then(imageModifier)
                .clip(if (circle) CircleShape else RoundedCornerShape(10.dp))
                .background(colors.background2)
        ) {
            RevealImage(
                model = image.thumbnail(320),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = typography().s.copy(
                    color = colors.text,
                    fontWeight = if (top) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 16.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank())
                BasicText(
                    text = subtitle,
                    style = typography().xs.copy(color = colors.textSecondary, fontSize = 13.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
        }
    }
}
