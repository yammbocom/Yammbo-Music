package it.fast4x.riplay.ui.components.themed

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.isTVDevice

@Composable
fun HeaderIconButton(
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indication: Indication? = null,
    iconSize: Dp? = 20.dp
) {
    IconButton(
        icon = icon,
        color = color,
        onClick = onClick,
        enabled = enabled,
        indication = indication,
        modifier = modifier
            .padding(all = 2.dp)
            .size(iconSize ?: 18.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconButton(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    @DrawableRes icon: Int,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indication: Indication? = null
) {
    val isTv = isTVDevice()
    if (isTv) {
        // Expose D-pad focus on TV: wrap the icon in a focusable Box that shows
        // a soft circular highlight + subtle scale when the control is focused.
        var isFocused by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(
            targetValue = if (isFocused) 1.12f else 1f,
            animationSpec = tween(durationMillis = 140),
            label = "iconBtnScale"
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(
                    if (isFocused) colorPalette().accent.copy(alpha = 0.28f)
                    else Color.Transparent
                )
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = if (isFocused) colorPalette().accent else Color.Transparent,
                    shape = CircleShape
                )
                .focusable(enabled = enabled)
                .onFocusChanged { isFocused = it.isFocused }
                .combinedClickable(
                    indication = indication ?: ripple(bounded = false),
                    interactionSource = remember { MutableInteractionSource() },
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(6.dp)
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(color),
                modifier = modifier
            )
        }
    } else {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = Modifier
                .combinedClickable(
                    indication = indication ?: ripple(bounded = false),
                    interactionSource = remember { MutableInteractionSource() },
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .then(modifier)
        )
    }
}

