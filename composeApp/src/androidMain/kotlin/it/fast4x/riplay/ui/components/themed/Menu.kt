package it.fast4x.riplay.ui.components.themed

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yambo.music.R
import it.fast4x.riplay.ui.styling.medium
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography

@Composable
inline fun Menu(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            //.padding(top = 12.dp)
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            // Same surface as the global sheet (so it reads as one card there), kept
            // for menus hosted outside the sheet.
            .background(colorPalette().background1)
            //.padding(top = 2.dp)
            // No top padding: the sheet's drag-handle pill already spaces the top.
            .padding(bottom = 8.dp)
            .navigationBarsPadding(),
        content = content
    )
}

/**
 * Hairline between a menu header (artwork + title) and its entries, or between two groups of
 * entries. Inset like the rows so it reads as a separator, not as an edge.
 */
@Composable
fun MenuHeaderDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(top = 8.dp, start = 20.dp, end = 20.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(colorPalette().text.copy(alpha = 0.08f))
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MenuEntry(
    painter: Painter,
    text: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    secondaryText: String? = null,
    enabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 20.dp)
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette().text),
            modifier = Modifier
                .size(22.dp)
        )

        Column(
            modifier = Modifier
                .padding(vertical = 10.dp)
                .weight(1f)
        ) {
            BasicText(
                text = text,
                style = typography().s.medium.copy(color = colorPalette().text, fontSize = 16.sp)
            )

            secondaryText?.takeIf { it.isNotBlank() }?.let { secondaryText ->
                BasicText(
                    text = secondaryText,
                    style = typography().xxs.medium.secondary.copy(fontSize = 13.sp)
                )
            }
        }

        trailingContent?.invoke()

    }
}

@Composable
fun MenuEntry(
    @DrawableRes icon: Int,
    text: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    secondaryText: String? = null,
    enabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null
) {
    MenuEntry(
        painterResource( icon ),
        text,
        onClick,
        onLongClick,
        secondaryText,
        enabled,
        trailingContent
    )
}

@Composable
fun SubMenuEntry(
    @DrawableRes icon: Int,
    text: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    secondaryText: String? = null,
    enabled: Boolean = true,
) {
    MenuEntry(
        painterResource( icon ),
        text,
        onClick,
        onLongClick,
        secondaryText,
        enabled,
        trailingContent = {
            Image(
                painter = painterResource(R.drawable.chevron_forward),
                contentDescription = null,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                    colorPalette().textSecondary
                ),
                modifier = Modifier
                    .size(18.dp)
            )
        }
    )
}

/**
 * Yammbo Noir switch: a white pill with a black knob when on, a hairline pill with a grey knob
 * when off. Used by the boolean rows of the menus instead of a check or an X icon.
 */
@Composable
fun NoirSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = colorPalette()
    val track by animateColorAsState(
        if (checked) colors.text else colors.text.copy(alpha = 0.16f),
        animationSpec = tween(180),
        label = "noirSwitchTrack"
    )
    val knob by animateColorAsState(
        if (checked) colors.background0 else colors.textSecondary,
        animationSpec = tween(180),
        label = "noirSwitchKnob"
    )
    val knobOffset by animateDpAsState(
        if (checked) 22.dp else 2.dp,
        animationSpec = tween(180),
        label = "noirSwitchOffset"
    )
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(CircleShape)
            .background(track)
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset)
                .size(24.dp)
                .clip(CircleShape)
                .background(knob)
        )
    }
}

/** A menu row for a boolean option: the entry plus a [NoirSwitch] on the right. */
@Composable
fun MenuToggleEntry(
    @DrawableRes icon: Int,
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
    secondaryText: String? = null,
    enabled: Boolean = true,
) {
    MenuEntry(
        icon = icon,
        text = text,
        onClick = onClick,
        secondaryText = secondaryText,
        enabled = enabled,
        trailingContent = { NoirSwitch(checked = checked) }
    )
}

/** A menu row for one choice out of several: a check on the right when it is the selected one. */
@Composable
fun MenuChoiceEntry(
    @DrawableRes icon: Int,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    secondaryText: String? = null,
    enabled: Boolean = true,
) {
    MenuEntry(
        icon = icon,
        text = text,
        onClick = onClick,
        onLongClick = onLongClick,
        secondaryText = secondaryText,
        enabled = enabled,
        trailingContent = if (selected) {
            {
                Image(
                    painter = painterResource(R.drawable.checkmark),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorPalette().text),
                    modifier = Modifier.size(20.dp)
                )
            }
        } else null
    )
}
